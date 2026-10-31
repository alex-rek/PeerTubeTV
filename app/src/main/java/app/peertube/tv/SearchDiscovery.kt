package app.peertube.tv

import android.content.Context
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

enum class SearchSource(val key: String) {
    SEPIA("sepia"), PEERSEEK("peerseek"), CUSTOM("custom"), FEDERATED("federated"), DIRECT("direct"), LOCAL("local");
    companion object { fun parse(value: String) = entries.firstOrNull { it.key == value } }
}

data class SearchSettings(
    val enabled: Set<SearchSource> = setOf(SearchSource.SEPIA, SearchSource.PEERSEEK, SearchSource.FEDERATED, SearchSource.DIRECT, SearchSource.LOCAL),
    val order: List<SearchSource> = SearchSource.entries.toList(),
    val mode: String = "hybrid", val sufficientResults: Int = 30,
    val maxParallel: Int = 3, val timeoutMs: Long = 12_000,
    val customIndexUrls: List<String> = emptyList(), val availabilityFilter: String = "unknown_or_available",
    val includeSensitive: Boolean = false, val sensitiveDisplayMode: String = "blur"
)

class SearchSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("peertube", Context.MODE_PRIVATE)
    fun read(): SearchSettings {
        val enabledKeys = prefs.getString("searchEnabledSources", "sepia,peerseek,federated,direct,local")!!.split(',').toSet()
        val orderKeys = prefs.getString("searchSourceOrder", "sepia,peerseek,custom,federated,direct,local")!!.split(',')
        val custom = runCatching { Gson().fromJson<List<String>>(prefs.getString("customSearchIndexes", "[]"), object : TypeToken<List<String>>() {}.type) ?: emptyList() }.getOrDefault(emptyList())
        return SearchSettings(
            enabled = enabledKeys.mapNotNullTo(linkedSetOf(), SearchSource::parse),
            order = (orderKeys.mapNotNull(SearchSource::parse) + SearchSource.entries).distinct(),
            mode = prefs.getString("searchExecutionMode", "hybrid")!!.takeIf { it in setOf("sequential", "parallel", "hybrid") } ?: "hybrid",
            sufficientResults = prefs.getInt("searchSufficientResults", 30).coerceIn(5, 100),
            maxParallel = prefs.getInt("searchMaxParallel", 3).coerceIn(1, 6),
            timeoutMs = prefs.getLong("searchTimeoutMs", 12_000L).coerceIn(3_000L, 30_000L),
            customIndexUrls = custom, availabilityFilter = prefs.getString("availabilityFilter", "unknown_or_available")!!,
            includeSensitive = prefs.getBoolean("searchIncludeSensitive", false),
            sensitiveDisplayMode = prefs.getString("sensitiveDisplayMode", "blur")!!.takeIf { it in setOf("hide", "blur", "warning", "show") } ?: "blur"
        )
    }
    fun setEnabled(source: SearchSource, enabled: Boolean) {
        val current = read().enabled.toMutableSet()
        if (enabled) current += source else current -= source
        prefs.edit().putString("searchEnabledSources", current.joinToString(",") { it.key }).apply()
    }
    fun setOrder(order: List<SearchSource>) = prefs.edit().putString("searchSourceOrder", order.distinct().joinToString(",") { it.key }).apply()
    fun setMode(value: String) = prefs.edit().putString("searchExecutionMode", value.takeIf { it in setOf("sequential", "parallel", "hybrid") } ?: "hybrid").apply()
    fun setSufficientResults(value: Int) = prefs.edit().putInt("searchSufficientResults", value.coerceIn(5, 100)).apply()
    fun setCustomIndexUrls(urls: List<String>) = prefs.edit().putString("customSearchIndexes", Gson().toJson(urls.map(String::trim).filter(String::isNotBlank).distinct().take(10))).apply()
    fun setAvailabilityFilter(value: String) = prefs.edit().putString("availabilityFilter", value.takeIf { it in setOf("all", "unknown_or_available", "available_only") } ?: "unknown_or_available").apply()
    fun setIncludeSensitive(value: Boolean) = prefs.edit().putBoolean("searchIncludeSensitive", value).apply()
    fun setSensitiveDisplayMode(value: String) = prefs.edit().putString("sensitiveDisplayMode", value.takeIf { it in setOf("hide", "blur", "warning", "show") } ?: "blur").apply()
}

