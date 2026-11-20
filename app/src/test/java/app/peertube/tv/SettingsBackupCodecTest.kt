package app.peertube.tv

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsBackupCodecTest {
    @Test
    fun decodesKnownKeysWithTheirDeclaredTypes() {
        val json = JsonParser.parseString(
            """{"server":{"type":"string","value":"https://video.example"},"gridMode":{"type":"boolean","value":true},"playerVolume":{"type":"int","value":80},"lastServerDiscoveryAt":{"type":"long","value":1234567890123},"uiScale":{"type":"float","value":1.1}}"""
        ).asJsonObject

        val values = SettingsBackupCodec.decode(json)

        assertEquals("https://video.example", values["server"])
        assertEquals(true, values["gridMode"])
        assertEquals(80, values["playerVolume"])
        assertEquals(1234567890123L, values["lastServerDiscoveryAt"])
        assertEquals(1.1f, values["uiScale"])
    }

    @Test
    fun restoresPlayerQualityAndInterfacePreferences() {
        val json = JsonParser.parseString(
            """{"playerVideoProfile":{"type":"int","value":720},"playerVideoCodec":{"type":"string","value":"video/avc"},"playerAutoFrameRate":{"type":"boolean","value":true},"searchTimeoutMs":{"type":"long","value":8000},"subtitleBackgroundOpacity":{"type":"float","value":0.5}}"""
        ).asJsonObject

        val values = SettingsBackupCodec.decode(json)

        assertEquals(720, values["playerVideoProfile"])
        assertEquals("video/avc", values["playerVideoCodec"])
        assertEquals(true, values["playerAutoFrameRate"])
        assertEquals(8000L, values["searchTimeoutMs"])
    }

    @Test
    fun exportsOnlyKeysThatCanBeRestored() {
        assertEquals(true, SettingsBackupCodec.isBackedUp("playerVideoProfile"))
        assertEquals(false, SettingsBackupCodec.isBackedUp("proxyPassword"))
        assertEquals(false, SettingsBackupCodec.isBackedUp("someFutureKey"))
    }

    @Test
    fun rejectsUnknownAndProxyKeysBeforeApplyingAnything() {
        val unknown = JsonParser.parseString("""{"admin":{"type":"boolean","value":true}}""").asJsonObject
        val secret = JsonParser.parseString("""{"proxyPassword":{"type":"string","value":"secret"}}""").asJsonObject

        assertThrows(IllegalStateException::class.java) { SettingsBackupCodec.decode(unknown) }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.decode(secret) }
    }

    @Test
    fun rejectsMismatchedTypesAndMalformedEntries() {
        val mismatch = JsonParser.parseString("""{"gridMode":{"type":"string","value":"true"}}""").asJsonObject
        val malformed = JsonParser.parseString("""{"server":{"type":"string","value":null}}""").asJsonObject

        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.decode(mismatch) }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.decode(malformed) }
    }
}
