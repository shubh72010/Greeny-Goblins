/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

@Composable
fun DraggableScrollbar(
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = LocalContentColor.current.copy(alpha = 0.8f),
    thumbColorActive: Color = MaterialTheme.colorScheme.secondary,
    thumbHeight: Dp = 72.dp,
    thumbWidth: Dp = 8.dp,
    thumbCornerRadius: Dp = 4.dp,
    trackWidth: Dp = 24.dp,
    minItemCountForScroll: Int = 15,
    minScrollRangeForDrag: Int = 5,
    headerItems: Int = 0,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var smoothedY by remember { mutableFloatStateOf(0f) }
    var smoothedThumbY by remember { mutableFloatStateOf(0f) }
    val animatedThumbY = remember { Animatable(0f) }
    var dragScrollJob by remember { mutableStateOf<Job?>(null) }

    val isUserScrolling by remember(scrollState) {
        derivedStateOf { scrollState.isScrollInProgress }
    }

    val isScrollable by remember {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visible = layoutInfo.visibleItemsInfo.size
            val contentCount = total - headerItems
            contentCount > minItemCountForScroll && contentCount > visible
        }
    }

    if (!isScrollable) return

    BoxWithConstraints(
        modifier =
            modifier
                .width(trackWidth)
                .fillMaxHeight()
                .pointerInput(scrollState) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val viewportHeight = size.height.toFloat()
                            val constThumbHeight = with(density) { thumbHeight.toPx() }
                            val maxThumbY = viewportHeight - constThumbHeight
                            smoothedThumbY = (offset.y - constThumbHeight / 2).coerceIn(0f, maxThumbY)
                        },
                        onDragEnd = {
                            dragScrollJob?.cancel()
                            isDragging = false
                        },
                        onDragCancel = {
                            dragScrollJob?.cancel()
                            isDragging = false
                        },
                    ) { change, _ ->
                        val viewportHeight = size.height.toFloat()
                        val constThumbHeight = with(density) { thumbHeight.toPx() }
                        val maxThumbY = viewportHeight - constThumbHeight

                        val targetThumbY = (change.position.y - constThumbHeight / 2).coerceIn(0f, maxThumbY)

                        val layoutInfo = scrollState.layoutInfo
                        val totalContentItems = layoutInfo.totalItemsCount - headerItems

                        val thumbSmoothingFactor =
                            when {
                                totalContentItems < 20 -> 0.4f
                                totalContentItems < 50 -> 0.6f
                                else -> 0.8f
                            }

                        smoothedThumbY = smoothedThumbY * (1f - thumbSmoothingFactor) + targetThumbY * thumbSmoothingFactor

                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) return@detectDragGestures

                        val maxScrollIndex = max(1, totalContentItems - visibleItems.size)

                        if (maxScrollIndex > minScrollRangeForDrag) {
                            val touchProgress = (change.position.y / size.height).coerceIn(0f, 1f)

                            val listSmoothingFactor =
                                when {
                                    totalContentItems < 20 -> 0.2f
                                    totalContentItems < 50 -> 0.5f
                                    else -> 0.8f
                                }

                            smoothedY = smoothedY * (1f - listSmoothingFactor) + touchProgress * listSmoothingFactor

                            val targetFractionalIndex = smoothedY * maxScrollIndex
                            val targetIndex =
                                (headerItems + targetFractionalIndex.toInt())
                                    .coerceIn(headerItems, layoutInfo.totalItemsCount - 1)

                            val fraction = (targetFractionalIndex - targetFractionalIndex.toInt()).coerceIn(0f, 1f)
                            val estimatedItemHeightPx = visibleItems.firstOrNull()?.size?.toFloat() ?: 0f
                            val targetOffsetPx = (fraction * estimatedItemHeightPx).toInt()

                            dragScrollJob?.cancel()
                            dragScrollJob = coroutineScope.launch {
                                try {
                                    scrollState.scrollToItem(
                                        index = targetIndex,
                                        scrollOffset = targetOffsetPx,
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                },
    ) {
        val viewportHeight = with(density) { this@BoxWithConstraints.maxHeight.toPx() }
        val constThumbHeight = with(density) { thumbHeight.toPx() }

        val targetThumbY by remember {
            derivedStateOf {
                val layoutInfo = scrollState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@derivedStateOf 0f

                val totalContentItems = layoutInfo.totalItemsCount - headerItems
                if (totalContentItems <= 0) return@derivedStateOf 0f

                val maxScrollIndex = max(1, totalContentItems - visibleItems.size)
                if (maxScrollIndex <= minScrollRangeForDrag) return@derivedStateOf 0f

                val rawIndex = (scrollState.firstVisibleItemIndex - headerItems).coerceAtLeast(0)

                val scrollProgress = rawIndex.toFloat() / maxScrollIndex

                val maxThumbY = viewportHeight - constThumbHeight
                (scrollProgress * maxThumbY).coerceIn(0f, maxThumbY)
            }
        }

        LaunchedEffect(targetThumbY, isDragging, isUserScrolling, smoothedThumbY) {
            val layoutInfo = scrollState.layoutInfo
            val totalContentItems = layoutInfo.totalItemsCount - headerItems

            when {
                isDragging -> {
                    if (totalContentItems < 50) {
                        animatedThumbY.snapTo(targetThumbY)
                    } else {
                        animatedThumbY.snapTo(smoothedThumbY)
                    }
                }

                isUserScrolling -> {
                    if (totalContentItems < 30) {
                        animatedThumbY.snapTo(targetThumbY)
                    } else {
                        animatedThumbY.snapTo(targetThumbY)
                    }
                }

                else -> {
                    animatedThumbY.animateTo(
                        targetValue = targetThumbY,
                        animationSpec =
                            spring(
                                stiffness = if (totalContentItems < 30) 80f else 150f,
                                dampingRatio = if (totalContentItems < 30) 1.5f else 0.9f,
                            ),
                    )
                }
            }
        }

        Canvas(
            modifier =
                Modifier
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
        ) {
            val color = if (isDragging) thumbColorActive else thumbColor
            val cornerRadiusPx = thumbCornerRadius.toPx()

            drawRoundRect(
                color = color,
                topLeft = Offset(0f, animatedThumbY.value),
                size = Size(this.size.width, constThumbHeight),
                cornerRadius = CornerRadius(cornerRadiusPx),
            )
        }
    }
}
