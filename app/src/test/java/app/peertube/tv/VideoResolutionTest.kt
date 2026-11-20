package app.peertube.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoResolutionTest {
    private fun file(height: Int) = VideoFile(fileUrl = "https://video.example/$height.mp4", resolution = Resolution(height, "${height}p"))
    private val files = sequenceOf(file(360), file(1080), file(720), file(2160))

    @Test
    fun automaticPicksHighestResolution() {
        assertEquals(2160, pickVideoFile(files, 0)?.resolution?.id)
    }

    @Test
    fun picksHighestResolutionWithinTheLimit() {
        assertEquals(720, pickVideoFile(files, 720)?.resolution?.id)
        assertEquals(1080, pickVideoFile(files, 1440)?.resolution?.id)
    }

    @Test
    fun fallsBackToSmallestWhenEveryFileIsTooTall() {
        assertEquals(360, pickVideoFile(files, 240)?.resolution?.id)
    }

    @Test
    fun ignoresFilesWithoutUrl() {
        assertNull(pickVideoFile(sequenceOf(VideoFile(fileUrl = " ", resolution = Resolution(720))), 0))
    }

    @Test
    fun directFilesFollowTheDefaultButHlsPlaylistWins() {
        val direct = Video(uuid = "a", name = "a", files = listOf(file(480), file(1080)))
        assertEquals("https://video.example/480.mp4", direct.playableUrl(720))
        val hls = direct.copy(streamingPlaylists = listOf(StreamingPlaylist(playlistUrl = "https://video.example/master.m3u8")))
        assertEquals("https://video.example/master.m3u8", hls.playableUrl(720))
    }
}
