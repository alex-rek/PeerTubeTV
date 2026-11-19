package app.peertube.tv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

internal fun Modifier.remoteCenterPress(onShortPress: () -> Unit, onLongPress: () -> Unit, holdSeconds: Float): Modifier = composed {
    val scope = rememberCoroutineScope()
    var centerDown by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var pressJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { pressJob?.cancel() } }
    onPreviewKeyEvent { event ->
        val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!isCenter) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (!centerDown) {
                    centerDown = true
                    longPressFired = false
                    pressJob = scope.launch {
                        delay((holdSeconds * 1_000L).toLong())
                        if (centerDown) {
                            longPressFired = true
                            onLongPress()
                            // The menu takes focus, so the matching key-up may be delivered there.
                            delay(4_000L)
                            centerDown = false
                            longPressFired = false
                            pressJob = null
                        }
                    }
                }
                true
            }
            KeyEventType.KeyUp -> {
                val wasPressed = centerDown
                pressJob?.cancel()
                pressJob = null
                val wasLongPress = longPressFired
                centerDown = false
                longPressFired = false
                if (wasPressed && !wasLongPress) onShortPress()
                true
            }
            else -> true
        }
    }
}

internal fun isTelevisionDevice(context: android.content.Context): Boolean =
    context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
