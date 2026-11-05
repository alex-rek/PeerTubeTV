package app.peertube.tv

import android.content.Context
import com.google.gson.reflect.TypeToken
import java.util.LinkedHashMap
import java.util.LinkedHashSet

internal class BoundedProgressMap(private val capacity: Int = 500) {
    private val positions = LinkedHashMap<String, Long>()
    fun load(values: Map<String, Long>) {
        positions.clear()
        values.forEach { (uuid, position) -> put(uuid, position) }
    }
    fun put(uuid: String, positionMs: Long) {
        if (uuid.isBlank()) return
        positions.remove(uuid)
        positions[uuid] = positionMs.coerceAtLeast(0L)
        while (positions.size > capacity) positions.remove(positions.keys.first())
    }
    operator fun get(uuid: String): Long? = positions[uuid]
    fun snapshot(): Map<String, Long> = LinkedHashMap(positions)
}

internal class BoundedWatchedIds(private val capacity: Int = 1_000) {
    private val ids = LinkedHashSet<String>()
    fun load(values: Iterable<String>) {
        ids.clear()
        values.forEach(::add)
    }
    fun add(uuid: String): Boolean {
        if (uuid.isBlank()) return false
        val added = ids.add(uuid)
        while (ids.size > capacity) ids.remove(ids.first())
        return added
    }
    operator fun contains(uuid: String): Boolean = uuid in ids
    fun snapshot(): List<String> = ids.toList()
}

/** In-memory progress cache; disk serialization happens on a 30-second cadence or lifecycle flush. */
internal object PlaybackStateStore {
    private const val PROGRESS_KEY = "watchProgressMs"
    private const val WATCHED_KEY = "watchedVideoIds"
    private const val FLUSH_INTERVAL_MS = 30_000L
    private val progress = BoundedProgressMap()
    private val watched = BoundedWatchedIds()
    private var loaded = false
    private var lastFlushAt = 0L
    private var dirtyProgress = false
    private var dirtyWatched = false

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences("peertube", Context.MODE_PRIVATE)
        val progressType = object : TypeToken<Map<String, Long>>() {}.type
        val watchedType = object : TypeToken<List<String>>() {}.type
        progress.load(runCatching { PeerTubeGson.instance.fromJson<Map<String, Long>>(prefs.getString(PROGRESS_KEY, "{}"), progressType).orEmpty() }.getOrDefault(emptyMap()))
        watched.load(runCatching { PeerTubeGson.instance.fromJson<List<String>>(prefs.getString(WATCHED_KEY, "[]"), watchedType).orEmpty() }.getOrDefault(emptyList()))
        loaded = true
    }

    @Synchronized
    fun progress(context: Context, uuid: String): Long {
        load(context)
        return progress[uuid]?.coerceAtLeast(0L) ?: 0L
    }

    @Synchronized
    fun progressSnapshot(context: Context): Map<String, Long> {
        load(context)
        return progress.snapshot()
    }

    @Synchronized
    fun updateProgress(context: Context, uuid: String, positionMs: Long, flushNow: Boolean = false) {
        load(context)
        progress.put(uuid, positionMs)
        dirtyProgress = true
        if (flushNow || System.currentTimeMillis() - lastFlushAt >= FLUSH_INTERVAL_MS) flushLocked(context)
    }

    @Synchronized
    fun addWatched(context: Context, uuid: String): Boolean {
        load(context)
        if (!watched.add(uuid)) return false
        dirtyWatched = true
        flushLocked(context)
        return true
    }

    @Synchronized
    fun watchedSnapshot(context: Context): List<String> {
        load(context)
        return watched.snapshot()
    }

    @Synchronized
    fun flush(context: Context) {
        load(context)
        flushLocked(context)
    }

    private fun flushLocked(context: Context) {
        if (!dirtyProgress && !dirtyWatched) return
        val editor = context.applicationContext.getSharedPreferences("peertube", Context.MODE_PRIVATE).edit()
        if (dirtyProgress) editor.putString(PROGRESS_KEY, PeerTubeGson.instance.toJson(progress.snapshot()))
        if (dirtyWatched) editor.putString(WATCHED_KEY, PeerTubeGson.instance.toJson(watched.snapshot()))
        editor.apply()
        dirtyProgress = false
        dirtyWatched = false
        lastFlushAt = System.currentTimeMillis()
    }
}

internal fun Video.compactForStorage(): Video = copy(description = "", files = null, streamingPlaylists = null)
