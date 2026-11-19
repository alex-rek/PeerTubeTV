package app.peertube.tv

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsDashboard(
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
    val uiLocale = currentLocale()
    val recommendationLanguageOptions = remember(uiLocale) {
        listOf("country" to "") + Locale.getISOLanguages().distinct().map { code ->
            code to Locale(code).getDisplayLanguage(uiLocale).takeIf(String::isNotBlank).orEmpty()
        }.filter { it.second.isNotBlank() }.sortedBy { it.second.lowercase(uiLocale) }
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
                                val recommendationLanguageLabel = if (model.recommendationLanguage == "country") stringResource(R.string.recommendations_language_country) else Locale.forLanguageTag(model.recommendationLanguage).getDisplayLanguage(currentLocale())
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
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.mini_section_rail), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(model.miniSectionRailEnabled, model::updateMiniSectionRailEnabled)
                                }
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
