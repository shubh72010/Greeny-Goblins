/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.ArchiveTuneCanvas
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.utils.BlockedArtwork
import timber.log.Timber

internal suspend fun resolveCanvasArtworkForPlayback(
    mediaId: String,
    songTitleRaw: String,
    artistNameRaw: String,
    albumId: String? = null,
    albumTitleRaw: String? = null,
    storefront: String,
    requireVertical: Boolean,
    allowNetwork: Boolean,
    currentIsMusicVideo: Boolean = false,
    canvasSource: CanvasSource = CanvasSource.AUTO,
    videoKind: CanvasVideoKind = CanvasVideoKind.MUSIC,
): CanvasArtwork? {
    if (BlockedArtwork.isBlocked(mediaId)) {
        Timber.tag(CanvasArtworkLogTag).d("Artwork blocked for %s; skipping canvas lookup", mediaId)
        return null
    }
    val preferLyric = videoKind == CanvasVideoKind.LYRIC
    if (canvasSource == CanvasSource.AUTO) {
        withContext(Dispatchers.IO) {
            CanvasArtworkPlaybackCache.get(
                mediaId = mediaId,
                preferCachedOnly = true,
            )
        }?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
            ?.takeIf { artwork -> artwork.isPlausibleCanvasFor(songTitleRaw, artistNameRaw, preferLyric) }
            ?.let { return it }
    }

    if (!allowNetwork || mediaId.isBlank()) {
        Timber.tag(CanvasArtworkLogTag).d("Skipping canvas network lookup for %s", mediaId)
        return null
    }

    return withContext(Dispatchers.IO) {
        val fetched =
            when (canvasSource) {
                CanvasSource.YOUTUBE ->
                    fetchYouTubeCanvasFallback(
                        mediaId = mediaId,
                        songTitleRaw = songTitleRaw,
                        artistNameRaw = artistNameRaw,
                        requireVertical = requireVertical,
                        currentIsMusicVideo = currentIsMusicVideo,
                        fallbackToAnyVideo = currentIsMusicVideo,
                        preferLyric = preferLyric,
                    )

                CanvasSource.APPLE ->
                    fetchCanvasArtworkForPlayback(
                        songTitleRaw = songTitleRaw,
                        artistNameRaw = artistNameRaw,
                        storefront = storefront,
                        requireVertical = requireVertical,
                    ) ?: fetchCanvasArtworkByAlbumFallback(
                        albumId = albumId,
                        albumTitleRaw = albumTitleRaw,
                        artistNameRaw = artistNameRaw,
                        storefront = storefront,
                        requireVertical = requireVertical,
                    ) ?: fetchYouTubeCanvasFallback(
                        mediaId = mediaId,
                        songTitleRaw = songTitleRaw,
                        artistNameRaw = artistNameRaw,
                        requireVertical = requireVertical,
                        currentIsMusicVideo = currentIsMusicVideo,
                        fallbackToAnyVideo = currentIsMusicVideo,
                        preferLyric = preferLyric,
                    )

                CanvasSource.AUTO ->
                    fetchYouTubeCanvasFallback(
                        mediaId = mediaId,
                        songTitleRaw = songTitleRaw,
                        artistNameRaw = artistNameRaw,
                        requireVertical = requireVertical,
                        currentIsMusicVideo = currentIsMusicVideo,
                        fallbackToAnyVideo = false,
                        preferLyric = preferLyric,
                    ) ?: fetchCanvasArtworkForPlayback(
                        songTitleRaw = songTitleRaw,
                        artistNameRaw = artistNameRaw,
                        storefront = storefront,
                        requireVertical = requireVertical,
                    ) ?: fetchCanvasArtworkByAlbumFallback(
                        albumId = albumId,
                        albumTitleRaw = albumTitleRaw,
                        artistNameRaw = artistNameRaw,
                        storefront = storefront,
                        requireVertical = requireVertical,
                    )
            }

        if (fetched == null) {
            Timber.tag(CanvasArtworkLogTag).d("No playable canvas resolved for %s", mediaId)
            return@withContext null
        }
        if (!fetched.isPlausibleCanvasFor(songTitleRaw, artistNameRaw, preferLyric)) {
            Timber.tag(CanvasArtworkLogTag).w(
                "Discarding implausible canvas for %s: name=%s artist=%s",
                mediaId,
                fetched.name,
                fetched.artist,
            )
            return@withContext null
        }

        CanvasArtworkPlaybackCache.put(mediaId, fetched)
    }
}

