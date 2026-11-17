package app.peertube.tv

import java.net.URI

internal val homePageDisplayModes = listOf("recommended", "interests", "server", "channel")

internal data class HomeChannelAddress(val name: String, val host: String) {
    val origin: String get() = accountServerOrigin("https://$host")
    val handle: String get() = "$name@${URI(origin).authority}"
}

internal fun parseHomeChannelAddress(value: String): HomeChannelAddress {
    val input = value.trim()
    require(input.isNotBlank()) { "Channel address is empty" }
    val url = runCatching { URI(input) }.getOrNull()
    val handle = if (url != null && url.scheme != null) {
        require(url.scheme.equals("https", true) && url.userInfo == null && url.rawQuery == null && url.rawFragment == null)
        val parts = url.path.split('/').filter(String::isNotBlank)
        require(parts.size == 2 && parts[0] in setOf("c", "video-channels"))
        "${parts[1]}@${url.rawAuthority}"
    } else input
    val normalized = validSubscriptionHandle(handle)
    return HomeChannelAddress(normalized.substringBeforeLast('@'), normalized.substringAfterLast('@'))
}
