package app.peertube.tv

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TranslatedCue(val startMs: Long, val endMs: Long, val text: String)

private val translationLanguages = listOf(
    "af" to "Afrikaans", "ar" to "العربية", "be" to "Беларуская", "bg" to "Български",
    "bn" to "বাংলা", "ca" to "Català", "cs" to "Čeština", "cy" to "Cymraeg", "da" to "Dansk",
    "de" to "Deutsch", "el" to "Ελληνικά", "en" to "English", "eo" to "Esperanto", "es" to "Español",
    "et" to "Eesti", "fa" to "فارسی", "fi" to "Suomi", "fr" to "Français", "ga" to "Gaeilge",
    "gl" to "Galego", "gu" to "ગુજરાતી", "he" to "עברית", "hi" to "हिन्दी", "hr" to "Hrvatski",
    "ht" to "Kreyòl ayisyen", "hu" to "Magyar", "id" to "Bahasa Indonesia", "is" to "Íslenska",
    "it" to "Italiano", "ja" to "日本語", "ka" to "ქართული", "kn" to "ಕನ್ನಡ", "ko" to "한국어",
    "lt" to "Lietuvių", "lv" to "Latviešu", "mk" to "Македонски", "mr" to "मराठी", "ms" to "Bahasa Melayu",
    "mt" to "Malti", "nl" to "Nederlands", "no" to "Norsk", "pl" to "Polski", "pt" to "Português",
    "ro" to "Română", "ru" to "Русский", "sk" to "Slovenčina", "sl" to "Slovenščina", "sq" to "Shqip",
    "sv" to "Svenska", "sw" to "Kiswahili", "ta" to "தமிழ்", "te" to "తెలుగు", "th" to "ไทย",
    "tl" to "Filipino", "tr" to "Türkçe", "uk" to "Українська", "ur" to "اردو", "vi" to "Tiếng Việt",
    "zh" to "中文"
)

fun subtitleTranslationLanguageOptions(): List<Pair<String, String>> = translationLanguages
fun translationLanguageName(code: String): String = translationLanguages.firstOrNull { it.first == code }?.second ?: code.uppercase()
fun subtitleTranslationLanguageCodes(): List<String> = translationLanguages.map { it.first }

fun downloadableSubtitleTranslationLanguages(): List<Pair<String, String>> = translationLanguages.filter { (code, _) ->
    com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(code) != null
}

fun refreshDownloadedTranslationLanguages(onResult: (Result<Set<String>>) -> Unit) {
    RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
        .addOnSuccessListener { models -> onResult(Result.success(models.map { it.language }.toSet())) }
        .addOnFailureListener { onResult(Result.failure(it)) }
}

fun downloadTranslationLanguage(language: String, onResult: (Result<Unit>) -> Unit) {
    val model = TranslateRemoteModel.Builder(language).build()
    RemoteModelManager.getInstance().download(model, DownloadConditions.Builder().build())
        .addOnSuccessListener { onResult(Result.success(Unit)) }
        .addOnFailureListener { onResult(Result.failure(it)) }
}

fun deleteTranslationLanguage(language: String, onResult: (Result<Unit>) -> Unit) {
    val model = TranslateRemoteModel.Builder(language).build()
    RemoteModelManager.getInstance().deleteDownloadedModel(model)
        .addOnSuccessListener { onResult(Result.success(Unit)) }
        .addOnFailureListener { onResult(Result.failure(it)) }
}

fun interfaceSubtitleLanguage(uiLanguage: String): String {
    val language = uiLanguage.takeIf { it.isNotBlank() && it != "auto" }?.substringBefore('-')
        ?: run {
            val configuration = android.content.res.Resources.getSystem().configuration
            @Suppress("DEPRECATION")
            val systemLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) configuration.locales[0] else configuration.locale
            systemLocale.language
        }
    return language.lowercase().takeIf { it in subtitleTranslationLanguageCodes() } ?: "en"
}

fun currentInterfaceTranslationLanguage(context: Context): String {
    val appLocale = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag().orEmpty()
    val configuration = context.resources.configuration
    @Suppress("DEPRECATION")
    val systemLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        configuration.locales[0]?.toLanguageTag().orEmpty()
    } else {
        configuration.locale?.toLanguageTag().orEmpty()
    }
    return interfaceSubtitleLanguage(appLocale.ifBlank { systemLocale })
}

private val metadataTranslationMemory = ConcurrentHashMap<String, String>()

private fun ensureTranslationModelsAvailable(
    translator: com.google.mlkit.nl.translate.Translator,
    sourceLanguage: String,
    targetLanguage: String
) {
    val downloadedLanguages = runCatching {
        Tasks.await(RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java), 20, TimeUnit.SECONDS)
            .map { it.language.lowercase() }.toSet()
    }.getOrNull()
    val source = sourceLanguage.lowercase()
    val target = targetLanguage.lowercase()
    if (downloadedLanguages == null || source !in downloadedLanguages || target !in downloadedLanguages) {
        Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()), 120, TimeUnit.SECONDS)
    }
}

