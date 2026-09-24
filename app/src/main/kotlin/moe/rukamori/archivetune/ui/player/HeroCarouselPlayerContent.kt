/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Timeline
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.PlayerConnection
import kotlin.math.roundToInt

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun HeroCarouselPlayerContent(
    playerConnection: PlayerConnection,
    queueWindows: List<Timeline.Window>,
    currentWindowIndex: Int,
    modifier: Modifier = Modifier,
    showOverlay: Boolean = true,
) {
    val context = LocalContext.current
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()

    val carouselState = rememberCarouselState { queueWindows.size }
    val latestQueueWindows by rememberUpdatedState(queueWindows)
    val latestCurrentIndex by rememberUpdatedState(currentWindowIndex)
    var isFirstScroll by remember { mutableStateOf(true) }

    LaunchedEffect(currentWindowIndex, queueWindows.size) {
        if (queueWindows.isNotEmpty()) {
            val target = currentWindowIndex.coerceIn(0, queueWindows.lastIndex)
            if (isFirstScroll) {
                carouselState.scrollToItem(target)
                isFirstScroll = false
            } else {
                // fast but smooth – tween 300ms avoids spring overshoot jank on mid-range devices
                carouselState.animateScrollToItem(
                    target,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { carouselState.currentItem to carouselState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { (item, isScrolling) ->
                if (!isScrolling &&
                    item in latestQueueWindows.indices &&
                    item != latestCurrentIndex
                ) {
                    playerConnection.player.seekToDefaultPosition(
                        latestQueueWindows[item].firstPeriodIndex,
                    )
                    playerConnection.player.playWhenReady = true
                }
            }
    }

    // fixed size = no BoxWithConstraints recompose on every drag frame → no stutter
    val heroHeight = 340.dp
    val heroMaxWidth = 340.dp
    // small fixed bitmap = no 1.5MP decode while animating
    val requestSize = 720
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HorizontalCenteredHeroCarousel(
            state = carouselState,
            maxItemWidth = heroMaxWidth,
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.fillMaxWidth().height(heroHeight),
        ) { index ->
            val window = queueWindows.getOrNull(index)
            val metadata = window?.mediaItem?.metadata
            val isActive = index == carouselState.currentItem
            val imageRequest =
                remember(metadata?.thumbnailUrl) {
                    ImageRequest
                        .Builder(context)
                        .data(metadata?.thumbnailUrl)
                        .size(Size(requestSize, requestSize))
                        .crossfade(120)
                        .build()
                }

            val figmaShape = RoundedCornerShape(28.dp)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(figmaShape)
                        .maskClip(figmaShape)
                        // no border during scroll = one less layer
                        .clickable {
                            if (window != null) {
                                if (index == latestCurrentIndex) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.player.seekToDefaultPosition(
                                        window.firstPeriodIndex,
                                    )
                                    playerConnection.player.playWhenReady = true
                                }
                            }
                        },
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                if (showOverlay) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.48f to Color.Black.copy(alpha = 0.08f),
                                        1f to Color.Black.copy(alpha = 0.84f),
                                    ),
                                ),
                    )
                }

                if (isActive && isPlaying) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        tonalElevation = 2.dp,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp)
                                .size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.equalizer),
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                if (showOverlay) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                    ) {
                        Text(
                            text = metadata?.title ?: "",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}