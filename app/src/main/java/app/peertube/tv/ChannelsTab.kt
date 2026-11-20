package app.peertube.tv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.ChannelsTab(m: HomeModel, ui: HomeUiState) {
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
                            Modifier.weight(0.40f).then(if (channel == accountChannels.firstOrNull()) Modifier.focusRequester(ui.sectionFirstFocusRequester) else Modifier).clickable { ui.sectionFocusPending = true; m.loadChannelVideos(channel.name, channel.host) },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            if (channel.host.isBlank()) stringResource(R.string.server_unknown) else channel.host,
                            Modifier.weight(0.34f).then(if (channel.host.isNotBlank()) Modifier.clickable { ui.sectionFocusPending = true; m.loadServerChannels(channel.host) } else Modifier),
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
                        Text(channel.displayName, Modifier.weight(0.40f).then(if (accountChannels.isEmpty() && channel == localChannels.firstOrNull()) Modifier.focusRequester(ui.sectionFirstFocusRequester) else Modifier).clickable { ui.sectionFocusPending = true; m.loadChannelVideos(channel.name, channel.host) },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.ServerChannelsTab(m: HomeModel, ui: HomeUiState) {
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
                    onClick = { channel.name?.takeIf(String::isNotBlank)?.let { name -> ui.sectionFocusPending = true; m.loadChannelVideos(name, channel.host ?: m.activeServerChannelsHost) } },
                    modifier = Modifier.fillMaxWidth().then(if (channel == m.serverChannels.firstOrNull()) Modifier.focusRequester(ui.sectionFirstFocusRequester) else Modifier),
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
}
