package app.peertube.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDeepLinkTest {
    @Test
    fun acceptsLegacyTvRecommendationLinkButReturnsOnlyVideoIdentity() {
        val link = parseExternalVideoLink(
            "peertubetv://video?uuid=1234-uuid&server=https%3A%2F%2Fvideos.example&title=ignored&views=123",
            { it == "videos.example" }
        )

        assertEquals(ExternalVideoLink("1234-uuid", "https://videos.example"), link)
    }

    @Test
    fun rejectsHttpPrivateUntrustedAndAmbiguousLinks() {
        assertNull(parseExternalVideoLink("peertubetv://video?uuid=id&server=http%3A%2F%2Fvideos.example") { true })
        assertNull(parseExternalVideoLink("peertubetv://video?uuid=id&server=https%3A%2F%2F127.0.0.1") { false })
        assertNull(parseExternalVideoLink("peertubetv://video?uuid=id&uuid=other&server=https%3A%2F%2Fvideos.example") { true })
        assertNull(parseExternalVideoLink("peertubetv://video?uuid=id&server=https%3A%2F%2Fuser%40videos.example") { true })
    }
}
