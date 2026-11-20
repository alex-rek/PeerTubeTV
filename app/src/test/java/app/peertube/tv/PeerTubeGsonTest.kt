package app.peertube.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PeerTubeGsonTest {
    @Test
    fun readsSubscriptionsStoredByMinifiedReleasesAndCurrentFormat() {
        val type = object : com.google.gson.reflect.TypeToken<List<VideoSubscription>>() {}.type
        val legacy = PeerTubeGson.instance.fromJson<List<VideoSubscription>>("""[{"a":"news","b":"video.example","c":"News"}]""", type)
        val current = PeerTubeGson.instance.fromJson<List<VideoSubscription>>("""[{"name":"news","host":"video.example","displayName":"News"}]""", type)

        assertEquals(listOf(VideoSubscription("news", "video.example", "News")), legacy)
        assertEquals(legacy, current)
    }

    @Test
    fun writesSubscriptionsWithReadableFieldNames() {
        val json = PeerTubeGson.instance.toJson(listOf(VideoSubscription("news", "video.example", "News")))

        assertEquals("""[{"name":"news","host":"video.example","displayName":"News"}]""", json)
    }

    @Test
    fun incompleteVideoReceivesSafeRequiredDefaults() {
        val video = PeerTubeGson.instance.fromJson("""{"uuid":null,"name":null,"description":null}""", Video::class.java)

        assertEquals("", video.uuid)
        assertEquals("", video.name)
        assertEquals("", video.description)
        assertEquals(null, video.playableUrl())
    }

    @Test
    fun nullOrMissingCollectionsAreNormalizedForApiAndSavedInstanceData() {
        val page = PeerTubeGson.instance.fromJson("""{"data":null}""", VideoPage::class.java)
        val instance = PeerTubeGson.instance.fromJson("""{"host":null,"languages":null}""", PeerTubeInstance::class.java)
        val oldCache = PeerTubeGson.instance.fromJson("""[{"uuid":"old","name":"Saved video","files":null,"streamingPlaylists":null}]""", Array<Video>::class.java)

        assertNotNull(page.data)
        assertEquals(emptyList<Video>(), page.data)
        assertEquals("", instance.host)
        assertEquals(emptyList<String>(), instance.languages)
        assertEquals(null, oldCache.single().playableUrl())
    }
}
