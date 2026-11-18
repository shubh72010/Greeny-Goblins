package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.CanvasSourceKey
import moe.rukamori.archivetune.constants.JusPlayerCanvasKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.CanvasSource
import moe.rukamori.archivetune.ui.player.CanvasVideoKind
import moe.rukamori.archivetune.ui.player.modular.LocalThumbnailCornerRadius
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.ui.utils.ThumbnailShapeKind
import moe.rukamori.archivetune.ui.utils.rememberThumbnailShape
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import timber.log.Timber
import java.util.Locale

fun registerCanvasArtworkComponent() {
    PlayerComponentRegistry.register(PlayerComponentType.CANVAS_ARTWORK.id) { _, metadata, playerConnection, isPlaying, _, _, _, _, _, _, modifier, _ ->
        val (canvasEnabled) = rememberPreference(JusPlayerCanvasKey, defaultValue = true)
        val (playerBackground) = rememberEnumPreference(PlayerBackgroundStyleKey, defaultValue = PlayerBackgroundStyle.JUSPLAYER_CANVAS)
        // The full-screen background already plays the canvas in that style; don't play it twice.
        val canvasActive = canvasEnabled && playerBackground != PlayerBackgroundStyle.JUSPLAYER_CANVAS
        CanvasArtworkComponent(metadata, playerConnection, isPlaying, canvasActive, modifier)
    }
}

@Composable
fun CanvasArtworkComponent(
    metadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    canvasActive: Boolean,
    modifier: Modifier = Modifier,
) {
    var canvasArtwork by remember(metadata?.id) {
        mutableStateOf<CanvasArtwork?>(null)
    }
    var fetchInFlight by remember(metadata?.id) {
        mutableStateOf(false)
    }
    val canvasSource by rememberEnumPreference(CanvasSourceKey, CanvasSource.AUTO)

    val storefront = remember {
        val country = Locale.getDefault().country
        if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
    }

    LaunchedEffect(metadata?.id, canvasSource, canvasActive) {
        val meta = metadata
        if (!canvasActive || meta == null) {
            canvasArtwork = null
            fetchInFlight = false
            return@LaunchedEffect
        }
        if (meta.title.isBlank() && meta.id.isBlank()) {
            canvasArtwork = null
            return@LaunchedEffect
        }

        if (fetchInFlight) return@LaunchedEffect
        fetchInFlight = true
        try {
            // ponytail: failsafe — one failed canvas lookup must not kill the player screen.
            canvasArtwork =
                runCatching {
                    resolveCanvasArtworkForPlayback(
                        mediaId = meta.id,
                        songTitleRaw = meta.title,
                        artistNameRaw = meta.artists.firstOrNull()?.name.orEmpty(),
                        albumId = meta.album?.id,
                        albumTitleRaw = meta.album?.title,
                        storefront = storefront,
                        requireVertical = false,
                        allowNetwork = true,
                        currentIsMusicVideo = meta.isMusicVideo,
                        canvasSource = canvasSource,
                        videoKind = CanvasVideoKind.MUSIC,
                    )
                }.onFailure { error ->
                    Timber.tag("CanvasArtwork").w(error, "Canvas lookup failed for %s", meta.id)
                }.getOrNull()
        } finally {
            fetchInFlight = false
        }
    }

    val artwork = canvasArtwork
    Box(modifier = modifier.fillMaxSize()) {
        // Always behind the video: if the stream is slow, fails, or canvas is off,
        // the blurred cover is what the user sees.
        CanvasFallbackArtwork(metadata, Modifier.fillMaxSize())
        if (canvasActive && artwork != null) {
            // ponytail: respect the thumbnail-shape pref — was unclipped, so the shape
            // option silently did nothing whenever canvas video was showing.
            val shape = rememberThumbnailShape(ThumbnailShapeKind.SONG, LocalThumbnailCornerRadius.current, metadata?.id)
            CanvasArtworkPlayer(
                primaryUrl = artwork.preferredAnimationUrl,
                fallbackUrl = artwork.preferredVerticalAnimationUrl.takeIf { !it.isNullOrBlank() }
                    ?: artwork.preferredAnimationUrl,
                isPlaying = isPlaying,
                clipStartMs = artwork.loopStartMs,
                clipEndMs = artwork.loopEndMs,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        }
    }
}

/** Blurred cover shown whenever the canvas is off or a lookup failed. */
@Composable
private fun CanvasFallbackArtwork(
    metadata: MediaMetadata?,
    modifier: Modifier = Modifier,
) {
    val shape = rememberThumbnailShape(ThumbnailShapeKind.SONG, LocalThumbnailCornerRadius.current, metadata?.id)
    Box(modifier = modifier.fillMaxSize().clip(shape)) {
        val thumbnailUrl = metadata?.thumbnailUrl
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
    }
}
