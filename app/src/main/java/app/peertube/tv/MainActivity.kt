package app.peertube.tv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ClosedCaption
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.Locale
import java.text.DateFormat
import java.text.SimpleDateFormat
import com.google.gson.reflect.TypeToken

private val supportedUiLanguages = listOf("en", "ru", "es", "fr", "de", "pt", "it", "zh-CN", "ja", "hi", "ar")

data class ChannelNavigationTarget(val name: String, val host: String)

class MainActivity : AppCompatActivity() {
    internal var pendingChannelNavigation by mutableStateOf<ChannelNavigationTarget?>(null)

    private var availableUpdate by mutableStateOf<GitHubRelease?>(null)
    private var updateMessage by mutableStateOf<String?>(null)
    private var updateBusy by mutableStateOf(false)
    private var pendingUpdateApk: java.io.File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingChannelNavigation = channelNavigationFrom(intent)
        setContent {
            val model: HomeModel = viewModel()
            val theme = PeerIcheThemes.fromId(model.selectedTheme)
            MaterialTheme(colorScheme = theme.colors) {
                CompositionLocalProvider(LocalPeerIcheTheme provides theme) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Home(model)
                    }
                    availableUpdate?.takeIf { updateMessage == null }?.let { release ->
                        AlertDialog(
                            onDismissRequest = { if (!updateBusy) availableUpdate = null },
                            title = { Text(stringResource(R.string.update_available_title, release.version)) },
                            text = { Text(release.notes.ifBlank { stringResource(R.string.update_available_message) }) },
                            confirmButton = {
                                TextButton(enabled = !updateBusy, onClick = { downloadAndInstall(release) }) {
                                    Text(if (updateBusy) stringResource(R.string.update_downloading) else stringResource(R.string.update_action))
                                }
                            },
                            dismissButton = {
                                TextButton(enabled = !updateBusy, onClick = { availableUpdate = null }) { Text(stringResource(R.string.update_later)) }
                            }
                        )
                    }
                    updateMessage?.let { message ->
                        AlertDialog(
                            onDismissRequest = { updateMessage = null },
                            title = { Text(stringResource(R.string.update_error_title)) },
                            text = { Text(message) },
                            confirmButton = { TextButton(onClick = { updateMessage = null }) { Text(stringResource(R.string.ok)) } }
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            availableUpdate = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    GitHubUpdates.latestRelease(this@MainActivity, BuildConfig.VERSION_NAME)
                }.getOrNull()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChannelNavigation = channelNavigationFrom(intent)
    }

    private fun channelNavigationFrom(intent: Intent): ChannelNavigationTarget? {
        if (!intent.getBooleanExtra("navigateChannelVideos", false)) return null
        val name = intent.getStringExtra("navigateChannelName")?.trim()?.takeIf(String::isNotBlank) ?: return null
        val host = intent.getStringExtra("navigateChannelHost")?.trim()?.removePrefix("https://")
            ?.removePrefix("http://")?.substringBefore('/')?.trimEnd('.')
            ?.takeIf { it.matches(Regex("(?i)[a-z0-9.-]{1,253}")) } ?: return null
        return ChannelNavigationTarget(name, host)
    }

    override fun onResume() {
        super.onResume()
        val apk = pendingUpdateApk ?: return
        if (android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
            pendingUpdateApk = null
            updateMessage = null
            launchInstaller(apk)
        } else {
            updateMessage = getString(R.string.update_permission_required)
        }
    }

    private fun downloadAndInstall(release: GitHubRelease) {
        updateBusy = true
        lifecycleScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { GitHubUpdates.downloadApk(this@MainActivity, release.apkUrl, release.sha256) }
            }
            updateBusy = false
            result.onSuccess { apk ->
                availableUpdate = null
                if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                    pendingUpdateApk = apk
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                } else launchInstaller(apk)
            }.onFailure { error ->
                updateMessage = getString(R.string.update_error_download, error.localizedMessage ?: getString(R.string.update_network_error))
            }
        }
    }

    internal fun checkForUpdates() {
        if (updateBusy) return
        android.widget.Toast.makeText(this, getString(R.string.update_checking), android.widget.Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { GitHubUpdates.latestRelease(this@MainActivity, BuildConfig.VERSION_NAME, includeCurrent = true) }
            }
            result.onFailure { updateMessage = getString(R.string.update_network_error) }
                .onSuccess { release ->
                    if (release == null) updateMessage = getString(R.string.update_network_error)
                    else if (GitHubUpdates.compareVersions(release.version, BuildConfig.VERSION_NAME) > 0) { updateMessage = null; availableUpdate = release }
                    else { updateMessage = null; android.widget.Toast.makeText(this@MainActivity, getString(R.string.update_no_new_version), android.widget.Toast.LENGTH_LONG).show() }
                }
        }
    }

    private fun launchInstaller(apk: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }.onFailure {
            updateMessage = getString(R.string.update_error_installer, apk.name)
        }
    }

}

