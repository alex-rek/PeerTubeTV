package app.peertube.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.SearchTab(m: HomeModel, ui: HomeUiState, search: SearchUiState, actions: HomeActions) {
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
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = actions.startVoiceSearch,
            modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_search), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        TvRemoteTextField(
            value = search.text,
            onValueChange = { search.text = it; if (it.isBlank()) search.submitted = false },
            label = stringResource(R.string.search_peertube),
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && search.text.isNotBlank()) {
                    ui.searchResultFocusPending = true
                    true
                } else false
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            labelStyle = MaterialTheme.typography.labelSmall
        )
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        SearchFilter(stringResource(R.string.search_sort), sortOptions.first { it.first == search.sort }.second, sortOptions) { search.sort = it }
        SearchFilter(stringResource(R.string.search_category), categoryOptions.first { it.first == search.category }.second, categoryOptions) { search.category = it }
        SearchFilter(stringResource(R.string.search_duration_filter), durationOptions.first { it.first == search.duration }.second, durationOptions) { search.duration = it }
        val availabilityOptions = listOf(
            "all" to stringResource(R.string.availability_all),
            "unknown_or_available" to stringResource(R.string.availability_unknown_or_available),
            "available_only" to stringResource(R.string.availability_available_only)
        )
        SearchFilter(stringResource(R.string.availability_filter), availabilityOptions.first { it.first == search.availabilityFilter }.second, availabilityOptions) { search.availabilityFilter = it; m.updateAvailabilityFilter(it); if (search.submitted) actions.submitSearch() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.search_live_only), style = MaterialTheme.typography.bodySmall)
            Switch(checked = search.liveOnly, onCheckedChange = { search.liveOnly = it })
        }
    }
    val sortedSearchVideos = remember(m.videos, m.videoSortMode) { m.sortVideos(m.videos) }
    val savedSearchVideoId = ui.lastFocusedCardBySection["sepia"]
    m.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (m.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
    if (m.isVerifyingAvailability) Text(stringResource(R.string.availability_checking), style = MaterialTheme.typography.bodySmall)
    val sourceLabels = mapOf(
        SearchSource.SEPIA to stringResource(R.string.search_source_sepia),
        SearchSource.PEERSEEK to stringResource(R.string.search_source_peerseek),
        SearchSource.CUSTOM to stringResource(R.string.search_source_custom),
        SearchSource.FEDERATED to stringResource(R.string.search_source_federated),
        SearchSource.DIRECT to stringResource(R.string.search_source_direct),
        SearchSource.LOCAL to stringResource(R.string.search_source_local)
    )
    val usedSources = m.searchSourcesByVideo.values.flatten().toSet().sortedBy { m.searchSettings.order.indexOf(it) }.joinToString(", ") { source ->
        sourceLabels.getValue(source)
    }
    if (usedSources.isNotBlank()) Text(stringResource(R.string.search_sources_in_use, usedSources), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (SearchSource.LOCAL in m.searchSourcesByVideo.values.flatten() && m.searchSourceErrors.isNotEmpty()) Text(stringResource(R.string.search_cache_stale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    if (m.searchSourceErrors.isNotEmpty() && m.videos.isEmpty()) Text(stringResource(R.string.search_source_errors, m.searchSourceErrors.entries.joinToString { (source, detail) -> "${source.key}: $detail" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    else if (search.submitted && m.videos.isEmpty() && m.query.isNotBlank() && m.error == null) Text(stringResource(R.string.search_empty), modifier = Modifier.padding(16.dp))
    if (search.submitted && m.gridMode) {
        val searchGridState = rememberLazyGridState()
        LaunchedEffect(ui.sectionFocusPending, savedSearchVideoId, sortedSearchVideos.size) {
            if (ui.sectionFocusPending) savedSearchVideoId?.let { id -> sortedSearchVideos.indexOfFirst { it.uuid == id }.takeIf { it >= 0 }?.let { searchGridState.scrollToItem(it) } }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val gridSpacing = 14.dp
            val minCardWidth = (240.dp * m.gridScale).coerceIn(120.dp, 360.dp)
            val columns = ((maxWidth + gridSpacing) / (minCardWidth + gridSpacing)).toInt().coerceAtLeast(1)
            LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(), state = searchGridState, contentPadding = PaddingValues(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(gridSpacing), verticalArrangement = Arrangement.spacedBy(gridSpacing)) {
                itemsIndexed(sortedSearchVideos, key = { _, video -> video.uuid }) { index, video -> VideoGridCard(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), m.videoTitleLines, m.tickerSpeed, { actions.openVideoInQueue(video, sortedSearchVideos) }, { actions.toggleFavorite(video) }, { m.toggleSubscription(video) }, { actions.openChannelVideos(video) }, actions.updateFocusedVideoMenu, m.contextMenuHoldSeconds, { m.excludeFromRecommendations(video) }, { m.markRecommendationWatched(video) }, if (index == 0) ui.firstSearchResultFocusRequester else null, if (index < columns) ui.searchButtonFocusRequester else null, ui.searchRestoreFocusRequester, savedSearchVideoId, isAtLeftBoundary = index % columns == 0) }
                if (m.searchHasMore) item { TextButton(onClick = { m.searchSepia(m.query, search.sort, search.category.toIntOrNull(), when(search.duration){"medium"->240;"long"->1200;else->null}, when(search.duration){"short"->240;"medium"->1200;else->null}, search.liveOnly, page = m.searchNextPage, append = true) }, enabled = !m.isSearching, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.search_more)) } }
            }
        }
    } else if (search.submitted) {
        val searchListState = rememberLazyListState()
        LaunchedEffect(ui.sectionFocusPending, savedSearchVideoId, sortedSearchVideos.size) {
            if (ui.sectionFocusPending) savedSearchVideoId?.let { id -> sortedSearchVideos.indexOfFirst { it.uuid == id }.takeIf { it >= 0 }?.let { searchListState.scrollToItem(it) } }
        }
        LazyColumn(state = searchListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(sortedSearchVideos, key = { _, video -> video.uuid }) { index, video -> VideoListRow(video, m.server, m.watchProgressFraction(video), video.uuid in m.favoriteIds, m.isSubscribed(video), { actions.openVideoInQueue(video, sortedSearchVideos) }, { actions.toggleFavorite(video) }, { m.toggleSubscription(video) }, { actions.openChannelVideos(video) }, actions.updateFocusedVideoMenu, m.contextMenuHoldSeconds, onExcludeRecommendation = { m.excludeFromRecommendations(video) }, onMarkWatched = { m.markRecommendationWatched(video) }, firstCardFocusRequester = if (index == 0) ui.firstSearchResultFocusRequester else null, upFocusRequester = if (index == 0) ui.searchButtonFocusRequester else null, preferredFocusRequester = ui.searchRestoreFocusRequester, preferredFocusVideoId = savedSearchVideoId) }
            if (m.searchHasMore) item { TextButton(onClick = { m.searchSepia(m.query, search.sort, search.category.toIntOrNull(), when(search.duration){"medium"->240;"long"->1200;else->null}, when(search.duration){"short"->240;"medium"->1200;else->null}, search.liveOnly, page = m.searchNextPage, append = true) }, enabled = !m.isSearching, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.search_more)) } }
        }
    }
}
