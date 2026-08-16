package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.CanvasSource
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.constants.CanvasSourceKey
import moe.rukamori.archivetune.constants.JusPlayerCanvasKey
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Locale

fun registerCanvasArtworkComponent() {
    PlayerComponentRegistry.register(PlayerComponentType.CANVAS_ARTWORK.id) { _, metadata, playerConnection, isPlaying, _, _, _, _, _, _, modifier, _ ->
        val (canvasEnabled) = rememberPreference(JusPlayerCanvasKey, defaultValue = false)
        if (canvasEnabled) {
            CanvasArtworkComponent(metadata, playerConnection, isPlaying, modifier)
        }
    }
}

@Composable
fun CanvasArtworkComponent(
    metadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
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
        val country = java.util.Locale.getDefault().country
        if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
    }

    LaunchedEffect(metadata?.id) {
        val meta = metadata ?: return@LaunchedEffect
        if (meta.title.isBlank() || meta.artists.firstOrNull()?.name.orEmpty().isBlank()) {
            canvasArtwork = null
            return@LaunchedEffect
        }

        if (fetchInFlight) return@LaunchedEffect
        fetchInFlight = true
        try {
            canvasArtwork =
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
                )
        } finally {
            fetchInFlight = false
        }
    }

    val artwork = canvasArtwork
    if (artwork != null) {
        CanvasArtworkPlayer(
            primaryUrl = artwork.preferredAnimationUrl,
            fallbackUrl = artwork.preferredVerticalAnimationUrl.takeIf { !it.isNullOrBlank() }
                ?: artwork.preferredAnimationUrl,
            isPlaying = isPlaying,
            clipStartMs = artwork.loopStartMs,
            clipEndMs = artwork.loopEndMs,
            modifier = modifier.fillMaxSize(),
        )
    }
}
