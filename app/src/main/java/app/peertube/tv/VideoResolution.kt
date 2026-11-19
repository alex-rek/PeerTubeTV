package app.peertube.tv

import android.content.Context

/** Preference holding the default video height chosen in Settings → Player; 0 means automatic. */
internal const val DEFAULT_VIDEO_HEIGHT_KEY = "playerVideoProfile"

/** Heights offered as the default resolution, highest first. */
internal val DEFAULT_VIDEO_HEIGHT_OPTIONS = listOf(2160, 1440, 1080, 720, 480, 360, 240)

internal fun defaultVideoHeight(context: Context): Int =
    context.getSharedPreferences("peertube", Context.MODE_PRIVATE).getInt(DEFAULT_VIDEO_HEIGHT_KEY, 0).coerceAtLeast(0)

/**
 * Picks the file closest to [maxHeight] without exceeding it. Automatic ([maxHeight] <= 0) takes the
 * highest resolution; if every file is taller than the limit, the smallest one is used.
 */
internal fun pickVideoFile(files: Sequence<VideoFile>, maxHeight: Int): VideoFile? {
    val candidates = files.filter { !it.fileUrl.isNullOrBlank() }.toList()
    if (maxHeight <= 0) return candidates.maxByOrNull { it.resolution?.id ?: 0 }
    return candidates.filter { (it.resolution?.id ?: 0) <= maxHeight }.maxByOrNull { it.resolution?.id ?: 0 }
        ?: candidates.minByOrNull { it.resolution?.id ?: 0 }
}
