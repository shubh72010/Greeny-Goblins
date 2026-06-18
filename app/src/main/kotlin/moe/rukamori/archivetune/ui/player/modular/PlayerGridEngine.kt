@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player.modular

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.models.MediaMetadata
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

val GRID_COLUMNS = 4
const val GRID_ROWS = 9

data class DragInfo(
    val slotIndex: Int,
    val typeId: String,
    val startRow: Int,
    val startCol: Int,
    val offsetX: Float,
    val offsetY: Float,
)

@Composable
fun ModularPlayerGrid(
    layout: PlayerLayout,
    metadata: MediaMetadata?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isSeeking: Boolean,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    sliderPosition: Long?,
    cellSize: Dp,
    style: PlayerComponentStyle = PlayerComponentStyle(),
    isEditMode: Boolean = false,
    onSlotSelected: (Int) -> Unit = {},
    selectedSlotIndex: Int = -1,
    dragInfo: DragInfo? = null,
    onDragStart: (Int, Float, Float) -> Unit = { _, _, _ -> },
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onDoubleTap: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val slots = layout.slots
    val gap = 4.dp
    val cellCornerRadius = 12.dp
    val density = LocalDensity.current
    val cellSizePx = with(density) { cellSize.toPx() }

    val gridHeight = cellSize * GRID_ROWS

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight),
    ) {
        val occupiedCells = remember(slots) {
            buildSet {
                for (s in slots) {
                    for (r in s.row until s.bottom) {
                        for (c in s.col until s.right) {
                            add(r to c)
                        }
                    }
                }
            }
        }

        if (isEditMode) {
            for (row in 0 until GRID_ROWS) {
                for (col in 0 until GRID_COLUMNS) {
                    if ((row to col) in occupiedCells) continue
                    EmptyEditCell(
                        cellSize = cellSize,
                        cornerRadius = cellCornerRadius,
                        gap = gap,
                        modifier = Modifier
                            .offset(
                                x = cellSize * col + gap / 2,
                                y = cellSize * row + gap / 2,
                            )
                            .width(cellSize - gap)
                            .height(cellSize - gap)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    val tap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                            if (!change.pressed) {
                                                change.consume()
                                                return@withTimeoutOrNull true
                                            }
                                        }
                                    }
                                    if (tap != null) {
                                        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                            awaitFirstDown(requireUnconsumed = false)
                                        }
                                        if (secondDown != null) {
                                            secondDown.consume()
                                            onDoubleTap(-1)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == secondDown.id } ?: continue
                                                if (!change.pressed) {
                                                    change.consume()
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                    )
                }
            }
        }

        slots.forEachIndexed { slotIndex, slot ->
            val slotWidth = cellSize * slot.width
            val slotHeight = cellSize * slot.height
            val type = PlayerComponentType.entries.find { it.id == slot.typeId }
            val isSelected = selectedSlotIndex == slotIndex
            val isDragging = dragInfo?.slotIndex == slotIndex

            if (type != null) {
                val renderer = PlayerComponentRegistry.get(slot.typeId)

                val cellModifier = if (isEditMode) {
                    Modifier.pointerInput(slotIndex) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()

                            val tap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                    if (!change.pressed) {
                                        change.consume()
                                        return@withTimeoutOrNull true
                                    }
                                }
                            }

                            if (tap == null) {
                                onDragStart(slotIndex, 0f, 0f)

                                drag(down.id) { change ->
                                    change.consume()
                                    onDrag((change.position - change.previousPosition).x, (change.position - change.previousPosition).y)
                                }

                                onDragEnd()
                            } else {
                                val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                    awaitFirstDown(requireUnconsumed = false)
                                }

                                if (secondDown != null) {
                                    secondDown.consume()
                                    onDoubleTap(slotIndex)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == secondDown.id } ?: continue
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                    }
                                } else {
                                    onSlotSelected(slotIndex)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }

                val dragVisualModifier = if (isDragging) {
                    val d = dragInfo!!
                    Modifier
                        .offset { IntOffset(d.offsetX.roundToInt(), d.offsetY.roundToInt()) }
                        .zIndex(10f)
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .offset(
                            x = cellSize * slot.col + gap / 2,
                            y = cellSize * slot.row + gap / 2,
                        )
                        .width(slotWidth - gap)
                        .height(slotHeight - gap)
                        .then(dragVisualModifier),
                ) {
                    GridCell(
                        cellSize = slotWidth - gap,
                        cellHeight = slotHeight - gap,
                        gap = 0.dp,
                        cornerRadius = cellCornerRadius,
                        isEditMode = isEditMode,
                        isSelected = isSelected && !isDragging,
                        isDragging = isDragging,
                        modifier = cellModifier.then(
                            if (isDragging) Modifier
                                .shadow(12.dp, RoundedCornerShape(cellCornerRadius))
                            else Modifier
                        ),
                    ) {
                        if (renderer != null) {
                            renderer(
                                type,
                                metadata,
                                playerConnection,
                                isPlaying,
                                position,
                                duration,
                                isSeeking,
                                onSeek,
                                onSeekEnd,
                                sliderPosition,
                                Modifier.fillMaxSize(),
                                style,
                            )
                        }
                        if (isEditMode) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                                EditBadge(type = type)
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .offset(
                            x = cellSize * slot.col + gap / 2,
                            y = cellSize * slot.row + gap / 2,
                        )
                        .width(slotWidth - gap)
                        .height(slotHeight - gap),
                ) {
                    GridCell(
                        cellSize = slotWidth - gap,
                        cellHeight = slotHeight - gap,
                        gap = 0.dp,
                        cornerRadius = cellCornerRadius,
                        isEditMode = isEditMode,
                        isSelected = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    cellSize: Dp,
    cellHeight: Dp? = null,
    gap: Dp,
    cornerRadius: Dp,
    isEditMode: Boolean,
    isSelected: Boolean,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val height = cellHeight ?: cellSize
    Box(
        modifier = modifier
            .width(cellSize)
            .height(height)
            .padding(gap)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                when {
                    isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    isEditMode -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f)
                }
            )
            .then(
                if (isEditMode && !isDragging) {
                    Modifier.border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(cornerRadius),
                    )
                } else if (isDragging) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(cornerRadius),
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun EmptyEditCell(
    cellSize: Dp,
    cornerRadius: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(cellSize)
            .height(cellSize)
            .padding(gap)
            .clip(RoundedCornerShape(cornerRadius))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(cornerRadius),
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(moe.rukamori.archivetune.R.drawable.add),
            contentDescription = "Empty slot",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun EditBadge(type: PlayerComponentType) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(type.previewIconRes),
            contentDescription = type.name,
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