data class SearchBatch(val videos: List<Video>, val total: Int? = null, val nextPage: Int? = null)
data class SearchOutcome(val videos: List<Video>, val sourcesByVideo: Map<String, Set<SearchSource>>, val errors: Map<SearchSource, String>, val hasMore: Boolean)
data class SearchRequest(
    val query: String, val page: Int, val pageSize: Int, val sort: String = "-match", val category: Int? = null,
    val durationMin: Int? = null, val durationMax: Int? = null, val liveOnly: Boolean = false,
    val language: String? = null, val country: String? = null, val activeServer: String, val includeSensitive: Boolean = false
)

class SearchAggregator(private val providers: Map<SearchSource, suspend (SearchRequest) -> SearchBatch>) {
    suspend fun search(request: SearchRequest, settings: SearchSettings): SearchOutcome {
        val query = request.query
        val page = request.page
        val pageSize = request.pageSize
        val sources = settings.order.filter { it in settings.enabled && (it != SearchSource.CUSTOM || settings.customIndexUrls.isNotEmpty()) }
        val errors = linkedMapOf<SearchSource, String>()
        val batches = mutableListOf<Pair<SearchSource, SearchBatch>>()
        suspend fun call(source: SearchSource): Pair<SearchSource, SearchBatch>? {
            val provider = providers[source] ?: return null
            val timeout = if (source == SearchSource.SEPIA && request.includeSensitive) maxOf(settings.timeoutMs, 30_000L) else settings.timeoutMs
            val batch = try {
                withTimeoutOrNull(timeout) { provider(request) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errors[source] = e.localizedMessage ?: e.javaClass.simpleName
                return null
            }
            if (batch == null) {
                errors[source] = "Timed out waiting for ${timeout} ms"
                return null
            }
            return source to batch
        }

        when (settings.mode) {
            "sequential" -> for (source in sources) {
                call(source)?.let(batches::add)
                val enough = batches.sumOf { it.second.videos.size } >= pageSize
                val sensitiveFound = batches.any { (_, batch) -> batch.videos.any(Video::nsfw) }
                if (enough && (!request.includeSensitive || sensitiveFound)) break
            }
            "parallel" -> {
                val semaphore = Semaphore(settings.maxParallel)
                coroutineScope {
                    sources.map { source -> async { semaphore.withPermit { call(source) } } }
                        .awaitAll().filterNotNull().forEach(batches::add)
                }
            }
            else -> {
                for (source in sources) {
                    call(source)?.let(batches::add)
                    val enough = batches.sumOf { it.second.videos.size } >= settings.sufficientResults
                    val sensitiveFound = batches.any { (_, batch) -> batch.videos.any(Video::nsfw) }
                    if (enough && (!request.includeSensitive || sensitiveFound)) break
                }
            }
        }
        val rank = sources.withIndex().associate { it.value to it.index }
        val sourceMap = linkedMapOf<String, MutableSet<SearchSource>>()
        val merged = mutableListOf<Video>()
        batches.sortedBy { rank[it.first] ?: Int.MAX_VALUE }.forEach { (source, batch) ->
            batch.videos.forEach { video ->
                val existing = merged.indexOfFirst { sameVideoIdentity(it, video) }
                if (existing < 0) {
                    merged += video
                    sourceMap.getOrPut(video.uuid) { linkedSetOf() } += source
                } else sourceMap.getOrPut(merged[existing].uuid) { linkedSetOf() } += source
            }
        }
        return SearchOutcome(merged, sourceMap.mapValues { it.value.toSet() }, errors, batches.any { (_, b) ->
            b.nextPage != null || (b.total ?: 0) > (page + 1) * pageSize
        })
    }
}

private fun sameVideoIdentity(first: Video, second: Video): Boolean {
    if (first.uuid.isNotBlank() && second.uuid.isNotBlank() && first.uuid.equals(second.uuid, true)) return true
    val firstUrl = first.url?.let { runCatching { URI(it).normalize().toString().trimEnd('/').lowercase(Locale.ROOT) }.getOrNull() }
    val secondUrl = second.url?.let { runCatching { URI(it).normalize().toString().trimEnd('/').lowercase(Locale.ROOT) }.getOrNull() }
    return firstUrl != null && firstUrl == secondUrl
}

data class DiscoveredServer(
    val host: String, val name: String? = null, val version: String? = null, val languages: List<String> = emptyList(),
    val country: String? = null, val discoveredFrom: Set<String> = emptySet(), val following: Set<String> = emptySet(),
    val followers: Set<String> = emptySet(), val apiStatus: String = "unknown", val apiError: String? = null, val streamStatus: String = "unknown",
    val metadataStatus: String = "unknown", val lastCheckedAt: Long = 0, val apiCheckedAt: Long = 0,
    val metadataCheckedAt: Long = 0, val streamCheckedAt: Long = 0,
    val totalVideos: Long? = null, val priority: Int = 0, val favorite: Boolean = false
)

class ServerRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("peertube", Context.MODE_PRIVATE)
    private val gson = Gson()
    @Synchronized fun all(): List<DiscoveredServer> = runCatching {
        gson.fromJson<List<DiscoveredServer>>(prefs.getString("discoveredServers", "[]"), object : TypeToken<List<DiscoveredServer>>() {}.type)
            .orEmpty().mapNotNull { it?.let(::normalizeDiscoveredServer) }
            .sortedByDescending { it.priority }
    }.getOrDefault(emptyList())
    @Synchronized fun merge(values: Collection<DiscoveredServer>) {
        val merged = all().associateBy { it.host.lowercase(Locale.ROOT) }.toMutableMap()
        values.mapNotNull(::normalizeDiscoveredServer).forEach { incoming ->
            val old = merged[incoming.host.lowercase(Locale.ROOT)]
            val newerApiCheck = old != null && incoming.apiCheckedAt > old.apiCheckedAt
            merged[incoming.host.lowercase(Locale.ROOT)] = if (old == null) incoming else old.copy(
                name = incoming.name ?: old.name, version = incoming.version ?: old.version,
                languages = incoming.languages.ifEmpty { old.languages }, country = incoming.country ?: old.country,
                discoveredFrom = old.discoveredFrom + incoming.discoveredFrom, following = old.following + incoming.following,
                followers = old.followers + incoming.followers,
                apiStatus = if (newerApiCheck) incoming.apiStatus else incoming.apiStatus.takeUnless { it == "unknown" } ?: old.apiStatus,
                apiError = if (newerApiCheck || incoming.apiStatus != "unknown") incoming.apiError else incoming.apiError ?: old.apiError,
                streamStatus = incoming.streamStatus.takeUnless { it == "unknown" } ?: old.streamStatus,
                lastCheckedAt = maxOf(old.lastCheckedAt, incoming.lastCheckedAt),
                apiCheckedAt = maxOf(old.apiCheckedAt, incoming.apiCheckedAt), metadataCheckedAt = maxOf(old.metadataCheckedAt, incoming.metadataCheckedAt),
                streamCheckedAt = maxOf(old.streamCheckedAt, incoming.streamCheckedAt),
                metadataStatus = incoming.metadataStatus.takeUnless { it == "unknown" } ?: old.metadataStatus,
                totalVideos = incoming.totalVideos ?: old.totalVideos,
                priority = incoming.priority.takeUnless { it == 0 } ?: old.priority,
                favorite = old.favorite || incoming.favorite
            )
        }
        prefs.edit().putString("discoveredServers", gson.toJson(merged.values.take(5000))).apply()
    }
    fun removeDiscovered() {
        val keep = all().filter { it.discoveredFrom.any { source -> source == "joinpeertube_seed" || source == "manual" } }
        prefs.edit().putString("discoveredServers", gson.toJson(keep)).apply()
    }
    @Synchronized fun toggleFavorite(host: String) {
        all().firstOrNull { it.host.equals(host, true) }?.let { merge(listOf(it.copy(favorite = !it.favorite))) }
    }
    @Synchronized fun movePriority(host: String, delta: Int) {
        val sorted = all().sortedWith(compareByDescending<DiscoveredServer> { it.priority }.thenBy { it.host }).toMutableList()
        val index = sorted.indexOfFirst { it.host.equals(host, true) }
        val target = (index + delta).coerceIn(0, (sorted.size - 1).coerceAtLeast(0))
        if (index >= 0 && target != index) {
            val item = sorted.removeAt(index)
            sorted.add(target, item)
            merge(sorted.mapIndexed { order, server -> server.copy(priority = sorted.size - order) })
        }
    }
    @Synchronized fun recordAvailability(host: String, api: AvailabilityCheck? = null, metadata: AvailabilityCheck? = null, stream: AvailabilityCheck? = null) {
        val current = all().firstOrNull { it.host.equals(host, true) } ?: DiscoveredServer(host, discoveredFrom = setOf("video_source"))
        merge(listOf(current.copy(
            apiStatus = api?.state ?: current.apiStatus,
            apiError = if (api == null) current.apiError else api.reason,
            metadataStatus = metadata?.state?.takeUnless { it == "unknown" } ?: current.metadataStatus,
            streamStatus = stream?.state?.takeUnless { it == "unknown" } ?: current.streamStatus,
            apiCheckedAt = api?.checkedAt ?: current.apiCheckedAt,
            metadataCheckedAt = metadata?.checkedAt ?: current.metadataCheckedAt,
            streamCheckedAt = stream?.checkedAt ?: current.streamCheckedAt,
            lastCheckedAt = listOfNotNull(api?.checkedAt, metadata?.checkedAt, stream?.checkedAt).maxOrNull() ?: current.lastCheckedAt
        )))
    }
    fun exportJson(): String = gson.toJson(all())
    fun importJson(json: String) {
        val type = object : TypeToken<List<DiscoveredServer>>() {}.type
        val items = gson.fromJson<List<DiscoveredServer>>(json, type).orEmpty()
        require(items.size <= 5000) { "Server file is too large" }
        merge(items.mapNotNull { it?.let(::normalizeDiscoveredServer) }.filter { SafeNetworkTarget.isSafeHttpsHost(it.host) })
    }
    fun seedBundled(context: Context) {
        val seedVersion = 4
        val removedPinnedHosts = setOf(
            "peervideo.ru", "peertube.cluster.wtf", "angeltales.angellive.ru",
            "video.pavel-english.ru", "video.radiodar.ru", "angelsoft.angellive.ru",
            "tube.ghk-academy.info", "video.383.su", "peertube.luanti.ru"
        )
        if (prefs.getInt("serverSeedVersion", 0) >= seedVersion) return
        // Remove the old pinned entries from already-installed copies as well as
        // from the new bundled catalog, otherwise the persisted cache would keep
        // restoring them after an app update.
        val retainedServers = all().filterNot { it.host.equalsAnyIgnoreCase(removedPinnedHosts) }
        prefs.edit().putString("discoveredServers", gson.toJson(retainedServers)).apply()
        val seeds = runCatching { context.assets.open("peertube_servers.json").bufferedReader().use { gson.fromJson<List<DiscoveredServer>>(it, object : TypeToken<List<DiscoveredServer>>() {}.type) } }.getOrNull().orEmpty()
            .filterNot { it.host.equalsAnyIgnoreCase(removedPinnedHosts) }
        merge(seeds.mapNotNull { it?.let(::normalizeDiscoveredServer) }.filter { it.host.matches(Regex("(?i)[a-z0-9.-]{1,253}") ) && !it.host.startsWith(".") && !it.host.endsWith(".") })
        prefs.edit().putInt("serverSeedVersion", seedVersion).putBoolean("serverSeedLoaded", true).apply()
    }
}