private fun Modifier.remoteCenterPress(onShortPress: () -> Unit, onLongPress: () -> Unit, holdSeconds: Float): Modifier = composed {
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

private data class SavedContentSnapshot(
    val watched: List<String>, val excluded: List<String>, val hidden: Set<String>,
    val history: List<Video>, val favorites: List<Video>, val subscriptions: List<VideoSubscription>
)

class HomeModel : ViewModel() {
    private var sepiaSearchJob: Job? = null
    private var sepiaSearchRequestId = 0
    var server by mutableStateOf("https://peertube.tv")
    var videos by mutableStateOf<List<Video>>(emptyList())
    var popularVideos by mutableStateOf<List<Video>>(emptyList())
    var popularVideosServer by mutableStateOf("")
    var isLoadingPopular by mutableStateOf(false)
    private var popularRequestId = 0
    private var androidTvPublishRequestId = 0
    var liveVideos by mutableStateOf<List<Video>>(emptyList())
    var recentlyEndedLiveVideos by mutableStateOf<List<Video>>(emptyList())
    var isLoadingLive by mutableStateOf(false)
    private var liveLoadRequestId = 0
    var isSearching by mutableStateOf(false)
    var recommendations by mutableStateOf<List<Video>>(emptyList())
    var homePageVideos by mutableStateOf<List<Video>>(emptyList())
    var isLoadingHomePage by mutableStateOf(false)
    var isLoadingMoreHomePage by mutableStateOf(false)
    var homePageHasMore by mutableStateOf(false)
    var homePageError by mutableStateOf<String?>(null)
    var homePageDisplayMode by mutableStateOf("recommended")
        private set
    var homePageInterests by mutableStateOf<Set<Int>>(emptySet())
        private set
    var homePageServer by mutableStateOf("")
        private set
    var homePageChannel by mutableStateOf("")
        private set
    var homePageConfigStatus by mutableStateOf<String?>(null)
        private set
    private var homePageRequestId = 0
    private var homePageNextStart = 0
    private val homePagePageSize = 30
    var subscriptionVideos by mutableStateOf<List<Video>>(emptyList())
    var channelVideos by mutableStateOf<List<Video>>(emptyList())
    var activeChannelTitle by mutableStateOf("")
    var channelReturnTab by mutableStateOf("channels")
    var activeServerChannelsHost by mutableStateOf("")
    var serverChannels by mutableStateOf<List<PeerTubeChannel>>(emptyList())
    var serverChannelsTotal by mutableIntStateOf(0)
    var isLoadingServerChannels by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var isLoadingRecommendations by mutableStateOf(false)
    var gridMode by mutableStateOf(true)
    var afterVideoAction by mutableStateOf("finish")
        private set
    var playerOverlayDurationSeconds by mutableIntStateOf(3)
        private set
    var showPlayerClock by mutableStateOf(false)
        private set
    var playerClockPosition by mutableStateOf("top_right")
        private set
    var playerClockSize by mutableStateOf("medium")
        private set
    var playerClockBackdrop by mutableStateOf("medium")
        private set
    var resumePlayback by mutableStateOf(true)
        private set
    var watchedRecommendationThreshold by mutableStateOf("never")
        private set
    var recommendationBasis by mutableStateOf("history")
        private set
    var networkProxy by mutableStateOf(NetworkProxySettings())
        private set
    val watchedVideoIds = mutableStateMapOf<String, Boolean>()
    val excludedRecommendationIds = mutableStateListOf<String>()
    var exitMode by mutableStateOf("none")
        private set
    var videoTitleLines by mutableIntStateOf(2)
        private set
    var tickerSpeed by mutableFloatStateOf(1f)
    var contextMenuHoldSeconds by mutableFloatStateOf(2f)
        private set
    var seekStepSeconds by mutableIntStateOf(10)
        private set
    var launchTab by mutableStateOf("home")
        private set
    var hiddenSections by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectedTheme by mutableStateOf(PeerIcheThemes.GRAPHITE)
        private set
    var showSearchIcon by mutableStateOf(true)
        private set
    var uiScale by mutableFloatStateOf(1f)
        private set
    var gridScale by mutableFloatStateOf(1f)
        private set
    var clock24Hour by mutableStateOf(true)
        private set
    var searchCountryCode by mutableStateOf("")
        private set
    var bufferProfile by mutableStateOf("medium")
        private set
    var playerVolume by mutableIntStateOf(100)
        private set
    var peerSeekEnabled by mutableStateOf(true)
        private set
    var searchSettings by mutableStateOf(SearchSettings())
        private set
    var searchSourceErrors by mutableStateOf<Map<SearchSource, String>>(emptyMap())
        private set
    var searchSourcesByVideo by mutableStateOf<Map<String, Set<SearchSource>>>(emptyMap())
        private set
    var searchHasMore by mutableStateOf(false)
        private set
    var searchNextPage by mutableIntStateOf(1)
        private set
    var isVerifyingAvailability by mutableStateOf(false)
        private set
    var availabilityByVideo by mutableStateOf<Map<String, AvailabilityCheck>>(emptyMap())
        private set
    var discoveredServers by mutableStateOf<List<DiscoveredServer>>(emptyList())
        private set
    var serverSortMode by mutableStateOf("default")
        private set
    var checkingServerHosts by mutableStateOf<Set<String>>(emptySet())
        private set
    var isCheckingServerAvailability by mutableStateOf(false)
        private set
    var serverAvailabilityCheckSummary by mutableStateOf<String?>(null)
        private set
    val sortedDiscoveredServers: List<DiscoveredServer>
        get() = discoveredServers.withIndex().sortedWith(compareBy<IndexedValue<DiscoveredServer>> { indexed ->
                val server = indexed.value
                val fresh = server.apiCheckedAt > 0L && System.currentTimeMillis() - server.apiCheckedAt <= 24L * 60L * 60L * 1000L
                when (serverSortMode) {
                    "availability" -> when { fresh && server.apiStatus == "available" -> 0; fresh && server.apiStatus == "unavailable" -> 2; else -> 1 }
                    "videos" -> 0
                    else -> 0
                }
            }.let { comparator ->
                when (serverSortMode) {
                    "videos" -> comparator.thenByDescending { it.value.totalVideos ?: -1L }.thenBy { it.index }
                    "availability" -> comparator.thenBy { it.index }
                    else -> compareBy<IndexedValue<DiscoveredServer>> { it.index }
                }
            }).map { it.value }
    var isDiscoveringServers by mutableStateOf(false)
        private set
    var discoveryError by mutableStateOf<String?>(null)
    private var searchSettingsRepository: SearchSettingsRepository? = null
    private var serverRepository: ServerRepository? = null
    private var serverDiscoveryManager: ServerDiscoveryManager? = null
    var videoSortMode by mutableStateOf("newest")
        private set
    var navigationSounds by mutableStateOf(true)
        private set
    val watchProgressMs = mutableStateMapOf<String, Long>()
    var query by mutableStateOf("")
    var tab by mutableStateOf("home")
    var uiLanguage by mutableStateOf("auto")
    var recommendationLanguage by mutableStateOf("country")
    var instances by mutableStateOf<List<PeerTubeInstance>>(emptyList())
    val sortedInstances: List<PeerTubeInstance>
        get() {
            val country = searchCountryCode.lowercase()
            if (country.isBlank()) return instances
            val preferredDomains = when (country) {
                "ru" -> setOf("ru", "su")
                "ua" -> setOf("ua", "uk")
                "gb" -> setOf("gb", "uk")
                "us" -> setOf("us", "com")
                else -> setOf(country)
            }
            return instances.withIndex().sortedWith(compareBy<IndexedValue<PeerTubeInstance>> {
                val host = it.value.host.lowercase().trimEnd('.')
                if (preferredDomains.any { suffix -> host == suffix || host.endsWith(".$suffix") }) 0 else 1
            }.thenBy { it.index }).map { it.value }
        }
    var isLoadingInstances by mutableStateOf(false)
    var instanceTotal by mutableIntStateOf(0)
    val history = mutableStateListOf<Video>()
    val historyViewedAt = mutableStateMapOf<String, Long>()
    val favorites = mutableStateListOf<Video>()
    val favoriteIds = mutableStateMapOf<String, Boolean>()
    val subscriptions = mutableStateListOf<VideoSubscription>()
    private val localSubscriptions = mutableStateListOf<VideoSubscription>()
    val localChannelSubscriptions: List<VideoSubscription> get() = localSubscriptions.toList()
    private val serverHistoryUuids = linkedSetOf<String>()
    var isLoadingHistory by mutableStateOf(false)
    private var activeAccountId: String? = null
    fun hasActiveAccount(): Boolean = activeAccountId != null
    private var accountObserverJob: Job? = null
    var isLoadingSubscriptions by mutableStateOf(false)
    private var prefs: android.content.SharedPreferences? = null
    private var appContext: android.content.Context? = null
    private val contentSaveLock = Any()
    private val contentSaveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var lastQueuedContentSnapshot: SavedContentSnapshot? = null
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("peertube", 0)
        networkProxy = NetworkProxySettings(
            enabled = prefs?.getBoolean("proxyEnabled", false) ?: false,
            type = prefs?.getString("proxyType", "http") ?: "http",
            host = prefs?.getString("proxyHost", "") ?: "",
            port = prefs?.getInt("proxyPort", 8080) ?: 8080,
            username = prefs?.getString("proxyUsername", "") ?: "",
            password = prefs?.let { ProxyCredentialStore.readAndMigrate(context.applicationContext, it) } ?: ""
        )
        ApiFactory.configureProxy(networkProxy, context.applicationContext)
        searchSettingsRepository = SearchSettingsRepository(context)
        searchSettings = searchSettingsRepository!!.read()
        serverRepository = ServerRepository(context).also { it.seedBundled(context) }
        serverDiscoveryManager = ServerDiscoveryManager(context, serverRepository!!)
        serverDiscoveryManager!!.schedulePeriodic()
        discoveredServers = serverRepository!!.all()
        serverSortMode = prefs?.getString("serverSortMode", null)
            ?: if (prefs?.getBoolean("serverAvailabilitySort", false) == true) "availability" else "default"
        val discovery = serverDiscoveryManager!!.settings()
        val lastDiscovery = prefs?.getLong("lastServerDiscoveryAt", 0L) ?: 0L
        if (discovery.automatic && System.currentTimeMillis() - lastDiscovery >= discovery.refreshDays * 24L * 60L * 60L * 1000L) runServerDiscovery(manual = false)
        history.clear()
        favorites.clear()
        subscriptions.clear()
        localSubscriptions.clear()
        historyViewedAt.clear()
        server = prefs?.getString("server", server) ?: server
        homePageDisplayMode = prefs?.getString("homeContentMode", "recommended")?.takeIf { it in homePageDisplayModes } ?: "recommended"
        homePageInterests = runCatching { PeerTubeGson.instance.fromJson<List<Int>>(prefs?.getString("homeInterests", "[]"), object : TypeToken<List<Int>>() {}.type).orEmpty().filter { it in 1..19 }.toSet() }.getOrDefault(emptySet())
        homePageServer = prefs?.getString("homeServer", "").orEmpty()
        homePageChannel = prefs?.getString("homeChannel", "").orEmpty()
        gridMode = prefs?.getBoolean("gridMode", true) ?: true
        afterVideoAction = prefs?.getString("afterVideoAction", "finish") ?: "finish"
        playerOverlayDurationSeconds = (prefs?.getInt("playerOverlayDurationSeconds", 3) ?: 3).coerceIn(3, 10).let { if (it == 3 || it == 5 || it == 10) it else 3 }
        showPlayerClock = prefs?.getBoolean("showPlayerClock", true) ?: true
        playerClockPosition = prefs?.getString("playerClockPosition", "top_right")?.takeIf { it == "top_left" || it == "top_right" } ?: "top_right"
        playerClockSize = prefs?.getString("playerClockSize", "medium")?.takeIf { it == "small" || it == "medium" || it == "large" } ?: "medium"
        playerClockBackdrop = prefs?.getString("playerClockBackdrop", "none")?.takeIf { it == "none" || it == "light" || it == "medium" || it == "strong" } ?: "none"
        if (prefs?.getBoolean("resumePlaybackDefaultMigrated", false) != true) {
            resumePlayback = true
            prefs?.edit()?.putBoolean("resumePlayback", true)?.putBoolean("resumePlaybackDefaultMigrated", true)?.apply()
        } else {
            resumePlayback = prefs?.getBoolean("resumePlayback", true) ?: true
        }
        watchedRecommendationThreshold = when (prefs?.getString("watchedRecommendationThreshold", if (prefs?.getBoolean("hideWatchedRecommendations", true) == true) "complete" else "never")) {
            "minute" -> "percent_5"
            "three_minutes" -> "percent_10"
            "half" -> "percent_50"
            "percent_5", "percent_10", "percent_50", "complete", "never" -> prefs?.getString("watchedRecommendationThreshold", "never") ?: "never"
            else -> "never"
        }
            ?.takeIf { it in setOf("never", "minute", "three_minutes", "half", "complete") } ?: "never"
        recommendationBasis = prefs?.getString("recommendationBasis", "history")?.takeIf { it in setOf("history", "subscriptions", "both") } ?: "history"
        recommendationLanguage = prefs?.getString("recommendationLanguage", "country")?.takeIf { it == "country" || it in Locale.getISOLanguages() } ?: "country"
        watchedVideoIds.clear()
        runCatching { PeerTubeGson.instance.fromJson<List<String>>(prefs?.getString("watchedVideoIds", "[]"), object : TypeToken<List<String>>() {}.type).orEmpty().forEach { watchedVideoIds[it] = true } }
        excludedRecommendationIds.clear()
        runCatching { excludedRecommendationIds.addAll(PeerTubeGson.instance.fromJson<List<String>>(prefs?.getString("excludedRecommendationIds", "[]"), object : TypeToken<List<String>>() {}.type) ?: emptyList()) }
        val savedExitMode = prefs?.getString("exitMode", null)
        val exitModeUserSelected = prefs?.getBoolean("exitModeUserSelected", false) ?: false
        val exitModeDefaultInitialized = prefs?.getBoolean("exitModeDefaultInitialized", false) ?: false
        exitMode = when {
            exitModeUserSelected -> savedExitMode?.takeIf { it in setOf("none", "single", "double") }
                ?: if (isTelevisionDevice(context)) "double" else "none"
            savedExitMode == "single" || savedExitMode == "double" -> {
                // Keep a non-default exit mode saved by an older app version.
                prefs?.edit()?.putBoolean("exitModeUserSelected", true)?.putBoolean("exitModeDefaultInitialized", true)?.apply()
                savedExitMode
            }
            !exitModeDefaultInitialized -> {
                (if (isTelevisionDevice(context)) "double" else "none").also { defaultMode ->
                    prefs?.edit()?.putString("exitMode", defaultMode)?.putBoolean("exitModeDefaultInitialized", true)?.apply()
                }
            }
            else -> savedExitMode?.takeIf { it in setOf("none", "single", "double") } ?: "none"
        }
        videoTitleLines = (prefs?.getInt("videoTitleLines", 2) ?: 2).coerceIn(1, 3)
        tickerSpeed = (prefs?.getFloat("tickerSpeed", 1f) ?: 1f).coerceIn(1f, 8f)
        contextMenuHoldSeconds = (prefs?.getFloat("contextMenuHoldSeconds", 1f) ?: 1f).coerceIn(1f, 5f)
        seekStepSeconds = (prefs?.getInt("seekStepSeconds", 10) ?: 10).takeIf { it in listOf(5, 10, 15, 30, 60) } ?: 10
        launchTab = prefs?.getString("launchTab", "home")?.takeIf { it in listOf("home", "popular", "live", "subscriptions", "channels", "recommendations", "instances", "history", "favorites") } ?: "home"
        hiddenSections = runCatching { PeerTubeGson.instance.fromJson<List<String>>(prefs?.getString("hiddenSections", "[]"), object : TypeToken<List<String>>() {}.type)?.toSet() ?: emptySet() }.getOrDefault(emptySet())
        val savedTheme = prefs?.getString("selectedTheme", PeerIcheThemes.GRAPHITE)
        selectedTheme = PeerIcheThemes.migrateId(savedTheme)
        if (savedTheme != selectedTheme) prefs?.edit()?.putString("selectedTheme", selectedTheme)?.apply()
        showSearchIcon = prefs?.getBoolean("showSearchIcon", true) ?: true
        uiScale = (prefs?.getFloat("uiScale", 0.85f) ?: 0.85f).coerceIn(0.85f, 1.4f)
        gridScale = (prefs?.getFloat("gridScale", 0.85f) ?: 0.85f).coerceIn(0.5f, 1.5f)
        clock24Hour = prefs?.getBoolean("clock24Hour", true) ?: true
        if (prefs?.getBoolean("playerDefaults20261021Applied", false) != true) {
            val editor = prefs?.edit()
            if (kotlin.math.abs((prefs?.getFloat("contextMenuHoldSeconds", 2f) ?: 2f) - 2f) < 0.001f) {
                contextMenuHoldSeconds = 1f
                editor?.putFloat("contextMenuHoldSeconds", 1f)
            }
            if (kotlin.math.abs((prefs?.getFloat("uiScale", 1f) ?: 1f) - 1f) < 0.001f) {
                uiScale = 0.85f
                editor?.putFloat("uiScale", 0.85f)
            }
            if (kotlin.math.abs((prefs?.getFloat("gridScale", 1f) ?: 1f) - 1f) < 0.001f) {
                gridScale = 0.85f
                editor?.putFloat("gridScale", 0.85f)
            }
            if (prefs?.getBoolean("showPlayerClock", false) == false) {
                showPlayerClock = true
                editor?.putBoolean("showPlayerClock", true)
            }
            if (prefs?.getString("playerClockBackdrop", "medium") == "medium") {
                playerClockBackdrop = "none"
                editor?.putString("playerClockBackdrop", "none")
            }
            editor?.putBoolean("playerDefaults20261021Applied", true)?.apply()
        }
        val savedCountryCode = prefs?.getString("searchCountryCode", "").orEmpty().uppercase(Locale.ROOT)
        val countryInitialized = prefs?.getBoolean("countryInitialized", false) ?: false
        if (savedCountryCode.isNotBlank()) {
            searchCountryCode = savedCountryCode
            prefs?.edit()?.putBoolean("countryInitialized", true)?.apply()
        } else if (countryInitialized) {
            searchCountryCode = ""
        } else {
            val systemConfiguration = android.content.res.Resources.getSystem().configuration
            val systemLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                systemConfiguration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                systemConfiguration.locale
            }
            val detectedCountry = systemLocale.country.uppercase(Locale.ROOT)
                .takeIf { it in Locale.getISOCountries() }
                .orEmpty()
            searchCountryCode = detectedCountry
            prefs?.edit()?.putString("searchCountryCode", detectedCountry)
                ?.putBoolean("countryInitialized", true)?.apply()
        }
        bufferProfile = prefs?.getString("bufferProfile", "medium")?.takeIf { it in listOf("small", "medium", "large", "largest") } ?: "medium"
        playerVolume = (prefs?.getInt("playerVolume", 100) ?: 100).coerceIn(0, 200)
        peerSeekEnabled = SearchSource.PEERSEEK in searchSettings.enabled
        videoSortMode = prefs?.getString("videoSortMode", "newest")?.takeIf { it in setOf("newest", "popular", "title") } ?: "newest"
        navigationSounds = prefs?.getBoolean("navigationSounds", true) ?: true
        syncWatchProgress()
        val storedLocaleTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        uiLanguage = when {
            storedLocaleTag.isBlank() -> "auto"
            storedLocaleTag in supportedUiLanguages -> storedLocaleTag
            storedLocaleTag.substringBefore('-') in supportedUiLanguages -> storedLocaleTag.substringBefore('-')
            else -> "auto"
        }
        val type = object : TypeToken<List<Video>>() {}.type
        runCatching { history.addAll(PeerTubeGson.instance.fromJson<List<Video>>(prefs?.getString("history", "[]"), type) ?: emptyList()) }
        val historyTimeType = object : TypeToken<Map<String, Long>>() {}.type
        runCatching { historyViewedAt.putAll(PeerTubeGson.instance.fromJson<Map<String, Long>>(prefs?.getString("historyViewedAt", "{}"), historyTimeType) ?: emptyMap()) }
        history.forEach { if (it.uuid !in historyViewedAt) historyViewedAt[it.uuid] = System.currentTimeMillis() }
        runCatching { favorites.addAll(PeerTubeGson.instance.fromJson<List<Video>>(prefs?.getString("favorites", "[]"), type) ?: emptyList()) }
        favorites.forEach { favoriteIds[it.uuid] = true }
        val subscriptionType = object : TypeToken<List<VideoSubscription>>() {}.type
        runCatching {
            localSubscriptions.addAll(PeerTubeGson.instance.fromJson<List<VideoSubscription>>(prefs?.getString("subscriptions", "[]"), subscriptionType)
                .orEmpty().mapNotNull { it?.let(::normalizeVideoSubscription) })
        }
        subscriptions.clear(); subscriptions.addAll(localSubscriptions)
        accountObserverJob?.cancel()
        accountObserverJob = viewModelScope.launch {
            val accounts = PeerTubeAccounts.get(context.applicationContext)
            runCatching { accounts.initialize() }
            accounts.state.collect { state ->
                val next = state.active?.takeUnless { it.needsLogin }?.id
                if (state.ready && next != activeAccountId) {
                    activeAccountId = next
                    if (next == null) {
                        subscriptions.clear(); subscriptions.addAll(localSubscriptions)
                        loadSubscriptions()
                    } else { loadSubscriptions(); loadHistory() }
                }
            }
        }
    }
    fun save() {
        val preferences = prefs ?: return
        preferences.edit().putString("server", server).putBoolean("gridMode", gridMode).putString("afterVideoAction", afterVideoAction)
            .putString("homeContentMode", homePageDisplayMode).putString("homeInterests", PeerTubeGson.instance.toJson(homePageInterests.sorted()))
            .putString("homeServer", homePageServer).putString("homeChannel", homePageChannel)
            .putInt("playerOverlayDurationSeconds", playerOverlayDurationSeconds).putBoolean("showPlayerClock", showPlayerClock)
            .putString("playerClockPosition", playerClockPosition).putString("playerClockSize", playerClockSize).putString("playerClockBackdrop", playerClockBackdrop)
            .putBoolean("resumePlayback", resumePlayback).putString("watchedRecommendationThreshold", watchedRecommendationThreshold)
            .putString("recommendationBasis", recommendationBasis).putString("recommendationLanguage", recommendationLanguage)
            .putBoolean("peerSeekEnabled", peerSeekEnabled).putString("videoSortMode", videoSortMode).putBoolean("navigationSounds", navigationSounds)
            .putString("exitMode", exitMode).putInt("videoTitleLines", videoTitleLines).putFloat("tickerSpeed", tickerSpeed)
            .putFloat("contextMenuHoldSeconds", contextMenuHoldSeconds).putInt("seekStepSeconds", seekStepSeconds).putString("launchTab", launchTab)
            .putString("selectedTheme", selectedTheme).putBoolean("showSearchIcon", showSearchIcon).putFloat("uiScale", uiScale).putFloat("gridScale", gridScale)
            .putBoolean("clock24Hour", clock24Hour).putString("searchCountryCode", searchCountryCode).putString("serverSortMode", serverSortMode)
            .putString("bufferProfile", bufferProfile).putInt("playerVolume", playerVolume).apply()

        val content = SavedContentSnapshot(
            watchedVideoIds.keys.toList().takeLast(1_000), excludedRecommendationIds.toList().takeLast(5_000), hiddenSections.toSet(),
            history.toList().take(100), favorites.toList().takeLast(1_000), localSubscriptions.toList().takeLast(1_000)
        )
        synchronized(contentSaveLock) {
            if (content == lastQueuedContentSnapshot) return
            lastQueuedContentSnapshot = content
            contentSaveExecutor.execute {
                val values = mapOf(
                    "watchedVideoIds" to PeerTubeGson.instance.toJson(content.watched),
                    "excludedRecommendationIds" to PeerTubeGson.instance.toJson(content.excluded),
                    "hiddenSections" to PeerTubeGson.instance.toJson(content.hidden),
                    "history" to PeerTubeGson.instance.toJson(content.history.map(Video::compactForStorage)),
                    "favorites" to PeerTubeGson.instance.toJson(content.favorites.map(Video::compactForStorage)),
                    "subscriptions" to PeerTubeGson.instance.toJson(content.subscriptions)
                )
                synchronized(contentSaveLock) {
                    if (content != lastQueuedContentSnapshot) return@execute
                    val editor = preferences.edit()
                    var changed = false
                    values.forEach { (key, value) ->
                        if (preferences.getString(key, null) != value) {
                            editor.putString(key, value)
                            changed = true
                        }
                    }
                    if (changed) editor.apply()
                }
            }
        }
    }
    fun updateHomePageDisplayMode(mode: String) {
        homePageDisplayMode = mode.takeIf { it in homePageDisplayModes } ?: "recommended"
        homePageVideos = emptyList()
        homePageConfigStatus = null
        save()
        if (tab == "home") loadHomePage()
    }
    fun updateHomePageInterest(id: Int, enabled: Boolean) {
        if (id !in 1..19) return
        homePageInterests = if (enabled) homePageInterests + id else homePageInterests - id
        homePageVideos = emptyList()
        save()
        if (tab == "home" && homePageDisplayMode == "interests") loadHomePage()
    }
    fun saveHomePageServer(value: String) {
        viewModelScope.launch {
            homePageConfigStatus = null
            val result = runCatchingCancellable {
                val origin = accountServerOrigin(value)
                ApiFactory.create(origin).videos(count = 1, isLocal = true, nsfw = "false")
                origin
            }
            result.onSuccess { origin ->
                homePageServer = origin
                homePageVideos = emptyList()
                homePageConfigStatus = appContext?.getString(R.string.home_source_saved)
                save()
                if (tab == "home" && homePageDisplayMode == "server") loadHomePage()
            }.onFailure { homePageConfigStatus = appContext?.getString(R.string.home_source_invalid) }
        }
    }
    fun saveHomePageChannel(value: String) {
        viewModelScope.launch {
            homePageConfigStatus = null
            val result = runCatchingCancellable {
                val channel = parseHomeChannelAddress(value)
                ApiFactory.create(channel.origin).channelVideos(channel.handle, count = 1)
                channel
            }
            result.onSuccess { channel ->
                homePageChannel = "${channel.name}@${channel.host}"
                homePageVideos = emptyList()
                homePageConfigStatus = appContext?.getString(R.string.home_source_saved)
                save()
                if (tab == "home" && homePageDisplayMode == "channel") loadHomePage()
            }.onFailure { homePageConfigStatus = appContext?.getString(R.string.home_source_invalid) }
        }
    }
    fun loadHomePage() {
        if (homePageDisplayMode == "recommended") {
            homePageVideos = emptyList()
            homePageError = null
            homePageHasMore = false
            homePageNextStart = 0
            load()
            return
        }
        val requestId = ++homePageRequestId
        val requestedServer = server
        val mode = homePageDisplayMode
        val interests = homePageInterests
        val sourceServer = homePageServer
        val sourceChannel = homePageChannel
        viewModelScope.launch {
            isLoadingHomePage = true
            isLoadingMoreHomePage = false
            homePageVideos = emptyList()
            homePageHasMore = false
            homePageNextStart = 0
            homePageError = null
            try {
                val page = when (mode) {
                    "interests" -> {
                        require(interests.isNotEmpty()) { appContext?.getString(R.string.home_interests_empty).orEmpty() }
                        ApiFactory.create(requestedServer).videos(count = homePagePageSize, categoryOneOf = interests.sorted(), nsfw = "false", start = 0)
                    }
                    "server" -> {
                        val origin = accountServerOrigin(sourceServer.ifBlank { requestedServer })
                        ApiFactory.create(origin).videos(count = homePagePageSize, isLocal = true, nsfw = "false", start = 0)
                    }
                    "channel" -> {
                        val channel = parseHomeChannelAddress(sourceChannel)
                        ApiFactory.create(channel.origin).channelVideos(channel.handle, count = homePagePageSize, start = 0).let { response ->
                            response.copy(data = response.data.filterNot { it.nsfw }.map { it.copy(host = it.host ?: channel.host) })
                        }
                    }
                    else -> VideoPage()
                }
                if (requestId == homePageRequestId && requestedServer == server && mode == homePageDisplayMode) {
                    homePageVideos = page.data
                    homePageNextStart = minOf(homePagePageSize, page.total)
                    homePageHasMore = page.data.isNotEmpty() && homePageNextStart < page.total
                    homePageError = if (page.data.isEmpty()) appContext?.getString(R.string.home_source_empty) else null
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId == homePageRequestId) {
                    homePageVideos = emptyList()
                    homePageError = error.localizedMessage ?: appContext?.getString(R.string.home_source_invalid)
                }
            } finally {
                if (requestId == homePageRequestId) isLoadingHomePage = false
            }
        }
    }
    fun loadMoreHomePage() {
        if (homePageDisplayMode == "recommended" || isLoadingHomePage || isLoadingMoreHomePage || !homePageHasMore) return
        val requestId = homePageRequestId
        val requestedServer = server
        val mode = homePageDisplayMode
        val interests = homePageInterests
        val sourceServer = homePageServer
        val sourceChannel = homePageChannel
        val start = homePageNextStart
        viewModelScope.launch {
            isLoadingMoreHomePage = true
            try {
                val page = when (mode) {
                    "interests" -> ApiFactory.create(requestedServer).videos(count = homePagePageSize, categoryOneOf = interests.sorted(), nsfw = "false", start = start)
                    "server" -> ApiFactory.create(accountServerOrigin(sourceServer.ifBlank { requestedServer })).videos(count = homePagePageSize, isLocal = true, nsfw = "false", start = start)
                    "channel" -> {
                        val channel = parseHomeChannelAddress(sourceChannel)
                        ApiFactory.create(channel.origin).channelVideos(channel.handle, count = homePagePageSize, start = start).let { response ->
                            response.copy(data = response.data.filterNot { it.nsfw }.map { it.copy(host = it.host ?: channel.host) })
                        }
                    }
                    else -> VideoPage()
                }
                if (requestId == homePageRequestId && requestedServer == server && mode == homePageDisplayMode) {
                    homePageVideos = (homePageVideos + page.data).distinctBy { it.uuid }
                    homePageNextStart = minOf(start + homePagePageSize, page.total)
                    homePageHasMore = page.data.isNotEmpty() && homePageNextStart < page.total
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId == homePageRequestId) {
                    homePageHasMore = false
                    homePageError = error.localizedMessage ?: appContext?.getString(R.string.error_request)
                }
            } finally {
                isLoadingMoreHomePage = false
            }
        }
    }
    fun updateRecommendationBasis(basis: String) {
        recommendationBasis = basis.takeIf { it in setOf("history", "subscriptions", "both") } ?: "history"
        save()
        loadRecommendations()
    }
    fun updateRecommendationLanguage(language: String) {
        recommendationLanguage = language.takeIf { it == "country" || it in Locale.getISOLanguages() } ?: "country"
        save()
        loadRecommendations()
    }
    fun updateNetworkProxy(settings: NetworkProxySettings) {
        if (settings.enabled && (settings.host.isBlank() || settings.port !in 1..65535)) return
        networkProxy = settings.copy(host = settings.host.trim())
        ApiFactory.configureProxy(networkProxy, appContext)
        prefs?.edit()?.putBoolean("proxyEnabled", networkProxy.enabled)?.putString("proxyType", networkProxy.type)
            ?.putString("proxyHost", networkProxy.host)?.putInt("proxyPort", networkProxy.port)
            ?.putString("proxyUsername", networkProxy.username)?.apply()
        appContext?.let { app -> prefs?.let { ProxyCredentialStore.write(app, it, networkProxy.password) } }
    }
    fun excludeFromRecommendations(video: Video) {
        if (video.uuid !in excludedRecommendationIds) excludedRecommendationIds.add(video.uuid)
        recommendations = recommendations.filterNot { it.uuid == video.uuid }
        publishAndroidTvRecommendations(recommendations)
        save()
    }
    fun markRecommendationWatched(video: Video) {
        if (video.uuid !in watchedVideoIds) watchedVideoIds[video.uuid] = true
        appContext?.let { PlaybackStateStore.addWatched(it, video.uuid) }
        if (video.uuid !in excludedRecommendationIds) excludedRecommendationIds.add(video.uuid)
        history.removeAll { it.uuid == video.uuid }
        history.add(0, video)
        while (history.size > 100) history.removeAt(history.lastIndex)
        markVideoViewed(video.uuid)
        recommendations = recommendations.filterNot { it.uuid == video.uuid }
        publishAndroidTvRecommendations(recommendations)
        save()
    }
    @JvmName("updateAfterVideoAction")
    fun setAfterVideoAction(action: String) { afterVideoAction = action; save() }
    fun updatePlayerOverlayDuration(seconds: Int) { playerOverlayDurationSeconds = seconds.coerceIn(3, 10).let { if (it == 3 || it == 5 || it == 10) it else 3 }; save() }
    fun updateShowPlayerClock(show: Boolean) { showPlayerClock = show; save() }
    fun updatePlayerClockPosition(position: String) { playerClockPosition = if (position == "top_left") "top_left" else "top_right"; save() }
    fun updatePlayerClockSize(size: String) { playerClockSize = size.takeIf { it == "small" || it == "medium" || it == "large" } ?: "medium"; save() }
    fun updatePlayerClockBackdrop(backdrop: String) { playerClockBackdrop = backdrop.takeIf { it == "none" || it == "light" || it == "medium" || it == "strong" } ?: "medium"; save() }
    fun updateResumePlayback(resume: Boolean) { resumePlayback = resume; save() }
    fun updateWatchedRecommendationThreshold(threshold: String) {
        watchedRecommendationThreshold = threshold.takeIf { it in setOf("never", "percent_5", "percent_10", "percent_50", "complete") } ?: "never"
        save()
        recommendations = if (watchedRecommendationThreshold == "never") recommendations else recommendations.filterNot { it.uuid in watchedVideoIds }
        loadRecommendations()
    }
    fun updateExitMode(mode: String) { exitMode = mode.takeIf { it == "none" || it == "single" || it == "double" } ?: "none"; prefs?.edit()?.putBoolean("exitModeUserSelected", true)?.putBoolean("exitModeDefaultInitialized", true)?.apply(); save() }
    fun updateVideoTitleLines(lines: Int) { videoTitleLines = lines.coerceIn(1, 3); save() }
    fun updateTickerSpeed(speed: Float) { tickerSpeed = (speed * 2).toInt().coerceIn(2, 16) / 2f; save() }
    fun updateContextMenuHoldSeconds(seconds: Float) { contextMenuHoldSeconds = (seconds * 2f).toInt().coerceIn(2, 10) / 2f; save() }
    fun updateSeekStep(seconds: Int) { seekStepSeconds = seconds.takeIf { it in listOf(5, 10, 15, 30, 60) } ?: 10; save() }
    fun updateLaunchTab(tab: String) { launchTab = tab; if (tab in hiddenSections) hiddenSections = hiddenSections - tab; save() }
    fun toggleSection(key: String, shown: Boolean) { hiddenSections = if (shown) hiddenSections - key else hiddenSections + key; if (!shown && tab == key) tab = launchTab; save() }
    fun updateTheme(theme: String) { selectedTheme = PeerIcheThemes.migrateId(theme); save() }
    fun updateShowSearchIcon(show: Boolean) { showSearchIcon = show; save() }
    fun updateUiScale(scale: Float) { uiScale = scale.coerceIn(0.85f, 1.4f); save() }
    fun updateGridScale(scale: Float) { gridScale = scale.coerceIn(0.5f, 1.5f); save() }
    fun updateClock24Hour(enabled: Boolean) { clock24Hour = enabled; save() }
    fun updateSearchCountry(code: String) { searchCountryCode = code.uppercase(Locale.ROOT); prefs?.edit()?.putBoolean("countryInitialized", true)?.apply(); save() }
    fun updateBufferProfile(profile: String) { bufferProfile = profile.takeIf { it in listOf("small", "medium", "large", "largest") } ?: "medium"; save() }
    fun updatePlayerVolume(value: Int) { playerVolume = value.coerceIn(0, 200); save() }
    fun updatePeerSeekEnabled(enabled: Boolean) = updateSearchSourceEnabled(SearchSource.PEERSEEK, enabled)
    fun updateSearchSourceEnabled(source: SearchSource, enabled: Boolean) {
        searchSettingsRepository?.setEnabled(source, enabled)
        searchSettings = searchSettingsRepository?.read() ?: searchSettings.copy(enabled = if (enabled) searchSettings.enabled + source else searchSettings.enabled - source)
        peerSeekEnabled = SearchSource.PEERSEEK in searchSettings.enabled
        sepiaSearchRequestId++
        sepiaSearchJob?.cancel()
        isSearching = false
        searchSourceErrors = emptyMap()
        error = null
    }
    fun moveSearchSource(source: SearchSource, delta: Int) {
        val order = searchSettings.order.toMutableList()
        val index = order.indexOf(source)
        val target = (index + delta).coerceIn(0, order.lastIndex)
        if (index >= 0 && target != index) {
            val value = order.removeAt(index)
            order.add(target, value)
            searchSettingsRepository?.setOrder(order)
            searchSettings = searchSettings.copy(order = order)
        }
    }
    fun updateSearchExecutionMode(value: String) { searchSettingsRepository?.setMode(value); searchSettings = searchSettingsRepository?.read() ?: searchSettings.copy(mode = value) }
    fun updateSearchSufficientResults(value: Int) { searchSettingsRepository?.setSufficientResults(value); searchSettings = searchSettingsRepository?.read() ?: searchSettings.copy(sufficientResults = value) }
    fun updateCustomSearchIndexes(text: String) {
        searchSettingsRepository?.setCustomIndexUrls(text.split(Regex("[\\r\\n,;]+")))
        searchSettings = searchSettingsRepository?.read() ?: searchSettings
    }
    fun updateAvailabilityFilter(value: String) { searchSettingsRepository?.setAvailabilityFilter(value); searchSettings = searchSettingsRepository?.read() ?: searchSettings.copy(availabilityFilter = value) }
    fun updateIncludeSensitive(enabled: Boolean) { searchSettingsRepository?.setIncludeSensitive(enabled); searchSettings = searchSettingsRepository?.read() ?: searchSettings.copy(includeSensitive = enabled) }
    fun updateSensitiveDisplayMode(mode: String) { searchSettingsRepository?.setSensitiveDisplayMode(mode); searchSettings = searchSettingsRepository?.read() ?: searchSettings.copy(sensitiveDisplayMode = mode) }
    fun setDiscoveryOption(key: String, value: Any) {
        val d = serverDiscoveryManager ?: return
        when (key) {
            "automatic" -> d.setAutomatic(value as Boolean)
            "depth" -> d.setMaxDepth(value as Int)
            "max_new" -> d.setMaxNew(value as Int)
            "refresh_days" -> d.setRefreshDays(value as Int)
            "wifi_only" -> d.setWifiOnly(value as Boolean)
            "join" -> d.setJoinEnabled(value as Boolean)
            "federation" -> d.setFederationEnabled(value as Boolean)
            "bundled" -> d.setBundledEnabled(value as Boolean)
            "json_enabled" -> d.setRemoteJsonEnabled(value as Boolean)
            "catalog_url" -> d.setServerJsonUrl(value as String)
        }
    }
    fun discoverySettings() = serverDiscoveryManager?.settings() ?: DiscoverySettings()
    fun serverJsonCatalogUrl() = serverDiscoveryManager?.serverJsonUrl().orEmpty()
    fun serversForSearch(): List<DiscoveredServer> {
        val d = discoverySettings()
        return discoveredServers.filter { server -> server.discoveredFrom.any { source ->
            source == "manual" || source == "video_source" || source == "federation_seed" || (source == "joinpeertube_seed" && d.bundledEnabled) ||
                (source == "joinpeertube" && d.joinPeerTubeEnabled) || (source.startsWith("federation:") && d.federationEnabled) ||
                (source == "remote_json" && d.remoteJsonEnabled)
        } }
    }
    fun runServerDiscovery(manual: Boolean = true) {
        val manager = serverDiscoveryManager ?: return
        if (!manual) {
            val discovery = manager.settings()
            if (!discovery.automatic) return
            if (discovery.wifiOnly) {
                val context = appContext ?: return
                val connectivity = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val network = connectivity?.activeNetwork
                val capabilities = network?.let(connectivity::getNetworkCapabilities)
                if (capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true) return
            }
        }
        viewModelScope.launch {
            isDiscoveringServers = true
            discoveryError = null
            try {
                manager.discover(server)
                discoveredServers = serverRepository?.all().orEmpty()
            } catch (e: Exception) { discoveryError = e.localizedMessage ?: e.javaClass.simpleName }
            finally { isDiscoveringServers = false }
        }
    }
    fun clearDiscoveredServers() {
        serverRepository?.removeDiscovered()
        discoveredServers = serverRepository?.all().orEmpty()
    }
    fun toggleServerFavorite(host: String) { serverRepository?.toggleFavorite(host); discoveredServers = serverRepository?.all().orEmpty() }
    fun moveServerPriority(host: String, delta: Int) { serverRepository?.movePriority(host, delta); discoveredServers = serverRepository?.all().orEmpty() }
    fun cycleServerSortMode() {
        serverSortMode = when (serverSortMode) { "default" -> "availability"; "availability" -> "videos"; else -> "default" }
        prefs?.edit()?.putString("serverSortMode", serverSortMode)?.apply()
    }
    fun checkServerAvailability(host: String) {
        if (host in checkingServerHosts) return
        checkingServerHosts = checkingServerHosts + host
        viewModelScope.launch {
            try {
                val check = ServerAvailabilityManager(serverRepository).checkApi(host)
                serverRepository?.recordAvailability(host, api = check)
                discoveredServers = serverRepository?.all().orEmpty()
            } finally {
                checkingServerHosts = checkingServerHosts - host
            }
        }
    }
    fun checkCachedServersAvailability() {
        if (isCheckingServerAvailability) return
        val servers = discoveredServers
        val now = System.currentTimeMillis()
        val stale = servers.filter { it.apiCheckedAt <= 0L || now - it.apiCheckedAt > 24L * 60L * 60L * 1000L }
        val targets = (stale.ifEmpty { servers.sortedBy { it.apiCheckedAt } }).take(50)
        if (targets.isEmpty()) return
        isCheckingServerAvailability = true
        val targetHosts = targets.mapTo(linkedSetOf()) { it.host }
        checkingServerHosts = checkingServerHosts + targetHosts
        serverAvailabilityCheckSummary = null
        viewModelScope.launch {
            try {
                val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                val checks = targets.map { item -> async {
                    semaphore.withPermit {
                        val check = ServerAvailabilityManager(serverRepository).checkApi(item.host)
                        serverRepository?.recordAvailability(item.host, api = check)
                        discoveredServers = serverRepository?.all().orEmpty()
                        item.host to check
                    }
                } }.awaitAll()
                val available = checks.count { it.second.state == "available" }
                val unavailable = checks.count { it.second.state == "unavailable" }
                val unknown = checks.size - available - unavailable
                serverAvailabilityCheckSummary = appContext?.getString(
                    R.string.server_check_summary, checks.size, servers.size, available, unavailable, unknown
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                serverAvailabilityCheckSummary = appContext?.getString(R.string.server_check_failed, e.localizedMessage ?: e.javaClass.simpleName)
            } finally {
                isCheckingServerAvailability = false
                checkingServerHosts = checkingServerHosts - targetHosts
            }
        }
    }
    fun importServerCatalog(json: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { serverRepository?.importJson(json) }
                .onSuccess { withContext(kotlinx.coroutines.Dispatchers.Main) { discoveredServers = serverRepository?.all().orEmpty(); discoveryError = null } }
                .onFailure { withContext(kotlinx.coroutines.Dispatchers.Main) { discoveryError = it.localizedMessage ?: it.javaClass.simpleName } }
        }
    }
    fun exportServerCatalog(): String = serverRepository?.exportJson().orEmpty()
    fun addServerManually(address: String) {
        val normalized = runCatching {
            val uri = URI(if (address.contains("://")) address.trim() else "https://${address.trim()}")
            require(uri.scheme == "https" && uri.userInfo == null && uri.query == null && uri.fragment == null)
            uri.host?.lowercase(Locale.ROOT) ?: error("Invalid server address")
        }.getOrElse { discoveryError = it.localizedMessage; return }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                require(SafeNetworkTarget.isSafeHttpsHost(normalized)) { "Only public server addresses are allowed" }
                val configuration = ApiFactory.config("https://$normalized").customConfig()
                serverRepository?.merge(listOf(DiscoveredServer(normalized, version = null, languages = configuration.instance?.languages.orEmpty(), discoveredFrom = setOf("manual"), apiStatus = "available", lastCheckedAt = System.currentTimeMillis())))
                withContext(kotlinx.coroutines.Dispatchers.Main) { discoveredServers = serverRepository?.all().orEmpty(); discoveryError = null }
            } catch (e: Exception) { withContext(kotlinx.coroutines.Dispatchers.Main) { discoveryError = e.localizedMessage ?: "PeerTube API could not be verified" } }
        }
    }
    fun updateVideoSortMode(mode: String) { videoSortMode = mode.takeIf { it in setOf("newest", "popular", "title") } ?: "newest"; save() }
    fun updateNavigationSounds(enabled: Boolean) { navigationSounds = enabled; save() }
    fun sortVideos(items: List<Video>): List<Video> = when (videoSortMode) {
        "popular" -> items.sortedByDescending { it.views }
        "title" -> items.sortedBy { it.name.lowercase(Locale.ROOT) }
        else -> items.sortedByDescending { it.publishedAt ?: "" }
    }

    fun syncWatchProgress() {
        watchProgressMs.clear()
        watchProgressMs.putAll(appContext?.let { PlaybackStateStore.progressSnapshot(it) }.orEmpty())
        watchedVideoIds.clear()
        appContext?.let { PlaybackStateStore.watchedSnapshot(it) }.orEmpty().forEach { watchedVideoIds[it] = true }
        if (watchedRecommendationThreshold != "never") recommendations = recommendations.filterNot { it.uuid in watchedVideoIds }
    }
    fun watchProgressFraction(video: Video): Float {
        if (video.duration <= 0) return 0f
        return ((watchProgressMs[video.uuid] ?: 0L).toFloat() / (video.duration * 1000f)).coerceIn(0f, 1f)
    }
    fun clearHistory() {
        val accountId = activeAccountId
        val remoteIds = serverHistoryUuids.toList()
        history.clear()
        historyViewedAt.clear()
        serverHistoryUuids.clear()
        recommendations = emptyList()
        save()
        prefs?.edit()?.putString("historyViewedAt", "{}")?.apply()
        val context = appContext
        if (accountId != null && context != null) viewModelScope.launch {
            remoteIds.forEach { uuid -> runCatching { PeerTubeAccounts.get(context).deleteHistory(accountId, uuid) }
                .onFailure { error = it.localizedMessage ?: "Could not clear server history" } }
        }
    }
    fun loadHistory() {
        val accountId = activeAccountId ?: return
        val context = appContext ?: return
        if (isLoadingHistory) return
        viewModelScope.launch {
            isLoadingHistory = true
            try {
                val accounts = PeerTubeAccounts.get(context)
                val active = accounts.state.value.active?.takeIf { it.id == accountId } ?: return@launch
                val remote = mutableListOf<Video>()
                val remoteIds = linkedSetOf<String>()
                var start = 0
                while (start < 1_000) {
                    val page = accounts.history(accountId, start)
                    val rows = page.getAsJsonArray("data")?.mapNotNull { item -> runCatching {
                        val video = PeerTubeGson.instance.fromJson(item, Video::class.java)
                        if (video.uuid.isBlank()) null else video.copy(
                            host = video.host ?: video.channel?.host ?: video.account?.host ?: video.url?.let { runCatching { URI(it).host }.getOrNull() } ?: URI(active.server).authority,
                            thumbnailUrl = video.thumbnailUrl ?: video.thumbnailPath?.let { URI(active.server + "/").resolve(it).toString() }
                        )
                    }.getOrNull() }.orEmpty()
                    remote += rows
                    remoteIds += rows.map { it.uuid }
                    start += rows.size
                    val total = runCatching { page.get("total")?.asInt }.getOrNull() ?: start
                    if (rows.isEmpty() || start >= total) break
                }
                serverHistoryUuids.clear(); serverHistoryUuids.addAll(remoteIds)
                val localOnly = history.filterNot { it.uuid in remoteIds }
                history.clear()
                history.addAll((remote + localOnly).distinctBy { it.uuid }.take(100))
                save()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.localizedMessage ?: "Could not sync server history" }
            finally { isLoadingHistory = false }
        }
    }
    fun markVideoViewed(uuid: String) {
        historyViewedAt[uuid] = System.currentTimeMillis()
        prefs?.edit()?.putString("historyViewedAt", PeerTubeGson.instance.toJson(historyViewedAt))?.apply()
    }
    fun clearHistoryOlderThan(hours: Int) {
        val cutoff = System.currentTimeMillis() - hours * 60L * 60L * 1000L
        val removed = history.filter { (historyViewedAt[it.uuid] ?: 0L) >= cutoff }.map { it.uuid }.toSet()
        history.removeAll { it.uuid in removed }
        removed.forEach(historyViewedAt::remove)
        recommendations = recommendations.filterNot { it.uuid in removed }
        save()
        prefs?.edit()?.putString("historyViewedAt", PeerTubeGson.instance.toJson(historyViewedAt))?.apply()
    }
    fun syncSubscriptions() {
        val subscriptionType = object : TypeToken<List<VideoSubscription>>() {}.type
        val stored = runCatching { PeerTubeGson.instance.fromJson<List<VideoSubscription>>(prefs?.getString("subscriptions", "[]"), subscriptionType)
            .orEmpty().mapNotNull { it?.let(::normalizeVideoSubscription) } }.getOrDefault(emptyList())
        localSubscriptions.clear()
        localSubscriptions.addAll(stored)
        if (activeAccountId == null) { subscriptions.clear(); subscriptions.addAll(stored) }
    }
    fun syncFavorites() {
        val videoType = object : TypeToken<List<Video>>() {}.type
        val stored = runCatching { PeerTubeGson.instance.fromJson<List<Video>>(prefs?.getString("favorites", "[]"), videoType) ?: emptyList() }.getOrDefault(emptyList())
        favorites.clear()
        favorites.addAll(stored)
        favoriteIds.clear()
        stored.forEach { favoriteIds[it.uuid] = true }
    }
    private fun changeSubscription(channel: VideoSubscription, subscribed: Boolean) {
        val current = subscriptions.firstOrNull { "${it.name}@${it.host}".equals("${channel.name}@${channel.host}", ignoreCase = true) }
        if ((current != null) == subscribed) return
        if (subscribed) subscriptions.add(channel) else current?.let(subscriptions::remove)
        val accountId = activeAccountId
        if (accountId == null) {
            val local = localSubscriptions.firstOrNull { "${it.name}@${it.host}".equals("${channel.name}@${channel.host}", ignoreCase = true) }
            if (subscribed && local == null) localSubscriptions.add(channel)
            if (!subscribed) local?.let(localSubscriptions::remove)
            save()
            return
        }
        val context = appContext ?: return
        viewModelScope.launch {
            runCatching { PeerTubeAccounts.get(context).setSubscription(accountId, "${channel.name}@${channel.host}", subscribed) }
                .onFailure { error = message(R.string.error_subscriptions, it.localizedMessage ?: "Subscription update failed") }
        }
    }
    fun removeSubscription(channel: VideoSubscription) = changeSubscription(channel, false)
    fun removeLocalSubscription(channel: VideoSubscription) {
        localSubscriptions.removeAll { "${it.name}@${it.host}".equals("${channel.name}@${channel.host}", ignoreCase = true) }
        if (activeAccountId == null) { subscriptions.clear(); subscriptions.addAll(localSubscriptions) }
        save()
    }
    fun toggleSubscription(video: Video) {
        val channel = video.channel
        val name = channel?.name?.takeIf(String::isNotBlank) ?: video.account?.name?.takeIf(String::isNotBlank) ?: return
        val host = channel?.host?.takeIf(String::isNotBlank) ?: video.account?.host?.takeIf(String::isNotBlank) ?: video.host?.takeIf(String::isNotBlank) ?: video.sourceServer(server).removePrefix("https://").removePrefix("http://")
        val existing = subscriptions.any { "${it.name}@${it.host}".equals("$name@$host", ignoreCase = true) }
        changeSubscription(VideoSubscription(name, host, channel?.displayName ?: video.account?.displayName ?: name), !existing)
    }
    fun isSubscribed(video: Video): Boolean {
        val name = video.channel?.name ?: video.account?.name ?: return false
        val host = video.channel?.host ?: video.account?.host ?: video.host ?: video.sourceServer(server).removePrefix("https://").removePrefix("http://")
        return subscriptions.any { "${it.name}@${it.host}".equals("$name@$host", ignoreCase = true) }
    }
    fun loadSubscriptions() {
        viewModelScope.launch {
            val context = appContext
            val accountId = activeAccountId
            if (accountId != null && context != null) {
                isLoadingSubscriptions = true
                subscriptions.clear()
                error = null
                try {
                    val accounts = PeerTubeAccounts.get(context)
                    val active = accounts.state.value.active?.takeIf { it.id == accountId } ?: throw AccountAuthFailure("session_expired")
                    val channels = mutableListOf<VideoSubscription>()
                    var start = 0
                    while (start < 2_000) {
                        val page = accounts.subscriptions(accountId, start)
                        val rows = page.getAsJsonArray("data")?.mapNotNull { element -> runCatching {
                            val obj = element.asJsonObject
                            val rawName = obj.get("name")?.asString.orEmpty().trim()
                            val name = rawName.substringBeforeLast('@', rawName)
                            val host = obj.get("host")?.asString?.takeIf(String::isNotBlank)
                                ?: rawName.substringAfterLast('@', "").takeIf(String::isNotBlank)
                                ?: java.net.URI(active.server).authority
                            if (name.isBlank()) null else VideoSubscription(name, host, obj.get("displayName")?.asString?.takeIf(String::isNotBlank) ?: name)
                        }.getOrNull() }.orEmpty()
                        channels += rows
                        start += rows.size
                        val total = runCatching { page.get("total")?.asInt }.getOrNull() ?: start
                        if (rows.isEmpty() || start >= total) break
                    }
                    subscriptions.clear(); subscriptions.addAll(channels.distinctBy { "${it.name}@${it.host}".lowercase() })
                    val feedPage = accounts.subscriptionVideos(accountId, 0)
                    val type = object : TypeToken<VideoPage>() {}.type
                    val feed = PeerTubeGson.instance.fromJson<VideoPage>(feedPage, type).data.map { video -> video.copy(
                        host = video.host ?: video.channel?.host ?: video.account?.host ?: java.net.URI(active.server).authority,
                        thumbnailUrl = video.thumbnailUrl ?: video.thumbnailPath?.let { java.net.URI(active.server + "/").resolve(it).toString() }
                    ) }
                    subscriptionVideos = feed.distinctBy { it.uuid }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    error = message(R.string.error_subscriptions, failure.localizedMessage ?: "Request failed")
                    subscriptionVideos = emptyList()
                } finally { isLoadingSubscriptions = false }
                return@launch
            }
            if (subscriptions.isEmpty()) { subscriptionVideos = emptyList(); error = null; return@launch }
            error = null
            val semaphore = kotlinx.coroutines.sync.Semaphore(4)
            val failures = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val results = subscriptions.map { followed -> async {
                semaphore.withPermit {
                    val name = followed.name?.trim().orEmpty()
                    val host = followed.host?.trim()?.removePrefix("https://")?.removePrefix("http://")?.substringBefore('/')?.trimEnd('.').orEmpty()
                    if (name.isBlank() || host.isBlank() || !host.matches(Regex("(?i)[a-z0-9.-]{1,253}"))) return@withPermit emptyList()
                    try {
                        ApiFactory.create("https://$host").channelVideos("$name@$host", count = 50).data
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (channelError: Exception) {
                        try {
                            ApiFactory.create("https://$host").videos(search = name, count = 50).data.filter { video ->
                                val channelName = video.channel?.name ?: video.account?.name
                                val channelHost = video.channel?.host ?: video.account?.host ?: video.host.orEmpty()
                                channelName.equals(name, ignoreCase = true) && channelHost.equals(host, ignoreCase = true)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failures.add("$host: ${e.localizedMessage ?: channelError.localizedMessage ?: "request failed"}")
                            emptyList()
                        }
                    }
                }
            } }.awaitAll().flatten().distinctBy { it.uuid }
            subscriptionVideos = results
            if (results.isEmpty() && failures.isNotEmpty()) error = message(R.string.error_subscriptions, failures.joinToString("; "))
        }
    }
    @JvmName("applyUiLanguage")
    fun setUiLanguage(tag: String) {
        uiLanguage = tag
        AppCompatDelegate.setApplicationLocales(if (tag == "auto") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
        if (prefs?.getBoolean("subtitleTranslationLanguageCustom", false) != true) {
            val targetLanguage = interfaceSubtitleLanguage(tag)
            prefs?.edit()?.putString("subtitleTranslationLanguage", targetLanguage)?.apply()
        }
    }
    private fun message(id: Int, detail: String): String = appContext?.getString(id, detail) ?: detail
    private fun contentLanguage(): String? = (if (uiLanguage == "auto") java.util.Locale.getDefault().language else uiLanguage).substringBefore('-').takeIf { it.isNotBlank() && it != "auto" }
    private fun recommendationContentLanguage(): String? {
        if (recommendationLanguage != "country") return recommendationLanguage.substringBefore('-')
        return when (searchCountryCode.uppercase(Locale.ROOT)) {
            "RU" -> "ru"; "UA" -> "uk"; "BY" -> "be"; "KZ" -> "kk"; "AM" -> "hy"; "GE" -> "ka"
            "DE", "AT" -> "de"; "FR" -> "fr"; "ES", "MX", "AR", "CL", "CO", "PE", "VE" -> "es"
            "IT" -> "it"; "PT", "BR" -> "pt"; "NL" -> "nl"; "PL" -> "pl"; "CZ" -> "cs"
            "SK" -> "sk"; "RO", "MD" -> "ro"; "GR" -> "el"; "TR" -> "tr"; "JP" -> "ja"
            "KR" -> "ko"; "CN", "TW", "HK" -> "zh"; "IN" -> "hi"; "ID" -> "id"
            "TH" -> "th"; "VN" -> "vi"; "IL" -> "he"; "SA", "EG", "AE" -> "ar"
            "US", "GB", "CA", "AU", "NZ", "IE", "ZA", "NG", "PH", "SG" -> "en"
            else -> null
        }
    }
    private fun countrySearchTerm(search: String): String {
        val countryName = runCatching { java.util.Locale("", searchCountryCode).getDisplayCountry(java.util.Locale.getDefault()) }.getOrDefault("")
        return if (countryName.isNotBlank()) "$search $countryName" else search
    }
    private fun publishAndroidTvRecommendations(items: List<Video>) {
        val context = appContext ?: return
        val sourceServer = server
        val requestId = ++androidTvPublishRequestId
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (requestId == androidTvPublishRequestId) TvHomeRecommendations.publish(context, sourceServer, items)
            }
        }
    }
    fun load(search: String? = null) { viewModelScope.launch { try { error = null; videos = ApiFactory.create(server).videos(search = search?.let(::countrySearchTerm), languageOneOf = contentLanguage()).data; if (search == null && recommendations.isEmpty()) publishAndroidTvRecommendations(videos) } catch (e: Exception) { error = message(R.string.error_server, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty()) } } }
    fun loadPopular() {
        val requestId = ++popularRequestId
        val requestedServer = server
        viewModelScope.launch {
            isLoadingPopular = true
            error = null
            try {
                val result = ApiFactory.create(requestedServer).videos(languageOneOf = contentLanguage()).data
                if (requestId == popularRequestId && requestedServer == server) {
                    popularVideos = result
                    popularVideosServer = requestedServer
                }
            } catch (e: Exception) {
                if (requestId == popularRequestId) error = message(R.string.error_server, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty())
            } finally {
                if (requestId == popularRequestId) isLoadingPopular = false
            }
        }
    }
    fun loadLive() {
        val requestId = ++liveLoadRequestId
        viewModelScope.launch {
            isLoadingLive = true
            try {
                error = null
                val searchApi = ApiFactory.sepiaSearch()
                // Don't search for words like "live": that misses streams whose title is unrelated.
                // Sepia supports an unfiltered browse request with the isLive flag.
                val results = searchApi.videos(
                    search = null,
                    start = 0,
                    count = 100,
                    sort = "-publishedAt",
                    isLive = true
                ).data.distinctBy { it.uuid }
                // Render the live results as soon as the index responds. Per-video detail calls
                // here used to serialize with hundreds of recent-video/session checks before the
                // screen could show anything.
                if (requestId == liveLoadRequestId) liveVideos = results
                viewModelScope.launch {
                    val nowMillis = System.currentTimeMillis()
                    val cutoffMillis = nowMillis - 24 * 60 * 60 * 1000L
                    // Recently ended replays are supplemental; keep this bounded and off the
                    // critical path so slow origins cannot delay the live list.
                    val candidates = runCatching {
                        searchApi.videos(search = null, start = 0, count = 60, sort = "-publishedAt", isLive = false).data
                    }.getOrDefault(emptyList()).distinctBy { it.uuid }.filter { video ->
                        val published = video.publishedAt?.let(::parseIsoDateMillis)
                        published != null && published >= cutoffMillis && published <= nowMillis
                    }.take(20)
                    val ended = candidates.chunked(8).flatMap { batch -> batch.map { video -> async {
                        runCatching {
                            val endedAt = ApiFactory.create(video.sourceServer(server)).liveSession(video.uuid).endDate?.let(::parseIsoDateMillis)
                            if (endedAt != null && endedAt >= cutoffMillis && endedAt <= nowMillis) video else null
                        }.getOrNull()
                    } }.awaitAll().filterNotNull() }.distinctBy { it.uuid }
                    if (requestId == liveLoadRequestId) {
                        recentlyEndedLiveVideos = ended
                        val endedIds = ended.mapTo(hashSetOf()) { it.uuid }
                        liveVideos = results.filterNot { it.uuid in endedIds }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = message(R.string.error_live, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty())
            } finally {
                if (requestId == liveLoadRequestId) isLoadingLive = false
            }
        }
    }
    fun searchSepia(search: String, sort: String = "-match", category: Int? = null, durationMin: Int? = null, durationMax: Int? = null, liveOnly: Boolean = false, page: Int = 0, append: Boolean = false) {
        val requestId = ++sepiaSearchRequestId
        sepiaSearchJob?.cancel()
        sepiaSearchJob = viewModelScope.launch {
            isSearching = true
            if (!append) { videos = emptyList(); searchSourceErrors = emptyMap(); searchSourcesByVideo = emptyMap(); searchHasMore = false; availabilityByVideo = emptyMap() }
            try {
                error = null
                val savedCache = runCatching { PeerTubeGson.instance.fromJson<List<Video>>(prefs?.getString("lastSearchVideos", "[]"), object : TypeToken<List<Video>>() {}.type) ?: emptyList() }.getOrDefault(emptyList())
                val providers = linkedMapOf<SearchSource, suspend (SearchRequest) -> SearchBatch>()
                providers[SearchSource.SEPIA] = { req ->
                    val result = ApiFactory.sepiaSearch().videos(
                        // Keep the user's literal query intact. Appending a country name
                        // here makes exact English titles and UUID-like terms fail to match.
                        search = req.query, start = req.page * req.pageSize, count = req.pageSize, sort = req.sort.takeUnless { it == "availability" } ?: "-match",
                        categoryOneOf = req.category, durationMin = req.durationMin, durationMax = req.durationMax,
                        isLive = if (req.liveOnly) true else null, languageOneOf = req.language,
                        nsfw = if (req.includeSensitive) "both" else "false"
                    )
                    SearchBatch(result.data, result.total, if ((req.page + 1) * req.pageSize < result.total) req.page + 1 else null)
                }
                providers[SearchSource.PEERSEEK] = { req ->
                    val result = ApiFactory.peerSeek().search(query = req.query, sort = when (req.sort) { "-publishedAt" -> "date"; "-views" -> "views"; else -> "relevance" }, language = req.language, page = req.page + 1, size = req.pageSize, includeLive = true)
                    val items = result.videos.mapNotNull(PeerSeekVideo::asVideo).filter { (!req.liveOnly || it.isLive == true) && (req.durationMin == null || it.duration >= req.durationMin) && (req.durationMax == null || it.duration <= req.durationMax) }
                    SearchBatch(items, result.total, (req.page + 1).takeIf { req.page * req.pageSize + items.size < result.total })
                }
                providers[SearchSource.FEDERATED] = { req ->
                    val host = URI(req.activeServer).host ?: error("Invalid active PeerTube server")
                    require(SafeNetworkTarget.isSafeHttpsHost(host)) { "Active PeerTube server address is not public" }
                    val result = ApiFactory.create("https://$host").searchVideos(req.query, req.page * req.pageSize, req.pageSize, req.sort.takeUnless { it == "availability" } ?: "-match", if (req.includeSensitive) "both" else "false")
                    SearchBatch(result.data, result.total, (req.page + 1).takeIf { (req.page + 1) * req.pageSize < result.total })
                }
                providers[SearchSource.DIRECT] = { req ->
                    val russianSearch = req.country.equals("RU", ignoreCase = true)
                    val knownServers = serversForSearch().filter { it.apiStatus != "unavailable" }.sortedWith(
                        compareByDescending<DiscoveredServer> { russianSearch && it.country.equals("RU", ignoreCase = true) }
                            .thenByDescending { if (russianSearch || !it.country.equals("RU", ignoreCase = true)) it.priority else 0 }
                    )
                    val preferred = (listOf(req.activeServer) + knownServers.map { "https://${it.host}" })
                        .distinct().take(5)
                    val attempts = preferred.mapNotNull { url ->
                        val host = runCatching { URI(url).host }.getOrNull() ?: return@mapNotNull null
                        if (!withContext(kotlinx.coroutines.Dispatchers.IO) { SafeNetworkTarget.isSafeHttpsHost(host) }) return@mapNotNull null
                        runCatching { ApiFactory.create("https://$host").searchVideos(req.query, req.page * req.pageSize, req.pageSize, req.sort.takeUnless { it == "availability" } ?: "-match", if (req.includeSensitive) "both" else "false").data }
                    }
                    if (attempts.isEmpty() || attempts.all { it.isFailure }) error("No known PeerTube server could be reached")
                    val found = attempts.mapNotNull { it.getOrNull() }.flatten()
                    SearchBatch(found, found.size, (req.page + 1).takeIf { found.size >= req.pageSize })
                }
                providers[SearchSource.CUSTOM] = { req ->
                    val client = CustomSearchIndexClient()
                    val attempts = searchSettings.customIndexUrls.map { url -> runCatching { client.search(url, req.query, req.page + 1, req.pageSize) } }
                    if (attempts.isEmpty() || attempts.all { it.isFailure }) error("All custom search indexes failed")
                    val all = attempts.mapNotNull { it.getOrNull() }
                    SearchBatch(all.flatMap { it.videos }, all.sumOf { it.total ?: it.videos.size }, (req.page + 1).takeIf { all.any { it.nextPage != null } })
                }
                providers[SearchSource.LOCAL] = { req ->
                    val cachedQuery = prefs?.getString("lastSearchQuery", "").orEmpty()
                    SearchBatch(if (cachedQuery.equals(req.query, true)) savedCache else emptyList())
                }
                val includeSensitive = searchSettings.includeSensitive && searchSettings.sensitiveDisplayMode != "hide"
                val req = SearchRequest(search, page, 40, sort, category, durationMin, durationMax, liveOnly, contentLanguage(), searchCountryCode, server, includeSensitive)
                val outcome = SearchAggregator(providers).search(req, searchSettings)
                var result = outcome.videos
                result = result.filter { video ->
                    (req.includeSensitive || !video.nsfw) &&
                        (!liveOnly || video.isLive == true) &&
                        (category == null || video.category?.id == category) &&
                        (durationMin == null || video.duration >= durationMin) &&
                        (durationMax == null || video.duration <= durationMax)
                }
                if (sort == "availability" && result.isNotEmpty()) {
                    isVerifyingAvailability = true
                    val checker = ServerAvailabilityManager(serverRepository)
                    val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                    val checks = result.map { video -> async { semaphore.withPermit { video.uuid to checker.checkVideo(video) } } }.awaitAll().toMap()
                    availabilityByVideo = availabilityByVideo + checks
                    discoveredServers = serverRepository?.all().orEmpty()
                    result = result.sortedWith(compareBy<Video> { when (checks[it.uuid]?.state) { "available" -> 0; "unavailable" -> 2; else -> 1 } }.thenByDescending { it.views })
                    isVerifyingAvailability = false
                }
                if (searchSettings.availabilityFilter == "available_only" && result.isNotEmpty()) {
                    isVerifyingAvailability = true
                    val checker = ServerAvailabilityManager(serverRepository)
                    val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                    result = result.map { video -> async {
                        semaphore.withPermit {
                            val status = checker.checkVideo(video)
                            availabilityByVideo = availabilityByVideo + (video.uuid to status)
                            if (status.state == "available") video else null
                        }
                    } }.awaitAll().filterNotNull()
                    discoveredServers = serverRepository?.all().orEmpty()
                    isVerifyingAvailability = false
                } else if (searchSettings.availabilityFilter == "unknown_or_available") {
                    result = result.filter { availabilityByVideo[it.uuid]?.state != "unavailable" }
                }
                if (requestId == sepiaSearchRequestId) {
                    videos = (if (append) videos else emptyList()) + result
                    query = search
                    searchSourceErrors = outcome.errors
                    searchSourcesByVideo = searchSourcesByVideo + outcome.sourcesByVideo
                    searchHasMore = outcome.hasMore
                    searchNextPage = page + 1
                    if (outcome.errors.isEmpty() || result.isNotEmpty()) prefs?.edit()?.putString("lastSearchQuery", search)?.putString("lastSearchVideos", PeerTubeGson.instance.toJson(videos.take(100)))?.apply()
                    if (result.isEmpty() && outcome.errors.isNotEmpty()) error = "${outcome.errors.size} search source(s) failed: ${outcome.errors.entries.joinToString { "${it.key.key}: ${it.value}" }}"
                }
            } catch (e: Exception) {
                if (requestId == sepiaSearchRequestId) error = message(R.string.error_sepia, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty())
            } finally { if (requestId == sepiaSearchRequestId) { isSearching = false; isVerifyingAvailability = false } }
        }
    }
    fun loadChannelVideos(name: String, host: String) {
        channelReturnTab = tab
        val normalizedHost = (host.ifBlank { server }).trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        val serverUrl = "https://$normalizedHost"
        channelVideos = emptyList()
        viewModelScope.launch {
            tab = "channel"
            activeChannelTitle = name
            try {
                error = null
                channelVideos = ApiFactory.create(serverUrl).channelVideos("$name@$normalizedHost").data
            } catch (e: Exception) {
                error = message(R.string.error_server, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty())
            }
        }
    }
    fun loadServerChannels(host: String, loadMore: Boolean = false) {
        val normalizedHost = (host.ifBlank { server }).trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (!loadMore || normalizedHost != activeServerChannelsHost) {
            serverChannels = emptyList()
            serverChannelsTotal = 0
        }
        channelReturnTab = "channels"
        activeServerChannelsHost = normalizedHost
        tab = "server_channels"
        viewModelScope.launch {
            isLoadingServerChannels = true
            try {
                error = null
                val start = if (loadMore && normalizedHost == activeServerChannelsHost) serverChannels.size else 0
                val page = ApiFactory.create("https://$normalizedHost").videoChannels(start = start, count = 100)
                serverChannelsTotal = page.total
                val withHost = page.data.map { channel ->
                    val channelHost = channel.host?.trim()?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')
                    channel.copy(host = channelHost?.takeIf(String::isNotBlank) ?: normalizedHost)
                }
                serverChannels = (if (start > 0) serverChannels + withHost else withHost)
                    .sortedWith(compareByDescending<PeerTubeChannel> { it.videosCount ?: -1L }
                        .thenBy { it.displayName ?: it.name.orEmpty() })
                val countBatches = withHost.chunked(6)
                countBatches.forEachIndexed { batchIndex, batch ->
                    val countedBatch = batch.map { channel -> async {
                        if (channel.videosCount != null || channel.name.isNullOrBlank()) channel
                        else {
                            val channelHost = channel.host?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/') ?: normalizedHost
                            val count = runCatching {
                                ApiFactory.create("https://$channelHost")
                                    .channelVideos("${channel.name}@$channelHost", count = 1).total.toLong()
                            }.getOrNull()
                            channel.copy(videosCount = count)
                        }
                    } }.awaitAll()
                    if (normalizedHost == activeServerChannelsHost) {
                        val countedById = countedBatch.associateBy { it.id?.toString() ?: "${it.name}@${it.host}" }
                        serverChannels = serverChannels.map { current ->
                            countedById[current.id?.toString() ?: "${current.name}@${current.host}"] ?: current
                        }.sortedWith(compareByDescending<PeerTubeChannel> { it.videosCount ?: -1L }
                            .thenBy { it.displayName ?: it.name.orEmpty() })
                    }
                    if (batchIndex < countBatches.lastIndex) delay(1_300L)
                }
            } catch (e: Exception) {
                error = message(R.string.error_server, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty())
            } finally {
                isLoadingServerChannels = false
            }
        }
    }
    fun loadInstances(loadMore: Boolean = false) {
        viewModelScope.launch {
            isLoadingInstances = true
            try {
                error = null
                val start = if (loadMore) instances.size else 0
                val page = ApiFactory.directory().instances(start = start, count = 100)
                instanceTotal = page.total
                val enriched = page.data.chunked(8).flatMap { batch ->
                    batch.map { instance -> async {
                        val languages = runCatching {
                            ApiFactory.config("https://${instance.host}").customConfig().instance?.languages.orEmpty()
                        }.getOrDefault(emptyList())
                        instance.copy(languages = languages)
                    } }.awaitAll()
                }
                instances = if (loadMore) instances + enriched else enriched
                serverRepository?.merge(enriched.map { instance -> DiscoveredServer(
                    host = instance.host.trim().lowercase(Locale.ROOT), name = instance.name, version = instance.version,
                    languages = instance.languages, country = instance.country, discoveredFrom = setOf("joinpeertube"),
                    apiStatus = "unknown", totalVideos = instance.totalVideos
                ) })
                discoveredServers = serverRepository?.all().orEmpty()
            } catch (e: Exception) {
                error = message(R.string.error_directory, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty())
            } finally {
                isLoadingInstances = false
            }
        }
    }
    private suspend fun recommendationSubscriptionSeeds(): List<Video> {
        if (subscriptions.isEmpty()) return emptyList()
        val semaphore = kotlinx.coroutines.sync.Semaphore(4)
        return kotlinx.coroutines.coroutineScope {
            subscriptions.map { followed -> async {
                semaphore.withPermit {
                    val name = followed.name.trim()
                    val host = followed.host.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').trimEnd('.')
                    if (name.isBlank() || !host.matches(Regex("(?i)[a-z0-9.-]{1,253}"))) return@withPermit emptyList()
                    try { ApiFactory.create("https://$host").channelVideos("$name@$host", count = 30).data }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) {
                        runCatching { ApiFactory.create("https://$host").videos(search = name, count = 30).data.filter { v ->
                            (v.channel?.name ?: v.account?.name).equals(name, true) &&
                                (v.channel?.host ?: v.account?.host ?: v.host.orEmpty()).equals(host, true)
                        } }.getOrDefault(emptyList())
                    }
                }
            } }.awaitAll().flatten().distinctBy { it.uuid }
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            isLoadingRecommendations = true
            try {
                error = null
                val watched = when (recommendationBasis) {
                    "subscriptions" -> (if (subscriptionVideos.isEmpty()) recommendationSubscriptionSeeds().also { subscriptionVideos = it } else subscriptionVideos).take(60)
                    "both" -> (history.take(30) + (if (subscriptionVideos.isEmpty()) recommendationSubscriptionSeeds().also { subscriptionVideos = it } else subscriptionVideos).take(60)).distinctBy { it.uuid }
                    else -> history.take(30)
                }
                if (watched.isEmpty()) { recommendations = emptyList(); return@launch }
                val stopWords = setOf("the", "and", "for", "with", "from", "this", "that", "как", "что", "это", "для", "или", "при", "про", "видео", "video")
                val titleTokens = watched.flatMap { it.name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) }
                    .filter { it.length >= 3 && it !in stopWords }
                val tags = watched.flatMap { it.tags.orEmpty() }.map { it.trim() }.filter { it.length >= 2 }
                val queries = (if (tags.isNotEmpty()) tags else titleTokens)
                    .groupingBy { it.lowercase() }.eachCount().entries
                    .sortedByDescending { it.value }.map { it.key }.distinct().take(5)
                if (queries.isEmpty()) { recommendations = emptyList(); return@launch }
                val searchApi = ApiFactory.sepiaSearch()
                val candidates = queries.map { term -> async {
                    runCatching { searchApi.videos(search = countrySearchTerm(term), count = 20, languageOneOf = recommendationContentLanguage()).data }
                        .getOrDefault(emptyList())
                } }.awaitAll().flatten()
                    .distinctBy { it.uuid }
                    .filterNot { candidate -> candidate.uuid in excludedRecommendationIds || (watchedRecommendationThreshold != "never" && candidate.uuid in watchedVideoIds) }
                val watchedTags = watched.flatMap { it.tags.orEmpty() }.map { it.lowercase() }.toSet()
                val watchedWords = titleTokens.toSet()
                recommendations = candidates.sortedByDescending { candidate ->
                    val tagScore = candidate.tags.orEmpty().count { it.lowercase() in watchedTags } * 4
                    val titleScore = candidate.name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).count { it in watchedWords }
                    val channelScore = if (watched.any { it.channel?.name != null && it.channel.name == candidate.channel?.name }) 1 else 0
                    val country = searchCountryCode.lowercase(Locale.ROOT)
                    val host = (candidate.host ?: candidate.channel?.host ?: candidate.account?.host).orEmpty().lowercase(Locale.ROOT)
                    val countryBonus = if (country.isNotBlank() && host.substringAfterLast('.', "") == country) 2 else 0
                    tagScore + titleScore + channelScore + countryBonus
                }
                if (recommendations.isNotEmpty()) publishAndroidTvRecommendations(recommendations)
            } catch (e: Exception) { error = message(R.string.error_recommendations, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty()) }
            finally { isLoadingRecommendations = false }
        }
    }
}

