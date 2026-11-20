package app.peertube.tv

import androidx.annotation.StringRes

/*
 * Player options are stored in preferences and passed to PlayerActivity as intent extras by their
 * [key]. Parsing goes through fromKey() so unknown or outdated values fall back to one default.
 */

enum class AfterVideoAction(val key: String, @StringRes val label: Int) {
    FINISH("finish", R.string.after_video_finish),
    REPEAT("repeat", R.string.after_video_repeat),
    NEXT("next", R.string.after_video_next),
    NEXT_CHANNEL("next_channel", R.string.after_video_next_channel);

    /** Whether playback continues with the next item of the queue. */
    val continuesQueue: Boolean get() = this == NEXT || this == NEXT_CHANNEL

    companion object {
        fun fromKey(key: String?): AfterVideoAction = entries.firstOrNull { it.key == key } ?: FINISH
    }
}

enum class BufferProfile(val key: String, @StringRes val label: Int, val minBufferMs: Int, val maxBufferMs: Int) {
    SMALL("small", R.string.buffer_small, 15_000, 45_000),
    MEDIUM("medium", R.string.buffer_medium, 30_000, 120_000),
    LARGE("large", R.string.buffer_large, 60_000, 180_000),
    LARGEST("largest", R.string.buffer_largest, 120_000, 300_000);

    companion object {
        fun fromKey(key: String?): BufferProfile = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

enum class ClockPosition(val key: String, @StringRes val label: Int) {
    TOP_LEFT("top_left", R.string.player_clock_top_left),
    TOP_RIGHT("top_right", R.string.player_clock_top_right);

    companion object {
        fun fromKey(key: String?): ClockPosition = entries.firstOrNull { it.key == key } ?: TOP_RIGHT
    }
}

enum class ClockSize(val key: String, @StringRes val label: Int, val fontSizeSp: Int) {
    SMALL("small", R.string.player_clock_small, 18),
    MEDIUM("medium", R.string.player_clock_medium, 24),
    LARGE("large", R.string.player_clock_large, 32);

    companion object {
        fun fromKey(key: String?): ClockSize = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

enum class ClockBackdrop(val key: String, @StringRes val label: Int, val argb: Long) {
    NONE("none", R.string.player_clock_backdrop_none, 0x00000000),
    LIGHT("light", R.string.player_clock_backdrop_light, 0x55000000),
    MEDIUM("medium", R.string.player_clock_backdrop_medium, 0x99000000),
    STRONG("strong", R.string.player_clock_backdrop_strong, 0xDD000000);

    companion object {
        fun fromKey(key: String?): ClockBackdrop = entries.firstOrNull { it.key == key } ?: NONE
    }
}
