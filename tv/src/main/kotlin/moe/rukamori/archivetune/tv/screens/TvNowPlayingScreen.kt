package moe.rukamori.archivetune.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.tv.theme.TvAccentCoral
import moe.rukamori.archivetune.tv.theme.TvFocusedBorder
import moe.rukamori.archivetune.tv.theme.TvInk
import moe.rukamori.archivetune.tv.theme.TvPaper
import moe.rukamori.archivetune.tv.theme.TvSurfaceVariant
import moe.rukamori.archivetune.tv.theme.TvTextSecondary

@Composable
fun TvNowPlayingScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentItem by playerConnection.currentMediaItem.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val metadata = currentItem?.mediaMetadata

    val title = metadata?.title?.toString() ?: "No media playing"
    val artist = metadata?.artist?.toString() ?: "Select a song to start"
    val artworkUri = metadata?.artworkUri

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvInk)
            .padding(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Artwork + Playback Controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(TvSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (artworkUri != null) {
                        AsyncImage(
                            model = artworkUri,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "JUS",
                            color = TvAccentCoral,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    color = TvPaper,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = artist,
                    color = TvTextSecondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(28.dp))

                // D-Pad Transport Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvControlButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        onClick = { playerConnection.player?.seekToPrevious() }
                    )

                    TvControlButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        isPrimary = true,
                        onClick = {
                            if (isPlaying) playerConnection.player?.pause() else playerConnection.player?.play()
                        }
                    )

                    TvControlButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        onClick = { playerConnection.player?.seekToNext() }
                    )
                }
            }

            // Right Column: Queue / Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(TvSurfaceVariant.copy(alpha = 0.5f))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Now Playing on TV",
                    color = TvAccentCoral,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "BitChord TV Engine + JusPlayer Core",
                    color = TvPaper,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Full Leanback remote control navigation enabled. Press BACK on your remote to return to the catalog.",
                    color = TvTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun TvControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(if (isPrimary) 64.dp else 48.dp)
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> TvAccentCoral
                    isPrimary -> TvSurfaceVariant
                    else -> Color.Transparent
                }
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, TvFocusedBorder, CircleShape)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.White else TvPaper,
            modifier = Modifier.size(if (isPrimary) 32.dp else 24.dp)
        )
    }
}