internal suspend fun fetchCanvasArtworkForPlayback(
    songTitleRaw: String,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
): CanvasArtwork? {
    val songTitle = normalizeCanvasSongTitle(songTitleRaw)
    val artistName = normalizeCanvasArtistName(artistNameRaw)
    val candidates =
        linkedSetOf(
            songTitle to artistName,
            songTitleRaw to artistName,
            songTitle to artistNameRaw,
            songTitleRaw to artistNameRaw,
        ).filter { (song, artist) ->
            song.isNotBlank() && artist.isNotBlank()
        }

    return candidates.firstNotNullOfOrNull { (song, artist) ->
        ArchiveTuneCanvas
            .getBySongArtist(
                song = song,
                artist = artist,
                storefront = storefront,
            )?.takeIf { artwork ->
                if (requireVertical) {
                    !artwork.preferredVerticalAnimationUrl.isNullOrBlank()
                } else {
                    !artwork.preferredAnimationUrl.isNullOrBlank()
                }
            }
    }
}

private suspend fun fetchCanvasArtworkByAlbumFallback(
    albumId: String?,
    albumTitleRaw: String?,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
): CanvasArtwork? {
    albumId
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { nonBlankAlbumId ->
            ArchiveTuneCanvas
                .getByAlbumId(nonBlankAlbumId)
                ?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
                ?.let { return it }
        }

    val albumTitle = albumTitleRaw?.trim().orEmpty()
    val artistName = artistNameRaw.trim()
    if (albumTitle.isBlank() || artistName.isBlank()) return null

    return ArchiveTuneCanvas
        .getBySongArtist(
            song = albumTitle,
            artist = artistName,
            storefront = storefront,
        )?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
}

private suspend fun fetchYouTubeCanvasFallback(
    mediaId: String,
    songTitleRaw: String,
    artistNameRaw: String,
    requireVertical: Boolean,
    currentIsMusicVideo: Boolean = false,
    fallbackToAnyVideo: Boolean = true,
    preferLyric: Boolean = false,
): CanvasArtwork? {
    if (requireVertical) return null
    return runCatching {
        YouTubeCanvasProvider.resolveForPlayback(
            mediaId = mediaId,
            songTitleRaw = songTitleRaw,
            artistNameRaw = artistNameRaw,
            requireVertical = false,
            currentIsMusicVideo = currentIsMusicVideo,
            fallbackToAnyVideo = fallbackToAnyVideo,
            preferLyric = preferLyric,
        )
    }.onFailure { error ->
        Timber.tag(CanvasArtworkLogTag).w(error, "YouTube canvas fallback failed for %s", mediaId)
    }.getOrNull()
}

private fun CanvasArtwork.hasRequiredCanvasVariant(requireVertical: Boolean): Boolean =
    if (requireVertical) {
        !preferredVerticalAnimationUrl.isNullOrBlank()
    } else {
        !preferredAnimationUrl.isNullOrBlank()
    }

private const val CanvasArtworkLogTag = "CanvasArtwork"

internal fun normalizeCanvasSongTitle(raw: String): String {
    val stripped =
        raw
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(
                Regex(
                    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(
                Regex(
                    "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(
                Regex(
                    "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(Regex("\\s+"), " ")
            .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun normalizeCanvasArtistName(raw: String): String {
    val first =
        raw
            .split(
                Regex(
                    "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                    RegexOption.IGNORE_CASE,
                ),
                limit = 2,
            ).firstOrNull()
            .orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}
