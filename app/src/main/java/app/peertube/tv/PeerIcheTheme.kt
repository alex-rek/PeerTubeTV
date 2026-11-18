package app.peertube.tv

import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** All application-wide theme choices and semantic colors live here. */
@Immutable
data class PeerIcheTheme(
    val id: String,
    val colors: ColorScheme,
    @DrawableRes val logoResource: Int,
    val focusedVideoBackground: Color,
    val focusedVideoForeground: Color,
)

object PeerIcheThemes {
    const val GRAPHITE = "graphite"
    const val LIGHT = "light"
    const val PEERTUBE = "peertube"
    const val BLUE = "blue"

    val all = listOf(GRAPHITE, LIGHT, PEERTUBE, BLUE)

    private val graphite = PeerIcheTheme(
        GRAPHITE,
        darkColorScheme(
            primary = Color(0xFFFF7A18), onPrimary = Color(0xFF251100),
            primaryContainer = Color(0xFFFF963F), onPrimaryContainer = Color(0xFF2A1500),
            secondary = Color(0xFFFF963F), onSecondary = Color(0xFF2A1500),
            secondaryContainer = Color(0xFF49301F), onSecondaryContainer = Color(0xFFFFE2CD),
            background = Color(0xFF101114), onBackground = Color(0xFFF5F5F5),
            surface = Color(0xFF191B20), onSurface = Color(0xFFF5F5F5),
            surfaceVariant = Color(0xFF202329), onSurfaceVariant = Color(0xFFB0B4BC),
            outline = Color(0xFF626873), error = Color(0xFFFF6B6B), onError = Color(0xFF2B0000),
        ),
        R.drawable.peeriche_logo_dark,
        Color.White,
        Color(0xFF20242A),
    )

    private val light = PeerIcheTheme(
        LIGHT,
        lightColorScheme(
            primary = Color(0xFFF36B21), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE1D2), onPrimaryContainer = Color(0xFF4A1600),
            secondary = Color(0xFFD85C12), onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFE1D2), onSecondaryContainer = Color(0xFF4A1600),
            background = Color(0xFFF5F6F8), onBackground = Color(0xFF20242A),
            surface = Color.White, onSurface = Color(0xFF20242A),
            surfaceVariant = Color(0xFFECEFF3), onSurfaceVariant = Color(0xFF606874),
            outline = Color(0xFF858D98), error = Color(0xFFB3261E), onError = Color.White,
        ),
        R.drawable.peeriche_logo_light,
        Color(0xFFFFE1D2),
        Color(0xFF4A1600),
    )

    private val peerTube = PeerIcheTheme(
        PEERTUBE,
        darkColorScheme(
            primary = Color(0xFFF1680D), onPrimary = Color(0xFF2A1000),
            primaryContainer = Color(0xFFFF8C42), onPrimaryContainer = Color(0xFF321300),
            secondary = Color(0xFFFF8C42), onSecondary = Color(0xFF321300),
            secondaryContainer = Color(0xFF4A2E1D), onSecondaryContainer = Color(0xFFFFE2D0),
            background = Color(0xFF202020), onBackground = Color.White,
            surface = Color(0xFF303030), onSurface = Color.White,
            surfaceVariant = Color(0xFF383838), onSurfaceVariant = Color(0xFFC0C0C0),
            outline = Color(0xFF777777), error = Color(0xFFFF6B6B), onError = Color(0xFF2B0000),
        ),
        R.drawable.peeriche_logo_dark,
        Color(0xFFFF8C42),
        Color(0xFF321300),
    )

    private val blue = PeerIcheTheme(
        BLUE,
        darkColorScheme(
            primary = Color(0xFF339DFF), onPrimary = Color(0xFF001B33),
            primaryContainer = Color(0xFF64B5FF), onPrimaryContainer = Color(0xFF061C31),
            secondary = Color(0xFF64B5FF), onSecondary = Color(0xFF061C31),
            secondaryContainer = Color(0xFF263F5B), onSecondaryContainer = Color(0xFFD8E9FF),
            background = Color(0xFF101B2B), onBackground = Color(0xFFF5F9FF),
            surface = Color(0xFF192A40), onSurface = Color(0xFFF5F9FF),
            surfaceVariant = Color(0xFF223650), onSurfaceVariant = Color(0xFFB3C4D8),
            outline = Color(0xFF607B98), error = Color(0xFFFF8A80), onError = Color(0xFF2B0000),
        ),
        R.drawable.peeriche_logo_dark,
        Color.White,
        Color(0xFF20242A),
    )

    fun fromId(id: String?): PeerIcheTheme = when (id) {
        LIGHT -> light
        PEERTUBE -> peerTube
        BLUE -> blue
        else -> graphite
    }

    /** Maps older saved color schemes without touching any other preferences. */
    fun migrateId(id: String?): String = when (id) {
        "gray", GRAPHITE -> GRAPHITE
        LIGHT -> LIGHT
        "red" -> GRAPHITE
        "turquoise", BLUE -> BLUE
        PEERTUBE -> PEERTUBE
        else -> GRAPHITE
    }
}

val LocalPeerIcheTheme = staticCompositionLocalOf { PeerIcheThemes.fromId(PeerIcheThemes.GRAPHITE) }