private fun String?.equalsAnyIgnoreCase(values: Set<String>): Boolean = this != null && values.any { equals(it, ignoreCase = true) }

internal fun normalizeDiscoveredServer(server: DiscoveredServer): DiscoveredServer? {
    val safeHost = server.host?.trim()?.removePrefix("https://")?.removePrefix("http://")
        ?.substringBefore('/')?.substringBefore(':')?.trimEnd('.')?.lowercase(Locale.ROOT)
        ?.takeIf {
            it.matches(Regex("(?i)[a-z0-9.-]{1,253}") ) && !it.startsWith('.') && !it.endsWith('.') &&
                it != "localhost" && !it.endsWith(".localhost") && !it.endsWith(".local") && !it.endsWith(".internal")
        }
        ?: return null
    return server.copy(
        host = safeHost,
        name = server.name?.takeIf(String::isNotBlank),
        version = server.version?.takeIf(String::isNotBlank),
        languages = server.languages.orEmpty().filter(String::isNotBlank).distinct(),
        country = server.country?.takeIf(String::isNotBlank),
        discoveredFrom = server.discoveredFrom.orEmpty().filter(String::isNotBlank).toSet(),
        following = server.following.orEmpty().filter(String::isNotBlank).toSet(),
        followers = server.followers.orEmpty().filter(String::isNotBlank).toSet(),
        apiStatus = server.apiStatus?.takeIf(String::isNotBlank) ?: "unknown",
        apiError = server.apiError?.takeIf(String::isNotBlank),
        streamStatus = server.streamStatus?.takeIf(String::isNotBlank) ?: "unknown",
        metadataStatus = server.metadataStatus?.takeIf(String::isNotBlank) ?: "unknown"
    )
}

