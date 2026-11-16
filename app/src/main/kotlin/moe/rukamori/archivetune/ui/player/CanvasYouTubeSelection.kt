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
import java.util.Locale

/**
 * Picks the best YouTube candidate for a track's canvas.
 *
 * Search results are evaluated as a *collection*: anti-patterns are filtered,
 * then candidates are tiered (OMV/UGC first, then official-indicator matches,
 * then the rest only at high confidence). Within a tier the highest score wins,
 * with view count as the tie-breaker. Never blindly takes index 0.
 */
internal fun selectYouTubeMusicVideoCandidate(
    candidates: List<SongItem>,
    songTitle: String,
    artistName: String,
    excludeVideoId: String? = null,
): SongItem? = rankYouTubeMusicVideoCandidates(candidates, songTitle, artistName, excludeVideoId).firstOrNull()

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
): List<SongItem> {
    val normalizedSong = songTitle.trim().lowercase(Locale.ROOT)
    val normalizedArtist = artistName.trim().lowercase(Locale.ROOT)
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
        val title = item.title.lowercase(Locale.ROOT)
        return title.contains(normalizedSong) || normalizedSong.contains(title)
    }

    fun antiPattern(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        // Always reject for canvas purposes.
        if (t.contains("lyric")) return true
        if (t.contains("audio")) return true
        if (t.contains("cover")) return true
        if (t.contains("shorts") || t.contains("#shorts")) return true
        if (t.contains("reaction") || t.contains("react to") || t.contains("reacting to")) return true
        if (t.contains("mashup")) return true
        if (t.contains("compilation")) return true
        if (t.contains("best of")) return true
        if (t.contains("loop") || t.contains("extended")) return true
        if (t.contains("1 hour") || t.contains("10 hour") || t.contains("24/7")) return true
        if (t.contains("fan edit") || t.contains("fan made") || t.contains("fanmade")) return true
        if (t.contains("tribute") || t.contains("remake")) return true
        if (t.contains("version of")) return true
        // Context-aware.
        if (!requestWantsRemix && t.contains("remix")) return true
        if (!requestWantsLive && (t.contains("live") || t.contains("concert"))) return true
        return false
    }

    fun isMusicVideo(item: SongItem): Boolean =
        item.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType in
            setOf(MUSIC_VIDEO_TYPE_OMV, MUSIC_VIDEO_TYPE_UGC)

    fun hasOfficialIndicator(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        return t.contains("official") || t.contains("vevo") || t.contains("music video") || t.contains("official video")
    }

    fun score(item: SongItem): Int {
        val title = item.title.lowercase(Locale.ROOT)
        val titleScore = when {
            title == normalizedSong -> 4
            title.contains(normalizedSong) || normalizedSong.contains(title) -> 2
            else -> 0
        }
        val artistScore = if (normalizedArtist.isBlank()) 1 else if (item.artists.any { artistMatches(it.name) }) 2 else 0
        val durationScore = item.duration?.let { if (it in 30..900) 1 else -2 } ?: 0
        val officialScore = when {
            title.contains("official music video") -> 3
            title.contains("official video") -> 2
            title.contains("official") -> 1
            title.contains("vevo") -> 2
            else -> 0
        } + if (isMusicVideo(item)) 5 else 0
        return titleScore + artistScore + durationScore + officialScore
    }

    val usable = candidates
        .filter { isUsable(it.id) }
        .filter { it.id != excludeVideoId }
        .filter { titleMatches(it) }
        .filter { itemArtistMatches(it) }
        .filter { !antiPattern(it.title) }

    if (usable.isEmpty()) return emptyList()

    val comparator = compareByDescending<SongItem> { score(it) }
        .thenByDescending { it.viewCount ?: 0L }

    val tier1 = usable.filter { isMusicVideo(it) }.sortedWith(comparator)
    val tier2 = usable.filter { !isMusicVideo(it) && hasOfficialIndicator(it.title) }.sortedWith(comparator)
    val tier3 =
        usable
            .filter { !isMusicVideo(it) && !hasOfficialIndicator(it.title) }
            .sortedWith(comparator)
            .takeIf { it.firstOrNull()?.let(::score)?.let { s -> s >= 8 } == true }
            .orEmpty()

    return tier1 + tier2 + tier3
}
