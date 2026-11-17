package moe.rukamori.archivetune.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.tv.components.TvNavTab
import moe.rukamori.archivetune.tv.components.TvNavigationRail
import moe.rukamori.archivetune.tv.screens.TvHomeScreen
import moe.rukamori.archivetune.tv.screens.TvLibraryScreen
import moe.rukamori.archivetune.tv.screens.TvNowPlayingScreen
import moe.rukamori.archivetune.tv.screens.TvSearchScreen
import moe.rukamori.archivetune.tv.screens.TvSettingsScreen
import moe.rukamori.archivetune.tv.theme.TvAccentCoral
import moe.rukamori.archivetune.tv.theme.TvFocusedBorder
import moe.rukamori.archivetune.tv.theme.TvInk
import moe.rukamori.archivetune.tv.theme.TvPaper
import moe.rukamori.archivetune.tv.theme.TvSurfaceVariant
import moe.rukamori.archivetune.tv.theme.TvTextSecondary

@Composable
fun TvApp(
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(TvNavTab.HOME) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }

    val playerConnection = LocalPlayerConnection.current
    val currentMediaItem by playerConnection?.currentMediaItem?.collectAsState() ?: remember { mutableStateOf(null) }

    BackHandler(enabled = isNowPlayingOpen) {
        isNowPlayingOpen = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvInk)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            TvNavigationRail(
                selectedTab = currentTab,
                onTabSelected = { currentTab = it }
            )

            Spacer(modifier = Modifier.width(24.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                when (currentTab) {
                    TvNavTab.HOME -> TvHomeScreen()
                    TvNavTab.SEARCH -> TvSearchScreen()
                    TvNavTab.LIBRARY -> TvLibraryScreen()
                    TvNavTab.SETTINGS -> TvSettingsScreen()
                }

                // Persistent Mini Player on TV if audio is loaded
                if (currentMediaItem != null && !isNowPlayingOpen) {
                    TvMiniPlayer(
                        onClick = { isNowPlayingOpen = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, end = 24.dp)
                    )
                }
            }
        }

        // Full-screen Now Playing Overlay
        AnimatedVisibility(
            visible = isNowPlayingOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TvNowPlayingScreen(onDismiss = { isNowPlayingOpen = false })
        }
    }
}

@Composable
private fun TvMiniPlayer(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentItem by playerConnection.currentMediaItem.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val metadata = currentItem?.mediaMetadata

    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TvSurfaceVariant)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, TvFocusedBorder, RoundedCornerShape(16.dp))
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
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (metadata?.artworkUri != null) {
                AsyncImage(
                    model = metadata.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TvAccentCoral)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata?.title?.toString() ?: "Unknown Title",
                    color = TvPaper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metadata?.artist?.toString() ?: "Unknown Artist",
                    color = TvTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (isPlaying) "Playing · Select to expand" else "Paused · Select to expand",
                color = if (isFocused) TvAccentCoral else TvTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