object SafeNetworkTarget {
    fun isSafeHttpsHost(hostInput: String): Boolean {
        val host = hostInput.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':').trimEnd('.').lowercase(Locale.ROOT)
        if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return false
        return runCatching {
            val addresses = InetAddress.getAllByName(host)
            addresses.isNotEmpty() && addresses.all(::isPublicAddress)
        }.getOrDefault(false)
    }
    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        if (address is Inet4Address) {
            val b = address.address.map { it.toInt() and 255 }
            return !(b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 ||
                (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
                (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
                (b[0] == 192 && b[1] == 0 && b[2] in setOf(0, 2)) ||
                (b[0] == 198 && (b[1] in 18..19 || b[1] == 51 && b[2] == 100)) ||
                (b[0] == 203 && b[1] == 0 && b[2] == 113))
        }
        if (address is Inet6Address) {
            val bytes = address.address.map { it.toInt() and 255 }
            return !((bytes[0] and 0xfe) == 0xfc || (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8))
        }
        return false
    }
}

class CustomSearchIndexClient {
    suspend fun search(template: String, query: String, page: Int, count: Int): SearchBatch = withContext(Dispatchers.IO) {
        require(template.contains("{query}")) { "Search URL must contain the {query} placeholder" }
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        val urlText = template.trim().replace("{query}", encoded).replace("{page}", page.toString()).replace("{count}", count.toString())
        val uri = URI(urlText)
        require(uri.scheme == "https" && uri.host != null && SafeNetworkTarget.isSafeHttpsHost(uri.host)) { "Only public HTTPS search indexes are allowed" }
        ApiFactory.safeHttpGet(urlText).use { response ->
            val code = response.code
            require(code in 200..299) { "Index returned HTTP $code" }
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            response.body?.byteStream()?.use { input ->
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= 2_000_000) { "Index response exceeds 2 MB" }
                    output.write(buffer, 0, read)
                }
            }
            val body = output.toString(Charsets.UTF_8.name())
            val root = JsonParser.parseString(body).asJsonObject
            val list = root.get("videos") ?: root.get("data")
            val videos = list?.asJsonArray?.mapNotNull { item ->
                runCatching {
                    val json = item.asJsonObject
                    if (json.has("uuid") && json.has("name")) Gson().fromJson<Video>(json, Video::class.java)
                    else Gson().fromJson<PeerSeekVideo>(json, PeerSeekVideo::class.java).asVideo()
                }.getOrNull()
            }.orEmpty()
            SearchBatch(videos, root.get("total")?.asInt, root.get("nextPage")?.takeUnless(JsonElement::isJsonNull)?.asInt)
        }
    }
}

