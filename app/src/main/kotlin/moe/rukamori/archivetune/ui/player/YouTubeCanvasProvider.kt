/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import timber.log.Timber
import java.util.Locale

/**
 * Canvas provider that loops a YouTube video of the current track.
 *
 * When the current track is a real music video (flagged via the app's metadata) it is
 * used directly. Otherwise a YT Music video search is used to find the actual music
 * video (song search only surfaces audio uploads). Falls back to the current track's
 * own video when no music video exists.
 *
 * The stream is resolved through the normal InnerTube pipeline (player endpoint +
 * signature/n deobfuscation) and surfaced as a [CanvasArtwork] carrying a clipped
 * loop window (see [CanvasArtworkPlayer]).
 */
object YouTubeCanvasProvider {
    private const val LogTag = "YouTubeCanvas"

    private const val LoopStartMs = 15_000L
    private const val LoopWindowMs = 25_000L
    private const val MinDurationSeconds = 30
    private const val MaxDurationSeconds = 15 * 60

    suspend fun resolveForPlayback(
        mediaId: String,
        songTitleRaw: String,
        artistNameRaw: String,
        requireVertical: Boolean,
        currentIsMusicVideo: Boolean = false,
        fallbackToAnyVideo: Boolean = true,
    ): CanvasArtwork? {
        if (requireVertical) return null
        if (mediaId.isBlank() && songTitleRaw.isBlank() && artistNameRaw.isBlank()) return null

        Timber.tag(LogTag).d(
            "resolveForPlayback mediaId=%s song=%s artist=%s currentIsMusicVideo=%b fallbackToAnyVideo=%b",
            mediaId,
            songTitleRaw,
            artistNameRaw,
            currentIsMusicVideo,
            fallbackToAnyVideo,
        )

        val currentVideoId = mediaId.takeIf { it.isUsableVideoId() }

        if (currentIsMusicVideo) {
            val current = currentVideoId?.let { videoId -> resolveVideo(videoId) }
            if (current != null) {
                Timber.tag(LogTag).d("Current track is already a music video (%s); using it", current.videoId)
                return current.toCanvasArtwork()
            }
        }

        Timber.tag(LogTag).d("Hunting for the real music video via video search")
        val videoSearchHit =
            searchForMusicVideo(songTitleRaw, artistNameRaw, excludeVideoId = currentVideoId)
                ?.let { item -> resolveVideo(item.id) }
        if (videoSearchHit != null) {
            Timber.tag(LogTag).d("Found candidate video %s; using it", videoSearchHit.videoId)
            return videoSearchHit.toCanvasArtwork()
        }

        if (!fallbackToAnyVideo) {
            Timber.tag(LogTag).d("No music video found and fallback disabled; returning null")
            return null
        }

        Timber.tag(LogTag).d("No music video found; falling back to the current track's own video")
        val current = currentVideoId?.let { videoId -> resolveVideo(videoId) }
        if (current != null) {
            return current.toCanvasArtwork()
        }

        val songSearchHit =
            searchForVideo(songTitleRaw, artistNameRaw, excludeVideoId = currentVideoId)
                ?.let { item -> resolveVideo(item.id) }
        return songSearchHit?.toCanvasArtwork()
    }

    private fun ResolvedVideo.toCanvasArtwork(): CanvasArtwork? {
        if (streamUrl.isBlank()) return null

        Timber.tag(LogTag).d(
            "Resolved YT canvas for %s (itag=%d, %dx%d, musicVideo=%b)",
            videoId,
            format.itag,
            format.width ?: 0,
            format.height ?: 0,
            isMusicVideo,
        )

        val durationMs = durationMs
        val startMs: Long
        val endMs: Long
        if (durationMs == null) {
            startMs = LoopStartMs
            endMs = LoopStartMs + LoopWindowMs
        } else if (durationMs <= LoopStartMs) {
            startMs = 0L
            endMs = durationMs
        } else {
            startMs = LoopStartMs
            endMs = minOf(durationMs, LoopStartMs + LoopWindowMs)
        }

        return CanvasArtwork(
            name = title,
            artist = artist,
            videoUrl = streamUrl,
            loopStartMs = startMs,
            loopEndMs = endMs,
        )
    }

