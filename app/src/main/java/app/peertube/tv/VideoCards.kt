package app.peertube.tv

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.util.Locale
import java.text.DateFormat
import java.text.SimpleDateFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun VideoListRow(video: Video, server: String, progress: Float, favorite: Boolean, subscribed: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit, onSubscription: () -> Unit, onChannel: () -> Unit, setContextMenuTarget: (String, (() -> Unit)?, (() -> Unit)?, Boolean) -> Unit, holdSeconds: Float, statusLabel: String? = null, onExcludeRecommendation: () -> Unit = {}, onMarkWatched: () -> Unit = {}, firstCardFocusRequester: FocusRequester? = null, upFocusRequester: FocusRequester? = null, preferredFocusRequester: FocusRequester? = null, preferredFocusVideoId: String? = null, isAtLeftBoundary: Boolean = true) {
    var contextMenu by remember { mutableStateOf(false) }
    var suppressOpenAfterMenu by remember { mutableStateOf(false) }
    val showContextMenu: () -> Unit = { suppressOpenAfterMenu = true; contextMenu = true }
    val dismissContextMenu: () -> Unit = { contextMenu = false; suppressOpenAfterMenu = false }
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().then(if (firstCardFocusRequester != null) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
            .then(if (preferredFocusRequester != null && video.uuid == preferredFocusVideoId) Modifier.focusRequester(preferredFocusRequester) else Modifier)
            .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
            .onFocusChanged { state -> if (state.isFocused) setContextMenuTarget(video.uuid, showContextMenu, onOpen, isAtLeftBoundary) else setContextMenuTarget(video.uuid, null, null, false) }
            .remoteCenterPress(onOpen, showContextMenu, holdSeconds)
            .combinedClickable(onClick = { if (suppressOpenAfterMenu) suppressOpenAfterMenu = false else onOpen() }, onLongClick = showContextMenu).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(video, server, Modifier.width(180.dp).height(102.dp), progress)
                Column(Modifier.weight(1f).padding(start = 14.dp, end = 54.dp)) {
                if (statusLabel.isNullOrBlank()) {
                    TranslatedVideoMetadataText(video.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                } else {
                    Text(stringResource(R.string.live_title_with_status, video.name, statusLabel), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("${video.channel?.displayName ?: stringResource(R.string.channel_unknown)} · ${stringResource(R.string.video_views, video.views.toString())}", maxLines = 1)
                PublishedDate(video.publishedAt)
            }
        }
        if (contextMenu) VideoContextMenu(video.name, subscribed, favorite, dismissContextMenu, { dismissContextMenu(); onOpen() }, { dismissContextMenu(); onSubscription() }, { dismissContextMenu(); onFavorite() }, { dismissContextMenu(); onChannel() }, { dismissContextMenu(); onExcludeRecommendation() }, { dismissContextMenu(); onMarkWatched() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun VideoGridCard(video: Video, server: String, progress: Float, favorite: Boolean, subscribed: Boolean, titleLines: Int, tickerSpeed: Float, onOpen: () -> Unit, onFavorite: () -> Unit, onSubscription: () -> Unit, onChannel: (String) -> Unit, setContextMenuTarget: (String, (() -> Unit)?, (() -> Unit)?, Boolean) -> Unit, holdSeconds: Float, onExcludeRecommendation: () -> Unit = {}, onMarkWatched: () -> Unit = {}, firstCardFocusRequester: FocusRequester? = null, upFocusRequester: FocusRequester? = null, preferredFocusRequester: FocusRequester? = null, preferredFocusVideoId: String? = null, isAtLeftBoundary: Boolean = false, onCardFocus: (Boolean) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    var contextMenu by remember { mutableStateOf(false) }
    var suppressOpenAfterMenu by remember { mutableStateOf(false) }
    val showContextMenu: () -> Unit = { suppressOpenAfterMenu = true; contextMenu = true }
    val dismissContextMenu: () -> Unit = { contextMenu = false; suppressOpenAfterMenu = false }
    val channel = video.channel?.name ?: video.account?.name ?: video.channel?.displayName ?: video.account?.displayName ?: ""
    Box {
        Card(
            modifier = Modifier.fillMaxWidth().then(if (firstCardFocusRequester != null) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                .then(if (preferredFocusRequester != null && video.uuid == preferredFocusVideoId) Modifier.focusRequester(preferredFocusRequester) else Modifier)
                .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
                .zIndex(if (focused) 1f else 0f)
                .onFocusChanged {
                    focused = it.isFocused
                    onCardFocus(it.isFocused)
                    if (it.isFocused) setContextMenuTarget(video.uuid, showContextMenu, onOpen, isAtLeftBoundary) else setContextMenuTarget(video.uuid, null, null, false)
                }
                .remoteCenterPress(onOpen, showContextMenu, holdSeconds)
                .combinedClickable(onClick = { if (suppressOpenAfterMenu) suppressOpenAfterMenu = false else onOpen() }, onLongClick = showContextMenu),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (focused) LocalPeerIcheTheme.current.focusedVideoBackground else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (focused) LocalPeerIcheTheme.current.focusedVideoForeground else MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, focusedElevation = if (focused) 8.dp else 1.dp)
        ) {
            Column {
                VideoThumbnail(video, server, Modifier.fillMaxWidth().aspectRatio(16f / 9f), progress)
                Column(Modifier.fillMaxWidth().height((titleLines * 23 + 80).dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    TranslatedVideoMetadataText(video.name, style = MaterialTheme.typography.titleMedium, maxLines = titleLines, minLines = titleLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = if (focused) Modifier.basicMarquee(velocity = 30.dp * tickerSpeed) else Modifier)
                    Text(video.channel?.displayName ?: video.account?.displayName ?: stringResource(R.string.channel_unknown), style = MaterialTheme.typography.bodySmall, color = if (focused) LocalPeerIcheTheme.current.focusedVideoForeground else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(stringResource(R.string.video_views, video.views.toString()), style = MaterialTheme.typography.bodySmall, color = if (focused) LocalPeerIcheTheme.current.focusedVideoForeground else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    PublishedDate(video.publishedAt, if (focused) LocalPeerIcheTheme.current.focusedVideoForeground else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (contextMenu) VideoContextMenu(video.name, subscribed, favorite, dismissContextMenu, { dismissContextMenu(); onOpen() }, { dismissContextMenu(); onSubscription() }, { dismissContextMenu(); onFavorite() }, { dismissContextMenu(); if (channel.isNotBlank()) onChannel(channel) }, { dismissContextMenu(); onExcludeRecommendation() }, { dismissContextMenu(); onMarkWatched() })
    }
}

@Composable
internal fun VideoContextMenu(
    title: String,
    subscribed: Boolean,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onSubscription: () -> Unit,
    onFavorite: () -> Unit,
    onChannel: () -> Unit,
    onExcludeRecommendation: () -> Unit = {},
    onMarkWatched: () -> Unit = {}
) {
    val initialMenuFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { initialMenuFocus.requestFocus() } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val panelOffset = remember { Animatable(1f) }
        LaunchedEffect(Unit) { panelOffset.animateTo(0f, animationSpec = tween(durationMillis = 220)) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)), contentAlignment = Alignment.CenterEnd) {
            Surface(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.30f).graphicsLayer {
                    translationX = size.width * panelOffset.value
                },
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).focusRequester(initialMenuFocus).focusable(), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        TextButton(onClick = onDismiss) { Text("‹") }
                    }
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.video_play), modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = onSubscription, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (subscribed) R.string.unsubscribe else R.string.subscribe), modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = onFavorite, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite), modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = onChannel, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_channel), modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = onExcludeRecommendation, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.exclude_recommendation), modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = onMarkWatched, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.mark_watched), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun VideoThumbnail(video: Video, server: String, modifier: Modifier = Modifier, progress: Float = 0f) {
    val context = LocalContext.current
    val thumbnailCandidates = remember(video.uuid, server, video.thumbnailUrl, video.thumbnailPath, video.previewPath) {
        val origin = video.sourceServer(server)
        val imageServers = listOf(server, origin).distinct()
        listOfNotNull(
            ApiFactory.image(origin, video.thumbnailUrl),
            imageServers.firstNotNullOfOrNull { ApiFactory.image(it, video.thumbnailPath) },
            imageServers.firstNotNullOfOrNull { ApiFactory.image(it, video.previewPath) },
            // Some older or partially compatible instances omit thumbnailPath in
            // search results even though their standard PeerTube thumbnail exists.
            ApiFactory.image(origin, "lazy-static/thumbnails/${video.uuid}.jpg"),
            ApiFactory.image(origin, "static/thumbnails/${video.uuid}.jpg"),
            ApiFactory.image(origin, "lazy-static/previews/${video.uuid}.jpg"),
            ApiFactory.image(origin, "static/previews/${video.uuid}.jpg")
        ).distinct()
    }
    var thumbnailCandidateIndex by remember(video.uuid, server) { mutableIntStateOf(0) }
    LaunchedEffect(thumbnailCandidates) { thumbnailCandidateIndex = 0 }
    val sensitivePrefs = remember(context) { context.getSharedPreferences("peertube", Activity.MODE_PRIVATE) }
    var sensitiveMode by remember(video.uuid) { mutableStateOf(sensitivePrefs.getString("sensitiveDisplayMode", "blur") ?: "blur") }
    DisposableEffect(sensitivePrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sensitiveDisplayMode") sensitiveMode = sensitivePrefs.getString(key, "blur") ?: "blur"
        }
        sensitivePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sensitivePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val blurSensitive = video.nsfw && sensitiveMode == "blur"
    val warnSensitive = video.nsfw && sensitiveMode == "warning"
    Box(modifier) {
        AsyncImage(
            model = thumbnailCandidates.getOrNull(thumbnailCandidateIndex),
            contentDescription = video.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (blurSensitive) Modifier.blur(28.dp) else Modifier),
            onError = {
                if (thumbnailCandidateIndex < thumbnailCandidates.lastIndex) thumbnailCandidateIndex++
            }
        )
        if (warnSensitive) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_sensitive_warning_badge), color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
        if (progress > 0f) {
            val progressColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart)) {
                drawLine(Color(0x99FFFFFF), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
                drawLine(progressColor, Offset(0f, size.height / 2), Offset(size.width * progress.coerceIn(0f, 1f), size.height / 2), strokeWidth = size.height)
            }
        }
        if (video.duration > 0) {
            val totalSeconds = video.duration
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val label = if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable private fun PublishedDate(publishedAt: String?, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val formatted = remember(publishedAt, currentLocale()) { formatPublishedDate(publishedAt) }
    if (formatted != null) Text(stringResource(R.string.video_published, formatted), style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
}

internal fun formatPublishedDate(value: String?): String? {
    val datePart = value?.take(10)?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) } ?: return null
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(parser.parse(datePart)!!)
    }.getOrNull()
}
