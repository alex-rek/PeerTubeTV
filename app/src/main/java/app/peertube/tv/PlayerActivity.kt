package app.peertube.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val homeScreenLink = intent.data?.takeIf { it.scheme == "peertubetv" && it.host == "video" }
        val url = intent.getStringExtra("url")
        val uuid = intent.getStringExtra("uuid") ?: homeScreenLink?.getQueryParameter("uuid")
        val server = intent.getStringExtra("server")?.takeIf(::isValidPeerTubeServer)
            ?: homeScreenLink?.getQueryParameter("server")?.takeIf(::isValidPeerTubeServer)
        val title = intent.getStringExtra("title") ?: homeScreenLink?.getQueryParameter("title") ?: "PeerTube"
        val views = intent.getLongExtra("views", homeScreenLink?.getQueryParameter("views")?.toLongOrNull() ?: 0L)
        val publishedAt = intent.getStringExtra("publishedAt") ?: homeScreenLink?.getQueryParameter("publishedAt")
        val channelName = intent.getStringExtra("channelName") ?: homeScreenLink?.getQueryParameter("channelName")
        val channelHost = intent.getStringExtra("channelHost") ?: homeScreenLink?.getQueryParameter("channelHost").orEmpty()
        val channelDisplayName = intent.getStringExtra("channelDisplayName") ?: homeScreenLink?.getQueryParameter("channelDisplayName")
        val queueJson = intent.getStringExtra("playbackQueue").orEmpty()
        val queueType = object : TypeToken<List<PlaybackQueueItem>>() {}.type
        val playbackQueue = runCatching { Gson().fromJson<List<PlaybackQueueItem>>(queueJson, queueType) ?: emptyList() }.getOrDefault(emptyList())
        val queueIndex = intent.getIntExtra("queueIndex", -1)
        val afterVideoAction = intent.getStringExtra("afterVideoAction") ?: "finish"
        val playerOverlayDurationSeconds = intent.getIntExtra("playerOverlayDurationSeconds", 3).let { if (it == 3 || it == 5 || it == 10) it else 3 }
        val showPlayerClock = intent.getBooleanExtra("showPlayerClock", false)
        val playerClockPosition = intent.getStringExtra("playerClockPosition") ?: "top_right"
        val playerClockSize = intent.getStringExtra("playerClockSize") ?: "medium"
        val playerClockBackdrop = intent.getStringExtra("playerClockBackdrop") ?: "medium"
        val resumePlayback = intent.getBooleanExtra("resumePlayback", true)
        val seekStepSeconds = intent.getIntExtra("seekStepSeconds", 10).takeIf { it in listOf(5, 10, 15, 30, 60) } ?: 10
        val clock24Hour = intent.getBooleanExtra("clock24Hour", true)
        val bufferProfile = intent.getStringExtra("bufferProfile")?.takeIf { it in listOf("small", "medium", "large", "largest") } ?: "medium"
        val playerVolume = intent.getIntExtra("playerVolume", 100).coerceIn(0, 200)
        val watchedRecommendationThreshold = intent.getStringExtra("watchedRecommendationThreshold") ?: "never"
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    VideoScreen(initialUrl = url, uuid = uuid, server = server, title = title, views = views, publishedAt = publishedAt, channelName = channelName, channelHost = channelHost, channelDisplayName = channelDisplayName, playbackQueue = playbackQueue, queueIndex = queueIndex, queueJson = queueJson, afterVideoAction = afterVideoAction, playerOverlayDurationSeconds = playerOverlayDurationSeconds, showPlayerClock = showPlayerClock, playerClockPosition = playerClockPosition, playerClockSize = playerClockSize, playerClockBackdrop = playerClockBackdrop, resumePlayback = resumePlayback, seekStepSeconds = seekStepSeconds, clock24Hour = clock24Hour, bufferProfile = bufferProfile, playerVolume = playerVolume, watchedRecommendationThreshold = watchedRecommendationThreshold)
                }
            }
        }
    }
}