    private suspend fun resolveVideo(videoId: String): ResolvedVideo? {
        Timber.tag(LogTag).d("resolveVideo start for %s", videoId)
        val authState = YouTube.currentPlaybackAuthState()
        val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()

        val candidates =
            listOf(
                YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                YouTubeClient.Companion.TVHTML5,
                YouTubeClient.Companion.WEB_EMBEDDED,
                YouTubeClient.Companion.MOBILE,
                YouTubeClient.Companion.IOS,
                YouTubeClient.Companion.WEB_REMIX,
            )

        for (client in candidates) {
            val clientName = client.clientName
            val poToken = authState.resolvePlayerPoToken(client)
            Timber.tag(LogTag).d(
                "Trying client %s for %s (poToken=%s)",
                clientName,
                videoId,
                if (poToken != null) "present" else "none",
            )
            val playerResult =
                YouTube.player(
                    videoId = videoId,
                    client = client,
                    signatureTimestamp = signatureTimestamp,
                    poToken = poToken,
                    setLogin = true,
                    authState = authState,
                )
            val response = playerResult.getOrNull()
            if (response == null) {
                Timber.tag(LogTag).w(
                    "YouTube.player failed for %s via %s: %s",
                    videoId,
                    clientName,
                    playerResult.exceptionOrNull(),
                )
                continue
            }

            val streamingData = response.streamingData
            if (streamingData == null) {
                Timber.tag(LogTag).w(
                    "No streamingData for %s via %s (status=%s, reason=%s)",
                    videoId,
                    clientName,
                    response.playabilityStatus.status,
                    response.playabilityStatus.reason,
                )
                continue
            }

            val resolved = resolveFormatUrl(videoId, client, authState, streamingData)
            if (resolved == null) {
                Timber.tag(LogTag).w("No usable video format/url for %s via %s", videoId, clientName)
                continue
            }
            val (format, streamUrl) = resolved

            val details = response.videoDetails
            return ResolvedVideo(
                videoId = videoId,
                title = details?.title,
                artist = details?.author,
                durationMs = details?.lengthSeconds?.toLongOrNull()?.times(1000L),
                format = format,
                streamUrl = streamUrl,
                isMusicVideo =
                    details?.musicVideoType in setOf(MUSIC_VIDEO_TYPE_OMV, MUSIC_VIDEO_TYPE_UGC),
            )
        }

        Timber.tag(LogTag).w("All clients failed for %s", videoId)
        return null
    }

    private suspend fun resolveFormatUrl(
        videoId: String,
        client: YouTubeClient,
        authState: PlaybackAuthState,
        streamingData: PlayerResponse.StreamingData,
    ): Pair<PlayerResponse.StreamingData.Format, String>? {
        val format = pickVideoFormat(streamingData)
        if (format == null) {
            val progressive = streamingData.formats.orEmpty()
            val adaptive = streamingData.adaptiveFormats
            Timber.tag(LogTag).w(
                "No video format for %s via %s: progressive=%s adaptive=%s",
                videoId,
                client.clientName,
                progressive.map { "itag=${it.itag} ${it.mimeType} ${it.width}x${it.height} url=${it.url != null} cipher=${it.signatureCipher != null || it.cipher != null}" },
                adaptive.map { "itag=${it.itag} ${it.mimeType} ${it.width}x${it.height} url=${it.url != null} cipher=${it.signatureCipher != null || it.cipher != null}" },
            )
            return null
        }
        val cipherSnapshot = moe.rukamori.archivetune.morideobfuscator.MoriCipherRuntime.snapshot.value
        Timber.tag(LogTag).d(
            "itag=%d via %s hasUrl=%s hasCipher=%s urlHasN=%s | MoriCipher status=%s playerId=%s lastFailure=%s",
            format.itag,
            client.clientName,
            format.url != null,
            format.signatureCipher != null || format.cipher != null,
            format.url?.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true,
            cipherSnapshot.status,
            cipherSnapshot.playerId,
            cipherSnapshot.lastFailure,
        )
        val streamUrlResult = NewPipeUtils.getStreamUrl(format, videoId, client, authState)
        val streamUrl = streamUrlResult.getOrNull()
        if (streamUrl.isNullOrBlank()) {
            Timber.tag(LogTag).w(
                "getStreamUrl failed for %s itag=%d via %s: %s",
                videoId,
                format.itag,
                client.clientName,
                streamUrlResult.exceptionOrNull(),
            )
            return null
        }
        return format to streamUrl
    }

