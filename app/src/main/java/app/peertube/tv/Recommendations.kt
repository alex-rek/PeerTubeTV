package app.peertube.tv

import java.util.Locale

/*
 * Pure recommendation and sorting logic used by HomeModel, kept free of Android and network code so it
 * can be unit-tested.
 */

private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
private val STOP_WORDS = setOf("the", "and", "for", "with", "from", "this", "that", "как", "что", "это", "для", "или", "при", "про", "видео", "video")

/** Significant title words of the watched videos (at least 3 letters, without stop words). */
internal fun recommendationTitleTokens(watched: List<Video>): List<String> =
    watched.flatMap { it.name.lowercase().split(WORD_SEPARATOR) }.filter { it.length >= 3 && it !in STOP_WORDS }

/** Up to five search terms: the most frequent tags of the watched videos, or their title words if none have tags. */
internal fun recommendationQueries(watched: List<Video>): List<String> {
    val tags = watched.flatMap { it.tags.orEmpty() }.map { it.trim() }.filter { it.length >= 2 }
    return (tags.ifEmpty { recommendationTitleTokens(watched) })
        .groupingBy { it.lowercase() }.eachCount().entries
        .sortedByDescending { it.value }.map { it.key }.distinct().take(5)
}

/**
 * Removes excluded (and, when [hideWatched], already watched) candidates and orders the rest by similarity
 * to [watched]: shared tags count 4, shared title words 1, the same channel 1, a host in [countryCode]'s TLD 2.
 */
internal fun rankRecommendations(
    candidates: List<Video>,
    watched: List<Video>,
    excludedIds: Set<String>,
    watchedIds: Set<String>,
    hideWatched: Boolean,
    countryCode: String
): List<Video> {
    val watchedTags = watched.flatMap { it.tags.orEmpty() }.map { it.lowercase() }.toSet()
    val watchedWords = recommendationTitleTokens(watched).toSet()
    val watchedChannels = watched.mapNotNull { it.channel?.name }.toSet()
    val country = countryCode.lowercase(Locale.ROOT)
    return candidates.distinctBy { it.uuid }
        .filterNot { it.uuid in excludedIds || (hideWatched && it.uuid in watchedIds) }
        .sortedByDescending { candidate ->
            val tagScore = candidate.tags.orEmpty().count { it.lowercase() in watchedTags } * 4
            val titleScore = candidate.name.lowercase().split(WORD_SEPARATOR).count { it in watchedWords }
            val channelScore = if (candidate.channel?.name in watchedChannels) 1 else 0
            val host = (candidate.host ?: candidate.channel?.host ?: candidate.account?.host).orEmpty().lowercase(Locale.ROOT)
            val countryBonus = if (country.isNotBlank() && host.substringAfterLast('.', "") == country) 2 else 0
            tagScore + titleScore + channelScore + countryBonus
        }
}

/** Orders a video list by the user's sort mode: "popular" (views), "title", otherwise newest first. */
internal fun sortVideosBy(mode: String, items: List<Video>): List<Video> = when (mode) {
    "popular" -> items.sortedByDescending { it.views }
    "title" -> items.sortedBy { it.name.lowercase(Locale.ROOT) }
    else -> items.sortedByDescending { it.publishedAt ?: "" }
}