private fun isValidPeerTubeServer(value: String): Boolean {
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
    return uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
}

@Composable private fun VideoScreen(initialUrl: String?, uuid: String?, server: String?, title: String, views: Long, publishedAt: String?, channelName: String?, channelHost: String, channelDisplayName: String?, playbackQueue: List<PlaybackQueueItem>, queueIndex: Int, queueJson: String, afterVideoAction: String, playerOverlayDurationSeconds: Int, showPlayerClock: Boolean, playerClockPosition: String, playerClockSize: String, playerClockBackdrop: String, resumePlayback: Boolean, seekStepSeconds: Int, clock24Hour: Boolean, bufferProfile: String, playerVolume: Int, watchedRecommendationThreshold: String) {
    val context = LocalContext.current
    var url by remember(initialUrl, uuid) { mutableStateOf(initialUrl?.takeIf(String::isNotBlank)) }
    var errorText by remember(initialUrl, uuid) { mutableStateOf<String?>(null) }
    var isLoading by remember(initialUrl, uuid) { mutableStateOf(true) }
    var subtitles by remember(initialUrl, uuid) { mutableStateOf<List<VideoCaption>>(emptyList()) }

    LaunchedEffect(initialUrl, uuid, server) {
        if (uuid.isNullOrBlank() || server.isNullOrBlank()) {
            if (url == null) errorText = context.getString(R.string.player_missing_context)
            isLoading = false
            return@LaunchedEffect
        }
        try {
            if (url == null) {
                val details = withContext(Dispatchers.IO) { ApiFactory.create(server).video(uuid) }
                url = details.playableUrl()
                if (url == null) errorText = context.getString(R.string.player_missing_video)
            }
            subtitles = runCatching { withContext(Dispatchers.IO) { ApiFactory.create(server).captions(uuid).data } }.getOrDefault(emptyList())
        } catch (e: Exception) {
            if (url == null) errorText = context.getString(R.string.player_fetch_error, e.localizedMessage ?: context.getString(R.string.error_request))
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Text(stringResource(R.string.player_loading), color = Color.White, modifier = Modifier.padding(top = 16.dp))
            }
        }
        url != null -> Player(
            url = url!!,
            uuid = uuid,
            title = title,
            subtitles = subtitles,
            server = server.orEmpty(),
            views = views,
            publishedAt = publishedAt,
            channelName = channelName,
            channelHost = channelHost,
            channelDisplayName = channelDisplayName,
            afterVideoAction = afterVideoAction,
            playerOverlayDurationSeconds = playerOverlayDurationSeconds,
            showPlayerClock = showPlayerClock,
            playerClockPosition = playerClockPosition,
            playerClockSize = playerClockSize,
            playerClockBackdrop = playerClockBackdrop,
            resumePlayback = resumePlayback,
            seekStepSeconds = seekStepSeconds,
            clock24Hour = clock24Hour,
            bufferProfile = bufferProfile,
            playerVolume = playerVolume,
            watchedRecommendationThreshold = watchedRecommendationThreshold,
            onVideoEnded = {
                val nextIndex = queueIndex + 1
                if ((afterVideoAction == "next" || afterVideoAction == "next_channel") && nextIndex in playbackQueue.indices) {
                    val next = playbackQueue[nextIndex]
                    context.startActivity(Intent(context, PlayerActivity::class.java)
                        .putExtra("url", next.url)
                        .putExtra("uuid", next.uuid)
                        .putExtra("server", next.server)
                        .putExtra("title", next.title)
                        .putExtra("views", next.views)
                        .putExtra("publishedAt", next.publishedAt)
                        .putExtra("channelName", next.channelName)
                        .putExtra("channelHost", next.channelHost)
                        .putExtra("channelDisplayName", next.channelDisplayName)
                        .putExtra("playbackQueue", queueJson)
                        .putExtra("queueIndex", nextIndex)
                        .putExtra("afterVideoAction", afterVideoAction)
                        .putExtra("playerOverlayDurationSeconds", playerOverlayDurationSeconds)
                        .putExtra("showPlayerClock", showPlayerClock)
                        .putExtra("playerClockPosition", playerClockPosition)
                        .putExtra("playerClockSize", playerClockSize)
                        .putExtra("playerClockBackdrop", playerClockBackdrop)
                        .putExtra("resumePlayback", resumePlayback)
                        .putExtra("seekStepSeconds", seekStepSeconds)
                        .putExtra("clock24Hour", clock24Hour)
                        .putExtra("bufferProfile", bufferProfile)
                        .putExtra("playerVolume", playerVolume)
                        .putExtra("watchedRecommendationThreshold", watchedRecommendationThreshold))
                }
                (context as? Activity)?.finish()
            }
        )
        else -> Text(errorText ?: stringResource(R.string.player_missing_video), color = Color.White, modifier = Modifier.padding(24.dp))
    }
}