    /**
     * Prefers a format whose URL is directly usable (plain `url`, no cipher) so no
     * signature deciphering is required — WEB_REMIX only returns unencrypted URLs
     * for adaptive (DASH) formats here; progressive MP4s come signature-ciphered and
     * the MoriCipher plan exposes the `n` transform but not a signature transform.
     * MP4 (self-contained, range-downloadable for the disk cache) wins over WebM.
     */
    private fun pickVideoFormat(
        streamingData: PlayerResponse.StreamingData,
    ): PlayerResponse.StreamingData.Format? {
        fun resolvable(format: PlayerResponse.StreamingData.Format): Boolean =
            format.url != null || format.signatureCipher != null || format.cipher != null

        fun hasPlainUrl(format: PlayerResponse.StreamingData.Format): Boolean = format.url != null

        val candidates =
            buildList {
                addAll(streamingData.formats.orEmpty())
                addAll(
                    streamingData.adaptiveFormats
                        .filter { !it.isAudio && it.width != null && it.height != null },
                )
            }.filter { it.width != null && it.height != null && resolvable(it) }

        val mp4 = candidates.filter { it.mimeType.contains("mp4", ignoreCase = true) }
        val usable = mp4.ifEmpty { candidates }

        usable
            .filter { hasPlainUrl(it) }
            .filter { it.height in 240..720 }
            .minByOrNull { it.height ?: 0 }
            ?.let { return it }

        usable
            .filter { hasPlainUrl(it) }
            .minByOrNull { it.height ?: 0 }
            ?.let { return it }

        usable
            .filter { it.height in 240..720 }
            .minByOrNull { it.height ?: 0 }
            ?.let { return it }

        return usable.minByOrNull { it.height ?: 0 }
    }

    private suspend fun searchForMusicVideo(
        songTitleRaw: String,
        artistNameRaw: String,
        excludeVideoId: String? = null,
    ): SongItem? {
        val song = songTitleRaw.trim()
        val artist = artistNameRaw.trim()
        if (song.isBlank() && artist.isBlank()) return null

        val query = buildList {
            if (song.isNotBlank()) add(song)
            if (artist.isNotBlank()) add(artist)
        }.joinToString(" ")

        val result =
            YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                .also { res ->
                    if (res == null) {
                        Timber.tag(LogTag).w("video search failed for query=%s", query)
                    }
                }
                ?: return null

        val normalizedSong = song.lowercase(Locale.ROOT)
        val normalizedArtist = artist.lowercase(Locale.ROOT)

        val allSongs = result.items.filterIsInstance<SongItem>()
        Timber.tag(LogTag).d(
            "video search query=%s returned %d items, %d songs",
            query,
            result.items.size,
            allSongs.size,
        )
        allSongs.forEach { item ->
            Timber.tag(LogTag).d(
                "  video candidate id=%s title=%s artists=%s dur=%s excluded=%b titleMatch=%b",
                item.id,
                item.title,
                item.artists.joinToString("/") { it.name },
                item.duration,
                item.id == excludeVideoId,
                normalizedSong.isBlank() ||
                    item.title.lowercase(Locale.ROOT).contains(normalizedSong) ||
                    normalizedSong.contains(item.title.lowercase(Locale.ROOT)),
            )
        }

        val matched =
            allSongs
                .filter { it.id.isUsableVideoId() }
                .filter { it.id != excludeVideoId }
                .filter { item ->
                    val title = item.title.lowercase(Locale.ROOT)
                    normalizedSong.isBlank() || title.contains(normalizedSong) || normalizedSong.contains(title)
                }
        if (matched.isEmpty()) {
            Timber.tag(LogTag).w("video search found %d raw items for query=%s but none matched", result.items.size, query)
            return null
        }

        return matched
            .sortedWith(
                compareByDescending<SongItem> { item ->
                    val title = item.title.lowercase(Locale.ROOT)
                    val titleScore =
                        when {
                            title == normalizedSong -> 4
                            title.contains(normalizedSong) || normalizedSong.contains(title) -> 2
                            else -> 0
                        }
                    val artistScore =
                        if (normalizedArtist.isBlank()) {
                            1
                        } else if (item.artists.any { artistMatches(it.name, normalizedArtist) }) {
                            2
                        } else {
                            0
                        }
                    val durationScore =
                        item.duration
                            ?.let { if (it in MinDurationSeconds..MaxDurationSeconds) 1 else -2 }
                            ?: 0
                    val officialScore =
                        when {
                            title.contains("official") -> 2
                            title.contains("lyric") -> -2
                            title.contains("audio") -> -1
                            else -> 0
                        }
                    titleScore + artistScore + durationScore + officialScore
                },
            ).firstOrNull()
    }

