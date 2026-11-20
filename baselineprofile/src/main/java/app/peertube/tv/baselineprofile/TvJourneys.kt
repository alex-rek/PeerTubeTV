package app.peertube.tv.baselineprofile

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope

internal const val PACKAGE_NAME = "app.peertube.tv"

/** Presses a D-pad/remote key [times] times, letting the UI settle in between like a real remote. */
internal fun MacrobenchmarkScope.press(keyCode: Int, times: Int = 1, pauseMs: Long = 350) {
    repeat(times) {
        device.pressKeyCode(keyCode)
        device.waitForIdle()
        SystemClock.sleep(pauseMs)
    }
}

/** Waits for the home screen content that is loaded from the network after the first frame. */
internal fun MacrobenchmarkScope.waitForHomeContent() {
    device.waitForIdle()
    SystemClock.sleep(4_000)
}

/** Typical remote-control session: browse the home shelves, switch sections, open search and a video. */
internal fun MacrobenchmarkScope.browseLikeAViewer() {
    waitForHomeContent()
    // Home shelves: move into the first shelf and scroll it.
    press(KeyEvent.KEYCODE_DPAD_DOWN)
    press(KeyEvent.KEYCODE_DPAD_RIGHT, 4)
    press(KeyEvent.KEYCODE_DPAD_LEFT, 4)
    press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
    // Open the side menu and switch to the "popular" grid.
    press(KeyEvent.KEYCODE_DPAD_LEFT, 2)
    press(KeyEvent.KEYCODE_DPAD_DOWN)
    press(KeyEvent.KEYCODE_DPAD_CENTER)
    SystemClock.sleep(3_000)
    press(KeyEvent.KEYCODE_DPAD_DOWN, 3)
    press(KeyEvent.KEYCODE_DPAD_UP, 3)
    // Open a video, let playback start, and come back.
    press(KeyEvent.KEYCODE_DPAD_CENTER)
    SystemClock.sleep(6_000)
    press(KeyEvent.KEYCODE_BACK)
    SystemClock.sleep(2_000)
    // Open the search tab from the top bar.
    press(KeyEvent.KEYCODE_DPAD_UP, 4)
    press(KeyEvent.KEYCODE_DPAD_CENTER)
    SystemClock.sleep(1_500)
}
