package app.peertube.tv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAggregatorTest {
    private val request = SearchRequest(
        query = "test", page = 0, pageSize = 10, activeServer = "https://videos.example.org"
    )

    @Test
    fun sequentialFallsThroughFailedAndShortSourcesInPriorityOrder() = runBlocking {
        val called = mutableListOf<SearchSource>()
        val providers = mutableMapOf<SearchSource, suspend (SearchRequest) -> SearchBatch>()
        providers[SearchSource.SEPIA] = { called += SearchSource.SEPIA; error("offline") }
        providers[SearchSource.PEERSEEK] = { called += SearchSource.PEERSEEK; SearchBatch(listOf(video("1"))) }
        providers[SearchSource.DIRECT] = { called += SearchSource.DIRECT; SearchBatch(listOf(video("2"), video("3"))) }
        val aggregator = SearchAggregator(providers)
        val settings = SearchSettings(
            enabled = setOf(SearchSource.SEPIA, SearchSource.PEERSEEK, SearchSource.DIRECT),
            order = listOf(SearchSource.SEPIA, SearchSource.PEERSEEK, SearchSource.DIRECT), mode = "sequential"
        )

        val outcome = aggregator.search(request, settings)

        assertEquals(listOf(SearchSource.SEPIA, SearchSource.PEERSEEK, SearchSource.DIRECT), called)
        assertEquals(listOf("1", "2", "3"), outcome.videos.map { it.uuid })
        assertTrue(outcome.errors.containsKey(SearchSource.SEPIA))
    }

    @Test
    fun hybridStopsAfterConfiguredSufficientCount() = runBlocking {
        val called = mutableListOf<SearchSource>()
        val providers = mutableMapOf<SearchSource, suspend (SearchRequest) -> SearchBatch>()
        providers[SearchSource.SEPIA] = { called += SearchSource.SEPIA; SearchBatch(listOf(video("1"))) }
        providers[SearchSource.PEERSEEK] = { called += SearchSource.PEERSEEK; SearchBatch(listOf(video("2"))) }
        providers[SearchSource.DIRECT] = { called += SearchSource.DIRECT; SearchBatch(listOf(video("3"))) }
        val aggregator = SearchAggregator(providers)
        val settings = SearchSettings(
            enabled = setOf(SearchSource.SEPIA, SearchSource.PEERSEEK, SearchSource.DIRECT),
            order = listOf(SearchSource.SEPIA, SearchSource.PEERSEEK, SearchSource.DIRECT),
            mode = "hybrid", sufficientResults = 2
        )

        val outcome = aggregator.search(request, settings)

        assertEquals(listOf(SearchSource.SEPIA, SearchSource.PEERSEEK), called)
        assertEquals(2, outcome.videos.size)
    }

    @Test
    fun parallelMergesResultsAndTracksDuplicateSourcesByUuid() = runBlocking {
        val providers = mutableMapOf<SearchSource, suspend (SearchRequest) -> SearchBatch>()
        providers[SearchSource.SEPIA] = { SearchBatch(listOf(video("same"), video("one"))) }
        providers[SearchSource.PEERSEEK] = { SearchBatch(listOf(video("same"), video("two"))) }
        val aggregator = SearchAggregator(providers)
        val settings = SearchSettings(
            enabled = setOf(SearchSource.SEPIA, SearchSource.PEERSEEK),
            order = listOf(SearchSource.SEPIA, SearchSource.PEERSEEK), mode = "parallel", maxParallel = 2
        )

        val outcome = aggregator.search(request, settings)

        assertEquals(setOf("same", "one", "two"), outcome.videos.map { it.uuid }.toSet())
        assertEquals(setOf(SearchSource.SEPIA, SearchSource.PEERSEEK), outcome.sourcesByVideo.getValue("same"))
    }

    @Test
    fun canonicalVideoUrlAlsoDeduplicatesDifferentProviderIds() = runBlocking {
        val providers = mutableMapOf<SearchSource, suspend (SearchRequest) -> SearchBatch>()
        providers[SearchSource.SEPIA] = { SearchBatch(listOf(Video(uuid = "provider-id", name = "First", url = "https://videos.example.org/w/abc"))) }
        providers[SearchSource.PEERSEEK] = { SearchBatch(listOf(Video(uuid = "global-id", name = "Second", url = "https://videos.example.org/w/abc/"))) }
        val settings = SearchSettings(
            enabled = setOf(SearchSource.SEPIA, SearchSource.PEERSEEK),
            order = listOf(SearchSource.SEPIA, SearchSource.PEERSEEK), mode = "parallel"
        )

        val outcome = SearchAggregator(providers).search(request, settings)

        assertEquals(1, outcome.videos.size)
        assertEquals(setOf(SearchSource.SEPIA, SearchSource.PEERSEEK), outcome.sourcesByVideo.getValue("provider-id"))
    }

    @Test
    fun disabledSourcesAreNotRequestedOrReportedAsFailed() = runBlocking {
        var sepiaCalled = false
        val providers = mutableMapOf<SearchSource, suspend (SearchRequest) -> SearchBatch>().apply {
            this[SearchSource.SEPIA] = {
            sepiaCalled = true
            error("offline")
            }
        }

        val outcome = SearchAggregator(providers).search(
            request,
            SearchSettings(enabled = emptySet(), order = listOf(SearchSource.SEPIA))
        )

        assertEquals(false, sepiaCalled)
        assertTrue(outcome.errors.isEmpty())
        assertTrue(outcome.videos.isEmpty())
    }

    private fun video(uuid: String) = Video(uuid = uuid, name = "Video $uuid")
}
