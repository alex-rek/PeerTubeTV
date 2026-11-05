package app.peertube.tv

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken

/** Gson still reads old persisted blobs; this adapter normalizes fields Gson can set to null via Unsafe. */
internal object PeerTubeGson {
    val instance: Gson = GsonBuilder().registerTypeAdapterFactory(object : TypeAdapterFactory {
        override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            val raw = type.rawType
            if (raw !in NORMALIZED_TYPES) return null
            val delegate = gson.getDelegateAdapter(this, type)
            return object : TypeAdapter<T>() {
                override fun read(input: com.google.gson.stream.JsonReader): T? =
                    delegate.read(input)?.let { normalize(it) as T }

                override fun write(output: com.google.gson.stream.JsonWriter, value: T?) = delegate.write(output, value)
            }
        }
    }).create()

    private val NORMALIZED_TYPES = setOf(
        Video::class.java, VideoPage::class.java, PeerTubeInstance::class.java, InstancePage::class.java,
        PeerTubeChannelPage::class.java, PeerSeekPage::class.java, CaptionPage::class.java,
        PlaybackQueueItem::class.java, DiscoveredServer::class.java, VideoSubscription::class.java
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> normalize(value: T): T = when (value) {
        is Video -> value.copy(
            uuid = (value.uuid as String?).orEmpty(),
            name = (value.name as String?).orEmpty(),
            description = (value.description as String?).orEmpty(),
            tags = (value.tags as List<String>?)?.filterNotNull(),
            files = (value.files as List<VideoFile>?)?.filterNotNull(),
            streamingPlaylists = (value.streamingPlaylists as List<StreamingPlaylist>?)?.filterNotNull()
        ) as T
        is VideoPage -> value.copy(data = (value.data as List<Video>?)?.filterNotNull().orEmpty()) as T
        is PeerTubeInstance -> value.copy(host = (value.host as String?).orEmpty(), languages = (value.languages as List<String>?)?.filterNotNull().orEmpty()) as T
        is InstancePage -> value.copy(data = (value.data as List<PeerTubeInstance>?)?.filterNotNull().orEmpty()) as T
        is PeerTubeChannelPage -> value.copy(data = (value.data as List<PeerTubeChannel>?)?.filterNotNull().orEmpty()) as T
        is PeerSeekPage -> value.copy(videos = (value.videos as List<PeerSeekVideo>?)?.filterNotNull().orEmpty()) as T
        is CaptionPage -> value.copy(data = (value.data as List<VideoCaption>?)?.filterNotNull().orEmpty()) as T
        is PlaybackQueueItem -> value.copy(
            uuid = (value.uuid as String?).orEmpty(), server = (value.server as String?).orEmpty(),
            title = (value.title as String?).orEmpty(), channelHost = (value.channelHost as String?).orEmpty()
        ) as T
        is DiscoveredServer -> value.copy(
            host = (value.host as String?).orEmpty(),
            languages = (value.languages as List<String>?)?.filterNotNull().orEmpty(),
            discoveredFrom = (value.discoveredFrom as Set<String>?)?.filterNotNull()?.toSet().orEmpty(),
            following = (value.following as Set<String>?)?.filterNotNull()?.toSet().orEmpty(),
            followers = (value.followers as Set<String>?)?.filterNotNull()?.toSet().orEmpty(),
            apiStatus = (value.apiStatus as String?).orEmpty().ifBlank { "unknown" },
            streamStatus = (value.streamStatus as String?).orEmpty().ifBlank { "unknown" },
            metadataStatus = (value.metadataStatus as String?).orEmpty().ifBlank { "unknown" }
        ) as T
        is VideoSubscription -> value.copy(
            name = (value.name as String?).orEmpty(), host = (value.host as String?).orEmpty(),
            displayName = (value.displayName as String?).orEmpty()
        ) as T
        else -> value
    }
}
