package app.peertube.tv

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredDataNormalizationTest {
    @Test
    fun legacyServerWithNullCollectionsCanBeMergedSafely() {
        val legacy = Gson().fromJson(
            """{"host":"EXAMPLE.org","discoveredFrom":null,"following":null,"followers":null,"languages":null}""",
            DiscoveredServer::class.java
        )

        val normalized = normalizeDiscoveredServer(legacy)!!

        assertEquals("example.org", normalized.host)
        assertTrue(normalized.discoveredFrom.isEmpty())
        assertTrue(normalized.following.isEmpty())
        assertTrue(normalized.followers.isEmpty())
        assertTrue(normalized.languages.isEmpty())
        assertEquals("unknown", normalized.apiStatus)
    }

    @Test
    fun invalidServerAndSubscriptionHostsAreIgnored() {
        val invalidServer = Gson().fromJson("""{"host":"localhost"}""", DiscoveredServer::class.java)
        val invalidSubscription = Gson().fromJson(
            """{"name":"channel","host":"/relative/path","displayName":null}""",
            VideoSubscription::class.java
        )

        assertEquals(null, normalizeDiscoveredServer(invalidServer))
        assertEquals(null, normalizeVideoSubscription(invalidSubscription))
    }
}
