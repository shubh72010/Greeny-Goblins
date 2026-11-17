/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import java.text.Normalizer
import java.util.Locale

/**
 * Picks the best YouTube candidate for a track's canvas.
 *
 * Search results are evaluated as a *collection*: anti-patterns are filtered,
 * then candidates are tiered (OMV/UGC and "music video" titles first, then official-indicator matches,
 * then the rest only at high confidence). Within a tier the highest score wins,
 * with view count as the tie-breaker. Never blindly takes index 0.
 */
internal fun selectYouTubeMusicVideoCandidate(
    candidates: List<SongItem>,
    songTitle: String,
    artistName: String,
    excludeVideoId: String? = null,
    allowLyric: Boolean = false,
): SongItem? = rankYouTubeMusicVideoCandidates(candidates, songTitle, artistName, excludeVideoId, allowLyric).firstOrNull()

internal fun cleanCanvasTitle(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("\\s*\\[[^]]*]"), "")
        .replace(
            Regex(
                "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                RegexOption.IGNORE_CASE,
            ),
            "",
        ).replace(
            Regex(
                "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix|deluxe|explicit)[^)]*\\)",
                RegexOption.IGNORE_CASE,
            ),
            "",
        ).replace(
            Regex(
                "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix|deluxe|explicit)\\b.*$",
                RegexOption.IGNORE_CASE,
            ),
            "",
        ).replace(Regex("[^a-zA-Z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase(Locale.ROOT)

/**
 * All usable candidates, best first. Search metadata can lie (a clean-titled result
 * may resolve to a lyric video), so callers should resolve several and validate the
 * actual player response rather than trusting the first ranked item.
 */
internal fun rankYouTubeMusicVideoCandidates(
    candidates: List<SongItem>,
    songTitle: String,
    artistName: String,
    excludeVideoId: String? = null,
    allowLyric: Boolean = false,
): List<SongItem> {
    val normalizedSong = songTitle.trim().lowercase(Locale.ROOT)
    val normalizedArtist = artistName.trim().lowercase(Locale.ROOT)
    val cleanTarget = cleanCanvasTitle(songTitle)
    val requestWantsRemix = normalizedSong.contains("remix")
    val requestWantsLive = normalizedSong.contains("live") || normalizedSong.contains("concert")

    fun isUsable(id: String): Boolean = id.length in 11..16 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    fun artistMatches(raw: String): Boolean {
        val name = raw.trim().lowercase(Locale.ROOT)
        if (name.isBlank() || normalizedArtist.isBlank()) return normalizedArtist.isBlank()
        val compactName = name.replace(" ", "")
        val compactArtist = normalizedArtist.replace(" ", "")
        return name.contains(normalizedArtist) || normalizedArtist.contains(name) ||
            compactName.contains(compactArtist) || compactArtist.contains(compactName)
    }

    fun itemArtistMatches(item: SongItem): Boolean {
        if (normalizedArtist.isBlank()) return true
        return item.artists.any { artistMatches(it.name) }
    }

    fun titleMatches(item: SongItem): Boolean {
        if (normalizedSong.isBlank()) return true
        val cleanCandidate = cleanCanvasTitle(item.title)
        val rawCandidate = item.title.lowercase(Locale.ROOT)

        if (cleanTarget.isNotBlank() && cleanCandidate.isNotBlank()) {
            if (cleanCandidate == cleanTarget ||
                cleanCandidate.contains(cleanTarget) ||
                cleanTarget.contains(cleanCandidate)
            ) {
                return true
            }

            val targetWords = cleanTarget.split(" ").filter { it.length > 1 }
            if (targetWords.isNotEmpty() && targetWords.all { cleanCandidate.contains(it) }) {
                return true
            }
        }

        return rawCandidate.contains(normalizedSong) || normalizedSong.contains(rawCandidate)
    }

    fun antiPattern(item: SongItem): Boolean {
        val title = item.title
        val t = title.lowercase(Locale.ROOT)
        // Topic channels are static audio-only uploads, not music videos
        if (item.artists.any { it.name.trim().endsWith("- Topic", ignoreCase = true) }) return true
        // Lyric videos are only eligible when the user explicitly asked for them.
        if (!allowLyric && t.contains("lyric")) return true
        if (t.contains("audio")) return true
        if (t.contains("cover")) return true
        if (t.contains("shorts") || t.contains("#shorts")) return true
        if (t.contains("reaction") || t.contains("react to") || t.contains("reacting to")) return true
        if (t.contains("mashup")) return true
        if (t.contains("compilation")) return true
        if (t.contains("best of")) return true
        if (t.contains("loop") || t.contains("extended")) return true
        if (t.contains("1 hour") || t.contains("10 hour") || t.contains("24/7") || t.contains("24 7")) return true
        if (t.contains("fan edit") || t.contains("fan made") || t.contains("fanmade")) return true
        if (t.contains("tribute") || t.contains("remake")) return true
        if (t.contains("version of")) return true
        // Context-aware (remix only; a live performance is a demoted tier, never discarded).
        if (!requestWantsRemix && t.contains("remix")) return true
        return false
    }

    fun isLivePerformance(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        return t.contains(Regex("\\b(live|concert)\\b"))
    }

    fun hasMusicVideoIndicator(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        return t.contains("music video") ||
            t.contains("official video") ||
            t.contains("official music video") ||
            t.contains("[mv]") ||
            t.contains("(mv)") ||
            t.contains(" m/v") ||
            t.contains("[m/v]") ||
            t.contains("(m/v)") ||
            t.contains("vevo")
    }

    fun isMusicVideo(item: SongItem): Boolean =
        item.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType in
            setOf(MUSIC_VIDEO_TYPE_OMV, MUSIC_VIDEO_TYPE_UGC) ||
            hasMusicVideoIndicator(item.title)

    fun hasOfficialIndicator(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        return hasMusicVideoIndicator(title) || t.contains("official")
    }

    fun score(item: SongItem): Int {
        val title = item.title.lowercase(Locale.ROOT)
        val cleanCandidate = cleanCanvasTitle(item.title)
        val titleScore = when {
            cleanCandidate == cleanTarget -> 6
            cleanTarget.isNotBlank() && cleanCandidate.contains(cleanTarget) -> 4
            title == normalizedSong -> 4
            title.contains(normalizedSong) || normalizedSong.contains(title) -> 2
            else -> 0
        }
        val artistScore = if (normalizedArtist.isBlank()) 1 else if (item.artists.any { artistMatches(it.name) }) 3 else 0
        val durationScore = item.duration?.let { if (it in 30..900) 1 else -2 } ?: 0
        val mvScore = when {
            title.contains("official music video") -> 6
            title.contains("music video") -> 5
            title.contains("official video") -> 4
            title.contains("[mv]") || title.contains("(mv)") || title.contains(" m/v") -> 4
            title.contains("vevo") -> 3
            title.contains("official") -> 2
            else -> 0
        } + if (item.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType in setOf(MUSIC_VIDEO_TYPE_OMV, MUSIC_VIDEO_TYPE_UGC)) 4 else 0
        return titleScore + artistScore + durationScore + mvScore
    }

    val usable = candidates
        .filter { isUsable(it.id) }
        .filter { it.id != excludeVideoId }
        .filter { titleMatches(it) }
        .filter { itemArtistMatches(it) }
        .filter { !antiPattern(it) }

    if (usable.isEmpty()) return emptyList()

    val comparator = compareByDescending<SongItem> { score(it) }
        .thenByDescending { it.viewCount ?: 0L }

    fun tierRank(item: SongItem): Int {
        val mv = isMusicVideo(item)
        val live = isLivePerformance(item.title)
        val official = hasOfficialIndicator(item.title)
        val lyric = item.title.lowercase(Locale.ROOT).contains("lyric")
        return when {
            allowLyric && lyric -> 0
            requestWantsLive && live -> 0
            mv && !live -> 0
            (mv && live) || (official && !live) -> 1
            !mv && !official && !live -> 2
            else -> 3
        }
    }

    val ranked =
        usable.sortedWith(
            compareBy<SongItem> { tierRank(it) }.then(comparator),
        )
    return ranked.filter { tierRank(it) <= 1 || score(it) >= 5 }
}
