/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.subsonic

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches YouTube queue items to OpenSubsonic server tracks (for the SUBSONIC
 * playback client). One `search3` per uncached videoId; matches cached in memory.
 * No match (or any failure) returns null so MusicService falls back to InnerTube.
 */
@Singleton
class SubsonicMatchResolver @Inject constructor(
    private val client: SubsonicClient,
) {
    private val matchCache = ConcurrentHashMap<String, String>()

    suspend fun resolveTrackId(
        config: SubsonicConfig,
        videoId: String,
        title: String,
        artists: List<String>,
    ): String? {
        matchCache[videoId]?.let { return it }
        val artist = artists.firstOrNull()?.takeIf { it.isNotBlank() }
        val combined = ((artist?.let { "$it " } ?: "") + title).trim()
        if (combined.isBlank()) return null
        // Combined "artist title" first (precise on AND-matching servers); fall back
        // to title-only since some servers phrase-match the whole query string.
        val queries = listOf(combined, title.trim()).distinct()
        var songs = emptyList<TrackPayload>()
        for (query in queries) {
            songs =
                runCatching { client.search3(config, query) }
                    .onFailure { Timber.tag("Subsonic").w(it, "match search failed") }
                    .getOrNull()?.song.orEmpty()
            if (songs.isNotEmpty()) {
                if (query != combined) Timber.tag("Subsonic").d("title-only retry hit for %s", combined)
                break
            }
        }
        val best = SubsonicMatcher.best(title, artists, songs) ?: run {
            Timber.tag("Subsonic").d("no server match for %s", combined)
            return null
        }
        Timber.tag("Subsonic").d("matched %s -> %s", combined, best.id)
        matchCache[videoId] = best.id
        return best.id
    }

    fun invalidate(videoId: String) {
        matchCache.remove(videoId)
    }
}

/** Pure title/artist scoring for server-track matching. Unit-tested. */
object SubsonicMatcher {
    private val bracketRegex = Regex("\\(.*?\\)|\\[.*?\\]")
    private val spaceRegex = Regex("\\s+")

    fun normalize(raw: String): String =
        bracketRegex.replace(raw.lowercase(), " ").replace(spaceRegex, " ").trim()

    fun tokenOverlap(a: String, b: String): Double {
        val tokensA = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        return tokensA.intersect(tokensB).size.toDouble() / maxOf(tokensA.size, tokensB.size)
    }

    fun score(queryTitle: String, queryArtists: List<String>, track: TrackPayload): Int {
        val want = normalize(queryTitle)
        val got = normalize(track.title)
        if (want.isBlank() || got.isBlank()) return 0
        val titleScore =
            when {
                got == want -> 100
                got.contains(want) || want.contains(got) -> 60
                else -> (tokenOverlap(want, got) * 50).toInt()
            }
        if (titleScore < 60) return titleScore
        val wantArtists = queryArtists.map(::normalize).filter { it.isNotBlank() }
        val trackArtist = normalize(track.artist.orEmpty())
        val artistScore =
            if (wantArtists.isEmpty() || trackArtist.isBlank()) {
                0
            } else if (wantArtists.any { it == trackArtist || trackArtist.contains(it) || it.contains(trackArtist) }) {
                50
            } else {
                0
            }
        return titleScore + artistScore
    }

    /** Accepts only solid matches: exact/contains title plus an artist signal when known. */
    fun best(queryTitle: String, queryArtists: List<String>, candidates: List<TrackPayload>): TrackPayload? {
        var best: TrackPayload? = null
        var bestScore = 0
        for (candidate in candidates) {
            val total = score(queryTitle, queryArtists, candidate)
            if (total > bestScore) {
                bestScore = total
                best = candidate
            }
        }
        // Exact title alone (100, no artist info) is not enough; contains (60) needs artist (+50).
        return if (bestScore >= 110 || (bestScore == 100 && queryArtists.all { it.isBlank() })) best else null
    }
}
