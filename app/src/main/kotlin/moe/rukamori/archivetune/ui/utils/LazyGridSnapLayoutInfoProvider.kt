/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.math.abs

@ExperimentalFoundationApi
fun SnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = { layoutSize, itemSize ->
        (layoutSize / 2f - itemSize / 2f)
    },
    velocityThreshold: Float = 400f,
): SnapLayoutInfoProvider =
    object : SnapLayoutInfoProvider {
        private val layoutInfo: LazyGridLayoutInfo
            get() = lazyGridState.layoutInfo

        private fun averageItemDimension(): Float {
            val items = layoutInfo.visibleItemsInfo
            if (items.isEmpty()) return 0f
            val isVertical = layoutInfo.orientation == Orientation.Vertical
            return if (isVertical) {
                items.first().size.height.toFloat()
            } else {
                items.first().size.width.toFloat()
            }
        }

        override fun calculateApproachOffset(
            velocity: Float,
            decayOffset: Float,
        ): Float {
            val itemDim = averageItemDimension()
            if (itemDim <= 0f) return 0f
            val decayDistance = abs(decayOffset)
            if (decayDistance < itemDim) return 0f
            val approachDistance = (decayDistance - itemDim).coerceAtLeast(0f)
            return if (decayOffset < 0f) -approachDistance else approachDistance
        }

        override fun calculateSnapOffset(velocity: Float): Float {
            val bounds = calculateSnappingOffsetBounds()
            val lower = if (bounds.start.isFinite()) bounds.start else 0f
            val upper = if (bounds.endInclusive.isFinite()) bounds.endInclusive else 0f

            return when {
                velocity < -velocityThreshold -> lower
                velocity > velocityThreshold -> upper
                else -> {
                    // Settle to the closer boundary on release
                    if (abs(lower) < abs(upper)) lower else upper
                }
            }
        }

        fun calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
            var lowerBoundOffset = Float.NEGATIVE_INFINITY
            var upperBoundOffset = Float.POSITIVE_INFINITY

            layoutInfo.visibleItemsInfo.forEach { item ->
                val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)

                if (offset <= 0 && offset > lowerBoundOffset) {
                    lowerBoundOffset = offset
                }

                if (offset >= 0 && offset < upperBoundOffset) {
                    upperBoundOffset = offset
                }
            }

            return lowerBoundOffset.rangeTo(upperBoundOffset)
        }
    }

fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyGridLayoutInfo,
    item: LazyGridItemInfo,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float,
): Float {
    val containerSize =
        layoutInfo.singleAxisViewportSize - layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding

    val isVertical = layoutInfo.orientation == Orientation.Vertical
    val itemDim = if (isVertical) item.size.height.toFloat() else item.size.width.toFloat()
    val itemPos = if (isVertical) item.offset.y.toFloat() else item.offset.x.toFloat()

    val desiredDistance = positionInLayout(containerSize.toFloat(), itemDim)
    return itemPos - desiredDistance
}

private val LazyGridLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width
