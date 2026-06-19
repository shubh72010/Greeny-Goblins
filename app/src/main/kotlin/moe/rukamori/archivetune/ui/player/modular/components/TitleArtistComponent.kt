package moe.rukamori.archivetune.ui.player.modular.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentRegistry
import moe.rukamori.archivetune.ui.player.modular.PlayerComponentType

fun registerTitleArtistComponents() {
    PlayerComponentRegistry.register(PlayerComponentType.TITLE.id) { _, metadata, _, _, _, _, _, _, _, _, modifier, style ->
        TitleComponent(metadata, modifier, style.textSizeScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.ARTIST.id) { _, metadata, _, _, _, _, _, _, _, _, modifier, style ->
        ArtistComponent(metadata, modifier, style.textSizeScale)
    }
    PlayerComponentRegistry.register(PlayerComponentType.TITLE_ARTIST.id) { _, metadata, _, _, _, _, _, _, _, _, modifier, style ->
        TitleArtistCombinedComponent(metadata, modifier, style.textSizeScale)
    }
}

@Composable
fun TitleComponent(
    metadata: MediaMetadata?,
    modifier: Modifier = Modifier,
    textSizeScale: Float = 1f,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = metadata?.title ?: "",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * textSizeScale,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxSize()
                .basicMarquee()
                .padding(horizontal = 12.dp),
        )
    }
}

@Composable
fun ArtistComponent(
    metadata: MediaMetadata?,
    modifier: Modifier = Modifier,
    textSizeScale: Float = 1f,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = metadata?.artists?.joinToString(", ") { it.name } ?: "",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = MaterialTheme.typography.titleMedium.fontSize * textSizeScale,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        )
    }
}

@Composable
fun TitleArtistCombinedComponent(
    metadata: MediaMetadata?,
    modifier: Modifier = Modifier,
    textSizeScale: Float = 1f,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = metadata?.title ?: "",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * textSizeScale,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = metadata?.artists?.joinToString(", ") { it.name } ?: "",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * textSizeScale,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}
