package app.peertube.tv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

data class VideoSubscription(val name: String, val host: String, val displayName: String)

internal fun normalizeVideoSubscription(subscription: VideoSubscription): VideoSubscription? {
    val name = subscription.name?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,200}")) } ?: return null
    val host = subscription.host?.trim()?.removePrefix("https://")?.removePrefix("http://")
        ?.substringBefore('/')?.trimEnd('.')?.takeIf { runCatching { accountServerOrigin("https://$it"); true }.getOrDefault(false) } ?: return null
    return subscription.copy(name = name, host = host, displayName = subscription.displayName?.takeIf(String::isNotBlank) ?: name)
}
