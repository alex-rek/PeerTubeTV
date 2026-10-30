package app.peertube.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val homeScreenLink = intent.data?.takeIf { it.scheme == "peertubetv" && it.host == "video" }
        val url = intent.getStringExtra("url")
        val audioOnly = intent.getBooleanExtra("audioOnly", false)
        val liveStateId = if (intent.hasExtra("liveStateId")) intent.getIntExtra("liveStateId", 0).takeIf { it != 0 } else null
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
                    VideoScreen(initialUrl = url, initialAudioOnly = audioOnly, initialLiveStateId = liveStateId, uuid = uuid, server = server, title = title, views = views, publishedAt = publishedAt, channelName = channelName, channelHost = channelHost, channelDisplayName = channelDisplayName, playbackQueue = playbackQueue, queueIndex = queueIndex, queueJson = queueJson, afterVideoAction = afterVideoAction, playerOverlayDurationSeconds = playerOverlayDurationSeconds, showPlayerClock = showPlayerClock, playerClockPosition = playerClockPosition, playerClockSize = playerClockSize, playerClockBackdrop = playerClockBackdrop, resumePlayback = resumePlayback, seekStepSeconds = seekStepSeconds, clock24Hour = clock24Hour, bufferProfile = bufferProfile, playerVolume = playerVolume, watchedRecommendationThreshold = watchedRecommendationThreshold)
                }
            }
        }
    }
}

private fun isValidPeerTubeServer(value: String): Boolean {
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
    return uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
}

