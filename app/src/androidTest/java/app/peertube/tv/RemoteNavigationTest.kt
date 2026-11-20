package app.peertube.tv

import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the home screen with D-pad key events, as a TV remote would. Runs on a connected Android TV
 * device: `./gradlew :app:connectedDebugAndroidTest` (reinstalls the app, which clears its data).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RemoteNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun press(vararg keyCodes: Int) {
        keyCodes.forEach {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(it)
            compose.waitForIdle()
            Thread.sleep(300)
        }
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private fun awaitHome() {
        // Initial focus is restored on the first card after a short delay.
        compose.waitForIdle()
        Thread.sleep(1_500)
    }

    @Test
    fun leftOpensSideMenuAndSelectingASectionClosesIt() {
        awaitHome()
        press(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.nav_home)).fetchSemanticsNodes().isNotEmpty() }

        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.nav_popular)), 5_000)
        compose.waitUntilDoesNotExist(hasText(text(R.string.nav_home)), 5_000)
    }

    @Test
    fun upFromContentReachesSearchButtonThatOpensSearch() {
        awaitHome()
        press(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER)

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.search_peertube)), 5_000)
        compose.onNodeWithText(text(R.string.search_sort), substring = true).assertExists()
    }
}
