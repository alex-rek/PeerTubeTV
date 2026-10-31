@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.peertube.tv

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.OkHttpClient
import java.util.LinkedHashMap

data class VideoPage(val data: List<Video> = emptyList(), val total: Int = 0)
data class PlaybackQueueItem(
    val uuid: String,
    val server: String,
    val url: String? = null,
    val title: String,
    val views: Long = 0,
    val publishedAt: String? = null,
    val channelName: String? = null,
    val channelHost: String = "",
    val channelDisplayName: String? = null,
    val audioOnly: Boolean = false,
    val liveStateId: Int? = null
)
data class LiveVideoSession(val startDate: String? = null, val endDate: String? = null)
data class CaptionPage(val data: List<VideoCaption> = emptyList())
data class VideoCaption(val language: CaptionLanguage? = null, val fileUrl: String? = null, val captionPath: String? = null, val automaticallyGenerated: Boolean? = null)
data class CaptionLanguage(val id: String? = null, val label: String? = null)
data class Video(val uuid: String, val name: String, val description: String? = "", val thumbnailPath: String? = null, val thumbnailUrl: String? = null, val views: Long = 0, val duration: Long = 0, val tags: List<String>? = null, val channel: Channel? = null, val account: Account? = null, val host: String? = null, val url: String? = null, val files: List<VideoFile>? = null, val streamingPlaylists: List<StreamingPlaylist>? = null, val publishedAt: String? = null, val isLive: Boolean? = null, val state: VideoState? = null, val category: VideoCategory? = null, val nsfw: Boolean = false, val previewPath: String? = null) {
    private fun allMediaFiles(): List<VideoFile> = files.orEmpty() + streamingPlaylists.orEmpty().flatMap { it.files.orEmpty() }

    fun isAudioOnlySource(): Boolean {
        val mediaFiles = allMediaFiles()
        return mediaFiles.any { it.hasVideo == false } && mediaFiles.none { it.hasVideo == true || it.hasVideo == null }
    }

    fun playableUrl(): String? = streamingPlaylists.orEmpty()
        .firstNotNullOfOrNull { it.playlistUrl?.takeIf(String::isNotBlank) }
        ?: files.orEmpty().asSequence()
            .filter { it.hasVideo != false }
            .maxByOrNull { it.resolution?.id ?: 0 }
            ?.fileUrl?.takeIf(String::isNotBlank)
        ?: streamingPlaylists.orEmpty().asSequence()
            .flatMap { it.files.orEmpty().asSequence() }
            .filter { it.hasVideo != false }
            .maxByOrNull { it.resolution?.id ?: 0 }
            ?.fileUrl?.takeIf(String::isNotBlank)
        ?: allMediaFiles().asSequence()
            .filter { it.hasVideo == false }
            .maxByOrNull { it.resolution?.id ?: 0 }
            ?.fileUrl?.takeIf(String::isNotBlank)

    fun sourceServer(fallback: String): String {
        val webUrl = url?.let { runCatching { java.net.URI(it) }.getOrNull() }
        val urlHost = webUrl?.host
        val sourceHost = host?.takeIf(String::isNotBlank)
            ?: account?.host?.takeIf(String::isNotBlank)
            ?: channel?.host?.takeIf(String::isNotBlank)
            ?: urlHost
        return when {
            urlHost != null && webUrl?.scheme in listOf("http", "https") -> "${webUrl.scheme}://$urlHost"
            sourceHost != null -> "https://$sourceHost"
            else -> fallback
        }
    }
}
data class Channel(val id: Int? = null, val displayName: String? = null, val name: String? = null, val host: String? = null)
data class Account(val displayName: String? = null, val name: String? = null, val host: String? = null)
data class VideoFile(val fileUrl: String? = null, val resolution: Resolution? = null, val hasVideo: Boolean? = null)
data class Resolution(val id: Int = 0, val label: String? = null)
data class StreamingPlaylist(val playlistUrl: String? = null, val files: List<VideoFile>? = null)
data class VideoState(val id: Int? = null, val label: String? = null)
data class VideoCategory(val id: Int? = null, val label: String? = null)