/** Identifies and translates metadata on device; successful translations are persisted for reuse. */
suspend fun translateVideoMetadata(context: Context, text: String, targetLanguage: String): String = withContext(Dispatchers.IO) {
    val sourceText = text.trim()
    val target = targetLanguage.substringBefore('-').lowercase()
    if (sourceText.length < 8 || sourceText.isBlank() || target !in subtitleTranslationLanguageCodes()) return@withContext text

    val digest = MessageDigest.getInstance("SHA-256").digest(sourceText.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val cacheKey = "videoMetadataTranslation_${target}_$digest"
    metadataTranslationMemory[cacheKey]?.let { return@withContext it }
    val preferences = context.applicationContext.getSharedPreferences("peertube", Context.MODE_PRIVATE)
    preferences.getString(cacheKey, null)?.let {
        metadataTranslationMemory[cacheKey] = it
        return@withContext it
    }

    val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build()
    )
    val sourceCode = runCatching { Tasks.await(languageIdentifier.identifyLanguage(sourceText.take(1_000))) }.getOrNull()
    if (sourceCode.isNullOrBlank() || sourceCode == "und") return@withContext text
    val source = com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(sourceCode)
        ?: return@withContext text
    if (source == target) return@withContext text
    val destination = com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(target)
        ?: return@withContext text

    val translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(destination).build()
    )
    val translated = try {
        ensureTranslationModelsAvailable(translator, source, destination)
        translateMetadataInChunks(translator, sourceText)
    } catch (_: Exception) {
        return@withContext text
    } finally {
        translator.close()
    }
    if (translated.isBlank() || translated == sourceText) return@withContext text
    synchronized(metadataTranslationMemory) {
        if (preferences.all.keys.count { it.startsWith("videoMetadataTranslation_") } >= 500) {
            preferences.all.keys.firstOrNull { it.startsWith("videoMetadataTranslation_") }?.let(preferences.edit()::remove)
        }
        preferences.edit().putString(cacheKey, translated).apply()
        metadataTranslationMemory[cacheKey] = translated
    }
    translated
}

private suspend fun translateMetadataInChunks(translator: com.google.mlkit.nl.translate.Translator, text: String): String {
    val paragraphs = text.lines()
    val translatedParagraphs = paragraphs.map { paragraph ->
        if (paragraph.length <= 400) Tasks.await(translator.translate(paragraph))
        else paragraph.chunked(350).joinToString("") { Tasks.await(translator.translate(it)) }
    }
    return translatedParagraphs.joinToString("\n")
}

@Composable
fun TranslatedVideoMetadataText(
    text: String,
    targetLanguage: String = "",
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolvedTargetLanguage = targetLanguage.ifBlank { currentInterfaceTranslationLanguage(context) }
    var displayText by remember(text, resolvedTargetLanguage) { mutableStateOf(text) }
    LaunchedEffect(text, resolvedTargetLanguage) {
        displayText = text
        displayText = translateVideoMetadata(context, text, resolvedTargetLanguage)
    }
    Text(displayText, modifier = modifier, style = style, color = color, fontSize = fontSize, maxLines = maxLines, minLines = minLines, overflow = overflow)
}

/** Downloads the selected public caption file and translates its cues on device. */
fun translateSubtitleFile(
    context: Context,
    server: String,
    caption: VideoCaption,
    targetLanguage: String,
    onProgress: (Int, Int) -> Unit,
    onCuesLoaded: (List<TranslatedCue>) -> Unit,
    onBatchTranslated: (List<TranslatedCue>) -> Unit
): List<TranslatedCue> {
    val sourceLanguage = caption.language?.id?.substringBefore('-')?.lowercase()
        ?: error("The source subtitle language is unknown")
    val target = targetLanguage.substringBefore('-').lowercase()
    require(sourceLanguage != target) { "The selected subtitle is already in the target language" }
    val mlSource = com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(sourceLanguage)
        ?: error("Translation does not support source language: $sourceLanguage")
    val mlTarget = com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(target)
        ?: error("Translation does not support target language: $target")

    val rawPath = caption.fileUrl?.takeIf(String::isNotBlank)
        ?: caption.captionPath?.takeIf(String::isNotBlank)
        ?: error("Subtitle file URL is missing")
    val captionUrl = when {
        rawPath.startsWith("https://", true) -> rawPath
        rawPath.startsWith("//") -> "https:$rawPath"
        else -> server.trimEnd('/') + if (rawPath.startsWith('/')) rawPath else "/$rawPath"
    }
    val subtitleText = ApiFactory.safeHttpGet(captionUrl).use { response ->
        check(response.isSuccessful) { "Subtitle download failed: HTTP ${response.code}" }
        check(response.body?.contentLength()?.let { it <= 5_000_000 } != false) { "Subtitle file is too large" }
        val body = response.body?.string().orEmpty()
        check(body.isNotBlank() && body.length <= 5_000_000) { "Subtitle file is empty or too large" }
        body
    }
    val cues = parseSubtitleCues(subtitleText)
    check(cues.isNotEmpty()) { "No readable subtitle cues were found" }
    onCuesLoaded(cues)

    val translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(mlSource).setTargetLanguage(mlTarget).build()
    )
    try {
        ensureTranslationModelsAvailable(translator, mlSource, mlTarget)
        val result = ArrayList<TranslatedCue>(cues.size)
        var completedCues = 0
        subtitleCueBatches(cues).forEach { batch ->
            val translatedBatch = translateSubtitleCueBatch(translator, batch)
            val translatedCues = batch.mapIndexed { index, cue -> cue.copy(text = translatedBatch[index]) }
            result += translatedCues
            onBatchTranslated(translatedCues)
            completedCues += batch.size
            onProgress(completedCues, cues.size)
        }
        return result
    } finally {
        translator.close()
    }
}

