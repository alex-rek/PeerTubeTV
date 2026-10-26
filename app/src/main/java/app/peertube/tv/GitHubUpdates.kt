package app.peertube.tv

import android.content.Context
import com.google.gson.JsonParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    val version: String,
    val notes: String,
    val apkUrl: String
)

object GitHubUpdates {
    private const val RELEASE_URL = "https://api.github.com/repos/alex-rek/PeerTubeTV/releases/latest"

    fun latestRelease(installedVersion: String): GitHubRelease? {
        val connection = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PeerTubeTV-Android")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val release = connection.inputStream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            if (release.get("draft")?.asBoolean != false || release.get("prerelease")?.asBoolean != false) return null
            val tag = release.get("tag_name")?.asString.orEmpty()
            val version = tag.removePrefix("v").removePrefix("V")
            if (compareVersions(version, installedVersion) <= 0) return null
            val assets = release.getAsJsonArray("assets") ?: return null
            val asset = assets.map { it.asJsonObject }
                .firstOrNull { it.get("name")?.asString?.equals("PeerTubeTV.apk", ignoreCase = true) == true }
                ?: assets.map { it.asJsonObject }.firstOrNull { it.get("name")?.asString?.endsWith(".apk", true) == true }
                ?: return null
            val downloadUrl = asset.get("browser_download_url")?.asString ?: return null
            return GitHubRelease(version, release.get("body")?.asString.orEmpty(), downloadUrl)
        } finally {
            connection.disconnect()
        }
    }

    fun downloadApk(context: Context, downloadUrl: String): File {
        val destination = File(context.cacheDir, "peertubetv-update.apk")
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PeerTubeTV-Android")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            require(destination.length() > 0L) { "Загруженный файл пуст" }
            return destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