    private suspend fun searchForVideo(
        songTitleRaw: String,
        artistNameRaw: String,
        excludeVideoId: String? = null,
    ): SongItem? {
        val song = songTitleRaw.trim()
        val artist = artistNameRaw.trim()
        if (song.isBlank() && artist.isBlank()) return null

        val query = buildList {
            if (song.isNotBlank()) add(song)
            if (artist.isNotBlank()) add(artist)
        }.joinToString(" ")

        val result =
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                .also { res ->
                    if (res == null) {
                        Timber.tag(LogTag).w("search failed for query=%s", query)
                    }
                }
                ?: return null

        val normalizedSong = song.lowercase(Locale.ROOT)
        val normalizedArtist = artist.lowercase(Locale.ROOT)

        val allSongs = result.items.filterIsInstance<SongItem>()
        Timber.tag(LogTag).d(
            "search query=%s returned %d items, %d songs",
            query,
            result.items.size,
            allSongs.size,
        )
        allSongs.forEach { item ->
            Timber.tag(LogTag).d(
                "  candidate id=%s title=%s artists=%s dur=%s mv=%b excluded=%b titleMatch=%b",
                item.id,
                item.title,
                item.artists.joinToString("/") { it.name },
                item.duration,
                item.isMusicVideo(),
                item.id == excludeVideoId,
                normalizedSong.isBlank() ||
                    item.title.lowercase(Locale.ROOT).contains(normalizedSong) ||
                    normalizedSong.contains(item.title.lowercase(Locale.ROOT)),
            )
        }

        val matched =
            allSongs
                .filter { it.id.isUsableVideoId() }
                .filter { it.id != excludeVideoId }
                .filter { item ->
                    val title = item.title.lowercase(Locale.ROOT)
                    normalizedSong.isBlank() || title.contains(normalizedSong) || normalizedSong.contains(title)
                }
        if (matched.isEmpty()) {
            Timber.tag(LogTag).w("search found %d raw items for query=%s but none matched", result.items.size, query)
            return null
        }

        return matched
            .sortedWith(
                compareByDescending<SongItem> { item ->
                    val title = item.title.lowercase(Locale.ROOT)
                    val titleScore =
                        when {
                            title == normalizedSong -> 4
                            title.contains(normalizedSong) || normalizedSong.contains(title) -> 2
                            else -> 0
                        }
                    val artistScore =
                        if (normalizedArtist.isBlank()) {
                            1
                        } else if (item.artists.any { artistMatches(it.name, normalizedArtist) }) {
                            2
                        } else {
                            0
                        }
                    val durationScore =
                        item.duration
                            ?.let { if (it in MinDurationSeconds..MaxDurationSeconds) 1 else -2 }
                            ?: 0
                    val musicVideoScore = if (item.isMusicVideo()) 5 else 0
                    titleScore + artistScore + durationScore + musicVideoScore
                },
            ).firstOrNull()
    }

    private fun SongItem.isMusicVideo(): Boolean =
        endpoint
            ?.watchEndpointMusicSupportedConfigs
            ?.watchEndpointMusicConfig
            ?.musicVideoType in setOf(MUSIC_VIDEO_TYPE_OMV, MUSIC_VIDEO_TYPE_UGC)

    private fun artistMatches(
        raw: String,
        normalizedArtist: String,
    ): Boolean {
        val name = raw.trim().lowercase(Locale.ROOT)
        if (name.isBlank()) return false
        val compactName = name.replace(" ", "")
        val compactArtist = normalizedArtist.replace(" ", "")
        return name.contains(normalizedArtist) ||
            normalizedArtist.contains(name) ||
            compactName.contains(compactArtist) ||
            compactArtist.contains(compactName)
    }

    private fun String.isUsableVideoId(): Boolean =
        length in 11..16 && all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private data class ResolvedVideo(
        val videoId: String,
        val title: String?,
        val artist: String?,
        val durationMs: Long?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val isMusicVideo: Boolean,
    )
}
