/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.utils.resize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckMixerSheet(
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deckState by playerConnection.deckMixerState.collectAsStateWithLifecycle()
    val crossfader by playerConnection.deckCrossfader.collectAsStateWithLifecycle()
    val deckAVol by playerConnection.deckAVolume.collectAsStateWithLifecycle()
    val deckBVol by playerConnection.deckBVolume.collectAsStateWithLifecycle()
    val deckMedia = deckState?.mediaItem
    val deckBPlayer = playerConnection.deckBPlayer
    val mainMeta by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    var deckBPos by remember { mutableLongStateOf(0L) }
    var deckBDur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(deckBPlayer, deckState) {
        while (true) {
            deckBPos = deckBPlayer?.currentPosition ?: 0L
            deckBDur = deckBPlayer?.duration?.takeIf { it > 0 } ?: 0L
            delay(200)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        Icon(painterResource(R.drawable.mix), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Deck Mix", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (deckMedia == null) "Add a second track to layer over Deck A" else "Two decks playing together",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.close), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Deck A — track card + per-deck volume
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = mainMeta?.thumbnailUrl?.resize(96, 96),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            )
                            if (mainMeta?.thumbnailUrl.isNullOrBlank()) {
                                Icon(painterResource(R.drawable.music_note), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                                    Text("A", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Text(
                                    mainMeta?.title ?: "Nothing playing",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                mainMeta?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() } ?: "Deck A — main output",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painterResource(if (deckAVol == 0f) R.drawable.volume_off else R.drawable.volume_up),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Slider(
                            value = deckAVol,
                            onValueChange = { playerConnection.setDeckAVolume(it) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                        Text("${(deckAVol * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                    }
                }
            }

            // Crossfader — the hero control
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "A",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (crossfader < 0.5f) 1f else 0.5f),
                        )
                        Text(
                            "Crossfader",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                        Text(
                            "B",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (crossfader > 0.5f) 1f else 0.5f),
                        )
                    }
                    Slider(
                        value = crossfader,
                        onValueChange = { playerConnection.setDeckCrossfader(it) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                        ),
                    )
                }
            }

            // Deck B
            if (deckMedia == null) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(painterResource(R.drawable.mix), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                        Text("Deck B empty", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Open any song menu → Add to mix",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                val artwork = deckMedia.mediaMetadata.artworkUri
                                if (artwork != null) {
                                    AsyncImage(
                                        model = artwork,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    Icon(painterResource(R.drawable.music_note), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary) {
                                        Text("B", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    Text(
                                        deckMedia.mediaMetadata.title?.toString() ?: deckMedia.mediaId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    deckMedia.mediaMetadata.artist?.toString() ?: deckMedia.mediaMetadata.albumTitle?.toString() ?: "Deck B",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            var isBPlaying by remember(deckState) { mutableStateOf(deckState?.isPlaying == true) }
                            LaunchedEffect(deckBPlayer) {
                                while (true) { isBPlaying = deckBPlayer?.isPlaying == true; delay(200) }
                            }
                            FilledIconButton(
                                onClick = { playerConnection.setDeckBPlayWhenReady(!isBPlaying) },
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Icon(painterResource(if (isBPlaying) R.drawable.pause else R.drawable.play), contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            FilledTonalIconButton(onClick = { playerConnection.removeDeckMix() }, modifier = Modifier.size(40.dp)) {
                                Icon(painterResource(R.drawable.close), contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                painterResource(if (deckBVol == 0f) R.drawable.volume_off else R.drawable.volume_up),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Slider(
                                value = deckBVol,
                                onValueChange = { playerConnection.setDeckBVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )
                            Text("${(deckBVol * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                        }

                        if (deckBDur > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Slider(
                                    value = deckBPos.toFloat().coerceIn(0f, deckBDur.toFloat()),
                                    onValueChange = { playerConnection.seekDeckB(it.toLong()) },
                                    valueRange = 0f..deckBDur.toFloat(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatMs(deckBPos), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatMs(deckBDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
