package app.peertube.tv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
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
    val ui = remember { HomeUiState() }
    val search = remember { SearchUiState() }
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
    val menuFocusScope = rememberCoroutineScope()
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
            ui.focusedVideoMenu = videoId to openMenu
            ui.lastFocusedCardBySection[m.tab] = videoId
            ui.focusedVideoAtLeftBoundary = atLeftBoundary
        } else if (ui.focusedVideoMenu?.first == videoId) {
            ui.focusedVideoMenu = null
            ui.focusedVideoAtLeftBoundary = false
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
    LaunchedEffect(ui.menuExpanded, ui.focusedMenuSectionKey) {
        if (ui.menuExpanded) {
            kotlinx.coroutines.yield()
            runCatching { (ui.expandedMenuFocusRequesters[ui.focusedMenuSectionKey] ?: ui.menuFocusRequester).requestFocus() }
        }
    }
    LaunchedEffect(m.tab) { ui.headerHidden = false }
    LaunchedEffect(ui.menuExpanded, ui.returnSearchFocusPending, m.showSearchIcon) {
        if (!ui.menuExpanded && ui.returnSearchFocusPending) {
            kotlinx.coroutines.delay(50)
            if (m.showSearchIcon) runCatching { ui.searchButtonFocusRequester.requestFocus() }
            ui.returnSearchFocusPending = false
        }
    }
    LaunchedEffect(ui.menuExpanded, ui.sectionFocusPending, ui.homeInitialized, m.tab, m.videos.size, m.homePageVideos.size, m.recommendations.size, m.popularVideos.size, m.liveVideos.size, m.channelVideos.size, m.serverChannels.size, m.subscriptionVideos.size, m.instances.size, ui.lastFocusedCardBySection[m.tab], m.homePageDisplayMode) {
        if (!ui.menuExpanded && ui.sectionFocusPending) {
            kotlinx.coroutines.delay(280)
            val rememberedId = ui.lastFocusedCardBySection[m.tab]
            val target = when {
                m.tab == "home" && m.homePageDisplayMode == "recommended" && rememberedId != null && m.recommendations.any { it.uuid == rememberedId } -> ui.homeRecommendationRestoreFocusRequester
                m.tab == "home" && m.homePageDisplayMode == "recommended" && rememberedId != null && m.videos.any { it.uuid == rememberedId } -> ui.homeLatestRestoreFocusRequester
                m.tab == "home" && m.homePageDisplayMode != "recommended" && rememberedId != null && m.homePageVideos.any { it.uuid == rememberedId } -> ui.homeGridRestoreFocusRequester
                m.tab == "sepia" && rememberedId != null && m.videos.any { it.uuid == rememberedId } -> ui.searchRestoreFocusRequester
                rememberedId != null -> ui.sectionRestoreFocusRequester
                m.tab == "home" -> if (m.recommendations.isEmpty()) ui.homeLatestFirstCardFocusRequester else ui.homeFirstCardFocusRequester
                else -> ui.sectionFirstFocusRequester
            }
            val focused = runCatching { target.requestFocus() }.getOrDefault(false)
            if (!focused) {
                val fallback = if (m.tab == "home") {
                    if (m.recommendations.isEmpty()) ui.homeLatestFirstCardFocusRequester else ui.homeFirstCardFocusRequester
                } else if (m.tab == "sepia") ui.firstSearchResultFocusRequester else ui.sectionFirstFocusRequester
                if (runCatching { fallback.requestFocus() }.getOrDefault(false)) ui.sectionFocusPending = false
            } else ui.sectionFocusPending = false
        }
    }
    BackHandler {
        if (ui.clearHistoryDialog) ui.clearHistoryDialog = false
        else if (ui.clearHistoryPeriodDialog) ui.clearHistoryPeriodDialog = false
        else if (ui.settingsOverlay && ui.settingsSubpage != null) ui.settingsSubpage = null
        else if (ui.settingsOverlay && ui.settingsCategory != null) { ui.settingsOverlay = false; ui.settingsCategory = null }
        else if (ui.settingsOverlay) ui.settingsOverlay = false
        else if (ui.menuExpanded) { ui.menuExpanded = false; ui.pendingMenuFocus[0]?.second?.cancel(); ui.pendingMenuFocus[0] = null; ui.sectionFocusPending = true }
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
            else -> ui.menuExpanded = true
        }
    }
    LaunchedEffect(Unit) {
        m.init(context)
        val hasPendingChannelNavigation = (context as? MainActivity)?.pendingChannelNavigation != null
        m.tab = if (hasPendingChannelNavigation) "channel" else m.launchTab
        ui.sectionFocusPending = true
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
        ui.homeInitialized = true
    }
    val pendingChannelNavigation = (context as? MainActivity)?.pendingChannelNavigation
    LaunchedEffect(ui.homeInitialized, pendingChannelNavigation) {
        val target = pendingChannelNavigation
        if (ui.homeInitialized && target != null) {
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
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (recognized.isNotEmpty()) {
                search.text = recognized
                m.query = recognized
                search.submitted = false
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
        if (!channelName.isNullOrBlank()) { ui.sectionFocusPending = true; m.loadChannelVideos(channelName, host) }
    }
    LaunchedEffect(m.searchSettings.availabilityFilter) { search.availabilityFilter = m.searchSettings.availabilityFilter }
    val submitSearch: () -> Unit = {
        val term = search.text.trim()
        if (term.isNotEmpty()) {
            search.submitted = true
            m.query = term
            val durationMin = when (search.duration) { "medium" -> 240; "long" -> 1200; else -> null }
            val durationMax = when (search.duration) { "short" -> 240; "medium" -> 1200; else -> null }
            m.searchSepia(term, search.sort, search.category.toIntOrNull(), durationMin, durationMax, search.liveOnly, page = 0)
        }
    }
    LaunchedEffect(m.tab, search.text, search.sort, search.category, search.duration, search.liveOnly) {
        if (m.tab == "sepia") {
            if (search.text.isBlank()) {
                search.submitted = false
            } else {
                kotlinx.coroutines.delay(350)
                submitSearch()
            }
        }
    }
    LaunchedEffect(m.tab, search.submitted, m.videos.size, ui.searchResultFocusPending) {
        if (m.tab == "sepia" && search.submitted && m.videos.isNotEmpty() && ui.searchResultFocusPending) {
            kotlinx.coroutines.delay(80)
            runCatching { ui.firstSearchResultFocusRequester.requestFocus() }
            ui.searchResultFocusPending = false
        }
    }
    val navigateToSection: (String, Boolean) -> Unit = { key, closeMenu ->
        ui.focusedMenuSectionKey = key
        if (closeMenu) { ui.menuExpanded = false; ui.pendingMenuFocus[0]?.second?.cancel(); ui.pendingMenuFocus[0] = null; ui.returnSearchFocusPending = false; ui.sectionFocusPending = true }
        val sectionChanged = m.tab != key
        if (key == "settings") {
            ui.settingsCategory = null
            ui.settingsSubpage = null
            ui.settingsOverlay = false
            m.tab = "settings"
        } else {
            ui.settingsOverlay = false
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
    val actions = HomeActions(openVideoInQueue, toggleFavorite, openChannelVideos, updateFocusedVideoMenu, navigateToSection, startVoiceSearch, submitSearch, exportBackup, launchBackupRestore)
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
                event.type == KeyEventType.KeyDown && ui.menuExpanded && event.key == Key.DirectionRight -> {
                    ui.menuExpanded = false
                    ui.pendingMenuFocus[0]?.second?.cancel()
                    ui.pendingMenuFocus[0] = null
                    ui.returnSearchFocusPending = false
                    ui.sectionFocusPending = true
                    true
                }
                event.type == KeyEventType.KeyDown && !ui.menuExpanded && ui.collapsedMenuItemFocused && event.key == Key.DirectionRight -> {
                    ui.sectionFocusPending = true
                    true
                }
                event.type == KeyEventType.KeyDown && ui.menuExpanded && event.key == Key.DirectionUp && ui.focusedMenuSectionKey == "home" && m.showSearchIcon -> {
                    ui.menuExpanded = false
                    ui.pendingMenuFocus[0]?.second?.cancel()
                    ui.pendingMenuFocus[0] = null
                    ui.sectionFocusPending = false
                    ui.returnSearchFocusPending = true
                    true
                }
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && !ui.menuExpanded -> {
                    if (ui.collapsedMenuItemFocused) { ui.menuExpanded = true; true }
                    else if (ui.focusedVideoMenu != null && !ui.focusedVideoAtLeftBoundary) {
                        if (focusManager.moveFocus(FocusDirection.Left)) true
                        else if (m.miniSectionRailEnabled && ui.collapsedMenuFocusRequesters[ui.focusedMenuSectionKey] != null) {
                            runCatching { ui.collapsedMenuFocusRequesters[ui.focusedMenuSectionKey]?.requestFocus() }
                            true
                        } else {
                            ui.menuExpanded = true
                            true
                        }
                    }
                    else if (m.miniSectionRailEnabled && ui.focusedVideoMenu != null && ui.collapsedMenuFocusRequesters[ui.focusedMenuSectionKey] != null) {
                        runCatching { ui.collapsedMenuFocusRequesters[ui.focusedMenuSectionKey]?.requestFocus() }
                        true
                    }
                    else if (ui.focusedVideoMenu != null && ui.focusedVideoAtLeftBoundary && !m.miniSectionRailEnabled) {
                        ui.menuExpanded = true
                        true
                    }
                    else if (focusManager.moveFocus(FocusDirection.Left)) true else { ui.menuExpanded = true; true }
                }
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && !ui.menuExpanded && m.showSearchIcon && !ui.searchButtonFocused -> {
                    if (focusManager.moveFocus(FocusDirection.Up)) true
                    else { runCatching { ui.searchButtonFocusRequester.requestFocus() }; true }
                }
                isMenuKey && event.type == KeyEventType.KeyDown && ui.focusedVideoMenu != null -> {
                    ui.focusedVideoMenu?.second?.invoke()
                    true
                }
                else -> false
            }
        }
      ) {
        HomeSideMenu(m, ui, actions, menuFocusScope)
        Column(Modifier.weight(1f).focusProperties { canFocus = !ui.menuExpanded }.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
            val contentTopInset by animateDpAsState(targetValue = if (ui.headerHidden) 0.dp else 70.dp, label = "contentTopInset")
            Spacer(Modifier.height(contentTopInset))
            when (m.tab) {
                "instances" -> ServerDirectoryTab(m, ui)
                "channels" -> ChannelsTab(m, ui)
                "server_channels" -> ServerChannelsTab(m, ui)
                "sepia" -> SearchTab(m, ui, search, actions)
                "settings" -> SettingsDashboard(
                    model = m,
                    selectedCategory = null,
                    selectedSubpage = null,
                    requestInitialFocus = !ui.menuExpanded,
                    firstFocusRequester = ui.sectionFirstFocusRequester,
                    onSelectCategory = { category ->
                        ui.settingsCategory = category
                        ui.settingsSubpage = null
                        ui.settingsOverlay = category != null
                    },
                    onSelectSubpage = { ui.settingsSubpage = it },
                    historyPeriodHours = ui.historyPeriodHours,
                    onHistoryPeriodChange = { ui.historyPeriodHours = it },
                    onClearHistory = { ui.clearHistoryDialog = true },
                    onClearHistoryPeriod = { ui.clearHistoryPeriodDialog = true },
                    onExportBackup = exportBackup,
                    onImportBackup = launchBackupRestore,
                    onCheckForUpdates = { (context as? MainActivity)?.checkForUpdates() }
                )
                "home" -> HomeFeedTab(m, ui, actions)
                else -> VideoSectionTab(m, ui, actions)
            }
        }
        HomeOverlays(m, ui, actions)
    }
    HomeTopBar(m, ui, search, accountState)
    }
}