/** Parses PeerTube's RFC 3339 timestamps on Android versions before java.time is available. */
internal fun parseIsoDateMillis(value: String): Long? {
    val match = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$""").matchEntire(value.trim()) ?: return null
    val fractionMillis = (match.groupValues[2] + "000").take(3)
    val timezone = match.groupValues[3].let { if (it == "Z") "+0000" else it.replace(":", "") }
    val normalized = "${match.groupValues[1]}.$fractionMillis$timezone"
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US).apply {
            isLenient = false
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(normalized)?.time
    }.getOrNull()
}

/** Like runCatching, but does not turn coroutine cancellation into an ordinary failure. */
internal suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

interface PeerTubeApi {
    @GET("api/v1/videos") suspend fun videos(@Query("sort") sort: String = "-publishedAt", @Query("count") count: Int = 30, @Query("search") search: String? = null, @Query("isLive") isLive: Boolean? = null, @Query("includeScheduledLive") includeScheduledLive: Boolean? = null, @Query("languageOneOf[]") languageOneOf: String? = null): VideoPage
    @GET("api/v1/search/videos") suspend fun searchVideos(@Query("search") search: String, @Query("start") start: Int = 0, @Query("count") count: Int = 40, @Query("sort") sort: String? = "-match", @Query("nsfw") nsfw: String? = null): VideoPage
    @GET("api/v1/server/following") suspend fun serverFollowing(@Query("start") start: Int = 0, @Query("count") count: Int = 20): com.google.gson.JsonObject
    @GET("api/v1/server/followers") suspend fun serverFollowers(@Query("start") start: Int = 0, @Query("count") count: Int = 20): com.google.gson.JsonObject
    @GET("api/v1/video-channels") suspend fun videoChannels(@Query("start") start: Int = 0, @Query("count") count: Int = 100): PeerTubeChannelPage
    @GET("api/v1/video-channels/{handle}/videos") suspend fun channelVideos(@Path(value = "handle", encoded = true) handle: String, @Query("count") count: Int = 100): VideoPage
    @GET("api/v1/videos/{id}") suspend fun video(@Path("id") id: String): Video
    @GET("api/v1/videos/{id}/live-session") suspend fun liveSession(@Path("id") id: String): LiveVideoSession
    @GET("api/v1/videos/{id}/captions") suspend fun captions(@Path("id") id: String): CaptionPage
    @GET("api/v1/videos/{id}/comment-threads") suspend fun commentThreads(@Path("id") id: String, @Query("start") start: Int = 0, @Query("count") count: Int = 20, @Query("sort") sort: String = "-createdAt"): com.google.gson.JsonObject
}

data class InstancePage(val total: Int = 0, val data: List<PeerTubeInstance> = emptyList())
data class PeerTubeChannelPage(val total: Int = 0, val data: List<PeerTubeChannel> = emptyList())
data class PeerTubeChannel(val id: Int? = null, val name: String? = null, val displayName: String? = null, val host: String? = null, val videosCount: Long? = null)
data class PeerTubeInstance(val host: String, val name: String? = null, val signupAllowed: Boolean? = null, val totalVideos: Long? = null, val health: Int? = null, val languages: List<String> = emptyList(), val version: String? = null, val country: String? = null, val totalInstanceFollowers: Long? = null, val totalInstanceFollowing: Long? = null)
data class PeerTubeCustomConfig(val instance: PeerTubeInstanceConfig? = null)
data class PeerTubeInstanceConfig(val languages: List<String> = emptyList())

interface InstanceDirectoryApi {
    @GET("api/v1/instances") suspend fun instances(@Query("start") start: Int = 0, @Query("count") count: Int = 100): InstancePage
}

interface PeerTubeConfigApi {
    @GET("api/v1/config") suspend fun publicConfig(): com.google.gson.JsonObject
    @GET("api/v1/config/custom") suspend fun customConfig(): PeerTubeCustomConfig
}

interface SepiaSearchApi {
    @GET("api/v1/search/videos") suspend fun videos(@Query("search") search: String? = null, @Query("start") start: Int = 0, @Query("count") count: Int = 30, @Query("sort") sort: String? = null, @Query("categoryOneOf") categoryOneOf: Int? = null, @Query("durationMin") durationMin: Int? = null, @Query("durationMax") durationMax: Int? = null, @Query("isLive") isLive: Boolean? = null, @Query("includeScheduledLive") includeScheduledLive: Boolean? = null, @Query("languageOneOf[]") languageOneOf: String? = null, @Query("nsfw") nsfw: String? = null): VideoPage
}

data class PeerSeekPage(val videos: List<PeerSeekVideo> = emptyList(), val total: Int = 0, val page: Int = 1, val size: Int = 24)
data class PeerSeekVideo(
    val id: String? = null, val uuid: String? = null, val title: String? = null, val name: String? = null,
    val description: String? = null, val thumbnailUrl: String? = null, val videoUrl: String? = null,
    val isLive: Boolean? = null, val views: Long? = null, val durationSeconds: Long? = null,
    val publishedAt: String? = null, val instance: com.google.gson.JsonElement? = null,
    val channel: com.google.gson.JsonElement? = null, val account: com.google.gson.JsonElement? = null, val tags: List<String>? = null
    , val category: com.google.gson.JsonElement? = null, val nsfw: Boolean = false
) {
    fun asVideo(): Video? {
        val videoId = uuid?.takeIf(String::isNotBlank) ?: id?.takeIf(String::isNotBlank) ?: return null
        fun field(element: com.google.gson.JsonElement?, vararg keys: String): String? {
            if (element == null || element.isJsonNull) return null
            if (element.isJsonPrimitive) return element.asString
            if (!element.isJsonObject) return null
            val obj = element.asJsonObject
            return keys.firstNotNullOfOrNull { key -> obj.get(key)?.takeUnless { it.isJsonNull }?.asString }
        }
        // `name` may be a display label, not a hostname. Treating it as an
        // origin sends the player to a made-up https://<display name> address.
        val host = (field(instance, "host", "domain", "hostname", "url") ?: instance?.takeIf { it.isJsonPrimitive }?.asString)
            ?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')
        val channelName = field(channel, "name", "displayName", "handle")
        val channelDisplayName = field(channel, "displayName", "name")
        val channelHost = field(channel, "host", "domain", "hostname") ?: host
        val accountName = field(account, "name", "displayName", "handle")
        val accountDisplayName = field(account, "displayName", "name")
        val accountHost = field(account, "host", "domain", "hostname") ?: host
        return Video(
            uuid = videoId,
            name = title?.takeIf(String::isNotBlank) ?: name?.takeIf(String::isNotBlank) ?: return null,
            description = description,
            thumbnailUrl = thumbnailUrl,
            views = views ?: 0L,
            duration = durationSeconds ?: 0L,
            tags = tags,
            channel = channelName?.let { Channel(displayName = channelDisplayName, name = it, host = channelHost) },
            account = accountName?.let { Account(displayName = accountDisplayName, name = it, host = accountHost) },
            host = host,
            url = videoUrl?.takeIf(String::isNotBlank),
            isLive = isLive,
            nsfw = nsfw,
            category = category?.takeIf { !it.isJsonNull }?.let { element ->
                if (element.isJsonPrimitive) VideoCategory(id = element.asString.toIntOrNull(), label = element.asString.takeUnless { it.toIntOrNull() != null })
                else if (element.isJsonObject) VideoCategory(id = element.asJsonObject.get("id")?.asInt, label = element.asJsonObject.get("label")?.asString)
                else null
            }
        )
    }
}
interface PeerSeekApi {
    @GET("v1/search") suspend fun search(@Query("q") query: String, @Query("sort") sort: String = "relevance", @Query("language") language: String? = null, @Query("page") page: Int = 1, @Query("size") size: Int = 40, @Query("include_live") includeLive: Boolean = true): PeerSeekPage
}

data class NetworkProxySettings(
    val enabled: Boolean = false,
    val type: String = "http",
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = ""
)

object ApiFactory {
    private val gsonConverter = GsonConverterFactory.create()
    private val systemProxySelector = java.net.ProxySelector.getDefault()
    @Volatile private var proxySettings = NetworkProxySettings()
    @Volatile private var safeHttpClient = buildHttpClient(proxySettings)
    private val peerTubeApis = object : LinkedHashMap<String, PeerTubeApi>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PeerTubeApi>?): Boolean = size > 32
    }
    @Volatile private var directoryApi: InstanceDirectoryApi? = null
    @Volatile private var sepiaApi: SepiaSearchApi? = null
    @Volatile private var peerSeekApi: PeerSeekApi? = null

    @Synchronized
    fun configureProxy(settings: NetworkProxySettings, context: android.content.Context? = null) {
        val normalized = settings.copy(type = settings.type.takeIf { it in setOf("http", "socks5") } ?: "http", port = settings.port.coerceIn(1, 65535))
        if (normalized == proxySettings) { context?.let(::installImageLoader); return }
        proxySettings = normalized
        safeHttpClient = buildHttpClient(normalized)
        peerTubeApis.clear()
        directoryApi = null
        sepiaApi = null
        peerSeekApi = null
        if (normalized.enabled && normalized.host.isNotBlank()) {
            val proxyHost = normalized.host
            val proxyPort = normalized.port
            val username = normalized.username
            val password = normalized.password
            java.net.ProxySelector.setDefault(object : java.net.ProxySelector() {
                private val proxy = java.net.Proxy(if (normalized.type == "socks5") java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP,
                    java.net.InetSocketAddress.createUnresolved(proxyHost, proxyPort))
                override fun select(uri: java.net.URI?): List<java.net.Proxy> =
                    if (uri?.scheme.equals("http", true) || uri?.scheme.equals("https", true)) listOf(proxy) else listOf(java.net.Proxy.NO_PROXY)
                override fun connectFailed(uri: java.net.URI?, socketAddress: java.net.SocketAddress?, error: java.io.IOException?) = Unit
            })
            if (username.isNotBlank()) java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
                    // Android may report SOCKS5 credentials with a requestor type other
                    // than PROXY. Match the configured proxy endpoint as well, as RussTube does.
                    val socksAuthentication = normalized.type == "socks5"
                    val proxyRequest = requestorType == RequestorType.PROXY || socksAuthentication
                    return if (proxyRequest && requestingHost.equals(proxyHost, true) && requestingPort == proxyPort)
                        java.net.PasswordAuthentication(username, password.toCharArray()) else null
                }
            }) else java.net.Authenticator.setDefault(null)
        } else {
            java.net.ProxySelector.setDefault(systemProxySelector)
            java.net.Authenticator.setDefault(null)
        }
        context?.let(::installImageLoader)
    }

    private fun installImageLoader(context: android.content.Context) {
        runCatching {
            // PeerTube instances often serve thumbnails from a redirecting CDN or
            // reverse proxy. Keep API redirects disabled, but allow Coil to follow
            // HTTPS image redirects using the same proxy, DNS and TLS safeguards.
            val imageClient = safeHttpClient.newBuilder()
                .followRedirects(true)
                .followSslRedirects(false)
                .build()
            coil.Coil.setImageLoader(coil.ImageLoader.Builder(context.applicationContext).okHttpClient(imageClient).build())
        }
    }

    private fun buildHttpClient(settings: NetworkProxySettings): OkHttpClient {
        val proxy = if (settings.enabled && settings.host.isNotBlank()) {
            java.net.Proxy(if (settings.type == "socks5") java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress.createUnresolved(settings.host.trim(), settings.port.coerceIn(1, 65535)))
        } else null
        val builder = OkHttpClient.Builder()
        .dns(object : okhttp3.Dns {
          override fun lookup(hostname: String): List<java.net.InetAddress> {
            if (settings.enabled && hostname.equals(settings.host.trim(), true)) return okhttp3.Dns.SYSTEM.lookup(hostname)
            val addresses = java.net.InetAddress.getAllByName(hostname).toList()
            require(addresses.isNotEmpty() && addresses.all { address ->
                !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isSiteLocalAddress && !address.isMulticastAddress &&
                    !(address is java.net.Inet4Address && run {
                        val b = address.address.map { it.toInt() and 255 }
                        b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 || (b[0] == 100 && b[1] in 64..127) ||
                            (b[0] == 169 && b[1] == 254) || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
                            (b[0] == 192 && b[1] == 0 && b[2] in setOf(0, 2)) ||
                            (b[0] == 198 && (b[1] in 18..19 || b[1] == 51 && b[2] == 100)) ||
                            (b[0] == 203 && b[1] == 0 && b[2] == 113)
                    }) && !(address is java.net.Inet6Address && ((address.address[0].toInt() and 0xfe) == 0xfc ||
                    (address.address[0].toInt() and 255) == 0x20 && (address.address[1].toInt() and 255) == 0x01 &&
                    (address.address[2].toInt() and 255) == 0x0d && (address.address[3].toInt() and 255) == 0xb8))
            }) { "Refusing to connect to a non-public address" }
            return addresses
          }
        })
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        if (proxy != null) builder.proxy(proxy)
        if (settings.enabled && settings.type == "http" && settings.username.isNotBlank()) {
            builder.proxyAuthenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) null
                else response.request.newBuilder().header("Proxy-Authorization", okhttp3.Credentials.basic(settings.username, settings.password)).build()
            }
        }
        return builder.build()
    }

    /** Shares the configured proxy-aware client with Media3 so HLS playlists and segments
     * follow the same proxy and authentication path as API and image requests. */
    @androidx.media3.common.util.UnstableApi
    fun media3DataSourceFactory(): androidx.media3.datasource.DataSource.Factory =
        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(safeHttpClient)
            .setUserAgent("PeerTubeTV")

    @Synchronized
    fun create(server: String): PeerTubeApi {
        val baseUrl = server.trimEnd('/') + "/"
        return peerTubeApis.getOrPut(baseUrl) { retrofit(baseUrl).create(PeerTubeApi::class.java) }
    }

    fun image(server: String, path: String?): String? {
        val value = path?.trim()?.takeIf(String::isNotBlank) ?: return null
        val base = runCatching { java.net.URI(server.trimEnd('/') + "/") }.getOrNull() ?: return null
        val candidate = when {
            value.startsWith("//") -> runCatching { java.net.URI("https:$value") }.getOrNull()
            else -> runCatching { base.resolve(value) }.getOrNull()
        } ?: return null
        if (candidate.scheme !in listOf("http", "https") || candidate.host.isNullOrBlank() || candidate.userInfo != null) return null
        // Android blocks cleartext image requests. Upgrade old PeerTube/CDN URLs to
        // HTTPS and let the fallback paths handle servers that don't publish them.
        val secure = if (candidate.scheme.equals("http", true)) {
            runCatching { java.net.URI("https", candidate.userInfo, candidate.host, candidate.port, candidate.path, candidate.query, candidate.fragment) }.getOrNull()
        } else candidate
        return secure?.toASCIIString()
    }
    fun directory(): InstanceDirectoryApi = directoryApi ?: synchronized(this) { directoryApi ?: retrofit("https://instances.joinpeertube.org/").create(InstanceDirectoryApi::class.java).also { directoryApi = it } }
    fun config(server: String): PeerTubeConfigApi = retrofit(server.trimEnd('/') + "/").create(PeerTubeConfigApi::class.java)
    fun sepiaSearch(): SepiaSearchApi = sepiaApi ?: synchronized(this) { sepiaApi ?: retrofit("https://sepiasearch.org/").create(SepiaSearchApi::class.java).also { sepiaApi = it } }
    fun peerSeek(): PeerSeekApi = peerSeekApi ?: synchronized(this) { peerSeekApi ?: retrofit("https://peerseek.video/").create(PeerSeekApi::class.java).also { peerSeekApi = it } }

    fun safeHttpGet(url: String, range: String? = null): okhttp3.Response {
        val uri = java.net.URI(url)
        require(uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null)
        require(SafeNetworkTarget.isSafeHttpsHost(uri.host)) { "Refusing to connect to a non-public address" }
        val request = okhttp3.Request.Builder().url(url).get()
            .header("Accept", "application/json, */*")
            .apply { if (range != null) header("Range", range) }
            .build()
        return safeHttpClient.newCall(request).execute()
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(safeHttpClient)
        .addConverterFactory(gsonConverter)
        .build()
}
