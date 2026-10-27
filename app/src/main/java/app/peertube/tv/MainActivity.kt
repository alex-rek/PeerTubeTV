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
import androidx.compose.animation.core.tween
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.Locale
import java.text.DateFormat
import java.text.SimpleDateFormat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private val supportedUiLanguages = listOf("en", "ru", "es", "fr", "de", "pt", "it", "zh-CN", "ja", "hi", "ar")

class MainActivity : AppCompatActivity() {
    private var availableUpdate by mutableStateOf<GitHubRelease?>(null)
    private var updateMessage by mutableStateOf<String?>(null)
    private var updateBusy by mutableStateOf(false)
    private var pendingUpdateApk: java.io.File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val colors = darkColorScheme(
            primary = Color(0xFF8AB4F8),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF0B0F14),
            surface = Color(0xFF141A22),
            surfaceVariant = Color(0xFF202833)
        )
        setContent {
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Home()
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
        lifecycleScope.launch {
            availableUpdate = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    GitHubUpdates.latestRelease(BuildConfig.VERSION_NAME)
                }.getOrNull()
            }
        }
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
                runCatching { GitHubUpdates.downloadApk(this@MainActivity, release.apkUrl) }
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
    var isSearching by mutableStateOf(false)
    var recommendations by mutableStateOf<List<Video>>(emptyList())
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
    val watchedVideoIds = mutableStateListOf<String>()
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
    var selectedTheme by mutableStateOf("peertube")
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
    var sortServersByAvailability by mutableStateOf(false)
        private set
    var checkingServerHosts by mutableStateOf<Set<String>>(emptySet())
        private set
    var isCheckingServerAvailability by mutableStateOf(false)
        private set
    var serverAvailabilityCheckSummary by mutableStateOf<String?>(null)
        private set
    val sortedDiscoveredServers: List<DiscoveredServer>
        get() = if (!sortServersByAvailability) discoveredServers else discoveredServers.withIndex()
            .sortedWith(compareBy<IndexedValue<DiscoveredServer>> { indexed ->
                val server = indexed.value
                val fresh = server.apiCheckedAt > 0L && System.currentTimeMillis() - server.apiCheckedAt <= 24L * 60L * 60L * 1000L
                when { fresh && server.apiStatus == "available" -> 0; fresh && server.apiStatus == "unavailable" -> 2; else -> 1 }
            }.thenBy { it.index }).map { it.value }
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
    val subscriptions = mutableStateListOf<VideoSubscription>()
    private var prefs: android.content.SharedPreferences? = null
    private var appContext: android.content.Context? = null
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("peertube", 0)
        networkProxy = NetworkProxySettings(
            enabled = prefs?.getBoolean("proxyEnabled", false) ?: false,
            type = prefs?.getString("proxyType", "http") ?: "http",
            host = prefs?.getString("proxyHost", "") ?: "",
            port = prefs?.getInt("proxyPort", 8080) ?: 8080,
            username = prefs?.getString("proxyUsername", "") ?: "",
            password = prefs?.getString("proxyPassword", "") ?: ""
        )
        ApiFactory.configureProxy(networkProxy, context.applicationContext)
        searchSettingsRepository = SearchSettingsRepository(context)
        searchSettings = searchSettingsRepository!!.read()
        serverRepository = ServerRepository(context).also { it.seedBundled(context) }
        serverDiscoveryManager = ServerDiscoveryManager(context, serverRepository!!)
        serverDiscoveryManager!!.schedulePeriodic()
        discoveredServers = serverRepository!!.all()
        sortServersByAvailability = prefs?.getBoolean("serverAvailabilitySort", false) ?: false
        val discovery = serverDiscoveryManager!!.settings()
        val lastDiscovery = prefs?.getLong("lastServerDiscoveryAt", 0L) ?: 0L
        if (discovery.automatic && System.currentTimeMillis() - lastDiscovery >= discovery.refreshDays * 24L * 60L * 60L * 1000L) runServerDiscovery(manual = false)
        history.clear()
        favorites.clear()
        subscriptions.clear()
        historyViewedAt.clear()
        server = prefs?.getString("server", server) ?: server
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
        watchedRecommendationThreshold = prefs?.getString("watchedRecommendationThreshold", if (prefs?.getBoolean("hideWatchedRecommendations", true) == true) "complete" else "never")
            ?.takeIf { it in setOf("never", "minute", "three_minutes", "half", "complete") } ?: "never"
        recommendationBasis = prefs?.getString("recommendationBasis", "history")?.takeIf { it in setOf("history", "subscriptions", "both") } ?: "history"
        watchedVideoIds.clear()
        runCatching { watchedVideoIds.addAll(Gson().fromJson<List<String>>(prefs?.getString("watchedVideoIds", "[]"), object : TypeToken<List<String>>() {}.type) ?: emptyList()) }
        excludedRecommendationIds.clear()
        runCatching { excludedRecommendationIds.addAll(Gson().fromJson<List<String>>(prefs?.getString("excludedRecommendationIds", "[]"), object : TypeToken<List<String>>() {}.type) ?: emptyList()) }
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
        hiddenSections = runCatching { Gson().fromJson<List<String>>(prefs?.getString("hiddenSections", "[]"), object : TypeToken<List<String>>() {}.type)?.toSet() ?: emptySet() }.getOrDefault(emptySet())
        selectedTheme = prefs?.getString("selectedTheme", "peertube")?.takeIf { it in listOf("peertube", "turquoise", "gray", "red") } ?: "peertube"
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
        runCatching { history.addAll(Gson().fromJson<List<Video>>(prefs?.getString("history", "[]"), type) ?: emptyList()) }
        val historyTimeType = object : TypeToken<Map<String, Long>>() {}.type
        runCatching { historyViewedAt.putAll(Gson().fromJson<Map<String, Long>>(prefs?.getString("historyViewedAt", "{}"), historyTimeType) ?: emptyMap()) }
        history.forEach { if (it.uuid !in historyViewedAt) historyViewedAt[it.uuid] = System.currentTimeMillis() }
        runCatching { favorites.addAll(Gson().fromJson<List<Video>>(prefs?.getString("favorites", "[]"), type) ?: emptyList()) }
        val subscriptionType = object : TypeToken<List<VideoSubscription>>() {}.type
        runCatching {
            subscriptions.addAll(Gson().fromJson<List<VideoSubscription>>(prefs?.getString("subscriptions", "[]"), subscriptionType)
                .orEmpty().mapNotNull { it?.let(::normalizeVideoSubscription) })
        }
    }
    fun save() { prefs?.edit()?.putString("server", server)?.putBoolean("gridMode", gridMode)?.putString("afterVideoAction", afterVideoAction)?.putInt("playerOverlayDurationSeconds", playerOverlayDurationSeconds)?.putBoolean("showPlayerClock", showPlayerClock)?.putString("playerClockPosition", playerClockPosition)?.putString("playerClockSize", playerClockSize)?.putString("playerClockBackdrop", playerClockBackdrop)?.putBoolean("resumePlayback", resumePlayback)?.putString("watchedRecommendationThreshold", watchedRecommendationThreshold)?.putString("recommendationBasis", recommendationBasis)?.putString("watchedVideoIds", Gson().toJson(watchedVideoIds.toList()))?.putString("excludedRecommendationIds", Gson().toJson(excludedRecommendationIds.toList()))?.putBoolean("peerSeekEnabled", peerSeekEnabled)?.putString("videoSortMode", videoSortMode)?.putBoolean("navigationSounds", navigationSounds)?.putString("exitMode", exitMode)?.putInt("videoTitleLines", videoTitleLines)?.putFloat("tickerSpeed", tickerSpeed)?.putFloat("contextMenuHoldSeconds", contextMenuHoldSeconds)?.putInt("seekStepSeconds", seekStepSeconds)?.putString("launchTab", launchTab)?.putString("hiddenSections", Gson().toJson(hiddenSections))?.putString("selectedTheme", selectedTheme)?.putBoolean("showSearchIcon", showSearchIcon)?.putFloat("uiScale", uiScale)?.putFloat("gridScale", gridScale)?.putBoolean("clock24Hour", clock24Hour)?.putString("searchCountryCode", searchCountryCode)?.putBoolean("serverAvailabilitySort", sortServersByAvailability)?.putString("bufferProfile", bufferProfile)?.putInt("playerVolume", playerVolume)?.putString("history", Gson().toJson(history))?.putString("favorites", Gson().toJson(favorites))?.putString("subscriptions", Gson().toJson(subscriptions))?.apply() }
    fun updateRecommendationBasis(basis: String) {
        recommendationBasis = basis.takeIf { it in setOf("history", "subscriptions", "both") } ?: "history"
        save()
        loadRecommendations()
    }
    fun updateNetworkProxy(settings: NetworkProxySettings) {
        if (settings.enabled && (settings.host.isBlank() || settings.port !in 1..65535)) return
        networkProxy = settings.copy(host = settings.host.trim())
        ApiFactory.configureProxy(networkProxy, appContext)
        prefs?.edit()?.putBoolean("proxyEnabled", networkProxy.enabled)?.putString("proxyType", networkProxy.type)
            ?.putString("proxyHost", networkProxy.host)?.putInt("proxyPort", networkProxy.port)
            ?.putString("proxyUsername", networkProxy.username)?.putString("proxyPassword", networkProxy.password)?.apply()
    }
    fun excludeFromRecommendations(video: Video) {
        if (video.uuid !in excludedRecommendationIds) excludedRecommendationIds.add(video.uuid)
        recommendations = recommendations.filterNot { it.uuid == video.uuid }
        publishAndroidTvRecommendations(recommendations)
        save()
    }
    fun markRecommendationWatched(video: Video) {
        if (video.uuid !in watchedVideoIds) watchedVideoIds.add(video.uuid)
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
        watchedRecommendationThreshold = threshold.takeIf { it in setOf("never", "minute", "three_minutes", "half", "complete") } ?: "never"
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
    fun updateTheme(theme: String) { selectedTheme = theme.takeIf { it in listOf("peertube", "turquoise", "gray", "red") } ?: "peertube"; save() }
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
    fun toggleServerAvailabilitySort() {
        sortServersByAvailability = !sortServersByAvailability
        prefs?.edit()?.putBoolean("serverAvailabilitySort", sortServersByAvailability)?.apply()
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
                        item.host to ServerAvailabilityManager().checkApi(item.host)
                    }
                } }.awaitAll()
                checks.forEach { (host, check) -> serverRepository?.recordAvailability(host, api = check) }
                discoveredServers = serverRepository?.all().orEmpty()
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

    fun colorScheme(): androidx.compose.material3.ColorScheme = when (selectedTheme) {
        "turquoise" -> darkColorScheme(primary = Color(0xFF4DD0C5), secondary = Color(0xFF80CBC4), background = Color(0xFF071719), surface = Color(0xFF102326), surfaceVariant = Color(0xFF18383B))
        "gray" -> darkColorScheme(primary = Color(0xFFD0D5DA), secondary = Color(0xFFAAB3BB), background = Color(0xFF0B0F14), surface = Color(0xFF171A21), surfaceVariant = Color(0xFF252A32))
        "red" -> darkColorScheme(primary = Color(0xFFFF6B65), secondary = Color(0xFFFFA198), background = Color(0xFF170B0B), surface = Color(0xFF261414), surfaceVariant = Color(0xFF3A2020))
        else -> darkColorScheme(primary = Color(0xFFF36C21), secondary = Color(0xFFFFA05E), background = Color(0xFF0B0F14), surface = Color(0xFF171A21), surfaceVariant = Color(0xFF27231F))
    }
    fun syncWatchProgress() {
        val type = object : TypeToken<Map<String, Long>>() {}.type
        val stored = runCatching { Gson().fromJson<Map<String, Long>>(prefs?.getString("watchProgressMs", "{}"), type) ?: emptyMap() }.getOrDefault(emptyMap())
        watchProgressMs.clear()
        watchProgressMs.putAll(stored)
        val savedWatched = runCatching { Gson().fromJson<List<String>>(prefs?.getString("watchedVideoIds", "[]"), object : TypeToken<List<String>>() {}.type) ?: emptyList() }.getOrDefault(emptyList())
        watchedVideoIds.clear()
        watchedVideoIds.addAll(savedWatched)
        if (watchedRecommendationThreshold != "never") recommendations = recommendations.filterNot { it.uuid in watchedVideoIds }
    }
    fun watchProgressFraction(video: Video): Float {
        if (video.duration <= 0) return 0f
        return ((watchProgressMs[video.uuid] ?: 0L).toFloat() / (video.duration * 1000f)).coerceIn(0f, 1f)
    }
    fun clearHistory() {
        history.clear()
        historyViewedAt.clear()
        recommendations = emptyList()
        save()
        prefs?.edit()?.putString("historyViewedAt", "{}")?.apply()
    }
    fun markVideoViewed(uuid: String) {
        historyViewedAt[uuid] = System.currentTimeMillis()
        prefs?.edit()?.putString("historyViewedAt", Gson().toJson(historyViewedAt))?.apply()
    }
    fun clearHistoryOlderThan(hours: Int) {
        val cutoff = System.currentTimeMillis() - hours * 60L * 60L * 1000L
        val removed = history.filter { (historyViewedAt[it.uuid] ?: 0L) >= cutoff }.map { it.uuid }.toSet()
        history.removeAll { it.uuid in removed }
        removed.forEach(historyViewedAt::remove)
        recommendations = recommendations.filterNot { it.uuid in removed }
        save()
        prefs?.edit()?.putString("historyViewedAt", Gson().toJson(historyViewedAt))?.apply()
    }
    fun syncSubscriptions() {
        val subscriptionType = object : TypeToken<List<VideoSubscription>>() {}.type
        val stored = runCatching { Gson().fromJson<List<VideoSubscription>>(prefs?.getString("subscriptions", "[]"), subscriptionType)
            .orEmpty().mapNotNull { it?.let(::normalizeVideoSubscription) } }.getOrDefault(emptyList())
        subscriptions.clear()
        subscriptions.addAll(stored)
    }
    fun syncFavorites() {
        val videoType = object : TypeToken<List<Video>>() {}.type
        val stored = runCatching { Gson().fromJson<List<Video>>(prefs?.getString("favorites", "[]"), videoType) ?: emptyList() }.getOrDefault(emptyList())
        favorites.clear()
        favorites.addAll(stored)
    }
    fun toggleSubscription(video: Video) {
        val channel = video.channel
        val name = channel?.name?.takeIf(String::isNotBlank) ?: video.account?.name?.takeIf(String::isNotBlank) ?: return
        val host = channel?.host?.takeIf(String::isNotBlank) ?: video.account?.host?.takeIf(String::isNotBlank) ?: video.host?.takeIf(String::isNotBlank) ?: video.sourceServer(server).removePrefix("https://").removePrefix("http://")
        val identity = "$name@$host".lowercase()
        val existing = subscriptions.firstOrNull { "${it.name}@${it.host}".lowercase() == identity }
        if (existing != null) subscriptions.remove(existing)
        else subscriptions.add(VideoSubscription(name, host, channel?.displayName ?: video.account?.displayName ?: name))
        save()
    }
    fun isSubscribed(video: Video): Boolean {
        val name = video.channel?.name ?: video.account?.name ?: return false
        val host = video.channel?.host ?: video.account?.host ?: video.host ?: video.sourceServer(server).removePrefix("https://").removePrefix("http://")
        return subscriptions.any { "${it.name}@${it.host}".equals("$name@$host", ignoreCase = true) }
    }
    fun loadSubscriptions() {
        viewModelScope.launch {
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
    }
    private fun message(id: Int, detail: String): String = appContext?.getString(id, detail) ?: detail
    private fun contentLanguage(): String? = (if (uiLanguage == "auto") java.util.Locale.getDefault().language else uiLanguage).substringBefore('-').takeIf { it.isNotBlank() && it != "auto" }
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
                // Sepia's isLive filter also includes live-video entries that are waiting to start
                // or have ended. Resolve entries without a playable URL from their origin server.
                liveVideos = results.chunked(8).flatMap { batch ->
                    batch.map { candidate -> async {
                        if (candidate.state?.id in listOf(4, 5) || candidate.playableUrl() != null) return@async candidate
                        runCatching {
                            ApiFactory.create(candidate.sourceServer(server)).video(candidate.uuid)
                        }.getOrNull() ?: candidate
                    } }.awaitAll().filterNotNull()
                }.distinctBy { it.uuid }
                val cutoff = java.time.Instant.now().minusSeconds(24 * 60 * 60)
                // Browse recent non-live videos too: archived streams no longer match isLive=true.
                val recentPages = (0..2).map { page -> async {
                    runCatching {
                        searchApi.videos(search = null, start = page * 100, count = 100, sort = "-publishedAt", isLive = false).data
                    }.getOrDefault(emptyList())
                } }.awaitAll()
                val recentCandidates = recentPages.flatten().distinctBy { it.uuid }.filter { video ->
                    val published = video.publishedAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
                    published != null && !published.isBefore(cutoff) && !published.isAfter(java.time.Instant.now())
                }.take(60)
                recentlyEndedLiveVideos = recentCandidates.chunked(6).flatMap { batch ->
                    batch.map { video -> async {
                        runCatching {
                            val origin = ApiFactory.create(video.sourceServer(server))
                            val endedAt = origin.liveSession(video.uuid).endDate?.let { java.time.Instant.parse(it) }
                            if (endedAt != null && !endedAt.isBefore(cutoff) && !endedAt.isAfter(java.time.Instant.now())) video else null
                        }.getOrNull()
                    } }.awaitAll().filterNotNull()
                }.distinctBy { it.uuid }
                val endedIds = recentlyEndedLiveVideos.mapTo(hashSetOf()) { it.uuid }
                liveVideos = results.filterNot { it.uuid in endedIds }
            } catch (e: Exception) {
                error = message(R.string.error_live, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty())
            } finally {
                isLoadingLive = false
            }
        }
    }
    fun searchSepia(search: String, sort: String = "-match", category: Int? = null, durationMin: Int? = null, durationMax: Int? = null, liveOnly: Boolean = false, page: Int = 0, append: Boolean = false) {
        val requestId = ++sepiaSearchRequestId
        sepiaSearchJob?.cancel()
        sepiaSearchJob = viewModelScope.launch {
            isSearching = true
            if (!append) { searchSourceErrors = emptyMap(); searchSourcesByVideo = emptyMap(); searchHasMore = false; availabilityByVideo = emptyMap() }
            try {
                error = null
                val savedCache = runCatching { Gson().fromJson<List<Video>>(prefs?.getString("lastSearchVideos", "[]"), object : TypeToken<List<Video>>() {}.type) ?: emptyList() }.getOrDefault(emptyList())
                val providers = linkedMapOf<SearchSource, suspend (SearchRequest) -> SearchBatch>()
                providers[SearchSource.SEPIA] = { req ->
                    val result = ApiFactory.sepiaSearch().videos(
                        search = countrySearchTerm(req.query), start = req.page * req.pageSize, count = req.pageSize, sort = req.sort.takeUnless { it == "availability" } ?: "-match",
                        categoryOneOf = req.category, durationMin = req.durationMin, durationMax = req.durationMax,
                        isLive = if (req.liveOnly) true else null, languageOneOf = req.language
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
                    val result = ApiFactory.create("https://$host").searchVideos(req.query, req.page * req.pageSize, req.pageSize, req.sort.takeUnless { it == "availability" } ?: "-match")
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
                        runCatching { ApiFactory.create("https://$host").searchVideos(req.query, req.page * req.pageSize, req.pageSize, req.sort.takeUnless { it == "availability" } ?: "-match").data }
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
                val req = SearchRequest(search, page, 40, sort, category, durationMin, durationMax, liveOnly, contentLanguage(), searchCountryCode, server)
                val outcome = SearchAggregator(providers).search(req, searchSettings)
                var result = outcome.videos
                result = result.filter { video ->
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
                    if (outcome.errors.isEmpty() || result.isNotEmpty()) prefs?.edit()?.putString("lastSearchQuery", search)?.putString("lastSearchVideos", Gson().toJson(videos.take(100)))?.apply()
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
                    runCatching { searchApi.videos(search = countrySearchTerm(term), count = 20, languageOneOf = contentLanguage()).data }
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
    val name = subscription.name?.trim()?.takeIf(String::isNotBlank) ?: return null
    val host = subscription.host?.trim()?.removePrefix("https://")?.removePrefix("http://")
        ?.substringBefore('/')?.trimEnd('.')?.takeIf { it.matches(Regex("(?i)[a-z0-9.-]{1,253}")) } ?: return null
    return subscription.copy(name = name, host = host, displayName = subscription.displayName?.takeIf(String::isNotBlank) ?: name)
}

private fun isTelevisionDevice(context: android.content.Context): Boolean =
    context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Home(m: HomeModel = viewModel()) {
    val density = LocalDensity.current
    MaterialTheme(colorScheme = m.colorScheme()) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density * m.uiScale, density.fontScale)) {
            HomeContent(m)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun HomeContent(m: HomeModel) {
    val context = LocalContext.current
    val navigationTone = remember { android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 35) }
    var lastNavigationToneAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(navigationTone) { onDispose { navigationTone.release() } }
    val isTvDevice = remember { isTelevisionDevice(context) }
    val tvProviderPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            m.load()
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
    val instanceCheckFocusRequester = remember { FocusRequester() }
    var menuExpanded by remember { mutableStateOf(false) }
    var settingsOverlay by remember { mutableStateOf(false) }
    var focusedVideoMenu by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var clearHistoryDialog by remember { mutableStateOf(false) }
    var clearHistoryPeriodDialog by remember { mutableStateOf(false) }
    var settingsCategory by remember { mutableStateOf<String?>(null) }
    var settingsSubpage by remember { mutableStateOf<String?>(null) }
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
    val updateFocusedVideoMenu: (String, (() -> Unit)?, (() -> Unit)?) -> Unit = { videoId, openMenu, _ ->
        if (openMenu != null) {
            focusedVideoMenu = videoId to openMenu
        } else if (focusedVideoMenu?.first == videoId) {
            focusedVideoMenu = null
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                restoreSettingsBackup(context, uri)
                m.init(context)
                m.syncWatchProgress()
                when (m.tab) {
                    "popular" -> m.loadPopular()
                    "instances" -> m.loadInstances()
                    "recommendations" -> m.loadRecommendations()
                    "subscriptions" -> m.loadSubscriptions()
                    "channels" -> m.syncSubscriptions()
                    "home" -> m.load()
                }
            }.onFailure { m.error = it.localizedMessage ?: context.getString(R.string.error_request) }
        }
    }
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner, m) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                m.syncWatchProgress()
                m.syncFavorites()
                m.syncSubscriptions()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(menuExpanded) { if (menuExpanded) menuFocusRequester.requestFocus() }
    LaunchedEffect(m.tab) { if (m.tab == "instances") runCatching { instanceCheckFocusRequester.requestFocus() } }
    BackHandler {
        if (clearHistoryDialog) clearHistoryDialog = false
        else if (clearHistoryPeriodDialog) clearHistoryPeriodDialog = false
        else if (settingsOverlay && settingsSubpage != null) settingsSubpage = null
        else if (settingsOverlay && settingsCategory != null) { settingsOverlay = false; settingsCategory = null }
        else if (settingsOverlay) settingsOverlay = false
        else if (menuExpanded) menuExpanded = false
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
        m.tab = m.launchTab
        m.syncWatchProgress()
        when (m.tab) {
            "popular" -> m.loadPopular()
            "instances" -> m.loadInstances()
            "recommendations" -> m.loadRecommendations()
            "subscriptions" -> m.loadSubscriptions()
            "channels" -> m.syncSubscriptions()
            "home" -> m.load()
        }
        m.loadRecommendations()
    }
    LaunchedEffect(m.tab) {
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
            .putExtra("playbackQueue", Gson().toJson(playbackQueue))
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
        if (m.favorites.any { it.uuid == video.uuid }) m.favorites.removeAll { it.uuid == video.uuid } else m.favorites.add(video)
        m.save()
    }
    val openChannelVideos: (Video) -> Unit = { video ->
        val channelName = video.channel?.name ?: video.account?.name
        val host = video.channel?.host ?: video.account?.host ?: video.host ?: video.sourceServer(m.server).removePrefix("https://").removePrefix("http://")
        if (!channelName.isNullOrBlank()) m.loadChannelVideos(channelName, host)
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
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && !menuExpanded -> {
                    if (focusManager.moveFocus(FocusDirection.Left)) true else { menuExpanded = true; true }
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
                modifier = Modifier.width(230.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(0.dp)
            ) {
              Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(64.dp))
                listOf("home" to R.string.nav_home, "popular" to R.string.nav_popular, "recommendations" to R.string.nav_recommendations, "live" to R.string.nav_live, "subscriptions" to R.string.nav_subscriptions, "channels" to R.string.nav_channels, "instances" to R.string.nav_instances, "history" to R.string.nav_history, "favorites" to R.string.nav_favorites, "settings" to R.string.nav_settings).filter { (key, _) -> key == "home" || key == "settings" || key !in m.hiddenSections }.forEach { (key, label) ->
                    val isSelected = if (key == "settings") (m.tab == "settings" || settingsOverlay) else m.tab == key
                    val selectedStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = if (isSelected) 20.sp else 16.sp, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
                    var navFocused by remember(key) { mutableStateOf(false) }
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
                    NavigationDrawerItem(label = { Text(stringResource(label), style = selectedStyle, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = if (navFocused) Modifier.basicMarquee(velocity = 30.dp * m.tickerSpeed) else Modifier) }, selected = isSelected, icon = { Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }, shape = RectangleShape, colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color.Transparent, unselectedContainerColor = Color.Transparent, selectedTextColor = Color.White, selectedIconColor = MaterialTheme.colorScheme.primary), onClick = {
                        menuExpanded = false
                        if (key == "settings") {
                            settingsCategory = null
                            settingsSubpage = null
                            settingsOverlay = false
                            m.tab = "settings"
                        } else {
                            settingsOverlay = false
                            m.tab = key
                            when (key) { "popular" -> if (m.popularVideosServer != m.server || m.popularVideos.isEmpty()) m.loadPopular(); "home" -> m.load(); "instances" -> m.loadInstances(); "recommendations" -> m.loadRecommendations(); "subscriptions" -> m.loadSubscriptions(); "channels" -> m.syncSubscriptions() }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).scale(if (navFocused) 1.08f else 1f).onFocusChanged { navFocused = it.isFocused }.then(if (key == "home") Modifier.focusRequester(menuFocusRequester) else Modifier))
                }
              }
            }
        }
        Column(Modifier.weight(1f).padding(start = if (menuExpanded) 20.dp else 76.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                if (m.showSearchIcon) {
                IconButton(onClick = { searchText = m.query; hasSearchSubmitted = false; m.tab = "sepia" }, modifier = Modifier.size(46.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) {
                    SearchGlyph()
                }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) { LocalDateTime(m.clock24Hour) }
                Spacer(Modifier.width(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.height(44.dp)) {
                    Image(painter = painterResource(R.drawable.peertube_mark), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
                    Image(painter = painterResource(R.drawable.peertube_wordmark), contentDescription = "PeerTube", contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface), modifier = Modifier.width(124.dp).height(34.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            if (m.tab == "instances") {
                Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(value = m.server, onValueChange = { m.server = it }, label = { Text(stringResource(R.string.server_hint)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
                Button(onClick = { m.save(); m.load(); m.tab = "home" }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.connect)) }
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
                                        .focusRequester(instanceCheckFocusRequester)
                                        .scale(if (checkAllFocused) 1.08f else 1f)
                                        .background(if (checkAllFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .onFocusChanged { checkAllFocused = it.isFocused }
                                        .heightIn(min = 52.dp)
                                ) {
                                    Text(if (m.isCheckingServerAvailability) stringResource(R.string.server_checking) else stringResource(R.string.server_check_all))
                                }
                                TextButton(
                                    onClick = m::toggleServerAvailabilitySort,
                                    modifier = Modifier
                                        .scale(if (sortFocused) 1.08f else 1f)
                                        .background(if (sortFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .onFocusChanged { sortFocused = it.isFocused }
                                        .heightIn(min = 52.dp)
                                ) {
                                    Text(stringResource(if (m.sortServersByAvailability) R.string.server_sort_default else R.string.server_sort_availability))
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
                            var cardFocused by remember(discovered.host) { mutableStateOf(false) }
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
                                freshApiCheck && discovered.apiStatus == "unavailable" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Card(
                                onClick = { m.server = "https://${discovered.host}"; m.save(); m.tab = "home"; m.load() },
                                colors = CardDefaults.cardColors(containerColor = if (cardFocused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth().scale(if (cardFocused) 1.015f else 1f).onFocusChanged { cardFocused = it.isFocused }
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
                                        Text("API $apiStatusText$apiDetail · ${stringResource(R.string.availability_metadata)} ${stringResource(statusResource(discovered.metadataStatus, discovered.metadataCheckedAt))} · ${stringResource(R.string.availability_video_stream)} ${stringResource(statusResource(discovered.streamStatus, discovered.streamCheckedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column {
                                        TextButton(
                                            onClick = { m.checkServerAvailability(discovered.host) },
                                            enabled = discovered.host !in m.checkingServerHosts,
                                            modifier = Modifier.scale(if (checkFocused) 1.08f else 1f)
                                                .background(if (checkFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                                .onFocusChanged { checkFocused = it.isFocused }
                                                .heightIn(min = 52.dp)
                                        ) { Text(if (discovered.host in m.checkingServerHosts) "…" else stringResource(R.string.server_check_api)) }
                                        TextButton(onClick = { m.toggleServerFavorite(discovered.host) }) { Text(if (discovered.favorite) "★" else "☆") }
                                        TextButton(onClick = { m.moveServerPriority(discovered.host, -1) }) { Text("↑") }
                                        TextButton(onClick = { m.moveServerPriority(discovered.host, 1) }) { Text("↓") }
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
                            m.load()
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
                if (m.subscriptions.isEmpty()) {
                    Text(stringResource(R.string.channels_empty), modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.channel_name_header), Modifier.weight(0.40f), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.channel_server_header), Modifier.weight(0.34f), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.channel_action_header), Modifier.weight(0.26f), style = MaterialTheme.typography.titleSmall)
                            }
                            HorizontalDivider()
                        }
                        items(m.subscriptions.toList(), key = { "${it.name}@${it.host}" }) { channel ->
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        channel.displayName,
                                        Modifier.weight(0.40f).clickable { m.loadChannelVideos(channel.name, channel.host) },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (channel.host.isBlank()) stringResource(R.string.server_unknown) else channel.host,
                                        Modifier.weight(0.34f).then(if (channel.host.isNotBlank()) Modifier.clickable { m.loadServerChannels(channel.host) } else Modifier),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (channel.host.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (channel.host.isNotBlank()) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    TextButton(onClick = { m.subscriptions.remove(channel); m.save() }, modifier = Modifier.weight(0.26f)) { Text(stringResource(R.string.unsubscribe), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
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
                                onClick = { channel.name?.takeIf(String::isNotBlank)?.let { name -> m.loadChannelVideos(name, channel.host ?: m.activeServerChannelsHost) } },
                                modifier = Modifier.fillMaxWidth(),
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
                Text(stringResource(R.string.nav_sepia), style = MaterialTheme.typography.headlineSmall)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = startVoiceSearch,
                        modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_search), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it; if (it.isBlank()) hasSearchSubmitted = false },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_peertube)) }
                    )
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (m.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                if (m.isVerifyingAvailability) Text(stringResource(R.string.availability_checking), style = MaterialTheme.typography.bodySmall)
                val searchContext = LocalContext.current
                val usedSources = m.searchSourcesByVideo.values.flatten().toSet().sortedBy { m.searchSettings.order.indexOf(it) }.joinToString(", ") { source ->
                    searchContext.getString(when (source) { SearchSource.SEPIA -> R.string.search_source_sepia; SearchSource.PEERSEEK -> R.string.search_source_peerseek; SearchSource.CUSTOM -> R.string.search_source_custom; SearchSource.FEDERATED -> R.string.search_source_federated; SearchSource.DIRECT -> R.string.search_source_direct; SearchSource.LOCAL -> R.string.search_source_local })
                }
                if (usedSources.isNotBlank()) Text(stringResource(R.string.search_sources_in_use, usedSources), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (SearchSource.LOCAL in m.searchSourcesByVideo.values.flatten() && m.searchSourceErrors.isNotEmpty()) Text(stringResource(R.string.search_cache_stale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                if (m.searchSourceErrors.isNotEmpty()) Text(stringResource(R.string.search_source_errors, m.searchSourceErrors.entries.joinToString { (source, detail) -> "${source.key}: $detail" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                else if (hasSearchSubmitted && m.videos.isEmpty() && m.query.isNotBlank() && m.error == null) Text(stringResource(R.string.search_empty), modifier = Modifier.padding(16.dp))
                if (hasSearchSubmitted && m.gridMode) {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (240.dp * m.gridScale).coerceIn(120.dp, 360.dp)), contentPadding = PaddingValues(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(m.sortVideos(m.videos), key = { it.uuid }) { video -> VideoGridCard(video, video.sourceServer(m.server), m.watchProgressFraction(video), m.favorites.any { it.uuid == video.uuid }, m.isSubscribed(video), m.videoTitleLines, m.tickerSpeed, { openVideoInQueue(video, m.sortVideos(m.videos)) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, { m.excludeFromRecommendations(video) }, { m.markRecommendationWatched(video) }) }
                        if (m.searchHasMore) item { TextButton(onClick = { m.searchSepia(m.query, searchSort, searchCategory.toIntOrNull(), when(searchDuration){"medium"->240;"long"->1200;else->null}, when(searchDuration){"short"->240;"medium"->1200;else->null}, searchLiveOnly, page = m.searchNextPage, append = true) }, enabled = !m.isSearching, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.search_more)) } }
                    }
                } else if (hasSearchSubmitted) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(m.sortVideos(m.videos), key = { it.uuid }) { video -> VideoListRow(video, video.sourceServer(m.server), m.watchProgressFraction(video), m.favorites.any { it.uuid == video.uuid }, m.isSubscribed(video), { openVideoInQueue(video, m.sortVideos(m.videos)) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }) }
                        if (m.searchHasMore) item { TextButton(onClick = { m.searchSepia(m.query, searchSort, searchCategory.toIntOrNull(), when(searchDuration){"medium"->240;"long"->1200;else->null}, when(searchDuration){"short"->240;"medium"->1200;else->null}, searchLiveOnly, page = m.searchNextPage, append = true) }, enabled = !m.isSearching, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.search_more)) } }
                    }
                }
            } else if (m.tab == "settings") {
                SettingsDashboard(
                    model = m,
                    selectedCategory = null,
                    selectedSubpage = null,
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
                    onImportBackup = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                )
            } else if (m.tab == "home") {
                val latestShelfFirstCardFocus = remember { FocusRequester() }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (m.recommendations.isNotEmpty()) {
                        item {
                            VideoShelf(
                                title = stringResource(R.string.shelf_recommended),
                                videos = m.sortVideos(m.recommendations),
                                server = m.server,
                                watchProgressMs = m.watchProgressMs,
                                tickerSpeed = m.tickerSpeed,
                                downFocusRequester = latestShelfFirstCardFocus,
                                titleLines = m.videoTitleLines,
                                onOpen = { video -> openVideoInQueue(video, m.recommendations) },
                                isFavorite = { video -> m.favorites.any { it.uuid == video.uuid } },
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
                            videos = m.sortVideos(m.videos),
                            server = m.server,
                            watchProgressMs = m.watchProgressMs,
                            tickerSpeed = m.tickerSpeed,
                            firstCardFocusRequester = latestShelfFirstCardFocus,
                                titleLines = m.videoTitleLines,
                            onOpen = { video -> openVideoInQueue(video, m.sortVideos(m.videos)) },
                            isFavorite = { video -> m.favorites.any { it.uuid == video.uuid } },
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
                if (m.tab == "subscriptions") {
                    Text(stringResource(R.string.nav_subscriptions), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.subscriptions_help), style = MaterialTheme.typography.bodyMedium)
                    if (m.subscriptions.isEmpty()) Text(stringResource(R.string.subscriptions_empty), modifier = Modifier.padding(16.dp))
                    else if (m.subscriptionVideos.isEmpty() && m.error == null) Text(stringResource(R.string.subscriptions_no_videos), modifier = Modifier.padding(16.dp))
                } else if (m.tab == "sepia") {
                    Text(stringResource(R.string.nav_sepia), style = MaterialTheme.typography.headlineSmall)
                } else if (m.tab == "channel") {
                    Text(m.activeChannelTitle, style = MaterialTheme.typography.headlineSmall)
                } else if (m.tab == "live") {
                    Text(stringResource(R.string.nav_live), style = MaterialTheme.typography.headlineSmall)
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
                    Text(stringResource(sectionTitle), style = MaterialTheme.typography.headlineSmall)
                    if (m.tab == "popular" && m.isLoadingPopular) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    else if (m.tab == "popular" && m.popularVideos.isEmpty() && m.error == null) Text(stringResource(R.string.subscriptions_no_videos), modifier = Modifier.padding(16.dp))
                    if (m.tab == "recommendations") {
                        Text(stringResource(R.string.recommendations_help), style = MaterialTheme.typography.bodyMedium)
                        if (m.isLoadingRecommendations) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                }
                val rows = m.sortVideos(when(m.tab) { "popular" -> m.popularVideos; "history" -> m.history.toList(); "favorites" -> m.favorites.toList(); "recommendations" -> m.recommendations; "subscriptions" -> m.subscriptionVideos; "live" -> m.liveVideos; "channel" -> m.channelVideos; else -> m.videos })
                m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (m.tab == "recommendations" && rows.isEmpty() && !m.isLoadingRecommendations && m.error == null) Text(stringResource(R.string.recommendations_empty), modifier = Modifier.padding(16.dp))
                if (m.tab == "live") {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        if (m.liveVideos.isNotEmpty()) {
                            item { Text(stringResource(R.string.live_now_title), style = MaterialTheme.typography.titleLarge) }
                            items(m.liveVideos, key = { "live-${it.uuid}" }) { video -> VideoListRow(video, m.server, m.watchProgressFraction(video), m.favorites.any { it.uuid == video.uuid }, m.isSubscribed(video), { openVideoInQueue(video, m.liveVideos) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, statusLabel = when (video.state?.id) { 4 -> stringResource(R.string.live_status_waiting); 5 -> stringResource(R.string.live_status_ended); else -> stringResource(R.string.live_status_on_air) }, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }) }
                        }
                        if (m.recentlyEndedLiveVideos.isNotEmpty()) {
                            item { Text(stringResource(R.string.live_recent_title), style = MaterialTheme.typography.titleLarge) }
                            items(m.recentlyEndedLiveVideos, key = { "ended-${it.uuid}" }) { video -> VideoListRow(video, m.server, m.watchProgressFraction(video), m.favorites.any { it.uuid == video.uuid }, m.isSubscribed(video), { openVideoInQueue(video, m.recentlyEndedLiveVideos) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, statusLabel = stringResource(R.string.live_status_ended), onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }) }
                        }
                    }
                } else if (m.tab == "popular" && rows.isEmpty()) {
                    // Loading, empty-state, or error are rendered with the section heading above.
                } else if (m.gridMode) {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (240.dp * m.gridScale).coerceIn(120.dp, 360.dp)), contentPadding = PaddingValues(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(rows, key = { it.uuid }) { video -> VideoGridCard(video, m.server, m.watchProgressFraction(video), m.favorites.any { it.uuid == video.uuid }, m.isSubscribed(video), m.videoTitleLines, m.tickerSpeed, { openVideoInQueue(video, rows) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, { m.excludeFromRecommendations(video) }, { m.markRecommendationWatched(video) }) }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(rows, key = { it.uuid }) { video -> VideoListRow(video, m.server, m.watchProgressFraction(video), m.favorites.any { it.uuid == video.uuid }, m.isSubscribed(video), { openVideoInQueue(video, rows) }, { toggleFavorite(video) }, { m.toggleSubscription(video) }, { openChannelVideos(video) }, updateFocusedVideoMenu, m.contextMenuHoldSeconds, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }) }
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
                            onImportBackup = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
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
    values.forEach { (key, value) ->
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
        item.add("value", Gson().toJsonTree(value))
        json.add(key, item)
    }
    java.util.zip.ZipOutputStream(output).use { zip ->
        zip.putNextEntry(java.util.zip.ZipEntry("settings.json"))
        zip.write(Gson().toJson(json).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

private fun restoreSettingsBackup(context: android.content.Context, uri: android.net.Uri) {
    val settingsJson = context.contentResolver.openInputStream(uri)?.use { input ->
        java.util.zip.ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entry.name != "settings.json") entry = zip.nextEntry
            check(entry != null) { "Backup does not contain settings.json" }
            zip.readBytes().toString(Charsets.UTF_8)
        }
    } ?: error("Backup input could not be opened")
    val json = com.google.gson.JsonParser.parseString(settingsJson).asJsonObject
    val editor = context.getSharedPreferences("peertube", 0).edit().clear()
    json.entrySet().forEach { (key, itemElement) ->
        val item = itemElement.asJsonObject
        val value = item.get("value")
        when (item.get("type").asString) {
            "string" -> editor.putString(key, value.asString)
            "boolean" -> editor.putBoolean(key, value.asBoolean)
            "int" -> editor.putInt(key, value.asInt)
            "long" -> editor.putLong(key, value.asLong)
            "float" -> editor.putFloat(key, value.asFloat)
            "string_set" -> editor.putStringSet(key, value.asJsonArray.map { it.asString }.toSet())
        }
    }
    check(editor.commit()) { "Settings backup could not be restored" }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsDashboard(
    model: HomeModel,
    selectedCategory: String?,
    selectedSubpage: String?,
    onSelectCategory: (String?) -> Unit,
    onSelectSubpage: (String?) -> Unit,
    historyPeriodHours: Int,
    onHistoryPeriodChange: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onClearHistoryPeriod: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val firstSettingsCardFocus = remember { FocusRequester() }
    LaunchedEffect(selectedCategory) {
        if (selectedCategory == null) runCatching { firstSettingsCardFocus.requestFocus() }
    }
    val settingsContext = LocalContext.current
    val playerPrefs = remember(settingsContext) { settingsContext.getSharedPreferences("peertube", 0) }
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
    var subtitleStyle by remember { mutableStateOf(playerPrefs.getString("subtitleStyle", "default") ?: "default") }
    var rememberSubtitleChannel by remember { mutableStateOf(playerPrefs.getBoolean("rememberSubtitleByChannel", true)) }
    val categories = listOf(
        "language" to R.string.settings_language_country,
        "general" to R.string.settings_general,
        "interface" to R.string.settings_interface,
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
                                "language" -> Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(54.dp))
                                "general" -> Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(54.dp))
                                "interface" -> Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(54.dp))
                                "player" -> Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(54.dp))
                                "search" -> SearchGlyph(54.dp)
                                "network" -> Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(54.dp))
                                "backup" -> Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(54.dp))
                                else -> Text("i", style = MaterialTheme.typography.displaySmall)
                            }
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                    if (selectedSubpage == "language") {
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
                            }
                            "general" -> {
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
                                    "minute" -> R.string.watched_threshold_minute
                                    "three_minutes" -> R.string.watched_threshold_three_minutes
                                    "half" -> R.string.watched_threshold_half
                                    "complete" -> R.string.watched_threshold_complete
                                    else -> R.string.watched_threshold_never
                                })
                                SettingsChoice(stringResource(R.string.hide_watched_recommendations), watchedThresholdLabel, listOf(
                                    "never" to stringResource(R.string.watched_threshold_never),
                                    "minute" to stringResource(R.string.watched_threshold_minute),
                                    "three_minutes" to stringResource(R.string.watched_threshold_three_minutes),
                                    "half" to stringResource(R.string.watched_threshold_half),
                                    "complete" to stringResource(R.string.watched_threshold_complete)
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
                            }
                            "interface" -> {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.grid_mode), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(model.gridMode, { model.gridMode = it; model.save() })
                                }
                                val linesLabel = stringResource(when (model.videoTitleLines) { 1 -> R.string.video_title_lines_1; 3 -> R.string.video_title_lines_3; else -> R.string.video_title_lines_2 })
                                SettingsChoice(stringResource(R.string.video_title_lines), linesLabel, listOf("1" to stringResource(R.string.video_title_lines_1), "2" to stringResource(R.string.video_title_lines_2), "3" to stringResource(R.string.video_title_lines_3))) { model.updateVideoTitleLines(it.toInt()) }
                                val speedLabel = "${String.format(Locale.ROOT, "%.1f", model.tickerSpeed).removeSuffix(".0")}x"
                                SettingsChoice(stringResource(R.string.ticker_speed), speedLabel, (2..16).map { n -> val speed = n / 2f; "${speed}" to "${String.format(Locale.ROOT, "%.1f", speed).removeSuffix(".0")}x" }) { model.updateTickerSpeed(it.toFloat()) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.clock_24_hour), Modifier.weight(1f)); Switch(model.clock24Hour, model::updateClock24Hour) }
                                val themeLabel = stringResource(when (model.selectedTheme) { "turquoise" -> R.string.theme_turquoise; "gray" -> R.string.theme_gray; "red" -> R.string.theme_red; else -> R.string.theme_peertube })
                                SettingsChoice(stringResource(R.string.color_theme), themeLabel, listOf("peertube" to stringResource(R.string.theme_peertube), "turquoise" to stringResource(R.string.theme_turquoise), "gray" to stringResource(R.string.theme_gray), "red" to stringResource(R.string.theme_red))) { model.updateTheme(it) }
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
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.subtitle_remember_channel), Modifier.weight(1f)); Switch(rememberSubtitleChannel, { rememberSubtitleChannel = it; playerPrefs.edit().putBoolean("rememberSubtitleByChannel", it).apply() }) }
                                SettingsChoice(stringResource(R.string.subtitle_style), stringResource(if (subtitleStyle == "yellow") R.string.subtitle_style_yellow else R.string.subtitle_style_default), listOf("default" to stringResource(R.string.subtitle_style_default), "yellow" to stringResource(R.string.subtitle_style_yellow))) { subtitleStyle = it; playerPrefs.edit().putString("subtitleStyle", it).apply() }
                                SettingsChoice(stringResource(R.string.subtitle_size), "${(subtitleSize * 100).toInt()}%", listOf("0.04" to "Small", "0.053" to "Medium", "0.065" to "Large")) { subtitleSize = it.toFloat(); playerPrefs.edit().putFloat("subtitleSize", subtitleSize).apply() }
                                SettingsChoice(stringResource(R.string.subtitle_bottom_offset), "${(subtitleOffset * 100).toInt()}%", (5..50 step 5).map { "${it / 100f}" to "$it%" }) { subtitleOffset = it.toFloat(); playerPrefs.edit().putFloat("subtitleBottomOffset", subtitleOffset).apply() }
                            }
                            "search" -> {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.show_search_icon), Modifier.weight(1f))
                                    Switch(model.showSearchIcon, model::updateShowSearchIcon)
                                }
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
                                    Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
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
                                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.about_version_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.about_artic_fox), style = MaterialTheme.typography.bodyMedium)
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
    Text(title, style = MaterialTheme.typography.titleSmall)
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
@Composable private fun VideoListRow(video: Video, server: String, progress: Float, favorite: Boolean, subscribed: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit, onSubscription: () -> Unit, onChannel: () -> Unit, setContextMenuTarget: (String, (() -> Unit)?, (() -> Unit)?) -> Unit, holdSeconds: Float, statusLabel: String? = null, onExcludeRecommendation: () -> Unit = {}, onMarkWatched: () -> Unit = {}) {
    var contextMenu by remember { mutableStateOf(false) }
    var suppressOpenAfterMenu by remember { mutableStateOf(false) }
    val showContextMenu: () -> Unit = { suppressOpenAfterMenu = true; contextMenu = true }
    val dismissContextMenu: () -> Unit = { contextMenu = false; suppressOpenAfterMenu = false }
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()
            .onFocusChanged { state -> if (state.isFocused) setContextMenuTarget(video.uuid, showContextMenu, onOpen) else setContextMenuTarget(video.uuid, null, null) }
            .remoteCenterPress(onOpen, showContextMenu, holdSeconds)
            .combinedClickable(onClick = { if (suppressOpenAfterMenu) suppressOpenAfterMenu = false else onOpen() }, onLongClick = showContextMenu).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(video, server, Modifier.width(180.dp).height(102.dp), progress)
                Column(Modifier.weight(1f).padding(start = 14.dp, end = 54.dp)) {
                Text(
                    if (statusLabel.isNullOrBlank()) video.name else stringResource(R.string.live_title_with_status, video.name, statusLabel),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text("${video.channel?.displayName ?: stringResource(R.string.channel_unknown)} · ${stringResource(R.string.video_views, video.views.toString())}", maxLines = 1)
                PublishedDate(video.publishedAt)
            }
        }
        IconButton(
            onClick = showContextMenu,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(42.dp)
                .onFocusChanged { state -> if (state.isFocused) setContextMenuTarget(video.uuid, showContextMenu, null) else setContextMenuTarget(video.uuid, null, null) }
                .background(Color.Black.copy(alpha = 0.68f), CircleShape)
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.video_actions), tint = Color.White)
        }
        if (contextMenu) VideoContextMenu(video.name, subscribed, favorite, dismissContextMenu, { dismissContextMenu(); onOpen() }, { dismissContextMenu(); onSubscription() }, { dismissContextMenu(); onFavorite() }, { dismissContextMenu(); onChannel() }, { dismissContextMenu(); onExcludeRecommendation() }, { dismissContextMenu(); onMarkWatched() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun VideoGridCard(video: Video, server: String, progress: Float, favorite: Boolean, subscribed: Boolean, titleLines: Int, tickerSpeed: Float, onOpen: () -> Unit, onFavorite: () -> Unit, onSubscription: () -> Unit, onChannel: (String) -> Unit, setContextMenuTarget: (String, (() -> Unit)?, (() -> Unit)?) -> Unit, holdSeconds: Float, onExcludeRecommendation: () -> Unit = {}, onMarkWatched: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    var contextMenu by remember { mutableStateOf(false) }
    var suppressOpenAfterMenu by remember { mutableStateOf(false) }
    val showContextMenu: () -> Unit = { suppressOpenAfterMenu = true; contextMenu = true }
    val dismissContextMenu: () -> Unit = { contextMenu = false; suppressOpenAfterMenu = false }
    val channel = video.channel?.name ?: video.account?.name ?: video.channel?.displayName ?: video.account?.displayName ?: ""
    Box {
        Card(
            modifier = Modifier.fillMaxWidth().zIndex(if (focused) 1f else 0f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) setContextMenuTarget(video.uuid, showContextMenu, onOpen) else setContextMenuTarget(video.uuid, null, null)
                }
                .remoteCenterPress(onOpen, showContextMenu, holdSeconds)
                .combinedClickable(onClick = { if (suppressOpenAfterMenu) suppressOpenAfterMenu = false else onOpen() }, onLongClick = showContextMenu),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (focused) Color.White else MaterialTheme.colorScheme.surface,
                contentColor = if (focused) Color.Black else MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, focusedElevation = if (focused) 8.dp else 1.dp)
        ) {
            Column {
                VideoThumbnail(video, server, Modifier.fillMaxWidth().aspectRatio(16f / 9f), progress)
                Column(Modifier.fillMaxWidth().height((titleLines * 20 + 80).dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(video.name, style = MaterialTheme.typography.titleSmall, maxLines = titleLines, minLines = titleLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = if (focused) Modifier.basicMarquee(velocity = 30.dp * tickerSpeed) else Modifier)
                    Text(video.channel?.displayName ?: video.account?.displayName ?: stringResource(R.string.channel_unknown), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(stringResource(R.string.video_views, video.views.toString()), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    PublishedDate(video.publishedAt)
                }
            }
        }
        IconButton(
            onClick = showContextMenu,
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(40.dp)
                .onFocusChanged { state -> if (state.isFocused) setContextMenuTarget(video.uuid, showContextMenu, null) else setContextMenuTarget(video.uuid, null, null) }
                .background(Color.Black.copy(alpha = 0.72f), CircleShape)
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.video_actions), tint = Color.White)
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
    Box(modifier) {
        AsyncImage(
            model = video.thumbnailUrl ?: ApiFactory.image(video.sourceServer(server), video.thumbnailPath),
            contentDescription = video.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (progress > 0f) {
            Canvas(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart)) {
                drawLine(Color(0x99FFFFFF), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
                drawLine(Color(0xFFF36C21), Offset(0f, size.height / 2), Offset(size.width * progress.coerceIn(0f, 1f), size.height / 2), strokeWidth = size.height)
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

@Composable private fun PublishedDate(publishedAt: String?) {
    val formatted = remember(publishedAt, Locale.getDefault()) { formatPublishedDate(publishedAt) }
    if (formatted != null) Text(stringResource(R.string.video_published, formatted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
        TextButton(onClick = { expanded = true }) { Text("$label: $selected", maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(value); expanded = false })
            }
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
    setContextMenuTarget: (String, (() -> Unit)?, (() -> Unit)?) -> Unit,
    contextMenuHoldSeconds: Float,
    titleLines: Int = 2,
    downFocusRequester: FocusRequester? = null,
    firstCardFocusRequester: FocusRequester? = null,
    onExcludeRecommendation: (Video) -> Unit = {},
    onMarkWatched: (Video) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            items(videos, key = { it.uuid }) { video ->
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
                            .then(if (video.uuid == videos.firstOrNull()?.uuid && firstCardFocusRequester != null) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                            .then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)
                            .scale(if (focused) 1.05f else 1f)
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) setContextMenuTarget(video.uuid, showContextMenu, { onOpen(video) })
                                else setContextMenuTarget(video.uuid, null, null)
                            }
                            .remoteCenterPress({ onOpen(video) }, showContextMenu, contextMenuHoldSeconds),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (focused) Color.White else MaterialTheme.colorScheme.surface,
                            contentColor = if (focused) Color.Black else MaterialTheme.colorScheme.onSurface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, focusedElevation = if (focused) 8.dp else 1.dp)
                    ) {
                        Column {
                            val progress = if (video.duration > 0) ((watchProgressMs[video.uuid] ?: 0L).toFloat() / (video.duration * 1000f)).coerceIn(0f, 1f) else 0f
                            VideoThumbnail(video, server, Modifier.fillMaxWidth().aspectRatio(16f / 9f), progress)
                            Column(Modifier.fillMaxWidth().height((titleLines * 20 + 58).dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    video.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = titleLines,
                                    minLines = titleLines,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = if (focused) Modifier.basicMarquee(velocity = 30.dp * tickerSpeed) else Modifier
                                )
                                Text(video.channel?.displayName ?: video.account?.displayName ?: stringResource(R.string.channel_unknown), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                val date = formatPublishedDate(video.publishedAt) ?: stringResource(R.string.shelf_date_unknown)
                                Text(stringResource(R.string.shelf_video_stats, video.views.toString(), date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    if (contextMenu) VideoContextMenu(video.name, isSubscribed(video), isFavorite(video), dismissContextMenu, { dismissContextMenu(); onOpen(video) }, { dismissContextMenu(); onSubscription(video) }, { dismissContextMenu(); onFavorite(video) }, { dismissContextMenu(); onChannel(video) }, { dismissContextMenu(); onExcludeRecommendation(video) }, { dismissContextMenu(); onMarkWatched(video) })
                }
            }
        }
    }
}
