package app.peertube.tv

import android.content.Context
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class GitHubRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String? = null
)

object GitHubUpdates {
    private const val RELEASE_URL = "https://api.github.com/repos/alex-rek/PeerIcheTV/releases/latest"
    private const val MAX_RELEASE_JSON_BYTES = 1_000_000
    private const val MAX_APK_BYTES = 250L * 1024 * 1024
    private val allowedDownloadHosts = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    fun latestRelease(context: Context, installedVersion: String, includeCurrent: Boolean = false): GitHubRelease? {
        val cacheBody = File(context.cacheDir, "github-latest-release.json")
        val cacheEtag = File(context.cacheDir, "github-latest-release.etag")
        val connection = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PeerIcheTV-Android")
            cacheEtag.takeIf { it.isFile }?.readText()?.takeIf(String::isNotBlank)?.let { setRequestProperty("If-None-Match", it) }
        }
        val json = try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> cacheBody.takeIf { it.isFile }?.readText() ?: return null
                in 200..299 -> readBounded(connection.inputStream, MAX_RELEASE_JSON_BYTES, 20_000).also { body ->
                    cacheBody.writeText(body)
                    connection.getHeaderField("ETag")?.let(cacheEtag::writeText)
                }
                else -> error("GitHub returned HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
        val release = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        if (release.get("draft")?.asBoolean != false || release.get("prerelease")?.asBoolean != false) return null
        val version = release.get("tag_name")?.asString.orEmpty().removePrefix("v").removePrefix("V")
        if (version.isBlank() || (!includeCurrent && compareVersions(version, installedVersion) <= 0)) return null
        val assets = release.getAsJsonArray("assets") ?: return null
        val apkAssets = assets.map { it.asJsonObject }
            .filter { it.get("name")?.asString?.endsWith(".apk", ignoreCase = true) == true }
        val asset = apkAssets.firstOrNull { it.get("name")?.asString?.equals("PeerIcheTV.apk", ignoreCase = true) == true }
            ?: apkAssets.firstOrNull { it.get("name")?.asString?.contains("universal", ignoreCase = true) == true }
            ?: apkAssets.firstOrNull { it.get("name")?.asString?.equals("PeerTubeTV.apk", ignoreCase = true) == true }
            ?: apkAssets.firstOrNull { it.get("name")?.asString?.contains("armeabi-v7a", ignoreCase = true) == true }
            ?: apkAssets.firstOrNull()
            ?: return null
        val downloadUrl = asset.get("browser_download_url")?.asString?.takeIf(::isAllowedDownloadUrl) ?: return null
        val digest = normalizeSha256(asset.get("digest")?.takeUnless { it.isJsonNull }?.asString)
        return GitHubRelease(version, release.get("body")?.asString.orEmpty(), downloadUrl, digest)
    }

    fun downloadApk(context: Context, downloadUrl: String, expectedSha256: String? = null): File {
        require(isAllowedDownloadUrl(downloadUrl)) { "The download URL is not an approved GitHub asset URL" }
        val destination = File(context.cacheDir, "peertubetv-update.apk")
        val connection = openFollowingAllowedRedirects(downloadUrl, connectTimeoutMs = 15_000, readTimeoutMs = 30_000)
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_APK_BYTES) { "The downloaded APK is too large" }
                    output.write(buffer, 0, count)
                }
            } }
            if (destination.length() == 0L) error(context.getString(R.string.update_empty_file))
            normalizeSha256(expectedSha256)?.let { expected ->
                val actual = sha256(destination)
                if (!actual.equals(expected, ignoreCase = true)) error(context.getString(R.string.update_checksum_mismatch))
            }
            return destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    internal fun isAllowedDownloadUrl(value: String): Boolean {
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) && uri.host?.lowercase(Locale.ROOT) in allowedDownloadHosts && uri.rawUserInfo == null
    }

    internal fun normalizeSha256(value: String?): String? {
        val digest = value?.trim()?.removePrefix("sha256:")?.lowercase(Locale.ROOT) ?: return null
        return digest.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    }

    internal fun compareVersions(left: String, right: String): Int {
        val a = left.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val b = right.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun openFollowingAllowedRedirects(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpURLConnection {
        var current = URL(url)
        repeat(6) { attempt ->
            require(isAllowedDownloadUrl(current.toExternalForm())) { "GitHub redirected to an unapproved download URL" }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PeerIcheTV-Android")
            }
            val code = connection.responseCode
            if (code !in 300..399) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(attempt < 5 && !location.isNullOrBlank()) { "Too many or invalid GitHub redirects" }
            current = URL(current, location)
        }
        error("Too many GitHub redirects")
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int, timeoutMs: Long): String {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            while (true) {
                if (System.nanoTime() > deadline) error("GitHub release request timed out")
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxBytes) { "GitHub release response is too large" }
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }
}