@Composable private fun Player(url: String, uuid: String?, title: String, subtitles: List<VideoCaption>, server: String, views: Long, publishedAt: String?, channelName: String?, channelHost: String, channelDisplayName: String?, afterVideoAction: String, playerOverlayDurationSeconds: Int, showPlayerClock: Boolean, playerClockPosition: String, playerClockSize: String, playerClockBackdrop: String, resumePlayback: Boolean, seekStepSeconds: Int, clock24Hour: Boolean, bufferProfile: String, playerVolume: Int, watchedRecommendationThreshold: String, onVideoEnded: () -> Unit) {
    val context = LocalContext.current

    var playbackError by remember(url) { mutableStateOf<String?>(null) }
    var isBuffering by remember(url) { mutableStateOf(true) }
    var controlsVisible by remember(url) { mutableStateOf(true) }
    var thresholdMarked by remember(uuid) { mutableStateOf(false) }
    var playerPosition by remember(url) { mutableLongStateOf(0L) }
    var playbackActive by remember(url) { mutableStateOf(false) }
    var controlsInteractionToken by remember(url) { mutableIntStateOf(0) }
    var focusedControlIndex by remember(url) { mutableIntStateOf(-1) }
    var progressFocused by remember(url) { mutableStateOf(false) }
    var playbackSpeed by remember(url) { mutableFloatStateOf(1f) }
    var repeatVideo by remember(url) { mutableStateOf(false) }
    var subtitlesEnabled by remember(url) { mutableStateOf(false) }
    val favoriteVideo = remember(uuid, title, server, channelName, channelHost, channelDisplayName) {
        Video(
            uuid = uuid.orEmpty(),
            name = title,
            channel = channelName?.let { Channel(displayName = channelDisplayName, name = it, host = channelHost) },
            host = channelHost,
            url = server
        )
    }
    val subscriptionHost = remember(channelHost, server) {
        channelHost.takeIf(String::isNotBlank)
            ?: runCatching { java.net.URI(server).host.orEmpty() }.getOrDefault("")
    }.removePrefix("https://").removePrefix("http://").trimEnd('/')
    var isFavorite by remember(uuid) { mutableStateOf(readFavoriteVideos(context).any { it.uuid == uuid }) }
    var isSubscribed by remember(channelName, channelHost, server) {
        mutableStateOf(readSavedSubscriptions(context).any { "${it.name}@${it.host}".equals("${channelName.orEmpty()}@$subscriptionHost", ignoreCase = true) })
    }
    val controlActions = buildList {
        addAll(0..4)
        if (subtitles.isNotEmpty()) add(5)
        if (!channelName.isNullOrBlank()) add(6)
        if (!uuid.isNullOrBlank()) add(7)
    }

    val trackSelector = remember(url) { DefaultTrackSelector(context) }
    val loadControl = remember(bufferProfile) {
        val (minBuffer, maxBuffer) = when (bufferProfile) {
            "small" -> 15_000 to 45_000
            "large" -> 60_000 to 180_000
            "largest" -> 120_000 to 300_000
            else -> 30_000 to 120_000
        }
        DefaultLoadControl.Builder().setBufferDurationsMs(minBuffer, maxBuffer, 2_500, 5_000).build()
    }
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val player = remember(url, bufferProfile, playerVolume) {
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).setLoadControl(loadControl).build().apply {
            volume = playerVolume / 100f
            val exoPlayer = this
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { playbackError = context.getString(R.string.player_playback_error, error.localizedMessage ?: context.getString(R.string.error_request)) }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                    if (playbackState == Player.STATE_ENDED) {
                        if (!uuid.isNullOrBlank() && watchedRecommendationThreshold != "never" && !thresholdMarked) {
                            markVideoWatched(context, uuid)
                            thresholdMarked = true
                        }
                        if (!uuid.isNullOrBlank()) persistWatchProgress(context, uuid, 0L)
                        if (afterVideoAction == "repeat") {
                            exoPlayer.seekTo(0)
                            exoPlayer.playWhenReady = true
                        } else latestOnVideoEnded()
                    }
                }
            })
            val subtitleConfigurations = subtitles.mapNotNull { caption ->
                val path = caption.fileUrl?.takeIf(String::isNotBlank) ?: caption.captionPath?.takeIf(String::isNotBlank)
                val captionUrl = path?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else server.trimEnd('/') + if (it.startsWith('/')) it else "/$it" }
                captionUrl?.let {
                    val mimeType = when {
                        it.substringBefore('?').endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                        else -> MimeTypes.TEXT_VTT
                    }
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it))
                        .setMimeType(mimeType)
                        .setLanguage(caption.language?.id)
                        .setLabel(caption.language?.label ?: caption.language?.id ?: context.getString(R.string.player_subtitles))
                        .setSelectionFlags(0)
                        .build()
                }
            }
            val mediaItem = MediaItem.Builder().setUri(url).setSubtitleConfigurations(subtitleConfigurations).build()
            setMediaItem(mediaItem)
            prepare()
            if (resumePlayback && !uuid.isNullOrBlank()) {
                val savedPosition = readWatchProgress(context, uuid)
                if (savedPosition > 0L) seekTo(savedPosition)
            }
            playWhenReady = true
        }
    }
    LaunchedEffect(player, uuid) {
        if (!uuid.isNullOrBlank()) {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                if (player.duration > 0 && player.playbackState != Player.STATE_ENDED) {
                    persistWatchProgress(context, uuid, player.currentPosition)
                    if (!thresholdMarked && isWatchedThresholdMet(watchedRecommendationThreshold, player.currentPosition, player.duration)) {
                        markVideoWatched(context, uuid)
                        thresholdMarked = true
                    }
                }
            }
        }
    }
    LaunchedEffect(player) {
        while (true) {
            playerPosition = player.currentPosition.coerceAtLeast(0L)
            playbackActive = player.isPlaying
            kotlinx.coroutines.delay(500)
        }
    }
    LaunchedEffect(controlsVisible, controlsInteractionToken, playerOverlayDurationSeconds) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(playerOverlayDurationSeconds * 1_000L)
            controlsVisible = false
            focusedControlIndex = -1
            progressFocused = false
        }
    }
    fun showControls() {
        controlsVisible = true
        controlsInteractionToken++
    }
    fun toggleFavorite() {
        val current = readFavoriteVideos(context).toMutableList()
        if (current.any { it.uuid == favoriteVideo.uuid }) current.removeAll { it.uuid == favoriteVideo.uuid }
        else current.add(favoriteVideo)
        context.getSharedPreferences("peertube", Context.MODE_PRIVATE).edit().putString("favorites", Gson().toJson(current)).apply()
        isFavorite = current.any { it.uuid == favoriteVideo.uuid }
    }
    fun toggleSubscription() {
        val name = channelName?.takeIf(String::isNotBlank) ?: return
        val host = subscriptionHost
        val current = readSavedSubscriptions(context).toMutableList()
        val existing = current.firstOrNull { "${it.name}@${it.host}".equals("$name@$host", ignoreCase = true) }
        if (existing != null) current.remove(existing)
        else current.add(VideoSubscription(name, host, channelDisplayName?.takeIf(String::isNotBlank) ?: name))
        context.getSharedPreferences("peertube", Context.MODE_PRIVATE).edit().putString("subscriptions", Gson().toJson(current)).apply()
        isSubscribed = existing == null
    }
    fun activateControl(index: Int) {
        when (index) {
            0 -> player.seekTo((player.currentPosition - seekStepSeconds * 1_000L).coerceAtLeast(0L))
            1 -> if (player.isPlaying) player.pause() else player.play()
            2 -> {
                val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                player.seekTo((player.currentPosition + seekStepSeconds * 1_000L).coerceAtMost(duration))
            }
            3 -> {
                repeatVideo = !repeatVideo
                player.repeatMode = if (repeatVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
            4 -> {
                playbackSpeed = when (playbackSpeed) { 0.75f -> 1f; 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 0.75f }
                player.setPlaybackSpeed(playbackSpeed)
            }
            5 -> if (subtitles.isNotEmpty()) {
                subtitlesEnabled = !subtitlesEnabled
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesEnabled).build()
            }
            6 -> toggleSubscription()
            7 -> toggleFavorite()
        }
        showControls()
    }
    DisposableEffect(player, uuid) {
        onDispose {
            if (!uuid.isNullOrBlank() && player.duration > 0 && player.playbackState != Player.STATE_ENDED) {
                persistWatchProgress(context, uuid, player.currentPosition)
                if (!thresholdMarked && isWatchedThresholdMet(watchedRecommendationThreshold, player.currentPosition, player.duration)) markVideoWatched(context, uuid)
            }
            player.release()
        }
    }
    val focusRequester = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .focusRequester(focusRequester)
        .focusable()
        .pointerInput(Unit) { detectTapGestures(onTap = { controlsVisible = !controlsVisible; controlsInteractionToken++ }) }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.nativeKeyEvent.keyCode in setOf(
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            )
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (focusedControlIndex >= 0 && !progressFocused) {
                        focusedControlIndex = (focusedControlIndex - 1 + controlActions.size) % controlActions.size
                    } else {
                        player.seekTo((player.currentPosition - seekStepSeconds * 1_000L).coerceAtLeast(0L))
                    }
                    showControls(); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (focusedControlIndex >= 0 && !progressFocused) {
                        focusedControlIndex = (focusedControlIndex + 1) % controlActions.size
                    } else {
                        val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + seekStepSeconds * 1_000L).coerceAtMost(duration))
                    }
                    showControls(); true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!controlsVisible) showControls()
                    else if (progressFocused || focusedControlIndex < 0) { progressFocused = false; focusedControlIndex = 0; showControls() }
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (controlsVisible) { focusedControlIndex = -1; progressFocused = true; showControls() }
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        if (focusedControlIndex >= 0 && !progressFocused) controlActions.getOrNull(focusedControlIndex)?.let(::activateControl)
                        else { if (player.isPlaying) player.pause() else player.play(); showControls() }
                    }; true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { if (event.nativeKeyEvent.repeatCount == 0) { if (player.isPlaying) player.pause() else player.play(); showControls() }; true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); showControls(); true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); showControls(); true }
                else -> false
            }
        }
    ) {
        AndroidView(factory = { viewContext ->
            RemotePlayerView(viewContext).apply {
                this.player = player
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        }, modifier = Modifier.fillMaxSize())
        if (isBuffering && playbackError == null) CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center), color = Color.White)
        playbackError?.let { Text(it, color = Color.White, modifier = Modifier.align(androidx.compose.ui.Alignment.Center).padding(24.dp)) }
        if (showPlayerClock) {
            PlayerClock(
                size = playerClockSize,
                backdrop = playerClockBackdrop,
                is24Hour = clock24Hour,
                modifier = Modifier.align(if (playerClockPosition == "top_left") androidx.compose.ui.Alignment.TopStart else androidx.compose.ui.Alignment.TopEnd).padding(24.dp)
            )
        }
        if (controlsVisible) {
            Column(
                Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
                    .padding(horizontal = 42.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val metadata = buildList {
                    add(channelDisplayName ?: channelName ?: context.getString(R.string.channel_unknown))
                    add(context.getString(R.string.video_views, views.toString()))
                    formatPublishedDate(publishedAt)?.let { add(context.getString(R.string.video_published, it)) }
                }.joinToString(" · ")
                Text(metadata, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = .88f), maxLines = 1, overflow = TextOverflow.Ellipsis)

                val focusedControlLabel = when (focusedControlIndex) {
                    0 -> R.string.player_control_back
                    1 -> if (playbackActive) R.string.player_control_pause else R.string.player_control_play
                    2 -> R.string.player_control_forward
                    3 -> R.string.player_control_repeat
                    4 -> R.string.player_control_speed
                    else -> when (controlActions.getOrNull(focusedControlIndex)) {
                        5 -> R.string.player_subtitles
                        6 -> if (isSubscribed) R.string.unsubscribe else R.string.subscribe
                        7 -> if (isFavorite) R.string.remove_favorite else R.string.add_favorite
                        else -> null
                    }
                }
                if (focusedControlLabel != null) {
                    Text(
                        stringResource(focusedControlLabel),
                        modifier = Modifier.background(Color(0xCC171717), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    PlayerControl("−${seekStepSeconds}s", "Seek backward", selected = focusedControlIndex == controlActions.indexOf(0)) { activateControl(0) }
                    PlayerControl(if (playbackActive) "Ⅱ" else "▶", if (playbackActive) "Pause" else "Play", large = true, selected = focusedControlIndex == controlActions.indexOf(1)) { activateControl(1) }
                    PlayerControl("+${seekStepSeconds}s", "Seek forward", selected = focusedControlIndex == controlActions.indexOf(2)) { activateControl(2) }
                    PlayerControl("↻${if (repeatVideo) "✓" else ""}", "Repeat", selected = focusedControlIndex == controlActions.indexOf(3)) { activateControl(3) }
                    PlayerControl("${playbackSpeed}×", "Playback speed", selected = focusedControlIndex == controlActions.indexOf(4)) { activateControl(4) }
                    if (subtitles.isNotEmpty()) PlayerControl(if (subtitlesEnabled) "CC ✓" else "CC", "Subtitles", selected = focusedControlIndex == controlActions.indexOf(5)) { activateControl(5) }
                    if (!channelName.isNullOrBlank()) PlayerControl(if (isSubscribed) "✓" else "＋", "Subscribe", selected = focusedControlIndex == controlActions.indexOf(6)) { activateControl(6) }
                    if (!uuid.isNullOrBlank()) PlayerControl(if (isFavorite) "♥✓" else "♥", "Favorite", selected = focusedControlIndex == controlActions.indexOf(7)) { activateControl(7) }
                    Spacer(Modifier.weight(1f))
                    Text("${formatPlayerTime(playerPosition)} / ${formatPlayerTime(player.duration)}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                val duration = player.duration.takeIf { it > 0L } ?: 1L
                Slider(value = (playerPosition.toFloat() / duration).coerceIn(0f, 1f), onValueChange = { playerPosition = (it * duration).toLong(); showControls() }, onValueChangeFinished = { player.seekTo(playerPosition) }, modifier = Modifier.fillMaxWidth()
                    .then(if (progressFocused) Modifier.background(Color(0x4475D5FF), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(horizontal = 8.dp) else Modifier))
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable private fun PlayerControl(label: String, description: String, large: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = if (large) 64.dp else 52.dp)
        .background(if (selected) Color(0xCCFF6D00) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        .semantics { contentDescription = description }) {
        Text(label, color = Color.White, fontSize = if (large) 30.sp else 20.sp, maxLines = 1)
    }
}

private fun formatPlayerTime(timeMs: Long): String {
    if (timeMs < 0L || timeMs == androidx.media3.common.C.TIME_UNSET) return "0:00"
    val totalSeconds = timeMs / 1_000
    return if (totalSeconds >= 3_600) "%d:%02d:%02d".format(totalSeconds / 3_600, (totalSeconds % 3_600) / 60, totalSeconds % 60)
    else "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun readFavoriteVideos(context: Context): List<Video> {
    val type = object : TypeToken<List<Video>>() {}.type
    return runCatching {
        Gson().fromJson<List<Video>>(context.getSharedPreferences("peertube", Context.MODE_PRIVATE).getString("favorites", "[]"), type) ?: emptyList()
    }.getOrDefault(emptyList())
}

private fun readSavedSubscriptions(context: Context): List<VideoSubscription> {
    val type = object : TypeToken<List<VideoSubscription>>() {}.type
    return runCatching {
        Gson().fromJson<List<VideoSubscription>>(context.getSharedPreferences("peertube", Context.MODE_PRIVATE).getString("subscriptions", "[]"), type) ?: emptyList()
    }.getOrDefault(emptyList())
}

private class RemotePlayerView(context: Context) : PlayerView(context) {
}

@Composable private fun PlayerClock(size: String, backdrop: String, is24Hour: Boolean, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val fontSize = when (size) { "small" -> 18.sp; "large" -> 32.sp; else -> 24.sp }
    val backgroundColor = when (backdrop) { "none" -> Color.Transparent; "light" -> Color(0x55000000); "strong" -> Color(0xDD000000); else -> Color(0x99000000) }
    val time = remember(now, is24Hour) { java.text.SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date(now)) }
    Text(time, modifier = modifier.background(backgroundColor).padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = fontSize)
}

private fun persistWatchProgress(context: android.content.Context, uuid: String, positionMs: Long) {
    val prefs = context.getSharedPreferences("peertube", 0)
    val type = object : TypeToken<MutableMap<String, Long>>() {}.type
    val positions = runCatching {
        Gson().fromJson<MutableMap<String, Long>>(prefs.getString("watchProgressMs", "{}"), type)?.toMutableMap() ?: mutableMapOf()
    }.getOrDefault(mutableMapOf())
    positions[uuid] = positionMs.coerceAtLeast(0L)
    prefs.edit().putString("watchProgressMs", Gson().toJson(positions)).apply()
}

private fun isWatchedThresholdMet(threshold: String, positionMs: Long, durationMs: Long): Boolean {
    if (durationMs <= 0L) return false
    return when (threshold) {
        "minute" -> positionMs >= 60_000L
        "three_minutes" -> positionMs >= 180_000L
        "half" -> positionMs >= durationMs / 2L
        "complete" -> false // completion is recorded by ExoPlayer's STATE_ENDED callback
        else -> false
    }
}

private fun markVideoWatched(context: android.content.Context, uuid: String) {
    val prefs = context.getSharedPreferences("peertube", 0)
    val type = object : TypeToken<MutableList<String>>() {}.type
    val watched = runCatching {
        Gson().fromJson<MutableList<String>>(prefs.getString("watchedVideoIds", "[]"), type)?.toMutableList() ?: mutableListOf()
    }.getOrDefault(mutableListOf())
    if (uuid !in watched) {
        watched.add(uuid)
        prefs.edit().putString("watchedVideoIds", Gson().toJson(watched)).apply()
    }
}

private fun readWatchProgress(context: android.content.Context, uuid: String): Long {
    val type = object : TypeToken<Map<String, Long>>() {}.type
    val positions = runCatching {
        Gson().fromJson<Map<String, Long>>(context.getSharedPreferences("peertube", 0).getString("watchProgressMs", "{}"), type) ?: emptyMap()
    }.getOrDefault(emptyMap())
    return positions[uuid]?.coerceAtLeast(0L) ?: 0L
}


