package app.peertube.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationsTest {
    private fun video(
        uuid: String,
        name: String = uuid,
        tags: List<String>? = null,
        channel: String? = null,
        host: String? = null,
        views: Long = 0,
        publishedAt: String? = null
    ) = Video(
        uuid = uuid, name = name, tags = tags, views = views, publishedAt = publishedAt, host = host,
        channel = channel?.let { Channel(name = it, host = host) }
    )

    @Test
    fun queriesPreferMostFrequentTags() {
        val watched = listOf(video("a", tags = listOf("Linux", "Kotlin")), video("b", tags = listOf("linux", "tv")))
        assertEquals("linux", recommendationQueries(watched).first())
        assertEquals(3, recommendationQueries(watched).size)
    }

    @Test
    fun queriesFallBackToTitleWordsWithoutStopWords() {
        val watched = listOf(video("a", name = "The Kotlin video for TV"), video("b", name = "Kotlin coroutines"))
        assertEquals(listOf("kotlin", "coroutines"), recommendationQueries(watched))
    }

    @Test
    fun queriesAreLimitedToFive() {
        val watched = listOf(video("a", tags = listOf("one", "two", "three", "four", "five", "six", "seven")))
        assertEquals(5, recommendationQueries(watched).size)
    }

    @Test
    fun rankingOrdersBySharedTagsThenTitleWordsAndDropsExcluded() {
        val watched = listOf(video("w", name = "Linux desktop tour", tags = listOf("linux")))
        val ranked = rankRecommendations(
            candidates = listOf(
                video("plain", name = "Cooking pasta"),
                video("title", name = "Desktop setup"),
                video("tag", name = "Something", tags = listOf("Linux")),
                video("excluded", name = "Linux desktop", tags = listOf("linux")),
                video("tag")
            ),
            watched = watched, excludedIds = setOf("excluded"), watchedIds = emptySet(), hideWatched = false, countryCode = ""
        )
        assertEquals(listOf("tag", "title", "plain"), ranked.map { it.uuid })
    }

    @Test
    fun rankingHidesWatchedOnlyWhenRequestedAndRewardsCountryHost() {
        val watched = listOf(video("w", name = "News"))
        val candidates = listOf(video("seen", name = "Other"), video("local", name = "Other", host = "video.example.de"))
        assertEquals(listOf("local", "seen"), rankRecommendations(candidates, watched, emptySet(), setOf("seen"), hideWatched = false, countryCode = "DE").map { it.uuid })
        assertEquals(listOf("local"), rankRecommendations(candidates, watched, emptySet(), setOf("seen"), hideWatched = true, countryCode = "DE").map { it.uuid })
    }

    @Test
    fun rankingRewardsSameChannel() {
        val watched = listOf(video("w", name = "Episode", channel = "makers"))
        val ranked = rankRecommendations(listOf(video("other", name = "Clip"), video("same", name = "Clip", channel = "makers")), watched, emptySet(), emptySet(), false, "")
        assertEquals("same", ranked.first().uuid)
    }

    @Test
    fun sortsByModeWithNewestAsDefault() {
        val items = listOf(
            video("b", name = "beta", views = 5, publishedAt = "2026-01-02"),
            video("a", name = "Alpha", views = 50, publishedAt = "2026-01-01"),
            video("c", name = "gamma", views = 1, publishedAt = "2026-01-03")
        )
        assertEquals(listOf("a", "b", "c"), sortVideosBy("popular", items).map { it.uuid })
        assertEquals(listOf("a", "b", "c"), sortVideosBy("title", items).map { it.uuid })
        assertEquals(listOf("c", "b", "a"), sortVideosBy("newest", items).map { it.uuid })
    }
}
