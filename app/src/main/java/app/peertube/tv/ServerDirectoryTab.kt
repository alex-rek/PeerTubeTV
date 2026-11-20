package app.peertube.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.ServerDirectoryTab(m: HomeModel, ui: HomeUiState) {
    Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(value = m.server, onValueChange = { m.server = it }, label = { Text(stringResource(R.string.server_hint)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { ui.instancesConnectFocusRequester.requestFocus(); true } else false }.focusProperties { down = ui.instancesConnectFocusRequester }.focusRequester(ui.serverFieldFocusRequester), singleLine = true)
    Button(onClick = { m.save(); m.loadHomePage(); m.tab = "home" }, modifier = Modifier.padding(top = 8.dp).focusRequester(ui.instancesConnectFocusRequester).focusRequester(ui.sectionFirstFocusRequester).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { ui.checkAllServersFocusRequester.requestFocus(); true } else false }.focusProperties { up = ui.serverFieldFocusRequester; down = ui.checkAllServersFocusRequester }) { Text(stringResource(R.string.connect)) }
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
                            .focusRequester(ui.checkAllServersFocusRequester)
                            .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { ui.firstServerConnectFocusRequester.requestFocus(); true } else false }
                            .focusProperties { down = ui.firstServerConnectFocusRequester }
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
                            .focusProperties { down = ui.firstServerConnectFocusRequester }
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
                val connectTargetRequester = if (isFirstDiscovered) ui.firstServerConnectFocusRequester else connectServerRequester
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
                                    .focusProperties { if (isFirstDiscovered) up = ui.checkAllServersFocusRequester; down = checkServerRequester }
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
}
