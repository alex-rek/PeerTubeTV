package app.peertube.tv

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import java.util.Locale
import java.text.SimpleDateFormat

@Composable internal fun SearchGlyph(glyphSize: androidx.compose.ui.unit.Dp = 26.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val canvasSize = this.size
        drawCircle(color = Color.White, radius = canvasSize.minDimension * 0.29f, center = Offset(canvasSize.width * 0.41f, canvasSize.height * 0.41f), style = Stroke(width = 2.5.dp.toPx()))
        drawLine(color = Color.White, start = Offset(canvasSize.width * 0.62f, canvasSize.height * 0.62f), end = Offset(canvasSize.width * 0.91f, canvasSize.height * 0.91f), strokeWidth = 3.dp.toPx())
    }
}

@Composable internal fun LocalDateTime(is24Hour: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val locale = currentLocale()
    Text(java.text.SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", locale).format(java.util.Date(now)), style = MaterialTheme.typography.headlineSmall)
    Text(java.text.SimpleDateFormat("EEE, d MMM", locale).format(java.util.Date(now)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The UI locale as an observable composition value, so text recomposes when the app language changes. */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale =
    androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current)[0] ?: Locale.ROOT

@Composable internal fun SearchFilter(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 34.dp),
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
        ) { Text("$label: $selected", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable internal fun VideoShelf(
    title: String,
    videos: List<Video>,
    server: String,
    watchProgressMs: Map<String, Long>,
    tickerSpeed: Float,
    onOpen: (Video) -> Unit,
    isFavorite: (Video) -> Boolean,
    isSubscribed: (Video) -> Boolean,
    onFavorite: (Video) -> Unit,
    onSubscription: (Video) -> Unit,
    onChannel: (Video) -> Unit,
    setContextMenuTarget: (String, (() -> Unit)?, (() -> Unit)?, Boolean) -> Unit,
    contextMenuHoldSeconds: Float,
    titleLines: Int = 2,
    downFocusRequester: FocusRequester? = null,
    downFocusRequesters: List<FocusRequester?>? = null,
    itemFocusRequesters: List<FocusRequester>? = null,
    firstCardFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    preferredFocusRequester: FocusRequester? = null,
    preferredFocusVideoId: String? = null,
    restoreFocusPending: Boolean = false,
    onExcludeRecommendation: (Video) -> Unit = {},
    onMarkWatched: (Video) -> Unit = {},
    onVideoFocus: (Int) -> Unit = {}
) {
    val shelfState = rememberLazyListState()
    LaunchedEffect(shelfState, preferredFocusVideoId, videos, restoreFocusPending) {
        val targetIndex = preferredFocusVideoId?.let { id -> videos.indexOfFirst { it.uuid == id } } ?: -1
        if (restoreFocusPending && targetIndex >= 0) runCatching { shelfState.scrollToItem(targetIndex) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyRow(state = shelfState, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            itemsIndexed(videos, key = { _, video -> video.uuid }) { index, video ->
                var focused by remember { mutableStateOf(false) }
                var contextMenu by remember { mutableStateOf(false) }
                var suppressOpenAfterMenu by remember { mutableStateOf(false) }
                val showContextMenu: () -> Unit = { suppressOpenAfterMenu = true; contextMenu = true }
                val dismissContextMenu: () -> Unit = { contextMenu = false; suppressOpenAfterMenu = false }
                val channel = video.channel?.name ?: video.account?.name ?: video.channel?.displayName ?: video.account?.displayName.orEmpty()
                Box {
                    Card(
                        onClick = { if (suppressOpenAfterMenu) suppressOpenAfterMenu = false else onOpen(video) },
                        modifier = Modifier.width(250.dp)
                            .then(itemFocusRequesters?.getOrNull(index)?.let { Modifier.focusRequester(it) } ?: Modifier)
                            .then(if (video.uuid == videos.firstOrNull()?.uuid && firstCardFocusRequester != null) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                            .then(if (video.uuid == preferredFocusVideoId && preferredFocusRequester != null) Modifier.focusRequester(preferredFocusRequester) else Modifier)
                            .then(if (downFocusRequester != null || downFocusRequesters?.getOrNull(index) != null || upFocusRequester != null) Modifier.focusProperties {
                                val targetDown = downFocusRequesters?.getOrNull(index) ?: downFocusRequester
                                if (targetDown != null) down = targetDown
                                if (upFocusRequester != null) up = upFocusRequester
                            } else Modifier)
                            .scale(if (focused) 1.05f else 1f)
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) {
                                    onVideoFocus(index)
                                    setContextMenuTarget(video.uuid, showContextMenu, { onOpen(video) }, index == 0)
                                }
                                else setContextMenuTarget(video.uuid, null, null, false)
                            }
                            .remoteCenterPress({ onOpen(video) }, showContextMenu, contextMenuHoldSeconds),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (focused) LocalPeerIcheTheme.current.focusedVideoBackground else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (focused) LocalPeerIcheTheme.current.focusedVideoForeground else MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, focusedElevation = if (focused) 8.dp else 1.dp)
                    ) {
                        Column {
                            val progress = if (video.duration > 0) ((watchProgressMs[video.uuid] ?: 0L).toFloat() / (video.duration * 1000f)).coerceIn(0f, 1f) else 0f
                            VideoThumbnail(video, server, Modifier.fillMaxWidth().aspectRatio(16f / 9f), progress)
                Column(Modifier.fillMaxWidth().height((titleLines * 23 + 58).dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                TranslatedVideoMetadataText(
                                    video.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = titleLines,
                                    minLines = titleLines,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = if (focused) Modifier.basicMarquee(velocity = 30.dp * tickerSpeed) else Modifier
                                )
                                Text(video.channel?.displayName ?: video.account?.displayName ?: stringResource(R.string.channel_unknown), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                val date = formatPublishedDate(video.publishedAt) ?: stringResource(R.string.shelf_date_unknown)
                                Text(stringResource(R.string.shelf_video_stats, video.views.toString(), date), style = MaterialTheme.typography.bodySmall, color = if (focused) LocalPeerIcheTheme.current.focusedVideoForeground else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    if (contextMenu) VideoContextMenu(video.name, isSubscribed(video), isFavorite(video), dismissContextMenu, { dismissContextMenu(); onOpen(video) }, { dismissContextMenu(); onSubscription(video) }, { dismissContextMenu(); onFavorite(video) }, { dismissContextMenu(); onChannel(video) }, { dismissContextMenu(); onExcludeRecommendation(video) }, { dismissContextMenu(); onMarkWatched(video) })
                }
            }
        }
    }
}
