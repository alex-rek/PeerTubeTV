package app.peertube.tv

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MediaHttpClientTest {
    @Test
    fun mediaClientAllowsLongTransfersButBoundsIdleReadsAndKeepsDnsPolicy() {
        val dns = object : okhttp3.Dns {
            override fun lookup(hostname: String) = listOf(java.net.InetAddress.getByName("203.0.113.10"))
        }
        val base = OkHttpClient.Builder().dns(dns).callTimeout(20, TimeUnit.SECONDS).build()

        val media = ApiFactory.buildMediaHttpClient(base)

        assertEquals(0, media.callTimeoutMillis.toLong())
        assertEquals(25_000, media.readTimeoutMillis)
        assertSame(dns, media.dns)
        assertTrue(media.followRedirects)
        assertFalse(media.followSslRedirects)
        assertEquals(20_000, base.callTimeoutMillis)
    }
}
