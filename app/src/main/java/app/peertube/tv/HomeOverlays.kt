package app.peertube.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeOverlays(m: HomeModel, ui: HomeUiState, actions: HomeActions) {
    val context = LocalContext.current
    val periodLabel = when (ui.historyPeriodHours) { 2 -> stringResource(R.string.history_period_2); 3 -> stringResource(R.string.history_period_3); 6 -> stringResource(R.string.history_period_6); 24 -> stringResource(R.string.history_period_24); else -> stringResource(R.string.history_period_1) }
    if (ui.settingsOverlay && ui.settingsCategory != null) {
        Dialog(
            onDismissRequest = { ui.settingsOverlay = false; ui.settingsCategory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val panelOffset = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                panelOffset.animateTo(0f, animationSpec = tween(durationMillis = 260))
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.30f).graphicsLayer {
                        translationX = size.width * panelOffset.value
                    },
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(0.dp)
                ) {
                    SettingsDashboard(
                        model = m,
                        selectedCategory = ui.settingsCategory,
                        selectedSubpage = ui.settingsSubpage,
                        requestInitialFocus = !ui.menuExpanded,
                        onSelectCategory = { category ->
                            ui.settingsCategory = category
                            if (category == null) ui.settingsSubpage = null
                            ui.settingsOverlay = category != null
                        },
                        onSelectSubpage = { ui.settingsSubpage = it },
                        historyPeriodHours = ui.historyPeriodHours,
                        onHistoryPeriodChange = { ui.historyPeriodHours = it },
                        onClearHistory = { ui.clearHistoryDialog = true },
                        onClearHistoryPeriod = { ui.clearHistoryPeriodDialog = true },
                        onExportBackup = actions.exportBackup,
                        onImportBackup = actions.launchBackupRestore,
                        onCheckForUpdates = { (context as? MainActivity)?.checkForUpdates() }
                    )
                }
            }
        }
    }
    if (ui.clearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { ui.clearHistoryDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { m.clearHistory(); ui.clearHistoryDialog = false }) {
                    Text(stringResource(R.string.history_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { ui.clearHistoryDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (ui.clearHistoryPeriodDialog) {
        AlertDialog(
            onDismissRequest = { ui.clearHistoryPeriodDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_period_confirm, periodLabel)) },
            confirmButton = {
                TextButton(onClick = { m.clearHistoryOlderThan(ui.historyPeriodHours); ui.clearHistoryPeriodDialog = false }) { Text(stringResource(R.string.history_clear_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { ui.clearHistoryPeriodDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