data class DiscoverySettings(
    val automatic: Boolean = true, val maxDepth: Int = 2, val maxNewPerRun: Int = 20,
    val refreshDays: Int = 7, val wifiOnly: Boolean = true,
    val joinPeerTubeEnabled: Boolean = true, val federationEnabled: Boolean = true,
    val bundledEnabled: Boolean = true, val remoteJsonEnabled: Boolean = true
)

class ServerDiscoveryManager(private val context: Context, private val repository: ServerRepository) {
    private val prefs = context.applicationContext.getSharedPreferences("peertube", Context.MODE_PRIVATE)
    fun settings() = DiscoverySettings(
        prefs.getBoolean("discoveryAutomatic", true), prefs.getInt("discoveryMaxDepth", 2).coerceIn(1, 5),
        prefs.getInt("discoveryMaxNew", 20).coerceIn(1, 100), prefs.getInt("discoveryRefreshDays", 7).coerceIn(1, 30),
        prefs.getBoolean("discoveryWifiOnly", true), prefs.getBoolean("discoveryJoinEnabled", true), prefs.getBoolean("discoveryFederationEnabled", true),
        prefs.getBoolean("discoveryBundledEnabled", true), prefs.getBoolean("discoveryJsonEnabled", true)
    )
    fun setAutomatic(value: Boolean) { prefs.edit().putBoolean("discoveryAutomatic", value).apply(); schedulePeriodic() }
    fun setMaxDepth(value: Int) = prefs.edit().putInt("discoveryMaxDepth", value.coerceIn(1, 5)).apply()
    fun setMaxNew(value: Int) = prefs.edit().putInt("discoveryMaxNew", value.coerceIn(1, 100)).apply()
    fun setRefreshDays(value: Int) { prefs.edit().putInt("discoveryRefreshDays", value.coerceIn(1, 30)).apply(); schedulePeriodic() }
    fun setWifiOnly(value: Boolean) { prefs.edit().putBoolean("discoveryWifiOnly", value).apply(); schedulePeriodic() }
    fun setJoinEnabled(value: Boolean) = prefs.edit().putBoolean("discoveryJoinEnabled", value).apply()
    fun setFederationEnabled(value: Boolean) = prefs.edit().putBoolean("discoveryFederationEnabled", value).apply()
    fun setBundledEnabled(value: Boolean) = prefs.edit().putBoolean("discoveryBundledEnabled", value).apply()
    fun setRemoteJsonEnabled(value: Boolean) = prefs.edit().putBoolean("discoveryJsonEnabled", value).apply()
    fun serverJsonUrl() = prefs.getString("serverCatalogJsonUrl", "").orEmpty()
    fun setServerJsonUrl(value: String) = prefs.edit().putString("serverCatalogJsonUrl", value.trim().take(2000)).apply()

