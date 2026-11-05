package app.peertube.tv

import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSafetyTest {
    private fun ipv6(vararg bytes: Int) = Inet6Address.getByAddress(null, ByteArray(16) { bytes.getOrElse(it) { 0 }.toByte() }, -1)

    @Test
    fun blocksPrivateIpv4RangesAndAllowsPublicIpv4() {
        assertFalse(SafeNetworkTarget.isPublicAddress(InetAddress.getByName("10.1.2.3")))
        assertFalse(SafeNetworkTarget.isPublicAddress(InetAddress.getByName("100.64.1.2")))
        assertFalse(SafeNetworkTarget.isPublicAddress(InetAddress.getByName("192.168.1.5")))
        assertTrue(SafeNetworkTarget.isPublicAddress(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun blocksIpv6LocalDocumentationMappedNat64AndSixToFourPrivateTargets() {
        assertFalse(SafeNetworkTarget.isPublicAddress(ipv6(0xfc))) // fc00::/7 ULA
        assertFalse(SafeNetworkTarget.isPublicAddress(ipv6(0x20, 0x01, 0x0d, 0xb8))) // 2001:db8::/32
        assertFalse(SafeNetworkTarget.isPublicAddress(ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff, 10, 0, 0, 1))) // ::ffff:10.0.0.1
        assertFalse(SafeNetworkTarget.isPublicAddress(ipv6(0x00, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0, 192, 168, 1, 2))) // 64:ff9b::192.168.1.2
        assertFalse(SafeNetworkTarget.isPublicAddress(ipv6(0x20, 0x02, 192, 168, 1, 2))) // 2002:c0a8:0102::
        assertTrue(SafeNetworkTarget.isPublicAddress(ipv6(0x20, 0x01, 0x48, 0x60, 0x48, 0x60)))
    }

    @Test
    fun parsesIsoDatesWithoutChangingUtcSemantics() {
        val utc = parseIsoDateMillis("2026-10-30T12:34:56Z")
        val offset = parseIsoDateMillis("2026-10-30T15:34:56+03:00")

        assertNotNull(utc)
        assertTrue(utc == offset)
        assertTrue(parseIsoDateMillis("not a date") == null)
    }

    @Test
    fun configApiIsCachedPerServer() {
        assertSame(ApiFactory.config("https://instance.example"), ApiFactory.config("https://instance.example/"))
    }
}