private fun subtitleCueBatches(cues: List<TranslatedCue>): List<List<TranslatedCue>> {
    val batches = mutableListOf<List<TranslatedCue>>()
    var current = mutableListOf<TranslatedCue>()
    var currentLength = 0
    cues.forEach { cue ->
        val markedLength = cue.text.length + 18
        // One ML Kit request per batch is substantially faster than translating
        // each short caption separately. Keep a conservative payload size so
        // long captions do not turn into an oversized request.
        if (current.isNotEmpty() && (current.size >= 24 || currentLength + markedLength > 2_400)) {
            batches += current
            current = mutableListOf()
            currentLength = 0
        }
        current += cue
        currentLength += markedLength
    }
    if (current.isNotEmpty()) batches += current
    return batches
}

private fun translateSubtitleCueBatch(
    translator: com.google.mlkit.nl.translate.Translator,
    batch: List<TranslatedCue>
): List<String> {
    if (batch.size == 1) return listOf(Tasks.await(translator.translate(batch.single().text)))

    val markerPattern = Regex("⟦PTV_(\\d{3})⟧")
    val markedText = batch.mapIndexed { index, cue -> "⟦PTV_${index.toString().padStart(3, '0')}⟧ ${cue.text}" }
        .joinToString("\n")
    val translated = runCatching { Tasks.await(translator.translate(markedText)) }.getOrNull()
    val markers = translated?.let(markerPattern::findAll)?.toList().orEmpty()
    if (markers.size == batch.size && markers.map { it.groupValues[1].toIntOrNull() } == batch.indices.toList()) {
        return markers.mapIndexed { index, marker ->
            val start = marker.range.last + 1
            val end = markers.getOrNull(index + 1)?.range?.first ?: translated!!.length
            translated!!.substring(start, end).trim().trimStart(':', '-', '–', '—').trim()
                .ifBlank { Tasks.await(translator.translate(batch[index].text)) }
        }
    }
    return batch.map { Tasks.await(translator.translate(it.text)) }
}

private val timestampPattern = "(?:\\d{1,}:)?\\d{2}:\\d{2}[,.]\\d{1,3}|\\d{1,}:\\d{2}[,.]\\d{1,3}"
private val cueTiming = Regex("^($timestampPattern)\\s+-->\\s+($timestampPattern).*$")

private fun parseSubtitleCues(text: String): List<TranslatedCue> {
    val lines = text.removePrefix("\uFEFF").lines()
    val result = mutableListOf<TranslatedCue>()
    var index = 0
    while (index < lines.size) {
        val timingLine = lines[index].trim()
        val timing = cueTiming.matchEntire(timingLine)
        if (timing == null) { index++; continue }
        val start = timing.groupValues[1].toSubtitleMillis()
        val end = timing.groupValues[2].toSubtitleMillis()
        index++
        val cueText = mutableListOf<String>()
        while (index < lines.size && lines[index].isNotBlank()) {
            cueText += lines[index].trim().replace(Regex("<[^>]*>"), "")
            index++
        }
        val cleanText = cueText.joinToString(" ").trim()
        if (cleanText.isNotBlank() && end > start) result += TranslatedCue(start, end, cleanText)
    }
    return result
}

private fun String.toSubtitleMillis(): Long {
    val time = substringBefore(',').substringBefore('.')
    val fraction = substringAfterLast('.', substringAfterLast(',', "0")).take(3).padEnd(3, '0').toLongOrNull() ?: 0L
    val parts = time.split(':').mapNotNull(String::toLongOrNull)
    if (parts.size !in 2..3) return 0L
    val hours = if (parts.size == 3) parts[0] else 0L
    val minutes = if (parts.size == 3) parts[1] else parts[0]
    val seconds = parts.last()
    val millis = fraction
    return ((hours * 60 + minutes) * 60 + seconds) * 1_000 + millis
}
