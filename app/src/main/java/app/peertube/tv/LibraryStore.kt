package app.peertube.tv

import android.content.Context
import androidx.core.content.edit
import com.google.gson.reflect.TypeToken

/**
 * Single owner of the locally stored favorites and channel subscriptions.
 *
 * The home screen and the player both change these lists. Every change is a read-modify-write of the
 * stored list under one lock, so an older in-memory copy can never overwrite a newer change.
 */
internal object LibraryStore {
    private const val FAVORITES_KEY = "favorites"
    private const val SUBSCRIPTIONS_KEY = "subscriptions"
    private const val MAX_ITEMS = 1_000
    private val lock = Any()
    private val videoListType = object : TypeToken<List<Video>>() {}.type
    private val subscriptionListType = object : TypeToken<List<VideoSubscription>>() {}.type

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences("peertube", Context.MODE_PRIVATE)

    fun favorites(context: Context): List<Video> = synchronized(lock) { readFavorites(context) }

    fun subscriptions(context: Context): List<VideoSubscription> = synchronized(lock) { readSubscriptions(context) }

    fun updateFavorites(context: Context, transform: (List<Video>) -> List<Video>): List<Video> = synchronized(lock) {
        val updated = transform(readFavorites(context)).distinctBy { it.uuid }.takeLast(MAX_ITEMS)
        preferences(context).edit { putString(FAVORITES_KEY, PeerTubeGson.instance.toJson(updated.map(Video::compactForStorage))) }
        updated
    }

    fun updateSubscriptions(context: Context, transform: (List<VideoSubscription>) -> List<VideoSubscription>): List<VideoSubscription> = synchronized(lock) {
        val updated = transform(readSubscriptions(context)).distinctBy { it.key() }.takeLast(MAX_ITEMS)
        preferences(context).edit { putString(SUBSCRIPTIONS_KEY, PeerTubeGson.instance.toJson(updated)) }
        updated
    }

    /** Adds or removes [video]; returns whether it is a favorite afterwards. */
    fun toggleFavorite(context: Context, video: Video): Boolean =
        updateFavorites(context) { current ->
            if (current.any { it.uuid == video.uuid }) current.filterNot { it.uuid == video.uuid } else current + video
        }.any { it.uuid == video.uuid }

    fun setSubscribed(context: Context, channel: VideoSubscription, subscribed: Boolean): List<VideoSubscription> =
        updateSubscriptions(context) { current ->
            val others = current.filterNot { it.key() == channel.key() }
            if (subscribed) others + (current.firstOrNull { it.key() == channel.key() } ?: channel) else others
        }

    private fun VideoSubscription.key() = "$name@$host".lowercase(java.util.Locale.ROOT)

    private fun readFavorites(context: Context): List<Video> = runCatching {
        PeerTubeGson.instance.fromJson<List<Video>>(preferences(context).getString(FAVORITES_KEY, "[]"), videoListType).orEmpty()
    }.onFailure { android.util.Log.w("PeerIcheTV", "Stored favorites could not be read", it) }.getOrDefault(emptyList())

    private fun readSubscriptions(context: Context): List<VideoSubscription> = runCatching {
        PeerTubeGson.instance.fromJson<List<VideoSubscription>>(preferences(context).getString(SUBSCRIPTIONS_KEY, "[]"), subscriptionListType)
            .orEmpty().mapNotNull { it?.let(::normalizeVideoSubscription) }
    }.onFailure { android.util.Log.w("PeerIcheTV", "Stored subscriptions could not be read", it) }.getOrDefault(emptyList())
}
