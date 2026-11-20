package app.peertube.tv

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Strict schema for portable settings backups. Secrets and unknown/future keys must not be imported. */
internal object SettingsBackupCodec {
    private val schema: Map<String, String> = buildMap {
        listOf("afterVideoAction", "availabilityFilter", "bufferProfile", "exitMode", "homeChannel", "homeContentMode", "homeServer", "launchTab", "playerAudioFormat", "playerAudioLanguage", "playerClockBackdrop", "playerClockPosition", "playerClockSize", "playerVideoCodec", "preferredSubtitleLanguage", "recommendationBasis", "recommendationLanguage", "searchCountryCode", "searchExecutionMode", "searchSourceOrder", "selectedTheme", "sensitiveDisplayMode", "server", "serverCatalogJsonUrl", "serverSortMode", "subtitleColor", "subtitleStyle", "subtitleTranslationLanguage", "videoSortMode", "watchedRecommendationThreshold").forEach { put(it, "string") }
        listOf("clock24Hour", "countryInitialized", "discoveryAutomatic", "discoveryBundledEnabled", "discoveryFederationEnabled", "discoveryJoinEnabled", "discoveryJsonEnabled", "discoveryWifiOnly", "exitModeDefaultInitialized", "exitModeUserSelected", "gridMode", "hideWatchedRecommendations", "miniSectionRailEnabled", "navigationSounds", "peerSeekEnabled", "playerAutoFrameRate", "playerDefaults20261021Applied", "rememberSubtitleByChannel", "resumePlayback", "resumePlaybackDefaultMigrated", "searchIncludeSensitive", "serverAvailabilitySort", "serverSeedLoaded", "showPlayerClock", "showSearchIcon", "subtitleBackgroundEnabled", "subtitlesEnabledByDefault", "subtitleTranslationEnabledByDefault", "subtitleTranslationLanguageCustom", "translateVideoMetadataEnabled", "useBuiltInTvKeyboard").forEach { put(it, "boolean") }
        listOf("discoveryMaxDepth", "discoveryMaxNew", "discoveryRefreshDays", "playerOverlayDurationSeconds", "playerVideoProfile", "playerVolume", "searchMaxParallel", "searchSufficientResults", "seekStepSeconds", "serverSeedVersion", "videoTitleLines").forEach { put(it, "int") }
        listOf("lastServerDiscoveryAt", "searchTimeoutMs").forEach { put(it, "long") }
        listOf("contextMenuHoldSeconds", "gridScale", "subtitleBackgroundOpacity", "subtitleBottomOffset", "subtitleHorizontalPosition", "subtitleSize", "tickerSpeed", "uiScale").forEach { put(it, "float") }
        listOf("customSearchIndexes", "discoveredServers", "excludedRecommendationIds", "favorites", "hiddenSections", "history", "historyViewedAt", "homeInterests", "lastSearchQuery", "lastSearchVideos", "searchEnabledSources", "subscriptions", "watchedVideoIds", "watchProgressMs").forEach { put(it, "string") }
    }

    /** Whether a stored preference belongs in a backup; export uses this so every backup can be restored. */
    fun isBackedUp(key: String): Boolean = key in schema

    fun decode(json: JsonObject): Map<String, Any> {
        val restored = LinkedHashMap<String, Any>()
        json.entrySet().forEach { (key, element) ->
            require(!key.startsWith("proxy", ignoreCase = true)) { "Proxy settings are not included in backups" }
            val expectedType = schema[key] ?: error("Unknown setting key: $key")
            require(element.isJsonObject) { "Invalid backup entry: $key" }
            val item = element.asJsonObject
            require(item.keySet() == setOf("type", "value")) { "Invalid backup entry: $key" }
            require(item.get("type").asString == expectedType) { "Invalid setting type: $key" }
            val value = item.get("value")
            restored[key] = parseValue(expectedType, value, key)
        }
        return restored
    }

    private fun parseValue(type: String, value: JsonElement, key: String): Any {
        require(value.isJsonPrimitive) { "Invalid setting value: $key" }
        val primitive = value.asJsonPrimitive
        return when (type) {
            "string" -> { require(primitive.isString); primitive.asString }
            "boolean" -> { require(primitive.isBoolean); primitive.asBoolean }
            "int" -> { require(primitive.isNumber); primitive.asInt }
            "long" -> { require(primitive.isNumber); primitive.asLong }
            "float" -> { require(primitive.isNumber); primitive.asFloat.also { require(it.isFinite()) } }
            else -> error("Invalid setting type: $key")
        }
    }
}
