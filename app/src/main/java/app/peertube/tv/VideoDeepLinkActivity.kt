package app.peertube.tv

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class ExternalVideoLink(val uuid: String, val server: String)

internal fun parseExternalVideoLink(value: String?, isSafeHost: (String) -> Boolean): ExternalVideoLink? {
    val link = runCatching { URI(value ?: return null) }.getOrNull() ?: return null
    if (link.scheme != "peertubetv" || link.host != "video" || link.rawUserInfo != null) return null
    val parameters = linkedMapOf<String, MutableList<String>>()
    link.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).forEach { part ->
        val separator = part.indexOf('=')
        val key = runCatching { URLDecoder.decode(if (separator < 0) part else part.substring(0, separator), StandardCharsets.UTF_8.name()) }.getOrNull() ?: return null
        val field = runCatching { URLDecoder.decode(if (separator < 0) "" else part.substring(separator + 1), StandardCharsets.UTF_8.name()) }.getOrNull() ?: return null
        parameters.getOrPut(key) { mutableListOf() } += field
    }
    val uuid = parameters["uuid"]?.singleOrNull()?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
    val server = parameters["server"]?.singleOrNull()?.takeIf(String::isNotBlank) ?: return null
    val serverUri = runCatching { URI(server) }.getOrNull() ?: return null
    val host = serverUri.host ?: return null
    if (serverUri.scheme != "https" || serverUri.rawUserInfo != null || serverUri.rawQuery != null || serverUri.rawFragment != null || serverUri.rawPath !in listOf("", "/") || !isSafeHost(host)) return null
    return ExternalVideoLink(uuid, serverUri.toASCIIString().trimEnd('/'))
}

/** Public entry point for TV launcher recommendations; forwards only a validated video identity. */
class VideoDeepLinkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val link = withContext(Dispatchers.IO) {
                parseExternalVideoLink(intent?.dataString) { SafeNetworkTarget.isSafeHttpsHost(it) }
            }
            if (link != null && !isFinishing) {
                startActivity(Intent(this@VideoDeepLinkActivity, PlayerActivity::class.java).apply {
                    putExtra("uuid", link.uuid)
                    putExtra("server", link.server)
                })
            }
            finish()
        }
    }
}