@Composable private fun VideoScreen(initialUrl: String?, initialAudioOnly: Boolean, initialLiveStateId: Int?, uuid: String?, server: String?, title: String, views: Long, publishedAt: String?, channelName: String?, channelHost: String, channelDisplayName: String?, playbackQueue: List<PlaybackQueueItem>, queueIndex: Int, queueJson: String, afterVideoAction: String, playerOverlayDurationSeconds: Int, showPlayerClock: Boolean, playerClockPosition: String, playerClockSize: String, playerClockBackdrop: String, resumePlayback: Boolean, seekStepSeconds: Int, clock24Hour: Boolean, bufferProfile: String, playerVolume: Int, watchedRecommendationThreshold: String) {
    val context = LocalContext.current
    var url by remember(initialUrl, uuid) { mutableStateOf(initialUrl?.takeIf(String::isNotBlank)) }
    var errorText by remember(initialUrl, uuid) { mutableStateOf<String?>(null) }
    var isLoading by remember(initialUrl, uuid) { mutableStateOf(true) }
    var subtitles by remember(initialUrl, uuid) { mutableStateOf<List<VideoCaption>>(emptyList()) }
    var videoDescription by remember(initialUrl, uuid) { mutableStateOf("") }
    var audioOnly by remember(initialUrl, uuid) { mutableStateOf(initialAudioOnly) }

    LaunchedEffect(initialUrl, uuid, server) {
        if (uuid.isNullOrBlank() || server.isNullOrBlank()) {
            if (url == null) errorText = context.getString(R.string.player_missing_context)
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val details = runCatching { withContext(Dispatchers.IO) { ApiFactory.create(server).video(uuid) } }.getOrNull()
            if (details != null) {
                videoDescription = details.description.orEmpty()
                if (url == null) {
                    url = details.playableUrl()
                    audioOnly = details.isAudioOnlySource()
                    if (url == null) errorText = when (details.state?.id ?: initialLiveStateId) {
                        4 -> context.getString(R.string.player_live_not_started)
                        5 -> context.getString(R.string.player_live_ended)
                        else -> if (details.isLive == true) context.getString(R.string.player_live_no_stream) else context.getString(R.string.player_missing_video)
                    }
                }
            } else if (url == null) {
                errorText = context.getString(R.string.player_fetch_error, context.getString(R.string.error_request))
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
            audioOnlySource = audioOnly,
            uuid = uuid,
            title = title,
            subtitles = subtitles,
            description = videoDescription,
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
                        .putExtra("audioOnly", next.audioOnly)
                        .putExtra("liveStateId", next.liveStateId ?: 0)
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

@Composable private fun Player(url: String, audioOnlySource: Boolean, uuid: String?, title: String, subtitles: List<VideoCaption>, description: String, server: String, views: Long, publishedAt: String?, channelName: String?, channelHost: String, channelDisplayName: String?, afterVideoAction: String, playerOverlayDurationSeconds: Int, showPlayerClock: Boolean, playerClockPosition: String, playerClockSize: String, playerClockBackdrop: String, resumePlayback: Boolean, seekStepSeconds: Int, clock24Hour: Boolean, bufferProfile: String, playerVolume: Int, watchedRecommendationThreshold: String, onVideoEnded: () -> Unit) {
    val context = LocalContext.current

    var playbackError by remember(url) { mutableStateOf<String?>(null) }
    var videoUnavailableReason by remember(url, audioOnlySource) {
        mutableStateOf(if (audioOnlySource) context.getString(R.string.player_audio_only_reason) else null)
    }
    var isBuffering by remember(url) { mutableStateOf(true) }
    var controlsVisible by remember(url) { mutableStateOf(true) }
    var thresholdMarked by remember(uuid) { mutableStateOf(false) }
    var playerPosition by remember(url) { mutableLongStateOf(0L) }
    var playbackActive by remember(url) { mutableStateOf(false) }
    var keepScreenAwake by remember(url) { mutableStateOf(false) }
    var controlsInteractionToken by remember(url) { mutableIntStateOf(0) }
    var focusedControlIndex by remember(url) { mutableIntStateOf(-1) }
    var progressFocused by remember(url) { mutableStateOf(false) }
    val playerPreferences = remember(context) { context.getSharedPreferences("peertube", Context.MODE_PRIVATE) }
    val appUiLanguageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "auto" }
    val interfaceTranslationLanguage = interfaceSubtitleLanguage(appUiLanguageTag)
    var subtitleBottomOffset by remember(playerPreferences) {
        mutableFloatStateOf(playerPreferences.getFloat("subtitleBottomOffset", 0.20f).coerceIn(0.02f, 0.50f))
    }
    var subtitleHorizontalPosition by remember(playerPreferences) { mutableFloatStateOf(playerPreferences.getFloat("subtitleHorizontalPosition", 0.50f).coerceIn(0.05f, 0.95f)) }
    var subtitleSize by remember(playerPreferences) { mutableFloatStateOf(playerPreferences.getFloat("subtitleSize", 0.053f)) }
    var subtitleColor by remember(playerPreferences) { mutableStateOf(playerPreferences.getString("subtitleColor", if (playerPreferences.getString("subtitleStyle", "default") == "yellow") "yellow" else "white") ?: "white") }
    var subtitleTranslationLanguage by remember(playerPreferences, appUiLanguageTag) {
        mutableStateOf(if (!playerPreferences.getBoolean("subtitleTranslationLanguageCustom", false)) interfaceTranslationLanguage
        else playerPreferences.getString("subtitleTranslationLanguage", interfaceTranslationLanguage) ?: interfaceTranslationLanguage)
    }
    var subtitleTranslationEnabled by remember(url) { mutableStateOf(false) }
    val translatedCues = remember(url) { mutableStateListOf<TranslatedCue>() }
    val sourceSubtitleCues = remember(url) { mutableStateListOf<TranslatedCue>() }
    var translationProgress by remember(url) { mutableIntStateOf(0) }
    var translationTotal by remember(url) { mutableIntStateOf(0) }
    var translationPreparing by remember(url) { mutableStateOf(false) }
    var translationError by remember(url) { mutableStateOf<String?>(null) }
    var translationJob by remember(url) { mutableStateOf<Job?>(null) }
    fun adjustSubtitleBottomOffset(delta: Float) {
        subtitleBottomOffset = (subtitleBottomOffset + delta).coerceIn(0.02f, 0.50f)
        playerPreferences.edit().putFloat("subtitleBottomOffset", subtitleBottomOffset).apply()
    }
    var qualityPanelOpen by remember(url) { mutableStateOf(false) }
    var qualityPanelRow by remember(url) { mutableIntStateOf(0) }
    var subtitlePanelOpen by remember(url) { mutableStateOf(false) }
    var subtitlePanelRow by remember(url) { mutableIntStateOf(0) }
    var autoTranslationConsidered by remember(url) { mutableStateOf(false) }
    val subtitlePanelScrollState = rememberScrollState()
    val subtitlePanelDensity = androidx.compose.ui.platform.LocalDensity.current
    var infoPanelOpen by remember(url) { mutableStateOf(false) }
    var infoPanelChannelFocused by remember(url) { mutableStateOf(false) }
    var commentsPanelOpen by remember(url) { mutableStateOf(false) }
    var commentsPanelRow by remember(url) { mutableIntStateOf(0) }
    var comments by remember(uuid) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var commentsTotal by remember(uuid) { mutableIntStateOf(0) }
    var commentsError by remember(uuid) { mutableStateOf<String?>(null) }
    var commentsStart by remember(uuid) { mutableIntStateOf(0) }
    var commentsLoading by remember(uuid) { mutableStateOf(false) }
    var commentsLoaded by remember(uuid) { mutableStateOf(false) }
    val infoScrollState = rememberScrollState()
    val commentsScrollState = rememberScrollState()
    val panelScope = rememberCoroutineScope()
    LaunchedEffect(subtitlePanelOpen, subtitlePanelRow, subtitles.size) {
        if (subtitlePanelOpen) {
            val target = with(subtitlePanelDensity) { (64 + subtitlePanelRow * 56).dp.roundToPx() }
            subtitlePanelScrollState.animateScrollTo(target.coerceAtMost(subtitlePanelScrollState.maxValue))
        }
    }
    var selectedVideoCodec by remember { mutableStateOf(playerPreferences.getString("playerVideoCodec", "auto") ?: "auto") }
    var selectedAudioFormat by remember { mutableStateOf(playerPreferences.getString("playerAudioFormat", "auto") ?: "auto") }
    var selectedAudioLanguage by remember { mutableStateOf(playerPreferences.getString("playerAudioLanguage", "auto") ?: "auto") }
    var selectedVideoProfile by remember { mutableIntStateOf(playerPreferences.getInt("playerVideoProfile", 0)) }
    var autoFrameRate by remember { mutableStateOf(playerPreferences.getBoolean("playerAutoFrameRate", false)) }
    var qualityFallbackAttempted by remember(url) { mutableStateOf(false) }
    var audioOnlyFallbackAttempted by remember(url) { mutableStateOf(false) }
    var playerSurfaceView by remember { mutableStateOf<RemotePlayerView?>(null) }
    var currentVideoFrameRate by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember(url) { mutableFloatStateOf(1f) }
    var repeatVideo by remember(url) { mutableStateOf(false) }
    val subtitleChannelKey = "subtitleTrack:${channelHost.lowercase()}:${channelName.orEmpty().lowercase()}"
    var rememberSubtitleByChannel by remember { mutableStateOf(playerPreferences.getBoolean("rememberSubtitleByChannel", true)) }
    val preferredSubtitleLanguage = playerPreferences.getString("preferredSubtitleLanguage", "auto") ?: "auto"
    val subtitlesEnabledByDefault = playerPreferences.getBoolean("subtitlesEnabledByDefault", false)
    var selectedSubtitleIndex by remember(subtitleChannelKey) { mutableIntStateOf(if (rememberSubtitleByChannel) playerPreferences.getInt(subtitleChannelKey, -1) else -1) }
    var subtitlesEnabled by remember(url, subtitleChannelKey) { mutableStateOf(selectedSubtitleIndex >= 0) }
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
        add(5)
        if (!channelName.isNullOrBlank()) add(6)
        if (!uuid.isNullOrBlank()) add(7)
        add(8)
        if (!uuid.isNullOrBlank()) add(9)
        if (!uuid.isNullOrBlank()) add(10)
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
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(ApiFactory.media3DataSourceFactory())
        ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector).setLoadControl(loadControl).build().apply {
            volume = playerVolume / 100f
            val exoPlayer = this
            addListener(object : Player.Listener {
                private fun updateScreenAwakeState() {
                    keepScreenAwake = exoPlayer.playWhenReady &&
                        exoPlayer.playbackState != Player.STATE_IDLE &&
                        exoPlayer.playbackState != Player.STATE_ENDED
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    updateScreenAwakeState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateScreenAwakeState()
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

                override fun onPlayerError(error: PlaybackException) {
                    val decoderFailure: MediaCodecRenderer.DecoderInitializationException? = generateSequence<Throwable>(error) { cause -> cause.cause }
                        .filterIsInstance<MediaCodecRenderer.DecoderInitializationException>()
                        .firstOrNull()
                    val failedVideoMime = decoderFailure?.mimeType?.startsWith("video/") == true
                    val currentVideoHeight = exoPlayer.videoFormat?.height ?: 0
                    val availableHeights = exoPlayer.currentTracks.groups
                        .filter { it.type == C.TRACK_TYPE_VIDEO }
                        .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it).height } }
                        .filter { it > 0 }.distinct().sortedDescending()
                    val fallbackHeight = if (currentVideoHeight > 0) {
                        availableHeights.filter { it < currentVideoHeight }.maxOrNull()
                    } else {
                        availableHeights.drop(1).firstOrNull()
                    }
                    val currentItem = exoPlayer.currentMediaItem
                    if (failedVideoMime && !qualityFallbackAttempted && fallbackHeight != null && currentItem != null) {
                        qualityFallbackAttempted = true
                        trackSelector.parameters = trackSelector.parameters.buildUpon()
                            .setPreferredVideoMimeType(null)
                            .setMaxVideoSize(Int.MAX_VALUE, fallbackHeight)
                            .build()
                        val resumeAt = exoPlayer.currentPosition
                        exoPlayer.setMediaItem(currentItem, resumeAt)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                        playbackError = null
                    } else if (failedVideoMime && !audioOnlyFallbackAttempted && currentItem != null && exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) {
                        audioOnlyFallbackAttempted = true
                        videoUnavailableReason = decoderFailure?.let { failure ->
                            failure.message?.takeIf(String::isNotBlank)
                                ?: failure.mimeType?.let { context.getString(R.string.player_video_decoder_reason, it) }
                        } ?: error.localizedMessage?.takeIf(String::isNotBlank)
                        trackSelector.parameters = trackSelector.parameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                            .setPreferredVideoMimeType(null)
                            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                            .build()
                        val resumeAt = exoPlayer.currentPosition
                        exoPlayer.setMediaItem(currentItem, resumeAt)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                        playbackError = null
                    } else {
                        playbackError = context.getString(R.string.player_playback_error, error.localizedMessage ?: context.getString(R.string.error_request))
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
    val playerWindow = (context as? Activity)?.window
    val originallyKeepScreenOn = remember(playerWindow) {
        playerWindow?.let { it.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 } == true
    }
    DisposableEffect(playerWindow, keepScreenAwake) {
        playerWindow?.let { window ->
            if (keepScreenAwake) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else if (!originallyKeepScreenOn) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            playerWindow?.let { window ->
                if (originallyKeepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
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
            currentVideoFrameRate = player.videoFormat?.frameRate?.takeIf { it > 0f } ?: 0f
            if (player.playbackState == Player.STATE_READY && player.videoFormat == null &&
                player.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) {
                if (videoUnavailableReason == null) videoUnavailableReason = context.getString(R.string.player_video_stream_missing_reason)
            }
            kotlinx.coroutines.delay(500)
        }
    }
    LaunchedEffect(playerSurfaceView, autoFrameRate, currentVideoFrameRate) {
        val surfaceView = playerSurfaceView?.videoSurfaceView as? SurfaceView
        val surface = surfaceView?.holder?.surface?.takeIf { it.isValid } ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 31) {
            val rate = if (autoFrameRate) currentVideoFrameRate else 0f
            surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
        }
    }
    LaunchedEffect(controlsVisible, controlsInteractionToken, playerOverlayDurationSeconds, qualityPanelOpen, subtitlePanelOpen, infoPanelOpen) {
        if (controlsVisible && !qualityPanelOpen && !subtitlePanelOpen && !infoPanelOpen) {
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
            5 -> { subtitlePanelOpen = !subtitlePanelOpen; subtitlePanelRow = 0 }
            6 -> toggleSubscription()
            7 -> toggleFavorite()
            8 -> {
                qualityPanelOpen = !qualityPanelOpen
                if (qualityPanelOpen) {
                    qualityPanelRow = 0
                    showControls()
                }
            }
            9 -> { infoPanelOpen = !infoPanelOpen; infoPanelChannelFocused = false }
            10 -> { commentsPanelOpen = !commentsPanelOpen; commentsPanelRow = 0 }
        }
        showControls()
    }
    fun selectSubtitle(index: Int) {
        if (subtitleTranslationEnabled) {
            subtitleTranslationEnabled = false
            translatedCues.clear()
            sourceSubtitleCues.clear()
            translationJob?.cancel()
        }
        selectedSubtitleIndex = index
        subtitlesEnabled = index >= 0
        val caption = subtitles.getOrNull(index)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, index < 0)
            .setPreferredTextLanguage(caption?.language?.id?.takeIf(String::isNotBlank))
            .build()
        if (rememberSubtitleByChannel) playerPreferences.edit().putInt(subtitleChannelKey, index).apply()
    }
    LaunchedEffect(subtitles, selectedSubtitleIndex) {
        if (subtitles.isNotEmpty()) {
            val channelTrackWasSaved = rememberSubtitleByChannel && playerPreferences.contains(subtitleChannelKey)
            val preferredIndex = if (preferredSubtitleLanguage != "auto") subtitles.indexOfFirst { it.language?.id.equals(preferredSubtitleLanguage, true) }.takeIf { it >= 0 } else null
            val desired = when {
                channelTrackWasSaved -> selectedSubtitleIndex.takeIf { it in subtitles.indices } ?: -1
                preferredIndex != null -> preferredIndex
                subtitlesEnabledByDefault -> 0
                else -> -1
            }
            if (selectedSubtitleIndex != desired || subtitlesEnabled != (desired >= 0)) selectSubtitle(desired)
        }
    }
    LaunchedEffect(uuid, server, commentsPanelOpen) {
        if (!commentsPanelOpen || commentsLoaded || uuid.isNullOrBlank()) return@LaunchedEffect
        commentsLoading = true
        commentsError = null
        val result = runCatching { withContext(Dispatchers.IO) { ApiFactory.create(server).commentThreads(uuid, 0, 20) } }
        result.onSuccess { body ->
            commentsTotal = body.get("total")?.asInt ?: 0
            comments = body.getAsJsonArray("data")?.mapNotNull { element ->
                val comment = element.asJsonObject.getAsJsonObject("comment") ?: return@mapNotNull null
                val text = comment.get("text")?.asString.orEmpty().replace(Regex("<[^>]*>"), " ").trim()
                if (text.isBlank()) return@mapNotNull null
                val account = comment.getAsJsonObject("account")
                val author = account?.get("displayName")?.asString?.takeIf(String::isNotBlank)
                    ?: account?.get("name")?.asString.orEmpty()
                author to text
            }.orEmpty()
        }.onFailure { commentsError = it.localizedMessage ?: it.javaClass.simpleName }
        commentsLoaded = result.isSuccess
        commentsLoading = false
    }
    fun loadMoreComments() {
        if (commentsLoading || comments.size >= commentsTotal || uuid.isNullOrBlank()) return
        panelScope.launch {
            commentsLoading = true
            val nextStart = commentsStart + 20
            runCatching { withContext(Dispatchers.IO) { ApiFactory.create(server).commentThreads(uuid, nextStart, 20) } }
                .onSuccess { body ->
                    commentsStart = nextStart
                    commentsTotal = body.get("total")?.asInt ?: commentsTotal
                    val next = body.getAsJsonArray("data")?.mapNotNull { element ->
                        val comment = element.asJsonObject.getAsJsonObject("comment") ?: return@mapNotNull null
                        val text = comment.get("text")?.asString.orEmpty().replace(Regex("<[^>]*>"), " ").trim()
                        if (text.isBlank()) return@mapNotNull null
                        val account = comment.getAsJsonObject("account")
                        (account?.get("displayName")?.asString ?: account?.get("name")?.asString.orEmpty()) to text
                    }.orEmpty()
                    comments = comments + next
                    commentsError = null
                }.onFailure { commentsError = it.localizedMessage ?: it.javaClass.simpleName }
            commentsLoading = false
        }
    }
    fun startSubtitleTranslation(enable: Boolean) {
        translationJob?.cancel()
        translationPreparing = false
        translationError = null
        if (!enable) {
            subtitleTranslationEnabled = false
            translatedCues.clear()
            sourceSubtitleCues.clear()
            if (selectedSubtitleIndex >= 0) selectSubtitle(selectedSubtitleIndex)
            else player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            return
        }
        val sourceCaption = subtitles.getOrNull(selectedSubtitleIndex)
            ?: subtitles.firstOrNull { it.language?.id.equals(preferredSubtitleLanguage, true) }
            ?: subtitles.firstOrNull()
        if (sourceCaption == null) {
            translationError = context.getString(R.string.subtitle_translation_no_source)
            subtitleTranslationEnabled = false
            translatedCues.clear()
            if (selectedSubtitleIndex >= 0) selectSubtitle(selectedSubtitleIndex)
            return
        }
        if (sourceCaption.language?.id?.substringBefore('-').equals(subtitleTranslationLanguage, true)) {
            translationError = context.getString(R.string.subtitle_translation_same_language)
            subtitleTranslationEnabled = false
            if (selectedSubtitleIndex >= 0) selectSubtitle(selectedSubtitleIndex)
            return
        }
        subtitleTranslationEnabled = true
        translatedCues.clear()
        translationProgress = 0
        translationTotal = 0
        translationPreparing = true
        translationJob = panelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    translateSubtitleFile(
                        context = context,
                        server = server,
                        caption = sourceCaption,
                        targetLanguage = subtitleTranslationLanguage,
                        onProgress = { done, total ->
                            panelScope.launch(Dispatchers.Main) {
                                translationProgress = done
                                translationTotal = total
                                translationPreparing = false
                            }
                        },
                        onCuesLoaded = { cues ->
                            panelScope.launch(Dispatchers.Main) {
                                sourceSubtitleCues.clear()
                                sourceSubtitleCues.addAll(cues)
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                                translationPreparing = false
                            }
                        },
                        onBatchTranslated = { batch ->
                            panelScope.launch(Dispatchers.Main) {
                                translatedCues.addAll(batch)
                                translationPreparing = false
                            }
                        }
                    )
                }
            }.onSuccess { cues ->
                translationPreparing = false
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                subtitleTranslationEnabled = false
                translationPreparing = false
                translationError = error.localizedMessage ?: context.getString(R.string.subtitle_translation_failed)
                translatedCues.clear()
                sourceSubtitleCues.clear()
                if (selectedSubtitleIndex >= 0) selectSubtitle(selectedSubtitleIndex)
            }
        }
    }
    LaunchedEffect(subtitles, selectedSubtitleIndex, subtitleTranslationLanguage) {
        if (autoTranslationConsidered || subtitles.isEmpty()) return@LaunchedEffect
        autoTranslationConsidered = true
        val selectedCaption = subtitles.getOrNull(selectedSubtitleIndex)?.takeIf { !it.language?.id.isNullOrBlank() }
        val sourceIndex = selectedCaption?.let(subtitles::indexOf)
            ?.takeIf { it >= 0 }
            ?: subtitles.indexOfFirst { !it.language?.id.isNullOrBlank() }
        if (sourceIndex < 0) return@LaunchedEffect
        val sourceLanguage = subtitles[sourceIndex].language?.id?.substringBefore('-')
        if (sourceLanguage.equals(subtitleTranslationLanguage, ignoreCase = true)) {
            if (selectedSubtitleIndex >= 0 && selectedSubtitleIndex != sourceIndex) selectSubtitle(sourceIndex)
        } else {
            if (selectedSubtitleIndex != sourceIndex) selectSubtitle(sourceIndex)
            startSubtitleTranslation(true)
        }
    }
    val videoTrackGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    val audioTrackGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val videoFormats = videoTrackGroups.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it) } }
    val audioFormats = audioTrackGroups.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it) } }
    val videoCodecs = listOf("auto") + videoFormats.mapNotNull { it.sampleMimeType }.distinct()
    val audioCodecs = listOf("auto") + audioFormats.mapNotNull { it.sampleMimeType }.distinct()
    val audioLanguages = listOf("auto") + audioFormats.mapNotNull { it.language?.takeIf(String::isNotBlank) }.distinct()
    val videoProfiles = listOf(0) + videoFormats.map { it.height }.filter { it > 0 }.distinct().sortedDescending()
    val availableTrackSignature = (videoFormats + audioFormats).joinToString("|") { "${it.sampleMimeType}:${it.height}:${it.language}:${it.channelCount}" }

    fun persistQualityPreference(key: String, value: Any) {
        val editor = playerPreferences.edit()
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
        }
        editor.apply()
    }
    fun applyVideoCodec(value: String) {
        selectedVideoCodec = value
        persistQualityPreference("playerVideoCodec", value)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredVideoMimeType(value.takeUnless { it == "auto" })
            .build()
    }
    fun applyAudioFormat(value: String) {
        selectedAudioFormat = value
        persistQualityPreference("playerAudioFormat", value)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioMimeType(value.takeUnless { it == "auto" })
            .build()
    }
    fun applyAudioLanguage(value: String) {
        selectedAudioLanguage = value
        persistQualityPreference("playerAudioLanguage", value)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(value.takeUnless { it == "auto" }).build()
    }
    fun applyVideoProfile(value: Int) {
        selectedVideoProfile = value
        persistQualityPreference("playerVideoProfile", value)
        val maxHeight = value.takeIf { it > 0 } ?: Int.MAX_VALUE
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, maxHeight).build()
    }
    fun cycleQualityRow(row: Int, delta: Int) {
        when (row) {
            0 -> applyVideoCodec(videoCodecs.cyclicNext(selectedVideoCodec, delta))
            1 -> applyAudioFormat(audioCodecs.cyclicNext(selectedAudioFormat, delta))
            2 -> applyAudioLanguage(audioLanguages.cyclicNext(selectedAudioLanguage, delta))
            3 -> applyVideoProfile(videoProfiles.cyclicNext(selectedVideoProfile, delta))
            4 -> {
                if (Build.VERSION.SDK_INT >= 31 && context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) {
                    autoFrameRate = !autoFrameRate
                    persistQualityPreference("playerAutoFrameRate", autoFrameRate)
                }
            }
        }
    }
    var initialQualityApplied by remember(player) { mutableStateOf(false) }
    LaunchedEffect(player, availableTrackSignature) {
        if (!initialQualityApplied && availableTrackSignature.isNotBlank()) {
            applyVideoCodec(selectedVideoCodec)
            applyAudioFormat(selectedAudioFormat)
            applyAudioLanguage(selectedAudioLanguage)
            if (selectedVideoProfile > 0) applyVideoProfile(selectedVideoProfile)
            initialQualityApplied = true
        }
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
            if (qualityPanelOpen) {
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { qualityPanelRow = (qualityPanelRow - 1).coerceAtLeast(0); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { qualityPanelRow = (qualityPanelRow + 1).coerceAtMost(4); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { cycleQualityRow(qualityPanelRow, -1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cycleQualityRow(qualityPanelRow, 1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                        if (event.nativeKeyEvent.repeatCount == 0) cycleQualityRow(qualityPanelRow, 1)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                        if (event.nativeKeyEvent.repeatCount == 0) { if (player.isPlaying) player.pause() else player.play() }
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { qualityPanelOpen = false; showControls(); true }
                    else -> true
                }
            } else if (subtitlePanelOpen) {
                val lastRow = subtitles.size + 8
                val subtitleLanguages = listOf("auto") + subtitles.mapNotNull { it.language?.id }.distinct()
                val translationLanguages = listOf(interfaceTranslationLanguage) + subtitleTranslationLanguageCodes().filter { it != interfaceTranslationLanguage }.sorted()
                fun adjustCurrentSubtitleSetting(direction: Int) {
                    when (subtitlePanelRow) {
                        subtitles.size + 2 -> {
                            val colors = listOf("white", "yellow", "cyan", "green")
                            subtitleColor = colors.cyclicNext(subtitleColor, direction)
                            playerPreferences.edit().putString("subtitleColor", subtitleColor).apply()
                            playerSurfaceView?.setSubtitleAppearance(subtitleSize, subtitleColor)
                        }
                        subtitles.size + 3 -> {
                            val sizes = listOf(.04f, .053f, .065f)
                            subtitleSize = sizes.cyclicNext(subtitleSize, direction)
                            playerPreferences.edit().putFloat("subtitleSize", subtitleSize).apply()
                            playerSurfaceView?.setSubtitleAppearance(subtitleSize, subtitleColor)
                        }
                        subtitles.size + 4 -> {
                            subtitleHorizontalPosition = (subtitleHorizontalPosition + direction * .05f).coerceIn(.05f, .95f)
                            playerPreferences.edit().putFloat("subtitleHorizontalPosition", subtitleHorizontalPosition).apply()
                            playerSurfaceView?.setSubtitlePosition(subtitleBottomOffset, subtitleHorizontalPosition)
                        }
                        subtitles.size + 5 -> adjustSubtitleBottomOffset(direction * .02f)
                        subtitles.size + 6 -> {
                            val current = playerPreferences.getString("preferredSubtitleLanguage", "auto") ?: "auto"
                            val language = subtitleLanguages.cyclicNext(current, direction)
                            playerPreferences.edit().putString("preferredSubtitleLanguage", language).apply()
                            subtitles.indexOfFirst { it.language?.id.equals(language, true) }.takeIf { language != "auto" && it >= 0 }?.let(::selectSubtitle)
                        }
                        subtitles.size + 7 -> {
                            subtitleTranslationLanguage = translationLanguages.cyclicNext(subtitleTranslationLanguage, direction)
                            playerPreferences.edit().putString("subtitleTranslationLanguage", subtitleTranslationLanguage).putBoolean("subtitleTranslationLanguageCustom", true).apply()
                            if (subtitleTranslationEnabled) startSubtitleTranslation(true)
                        }
                        subtitles.size + 8 -> startSubtitleTranslation(!subtitleTranslationEnabled)
                    }
                }
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { subtitlePanelRow = (subtitlePanelRow - 1).coerceAtLeast(0); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { subtitlePanelRow = (subtitlePanelRow + 1).coerceAtMost(lastRow); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            if (subtitlePanelRow >= subtitles.size + 2) adjustCurrentSubtitleSetting(-1)
                            else if (subtitlePanelRow == 0) selectSubtitle(-1)
                            else if (subtitlePanelRow in 1..subtitles.size) selectSubtitle(subtitlePanelRow - 1)
                            else { rememberSubtitleByChannel = !rememberSubtitleByChannel; playerPreferences.edit().putBoolean("rememberSubtitleByChannel", rememberSubtitleByChannel).apply() }
                        }; true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            if (subtitlePanelRow >= subtitles.size + 2) adjustCurrentSubtitleSetting(1)
                            else if (subtitlePanelRow == 0) selectSubtitle(-1)
                            else if (subtitlePanelRow in 1..subtitles.size) selectSubtitle(subtitlePanelRow - 1)
                            else { rememberSubtitleByChannel = !rememberSubtitleByChannel; playerPreferences.edit().putBoolean("rememberSubtitleByChannel", rememberSubtitleByChannel).apply() }
                        }; true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                        if (event.nativeKeyEvent.repeatCount == 0) when (subtitlePanelRow) {
                            0 -> selectSubtitle(-1)
                            in 1..subtitles.size -> selectSubtitle(subtitlePanelRow - 1)
                            subtitles.size + 1 -> { rememberSubtitleByChannel = !rememberSubtitleByChannel; playerPreferences.edit().putBoolean("rememberSubtitleByChannel", rememberSubtitleByChannel).apply() }
                            else -> adjustCurrentSubtitleSetting(1)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { subtitlePanelOpen = false; showControls(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (event.nativeKeyEvent.repeatCount == 0) { if (player.isPlaying) player.pause() else player.play() }; true }
                    else -> true
                }
            } else if (infoPanelOpen) {
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { panelScope.launch { infoScrollState.scrollTo((infoScrollState.value - 180).coerceAtLeast(0)) }; infoPanelChannelFocused = false; true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { panelScope.launch { val target = (infoScrollState.value + 180).coerceAtMost(infoScrollState.maxValue); infoScrollState.scrollTo(target); infoPanelChannelFocused = target >= infoScrollState.maxValue && infoScrollState.maxValue > 0 }; true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { infoPanelOpen = false; showControls(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                        if (infoPanelChannelFocused) runCatching {
                            val host = channelHost.ifBlank { Uri.parse(server).host.orEmpty() }
                            val channelUrl = Uri.parse("https://$host/video-channels/${Uri.encode(channelName.orEmpty())}/videos")
                            context.startActivity(Intent(Intent.ACTION_VIEW, channelUrl))
                        }
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (event.nativeKeyEvent.repeatCount == 0) { if (player.isPlaying) player.pause() else player.play() }; true }
                    else -> true
                }
            } else if (commentsPanelOpen) {
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { commentsPanelRow = (commentsPanelRow - 1).coerceAtLeast(0); panelScope.launch { commentsScrollState.scrollTo((commentsScrollState.value - 180).coerceAtLeast(0)) }; true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { commentsPanelRow++; panelScope.launch { commentsScrollState.scrollTo((commentsScrollState.value + 180).coerceAtMost(commentsScrollState.maxValue)) }; true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_SELECT -> { if (commentsPanelRow >= comments.size) loadMoreComments(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DPAD_LEFT -> { commentsPanelOpen = false; showControls(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (event.nativeKeyEvent.repeatCount == 0) { if (player.isPlaying) player.pause() else player.play() }; true }
                    else -> true
                }
            } else
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
                    if (controlsVisible) { focusedControlIndex = -1; progressFocused = false; controlsVisible = false; controlsInteractionToken++ }
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
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> false
                else -> false
            }
        }
    ) {
        AndroidView(factory = { viewContext ->
            RemotePlayerView(viewContext).apply {
                playerSurfaceView = this
                this.player = player
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                val subtitleSize = playerPreferences.getFloat("subtitleSize", 0.053f)
                setSubtitleAppearance(subtitleSize, subtitleColor)
                setSubtitlePosition(subtitleBottomOffset, subtitleHorizontalPosition)
            }
        }, update = { playerView -> (playerView as? RemotePlayerView)?.apply { setSubtitleAppearance(subtitleSize, subtitleColor); setSubtitlePosition(subtitleBottomOffset, subtitleHorizontalPosition) } }, modifier = Modifier.fillMaxSize())
        if (subtitleTranslationEnabled) {
            val currentCue = translatedCues.firstOrNull { playerPosition >= it.startMs && playerPosition <= it.endMs }
                ?: sourceSubtitleCues.firstOrNull { playerPosition >= it.startMs && playerPosition <= it.endMs }
            currentCue?.let { cue ->
                val height = (playerSurfaceView?.height ?: 1080).toFloat()
                val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
                val captionColor = when (subtitleColor) { "yellow" -> Color.Yellow; "cyan" -> Color.Cyan; "green" -> Color.Green; else -> Color.White }
                val captionModifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth(.9f)
                    .graphicsLayer {
                        translationX = (playerSurfaceView?.width ?: 0) * (subtitleHorizontalPosition - .5f)
                        translationY = -(playerSurfaceView?.height ?: 0) * subtitleBottomOffset
                    }
                Text(
                    cue.text,
                    color = Color.Black,
                    fontSize = (height * subtitleSize / density).sp,
                    lineHeight = (height * subtitleSize * 1.2f / density).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge.copy(drawStyle = Stroke(width = 3.5f)),
                    modifier = captionModifier
                )
                Text(
                    cue.text,
                    color = captionColor,
                    fontSize = (height * subtitleSize / density).sp,
                    lineHeight = (height * subtitleSize * 1.2f / density).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = captionModifier
                )
            }
        }
        if (isBuffering && playbackError == null) CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center), color = Color.White)
        playbackError?.let { Text(it, color = Color.White, modifier = Modifier.align(androidx.compose.ui.Alignment.Center).padding(24.dp)) }
        videoUnavailableReason?.let { reason ->
            Column(
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    .background(Color(0xCC101010), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔊", fontSize = 56.sp)
                Text(stringResource(R.string.player_video_unavailable), color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.player_video_unavailable_reason, reason), color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodyMedium)
            }
        }
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
                TranslatedVideoMetadataText(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        8 -> R.string.player_quality_title
                        9 -> R.string.video_info
                        10 -> R.string.player_comments
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

                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PlayerControl("↶", "Seek backward ${seekStepSeconds} seconds", selected = focusedControlIndex == controlActions.indexOf(0)) { activateControl(0) }
                    PlayerControl(if (playbackActive) "Ⅱ" else "▶", if (playbackActive) "Pause" else "Play", large = true, selected = focusedControlIndex == controlActions.indexOf(1)) { activateControl(1) }
                    PlayerControl("↷", "Seek forward ${seekStepSeconds} seconds", selected = focusedControlIndex == controlActions.indexOf(2)) { activateControl(2) }
                    PlayerControl("↻${if (repeatVideo) "✓" else ""}", "Repeat", selected = focusedControlIndex == controlActions.indexOf(3)) { activateControl(3) }
                    PlayerControl("${playbackSpeed}×", "Playback speed", selected = focusedControlIndex == controlActions.indexOf(4)) { activateControl(4) }
                    PlayerControl("CC", "Subtitles", selected = focusedControlIndex == controlActions.indexOf(5)) { activateControl(5) }
                    if (!channelName.isNullOrBlank()) PlayerControl(if (isSubscribed) "✓" else "＋", "Subscribe", selected = focusedControlIndex == controlActions.indexOf(6)) { activateControl(6) }
                    if (!uuid.isNullOrBlank()) PlayerControl(if (isFavorite) "♥✓" else "♥", "Favorite", selected = focusedControlIndex == controlActions.indexOf(7)) { activateControl(7) }
                    PlayerControl("HQ", "Playback quality settings", selected = focusedControlIndex == controlActions.indexOf(8)) { activateControl(8) }
                    if (!uuid.isNullOrBlank()) PlayerControl("ⓘ", "Video information", selected = focusedControlIndex == controlActions.indexOf(9)) { activateControl(9) }
                    if (!uuid.isNullOrBlank()) PlayerIconControl(Icons.Outlined.ChatBubbleOutline, "Comments", selected = focusedControlIndex == controlActions.indexOf(10)) { activateControl(10) }
                    Text("${formatPlayerTime(playerPosition)}/${formatPlayerTime(player.duration)}", modifier = Modifier.widthIn(min = 58.dp).padding(start = 4.dp), color = Color.White, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                val duration = player.duration.takeIf { it > 0L } ?: 1L
                Slider(value = (playerPosition.toFloat() / duration).coerceIn(0f, 1f), onValueChange = { playerPosition = (it * duration).toLong(); showControls() }, onValueChangeFinished = { player.seekTo(playerPosition) }, modifier = Modifier.fillMaxWidth()
                    .then(if (progressFocused) Modifier.background(Color(0x4475D5FF), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(horizontal = 8.dp) else Modifier))
            }
        }
        if (qualityPanelOpen) {
            val panelTitles = listOf(
                stringResource(R.string.player_video_format),
                stringResource(R.string.player_audio_format),
                stringResource(R.string.player_audio_language),
                stringResource(R.string.player_video_profile),
                stringResource(R.string.player_auto_frame_rate)
            )
            val valueLabels = listOf(
                if (selectedVideoCodec == "auto") stringResource(R.string.player_quality_auto) else codecDisplayName(selectedVideoCodec),
                if (selectedAudioFormat == "auto") stringResource(R.string.player_quality_auto) else codecDisplayName(selectedAudioFormat),
                if (selectedAudioLanguage == "auto") stringResource(R.string.player_quality_auto) else java.util.Locale.forLanguageTag(selectedAudioLanguage).getDisplayName(java.util.Locale.getDefault()),
                if (selectedVideoProfile == 0) stringResource(R.string.player_quality_auto) else "${selectedVideoProfile}p",
                when {
                    Build.VERSION.SDK_INT < 31 || !context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) -> stringResource(R.string.player_quality_unavailable)
                    autoFrameRate -> stringResource(R.string.player_quality_on)
                    else -> stringResource(R.string.player_quality_off)
                }
            )
            Column(
                Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(.34f)
                    .background(Color(0xF21A1A1A))
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.player_quality_title), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.player_quality_hint), color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                panelTitles.forEachIndexed { index, label ->
                    val enabled = index != 4 || (Build.VERSION.SDK_INT >= 31 && context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK))
                    TextButton(
                        onClick = { if (enabled) cycleQualityRow(index, 1) },
                        modifier = Modifier.fillMaxWidth().background(if (qualityPanelRow == index) Color(0xCCFF6D00) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
                        enabled = enabled
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = Color.White)
                            Text(valueLabels[index], color = if (enabled) Color.White.copy(alpha = .75f) else Color.LightGray)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.player_quality_controls_hint), color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (subtitlePanelOpen) {
            Column(
                Modifier.align(androidx.compose.ui.Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.34f)
                    .background(Color(0xF21A1A1A)).verticalScroll(subtitlePanelScrollState).padding(horizontal = 24.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.player_subtitles), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                if (subtitles.isEmpty()) Text(stringResource(R.string.subtitle_none_available), color = Color.White.copy(alpha = .7f))
                TextButton(onClick = { selectSubtitle(-1) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == 0) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) {
                    Text(stringResource(R.string.subtitle_track_off), color = Color.White, modifier = Modifier.fillMaxWidth())
                }
                subtitles.forEachIndexed { index, caption ->
                    val automaticLabel = stringResource(R.string.subtitle_track_auto)
                    val defaultLabel = stringResource(R.string.player_subtitles)
                    val captionLabel = buildString {
                        append(caption.language?.label ?: caption.language?.id ?: defaultLabel)
                        if (caption.automaticallyGenerated == true) append(" · ").append(automaticLabel)
                        if (index == selectedSubtitleIndex) append(" ✓")
                    }
                    TextButton(onClick = { selectSubtitle(index) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == index + 1) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) {
                        Text(captionLabel, color = Color.White, modifier = Modifier.fillMaxWidth())
                    }
                }
                val rememberRow = subtitles.size + 1
                TextButton(onClick = { rememberSubtitleByChannel = !rememberSubtitleByChannel; playerPreferences.edit().putBoolean("rememberSubtitleByChannel", rememberSubtitleByChannel).apply() }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == rememberRow) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) {
                    Text("${stringResource(R.string.subtitle_remember_channel)}  ${if (rememberSubtitleByChannel) "✓" else "○"}", color = Color.White, modifier = Modifier.fillMaxWidth())
                }
                val colorRow = subtitles.size + 2
                val sizeRow = subtitles.size + 3
                val horizontalRow = subtitles.size + 4
                val verticalRow = subtitles.size + 5
                val languageRow = subtitles.size + 6
                val subtitleColorLabel = when (subtitleColor) { "yellow" -> stringResource(R.string.subtitle_color_yellow); "cyan" -> stringResource(R.string.subtitle_color_cyan); "green" -> stringResource(R.string.subtitle_color_green); else -> stringResource(R.string.subtitle_color_white) }
                TextButton(onClick = { subtitleColor = listOf("white", "yellow", "cyan", "green").cyclicNext(subtitleColor, 1); playerPreferences.edit().putString("subtitleColor", subtitleColor).apply(); playerSurfaceView?.setSubtitleAppearance(subtitleSize, subtitleColor) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == colorRow) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) { Text("${stringResource(R.string.subtitle_color)}: $subtitleColorLabel", color = Color.White, modifier = Modifier.fillMaxWidth()) }
                TextButton(onClick = { subtitleSize = listOf(.04f, .053f, .065f).cyclicNext(subtitleSize, 1); playerPreferences.edit().putFloat("subtitleSize", subtitleSize).apply(); playerSurfaceView?.setSubtitleAppearance(subtitleSize, subtitleColor) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == sizeRow) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) { Text("${stringResource(R.string.subtitle_size)}: ${(subtitleSize * 100).toInt()}%", color = Color.White, modifier = Modifier.fillMaxWidth()) }
                TextButton(onClick = { subtitleHorizontalPosition = (subtitleHorizontalPosition + .05f).coerceAtMost(.95f); playerPreferences.edit().putFloat("subtitleHorizontalPosition", subtitleHorizontalPosition).apply(); playerSurfaceView?.setSubtitlePosition(subtitleBottomOffset, subtitleHorizontalPosition) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == horizontalRow) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) { Text("${stringResource(R.string.subtitle_horizontal_position)}: ${(subtitleHorizontalPosition * 100).toInt()}%", color = Color.White, modifier = Modifier.fillMaxWidth()) }
                TextButton(onClick = { adjustSubtitleBottomOffset(0.02f) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == verticalRow) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) {
                    Text(stringResource(R.string.subtitle_position_from_bottom, (subtitleBottomOffset * 100).toInt()), color = Color.White, modifier = Modifier.fillMaxWidth())
                }
                val languageCode = playerPreferences.getString("preferredSubtitleLanguage", "auto") ?: "auto"
                val languageLabel = subtitles.firstOrNull { it.language?.id.equals(languageCode, true) }?.language?.label ?: languageCode.uppercase()
                TextButton(onClick = { val next = (listOf("auto") + subtitles.mapNotNull { it.language?.id }.distinct()).cyclicNext(languageCode, 1); playerPreferences.edit().putString("preferredSubtitleLanguage", next).apply(); subtitles.indexOfFirst { it.language?.id.equals(next, true) }.takeIf { next != "auto" && it >= 0 }?.let(::selectSubtitle) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == languageRow) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) { Text("${stringResource(R.string.subtitle_language)}: $languageLabel", color = Color.White, modifier = Modifier.fillMaxWidth()) }
                val translationLanguageLabel = translationLanguageName(subtitleTranslationLanguage)
                val orderedTranslationLanguages = listOf(interfaceTranslationLanguage) + subtitleTranslationLanguageCodes().filter { it != interfaceTranslationLanguage }.sorted()
                TextButton(onClick = { subtitleTranslationLanguage = orderedTranslationLanguages.cyclicNext(subtitleTranslationLanguage, 1); playerPreferences.edit().putString("subtitleTranslationLanguage", subtitleTranslationLanguage).putBoolean("subtitleTranslationLanguageCustom", true).apply(); if (subtitleTranslationEnabled) startSubtitleTranslation(true) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == subtitles.size + 7) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) {
                    Text("${stringResource(R.string.subtitle_translate_to)}: $translationLanguageLabel", color = Color.White, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { startSubtitleTranslation(!subtitleTranslationEnabled) }, modifier = Modifier.fillMaxWidth().background(if (subtitlePanelRow == subtitles.size + 8) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) {
                    Text("${stringResource(R.string.subtitle_translation)}: ${if (subtitleTranslationEnabled) stringResource(R.string.translation_on) else stringResource(R.string.translation_off)}", color = Color.White, modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.subtitle_translation_hint), color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                if (translationPreparing) Text(stringResource(R.string.subtitle_translation_preparing), color = Color.White.copy(alpha = .75f))
                else if (translationJob?.isActive == true) Text(stringResource(R.string.subtitle_translation_progress, translationProgress, translationTotal), color = Color.White.copy(alpha = .75f))
                translationError?.let { Text(it, color = Color(0xFFFFB4AB)) }
            }
        }
        if (infoPanelOpen) {
            Column(
                Modifier.align(androidx.compose.ui.Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.38f)
                    .background(Color(0xF21A1A1A)).verticalScroll(infoScrollState).padding(horizontal = 24.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TranslatedVideoMetadataText(title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(channelDisplayName ?: channelName ?: stringResource(R.string.channel_unknown), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.video_views, views.toString()), color = Color.White.copy(alpha = .8f))
                formatPublishedDate(publishedAt)?.let { Text(stringResource(R.string.video_published, it), color = Color.White.copy(alpha = .8f)) }
                HorizontalDivider(color = Color.White.copy(alpha = .3f))
                if (description.isBlank()) {
                    Text(stringResource(R.string.player_info_unavailable), color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                } else {
                    TranslatedVideoMetadataText(description, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val host = channelHost.ifBlank { Uri.parse(server).host.orEmpty() }
                        val target = Uri.parse("https://$host/video-channels/${Uri.encode(channelName.orEmpty())}/videos")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    },
                    modifier = Modifier.fillMaxWidth().background(if (infoPanelChannelFocused) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))
                ) { Text(stringResource(R.string.open_channel_video_page), color = Color.White, modifier = Modifier.fillMaxWidth()) }
            }
        }
        if (commentsPanelOpen) {
            Column(
                Modifier.align(androidx.compose.ui.Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.38f)
                    .background(Color(0xF21A1A1A)).verticalScroll(commentsScrollState).padding(horizontal = 24.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.player_comments), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                if (commentsLoading && comments.isEmpty()) Text(stringResource(R.string.player_comments_loading), color = Color.White.copy(alpha = .75f))
                commentsError?.let { Text(stringResource(R.string.player_comments_error, it), color = Color(0xFFFFB4AB)) }
                comments.forEachIndexed { index, (author, body) ->
                    Column(Modifier.fillMaxWidth().background(if (commentsPanelRow == index) Color(0x663F3F3F) else Color.Transparent, RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Text(author.ifBlank { stringResource(R.string.channel_unknown) }, color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(body, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (commentsTotal == 0 && !commentsLoading && commentsError == null) Text(stringResource(R.string.player_comments_empty), color = Color.White.copy(alpha = .75f))
                if (comments.size < commentsTotal) TextButton(onClick = { loadMoreComments() }, modifier = Modifier.background(if (commentsPanelRow >= comments.size) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(8.dp))) { Text(stringResource(R.string.player_comments_more), color = Color.White) }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable private fun PlayerControl(label: String, description: String, large: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(42.dp).heightIn(min = if (large) 52.dp else 48.dp)
        .background(if (selected) Color(0xCCFF6D00) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        .semantics { contentDescription = description }) {
        Text(label, color = Color.White, fontSize = if (large) 26.sp else if (label == "CC") 15.sp else 20.sp, maxLines = 1, softWrap = false)
    }
}

@Composable private fun PlayerIconControl(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)
        .background(if (selected) Color(0xCCFF6D00) else Color.Transparent, RoundedCornerShape(12.dp))
        .semantics { contentDescription = description }) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

private fun codecDisplayName(mimeType: String): String = when (mimeType.lowercase()) {
    "video/avc" -> "AVC / H.264"
    "video/hevc" -> "HEVC / H.265"
    "video/av01" -> "AV1"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/x-vnd.on2.vp8" -> "VP8"
    "audio/mp4a-latm" -> "AAC"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/ac3" -> "AC-3"
    "audio/eac3" -> "E-AC-3"
    else -> mimeType.substringAfter('/').uppercase()
}

private fun <T> List<T>.cyclicNext(current: T, delta: Int): T {
    if (isEmpty()) return current
    val currentIndex = indexOf(current).takeIf { it >= 0 } ?: 0
    return this[(currentIndex + delta + size) % size]
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
    private var subtitleOffsetFromBottom = 0.20f
    private var subtitleHorizontalPosition = 0.50f

    fun setSubtitleOffsetFromBottom(fraction: Float) {
        subtitleOffsetFromBottom = fraction.coerceIn(0.02f, 0.50f)
        applySubtitleOffset()
    }

    fun setSubtitlePosition(bottom: Float, horizontal: Float) {
        subtitleOffsetFromBottom = bottom.coerceIn(0.02f, 0.50f)
        subtitleHorizontalPosition = horizontal.coerceIn(0.05f, 0.95f)
        applySubtitleOffset()
    }

    fun setSubtitleAppearance(fraction: Float, colorName: String) {
        val color = when (colorName) {
            "yellow" -> android.graphics.Color.YELLOW
            "cyan" -> android.graphics.Color.CYAN
            "green" -> android.graphics.Color.GREEN
            else -> android.graphics.Color.WHITE
        }
        subtitleView?.setFractionalTextSize(fraction)
        subtitleView?.setStyle(CaptionStyleCompat(color, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        applySubtitleOffset()
    }

    private fun applySubtitleOffset() {
        subtitleView?.let { view ->
            // Translate the entire cue layer so this also works for subtitles with
            // an explicit WebVTT/TTML line position that ignores bottom padding.
            view.setBottomPaddingFraction(0f)
            view.translationY = -height * subtitleOffsetFromBottom
            view.translationX = width * (subtitleHorizontalPosition - 0.5f)
        }
    }
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


