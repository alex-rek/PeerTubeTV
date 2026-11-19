package app.peertube.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.VideoSectionTab(m: HomeModel, ui: HomeUiState, actions: HomeActions) {
    if (m.tab == "subscriptions") {
        if (!ui.headerHidden) {
            Text(stringResource(R.string.nav_subscriptions), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.subscriptions_help), style = MaterialTheme.typography.bodyMedium)
        }
        if (m.isLoadingSubscriptions) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        else if (m.subscriptions.isEmpty()) Text(stringResource(R.string.subscriptions_empty), modifier = Modifier.padding(16.dp))
        else if (m.subscriptionVideos.isEmpty() && m.error == null) Text(stringResource(R.string.subscriptions_no_videos), modifier = Modifier.padding(16.dp))
    } else if (m.tab == "channel") {
        if (!ui.headerHidden) Text(m.activeChannelTitle, style = MaterialTheme.typography.headlineSmall)
    } else if (m.tab == "live") {
        if (!ui.headerHidden) Text(stringResource(R.string.nav_live), style = MaterialTheme.typography.headlineSmall)
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
        if (!ui.headerHidden) Text(stringResource(sectionTitle), style = MaterialTheme.typography.headlineSmall)
        if (m.tab == "history" && m.isLoadingHistory) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (m.tab == "popular" && m.isLoadingPopular) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        else if (m.tab == "popular" && m.popularVideos.isEmpty() && m.error == null) Text(stringResource(R.string.subscriptions_no_videos), modifier = Modifier.padding(16.dp))
        if (m.tab == "recommendations") {
            if (!ui.headerHidden) Text(stringResource(R.string.recommendations_help), style = MaterialTheme.typography.bodyMedium)
            if (m.isLoadingRecommendations) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
    }
    val rows = m.sortVideos(when(m.tab) { "popular" -> m.popularVideos; "history" -> m.history.toList(); "favorites" -> m.favorites.toList(); "recommendations" -> m.recommendations; "subscriptions" -> m.subscriptionVideos; "live" -> m.liveVideos; "channel" -> m.channelVideos; else -> m.videos })
    val savedSectionVideoId = ui.lastFocusedCardBySection[m.tab]
    m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (m.tab == "recommendations" && rows.isEmpty() && !m.isLoadingRecommendations && m.error == null) Text(stringResource(R.string.recommendations_empty), modifier = Modifier.padding(16.dp))
    if (m.tab == "live") {
        val liveListState = key(m.tab) { rememberLazyListState() }
        LaunchedEffect(ui.sectionFocusPending, savedSectionVideoId, m.liveVideos, m.recentlyEndedLiveVideos) {
            if (ui.sectionFocusPending && savedSectionVideoId != null) {
                val liveIndex = m.liveVideos.indexOfFirst { it.uuid == savedSectionVideoId }
                val endedIndex = m.recentlyEndedLiveVideos.indexOfFirst { it.uuid == savedSectionVideoId }
                if (liveIndex >= 0) liveListState.scrollToItem(if (m.liveVideos.isNotEmpty()) liveIndex + 1 else liveIndex)
                else if (endedIndex >= 0) liveListState.scrollToItem((if (m.liveVideos.isNotEmpty()) m.liveVideos.size + 1 else 0) + endedIndex + 1)
            }
        }
        LazyColumn(Modifier.weight(1f), state = liveListState, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (m.liveVideos.isNotEmpty()) {
                item { Text(stringResource(R.string.live_now_title), style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(m.liveVideos, key = { _, video -> "live-${video.uuid}" }) { index, video -> VideoListRow(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), { actions.openVideoInQueue(video, m.liveVideos) }, { actions.toggleFavorite(video) }, { m.toggleSubscription(video) }, { actions.openChannelVideos(video) }, actions.updateFocusedVideoMenu, m.contextMenuHoldSeconds, statusLabel = when (video.state?.id) { 4 -> stringResource(R.string.live_status_waiting); 5 -> stringResource(R.string.live_status_ended); else -> stringResource(R.string.live_status_on_air) }, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }, firstCardFocusRequester = if (index == 0) ui.sectionFirstFocusRequester else null, upFocusRequester = if (index == 0) ui.searchButtonFocusRequester else null, preferredFocusRequester = ui.sectionRestoreFocusRequester, preferredFocusVideoId = savedSectionVideoId) }
            }
            if (m.recentlyEndedLiveVideos.isNotEmpty()) {
                item { Text(stringResource(R.string.live_recent_title), style = MaterialTheme.typography.titleLarge) }
                itemsIndexed(m.recentlyEndedLiveVideos, key = { _, video -> "ended-${video.uuid}" }) { index, video -> VideoListRow(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), { actions.openVideoInQueue(video, m.recentlyEndedLiveVideos) }, { actions.toggleFavorite(video) }, { m.toggleSubscription(video) }, { actions.openChannelVideos(video) }, actions.updateFocusedVideoMenu, m.contextMenuHoldSeconds, statusLabel = stringResource(R.string.live_status_ended), onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }, firstCardFocusRequester = if (m.liveVideos.isEmpty() && index == 0) ui.sectionFirstFocusRequester else null, upFocusRequester = if (m.liveVideos.isEmpty() && index == 0) ui.searchButtonFocusRequester else null, preferredFocusRequester = ui.sectionRestoreFocusRequester, preferredFocusVideoId = savedSectionVideoId) }
            }
        }
    } else if (m.tab == "popular" && rows.isEmpty()) {
        // Loading, empty-state, or error are rendered with the section heading above.
    } else if (m.gridMode) {
        val sectionGridState = key(m.tab) { rememberLazyGridState() }
        LaunchedEffect(ui.sectionFocusPending, savedSectionVideoId, rows) {
            if (ui.sectionFocusPending && savedSectionVideoId != null) rows.indexOfFirst { it.uuid == savedSectionVideoId }.takeIf { it >= 0 }?.let { sectionGridState.scrollToItem(it) }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val gridSpacing = 14.dp
            val minCardWidth = (240.dp * m.gridScale).coerceIn(120.dp, 360.dp)
            val columns = ((maxWidth + gridSpacing) / (minCardWidth + gridSpacing)).toInt().coerceAtLeast(1)
            LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(), state = sectionGridState, contentPadding = PaddingValues(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(gridSpacing), verticalArrangement = Arrangement.spacedBy(gridSpacing)) {
                itemsIndexed(rows, key = { _, video -> video.uuid }) { index, video -> VideoGridCard(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), m.videoTitleLines, m.tickerSpeed, { actions.openVideoInQueue(video, rows) }, { actions.toggleFavorite(video) }, { m.toggleSubscription(video) }, { actions.openChannelVideos(video) }, actions.updateFocusedVideoMenu, m.contextMenuHoldSeconds, { m.excludeFromRecommendations(video) }, { m.markRecommendationWatched(video) }, if (index == 0) ui.sectionFirstFocusRequester else null, if (index < columns) ui.searchButtonFocusRequester else null, ui.sectionRestoreFocusRequester, savedSectionVideoId, isAtLeftBoundary = index % columns == 0) }
            }
        }
    } else {
        val sectionListState = key(m.tab) { rememberLazyListState() }
        LaunchedEffect(ui.sectionFocusPending, savedSectionVideoId, rows) {
            if (ui.sectionFocusPending && savedSectionVideoId != null) rows.indexOfFirst { it.uuid == savedSectionVideoId }.takeIf { it >= 0 }?.let { sectionListState.scrollToItem(it) }
        }
        LazyColumn(state = sectionListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(rows, key = { _, video -> video.uuid }) { index, video -> VideoListRow(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), { actions.openVideoInQueue(video, rows) }, { actions.toggleFavorite(video) }, { m.toggleSubscription(video) }, { actions.openChannelVideos(video) }, actions.updateFocusedVideoMenu, m.contextMenuHoldSeconds, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }, firstCardFocusRequester = if (index == 0) ui.sectionFirstFocusRequester else null, upFocusRequester = if (index == 0) ui.searchButtonFocusRequester else null, preferredFocusRequester = ui.sectionRestoreFocusRequester, preferredFocusVideoId = savedSectionVideoId) }
        }
    }
}
