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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.HomeFeedTab(m: HomeModel, ui: HomeUiState, actions: HomeActions) {
    val savedHomeVideoId = ui.lastFocusedCardBySection["home"]
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
                    firstCardFocusRequester = ui.homeFirstCardFocusRequester,
                    preferredFocusRequester = ui.homeRecommendationRestoreFocusRequester,
                    preferredFocusVideoId = savedHomeVideoId,
                    restoreFocusPending = ui.sectionFocusPending,
                    downFocusRequesters = recommendationDownRequesters,
                    upFocusRequester = ui.searchButtonFocusRequester,
                    titleLines = m.videoTitleLines,
                    onOpen = { video -> actions.openVideoInQueue(video, m.recommendations) },
                    isFavorite = { video -> video.uuid in m.favoriteIds },
                    isSubscribed = m::isSubscribed,
                    onFavorite = actions.toggleFavorite,
                    onSubscription = m::toggleSubscription,
                    onChannel = actions.openChannelVideos,
                    setContextMenuTarget = actions.updateFocusedVideoMenu,
                    contextMenuHoldSeconds = m.contextMenuHoldSeconds,
                    onExcludeRecommendation = m::excludeFromRecommendations,
                    onMarkWatched = m::markRecommendationWatched,
                    onVideoFocus = { ui.headerHidden = false }
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
                    firstCardFocusRequester = if (m.recommendations.isEmpty()) ui.homeFirstCardFocusRequester else ui.homeLatestFirstCardFocusRequester,
                    preferredFocusRequester = ui.homeLatestRestoreFocusRequester,
                    preferredFocusVideoId = savedHomeVideoId,
                    restoreFocusPending = ui.sectionFocusPending,
                    itemFocusRequesters = latestCardRequesters,
                    upFocusRequester = ui.searchButtonFocusRequester,
                    titleLines = m.videoTitleLines,
                onOpen = { video -> actions.openVideoInQueue(video, m.sortVideos(m.videos)) },
                isFavorite = { video -> video.uuid in m.favoriteIds },
                isSubscribed = m::isSubscribed,
                onFavorite = actions.toggleFavorite,
                onSubscription = m::toggleSubscription,
                onChannel = actions.openChannelVideos,
                    setContextMenuTarget = actions.updateFocusedVideoMenu,
                contextMenuHoldSeconds = m.contextMenuHoldSeconds,
                onExcludeRecommendation = m::excludeFromRecommendations,
                onMarkWatched = m::markRecommendationWatched,
                onVideoFocus = { ui.headerHidden = m.recommendations.isNotEmpty() }
            )
        }
        }
    } else {
        val sortedHomePageVideos = remember(m.homePageVideos, m.videoSortMode) { m.sortVideos(m.homePageVideos) }
        val homeGridState = rememberLazyGridState()
            val homeVideoStartIndex = 1 + (if (m.isLoadingHomePage) 1 else 0) + (if (!m.homePageError.isNullOrBlank()) 1 else 0)
            LaunchedEffect(ui.sectionFocusPending, savedHomeVideoId, sortedHomePageVideos.size, homeVideoStartIndex) {
                if (ui.sectionFocusPending) savedHomeVideoId?.let { id -> sortedHomePageVideos.indexOfFirst { it.uuid == id }.takeIf { it >= 0 }?.let { homeGridState.scrollToItem(homeVideoStartIndex + it) } }
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
                    if (!ui.headerHidden) Text(customHomeTitle, style = MaterialTheme.typography.headlineSmall)
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
                        onOpen = { actions.openVideoInQueue(video, sortedHomePageVideos) },
                        onFavorite = { actions.toggleFavorite(video) },
                        onSubscription = { m.toggleSubscription(video) },
                        onChannel = { actions.openChannelVideos(video) },
                        setContextMenuTarget = actions.updateFocusedVideoMenu,
                        holdSeconds = m.contextMenuHoldSeconds,
                        onExcludeRecommendation = { m.excludeFromRecommendations(video) },
                        onMarkWatched = { m.markRecommendationWatched(video) },
                        firstCardFocusRequester = if (index == 0) ui.homeFirstCardFocusRequester else null,
                        upFocusRequester = if (index < columns) ui.searchButtonFocusRequester else null,
                        preferredFocusRequester = ui.homeGridRestoreFocusRequester,
                        preferredFocusVideoId = savedHomeVideoId,
                        isAtLeftBoundary = index % columns == 0,
                        onCardFocus = { focused -> if (focused) ui.headerHidden = index / columns > 0 }
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
}
