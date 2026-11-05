package app.peertube.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateStoreTest {
    @Test
    fun progressCacheKeepsMostRecentlyUpdatedFiveHundredItems() {
        val cache = BoundedProgressMap(capacity = 3)
        cache.load(linkedMapOf("old" to 1L, "middle" to 2L))
        cache.put("new", 3L)
        cache.put("old", 4L)
        cache.put("latest", 5L)

        assertEquals(mapOf("new" to 3L, "old" to 4L, "latest" to 5L), cache.snapshot())
        assertNull(cache["middle"])
    }

    @Test
    fun watchedIdsUseUniqueBoundedSetAndRejectBlankIds() {
        val cache = BoundedWatchedIds(capacity = 2)
        assertTrue(cache.add("one"))
        assertFalse(cache.add("one"))
        assertFalse(cache.add(" "))
        cache.add("two")
        cache.add("three")

        assertFalse("one" in cache)
        assertTrue("two" in cache)
        assertTrue("three" in cache)
        assertEquals(listOf("two", "three"), cache.snapshot())
    }

    @Test
    fun compactSavedVideoKeepsCardMetadataAndDropsLargeMediaPayloads() {
        val video = Video(
            uuid = "id", name = "name", description = "long description", thumbnailUrl = "https://image.example/thumb.jpg",
            files = listOf(VideoFile(fileUrl = "https://media.example/video.mp4")),
            streamingPlaylists = listOf(StreamingPlaylist(playlistUrl = "https://media.example/video.m3u8"))
        )

        val compact = video.compactForStorage()

        assertEquals("id", compact.uuid)
        assertEquals(video.thumbnailUrl, compact.thumbnailUrl)
        assertEquals("", compact.description)
        assertEquals(null, compact.files)
        assertEquals(null, compact.streamingPlaylists)
    }
}
