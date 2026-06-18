package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType

fun registerCoverArtComponent() {
    PlayerComponentRegistry.register(PlayerComponentType.COVER_ART.id) { _, metadata, _, _, _, _, _, _, _, _, modifier, _ ->
        CoverArtComponent(metadata, modifier)
    }
}

@Composable
fun CoverArtComponent(
    metadata: MediaMetadata?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = metadata?.thumbnailUrl,
            contentDescription = metadata?.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}
