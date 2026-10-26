package app.peertube.tv

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
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
data class Video(val uuid: String, val name: String, val description: String? = "", val thumbnailPath: String? = null, val thumbnailUrl: String? = null, val views: Long = 0, val duration: Long = 0, val tags: List<String>? = null, val channel: Channel? = null, val account: Account? = null, val host: String? = null, val url: String? = null, val files: List<VideoFile>? = null, val streamingPlaylists: List<StreamingPlaylist>? = null, val publishedAt: String? = null, val isLive: Boolean? = null, val state: VideoState? = null) {
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

interface PeerTubeApi {
    @GET("api/v1/videos") suspend fun videos(@Query("sort") sort: String = "-publishedAt", @Query("count") count: Int = 30, @Query("search") search: String? = null, @Query("isLive") isLive: Boolean? = null, @Query("includeScheduledLive") includeScheduledLive: Boolean? = null, @Query("languageOneOf[]") languageOneOf: String? = null): VideoPage
    @GET("api/v1/video-channels") suspend fun videoChannels(@Query("start") start: Int = 0, @Query("count") count: Int = 100): PeerTubeChannelPage
    @GET("api/v1/video-channels/{handle}/videos") suspend fun channelVideos(@Path(value = "handle", encoded = true) handle: String, @Query("count") count: Int = 100): VideoPage
    @GET("api/v1/videos/{id}") suspend fun video(@Path("id") id: String): Video
    @GET("api/v1/videos/{id}/live-session") suspend fun liveSession(@Path("id") id: String): LiveVideoSession
    @GET("api/v1/videos/{id}/captions") suspend fun captions(@Path("id") id: String): CaptionPage
}

data class InstancePage(val total: Int = 0, val data: List<PeerTubeInstance> = emptyList())
data class PeerTubeChannelPage(val total: Int = 0, val data: List<PeerTubeChannel> = emptyList())
data class PeerTubeChannel(val id: Int? = null, val name: String? = null, val displayName: String? = null, val host: String? = null, val videosCount: Long? = null)
data class PeerTubeInstance(val host: String, val name: String? = null, val signupAllowed: Boolean? = null, val totalVideos: Long? = null, val health: Int? = null, val languages: List<String> = emptyList())
data class PeerTubeCustomConfig(val instance: PeerTubeInstanceConfig? = null)
data class PeerTubeInstanceConfig(val languages: List<String> = emptyList())

interface InstanceDirectoryApi {
    @GET("api/v1/instances") suspend fun instances(@Query("start") start: Int = 0, @Query("count") count: Int = 100): InstancePage
}

interface PeerTubeConfigApi {
    @GET("api/v1/config/custom") suspend fun customConfig(): PeerTubeCustomConfig
}

interface SepiaSearchApi {
    @GET("api/v1/search/videos") suspend fun videos(@Query("search") search: String? = null, @Query("start") start: Int = 0, @Query("count") count: Int = 30, @Query("sort") sort: String? = null, @Query("categoryOneOf") categoryOneOf: Int? = null, @Query("durationMin") durationMin: Int? = null, @Query("durationMax") durationMax: Int? = null, @Query("isLive") isLive: Boolean? = null, @Query("includeScheduledLive") includeScheduledLive: Boolean? = null, @Query("languageOneOf[]") languageOneOf: String? = null): VideoPage
}

data class PeerSeekPage(val videos: List<PeerSeekVideo> = emptyList(), val total: Int = 0)
data class PeerSeekVideo(
    val id: String? = null, val uuid: String? = null, val title: String? = null, val name: String? = null,
    val description: String? = null, val thumbnailUrl: String? = null, val videoUrl: String? = null,
    val isLive: Boolean? = null, val views: Long? = null, val durationSeconds: Long? = null,
    val publishedAt: String? = null, val instance: com.google.gson.JsonElement? = null,
    val channel: com.google.gson.JsonElement? = null, val account: com.google.gson.JsonElement? = null, val tags: List<String>? = null
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
        val host = (field(instance, "host", "name", "url") ?: instance?.takeIf { it.isJsonPrimitive }?.asString)
            ?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')
        val channelName = field(channel, "name", "displayName", "handle")
        val channelDisplayName = field(channel, "displayName", "name")
        val channelHost = field(channel, "host") ?: host
        val accountName = field(account, "name", "displayName", "handle")
        val accountDisplayName = field(account, "displayName", "name")
        val accountHost = field(account, "host") ?: host
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
            isLive = isLive
        )
    }
}
interface PeerSeekApi {
    @GET("v1/search") suspend fun search(@Query("q") query: String, @Query("sort") sort: String = "relevance", @Query("language") language: String? = null, @Query("page") page: Int = 1, @Query("size") size: Int = 40, @Query("include_live") includeLive: Boolean = true): PeerSeekPage
}

object ApiFactory {
    private val gsonConverter = GsonConverterFactory.create()
    private val peerTubeApis = object : LinkedHashMap<String, PeerTubeApi>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PeerTubeApi>?): Boolean = size > 32
    }
    private val directoryApi: InstanceDirectoryApi by lazy { retrofit("https://instances.joinpeertube.org/").create(InstanceDirectoryApi::class.java) }
    private val sepiaApi: SepiaSearchApi by lazy { retrofit("https://sepiasearch.org/").create(SepiaSearchApi::class.java) }
    private val peerSeekApi: PeerSeekApi by lazy { retrofit("https://peerseek.video/").create(PeerSeekApi::class.java) }

    @Synchronized
    fun create(server: String): PeerTubeApi {
        val baseUrl = server.trimEnd('/') + "/"
        return peerTubeApis.getOrPut(baseUrl) { retrofit(baseUrl).create(PeerTubeApi::class.java) }
    }

    fun image(server: String, path: String?) = path?.let { if (it.startsWith("http")) it else server.trimEnd('/') + it }
    fun directory(): InstanceDirectoryApi = directoryApi
    fun config(server: String): PeerTubeConfigApi = retrofit(server.trimEnd('/') + "/").create(PeerTubeConfigApi::class.java)
    fun sepiaSearch(): SepiaSearchApi = sepiaApi
    fun peerSeek(): PeerSeekApi = peerSeekApi

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(gsonConverter)
        .build()
}
