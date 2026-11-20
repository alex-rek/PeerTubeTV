package app.peertube.tv

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.Locale
import com.google.gson.reflect.TypeToken

internal val supportedUiLanguages = listOf("en", "ru", "es", "fr", "de", "pt", "it", "zh-CN", "ja", "hi", "ar")

private data class SavedContentSnapshot(
    val watched: List<String>, val excluded: List<String>, val hidden: Set<String>,
    val history: List<Video>
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
    var afterVideoAction by mutableStateOf(AfterVideoAction.FINISH)
        private set
    var playerOverlayDurationSeconds by mutableIntStateOf(3)
        private set
    var showPlayerClock by mutableStateOf(false)
        private set
    var playerClockPosition by mutableStateOf(ClockPosition.TOP_RIGHT)
        private set
    var playerClockSize by mutableStateOf(ClockSize.MEDIUM)
        private set
    var playerClockBackdrop by mutableStateOf(ClockBackdrop.NONE)
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
    var miniSectionRailEnabled by mutableStateOf(true)
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
    var bufferProfile by mutableStateOf(BufferProfile.MEDIUM)
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
        // Job scheduling and parsing the server catalog take ~70 ms on a TV box: keep them off the main thread.
        val repository = serverRepository!!
        val discoveryManager = serverDiscoveryManager!!
        viewModelScope.launch {
            val servers = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { discoveryManager.schedulePeriodic() }.onFailure { android.util.Log.w("PeerIcheTV", "Could not schedule server discovery", it) }
                repository.all()
            }
            if (discoveredServers.isEmpty()) discoveredServers = servers
        }
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
        afterVideoAction = AfterVideoAction.fromKey(prefs?.getString("afterVideoAction", null))
        playerOverlayDurationSeconds = (prefs?.getInt("playerOverlayDurationSeconds", 3) ?: 3).coerceIn(3, 10).let { if (it == 3 || it == 5 || it == 10) it else 3 }
        showPlayerClock = prefs?.getBoolean("showPlayerClock", true) ?: true
        playerClockPosition = ClockPosition.fromKey(prefs?.getString("playerClockPosition", null))
        playerClockSize = ClockSize.fromKey(prefs?.getString("playerClockSize", null))
        playerClockBackdrop = ClockBackdrop.fromKey(prefs?.getString("playerClockBackdrop", null))
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
        miniSectionRailEnabled = prefs?.getBoolean("miniSectionRailEnabled", true) ?: true
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
                playerClockBackdrop = ClockBackdrop.NONE
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
        bufferProfile = BufferProfile.fromKey(prefs?.getString("bufferProfile", null))
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
        favorites.addAll(LibraryStore.favorites(context))
        favorites.forEach { favoriteIds[it.uuid] = true }
        localSubscriptions.addAll(LibraryStore.subscriptions(context))
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
        preferences.edit().putString("server", server).putBoolean("gridMode", gridMode).putString("afterVideoAction", afterVideoAction.key)
            .putString("homeContentMode", homePageDisplayMode).putString("homeInterests", PeerTubeGson.instance.toJson(homePageInterests.sorted()))
            .putString("homeServer", homePageServer).putString("homeChannel", homePageChannel)
            .putInt("playerOverlayDurationSeconds", playerOverlayDurationSeconds).putBoolean("showPlayerClock", showPlayerClock)
            .putString("playerClockPosition", playerClockPosition.key).putString("playerClockSize", playerClockSize.key).putString("playerClockBackdrop", playerClockBackdrop.key)
            .putBoolean("resumePlayback", resumePlayback).putString("watchedRecommendationThreshold", watchedRecommendationThreshold)
            .putString("recommendationBasis", recommendationBasis).putString("recommendationLanguage", recommendationLanguage)
            .putBoolean("peerSeekEnabled", peerSeekEnabled).putString("videoSortMode", videoSortMode).putBoolean("navigationSounds", navigationSounds)
            .putString("exitMode", exitMode).putInt("videoTitleLines", videoTitleLines).putFloat("tickerSpeed", tickerSpeed)
            .putFloat("contextMenuHoldSeconds", contextMenuHoldSeconds).putInt("seekStepSeconds", seekStepSeconds).putString("launchTab", launchTab)
            .putString("selectedTheme", selectedTheme).putBoolean("showSearchIcon", showSearchIcon).putBoolean("miniSectionRailEnabled", miniSectionRailEnabled).putFloat("uiScale", uiScale).putFloat("gridScale", gridScale)
            .putBoolean("clock24Hour", clock24Hour).putString("searchCountryCode", searchCountryCode).putString("serverSortMode", serverSortMode)
            .putString("bufferProfile", bufferProfile.key).putInt("playerVolume", playerVolume).apply()

        val content = SavedContentSnapshot(
            watchedVideoIds.keys.toList().takeLast(1_000), excludedRecommendationIds.toList().takeLast(5_000), hiddenSections.toSet(),
            history.toList().take(100)
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
            } catch (error: Exception) { logFailure("loadHomePage failed", error);
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
            } catch (error: Exception) { logFailure("loadMoreHomePage failed", error);
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
    fun setAfterVideoAction(action: AfterVideoAction) { afterVideoAction = action; save() }
    fun updatePlayerOverlayDuration(seconds: Int) { playerOverlayDurationSeconds = seconds.coerceIn(3, 10).let { if (it == 3 || it == 5 || it == 10) it else 3 }; save() }
    fun updateShowPlayerClock(show: Boolean) { showPlayerClock = show; save() }
    fun updatePlayerClockPosition(position: ClockPosition) { playerClockPosition = position; save() }
    fun updatePlayerClockSize(size: ClockSize) { playerClockSize = size; save() }
    fun updatePlayerClockBackdrop(backdrop: ClockBackdrop) { playerClockBackdrop = backdrop; save() }
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
    fun updateMiniSectionRailEnabled(enabled: Boolean) { miniSectionRailEnabled = enabled; save() }
    fun updateShowSearchIcon(show: Boolean) { showSearchIcon = show; save() }
    fun updateUiScale(scale: Float) { uiScale = scale.coerceIn(0.85f, 1.4f); save() }
    fun updateGridScale(scale: Float) { gridScale = scale.coerceIn(0.5f, 1.5f); save() }
    fun updateClock24Hour(enabled: Boolean) { clock24Hour = enabled; save() }
    fun updateSearchCountry(code: String) { searchCountryCode = code.uppercase(Locale.ROOT); prefs?.edit()?.putBoolean("countryInitialized", true)?.apply(); save() }
    fun updateBufferProfile(profile: BufferProfile) { bufferProfile = profile; save() }
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("runServerDiscovery failed", e); discoveryError = e.localizedMessage ?: e.javaClass.simpleName }
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
            } catch (e: Exception) { logFailure("checkCachedServersAvailability failed", e);
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("addServerManually failed", e); withContext(kotlinx.coroutines.Dispatchers.Main) { discoveryError = e.localizedMessage ?: "PeerTube API could not be verified" } }
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
        val stored = appContext?.let(LibraryStore::subscriptions) ?: return
        localSubscriptions.clear()
        localSubscriptions.addAll(stored)
        if (activeAccountId == null) { subscriptions.clear(); subscriptions.addAll(stored) }
    }
    fun syncFavorites() {
        val stored = appContext?.let(LibraryStore::favorites) ?: return
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
            val stored = appContext?.let { LibraryStore.setSubscribed(it, channel, subscribed) } ?: return
            localSubscriptions.clear(); localSubscriptions.addAll(stored)
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
        val stored = appContext?.let { LibraryStore.setSubscribed(it, channel, false) } ?: return
        localSubscriptions.clear(); localSubscriptions.addAll(stored)
        if (activeAccountId == null) { subscriptions.clear(); subscriptions.addAll(localSubscriptions) }
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
                        } catch (e: Exception) { logFailure("loadSubscriptions failed", e);
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
    fun load(search: String? = null) { viewModelScope.launch { try { error = null; videos = ApiFactory.create(server).videos(search = search?.let(::countrySearchTerm), languageOneOf = contentLanguage()).data; if (search == null && recommendations.isEmpty()) publishAndroidTvRecommendations(videos) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("load failed", e); error = message(R.string.error_server, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty()) } } }
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("loadPopular failed", e);
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
            } catch (e: Exception) { logFailure("loadLive failed", e);
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("searchSepia failed", e);
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("loadChannelVideos failed", e);
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("loadServerChannels failed", e);
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("loadInstances failed", e);
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
                    catch (error: Exception) {
                        logFailure("Channel lookup failed, falling back to search", error)
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { logFailure("loadRecommendations failed", e); error = message(R.string.error_recommendations, e.localizedMessage ?: appContext?.getString(R.string.error_request).orEmpty()) }
            finally { isLoadingRecommendations = false }
        }
    }
}
