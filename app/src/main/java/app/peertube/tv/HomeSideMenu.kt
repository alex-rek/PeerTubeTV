package app.peertube.tv

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeSideMenu(m: HomeModel, ui: HomeUiState, actions: HomeActions, menuFocusScope: CoroutineScope) {
    if (ui.menuExpanded) {
        Surface(
            modifier = Modifier.width(206.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(0.dp)
        ) {
          Column(Modifier.fillMaxSize().focusGroup().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(77.dp))
            listOf("home" to R.string.nav_home, "popular" to R.string.nav_popular, "recommendations" to R.string.nav_recommendations, "live" to R.string.nav_live, "subscriptions" to R.string.nav_subscriptions, "channels" to R.string.nav_channels, "instances" to R.string.nav_instances, "history" to R.string.nav_history, "favorites" to R.string.nav_favorites, "settings" to R.string.nav_settings).filter { (key, _) -> key == "home" || key == "settings" || key !in m.hiddenSections }.forEach { (key, label) ->
                val isSelected = if (key == "settings") (m.tab == "settings" || ui.settingsOverlay) else m.tab == key
                val selectedStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = if (isSelected) 20.sp else 16.sp, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
                var navFocused by remember(key) { mutableStateOf(false) }
                val itemFocusRequester = remember(key) { FocusRequester() }
                ui.expandedMenuFocusRequesters[key] = itemFocusRequester
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
                NavigationDrawerItem(label = { Text(stringResource(label), style = selectedStyle.copy(fontSize = if (isSelected) 18.sp else 15.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = if (navFocused) Modifier.basicMarquee(velocity = 30.dp * m.tickerSpeed) else Modifier) }, selected = isSelected, icon = { Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }, shape = RectangleShape, colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color.Transparent, unselectedContainerColor = Color.Transparent, selectedTextColor = MaterialTheme.colorScheme.onSurface, selectedIconColor = MaterialTheme.colorScheme.primary), onClick = { actions.navigateToSection(key, true) }, modifier = Modifier.fillMaxWidth().height(38.dp).focusRequester(itemFocusRequester).scale(if (navFocused) 1.06f else 1f).onFocusChanged {
                    navFocused = it.isFocused
                    if (it.isFocused) {
                        ui.focusedMenuSectionKey = key
                        ui.pendingMenuFocus[0]?.second?.cancel()
                        ui.pendingMenuFocus[0] = key to menuFocusScope.launch {
                            delay(220)
                            if (ui.menuExpanded && ui.pendingMenuFocus[0]?.first == key) {
                                ui.pendingMenuFocus[0] = null
                                actions.navigateToSection(key, false)
                            }
                        }
                    } else if (ui.pendingMenuFocus[0]?.first == key) {
                        ui.pendingMenuFocus[0]?.second?.cancel()
                        ui.pendingMenuFocus[0] = null
                    }
                }.then(if (key == "home") Modifier.focusRequester(ui.menuFocusRequester) else Modifier))
            }
          }
        }
    } else if (m.miniSectionRailEnabled) {
        val railItems = listOf("home" to R.string.nav_home, "popular" to R.string.nav_popular, "recommendations" to R.string.nav_recommendations, "live" to R.string.nav_live, "subscriptions" to R.string.nav_subscriptions, "channels" to R.string.nav_channels, "instances" to R.string.nav_instances, "history" to R.string.nav_history, "favorites" to R.string.nav_favorites, "settings" to R.string.nav_settings).filter { (key, _) -> key == "home" || key == "settings" || key !in m.hiddenSections }
        Surface(Modifier.width(60.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxSize().padding(top = 77.dp).focusGroup(), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                railItems.forEach { (key, label) ->
                    val isSelected = if (key == "settings") (m.tab == "settings" || ui.settingsOverlay) else m.tab == key
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
                    ui.collapsedMenuFocusRequesters[key] = railFocusRequester
                    IconButton(onClick = { actions.navigateToSection(key, false) }, modifier = Modifier.size(44.dp).focusRequester(railFocusRequester)
                        .onFocusChanged {
                            railFocused = it.isFocused
                            ui.collapsedMenuItemFocused = it.isFocused
                            if (it.isFocused) {
                                ui.focusedMenuSectionKey = key
                                ui.pendingMenuFocus[0]?.second?.cancel()
                                ui.pendingMenuFocus[0] = key to menuFocusScope.launch {
                                    delay(220)
                                    if (!ui.menuExpanded && ui.focusedMenuSectionKey == key && ui.pendingMenuFocus[0]?.first == key) {
                                        ui.pendingMenuFocus[0] = null
                                        actions.navigateToSection(key, false)
                                    }
                                }
                            } else if (ui.pendingMenuFocus[0]?.first == key) {
                                ui.pendingMenuFocus[0]?.second?.cancel()
                                ui.pendingMenuFocus[0] = null
                            }
                        }
                        .background(if (isSelected || railFocused) MaterialTheme.colorScheme.primary.copy(alpha = if (railFocused) .24f else .12f) else Color.Transparent, RoundedCornerShape(12.dp))) {
                        Icon(icon, contentDescription = stringResource(label), tint = if (isSelected || railFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