    fun schedulePeriodic() {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (!settings().automatic) { scheduler.cancel(SERVER_DISCOVERY_JOB_ID); return }
        val networkType = if (settings().wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
        val job = JobInfo.Builder(SERVER_DISCOVERY_JOB_ID, ComponentName(context, ServerDiscoveryJobService::class.java))
            .setRequiredNetworkType(networkType)
            .setPeriodic(settings().refreshDays * 24L * 60L * 60L * 1000L)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    suspend fun discover(activeServer: String): Int = withContext(Dispatchers.IO) { withTimeout(90_000) {
        val config = settings()
        val maxNew = config.maxNewPerRun
        val found = LinkedHashMap<String, DiscoveredServer>()
        val now = System.currentTimeMillis()
        if (config.joinPeerTubeEnabled && maxNew > 0) {
            var start = 0
            var examined = 0
            while (found.size < maxNew && examined < maxNew * 4) {
                val page = runCatching { ApiFactory.directory().instances(start = start, count = 100) }.getOrNull() ?: break
                if (page.data.isEmpty()) break
                page.data.forEach { item ->
                    if (found.size >= maxNew) return@forEach
                    val host = item.host.trim().lowercase(Locale.ROOT)
                    if (!SafeNetworkTarget.isSafeHttpsHost(host)) return@forEach
                    if (host !in found) found[host] = DiscoveredServer(host, item.name, item.version, item.languages, item.country,
                        setOf("joinpeertube"), apiStatus = "unknown", totalVideos = item.totalVideos)
                }
                examined += page.data.size
                start += page.data.size
                if (start >= page.total) break
            }
        }

        val remoteJsonList = serverJsonUrl()
        if (config.remoteJsonEnabled && remoteJsonList.isNotBlank() && found.size < maxNew) {
            val imported = runCatching { fetchServerJsonList(remoteJsonList) }.getOrNull().orEmpty()
            val remaining = (maxNew - found.size).coerceAtLeast(0)
            imported.take(remaining).forEach { server ->
                if (SafeNetworkTarget.isSafeHttpsHost(server.host)) {
                    val confirmed = runCatching { ApiFactory.config("https://${server.host}").publicConfig() }.getOrNull()
                    val normalizedHost = server.host.lowercase(Locale.ROOT)
                    if (confirmed != null && normalizedHost !in found) found[normalizedHost] = server.copy(discoveredFrom = server.discoveredFrom + "remote_json", apiStatus = "available", lastCheckedAt = now)
                }
            }
        }

        if (config.federationEnabled && found.size < maxNew) {
            val rootHost = runCatching { URI(activeServer).host }.getOrNull()
                ?.lowercase(Locale.ROOT)?.takeIf(SafeNetworkTarget::isSafeHttpsHost)
            val existing = repository.all().filter { SafeNetworkTarget.isSafeHttpsHost(it.host) && (config.bundledEnabled || "joinpeertube_seed" !in it.discoveredFrom) }
                .sortedByDescending { it.priority }.map { it.host }
            var frontier = (listOfNotNull(rootHost) + existing).distinct().take(8)
            val visited = mutableSetOf<String>()
            for (depth in 1..config.maxDepth) {
                val next = linkedSetOf<String>()
                for (host in frontier) {
                    if (!visited.add(host) || found.size >= maxNew) continue
                    if (!SafeNetworkTarget.isSafeHttpsHost(host)) continue
                    val api = ApiFactory.create("https://$host")
                    val relationships = mutableMapOf<String, Set<String>>()
                    for (kind in listOf("following", "followers")) {
                        val pages = runCatching {
                            if (kind == "following") api.serverFollowing(start = 0, count = 20) else api.serverFollowers(start = 0, count = 20)
                        }.getOrNull() ?: continue
                        relationships[kind] = extractPeerTubeHosts(pages) - host
                    }
                    val links = relationships.values.flatten().toMutableSet()
                    val localRecord = repository.all().firstOrNull { it.host.equals(host, true) }
                        ?: DiscoveredServer(host, discoveredFrom = setOf("federation_seed"))
                    repository.merge(listOf(localRecord.copy(
                        following = localRecord.following + relationships["following"].orEmpty(),
                        followers = localRecord.followers + relationships["followers"].orEmpty()
                    )))
                    for (candidate in links) {
                        if (found.size >= maxNew) break
                        if (candidate in visited || !SafeNetworkTarget.isSafeHttpsHost(candidate)) continue
                        // Confirm PeerTube API behavior on-device before admitting a federation result.
                        runCatching { ApiFactory.config("https://$candidate").publicConfig() }.getOrNull() ?: continue
                        val record = DiscoveredServer(candidate, version = null, discoveredFrom = setOf("federation:$host"), apiStatus = "available", lastCheckedAt = now)
                        found[candidate] = record
                        next += candidate
                        kotlinx.coroutines.delay(400)
                    }
                }
                frontier = next.toList()
                if (frontier.isEmpty()) break
            }
        }
        repository.merge(found.values)
        prefs.edit().putLong("lastServerDiscoveryAt", now).apply()
        found.size
    } }

    private fun extractPeerTubeHosts(root: JsonElement): Set<String> {
        val result = linkedSetOf<String>()
        fun visit(element: JsonElement?) {
            if (element == null || element.isJsonNull) return
            if (element.isJsonArray) element.asJsonArray.forEach(::visit)
            else if (element.isJsonObject) element.asJsonObject.entrySet().forEach { (key, value) ->
                if (key in setOf("url", "host", "uri", "id") && value.isJsonPrimitive) {
                    val text = runCatching { value.asString }.getOrNull().orEmpty()
                    val host = runCatching { if (text.startsWith("http")) URI(text).host else text.substringAfter('@', text).substringBefore('/') }.getOrNull()
                    host?.lowercase(Locale.ROOT)?.trimEnd('.')?.takeIf { it.contains('.') }?.let(result::add)
                }
                visit(value)
            }
        }
        visit(root)
        return result
    }

    private suspend fun fetchServerJsonList(urlText: String): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val uri = URI(urlText)
        require(uri.scheme == "https" && uri.host != null && SafeNetworkTarget.isSafeHttpsHost(uri.host)) { "Only public HTTPS catalog URLs are allowed" }
        ApiFactory.safeHttpGet(urlText).use { response ->
            require(response.code in 200..299) { "Server catalog returned HTTP ${response.code}" }
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            response.body?.byteStream()?.use { input ->
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= 2_000_000) { "Server catalog exceeds 2 MB" }
                    output.write(buffer, 0, read)
                }
            }
            val root = JsonParser.parseString(output.toString(Charsets.UTF_8.name()))
            val list = if (root.isJsonArray) root.asJsonArray else root.asJsonObject.get("servers")?.asJsonArray ?: root.asJsonObject.get("data")?.asJsonArray
            list?.mapNotNull { element -> runCatching { if (element.isJsonPrimitive) DiscoveredServer(host = element.asString) else Gson().fromJson<DiscoveredServer>(element, DiscoveredServer::class.java) }.getOrNull() }.orEmpty()
        }
    }

    companion object { const val SERVER_DISCOVERY_JOB_ID = 74021 }
}