data class VideoSubscription(val name: String, val host: String, val displayName: String)

internal fun normalizeVideoSubscription(subscription: VideoSubscription): VideoSubscription? {
    val name = subscription.name?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,200}")) } ?: return null
    val host = subscription.host?.trim()?.removePrefix("https://")?.removePrefix("http://")
        ?.substringBefore('/')?.trimEnd('.')?.takeIf { runCatching { accountServerOrigin("https://$it"); true }.getOrDefault(false) } ?: return null
    return subscription.copy(name = name, host = host, displayName = subscription.displayName?.takeIf(String::isNotBlank) ?: name)
}

private fun isTelevisionDevice(context: android.content.Context): Boolean =
    context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)

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
                        focusManager.moveFocus(FocusDirection.Left)
                        true
                    }
                    else if (focusedVideoMenu != null && collapsedMenuFocusRequesters[focusedMenuSectionKey] != null) {
                        runCatching { collapsedMenuFocusRequesters[focusedMenuSectionKey]?.requestFocus() }
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
                Spacer(Modifier.height(150.dp))
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
        } else {
            val railItems = listOf("home" to R.string.nav_home, "popular" to R.string.nav_popular, "recommendations" to R.string.nav_recommendations, "live" to R.string.nav_live, "subscriptions" to R.string.nav_subscriptions, "channels" to R.string.nav_channels, "instances" to R.string.nav_instances, "history" to R.string.nav_history, "favorites" to R.string.nav_favorites, "settings" to R.string.nav_settings).filter { (key, _) -> key == "home" || key == "settings" || key !in m.hiddenSections }
            Surface(Modifier.width(60.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxSize().padding(top = 154.dp).focusGroup(), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Spacer(Modifier.height(if (headerHidden) 0.dp else 70.dp))
            if (m.tab == "instances") {
                Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(value = m.server, onValueChange = { m.server = it }, label = { Text(stringResource(R.string.server_hint)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(sectionFirstFocusRequester), singleLine = true)
                Button(onClick = { m.save(); m.loadHomePage(); m.tab = "home" }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.connect)) }
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
                                            modifier = Modifier.heightIn(min = 52.dp)
                                        ) { Text(stringResource(R.string.connect)) }
                                        TextButton(
                                            onClick = { m.checkServerAvailability(discovered.host) },
                                            enabled = discovered.host !in m.checkingServerHosts,
                                            modifier = Modifier.scale(if (checkFocused) 1.08f else 1f)
                                                .background(if (checkFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                                .onFocusChanged { checkFocused = it.isFocused }
                                                .heightIn(min = 52.dp)
                                        ) { Text(if (discovered.host in m.checkingServerHosts) "…" else stringResource(R.string.server_check_api)) }
                                        TextButton(onClick = { m.toggleServerFavorite(discovered.host) }, modifier = Modifier.heightIn(min = 52.dp)) { Text(if (discovered.favorite) "★" else "☆") }
                                        TextButton(onClick = { m.moveServerPriority(discovered.host, -1) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("↑") }
                                        TextButton(onClick = { m.moveServerPriority(discovered.host, 1) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("↓") }
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
                    ObserveScrollDirection(searchGridState, { searchGridState.firstVisibleItemIndex * 1_000_000L + searchGridState.firstVisibleItemScrollOffset }, { searchGridState.isScrollInProgress }) { headerHidden = it }
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
                    ObserveScrollDirection(searchListState, { searchListState.firstVisibleItemIndex * 1_000_000L + searchListState.firstVisibleItemScrollOffset }, { searchListState.isScrollInProgress }) { headerHidden = it }
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
                    ObserveScrollDirection(homeShelvesListState, { homeShelvesListState.firstVisibleItemIndex * 1_000_000L + homeShelvesListState.firstVisibleItemScrollOffset }, { homeShelvesListState.isScrollInProgress }) { headerHidden = it }
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
                                onMarkWatched = m::markRecommendationWatched
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
                            onMarkWatched = m::markRecommendationWatched
                        )
                    }
                    }
                } else {
                    val sortedHomePageVideos = remember(m.homePageVideos, m.videoSortMode) { m.sortVideos(m.homePageVideos) }
                    val homeGridState = rememberLazyGridState()
                    ObserveScrollDirection(homeGridState, { homeGridState.firstVisibleItemIndex * 1_000_000L + homeGridState.firstVisibleItemScrollOffset }, { homeGridState.isScrollInProgress }) { headerHidden = it }
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
                                    isAtLeftBoundary = index % columns == 0
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
                    ObserveScrollDirection(liveListState, { liveListState.firstVisibleItemIndex * 1_000_000L + liveListState.firstVisibleItemScrollOffset }, { liveListState.isScrollInProgress }) { headerHidden = it }
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
                    ObserveScrollDirection(sectionGridState, { sectionGridState.firstVisibleItemIndex * 1_000_000L + sectionGridState.firstVisibleItemScrollOffset }, { sectionGridState.isScrollInProgress }) { headerHidden = it }
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
                    ObserveScrollDirection(sectionListState, { sectionListState.firstVisibleItemIndex * 1_000_000L + sectionListState.firstVisibleItemScrollOffset }, { sectionListState.isScrollInProgress }) { headerHidden = it }
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
            .padding(start = 80.dp, end = 20.dp, top = 4.dp).fillMaxWidth().zIndex(10f)
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

private fun createSettingsBackup(context: android.content.Context): String {
    val fileName = "PeerTubeTV-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(java.util.Date())}.zip"
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/PeerTubeTV")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create backup in Downloads")
        try {
            resolver.openOutputStream(uri)?.use { writeSettingsBackupZip(context, it) } ?: error("Backup output could not be opened")
            val published = android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            return "Downloads/PeerTubeTV/$fileName"
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
    val backupParent = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    val directory = java.io.File(backupParent, "PeerTubeTV")
    check(directory.exists() || directory.mkdirs()) { "Could not create backup directory" }
    val file = java.io.File(directory, fileName)
    java.io.FileOutputStream(file).use { writeSettingsBackupZip(context, it) }
    return file.absolutePath
}

private fun writeSettingsBackupZip(context: android.content.Context, output: java.io.OutputStream) {
    val values = context.getSharedPreferences("peertube", 0).all
    val json = com.google.gson.JsonObject()
    values.filterKeys { !it.startsWith("proxy", ignoreCase = true) }.forEach { (key, value) ->
        val item = com.google.gson.JsonObject()
        val type = when (value) {
            is String -> "string"
            is Boolean -> "boolean"
            is Int -> "int"
            is Long -> "long"
            is Float -> "float"
            is Set<*> -> "string_set"
            else -> return@forEach
        }
        item.addProperty("type", type)
        item.add("value", PeerTubeGson.instance.toJsonTree(value))
        json.add(key, item)
    }
    java.util.zip.ZipOutputStream(output).use { zip ->
        zip.putNextEntry(java.util.zip.ZipEntry("settings.json"))
        zip.write(PeerTubeGson.instance.toJson(json).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

private fun restoreSettingsBackup(context: android.content.Context, uri: android.net.Uri) {
    val settingsJson = context.contentResolver.openInputStream(uri)?.use { input ->
        java.util.zip.ZipInputStream(input).use { zip ->
            val entry = zip.nextEntry
            require(entry?.name == "settings.json" && !entry.isDirectory) { "Backup does not contain settings.json" }
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BACKUP_SETTINGS_BYTES) { "Backup settings file is too large" }
                output.write(buffer, 0, count)
            }
            require(zip.nextEntry == null) { "Backup contains unexpected files" }
            output.toString(Charsets.UTF_8.name())
        }
    } ?: error("Backup input could not be opened")
    require(settingsJson.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_SETTINGS_BYTES) { "Backup settings file is too large" }
    val json = com.google.gson.JsonParser.parseString(settingsJson).asJsonObject
    val validated = SettingsBackupCodec.decode(json)
    val editor = context.getSharedPreferences("peertube", 0).edit()
    validated.forEach { (key, value) ->
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
        }
    }
    check(editor.commit()) { "Settings backup could not be restored" }
}

private const val MAX_BACKUP_SETTINGS_BYTES = 512 * 1024

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsDashboard(
    model: HomeModel,
    selectedCategory: String?,
    selectedSubpage: String?,
    requestInitialFocus: Boolean,
    firstFocusRequester: FocusRequester? = null,
    onSelectCategory: (String?) -> Unit,
    onSelectSubpage: (String?) -> Unit,
    historyPeriodHours: Int,
    onHistoryPeriodChange: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onClearHistoryPeriod: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    val firstSettingsCardFocus = firstFocusRequester ?: remember { FocusRequester() }
    LaunchedEffect(selectedCategory, requestInitialFocus) {
        if (selectedCategory == null && requestInitialFocus) runCatching { firstSettingsCardFocus.requestFocus() }
    }
    val settingsContext = LocalContext.current
    val playerPrefs = remember(settingsContext) { settingsContext.getSharedPreferences("peertube", 0) }
    var homeServerDraft by remember(model.homePageServer) { mutableStateOf(model.homePageServer) }
    var homeChannelDraft by remember(model.homePageChannel) { mutableStateOf(model.homePageChannel) }
    var customIndexesInput by remember { mutableStateOf(model.searchSettings.customIndexUrls.joinToString("\n")) }
    var manualServerInput by remember { mutableStateOf("") }
    var remoteServerCatalogInput by remember { mutableStateOf(model.serverJsonCatalogUrl()) }
    val serverImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { settingsContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
            .onSuccess { json -> if (!json.isNullOrBlank()) model.importServerCatalog(json) }
            .onFailure { model.discoveryError = it.localizedMessage ?: it.javaClass.simpleName }
    }
    var subtitleSize by remember { mutableFloatStateOf(playerPrefs.getFloat("subtitleSize", 0.053f)) }
    var subtitleOffset by remember { mutableFloatStateOf(playerPrefs.getFloat("subtitleBottomOffset", 0.20f).coerceIn(0.02f, 0.50f)) }
    var subtitleHorizontal by remember { mutableFloatStateOf(playerPrefs.getFloat("subtitleHorizontalPosition", 0.50f).coerceIn(0.05f, 0.95f)) }
    var subtitleColor by remember { mutableStateOf(playerPrefs.getString("subtitleColor", if (playerPrefs.getString("subtitleStyle", "default") == "yellow") "yellow" else "white") ?: "white") }
    var subtitleBackgroundEnabled by remember { mutableStateOf(playerPrefs.getBoolean("subtitleBackgroundEnabled", false)) }
    var subtitleBackgroundOpacity by remember { mutableFloatStateOf(playerPrefs.getFloat("subtitleBackgroundOpacity", 0.65f).coerceIn(0.15f, 0.90f)) }
    var subtitlesDefault by remember { mutableStateOf(playerPrefs.getBoolean("subtitlesEnabledByDefault", false)) }
    var videoMetadataTranslationEnabled by remember(playerPrefs) { mutableStateOf(playerPrefs.getBoolean("translateVideoMetadataEnabled", false)) }
    var subtitleTranslationDefault by remember(playerPrefs) { mutableStateOf(playerPrefs.getBoolean("subtitleTranslationEnabledByDefault", false)) }
    var preferredSubtitleLanguage by remember { mutableStateOf(playerPrefs.getString("preferredSubtitleLanguage", "auto") ?: "auto") }
    var subtitleTranslationLanguage by remember(playerPrefs, model.uiLanguage) {
        mutableStateOf(if (!playerPrefs.getBoolean("subtitleTranslationLanguageCustom", false)) interfaceSubtitleLanguage(model.uiLanguage)
        else playerPrefs.getString("subtitleTranslationLanguage", interfaceSubtitleLanguage(model.uiLanguage)) ?: interfaceSubtitleLanguage(model.uiLanguage))
    }
    var useBuiltInTvKeyboard by remember(playerPrefs) { mutableStateOf(playerPrefs.getBoolean("useBuiltInTvKeyboard", true)) }
    var downloadedTranslationLanguages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var translationModelLanguage by remember { mutableStateOf("en") }
    var translationModelStatus by remember { mutableStateOf<String?>(null) }
    var translationModelBusy by remember { mutableStateOf(false) }
    LaunchedEffect(settingsContext) {
        refreshDownloadedTranslationLanguages { result ->
            result.onSuccess { downloadedTranslationLanguages = it }
                .onFailure { translationModelStatus = translationModelErrorMessage(settingsContext, it) }
        }
    }
    var rememberSubtitleChannel by remember { mutableStateOf(playerPrefs.getBoolean("rememberSubtitleByChannel", true)) }
    val categories = listOf(
        "accounts" to R.string.settings_accounts,
        "language" to R.string.settings_language_country,
        "general" to R.string.settings_general,
        "interface" to R.string.settings_interface,
        "subtitles" to R.string.settings_subtitles,
        "translation" to R.string.settings_translation,
        "player" to R.string.settings_player,
        "search" to R.string.settings_search,
        "network" to R.string.settings_network,
        "backup" to R.string.settings_backup,
        "about" to R.string.settings_about
    )
    val languageOptions = listOf("auto" to stringResource(R.string.language_auto)) + supportedUiLanguages.map { tag ->
        val locale = Locale.forLanguageTag(tag)
        tag to locale.getDisplayName(locale)
    }
    val recommendationLanguageOptions = remember {
        listOf("country" to "") + Locale.getISOLanguages().distinct().map { code ->
            code to Locale(code).getDisplayLanguage(Locale.getDefault()).takeIf(String::isNotBlank).orEmpty()
        }.filter { it.second.isNotBlank() }.sortedBy { it.second.lowercase(Locale.getDefault()) }
    }.map { (code, label) -> code to if (code == "country") stringResource(R.string.recommendations_language_country) else label }
    val translationTargetLanguage = interfaceSubtitleLanguage(model.uiLanguage)
    val subtitleTranslationOptions = listOf("auto" to stringResource(R.string.subtitle_translation_follow_interface, translationLanguageName(translationTargetLanguage))) +
        subtitleTranslationLanguageOptions().sortedBy { if (it.first == translationTargetLanguage) "" else it.second.lowercase(Locale.getDefault()) }
    val countryOptions = remember { Locale.getISOCountries().map { code -> code to Locale("", code).getDisplayCountry(Locale.getDefault()) }.filter { it.second.isNotBlank() }.sortedBy { it.second.lowercase(Locale.getDefault()) } }
    Box(Modifier.fillMaxSize()) {
        if (selectedCategory == null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(categories, key = { _, item -> item.first }) { index, (key, title) ->
                    Card(
                        onClick = { onSelectCategory(key) },
                        modifier = Modifier.fillMaxWidth().height(132.dp)
                            .then(if (index == 0) Modifier.focusRequester(firstSettingsCardFocus) else Modifier),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                            when (key) {
                                "accounts" -> Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(54.dp))
                                "subtitles" -> Icon(Icons.Filled.ClosedCaption, contentDescription = null, modifier = Modifier.size(54.dp))
                                "translation" -> Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(54.dp))
                                "language" -> Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(54.dp))
                                "general" -> Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(54.dp))
                                "interface" -> Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(54.dp))
                                "player" -> Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(54.dp))
                                "search" -> SearchGlyph(54.dp)
                                "network" -> Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(54.dp))
                                "backup" -> Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(54.dp))
                                else -> Text("i", style = MaterialTheme.typography.displaySmall)
                            }
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (selectedCategory != null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    val panelTitle = when (selectedSubpage) {
                        "language" -> R.string.language
                        "country" -> R.string.country_label
                        else -> categories.firstOrNull { it.first == selectedCategory }?.second ?: R.string.settings_general
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(panelTitle), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (selectedSubpage != null) onSelectSubpage(null) else onSelectCategory(null) }) { Text("‹") }
                    }
                    HorizontalDivider()
                    if (selectedCategory == "translation") {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.translate_video_metadata), Modifier.weight(1f))
                                Switch(videoMetadataTranslationEnabled, { videoMetadataTranslationEnabled = it; playerPrefs.edit().putBoolean("translateVideoMetadataEnabled", it).apply() })
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.subtitle_translation_default), Modifier.weight(1f))
                                Switch(subtitleTranslationDefault, { subtitleTranslationDefault = it; playerPrefs.edit().putBoolean("subtitleTranslationEnabledByDefault", it).apply() })
                            }
                                val translationChoice = if (!playerPrefs.getBoolean("subtitleTranslationLanguageCustom", false)) "auto" else subtitleTranslationLanguage
                                SettingsChoice(stringResource(R.string.subtitle_translate_to), if (translationChoice == "auto") stringResource(R.string.subtitle_translation_follow_interface, translationLanguageName(translationTargetLanguage)) else translationLanguageName(subtitleTranslationLanguage), subtitleTranslationOptions) {
                                    val useInterfaceLanguage = it == "auto"
                                    subtitleTranslationLanguage = if (useInterfaceLanguage) translationTargetLanguage else it
                                    playerPrefs.edit().putString("subtitleTranslationLanguage", subtitleTranslationLanguage).putBoolean("subtitleTranslationLanguageCustom", !useInterfaceLanguage).apply()
                                }
                                Text(stringResource(R.string.subtitle_models_title), style = MaterialTheme.typography.titleMedium)
                                val availableModelLanguages = downloadableSubtitleTranslationLanguages().filter { it.first !in downloadedTranslationLanguages }
                                val selectedModelLanguage = translationModelLanguage.takeIf { it in availableModelLanguages.map { language -> language.first } }
                                    ?: availableModelLanguages.firstOrNull()?.first.orEmpty()
                                if (availableModelLanguages.isNotEmpty()) {
                                    SettingsChoice(
                                        stringResource(R.string.subtitle_model_choose),
                                        translationLanguageName(selectedModelLanguage),
                                        availableModelLanguages
                                    ) { translationModelLanguage = it; translationModelStatus = null }
                                    Button(
                                        onClick = {
                                            translationModelBusy = true
                                            translationModelStatus = settingsContext.getString(R.string.subtitle_model_downloading)
                                            downloadTranslationLanguage(selectedModelLanguage) { result ->
                                                translationModelBusy = false
                                                result.onSuccess {
                                                    downloadedTranslationLanguages = downloadedTranslationLanguages + selectedModelLanguage
                                                    translationModelStatus = settingsContext.getString(R.string.subtitle_model_downloaded)
                                                }.onFailure {
                                                    translationModelStatus = translationModelErrorMessage(settingsContext, it)
                                                }
                                            }
                                        },
                                        enabled = !translationModelBusy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (translationModelBusy) stringResource(R.string.subtitle_model_downloading) else stringResource(R.string.subtitle_model_download)) }
                                } else {
                                    Text(stringResource(R.string.subtitle_models_all_downloaded), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(stringResource(R.string.subtitle_models_downloaded_title), style = MaterialTheme.typography.titleSmall)
                                if (downloadedTranslationLanguages.isEmpty()) {
                                    Text(stringResource(R.string.subtitle_models_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    downloadableSubtitleTranslationLanguages().filter { it.first in downloadedTranslationLanguages }.forEach { (code, label) ->
                                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(label, Modifier.weight(1f))
                                            TextButton(
                                                enabled = !translationModelBusy,
                                                onClick = {
                                                    translationModelBusy = true
                                                    translationModelStatus = settingsContext.getString(R.string.subtitle_model_removing)
                                                    deleteTranslationLanguage(code) { result ->
                                                        translationModelBusy = false
                                                        result.onSuccess {
                                                            downloadedTranslationLanguages = downloadedTranslationLanguages - code
                                                            translationModelStatus = settingsContext.getString(R.string.subtitle_model_removed)
                                                        }.onFailure {
                                                            translationModelStatus = translationModelErrorMessage(settingsContext, it)
                                                        }
                                                    }
                                                }
                                            ) { Text(stringResource(R.string.subtitle_model_remove)) }
                                        }
                                    }
                                }
                                translationModelStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        }
                    } else if (selectedSubpage == "language") {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(languageOptions, key = { it.first }) { (tag, label) ->
                                TextButton(onClick = { model.setUiLanguage(tag); onSelectSubpage(null) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (tag == model.uiLanguage) "$label  ✓" else label, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    } else if (selectedSubpage == "country") {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item {
                                TextButton(onClick = { model.updateSearchCountry(""); onSelectSubpage(null) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (model.searchCountryCode.isBlank()) "${stringResource(R.string.country_default)}  ✓" else stringResource(R.string.country_default), modifier = Modifier.fillMaxWidth())
                                }
                            }
                            items(countryOptions, key = { it.first }) { (code, name) ->
                                TextButton(onClick = { model.updateSearchCountry(code); onSelectSubpage(null) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (code == model.searchCountryCode) "$name  ✓" else name, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (selectedCategory) {
                            "accounts" -> PeerTubeAccountSettingsPanel(model = model) { server ->
                                model.server = server
                                model.save()
                                model.loadHomePage()
                                model.loadSubscriptions()
                            }
                            "language" -> {
                                val langLabel = if (model.uiLanguage == "auto") stringResource(R.string.language_auto) else {
                                    val locale = Locale.forLanguageTag(model.uiLanguage)
                                    locale.getDisplayName(locale)
                                }
                                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(onClick = { onSelectSubpage("language") }, modifier = Modifier.fillMaxWidth()) {
                                    Text(langLabel, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Text(stringResource(R.string.country_label), style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { onSelectSubpage("country") }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (model.searchCountryCode.isBlank()) stringResource(R.string.country_default) else countryOptions.firstOrNull { it.first == model.searchCountryCode }?.second ?: model.searchCountryCode)
                                }
                                val recommendationLanguageLabel = if (model.recommendationLanguage == "country") stringResource(R.string.recommendations_language_country) else Locale.forLanguageTag(model.recommendationLanguage).getDisplayLanguage(Locale.getDefault())
                                SettingsChoice(stringResource(R.string.recommendations_language), recommendationLanguageLabel, recommendationLanguageOptions) { model.updateRecommendationLanguage(it) }
                            }
                            "subtitles" -> {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.subtitle_remember_channel), Modifier.weight(1f)); Switch(rememberSubtitleChannel, { rememberSubtitleChannel = it; playerPrefs.edit().putBoolean("rememberSubtitleByChannel", it).apply() }) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.subtitle_default_enabled), Modifier.weight(1f)); Switch(subtitlesDefault, { subtitlesDefault = it; playerPrefs.edit().putBoolean("subtitlesEnabledByDefault", it).apply() }) }
                                SettingsChoice(stringResource(R.string.subtitle_color), stringResource(when (subtitleColor) { "yellow" -> R.string.subtitle_color_yellow; "cyan" -> R.string.subtitle_color_cyan; "green" -> R.string.subtitle_color_green; else -> R.string.subtitle_color_white }), listOf("white" to stringResource(R.string.subtitle_color_white), "yellow" to stringResource(R.string.subtitle_color_yellow), "cyan" to stringResource(R.string.subtitle_color_cyan), "green" to stringResource(R.string.subtitle_color_green))) { subtitleColor = it; playerPrefs.edit().putString("subtitleColor", it).apply() }
                                SettingsChoice(stringResource(R.string.subtitle_size), "${(subtitleSize * 100).toInt()}%", listOf("0.04" to "Small", "0.053" to "Medium", "0.065" to "Large")) { subtitleSize = it.toFloat(); playerPrefs.edit().putFloat("subtitleSize", subtitleSize).apply() }
                                SettingsChoice(stringResource(R.string.subtitle_horizontal_position), "${(subtitleHorizontal * 100).toInt()}%", (5..95 step 5).map { "${it / 100f}" to "$it%" }) { subtitleHorizontal = it.toFloat(); playerPrefs.edit().putFloat("subtitleHorizontalPosition", subtitleHorizontal).apply() }
                                SettingsChoice(stringResource(R.string.subtitle_bottom_offset), "${(subtitleOffset * 100).toInt()}%", (5..50 step 5).map { "${it / 100f}" to "$it%" }) { subtitleOffset = it.toFloat(); playerPrefs.edit().putFloat("subtitleBottomOffset", subtitleOffset).apply() }
                                SettingsChoice(stringResource(R.string.subtitle_language), if (preferredSubtitleLanguage == "auto") stringResource(R.string.subtitle_language_auto) else preferredSubtitleLanguage.uppercase(), listOf("auto" to stringResource(R.string.subtitle_language_auto), "ru" to "Русский", "en" to "English", "de" to "Deutsch", "fr" to "Français", "es" to "Español")) { preferredSubtitleLanguage = it; playerPrefs.edit().putString("preferredSubtitleLanguage", it).apply() }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.subtitle_background_enabled), Modifier.weight(1f)); Switch(subtitleBackgroundEnabled, { subtitleBackgroundEnabled = it; playerPrefs.edit().putBoolean("subtitleBackgroundEnabled", it).apply() }) }
                                if (subtitleBackgroundEnabled) SettingsChoice(stringResource(R.string.subtitle_background_opacity), "${(subtitleBackgroundOpacity * 100).toInt()}%", listOf("0.25" to "25%", "0.40" to "40%", "0.55" to "55%", "0.70" to "70%", "0.85" to "85%")) { subtitleBackgroundOpacity = it.toFloat(); playerPrefs.edit().putFloat("subtitleBackgroundOpacity", subtitleBackgroundOpacity).apply() }
                            }
                            "general" -> {
                                val homeModeLabel = stringResource(when (model.homePageDisplayMode) {
                                    "interests" -> R.string.home_mode_interests
                                    "server" -> R.string.home_mode_server
                                    "channel" -> R.string.home_mode_channel
                                    else -> R.string.home_mode_recommended
                                })
                                SettingsChoice(stringResource(R.string.home_content), homeModeLabel, listOf(
                                    "recommended" to stringResource(R.string.home_mode_recommended),
                                    "interests" to stringResource(R.string.home_mode_interests),
                                    "server" to stringResource(R.string.home_mode_server),
                                    "channel" to stringResource(R.string.home_mode_channel)
                                )) { model.updateHomePageDisplayMode(it) }
                                when (model.homePageDisplayMode) {
                                    "interests" -> {
                                        Text(stringResource(R.string.home_interests_help), style = MaterialTheme.typography.bodySmall)
                                        val labels = listOf(
                                            R.string.home_interest_music, R.string.home_interest_films, R.string.home_interest_vehicles,
                                            R.string.home_interest_art, R.string.home_interest_sports, R.string.home_interest_travel,
                                            R.string.home_interest_gaming, R.string.home_interest_people, R.string.home_interest_comedy,
                                            R.string.home_interest_entertainment, R.string.home_interest_news, R.string.home_interest_howto,
                                            R.string.home_interest_education, R.string.home_interest_activism, R.string.home_interest_science,
                                            R.string.home_interest_animals, R.string.home_interest_kids, R.string.home_interest_food,
                                            R.string.home_interest_nature
                                        )
                                        labels.forEachIndexed { index, label ->
                                            val id = index + 1
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                                Checkbox(checked = id in model.homePageInterests, onCheckedChange = { model.updateHomePageInterest(id, it) })
                                            }
                                        }
                                        if (model.homePageInterests.isEmpty()) Text(stringResource(R.string.home_interests_empty), color = MaterialTheme.colorScheme.error)
                                    }
                                    "server" -> {
                                        OutlinedTextField(
                                            value = homeServerDraft,
                                            onValueChange = { homeServerDraft = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            label = { Text(stringResource(R.string.home_server_address)) },
                                            placeholder = { Text(model.server) }
                                        )
                                        Text(stringResource(R.string.home_server_hint), style = MaterialTheme.typography.bodySmall)
                                        Button(onClick = { model.saveHomePageServer(homeServerDraft.ifBlank { model.server }) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_source_check_save)) }
                                    }
                                    "channel" -> {
                                        OutlinedTextField(
                                            value = homeChannelDraft,
                                            onValueChange = { homeChannelDraft = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            label = { Text(stringResource(R.string.home_channel_address)) },
                                            placeholder = { Text("channel@example.org") }
                                        )
                                        Button(onClick = { model.saveHomePageChannel(homeChannelDraft) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_source_check_save)) }
                                    }
                                }
                                model.homePageConfigStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                val launchLabel = stringResource(when (model.launchTab) {
                                    "popular" -> R.string.nav_popular; "live" -> R.string.nav_live; "subscriptions" -> R.string.nav_subscriptions
                                    "channels" -> R.string.nav_channels; "recommendations" -> R.string.nav_recommendations; "instances" -> R.string.nav_instances
                                    "history" -> R.string.nav_history; "favorites" -> R.string.nav_favorites; else -> R.string.nav_home
                                })
                                SettingsChoice(stringResource(R.string.startup_section), launchLabel, listOf(
                                    "home" to stringResource(R.string.nav_home), "popular" to stringResource(R.string.nav_popular),
                                    "live" to stringResource(R.string.nav_live), "subscriptions" to stringResource(R.string.nav_subscriptions),
                                    "channels" to stringResource(R.string.nav_channels), "recommendations" to stringResource(R.string.nav_recommendations),
                                    "instances" to stringResource(R.string.nav_instances), "history" to stringResource(R.string.nav_history),
                                    "favorites" to stringResource(R.string.nav_favorites)
                                )) { model.updateLaunchTab(it) }
                                Text(stringResource(R.string.settings_sections), style = MaterialTheme.typography.titleMedium)
                                listOf("home" to R.string.nav_home, "popular" to R.string.nav_popular, "recommendations" to R.string.nav_recommendations, "live" to R.string.nav_live, "subscriptions" to R.string.nav_subscriptions, "channels" to R.string.nav_channels, "instances" to R.string.nav_instances, "history" to R.string.nav_history, "favorites" to R.string.nav_favorites).forEach { (key, label) ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Checkbox(checked = key !in model.hiddenSections, enabled = key != "home" && key != model.launchTab, onCheckedChange = { model.toggleSection(key, it) })
                                    }
                                }
                                val exitLabel = stringResource(when (model.exitMode) { "single" -> R.string.exit_mode_single; "double" -> R.string.exit_mode_double; else -> R.string.exit_mode_none })
                                SettingsChoice(stringResource(R.string.exit_mode_title), exitLabel, listOf(
                                    "none" to stringResource(R.string.exit_mode_none), "double" to stringResource(R.string.exit_mode_double), "single" to stringResource(R.string.exit_mode_single)
                                )) { model.updateExitMode(it) }
                                val menuHoldLabel = "${String.format(Locale.ROOT, "%.1f", model.contextMenuHoldSeconds).removeSuffix(".0")} ${stringResource(R.string.seconds_short)}"
                                SettingsChoice(
                                    stringResource(R.string.context_menu_hold_duration),
                                    menuHoldLabel,
                                    (2..10).map { it / 2f }.map { seconds ->
                                        seconds.toString() to "${String.format(Locale.ROOT, "%.1f", seconds).removeSuffix(".0")} ${stringResource(R.string.seconds_short)}"
                                    }
                                ) { model.updateContextMenuHoldSeconds(it.toFloat()) }
                                val watchedThresholdLabel = stringResource(when (model.watchedRecommendationThreshold) {
                                    "percent_5" -> R.string.watched_threshold_5_percent
                                    "percent_10" -> R.string.watched_threshold_10_percent
                                    "percent_50" -> R.string.watched_threshold_50_percent
                                    "complete" -> R.string.watched_threshold_100_percent
                                    else -> R.string.watched_threshold_never
                                })
                                SettingsChoice(stringResource(R.string.hide_watched_recommendations), watchedThresholdLabel, listOf(
                                    "never" to stringResource(R.string.watched_threshold_never),
                                    "percent_5" to stringResource(R.string.watched_threshold_5_percent),
                                    "percent_10" to stringResource(R.string.watched_threshold_10_percent),
                                    "percent_50" to stringResource(R.string.watched_threshold_50_percent),
                                    "complete" to stringResource(R.string.watched_threshold_100_percent)
                                )) { model.updateWatchedRecommendationThreshold(it) }
                                val recommendationBasisLabel = stringResource(when (model.recommendationBasis) {
                                    "subscriptions" -> R.string.recommendations_basis_subscriptions
                                    "both" -> R.string.recommendations_basis_both
                                    else -> R.string.recommendations_basis_history
                                })
                                SettingsChoice(stringResource(R.string.recommendations_basis), recommendationBasisLabel, listOf(
                                    "history" to stringResource(R.string.recommendations_basis_history),
                                    "subscriptions" to stringResource(R.string.recommendations_basis_subscriptions),
                                    "both" to stringResource(R.string.recommendations_basis_both)
                                )) { model.updateRecommendationBasis(it) }
                                HorizontalDivider()
                                Button(onClick = onClearHistory, enabled = model.history.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_clear_history)) }
                                val periodLabel = stringResource(when (historyPeriodHours) { 2 -> R.string.history_period_2; 3 -> R.string.history_period_3; 6 -> R.string.history_period_6; 24 -> R.string.history_period_24; else -> R.string.history_period_1 })
                                SettingsChoice(stringResource(R.string.history_clear_period_title), periodLabel, listOf(
                                    "1" to stringResource(R.string.history_period_1), "2" to stringResource(R.string.history_period_2),
                                    "3" to stringResource(R.string.history_period_3), "6" to stringResource(R.string.history_period_6),
                                    "24" to stringResource(R.string.history_period_24)
                                )) { onHistoryPeriodChange(it.toInt()) }
                                OutlinedButton(onClick = onClearHistoryPeriod, enabled = model.history.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.history_clear_period_action)) }
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.use_built_in_tv_keyboard), Modifier.weight(1f))
                                    Switch(useBuiltInTvKeyboard, {
                                        useBuiltInTvKeyboard = it
                                        playerPrefs.edit().putBoolean("useBuiltInTvKeyboard", it).apply()
                                    })
                                }
                            }
                            "interface" -> {
                                val themeLabel = stringResource(when (model.selectedTheme) { PeerIcheThemes.LIGHT -> R.string.theme_light; PeerIcheThemes.PEERTUBE -> R.string.theme_peertube; PeerIcheThemes.BLUE -> R.string.theme_blue; else -> R.string.theme_graphite })
                                SettingsChoice(stringResource(R.string.color_theme), themeLabel, listOf(
                                    PeerIcheThemes.GRAPHITE to stringResource(R.string.theme_graphite),
                                    PeerIcheThemes.LIGHT to stringResource(R.string.theme_light),
                                    PeerIcheThemes.PEERTUBE to stringResource(R.string.theme_peertube),
                                    PeerIcheThemes.BLUE to stringResource(R.string.theme_blue),
                                )) { model.updateTheme(it) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.grid_mode), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(model.gridMode, { model.gridMode = it; model.save() })
                                }
                                val linesLabel = stringResource(when (model.videoTitleLines) { 1 -> R.string.video_title_lines_1; 3 -> R.string.video_title_lines_3; else -> R.string.video_title_lines_2 })
                                SettingsChoice(stringResource(R.string.video_title_lines), linesLabel, listOf("1" to stringResource(R.string.video_title_lines_1), "2" to stringResource(R.string.video_title_lines_2), "3" to stringResource(R.string.video_title_lines_3))) { model.updateVideoTitleLines(it.toInt()) }
                                val speedLabel = "${String.format(Locale.ROOT, "%.1f", model.tickerSpeed).removeSuffix(".0")}x"
                                SettingsChoice(stringResource(R.string.ticker_speed), speedLabel, (2..16).map { n -> val speed = n / 2f; "${speed}" to "${String.format(Locale.ROOT, "%.1f", speed).removeSuffix(".0")}x" }) { model.updateTickerSpeed(it.toFloat()) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.clock_24_hour), Modifier.weight(1f)); Switch(model.clock24Hour, model::updateClock24Hour) }
                                SettingsChoice(stringResource(R.string.ui_scale), "${String.format(Locale.ROOT, "%.2f", model.uiScale)}x", (85..140 step 5).map { it / 100f }.map { it.toString() to "${String.format(Locale.ROOT, "%.2f", it)}x" }) { model.updateUiScale(it.toFloat()) }
                                SettingsChoice(stringResource(R.string.grid_scale), "${String.format(Locale.ROOT, "%.2f", model.gridScale)}x", (50..150 step 5).map { it / 100f }.map { it.toString() to "${String.format(Locale.ROOT, "%.2f", it)}x" }) { model.updateGridScale(it.toFloat()) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.navigation_sounds), Modifier.weight(1f)); Switch(model.navigationSounds, model::updateNavigationSounds) }
                            }
                            "player" -> {
                                val seekLabel = "${model.seekStepSeconds} ${stringResource(R.string.seconds_short)}"
                                SettingsChoice(stringResource(R.string.player_seek_step), seekLabel, listOf(5, 10, 15, 30, 60).map { it.toString() to "$it ${stringResource(R.string.seconds_short)}" }) { model.updateSeekStep(it.toInt()) }
                                val bufferLabel = stringResource(when (model.bufferProfile) { "small" -> R.string.buffer_small; "large" -> R.string.buffer_large; "largest" -> R.string.buffer_largest; else -> R.string.buffer_medium })
                                SettingsChoice(stringResource(R.string.player_buffer_size), bufferLabel, listOf("small" to stringResource(R.string.buffer_small), "medium" to stringResource(R.string.buffer_medium), "large" to stringResource(R.string.buffer_large), "largest" to stringResource(R.string.buffer_largest))) { model.updateBufferProfile(it) }
                                val volumeLabel = "${model.playerVolume}%"
                                SettingsChoice(stringResource(R.string.player_volume), volumeLabel, (0..200 step 10).map { it.toString() to "$it%" }) { model.updatePlayerVolume(it.toInt()) }
                                val afterLabel = stringResource(when (model.afterVideoAction) { "repeat" -> R.string.after_video_repeat; "next" -> R.string.after_video_next; "next_channel" -> R.string.after_video_next_channel; else -> R.string.after_video_finish })
                                SettingsChoice(stringResource(R.string.after_video_action_title), afterLabel, listOf("finish" to stringResource(R.string.after_video_finish), "repeat" to stringResource(R.string.after_video_repeat), "next" to stringResource(R.string.after_video_next), "next_channel" to stringResource(R.string.after_video_next_channel))) { model.setAfterVideoAction(it) }
                                val durationLabel = stringResource(when (model.playerOverlayDurationSeconds) { 5 -> R.string.player_duration_5; 10 -> R.string.player_duration_10; else -> R.string.player_duration_3 })
                                SettingsChoice(stringResource(R.string.player_overlay_duration), durationLabel, listOf(3 to R.string.player_duration_3, 5 to R.string.player_duration_5, 10 to R.string.player_duration_10).map { it.first.toString() to stringResource(it.second) }) { model.updatePlayerOverlayDuration(it.toInt()) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.player_clock_show), Modifier.weight(1f)); Switch(model.showPlayerClock, model::updateShowPlayerClock) }
                                if (model.showPlayerClock) {
                                    SettingsChoice(stringResource(R.string.player_clock_position), stringResource(if (model.playerClockPosition == "top_left") R.string.player_clock_top_left else R.string.player_clock_top_right), listOf("top_left" to stringResource(R.string.player_clock_top_left), "top_right" to stringResource(R.string.player_clock_top_right))) { model.updatePlayerClockPosition(it) }
                                    SettingsChoice(stringResource(R.string.player_clock_size), stringResource(when (model.playerClockSize) { "small" -> R.string.player_clock_small; "large" -> R.string.player_clock_large; else -> R.string.player_clock_medium }), listOf("small" to stringResource(R.string.player_clock_small), "medium" to stringResource(R.string.player_clock_medium), "large" to stringResource(R.string.player_clock_large))) { model.updatePlayerClockSize(it) }
                                    SettingsChoice(stringResource(R.string.player_clock_backdrop), stringResource(when (model.playerClockBackdrop) { "none" -> R.string.player_clock_backdrop_none; "light" -> R.string.player_clock_backdrop_light; "strong" -> R.string.player_clock_backdrop_strong; else -> R.string.player_clock_backdrop_medium }), listOf("none" to stringResource(R.string.player_clock_backdrop_none), "light" to stringResource(R.string.player_clock_backdrop_light), "medium" to stringResource(R.string.player_clock_backdrop_medium), "strong" to stringResource(R.string.player_clock_backdrop_strong))) { model.updatePlayerClockBackdrop(it) }
                                }
                                SettingsChoice(stringResource(R.string.player_resume_title), stringResource(if (model.resumePlayback) R.string.player_resume_saved else R.string.player_resume_start), listOf("false" to stringResource(R.string.player_resume_start), "true" to stringResource(R.string.player_resume_saved))) { model.updateResumePlayback(it.toBoolean()) }
                            }
                            "search" -> {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.show_search_icon), Modifier.weight(1f))
                                    Switch(model.showSearchIcon, model::updateShowSearchIcon)
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.search_include_sensitive), Modifier.weight(1f))
                                    Switch(model.searchSettings.includeSensitive, model::updateIncludeSensitive)
                                }
                                SettingsChoice(
                                    stringResource(R.string.search_sensitive_display),
                                    stringResource(when (model.searchSettings.sensitiveDisplayMode) { "hide" -> R.string.search_sensitive_hide; "warning" -> R.string.search_sensitive_warning; "show" -> R.string.search_sensitive_show; else -> R.string.search_sensitive_blur }),
                                    listOf("hide" to stringResource(R.string.search_sensitive_hide), "blur" to stringResource(R.string.search_sensitive_blur), "warning" to stringResource(R.string.search_sensitive_warning), "show" to stringResource(R.string.search_sensitive_show))
                                ) { model.updateSensitiveDisplayMode(it) }
                                val sourceLabels = listOf(
                                    SearchSource.SEPIA to R.string.search_source_sepia,
                                    SearchSource.PEERSEEK to R.string.search_source_peerseek,
                                    SearchSource.CUSTOM to R.string.search_source_custom,
                                    SearchSource.FEDERATED to R.string.search_source_federated,
                                    SearchSource.DIRECT to R.string.search_source_direct,
                                    SearchSource.LOCAL to R.string.search_source_local
                                )
                                Text(stringResource(R.string.search_methods), style = MaterialTheme.typography.titleMedium)
                                model.searchSettings.order.forEachIndexed { index, source ->
                                    val label = sourceLabels.first { it.first == source }.second
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${index + 1}. ${stringResource(label)}", Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Switch(checked = source in model.searchSettings.enabled, onCheckedChange = { model.updateSearchSourceEnabled(source, it) })
                                        TextButton(onClick = { model.moveSearchSource(source, -1) }, enabled = index > 0) { Text("↑") }
                                        TextButton(onClick = { model.moveSearchSource(source, 1) }, enabled = index < model.searchSettings.order.lastIndex) { Text("↓") }
                                    }
                                }
                                SettingsChoice(stringResource(R.string.search_execution_mode), stringResource(when (model.searchSettings.mode) { "sequential" -> R.string.search_mode_sequential; "parallel" -> R.string.search_mode_parallel; else -> R.string.search_mode_hybrid }), listOf("sequential" to stringResource(R.string.search_mode_sequential), "parallel" to stringResource(R.string.search_mode_parallel), "hybrid" to stringResource(R.string.search_mode_hybrid))) { model.updateSearchExecutionMode(it) }
                                SettingsChoice(stringResource(R.string.search_sufficient_results), model.searchSettings.sufficientResults.toString(), (10..100 step 10).map { it.toString() to it.toString() }) { model.updateSearchSufficientResults(it.toInt()) }
                                Text(stringResource(R.string.search_custom_indexes_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(value = customIndexesInput, onValueChange = { customIndexesInput = it; model.updateCustomSearchIndexes(it) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, label = { Text(stringResource(R.string.search_source_custom)) })
                                val sortTitle = stringResource(when (model.videoSortMode) { "popular" -> R.string.video_sort_popular; "title" -> R.string.video_sort_title; else -> R.string.video_sort_newest })
                                SettingsChoice(stringResource(R.string.video_sort_mode), sortTitle, listOf("newest" to stringResource(R.string.video_sort_newest), "popular" to stringResource(R.string.video_sort_popular), "title" to stringResource(R.string.video_sort_title))) { model.updateVideoSortMode(it) }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Text(stringResource(R.string.discovery_settings_title), style = MaterialTheme.typography.titleLarge)
                                val discovery = model.discoverySettings()
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.discovery_automatic), Modifier.weight(1f)); Switch(discovery.automatic, { model.setDiscoveryOption("automatic", it) }) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.discovery_bundled), Modifier.weight(1f)); Switch(discovery.bundledEnabled, { model.setDiscoveryOption("bundled", it) }) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.discovery_join), Modifier.weight(1f)); Switch(discovery.joinPeerTubeEnabled, { model.setDiscoveryOption("join", it) }) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.discovery_federation), Modifier.weight(1f)); Switch(discovery.federationEnabled, { model.setDiscoveryOption("federation", it) }) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.discovery_json), Modifier.weight(1f)); Switch(discovery.remoteJsonEnabled, { model.setDiscoveryOption("json_enabled", it) }) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.discovery_wifi), Modifier.weight(1f)); Switch(discovery.wifiOnly, { model.setDiscoveryOption("wifi_only", it) }) }
                                SettingsChoice(stringResource(R.string.discovery_depth), discovery.maxDepth.toString(), (1..5).map { it.toString() to it.toString() }) { model.setDiscoveryOption("depth", it.toInt()) }
                                SettingsChoice(stringResource(R.string.discovery_limit), discovery.maxNewPerRun.toString(), listOf(10, 20, 30, 50, 100).map { it.toString() to it.toString() }) { model.setDiscoveryOption("max_new", it.toInt()) }
                                SettingsChoice(stringResource(R.string.discovery_refresh), "${discovery.refreshDays} d", listOf(1, 3, 7, 14, 30).map { it.toString() to "$it d" }) { model.setDiscoveryOption("refresh_days", it.toInt()) }
                                OutlinedTextField(value = remoteServerCatalogInput, onValueChange = { remoteServerCatalogInput = it; model.setDiscoveryOption("catalog_url", it) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.discovery_json_url)) })
                                OutlinedTextField(value = manualServerInput, onValueChange = { manualServerInput = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.server_host_hint)) })
                                Button(onClick = { model.addServerManually(manualServerInput); manualServerInput = "" }, enabled = manualServerInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_add_manual)) }
                                Button(onClick = { model.runServerDiscovery() }, enabled = !model.isDiscoveringServers, modifier = Modifier.fillMaxWidth()) { Text(if (model.isDiscoveringServers) stringResource(R.string.discovery_working) else stringResource(R.string.discovery_run)) }
                                Text(stringResource(R.string.discovery_found, model.discoveredServers.size), style = MaterialTheme.typography.bodySmall)
                                model.discoveryError?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { model.clearDiscoveredServers() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.discovery_clear)) }
                                    OutlinedButton(onClick = { serverImportLauncher.launch(arrayOf("application/json", "text/json", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.server_import)) }
                                    OutlinedButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, model.exportServerCatalog())
                                        settingsContext.startActivity(Intent.createChooser(send, settingsContext.getString(R.string.server_export)))
                                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.server_export)) }
                                }
                            }
                            "network" -> {
                                val proxyCheckScope = rememberCoroutineScope()
                                var proxyEnabled by remember(model) { mutableStateOf(model.networkProxy.enabled) }
                                var proxyType by remember(model) { mutableStateOf(model.networkProxy.type) }
                                var proxyHost by remember(model) { mutableStateOf(model.networkProxy.host) }
                                var proxyPort by remember(model) { mutableStateOf(model.networkProxy.port.toString()) }
                                var proxyUsername by remember(model) { mutableStateOf(model.networkProxy.username) }
                                var proxyPassword by remember(model) { mutableStateOf(model.networkProxy.password) }
                                var proxyCheckStatus by remember(model) { mutableStateOf<String?>(null) }
                                var proxyCheckPassed by remember(model) { mutableStateOf<Boolean?>(null) }
                                var proxyCheckRunning by remember(model) { mutableStateOf(false) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.proxy_enabled), Modifier.weight(1f))
                                    Switch(checked = proxyEnabled, onCheckedChange = { enabled ->
                                        proxyEnabled = enabled
                                        proxyCheckStatus = null
                                        proxyCheckPassed = null
                                        // The enable switch is an immediate setting; don't leave
                                        // the active OkHttp clients on the previous proxy until
                                        // the separate connection-check/apply button is pressed.
                                        model.updateNetworkProxy(NetworkProxySettings(
                                            enabled = enabled,
                                            type = proxyType,
                                            host = proxyHost.trim(),
                                            port = proxyPort.toIntOrNull() ?: model.networkProxy.port,
                                            username = proxyUsername,
                                            password = proxyPassword
                                        ))
                                    })
                                }
                                SettingsChoice(stringResource(R.string.proxy_type), stringResource(if (proxyType == "socks5") R.string.proxy_socks5 else R.string.proxy_http), listOf(
                                    "http" to stringResource(R.string.proxy_http), "socks5" to stringResource(R.string.proxy_socks5)
                                )) { proxyType = it }
                                OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.proxy_host)) })
                                OutlinedTextField(value = proxyPort, onValueChange = { proxyPort = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.proxy_port)) })
                                OutlinedTextField(value = proxyUsername, onValueChange = { proxyUsername = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.proxy_username)) })
                                OutlinedTextField(value = proxyPassword, onValueChange = { proxyPassword = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.proxy_password)) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                                Button(onClick = {
                                    val port = proxyPort.toIntOrNull() ?: 0
                                    val settings = NetworkProxySettings(proxyEnabled, proxyType, proxyHost.trim(), port, proxyUsername, proxyPassword)
                                    model.updateNetworkProxy(settings)
                                    proxyCheckRunning = true
                                    proxyCheckPassed = null
                                    proxyCheckStatus = settingsContext.getString(R.string.proxy_checking)
                                    proxyCheckScope.launch {
                                        val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            runCatching {
                                                ApiFactory.safeHttpGet("https://peervideo.ru/api/v1/config").use { response ->
                                                    val message = when {
                                                        response.code == 407 -> settingsContext.getString(R.string.proxy_check_auth_failed)
                                                        settings.enabled && response.isSuccessful -> settingsContext.getString(R.string.proxy_check_success, response.code)
                                                        !settings.enabled && response.isSuccessful -> settingsContext.getString(R.string.proxy_check_direct_success, response.code)
                                                        else -> settingsContext.getString(R.string.proxy_check_response, response.code)
                                                    }
                                                    message to (response.code in 200..299)
                                                }
                                            }.getOrElse { error ->
                                                settingsContext.getString(R.string.proxy_check_failed, error.localizedMessage ?: error.javaClass.simpleName) to false
                                            }
                                        }
                                        proxyCheckStatus = result.first
                                        proxyCheckPassed = result.second
                                        proxyCheckRunning = false
                                    }
                                }, enabled = !proxyCheckRunning && (!proxyEnabled || (proxyHost.isNotBlank() && proxyPort.toIntOrNull() in 1..65535)), modifier = Modifier.fillMaxWidth()) {
                                    Text(if (proxyCheckRunning) stringResource(R.string.proxy_checking) else stringResource(R.string.proxy_apply))
                                }
                                proxyCheckStatus?.let { status ->
                                    Text(status, style = MaterialTheme.typography.bodySmall, color = when (proxyCheckPassed) {
                                        true -> MaterialTheme.colorScheme.primary
                                        false -> MaterialTheme.colorScheme.error
                                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    })
                                }
                                Text(stringResource(R.string.proxy_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            "backup" -> {
                                Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_create)) }
                                OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_restore)) }
                            }
                            "about" -> {
                                Box(Modifier.fillMaxWidth().heightIn(min = 620.dp)) {
                                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Image(painterResource(LocalPeerIcheTheme.current.logoResource), contentDescription = stringResource(R.string.app_name), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 10.dp))
                                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                                        Text(stringResource(R.string.about_version_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = onCheckForUpdates, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_check_action)) }
                                    }
                                    Text(stringResource(R.string.about_artic_fox), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsChoice(title: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    val choiceFocus = remember { FocusRequester() }
    val firstOptionFocus = remember { FocusRequester() }
    LaunchedEffect(expanded, restoreFocus) {
        if (expanded) {
            runCatching { firstOptionFocus.requestFocus() }
        } else if (restoreFocus) {
            withFrameNanos { }
            runCatching { choiceFocus.requestFocus() }
            restoreFocus = false
        }
    }
    Text(title, style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().focusRequester(choiceFocus)) {
            Text(selected, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false; restoreFocus = true },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.32f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.forEachIndexed { index, (value, label) ->
                                val optionModifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(firstOptionFocus) else Modifier)
                                if (value == selected) {
                                    Button(onClick = { expanded = false; restoreFocus = true; onSelect(value) }, modifier = optionModifier) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { expanded = false; restoreFocus = true; onSelect(value) }, modifier = optionModifier) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun VideoListRow(video: Video, server: String, progress: Float, favorite: Boolean, subscribed: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit, onSubscription: () -> Unit, onChannel: () -> Unit, setContextMenuTarget: (String, (() -> Unit)?, (() -> Unit)?, Boolean) -> Unit, holdSeconds: Float, statusLabel: String? = null, onExcludeRecommendation: () -> Unit = {}, onMarkWatched: () -> Unit = {}, firstCardFocusRequester: FocusRequester? = null, upFocusRequester: FocusRequester? = null, preferredFocusRequester: FocusRequester? = null, preferredFocusVideoId: String? = null, isAtLeftBoundary: Boolean = true) {
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
@Composable private fun VideoGridCard(video: Video, server: String, progress: Float, favorite: Boolean, subscribed: Boolean, titleLines: Int, tickerSpeed: Float, onOpen: () -> Unit, onFavorite: () -> Unit, onSubscription: () -> Unit, onChannel: (String) -> Unit, setContextMenuTarget: (String, (() -> Unit)?, (() -> Unit)?, Boolean) -> Unit, holdSeconds: Float, onExcludeRecommendation: () -> Unit = {}, onMarkWatched: () -> Unit = {}, firstCardFocusRequester: FocusRequester? = null, upFocusRequester: FocusRequester? = null, preferredFocusRequester: FocusRequester? = null, preferredFocusVideoId: String? = null, isAtLeftBoundary: Boolean = false) {
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
private fun VideoContextMenu(
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

@Composable private fun VideoThumbnail(video: Video, server: String, modifier: Modifier = Modifier, progress: Float = 0f) {
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
    val formatted = remember(publishedAt, Locale.getDefault()) { formatPublishedDate(publishedAt) }
    if (formatted != null) Text(stringResource(R.string.video_published, formatted), style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
}

internal fun formatPublishedDate(value: String?): String? {
    val datePart = value?.take(10)?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) } ?: return null
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(parser.parse(datePart)!!)
    }.getOrNull()
}

@Composable private fun SearchGlyph(glyphSize: androidx.compose.ui.unit.Dp = 26.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val canvasSize = this.size
        drawCircle(color = Color.White, radius = canvasSize.minDimension * 0.29f, center = Offset(canvasSize.width * 0.41f, canvasSize.height * 0.41f), style = Stroke(width = 2.5.dp.toPx()))
        drawLine(color = Color.White, start = Offset(canvasSize.width * 0.62f, canvasSize.height * 0.62f), end = Offset(canvasSize.width * 0.91f, canvasSize.height * 0.91f), strokeWidth = 3.dp.toPx())
    }
}

@Composable private fun LocalDateTime(is24Hour: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    Text(java.text.SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(java.util.Date(now)), style = MaterialTheme.typography.headlineSmall)
    Text(java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(java.util.Date(now)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun SearchFilter(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
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

@Composable
private fun ObserveScrollDirection(
    stateKey: Any,
    position: () -> Long,
    isScrolling: () -> Boolean,
    onDirectionChanged: (Boolean) -> Unit
) {
    val currentCallback by rememberUpdatedState(onDirectionChanged)
    LaunchedEffect(stateKey) {
        var previousPosition: Long? = null
        var lastDirectionWasDown: Boolean? = null
        snapshotFlow { position() to isScrolling() }.distinctUntilChanged().collect { (currentPosition, scrolling) ->
            previousPosition?.let { previous ->
                if (scrolling && currentPosition > previous) {
                    lastDirectionWasDown = true
                    currentCallback(true)
                } else if (scrolling && currentPosition < previous) {
                    lastDirectionWasDown = false
                    if (currentPosition <= 0L) currentCallback(false)
                } else if (!scrolling && currentPosition <= 0L && lastDirectionWasDown == false) {
                    currentCallback(false)
                }
            }
            previousPosition = currentPosition
        }
    }
}

@Composable private fun VideoShelf(
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
    onMarkWatched: (Video) -> Unit = {}
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
                                if (it.isFocused) setContextMenuTarget(video.uuid, showContextMenu, { onOpen(video) }, index == 0)
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
