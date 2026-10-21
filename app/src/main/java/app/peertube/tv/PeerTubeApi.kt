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
    val channelDisplayName: String? = null
)
data class LiveVideoSession(val startDate: String? = null, val endDate: String? = null)
data class CaptionPage(val data: List<VideoCaption> = emptyList())
data class VideoCaption(val language: CaptionLanguage? = null, val fileUrl: String? = null, val captionPath: String? = null, val automaticallyGenerated: Boolean? = null)
data class CaptionLanguage(val id: String? = null, val label: String? = null)
data class Video(val uuid: String, val name: String, val description: String? = "", val thumbnailPath: String? = null, val thumbnailUrl: String? = null, val views: Long = 0, val duration: Long = 0, val tags: List<String>? = null, val channel: Channel? = null, val account: Account? = null, val host: String? = null, val url: String? = null, val files: List<VideoFile>? = null, val streamingPlaylists: List<StreamingPlaylist>? = null, val publishedAt: String? = null, val isLive: Boolean? = null) {
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

object ApiFactory {
    private val gsonConverter = GsonConverterFactory.create()
    private val peerTubeApis = object : LinkedHashMap<String, PeerTubeApi>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PeerTubeApi>?): Boolean = size > 32
    }
    private val directoryApi: InstanceDirectoryApi by lazy { retrofit("https://instances.joinpeertube.org/").create(InstanceDirectoryApi::class.java) }
    private val sepiaApi: SepiaSearchApi by lazy { retrofit("https://sepiasearch.org/").create(SepiaSearchApi::class.java) }

    @Synchronized
    fun create(server: String): PeerTubeApi {
        val baseUrl = server.trimEnd('/') + "/"
        return peerTubeApis.getOrPut(baseUrl) { retrofit(baseUrl).create(PeerTubeApi::class.java) }
    }

    fun image(server: String, path: String?) = path?.let { if (it.startsWith("http")) it else server.trimEnd('/') + it }
    fun directory(): InstanceDirectoryApi = directoryApi
    fun config(server: String): PeerTubeConfigApi = retrofit(server.trimEnd('/') + "/").create(PeerTubeConfigApi::class.java)
    fun sepiaSearch(): SepiaSearchApi = sepiaApi

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(gsonConverter)
        .build()
}
