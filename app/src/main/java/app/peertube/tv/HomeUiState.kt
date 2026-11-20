package app.peertube.tv

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.Job

/** UI-only state of the home screen: focus bookkeeping, menu, header and dialog flags. */
@Stable
internal class HomeUiState {
    val menuFocusRequester = FocusRequester()
    val searchButtonFocusRequester = FocusRequester()
    val pendingMenuFocus = arrayOfNulls<Pair<String, Job>>(1)
    val expandedMenuFocusRequesters = mutableMapOf<String, FocusRequester>()
    val collapsedMenuFocusRequesters = mutableMapOf<String, FocusRequester>()
    val homeFirstCardFocusRequester = FocusRequester()
    val homeLatestFirstCardFocusRequester = FocusRequester()
    val homeGridRestoreFocusRequester = FocusRequester()
    val homeRecommendationRestoreFocusRequester = FocusRequester()
    val homeLatestRestoreFocusRequester = FocusRequester()
    val sectionFirstFocusRequester = FocusRequester()
    val sectionRestoreFocusRequester = FocusRequester()
    val firstSearchResultFocusRequester = FocusRequester()
    val searchRestoreFocusRequester = FocusRequester()
    val instancesConnectFocusRequester = FocusRequester()
    val checkAllServersFocusRequester = FocusRequester()
    val firstServerConnectFocusRequester = FocusRequester()
    val serverFieldFocusRequester = FocusRequester()
    // Focus bookkeeping is consumed by the restore effects on menu/tab changes;
    // it does not need to invalidate the whole screen on every D-pad movement.
    val lastFocusedCardBySection = mutableMapOf<String, String>()
    var focusedMenuSectionKey by mutableStateOf("home")
    var collapsedMenuItemFocused by mutableStateOf(false)
    var focusedVideoAtLeftBoundary by mutableStateOf(false)
    var sectionFocusPending by mutableStateOf(false)
    var searchResultFocusPending by mutableStateOf(false)
    var returnSearchFocusPending by mutableStateOf(false)
    var searchButtonFocused by mutableStateOf(false)
    var menuExpanded by mutableStateOf(false)
    var headerHidden by mutableStateOf(false)
    var settingsOverlay by mutableStateOf(false)
    var focusedVideoMenu by mutableStateOf<Pair<String, () -> Unit>?>(null)
    var clearHistoryDialog by mutableStateOf(false)
    var clearHistoryPeriodDialog by mutableStateOf(false)
    var settingsCategory by mutableStateOf<String?>(null)
    var settingsSubpage by mutableStateOf<String?>(null)
    var homeInitialized by mutableStateOf(false)
    var historyPeriodHours by mutableIntStateOf(1)
}

/** Query and filters of the search tab. */
@Stable
internal class SearchUiState {
    var text by mutableStateOf("")
    var submitted by mutableStateOf(false)
    var sort by mutableStateOf("-match")
    var category by mutableStateOf("any")
    var duration by mutableStateOf("any")
    var liveOnly by mutableStateOf(false)
    var availabilityFilter by mutableStateOf("unknown_or_available")
}

/** Callbacks shared by the home screen tabs. */
@Immutable
internal class HomeActions(
    val openVideoInQueue: (Video, List<Video>) -> Unit,
    val toggleFavorite: (Video) -> Unit,
    val openChannelVideos: (Video) -> Unit,
    val updateFocusedVideoMenu: (String, (() -> Unit)?, (() -> Unit)?, Boolean) -> Unit,
    val navigateToSection: (String, Boolean) -> Unit,
    val startVoiceSearch: () -> Unit,
    val submitSearch: () -> Unit,
    val exportBackup: () -> Unit,
    val launchBackupRestore: () -> Unit,
)
