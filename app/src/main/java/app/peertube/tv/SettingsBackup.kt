package app.peertube.tv

import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.util.Locale
import java.text.SimpleDateFormat

internal fun createSettingsBackup(context: android.content.Context): String {
    val fileName = "PeerTubeTV-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(java.util.Date())}.zip"
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/PeerTubeTV")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create backup in Downloads")
        try {
            resolver.openOutputStream(uri)?.use { writeSettingsBackupZip(context, it) } ?: error("Backup output could not be opened")
            val published = android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            return "Downloads/PeerTubeTV/$fileName"
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
    val backupParent = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    val directory = java.io.File(backupParent, "PeerTubeTV")
    check(directory.exists() || directory.mkdirs()) { "Could not create backup directory" }
    val file = java.io.File(directory, fileName)
    java.io.FileOutputStream(file).use { writeSettingsBackupZip(context, it) }
    return file.absolutePath
}

private fun writeSettingsBackupZip(context: android.content.Context, output: java.io.OutputStream) {
    val values = context.getSharedPreferences("peertube", 0).all
    val json = com.google.gson.JsonObject()
    values.filterKeys { !it.startsWith("proxy", ignoreCase = true) }.forEach { (key, value) ->
        val item = com.google.gson.JsonObject()
        val type = when (value) {
            is String -> "string"
            is Boolean -> "boolean"
            is Int -> "int"
            is Long -> "long"
            is Float -> "float"
            is Set<*> -> "string_set"
            else -> return@forEach
        }
        item.addProperty("type", type)
        item.add("value", PeerTubeGson.instance.toJsonTree(value))
        json.add(key, item)
    }
    java.util.zip.ZipOutputStream(output).use { zip ->
        zip.putNextEntry(java.util.zip.ZipEntry("settings.json"))
        zip.write(PeerTubeGson.instance.toJson(json).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

internal fun restoreSettingsBackup(context: android.content.Context, uri: android.net.Uri) {
    val settingsJson = context.contentResolver.openInputStream(uri)?.use { input ->
        java.util.zip.ZipInputStream(input).use { zip ->
            val entry = zip.nextEntry
            require(entry?.name == "settings.json" && !entry.isDirectory) { "Backup does not contain settings.json" }
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BACKUP_SETTINGS_BYTES) { "Backup settings file is too large" }
                output.write(buffer, 0, count)
            }
            require(zip.nextEntry == null) { "Backup contains unexpected files" }
            output.toString(Charsets.UTF_8.name())
        }
    } ?: error("Backup input could not be opened")
    require(settingsJson.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_SETTINGS_BYTES) { "Backup settings file is too large" }
    val json = com.google.gson.JsonParser.parseString(settingsJson).asJsonObject
    val validated = SettingsBackupCodec.decode(json)
    val editor = context.getSharedPreferences("peertube", 0).edit()
    validated.forEach { (key, value) ->
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
        }
    }
    check(editor.commit()) { "Settings backup could not be restored" }
}

private const val MAX_BACKUP_SETTINGS_BYTES = 512 * 1024
