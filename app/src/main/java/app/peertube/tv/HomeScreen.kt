package app.peertube.tv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Home(m: HomeModel = viewModel()) {
    val density = LocalDensity.current
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density * m.uiScale, density.fontScale)) {
        HomeContent(m)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun HomeContent(m: HomeModel) {
    val context = LocalContext.current
    val accountManager = remember(context) { PeerTubeAccounts.get(context) }
    val accountState by accountManager.state.collectAsState()
    LaunchedEffect(accountManager) { runCatching { accountManager.initialize() } }
    val navigationTone = remember { android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 35) }
    var lastNavigationToneAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(navigationTone) { onDispose { navigationTone.release() } }
    val isTvDevice = remember { isTelevisionDevice(context) }
    val tvProviderPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            m.loadHomePage()
            m.loadRecommendations()
        }
    }
    LaunchedEffect(isTvDevice) {
        val permission = "com.android.providers.tv.permission.WRITE_EPG_DATA"
        if (isTvDevice && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            tvProviderPermissionLauncher.launch(permission)
        }
    }
    val lifecycleOwner = context as? LifecycleOwner
    val focusManager = LocalFocusManager.current
    val menuFocusRequester = remember { FocusRequester() }
    val searchButtonFocusRequester = remember { FocusRequester() }
    val menuFocusScope = rememberCoroutineScope()
    val pendingMenuFocus = remember { arrayOfNulls<Pair<String, Job>>(1) }
    val expandedMenuFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val collapsedMenuFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val homeFirstCardFocusRequester = remember { FocusRequester() }
    val homeLatestFirstCardFocusRequester = remember { FocusRequester() }
    val homeGridRestoreFocusRequester = remember { FocusRequester() }
    val homeRecommendationRestoreFocusRequester = remember { FocusRequester() }
    val homeLatestRestoreFocusRequester = remember { FocusRequester() }
    val sectionFirstFocusRequester = remember { FocusRequester() }
    val sectionRestoreFocusRequester = remember { FocusRequester() }
    val firstSearchResultFocusRequester = remember { FocusRequester() }
    val searchRestoreFocusRequester = remember { FocusRequester() }
    val instancesConnectFocusRequester = remember { FocusRequester() }
    val checkAllServersFocusRequester = remember { FocusRequester() }
    val firstServerConnectFocusRequester = remember { FocusRequester() }
    // Focus bookkeeping is consumed by the restore effects on menu/tab changes;
    // it does not need to invalidate the whole screen on every D-pad movement.
    val lastFocusedCardBySection = remember { mutableMapOf<String, String>() }
    var focusedMenuSectionKey by remember { mutableStateOf("home") }
    var collapsedMenuItemFocused by remember { mutableStateOf(false) }
    var focusedVideoAtLeftBoundary by remember { mutableStateOf(false) }
    var sectionFocusPending by remember { mutableStateOf(false) }
    var searchResultFocusPending by remember { mutableStateOf(false) }
    var returnSearchFocusPending by remember { mutableStateOf(false) }
    var searchButtonFocused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var headerHidden by remember { mutableStateOf(false) }
    var settingsOverlay by remember { mutableStateOf(false) }
    var focusedVideoMenu by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var clearHistoryDialog by remember { mutableStateOf(false) }
    var clearHistoryPeriodDialog by remember { mutableStateOf(false) }
    var settingsCategory by remember { mutableStateOf<String?>(null) }
    var settingsSubpage by remember { mutableStateOf<String?>(null) }
    var homeInitialized by remember { mutableStateOf(false) }
    var historyPeriodHours by remember { mutableIntStateOf(1) }
    val periodLabel = when (historyPeriodHours) { 2 -> stringResource(R.string.history_period_2); 3 -> stringResource(R.string.history_period_3); 6 -> stringResource(R.string.history_period_6); 24 -> stringResource(R.string.history_period_24); else -> stringResource(R.string.history_period_1) }
    val backupScope = rememberCoroutineScope()
    val exportBackup: () -> Unit = {
        backupScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { createSettingsBackup(context) }
                .onSuccess { path ->
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, context.getString(R.string.backup_saved, path), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                .onFailure { error ->
                    withContext(kotlinx.coroutines.Dispatchers.Main) { m.error = error.localizedMessage ?: context.getString(R.string.error_request) }
                }
        }
    }
    val updateFocusedVideoMenu: (String, (() -> Unit)?, (() -> Unit)?, Boolean) -> Unit = { videoId, openMenu, _, atLeftBoundary ->
        if (openMenu != null) {
            focusedVideoMenu = videoId to openMenu
            lastFocusedCardBySection[m.tab] = videoId
            focusedVideoAtLeftBoundary = atLeftBoundary
        } else if (focusedVideoMenu?.first == videoId) {
            focusedVideoMenu = null
            focusedVideoAtLeftBoundary = false
        }
    }
    val restoreBackupFromUri: (Uri?) -> Unit = { uri ->
        if (uri != null) backupScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { restoreSettingsBackup(context, uri) }
            }
            result.onSuccess {
                m.init(context)
                m.syncWatchProgress()
                when (m.tab) {
                    "popular" -> m.loadPopular()
                    "instances" -> m.loadInstances()
                    "recommendations" -> m.loadRecommendations()
                    "subscriptions" -> m.loadSubscriptions()
                    "channels" -> m.syncSubscriptions()
                    "home" -> m.loadHomePage()
                }
            }.onFailure {
                m.error = it.localizedMessage ?: context.getString(R.string.error_request)
                android.widget.Toast.makeText(context, m.error, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), restoreBackupFromUri)
    val importBackupFallbackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), restoreBackupFromUri)
    val launchBackupRestore: () -> Unit = {
        try {
            // A wildcard lets Android's system document picker work on TV devices
            // whose file providers do not advertise application/zip explicitly.
            importBackupLauncher.launch(arrayOf("*/*"))
        } catch (_: android.content.ActivityNotFoundException) {
            try { importBackupFallbackLauncher.launch("*/*") }
            catch (error: android.content.ActivityNotFoundException) {
                m.error = error.localizedMessage ?: context.getString(R.string.error_request)
                android.widget.Toast.makeText(context, m.error, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner, m) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                m.syncWatchProgress()
                m.syncFavorites()
                m.syncSubscriptions()
                if (m.tab == "history") m.loadHistory()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(menuExpanded, focusedMenuSectionKey) {
        if (menuExpanded) {
            kotlinx.coroutines.yield()
            runCatching { (expandedMenuFocusRequesters[focusedMenuSectionKey] ?: menuFocusRequester).requestFocus() }
        }
    }
    LaunchedEffect(m.tab) { headerHidden = false }
    LaunchedEffect(menuExpanded, returnSearchFocusPending, m.showSearchIcon) {
        if (!menuExpanded && returnSearchFocusPending) {
            kotlinx.coroutines.delay(50)
            if (m.showSearchIcon) runCatching { searchButtonFocusRequester.requestFocus() }
            returnSearchFocusPending = false
        }
    }
    LaunchedEffect(menuExpanded, sectionFocusPending, homeInitialized, m.tab, m.videos.size, m.homePageVideos.size, m.recommendations.size, m.popularVideos.size, m.liveVideos.size, m.channelVideos.size, m.serverChannels.size, m.subscriptionVideos.size, m.instances.size, lastFocusedCardBySection[m.tab], m.homePageDisplayMode) {
        if (!menuExpanded && sectionFocusPending) {
            kotlinx.coroutines.delay(280)
            val rememberedId = lastFocusedCardBySection[m.tab]
            val target = when {
                m.tab == "home" && m.homePageDisplayMode == "recommended" && rememberedId != null && m.recommendations.any { it.uuid == rememberedId } -> homeRecommendationRestoreFocusRequester
                m.tab == "home" && m.homePageDisplayMode == "recommended" && rememberedId != null && m.videos.any { it.uuid == rememberedId } -> homeLatestRestoreFocusRequester
                m.tab == "home" && m.homePageDisplayMode != "recommended" && rememberedId != null && m.homePageVideos.any { it.uuid == rememberedId } -> homeGridRestoreFocusRequester
                m.tab == "sepia" && rememberedId != null && m.videos.any { it.uuid == rememberedId } -> searchRestoreFocusRequester
                rememberedId != null -> sectionRestoreFocusRequester
                m.tab == "home" -> if (m.recommendations.isEmpty()) homeLatestFirstCardFocusRequester else homeFirstCardFocusRequester
                else -> sectionFirstFocusRequester
            }
            val focused = runCatching { target.requestFocus() }.getOrDefault(false)
            if (!focused) {
                val fallback = if (m.tab == "home") {
                    if (m.recommendations.isEmpty()) homeLatestFirstCardFocusRequester else homeFirstCardFocusRequester
                } else if (m.tab == "sepia") firstSearchResultFocusRequester else sectionFirstFocusRequester
                if (runCatching { fallback.requestFocus() }.getOrDefault(false)) sectionFocusPending = false
            } else sectionFocusPending = false
        }
    }
    BackHandler {
        if (clearHistoryDialog) clearHistoryDialog = false
        else if (clearHistoryPeriodDialog) clearHistoryPeriodDialog = false
        else if (settingsOverlay && settingsSubpage != null) settingsSubpage = null
        else if (settingsOverlay && settingsCategory != null) { settingsOverlay = false; settingsCategory = null }
        else if (settingsOverlay) settingsOverlay = false
        else if (menuExpanded) { menuExpanded = false; pendingMenuFocus[0]?.second?.cancel(); pendingMenuFocus[0] = null; sectionFocusPending = true }
        else if (m.tab == "channel") m.tab = m.channelReturnTab
        else if (m.tab == "server_channels") m.tab = "channels"
        else when (m.exitMode) {
            "single" -> (context as? Activity)?.finish()
            "double" -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastBackPressAt <= 2_000L) (context as? Activity)?.finish()
                else {
                    lastBackPressAt = now
                    android.widget.Toast.makeText(context, context.getString(R.string.exit_double_prompt), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            else -> menuExpanded = true
        }
    }
    LaunchedEffect(Unit) {
        m.init(context)
        val hasPendingChannelNavigation = (context as? MainActivity)?.pendingChannelNavigation != null
        m.tab = if (hasPendingChannelNavigation) "channel" else m.launchTab
        sectionFocusPending = true
        m.syncWatchProgress()
        if (!hasPendingChannelNavigation) {
            when (m.tab) {
                "popular" -> m.loadPopular()
                "instances" -> m.loadInstances()
                "recommendations" -> m.loadRecommendations()
                "subscriptions" -> m.loadSubscriptions()
                "channels" -> m.syncSubscriptions()
                "home" -> m.loadHomePage()
            }
            if (m.tab != "recommendations") m.loadRecommendations()
        }
        homeInitialized = true
    }
    val pendingChannelNavigation = (context as? MainActivity)?.pendingChannelNavigation
    LaunchedEffect(homeInitialized, pendingChannelNavigation) {
        val target = pendingChannelNavigation
        if (homeInitialized && target != null) {
            m.loadChannelVideos(target.name, target.host)
            (context as? MainActivity)?.pendingChannelNavigation = null
        }
    }
    LaunchedEffect(m.tab) {
        if (m.tab == "history") m.loadHistory()
        if (m.tab == "live") {
            m.loadLive()
            while (true) {
                kotlinx.coroutines.delay(60_000)
                m.loadLive()
            }
        }
    }
    var searchText by remember { mutableStateOf("") }
    var hasSearchSubmitted by remember { mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (recognized.isNotEmpty()) {
                searchText = recognized
                m.query = recognized
                hasSearchSubmitted = false
                m.tab = "sepia"
            }
        }
    }
    val startVoiceSearch: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { speechLauncher.launch(intent) }
        catch (_: android.content.ActivityNotFoundException) { m.error = context.getString(R.string.voice_unavailable) }
    }
    val openVideoInQueue: (Video, List<Video>) -> Unit = { video, queue ->
        if (m.history.none { it.uuid == video.uuid }) m.history.add(0, video)
        else { m.history.removeAll { it.uuid == video.uuid }; m.history.add(0, video) }
        while (m.history.size > 100) m.history.removeAt(m.history.lastIndex)
        m.markVideoViewed(video.uuid)
        m.save()
        if (m.tab == "recommendations") m.loadRecommendations()
        val channelName = video.channel?.name ?: video.account?.name
        val channelHost = video.channel?.host ?: video.account?.host ?: video.host
        val queueForPlayback = if (m.afterVideoAction == "next_channel" && !channelName.isNullOrBlank()) queue.filter {
            (it.channel?.name ?: it.account?.name).equals(channelName, ignoreCase = true) &&
                (it.channel?.host ?: it.account?.host ?: it.host).equals(channelHost, ignoreCase = true)
        } else queue
        val playbackQueue = queueForPlayback.map { item ->
            val host = item.channel?.host ?: item.account?.host ?: item.host
                ?: item.sourceServer(m.server).removePrefix("https://").removePrefix("http://")
            PlaybackQueueItem(
                uuid = item.uuid,
                server = item.sourceServer(m.server),
                url = item.playableUrl()?.takeUnless { item.state?.id in listOf(4, 5) },
                title = item.name,
                views = item.views,
                publishedAt = item.publishedAt,
                channelName = item.channel?.name ?: item.account?.name,
                channelHost = host,
                channelDisplayName = item.channel?.displayName ?: item.account?.displayName ?: item.channel?.name ?: item.account?.name,
                audioOnly = item.isAudioOnlySource(),
                liveStateId = item.state?.id
            )
        }
        val queueIndex = playbackQueue.indexOfFirst { it.uuid == video.uuid }
        context.startActivity(Intent(context, PlayerActivity::class.java)
            .putExtra("url", video.playableUrl()?.takeUnless { video.state?.id in listOf(4, 5) })
            .putExtra("audioOnly", video.isAudioOnlySource())
            .putExtra("liveStateId", video.state?.id ?: 0)
            .putExtra("uuid", video.uuid)
            .putExtra("server", video.sourceServer(m.server))
            .putExtra("title", video.name)
            .putExtra("views", video.views)
            .putExtra("publishedAt", video.publishedAt)
            .putExtra("channelName", video.channel?.name ?: video.account?.name)
            .putExtra("channelHost", video.channel?.host ?: video.account?.host ?: video.host ?: video.sourceServer(m.server).removePrefix("https://").removePrefix("http://"))
            .putExtra("channelDisplayName", video.channel?.displayName ?: video.account?.displayName ?: video.channel?.name ?: video.account?.name)
            .putExtra("playbackQueue", PeerTubeGson.instance.toJson(playbackQueue))
            .putExtra("queueIndex", queueIndex)
            .putExtra("afterVideoAction", m.afterVideoAction)
            .putExtra("playerOverlayDurationSeconds", m.playerOverlayDurationSeconds)
            .putExtra("showPlayerClock", m.showPlayerClock)
            .putExtra("playerClockPosition", m.playerClockPosition)
            .putExtra("playerClockSize", m.playerClockSize)
            .putExtra("playerClockBackdrop", m.playerClockBackdrop)
            .putExtra("resumePlayback", m.resumePlayback)
            .putExtra("seekStepSeconds", m.seekStepSeconds)
            .putExtra("clock24Hour", m.clock24Hour)
            .putExtra("bufferProfile", m.bufferProfile)
            .putExtra("playerVolume", m.playerVolume)
            .putExtra("watchedRecommendationThreshold", m.watchedRecommendationThreshold))
    }
    val toggleFavorite: (Video) -> Unit = { video ->
        if (video.uuid in m.favoriteIds) {
            m.favorites.removeAll { it.uuid == video.uuid }
            m.favoriteIds.remove(video.uuid)
        } else {
            m.favorites.add(video)
            m.favoriteIds[video.uuid] = true
        }
        m.save()
    }
    val openChannelVideos: (Video) -> Unit = { video ->
        val channelName = video.channel?.name ?: video.account?.name
        val host = video.channel?.host ?: video.account?.host ?: video.host ?: video.sourceServer(m.server).removePrefix("https://").removePrefix("http://")
        if (!channelName.isNullOrBlank()) { sectionFocusPending = true; m.loadChannelVideos(channelName, host) }
    }
    var searchSort by remember { mutableStateOf("-match") }
    var searchCategory by remember { mutableStateOf("any") }
    var searchDuration by remember { mutableStateOf("any") }
    var searchLiveOnly by remember { mutableStateOf(false) }
    var searchAvailabilityFilter by remember { mutableStateOf("unknown_or_available") }
    LaunchedEffect(m.searchSettings.availabilityFilter) { searchAvailabilityFilter = m.searchSettings.availabilityFilter }
    val sortOptions = listOf(
        "-match" to stringResource(R.string.search_relevance),
        "-publishedAt" to stringResource(R.string.search_sort_date),
        "-views" to stringResource(R.string.search_sort_views),
        "-likes" to stringResource(R.string.search_sort_likes),
        "-duration" to stringResource(R.string.search_sort_duration),
        "availability" to stringResource(R.string.search_sort_accessibility)
    )
    val categoryOptions = listOf(
        "any" to stringResource(R.string.search_category_any),
        "1" to stringResource(R.string.search_category_music),
        "5" to stringResource(R.string.search_category_sport),
        "7" to stringResource(R.string.search_category_gaming),
        "11" to stringResource(R.string.search_category_news),
        "13" to stringResource(R.string.search_category_education)
    )
    val durationOptions = listOf(
        "any" to stringResource(R.string.search_duration_any),
        "short" to stringResource(R.string.search_duration_short),
        "medium" to stringResource(R.string.search_duration_medium),
        "long" to stringResource(R.string.search_duration_long)
    )
    val submitSearch: () -> Unit = {
        val term = searchText.trim()
        if (term.isNotEmpty()) {
            hasSearchSubmitted = true
            m.query = term
            val durationMin = when (searchDuration) { "medium" -> 240; "long" -> 1200; else -> null }
            val durationMax = when (searchDuration) { "short" -> 240; "medium" -> 1200; else -> null }
            m.searchSepia(term, searchSort, searchCategory.toIntOrNull(), durationMin, durationMax, searchLiveOnly, page = 0)
        }
    }
    LaunchedEffect(m.tab, searchText, searchSort, searchCategory, searchDuration, searchLiveOnly) {
        if (m.tab == "sepia") {
            if (searchText.isBlank()) {
                hasSearchSubmitted = false
            } else {
                kotlinx.coroutines.delay(350)
                submitSearch()
            }
        }
    }
    LaunchedEffect(m.tab, hasSearchSubmitted, m.videos.size, searchResultFocusPending) {
        if (m.tab == "sepia" && hasSearchSubmitted && m.videos.isNotEmpty() && searchResultFocusPending) {
            kotlinx.coroutines.delay(80)
            runCatching { firstSearchResultFocusRequester.requestFocus() }
            searchResultFocusPending = false
        }
    }
    val navigateToSection: (String, Boolean) -> Unit = { key, closeMenu ->
        focusedMenuSectionKey = key
        if (closeMenu) { menuExpanded = false; pendingMenuFocus[0]?.second?.cancel(); pendingMenuFocus[0] = null; returnSearchFocusPending = false; sectionFocusPending = true }
        val sectionChanged = m.tab != key
        if (key == "settings") {
            settingsCategory = null
            settingsSubpage = null
            settingsOverlay = false
            m.tab = "settings"
        } else {
            settingsOverlay = false
            m.tab = key
            if (sectionChanged) {
                when (key) {
                    "popular" -> if (m.popularVideosServer != m.server || m.popularVideos.isEmpty()) m.loadPopular()
                    "home" -> if (m.videos.isEmpty() || m.homePageDisplayMode != "recommended" && m.homePageVideos.isEmpty()) m.loadHomePage()
                    "instances" -> if (m.instances.isEmpty()) m.loadInstances()
                    "recommendations" -> if (m.recommendations.isEmpty()) m.loadRecommendations()
                    "subscriptions" -> if (m.subscriptionVideos.isEmpty()) m.loadSubscriptions()
                    "channels" -> m.syncSubscriptions()
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) BoxScope@{
      Row(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            val keyCode = event.key.keyCode.toInt()
            if (event.type == KeyEventType.KeyDown && m.navigationSounds) {
                val sound = when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> android.view.SoundEffectConstants.NAVIGATION_LEFT
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> android.view.SoundEffectConstants.NAVIGATION_RIGHT
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> android.view.SoundEffectConstants.NAVIGATION_UP
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> android.view.SoundEffectConstants.NAVIGATION_DOWN
                    else -> null
                }
                val now = android.os.SystemClock.uptimeMillis()
                if (sound != null && now - lastNavigationToneAt >= 70L) {
                    navigationTone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 32)
                    lastNavigationToneAt = now
                }
            }
            val isMenuKey = keyCode == android.view.KeyEvent.KEYCODE_MENU || keyCode == android.view.KeyEvent.KEYCODE_INFO || keyCode == android.view.KeyEvent.KEYCODE_SETTINGS
            when {
                event.type == KeyEventType.KeyDown && menuExpanded && event.key == Key.DirectionRight -> {
                    menuExpanded = false
                    pendingMenuFocus[0]?.second?.cancel()
                    pendingMenuFocus[0] = null
                    returnSearchFocusPending = false
                    sectionFocusPending = true
                    true
                }
                event.type == KeyEventType.KeyDown && !menuExpanded && collapsedMenuItemFocused && event.key == Key.DirectionRight -> {
                    sectionFocusPending = true
                    true
                }
                event.type == KeyEventType.KeyDown && menuExpanded && event.key == Key.DirectionUp && focusedMenuSectionKey == "home" && m.showSearchIcon -> {
                    menuExpanded = false
                    pendingMenuFocus[0]?.second?.cancel()
                    pendingMenuFocus[0] = null
                    sectionFocusPending = false
                    returnSearchFocusPending = true
                    true
                }
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && !menuExpanded -> {
                    if (collapsedMenuItemFocused) { menuExpanded = true; true }
                    else if (focusedVideoMenu != null && !focusedVideoAtLeftBoundary) {
                        if (focusManager.moveFocus(FocusDirection.Left)) true
                        else if (m.miniSectionRailEnabled && collapsedMenuFocusRequesters[focusedMenuSectionKey] != null) {
                            runCatching { collapsedMenuFocusRequesters[focusedMenuSectionKey]?.requestFocus() }
                            true
                        } else {
                            menuExpanded = true
                            true
                        }
                    }
                    else if (m.miniSectionRailEnabled && focusedVideoMenu != null && collapsedMenuFocusRequesters[focusedMenuSectionKey] != null) {
                        runCatching { collapsedMenuFocusRequesters[focusedMenuSectionKey]?.requestFocus() }
                        true
                    }
                    else if (focusedVideoMenu != null && focusedVideoAtLeftBoundary && !m.miniSectionRailEnabled) {
                        menuExpanded = true
                        true
                    }
                    else if (focusManager.moveFocus(FocusDirection.Left)) true else { menuExpanded = true; true }
                }
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && !menuExpanded && m.showSearchIcon && !searchButtonFocused -> {
                    if (focusManager.moveFocus(FocusDirection.Up)) true
                    else { runCatching { searchButtonFocusRequester.requestFocus() }; true }
                }
                isMenuKey && event.type == KeyEventType.KeyDown && focusedVideoMenu != null -> {
                    focusedVideoMenu?.second?.invoke()
                    true
                }
                else -> false
            }
        }
      ) {
        if (menuExpanded) {
            Surface(
                modifier = Modifier.width(206.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(0.dp)
            ) {
              Column(Modifier.fillMaxSize().focusGroup().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(77.dp))
                listOf("home" to R.string.nav_home, "popular" to R.string.nav_popular, "recommendations" to R.string.nav_recommendations, "live" to R.string.nav_live, "subscriptions" to R.string.nav_subscriptions, "channels" to R.string.nav_channels, "instances" to R.string.nav_instances, "history" to R.string.nav_history, "favorites" to R.string.nav_favorites, "settings" to R.string.nav_settings).filter { (key, _) -> key == "home" || key == "settings" || key !in m.hiddenSections }.forEach { (key, label) ->
                    val isSelected = if (key == "settings") (m.tab == "settings" || settingsOverlay) else m.tab == key
                    val selectedStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = if (isSelected) 20.sp else 16.sp, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
                    var navFocused by remember(key) { mutableStateOf(false) }
                    val itemFocusRequester = remember(key) { FocusRequester() }
                    expandedMenuFocusRequesters[key] = itemFocusRequester
                    val icon = when (key) {
                        "home" -> Icons.Filled.Home
                        "popular" -> Icons.Filled.Whatshot
                        "live" -> Icons.Filled.LiveTv
                        "subscriptions" -> Icons.Filled.Subscriptions
                        "channels" -> Icons.Filled.VideoLibrary
                        "recommendations" -> Icons.Filled.Recommend
                        "instances" -> Icons.Filled.Public
                        "history" -> Icons.Filled.History
                        "favorites" -> Icons.Filled.Favorite
                        else -> Icons.Filled.Settings
                    }
                    NavigationDrawerItem(label = { Text(stringResource(label), style = selectedStyle.copy(fontSize = if (isSelected) 18.sp else 15.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = if (navFocused) Modifier.basicMarquee(velocity = 30.dp * m.tickerSpeed) else Modifier) }, selected = isSelected, icon = { Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }, shape = RectangleShape, colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color.Transparent, unselectedContainerColor = Color.Transparent, selectedTextColor = MaterialTheme.colorScheme.onSurface, selectedIconColor = MaterialTheme.colorScheme.primary), onClick = { navigateToSection(key, true) }, modifier = Modifier.fillMaxWidth().height(38.dp).focusRequester(itemFocusRequester).scale(if (navFocused) 1.06f else 1f).onFocusChanged {
                        navFocused = it.isFocused
                        if (it.isFocused) {
                            focusedMenuSectionKey = key
                            pendingMenuFocus[0]?.second?.cancel()
                            pendingMenuFocus[0] = key to menuFocusScope.launch {
                                delay(220)
                                if (menuExpanded && pendingMenuFocus[0]?.first == key) {
                                    pendingMenuFocus[0] = null
                                    navigateToSection(key, false)
                                }
                            }
                        } else if (pendingMenuFocus[0]?.first == key) {
                            pendingMenuFocus[0]?.second?.cancel()
                            pendingMenuFocus[0] = null
                        }
                    }.then(if (key == "home") Modifier.focusRequester(menuFocusRequester) else Modifier))
                }
              }
            }
        } else if (m.miniSectionRailEnabled) {
            val railItems = listOf("home" to R.string.nav_home, "popular" to R.string.nav_popular, "recommendations" to R.string.nav_recommendations, "live" to R.string.nav_live, "subscriptions" to R.string.nav_subscriptions, "channels" to R.string.nav_channels, "instances" to R.string.nav_instances, "history" to R.string.nav_history, "favorites" to R.string.nav_favorites, "settings" to R.string.nav_settings).filter { (key, _) -> key == "home" || key == "settings" || key !in m.hiddenSections }
            Surface(Modifier.width(60.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxSize().padding(top = 77.dp).focusGroup(), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    railItems.forEach { (key, label) ->
                        val isSelected = if (key == "settings") (m.tab == "settings" || settingsOverlay) else m.tab == key
                        val icon = when (key) {
                            "home" -> Icons.Filled.Home
                            "popular" -> Icons.Filled.Whatshot
                            "live" -> Icons.Filled.LiveTv
                            "subscriptions" -> Icons.Filled.Subscriptions
                            "channels" -> Icons.Filled.VideoLibrary
                            "recommendations" -> Icons.Filled.Recommend
                            "instances" -> Icons.Filled.Public
                            "history" -> Icons.Filled.History
                            "favorites" -> Icons.Filled.Favorite
                            else -> Icons.Filled.Settings
                        }
                        var railFocused by remember(key) { mutableStateOf(false) }
                        val railFocusRequester = remember(key) { FocusRequester() }
                        collapsedMenuFocusRequesters[key] = railFocusRequester
                        IconButton(onClick = { navigateToSection(key, false) }, modifier = Modifier.size(44.dp).focusRequester(railFocusRequester)
                            .onFocusChanged {
                                railFocused = it.isFocused
                                collapsedMenuItemFocused = it.isFocused
                                if (it.isFocused) {
                                    focusedMenuSectionKey = key
                                    pendingMenuFocus[0]?.second?.cancel()
                                    pendingMenuFocus[0] = key to menuFocusScope.launch {
                                        delay(220)
                                        if (!menuExpanded && focusedMenuSectionKey == key && pendingMenuFocus[0]?.first == key) {
                                            pendingMenuFocus[0] = null
                                            navigateToSection(key, false)
                                        }
                                    }
                                } else if (pendingMenuFocus[0]?.first == key) {
                                    pendingMenuFocus[0]?.second?.cancel()
                                    pendingMenuFocus[0] = null
                                }
                            }
                            .background(if (isSelected || railFocused) MaterialTheme.colorScheme.primary.copy(alpha = if (railFocused) .24f else .12f) else Color.Transparent, RoundedCornerShape(12.dp))) {
                            Icon(icon, contentDescription = stringResource(label), tint = if (isSelected || railFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).focusProperties { canFocus = !menuExpanded }.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
            val contentTopInset by animateDpAsState(targetValue = if (headerHidden) 0.dp else 70.dp, label = "contentTopInset")
            Spacer(Modifier.height(contentTopInset))
            if (m.tab == "instances") {
                Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(value = m.server, onValueChange = { m.server = it }, label = { Text(stringResource(R.string.server_hint)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { instancesConnectFocusRequester.requestFocus(); true } else false }.focusProperties { down = instancesConnectFocusRequester }.focusRequester(sectionFirstFocusRequester), singleLine = true)
                Button(onClick = { m.save(); m.loadHomePage(); m.tab = "home" }, modifier = Modifier.padding(top = 8.dp).focusRequester(instancesConnectFocusRequester).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { checkAllServersFocusRequester.requestFocus(); true } else false }.focusProperties { up = sectionFirstFocusRequester; down = checkAllServersFocusRequester }) { Text(stringResource(R.string.connect)) }
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Text(stringResource(R.string.directory_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.directory_help), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (m.isLoadingInstances) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.discoveredServers.isNotEmpty()) {
                        item {
                            var checkAllFocused by remember { mutableStateOf(false) }
                            var sortFocused by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.search_source_local), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = m::checkCachedServersAvailability,
                                    enabled = !m.isCheckingServerAvailability,
                                    modifier = Modifier
                                        .scale(if (checkAllFocused) 1.08f else 1f)
                                        .background(if (checkAllFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .focusRequester(checkAllServersFocusRequester)
                                        .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { firstServerConnectFocusRequester.requestFocus(); true } else false }
                                        .focusProperties { down = firstServerConnectFocusRequester }
                                        .onFocusChanged { checkAllFocused = it.isFocused }
                                        .heightIn(min = 52.dp)
                                ) {
                                    Text(if (m.isCheckingServerAvailability) stringResource(R.string.server_checking) else stringResource(R.string.server_check_all))
                                }
                                TextButton(
                                    onClick = m::cycleServerSortMode,
                                    modifier = Modifier
                                        .scale(if (sortFocused) 1.08f else 1f)
                                        .background(if (sortFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .focusProperties { down = firstServerConnectFocusRequester }
                                        .onFocusChanged { sortFocused = it.isFocused }
                                        .heightIn(min = 52.dp)
                                ) {
                                    Text(stringResource(when (m.serverSortMode) { "default" -> R.string.server_sort_availability; "availability" -> R.string.server_sort_videos; else -> R.string.server_sort_default }))
                                }
                            }
                        }
                        if (m.serverAvailabilityCheckSummary != null || m.isCheckingServerAvailability) item {
                            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                if (m.isCheckingServerAvailability) LinearProgressIndicator(Modifier.fillMaxWidth())
                                m.serverAvailabilityCheckSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        items(m.sortedDiscoveredServers, key = { "saved-${it.host}" }) { discovered ->
                            var checkFocused by remember(discovered.host) { mutableStateOf(false) }
                            val isFirstDiscovered = discovered == m.sortedDiscoveredServers.firstOrNull()
                            val connectServerRequester = remember(discovered.host) { FocusRequester() }
                            val connectTargetRequester = if (isFirstDiscovered) firstServerConnectFocusRequester else connectServerRequester
                            val checkServerRequester = remember(discovered.host) { FocusRequester() }
                            val favoriteServerRequester = remember(discovered.host) { FocusRequester() }
                            val moveServerUpRequester = remember(discovered.host) { FocusRequester() }
                            val moveServerDownRequester = remember(discovered.host) { FocusRequester() }
                            val now = System.currentTimeMillis()
                            fun statusResource(status: String, checkedAt: Long): Int = when {
                                checkedAt <= 0L -> R.string.server_status_unknown
                                now - checkedAt > 24L * 60L * 60L * 1000L -> R.string.server_status_stale
                                status == "available" -> R.string.server_status_available
                                status == "unavailable" -> R.string.server_status_unavailable
                                else -> R.string.server_status_inconclusive
                            }
                            val freshApiCheck = discovered.apiCheckedAt > 0L && now - discovered.apiCheckedAt <= 24L * 60L * 60L * 1000L
                            val apiMarkColor = when {
                                freshApiCheck && discovered.apiStatus == "available" -> Color(0xFF4CAF50)
                                freshApiCheck && discovered.apiStatus == "unavailable" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("●", color = apiMarkColor, modifier = Modifier.padding(end = 8.dp))
                                            Text(discovered.name?.takeIf(String::isNotBlank) ?: discovered.host, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(discovered.host, style = MaterialTheme.typography.bodyMedium)
                                        Text("${discovered.version ?: "PeerTube"} · ${discovered.country.orEmpty()} · ${discovered.totalVideos ?: "—"} videos", style = MaterialTheme.typography.bodySmall)
                                        val apiStatusText = stringResource(statusResource(discovered.apiStatus, discovered.apiCheckedAt))
                                        val apiDetail = discovered.apiError?.let { " · $it" }.orEmpty()
                                        Text("● API $apiStatusText$apiDetail", style = MaterialTheme.typography.bodySmall, color = apiMarkColor)
                                        Text("${stringResource(R.string.availability_metadata)} ${stringResource(statusResource(discovered.metadataStatus, discovered.metadataCheckedAt))} · ${stringResource(R.string.availability_video_stream)} ${stringResource(statusResource(discovered.streamStatus, discovered.streamCheckedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(Modifier.focusGroup()) {
                                        TextButton(
                                            onClick = { m.server = "https://${discovered.host}"; m.save(); m.tab = "home"; m.loadHomePage() },
                                            modifier = Modifier.focusRequester(connectTargetRequester)
                                                .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { checkServerRequester.requestFocus(); true } else false }
                                                .focusProperties { if (isFirstDiscovered) up = checkAllServersFocusRequester; down = checkServerRequester }
                                                .heightIn(min = 52.dp)
                                        ) { Text(stringResource(R.string.connect)) }
                                        TextButton(
                                            onClick = { m.checkServerAvailability(discovered.host) },
                                            enabled = discovered.host !in m.checkingServerHosts,
                                            modifier = Modifier.focusRequester(checkServerRequester)
                                                .focusProperties { up = connectTargetRequester; down = favoriteServerRequester }
                                                .scale(if (checkFocused) 1.08f else 1f)
                                                .background(if (checkFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                                .onFocusChanged { checkFocused = it.isFocused }
                                                .heightIn(min = 52.dp)
                                        ) { Text(if (discovered.host in m.checkingServerHosts) "…" else stringResource(R.string.server_check_api)) }
                                        TextButton(onClick = { m.toggleServerFavorite(discovered.host) }, modifier = Modifier.focusRequester(favoriteServerRequester).focusProperties { up = checkServerRequester; down = moveServerUpRequester }.heightIn(min = 52.dp)) { Text(if (discovered.favorite) "★" else "☆") }
                                        TextButton(onClick = { m.moveServerPriority(discovered.host, -1) }, modifier = Modifier.focusRequester(moveServerUpRequester).focusProperties { up = favoriteServerRequester; down = moveServerDownRequester }.heightIn(min = 52.dp)) { Text("↑") }
                                        TextButton(onClick = { m.moveServerPriority(discovered.host, 1) }, modifier = Modifier.focusRequester(moveServerDownRequester).focusProperties { up = moveServerUpRequester }.heightIn(min = 52.dp)) { Text("↓") }
                                    }
                                }
                            }
                        }
                    }
                    if (m.sortedInstances.isNotEmpty()) item { Text(stringResource(R.string.directory_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                    items(m.sortedInstances, key = { it.host }) { instance ->
                        Card(onClick = {
                            m.server = "https://${instance.host}"
                            m.save()
                            m.tab = "home"
                            m.loadHomePage()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(instance.name?.takeIf(String::isNotBlank) ?: instance.host, style = MaterialTheme.typography.titleMedium)
                                Text(instance.host, style = MaterialTheme.typography.bodyMedium)
                                Text("${instance.version ?: "PeerTube"} · ${instance.country.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.videos_count, instance.totalVideos?.toString() ?: "—", stringResource(if (instance.signupAllowed == true) R.string.signup_open else R.string.signup_closed)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (m.instances.size < m.instanceTotal) {
                        item {
                            TextButton(onClick = { m.loadInstances(loadMore = true) }, enabled = !m.isLoadingInstances, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.instances_load_more, m.instances.size, m.instanceTotal)) }
                        }
                    }
                }
            } else if (m.tab == "channels") {
                Text(stringResource(R.string.channels_title), style = MaterialTheme.typography.headlineSmall)
                val localChannels = m.localChannelSubscriptions
                val accountChannels = if (m.hasActiveAccount()) m.subscriptions.toList() else emptyList()
                if (localChannels.isEmpty() && accountChannels.isEmpty()) {
                    Text(stringResource(R.string.channels_empty), modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        if (accountChannels.isNotEmpty() || m.hasActiveAccount()) item {
                            Text(stringResource(R.string.channels_server_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.channel_name_header), Modifier.weight(0.40f), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.channel_server_header), Modifier.weight(0.34f), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.channel_action_header), Modifier.weight(0.26f), style = MaterialTheme.typography.titleSmall)
                            }
                            HorizontalDivider()
                        }
                        items(accountChannels, key = { "server:${it.name}@${it.host}" }) { channel ->
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        channel.displayName,
                                        Modifier.weight(0.40f).then(if (channel == accountChannels.firstOrNull()) Modifier.focusRequester(sectionFirstFocusRequester) else Modifier).clickable { sectionFocusPending = true; m.loadChannelVideos(channel.name, channel.host) },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (channel.host.isBlank()) stringResource(R.string.server_unknown) else channel.host,
                                        Modifier.weight(0.34f).then(if (channel.host.isNotBlank()) Modifier.clickable { sectionFocusPending = true; m.loadServerChannels(channel.host) } else Modifier),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (channel.host.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (channel.host.isNotBlank()) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    TextButton(onClick = { m.removeSubscription(channel) }, modifier = Modifier.weight(0.26f)) { Text(stringResource(R.string.unsubscribe), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                                }
                                HorizontalDivider()
                            }
                        }
                        item {
                            Text(stringResource(R.string.channels_local_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                            if (localChannels.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.channel_name_header), Modifier.weight(0.40f), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.channel_server_header), Modifier.weight(0.34f), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.channel_action_header), Modifier.weight(0.26f), style = MaterialTheme.typography.titleSmall)
                                }
                                HorizontalDivider()
                            } else Text(stringResource(R.string.channels_local_empty), modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(localChannels, key = { "local:${it.name}@${it.host}" }) { channel ->
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(channel.displayName, Modifier.weight(0.40f).then(if (accountChannels.isEmpty() && channel == localChannels.firstOrNull()) Modifier.focusRequester(sectionFirstFocusRequester) else Modifier).clickable { sectionFocusPending = true; m.loadChannelVideos(channel.name, channel.host) },
                                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(channel.host.ifBlank { stringResource(R.string.server_unknown) }, Modifier.weight(0.34f), style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    TextButton(onClick = { m.removeLocalSubscription(channel) }, modifier = Modifier.weight(0.26f)) {
                                        Text(stringResource(R.string.unsubscribe), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            } else if (m.tab == "server_channels") {
                Text(m.activeServerChannelsHost, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.channels_title), style = MaterialTheme.typography.titleMedium)
                if (m.isLoadingServerChannels) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (m.serverChannels.isEmpty() && !m.isLoadingServerChannels && m.error == null) {
                    Text(stringResource(R.string.server_channels_empty), modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(m.serverChannels, key = { "${it.id ?: it.name.orEmpty()}@${it.host}" }) { channel ->
                            TextButton(
                                onClick = { channel.name?.takeIf(String::isNotBlank)?.let { name -> sectionFocusPending = true; m.loadChannelVideos(name, channel.host ?: m.activeServerChannelsHost) } },
                                modifier = Modifier.fillMaxWidth().then(if (channel == m.serverChannels.firstOrNull()) Modifier.focusRequester(sectionFirstFocusRequester) else Modifier),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(channel.displayName?.takeIf(String::isNotBlank) ?: channel.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("${channel.name.orEmpty()}@${channel.host ?: m.activeServerChannelsHost}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.channel_video_count, channel.videosCount?.toString() ?: "—"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (m.serverChannels.size < m.serverChannelsTotal) {
                            item {
                                TextButton(onClick = { m.loadServerChannels(m.activeServerChannelsHost, loadMore = true) }, enabled = !m.isLoadingServerChannels, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.instances_load_more, m.serverChannels.size, m.serverChannelsTotal))
                                }
                            }
                        }
                    }
                }
            } else if (m.tab == "sepia") {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = startVoiceSearch,
                        modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_search), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                    TvRemoteTextField(
                        value = searchText,
                        onValueChange = { searchText = it; if (it.isBlank()) hasSearchSubmitted = false },
                        label = stringResource(R.string.search_peertube),
                        modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && searchText.isNotBlank()) {
                                searchResultFocusPending = true
                                true
                            } else false
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        labelStyle = MaterialTheme.typography.labelSmall
                    )
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    SearchFilter(stringResource(R.string.search_sort), sortOptions.first { it.first == searchSort }.second, sortOptions) { searchSort = it }
                    SearchFilter(stringResource(R.string.search_category), categoryOptions.first { it.first == searchCategory }.second, categoryOptions) { searchCategory = it }
                    SearchFilter(stringResource(R.string.search_duration_filter), durationOptions.first { it.first == searchDuration }.second, durationOptions) { searchDuration = it }
                    val availabilityOptions = listOf(
                        "all" to stringResource(R.string.availability_all),
                        "unknown_or_available" to stringResource(R.string.availability_unknown_or_available),
                        "available_only" to stringResource(R.string.availability_available_only)
                    )
                    SearchFilter(stringResource(R.string.availability_filter), availabilityOptions.first { it.first == searchAvailabilityFilter }.second, availabilityOptions) { searchAvailabilityFilter = it; m.updateAvailabilityFilter(it); if (hasSearchSubmitted) submitSearch() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.search_live_only), style = MaterialTheme.typography.bodySmall)
                        Switch(checked = searchLiveOnly, onCheckedChange = { searchLiveOnly = it })
                    }
                }
                val sortedSearchVideos = remember(m.videos, m.videoSortMode) { m.sortVideos(m.videos) }
                val savedSearchVideoId = lastFocusedCardBySection["sepia"]
                m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (m.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                if (m.isVerifyingAvailability) Text(stringResource(R.string.availability_checking), style = MaterialTheme.typography.bodySmall)
                val searchContext = LocalContext.current
                val usedSources = m.searchSourcesByVideo.values.flatten().toSet().sortedBy { m.searchSettings.order.indexOf(it) }.joinToString(", ") { source ->
                    searchContext.getString(when (source) { SearchSource.SEPIA -> R.string.search_source_sepia; SearchSource.PEERSEEK -> R.string.search_source_peerseek; SearchSource.CUSTOM -> R.string.search_source_custom; SearchSource.FEDERATED -> R.string.search_source_federated; SearchSource.DIRECT -> R.string.search_source_direct; SearchSource.LOCAL -> R.string.search_source_local })
                }
                if (usedSources.isNotBlank()) Text(stringResource(R.string.search_sources_in_use, usedSources), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (SearchSource.LOCAL in m.searchSourcesByVideo.values.flatten() && m.searchSourceErrors.isNotEmpty()) Text(stringResource(R.string.search_cache_stale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                if (m.searchSourceErrors.isNotEmpty() && m.videos.isEmpty()) Text(stringResource(R.string.search_source_errors, m.searchSourceErrors.entries.joinToString { (source, detail) -> "${source.key}: $detail" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                else if (hasSearchSubmitted && m.videos.isEmpty() && m.query.isNotBlank() && m.error == null) Text(stringResource(R.string.search_empty), modifier = Modifier.padding(16.dp))
                if (hasSearchSubmitted && m.gridMode) {
                    val searchGridState = rememberLazyGridState()
                    LaunchedEffect(sectionFocusPending, savedSearchVideoId, sortedSearchVideos.size) {
                        if (sectionFocusPending) savedSearchVideoId?.let { id -> sortedSearchVideos.indexOfFirst { it.uuid == id }.takeIf { it >= 0 }?.let { searchGridState.scrollToItem(it) } }
                    }
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val gridSpacing = 14.dp
                        val minCardWidth = (240.dp * m.gridScale).coerceIn(120.dp, 360.dp)
                        val columns = ((maxWidth + gridSpacing) / (minCardWidth + gridSpacing)).toInt().coerceAtLeast(1)
                        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(), state = searchGridState, contentPadding = PaddingValues(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(gridSpacing), verticalArrangement = Arrangement.spacedBy(gridSpacing)) {
                            itemsIndexed(sortedSearchVideos, key = { _, video -> video.uuid }) { index, video -> VideoGridCard(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), m.videoTitleLines, m.tickerSpeed, { openVideoInQueue(video, sortedSearchVideos) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, { m.excludeFromRecommendations(video) }, { m.markRecommendationWatched(video) }, if (index == 0) firstSearchResultFocusRequester else null, if (index < columns) searchButtonFocusRequester else null, searchRestoreFocusRequester, savedSearchVideoId, isAtLeftBoundary = index % columns == 0) }
                            if (m.searchHasMore) item { TextButton(onClick = { m.searchSepia(m.query, searchSort, searchCategory.toIntOrNull(), when(searchDuration){"medium"->240;"long"->1200;else->null}, when(searchDuration){"short"->240;"medium"->1200;else->null}, searchLiveOnly, page = m.searchNextPage, append = true) }, enabled = !m.isSearching, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.search_more)) } }
                        }
                    }
                } else if (hasSearchSubmitted) {
                    val searchListState = rememberLazyListState()
                    LaunchedEffect(sectionFocusPending, savedSearchVideoId, sortedSearchVideos.size) {
                        if (sectionFocusPending) savedSearchVideoId?.let { id -> sortedSearchVideos.indexOfFirst { it.uuid == id }.takeIf { it >= 0 }?.let { searchListState.scrollToItem(it) } }
                    }
                    LazyColumn(state = searchListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(sortedSearchVideos, key = { _, video -> video.uuid }) { index, video -> VideoListRow(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), { openVideoInQueue(video, sortedSearchVideos) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }, firstCardFocusRequester = if (index == 0) firstSearchResultFocusRequester else null, upFocusRequester = if (index == 0) searchButtonFocusRequester else null, preferredFocusRequester = searchRestoreFocusRequester, preferredFocusVideoId = savedSearchVideoId) }
                        if (m.searchHasMore) item { TextButton(onClick = { m.searchSepia(m.query, searchSort, searchCategory.toIntOrNull(), when(searchDuration){"medium"->240;"long"->1200;else->null}, when(searchDuration){"short"->240;"medium"->1200;else->null}, searchLiveOnly, page = m.searchNextPage, append = true) }, enabled = !m.isSearching, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.search_more)) } }
                    }
                }
            } else if (m.tab == "settings") {
                        SettingsDashboard(
                    model = m,
                    selectedCategory = null,
                    selectedSubpage = null,
                    requestInitialFocus = !menuExpanded,
                            firstFocusRequester = sectionFirstFocusRequester,
                    onSelectCategory = { category ->
                        settingsCategory = category
                        settingsSubpage = null
                        settingsOverlay = category != null
                    },
                    onSelectSubpage = { settingsSubpage = it },
                    historyPeriodHours = historyPeriodHours,
                    onHistoryPeriodChange = { historyPeriodHours = it },
                    onClearHistory = { clearHistoryDialog = true },
                    onClearHistoryPeriod = { clearHistoryPeriodDialog = true },
                    onExportBackup = exportBackup,
                    onImportBackup = launchBackupRestore,
                    onCheckForUpdates = { (context as? MainActivity)?.checkForUpdates() }
                )
            } else if (m.tab == "home") {
                val savedHomeVideoId = lastFocusedCardBySection["home"]
                val customHomeTitle = stringResource(when (m.homePageDisplayMode) {
                    "interests" -> R.string.home_mode_interests
                    "server" -> R.string.home_mode_server
                    "channel" -> R.string.home_mode_channel
                    else -> R.string.home_mode_recommended
                })
                if (m.homePageDisplayMode == "recommended") {
                    val homeShelvesListState = rememberLazyListState()
                    val latestVideos = remember(m.videos, m.videoSortMode) { m.sortVideos(m.videos) }
                    val latestCardRequesters = remember(latestVideos.map { it.uuid }) { latestVideos.map { FocusRequester() } }
                    val recommendationVideos = remember(m.recommendations, m.videoSortMode) { m.sortVideos(m.recommendations) }
                    val recommendationDownRequesters = remember(recommendationVideos.map { it.uuid }, latestVideos.map { it.uuid }) {
                        recommendationVideos.indices.map { index -> latestCardRequesters.getOrNull(index.coerceAtMost((latestCardRequesters.size - 1).coerceAtLeast(0))) }
                    }
                    LazyColumn(Modifier.weight(1f), state = homeShelvesListState, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (m.recommendations.isNotEmpty()) {
                        item {
                            VideoShelf(
                                title = stringResource(R.string.shelf_recommended),
                                videos = recommendationVideos,
                                server = m.server,
                                watchProgressMs = m.watchProgressMs,
                                tickerSpeed = m.tickerSpeed,
                                firstCardFocusRequester = homeFirstCardFocusRequester,
                                preferredFocusRequester = homeRecommendationRestoreFocusRequester,
                                preferredFocusVideoId = savedHomeVideoId,
                                restoreFocusPending = sectionFocusPending,
                                downFocusRequesters = recommendationDownRequesters,
                                upFocusRequester = searchButtonFocusRequester,
                                titleLines = m.videoTitleLines,
                                onOpen = { video -> openVideoInQueue(video, m.recommendations) },
                                isFavorite = { video -> video.uuid in m.favoriteIds },
                                isSubscribed = m::isSubscribed,
                                onFavorite = toggleFavorite,
                                onSubscription = m::toggleSubscription,
                                onChannel = openChannelVideos,
                                setContextMenuTarget = updateFocusedVideoMenu,
                                contextMenuHoldSeconds = m.contextMenuHoldSeconds,
                                onExcludeRecommendation = m::excludeFromRecommendations,
                                onMarkWatched = m::markRecommendationWatched,
                                onVideoFocus = { headerHidden = false }
                            )
                        }
                    }
                    item {
                        VideoShelf(
                            title = stringResource(R.string.shelf_latest),
                            videos = latestVideos,
                            server = m.server,
                            watchProgressMs = m.watchProgressMs,
                            tickerSpeed = m.tickerSpeed,
                                firstCardFocusRequester = if (m.recommendations.isEmpty()) homeFirstCardFocusRequester else homeLatestFirstCardFocusRequester,
                                preferredFocusRequester = homeLatestRestoreFocusRequester,
                                preferredFocusVideoId = savedHomeVideoId,
                                restoreFocusPending = sectionFocusPending,
                                itemFocusRequesters = latestCardRequesters,
                                upFocusRequester = searchButtonFocusRequester,
                                titleLines = m.videoTitleLines,
                            onOpen = { video -> openVideoInQueue(video, m.sortVideos(m.videos)) },
                            isFavorite = { video -> video.uuid in m.favoriteIds },
                            isSubscribed = m::isSubscribed,
                            onFavorite = toggleFavorite,
                            onSubscription = m::toggleSubscription,
                            onChannel = openChannelVideos,
                                setContextMenuTarget = updateFocusedVideoMenu,
                            contextMenuHoldSeconds = m.contextMenuHoldSeconds,
                            onExcludeRecommendation = m::excludeFromRecommendations,
                            onMarkWatched = m::markRecommendationWatched,
                            onVideoFocus = { headerHidden = m.recommendations.isNotEmpty() }
                        )
                    }
                    }
                } else {
                    val sortedHomePageVideos = remember(m.homePageVideos, m.videoSortMode) { m.sortVideos(m.homePageVideos) }
                    val homeGridState = rememberLazyGridState()
                        val homeVideoStartIndex = 1 + (if (m.isLoadingHomePage) 1 else 0) + (if (!m.homePageError.isNullOrBlank()) 1 else 0)
                        LaunchedEffect(sectionFocusPending, savedHomeVideoId, sortedHomePageVideos.size, homeVideoStartIndex) {
                            if (sectionFocusPending) savedHomeVideoId?.let { id -> sortedHomePageVideos.indexOfFirst { it.uuid == id }.takeIf { it >= 0 }?.let { homeGridState.scrollToItem(homeVideoStartIndex + it) } }
                        }
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val spacing = 14.dp
                        val minCardWidth = (240.dp * m.gridScale).coerceIn(180.dp, 360.dp)
                        val columns = ((maxWidth + spacing) / (minCardWidth + spacing)).toInt().coerceAtLeast(1)
                        LaunchedEffect(homeGridState, columns, m.tab, m.homePageDisplayMode) {
                            if (m.tab == "home" && m.homePageDisplayMode != "recommended") {
                                snapshotFlow {
                                    val layout = homeGridState.layoutInfo
                                    val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                                    lastVisible >= layout.totalItemsCount - columns - 5
                                }.distinctUntilChanged().collect { nearEnd ->
                                    if (nearEnd) m.loadMoreHomePage()
                                }
                            }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = homeGridState,
                            contentPadding = PaddingValues(bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                if (!headerHidden) Text(customHomeTitle, style = MaterialTheme.typography.headlineSmall)
                            }
                            if (m.isLoadingHomePage) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                            if (!m.homePageError.isNullOrBlank()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Text(m.homePageError.orEmpty(), color = if (m.homePageVideos.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            itemsIndexed(sortedHomePageVideos, key = { _, video -> video.uuid }) { index, video ->
                                VideoGridCard(
                                    video = video,
                                    server = m.server,
                                    progress = m.watchProgressFraction(video),
                                    favorite = video.uuid in m.favoriteIds,
                                    subscribed = m.isSubscribed(video),
                                    titleLines = m.videoTitleLines,
                                    tickerSpeed = m.tickerSpeed,
                                    onOpen = { openVideoInQueue(video, sortedHomePageVideos) },
                                    onFavorite = { toggleFavorite(video) },
                                    onSubscription = { m.toggleSubscription(video) },
                                    onChannel = { openChannelVideos(video) },
                                    setContextMenuTarget = updateFocusedVideoMenu,
                                    holdSeconds = m.contextMenuHoldSeconds,
                                    onExcludeRecommendation = { m.excludeFromRecommendations(video) },
                                    onMarkWatched = { m.markRecommendationWatched(video) },
                                    firstCardFocusRequester = if (index == 0) homeFirstCardFocusRequester else null,
                                    upFocusRequester = if (index < columns) searchButtonFocusRequester else null,
                                    preferredFocusRequester = homeGridRestoreFocusRequester,
                                    preferredFocusVideoId = savedHomeVideoId,
                                    isAtLeftBoundary = index % columns == 0,
                                    onCardFocus = { focused -> if (focused) headerHidden = index / columns > 0 }
                                )
                            }
                            if (m.isLoadingMoreHomePage) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                if (m.tab == "subscriptions") {
                    if (!headerHidden) {
                        Text(stringResource(R.string.nav_subscriptions), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.subscriptions_help), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (m.isLoadingSubscriptions) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    else if (m.subscriptions.isEmpty()) Text(stringResource(R.string.subscriptions_empty), modifier = Modifier.padding(16.dp))
                    else if (m.subscriptionVideos.isEmpty() && m.error == null) Text(stringResource(R.string.subscriptions_no_videos), modifier = Modifier.padding(16.dp))
                } else if (m.tab == "channel") {
                    if (!headerHidden) Text(m.activeChannelTitle, style = MaterialTheme.typography.headlineSmall)
                } else if (m.tab == "live") {
                    if (!headerHidden) Text(stringResource(R.string.nav_live), style = MaterialTheme.typography.headlineSmall)
                    if (m.isLoadingLive) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    else if (m.liveVideos.isEmpty() && m.recentlyEndedLiveVideos.isEmpty() && m.error == null) Text(stringResource(R.string.live_empty), modifier = Modifier.padding(16.dp))
                } else {
                    val sectionTitle = when (m.tab) {
                        "popular" -> R.string.nav_popular
                        "history" -> R.string.nav_history
                        "favorites" -> R.string.nav_favorites
                        "recommendations" -> R.string.nav_recommendations
                        else -> R.string.nav_home
                    }
                    if (!headerHidden) Text(stringResource(sectionTitle), style = MaterialTheme.typography.headlineSmall)
                    if (m.tab == "history" && m.isLoadingHistory) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    if (m.tab == "popular" && m.isLoadingPopular) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    else if (m.tab == "popular" && m.popularVideos.isEmpty() && m.error == null) Text(stringResource(R.string.subscriptions_no_videos), modifier = Modifier.padding(16.dp))
                    if (m.tab == "recommendations") {
                        if (!headerHidden) Text(stringResource(R.string.recommendations_help), style = MaterialTheme.typography.bodyMedium)
                        if (m.isLoadingRecommendations) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                }
                val rows = m.sortVideos(when(m.tab) { "popular" -> m.popularVideos; "history" -> m.history.toList(); "favorites" -> m.favorites.toList(); "recommendations" -> m.recommendations; "subscriptions" -> m.subscriptionVideos; "live" -> m.liveVideos; "channel" -> m.channelVideos; else -> m.videos })
                val savedSectionVideoId = lastFocusedCardBySection[m.tab]
                m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (m.tab == "recommendations" && rows.isEmpty() && !m.isLoadingRecommendations && m.error == null) Text(stringResource(R.string.recommendations_empty), modifier = Modifier.padding(16.dp))
                if (m.tab == "live") {
                    val liveListState = key(m.tab) { rememberLazyListState() }
                    LaunchedEffect(sectionFocusPending, savedSectionVideoId, m.liveVideos, m.recentlyEndedLiveVideos) {
                        if (sectionFocusPending && savedSectionVideoId != null) {
                            val liveIndex = m.liveVideos.indexOfFirst { it.uuid == savedSectionVideoId }
                            val endedIndex = m.recentlyEndedLiveVideos.indexOfFirst { it.uuid == savedSectionVideoId }
                            if (liveIndex >= 0) liveListState.scrollToItem(if (m.liveVideos.isNotEmpty()) liveIndex + 1 else liveIndex)
                            else if (endedIndex >= 0) liveListState.scrollToItem((if (m.liveVideos.isNotEmpty()) m.liveVideos.size + 1 else 0) + endedIndex + 1)
                        }
                    }
                    LazyColumn(Modifier.weight(1f), state = liveListState, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        if (m.liveVideos.isNotEmpty()) {
                            item { Text(stringResource(R.string.live_now_title), style = MaterialTheme.typography.titleLarge) }
                        itemsIndexed(m.liveVideos, key = { _, video -> "live-${video.uuid}" }) { index, video -> VideoListRow(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), { openVideoInQueue(video, m.liveVideos) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, statusLabel = when (video.state?.id) { 4 -> stringResource(R.string.live_status_waiting); 5 -> stringResource(R.string.live_status_ended); else -> stringResource(R.string.live_status_on_air) }, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }, firstCardFocusRequester = if (index == 0) sectionFirstFocusRequester else null, upFocusRequester = if (index == 0) searchButtonFocusRequester else null, preferredFocusRequester = sectionRestoreFocusRequester, preferredFocusVideoId = savedSectionVideoId) }
                        }
                        if (m.recentlyEndedLiveVideos.isNotEmpty()) {
                            item { Text(stringResource(R.string.live_recent_title), style = MaterialTheme.typography.titleLarge) }
                            itemsIndexed(m.recentlyEndedLiveVideos, key = { _, video -> "ended-${video.uuid}" }) { index, video -> VideoListRow(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), { openVideoInQueue(video, m.recentlyEndedLiveVideos) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, statusLabel = stringResource(R.string.live_status_ended), onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }, firstCardFocusRequester = if (m.liveVideos.isEmpty() && index == 0) sectionFirstFocusRequester else null, upFocusRequester = if (m.liveVideos.isEmpty() && index == 0) searchButtonFocusRequester else null, preferredFocusRequester = sectionRestoreFocusRequester, preferredFocusVideoId = savedSectionVideoId) }
                        }
                    }
                } else if (m.tab == "popular" && rows.isEmpty()) {
                    // Loading, empty-state, or error are rendered with the section heading above.
                } else if (m.gridMode) {
                    val sectionGridState = key(m.tab) { rememberLazyGridState() }
                    LaunchedEffect(sectionFocusPending, savedSectionVideoId, rows) {
                        if (sectionFocusPending && savedSectionVideoId != null) rows.indexOfFirst { it.uuid == savedSectionVideoId }.takeIf { it >= 0 }?.let { sectionGridState.scrollToItem(it) }
                    }
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val gridSpacing = 14.dp
                        val minCardWidth = (240.dp * m.gridScale).coerceIn(120.dp, 360.dp)
                        val columns = ((maxWidth + gridSpacing) / (minCardWidth + gridSpacing)).toInt().coerceAtLeast(1)
                        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(), state = sectionGridState, contentPadding = PaddingValues(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(gridSpacing), verticalArrangement = Arrangement.spacedBy(gridSpacing)) {
                            itemsIndexed(rows, key = { _, video -> video.uuid }) { index, video -> VideoGridCard(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), m.videoTitleLines, m.tickerSpeed, { openVideoInQueue(video, rows) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, { m.excludeFromRecommendations(video) }, { m.markRecommendationWatched(video) }, if (index == 0) sectionFirstFocusRequester else null, if (index < columns) searchButtonFocusRequester else null, sectionRestoreFocusRequester, savedSectionVideoId, isAtLeftBoundary = index % columns == 0) }
                        }
                    }
                } else {
                    val sectionListState = key(m.tab) { rememberLazyListState() }
                    LaunchedEffect(sectionFocusPending, savedSectionVideoId, rows) {
                        if (sectionFocusPending && savedSectionVideoId != null) rows.indexOfFirst { it.uuid == savedSectionVideoId }.takeIf { it >= 0 }?.let { sectionListState.scrollToItem(it) }
                    }
                    LazyColumn(state = sectionListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(rows, key = { _, video -> video.uuid }) { index, video -> VideoListRow(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), { openVideoInQueue(video, rows) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }, firstCardFocusRequester = if (index == 0) sectionFirstFocusRequester else null, upFocusRequester = if (index == 0) searchButtonFocusRequester else null, preferredFocusRequester = sectionRestoreFocusRequester, preferredFocusVideoId = savedSectionVideoId) }
                    }
                }
            }
        }
        if (settingsOverlay && settingsCategory != null) {
            Dialog(
                onDismissRequest = { settingsOverlay = false; settingsCategory = null },
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
                            selectedCategory = settingsCategory,
                            selectedSubpage = settingsSubpage,
                            requestInitialFocus = !menuExpanded,
                            onSelectCategory = { category ->
                                settingsCategory = category
                                if (category == null) settingsSubpage = null
                                settingsOverlay = category != null
                            },
                            onSelectSubpage = { settingsSubpage = it },
                            historyPeriodHours = historyPeriodHours,
                            onHistoryPeriodChange = { historyPeriodHours = it },
                            onClearHistory = { clearHistoryDialog = true },
                            onClearHistoryPeriod = { clearHistoryPeriodDialog = true },
                            onExportBackup = exportBackup,
                            onImportBackup = launchBackupRestore,
                            onCheckForUpdates = { (context as? MainActivity)?.checkForUpdates() }
                        )
                    }
                }
            }
        }
        if (clearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { clearHistoryDialog = false },
                title = { Text(stringResource(R.string.history_clear_confirm_title)) },
                text = { Text(stringResource(R.string.history_clear_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = { m.clearHistory(); clearHistoryDialog = false }) {
                        Text(stringResource(R.string.history_clear_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clearHistoryDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (clearHistoryPeriodDialog) {
            AlertDialog(
                onDismissRequest = { clearHistoryPeriodDialog = false },
                title = { Text(stringResource(R.string.history_clear_confirm_title)) },
                text = { Text(stringResource(R.string.history_clear_period_confirm, periodLabel)) },
                confirmButton = {
                    TextButton(onClick = { m.clearHistoryOlderThan(historyPeriodHours); clearHistoryPeriodDialog = false }) { Text(stringResource(R.string.history_clear_confirm_action)) }
                },
                dismissButton = { TextButton(onClick = { clearHistoryPeriodDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }
    val topBarOffset by animateDpAsState(targetValue = if (headerHidden) (-92).dp else 0.dp, label = "topBarOffset")
    Row(
        Modifier.align(Alignment.TopStart).offset(y = topBarOffset).graphicsLayer { alpha = if (headerHidden) 0f else 1f }
            .padding(start = if (menuExpanded) 226.dp else if (m.miniSectionRailEnabled) 80.dp else 20.dp, end = 20.dp, top = 4.dp).fillMaxWidth().zIndex(10f)
            .focusProperties { canFocus = !menuExpanded && !headerHidden },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (m.showSearchIcon) {
            IconButton(
                onClick = { searchText = m.query; hasSearchSubmitted = false; m.tab = "sepia" },
                modifier = Modifier.size(46.dp).focusRequester(searchButtonFocusRequester)
                    .focusProperties { if (m.tab == "home") down = homeFirstCardFocusRequester else if (m.tab != "sepia") down = sectionFirstFocusRequester }
                    .onFocusChanged { searchButtonFocused = it.isFocused }
                    .then(if (searchButtonFocused) Modifier.border(3.dp, Color(0xFFFFB066), CircleShape).padding(3.dp) else Modifier)
                    .background(if (searchButtonFocused) Color(0xFFC45100) else MaterialTheme.colorScheme.primary, CircleShape)
            ) { SearchGlyph() }
        }
        if (accountState.active != null) {
            val activeAccount = accountState.active!!
            IconButton(
                onClick = { settingsCategory = "accounts"; settingsSubpage = null; settingsOverlay = true },
                modifier = Modifier.padding(start = if (m.showSearchIcon) 18.dp else 0.dp).size(44.dp)
            ) {
                if (!activeAccount.avatar.isNullOrBlank()) AsyncImage(activeAccount.avatar, contentDescription = activeAccount.displayName,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.AccountCircle, contentDescription = activeAccount.displayName, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) { LocalDateTime(m.clock24Hour) }
        Spacer(Modifier.width(14.dp))
        Image(painter = painterResource(LocalPeerIcheTheme.current.logoResource), contentDescription = stringResource(R.string.app_name), contentScale = ContentScale.Fit, modifier = Modifier.width(176.dp).height(38.dp))
    }
    }
}