data class AvailabilityCheck(
    val state: String, val reason: String? = null, val httpCode: Int? = null,
    val checkedAt: Long = System.currentTimeMillis(), val apiState: String? = null, val metadataState: String? = null
)

class ServerAvailabilityManager(private val repository: ServerRepository? = null) {
    suspend fun checkApi(host: String): AvailabilityCheck = withContext(Dispatchers.IO) {
        if (!SafeNetworkTarget.isSafeHttpsHost(host)) return@withContext AvailabilityCheck("unknown", "unsafe_or_invalid_host")
        try {
            withTimeout(10_000) { ApiFactory.config("https://$host").publicConfig() }
            AvailabilityCheck("available")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: retrofit2.HttpException) { AvailabilityCheck(if (e.code() in setOf(404, 410)) "unavailable" else "unknown", "http_${e.code()}", e.code()) }
        catch (e: Exception) { AvailabilityCheck("unknown", e.javaClass.simpleName) }
    }

    suspend fun checkVideo(video: Video): AvailabilityCheck = withContext(Dispatchers.IO) {
        val metadata = checkMetadata(video)
        val resolvedVideo = metadata.first ?: video
        val metadataCheck = metadata.second
        val apiState = when (metadataCheck.state) { "available", "unavailable" -> "available"; else -> "unknown" }
        val serverHost = runCatching { URI(video.sourceServer("https://invalid.invalid")).host }.getOrNull()
        val apiCheck = AvailabilityCheck(apiState, checkedAt = metadataCheck.checkedAt)
        suspend fun finish(streamCheck: AvailabilityCheck): AvailabilityCheck {
            val combined = streamCheck.copy(apiState = apiCheck.state, metadataState = metadataCheck.state)
            if (serverHost != null) repository?.recordAvailability(serverHost, apiCheck, metadataCheck, streamCheck)
            return combined
        }
        val stream = resolvedVideo.playableUrl() ?: return@withContext finish(AvailabilityCheck("unknown", "no_advertised_video_stream"))
        val uri = runCatching { URI(stream) }.getOrNull()
        val host = uri?.host ?: return@withContext finish(AvailabilityCheck("unknown", "invalid_stream_url"))
        if (uri.scheme != "https" || !SafeNetworkTarget.isSafeHttpsHost(host)) return@withContext finish(AvailabilityCheck("unknown", "unsafe_or_non_https_stream"))
        try {
            val code = ApiFactory.safeHttpGet(stream, "bytes=0-0").use { it.code }
            val state = when {
                code == 206 || code == 200 -> "available"
                code == 404 || code == 410 -> "unavailable"
                else -> "unknown"
            }
            finish(AvailabilityCheck(state, when (code) {
                403 -> "forbidden_or_restricted"
                404 -> "not_found"
                410 -> "gone"
                429 -> "rate_limited"
                in 500..599 -> "server_error"
                in 300..399 -> "redirect_not_followed"
                else -> null
            }, code))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { finish(AvailabilityCheck("unknown", e.javaClass.simpleName)) }
    }

    private suspend fun checkMetadata(video: Video): Pair<Video?, AvailabilityCheck> {
        val origin = video.sourceServer("https://invalid.invalid")
        val host = runCatching { URI(origin).host }.getOrNull() ?: return null to AvailabilityCheck("unknown", "invalid_metadata_host")
        if (!SafeNetworkTarget.isSafeHttpsHost(host)) return null to AvailabilityCheck("unknown", "unsafe_metadata_host")
        return try {
            val resolved = withTimeout(10_000) { ApiFactory.create("https://$host").video(video.uuid) }
            resolved to AvailabilityCheck("available")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: retrofit2.HttpException) {
            val state = if (e.code() in setOf(404, 410)) "unavailable" else "unknown"
            null to AvailabilityCheck(state, "http_${e.code()}", e.code())
        } catch (e: Exception) { null to AvailabilityCheck("unknown", e.javaClass.simpleName) }
    }
}
