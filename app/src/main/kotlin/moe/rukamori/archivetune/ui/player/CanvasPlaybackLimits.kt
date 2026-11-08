package moe.rukamori.archivetune.ui.player

object CanvasPlaybackLimits {
    const val MAX_WIDTH = 1200
}

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ArtworkMeshBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) { content() }

@Composable
fun rememberArtworkMesh(): Any? = null
