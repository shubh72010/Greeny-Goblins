/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import java.text.Normalizer
import java.util.Locale

/**
 * Final safety gate: a resolved canvas must not be an obvious lyric/audio/cover
 * upload, and its artist must match the requested track. This runs on *every*
 * result (YouTube, Apple, backend, cache) so one bad source cannot surface a
 * wrong video.
 */
internal fun CanvasArtwork.isPlausibleCanvasFor(
    songTitle: String,
    artistName: String,
    allowLyric: Boolean = false,
): Boolean {
    val name = normalizedCanvasText(name.orEmpty())
    if (name.hasCanvasAntiPattern(allowLyric)) return false

    val requestedArtist = normalizedCanvasText(artistName)
    val resolvedArtist = normalizedCanvasText(artist.orEmpty())
    if (requestedArtist.isNotBlank() && resolvedArtist.isNotBlank()) {
        val compactRequested = requestedArtist.replace(" ", "")
        val compactResolved = resolvedArtist.replace(" ", "")
        if (!resolvedArtist.contains(requestedArtist) &&
            !requestedArtist.contains(resolvedArtist) &&
            !compactResolved.contains(compactRequested) &&
            !compactRequested.contains(compactResolved)
        ) {
            return false
        }
    }
    return true
}

private fun normalizedCanvasText(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun String.hasCanvasAntiPattern(allowLyric: Boolean): Boolean {
    if (isEmpty()) return false
    return buildList {
        if (!allowLyric) add("lyric")
        addAll(
            listOf(
                "audio",
                "cover",
                "shorts",
                "reaction",
                "react to",
                "mashup",
                "compilation",
                "best of",
                "loop",
                "extended",
                "1 hour",
                "10 hour",
                "24 7",
                "fan edit",
                "fan made",
                "tribute",
                "remake",
            ),
        )
    }.any { contains(it) }
}
