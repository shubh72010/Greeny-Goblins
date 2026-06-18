package moe.rukamori.archivetune.ui.player.modular

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.PlayerConnection



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModularExpandedPlayer(
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isSeeking: Boolean,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    sliderPosition: Long?,
    modifier: Modifier = Modifier,
    onEditModeChanged: ((Boolean) -> Unit)? = null,
    onQueueClick: () -> Unit = {},
    onLyricsClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
) {
    ComponentInitializer.ensureInitialized()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val layoutPreset by PlayerLayoutRepository.observeLayoutPreset(context)
        .collectAsState(initial = PlayerLayoutPreset.CLASSIC)
    var layout by remember(layoutPreset) {
        mutableStateOf(PlayerLayoutRepository.getLayout(layoutPreset))
    }

    LaunchedEffect(layoutPreset) {
        if (layoutPreset == PlayerLayoutPreset.CUSTOM) {
            PlayerLayoutRepository.getCustomLayout(context)?.let { layout = it }
        } else {
            layout = PlayerLayoutRepository.getLayout(layoutPreset)
        }
    }

    var isEditMode by remember { mutableStateOf(false) }
    LaunchedEffect(isEditMode) {
        onEditModeChanged?.invoke(isEditMode)
    }
    var editingSlots by remember { mutableStateOf(listOf<PlayerGridSlot>()) }
    var selectedSlotIndex by remember { mutableIntStateOf(-1) }
    var showPalette by remember { mutableStateOf(false) }
    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
    var editingTextSizeScale by remember { mutableStateOf(1f) }
    var editingPlayButtonScale by remember { mutableStateOf(1f) }
    var editingShowTimeOnSeekBar by remember { mutableStateOf(true) }
    val density = LocalDensity.current

    fun enterEditMode() {
        editingSlots = layout.slots.toMutableList()
        editingTextSizeScale = layout.textSizeScale
        editingPlayButtonScale = layout.playButtonScale
        editingShowTimeOnSeekBar = layout.showTimeOnSeekBar
        selectedSlotIndex = -1
        showPalette = false
        isEditMode = true
    }

    fun exitEditMode() {
        isEditMode = false
        selectedSlotIndex = -1
        showPalette = false
        dragInfo = null
    }

    fun saveLayout() {
        val finalLayout = layout.copy(
            slots = editingSlots,
            textSizeScale = editingTextSizeScale,
            playButtonScale = editingPlayButtonScale,
            showTimeOnSeekBar = editingShowTimeOnSeekBar,
        )
        layout = finalLayout
        scope.launch {
            PlayerLayoutRepository.setLayoutPreset(context, PlayerLayoutPreset.CUSTOM)
            PlayerLayoutRepository.saveCustomLayout(context, finalLayout)
        }
        exitEditMode()
    }

    fun moveSlot(fromIndex: Int, toRow: Int, toCol: Int) {
        if (fromIndex !in editingSlots.indices) return
        val slot = editingSlots[fromIndex]
        val newSlot = slot.copy(row = toRow, col = toCol)

        val outOfBounds = toRow < 0 || toRow >= GRID_ROWS ||
            toCol < 0 || toCol + newSlot.width > GRID_COLUMNS ||
            toRow + newSlot.height > GRID_ROWS
        if (outOfBounds) return

        val collision = editingSlots.withIndex().any { (i, other) ->
            i != fromIndex && newSlot.overlaps(other)
        }
        if (collision) return

        editingSlots = editingSlots.toMutableList().apply {
            set(fromIndex, newSlot)
        }
    }

    fun tryMoveSlot(fromIndex: Int, toRow: Int, toCol: Int): Boolean {
        if (fromIndex !in editingSlots.indices) return false
        val slot = editingSlots[fromIndex]
        val newSlot = slot.copy(row = toRow, col = toCol)

        val outOfBounds = toRow < 0 || toRow >= GRID_ROWS ||
            toCol < 0 || toCol + newSlot.width > GRID_COLUMNS ||
            toRow + newSlot.height > GRID_ROWS
        if (outOfBounds) return false

        val collides = editingSlots.withIndex().any { (i, other) ->
            i != fromIndex && newSlot.overlaps(other)
        }
        if (collides) return false

        editingSlots = editingSlots.toMutableList().apply {
            set(fromIndex, newSlot)
        }
        return true
    }

    fun resizeSlot(index: Int, targetWidth: Int, targetHeight: Int) {
        if (index !in editingSlots.indices) return
        val slot = editingSlots[index]
        val resized = slot.copy(width = targetWidth, height = targetHeight)
        val outOfBounds = resized.col + resized.width > GRID_COLUMNS ||
            resized.row + resized.height > GRID_ROWS
        if (outOfBounds) return

        val collision = editingSlots.withIndex().any { (i, other) ->
            i != index && resized.overlaps(other)
        }
        if (collision) return

        editingSlots = editingSlots.toMutableList().apply {
            set(index, resized)
        }
    }

    val activeLayout = if (isEditMode) layout.copy(slots = editingSlots) else layout
    val activeStyle = PlayerComponentStyle(
        textSizeScale = if (isEditMode) editingTextSizeScale else layout.textSizeScale,
        playButtonScale = if (isEditMode) editingPlayButtonScale else layout.playButtonScale,
        showTimeOnSeekBar = if (isEditMode) editingShowTimeOnSeekBar else layout.showTimeOnSeekBar,
    )

    BackHandler(enabled = isEditMode) {
        exitEditMode()
    }

    Column(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPlayerActions provides PlayerActions(
            onQueueClick = onQueueClick,
            onLyricsClick = onLyricsClick,
            onSleepTimerClick = onSleepTimerClick,
        )) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val cellByWidth = maxWidth / GRID_COLUMNS
            val cellByHeight = maxHeight / GRID_ROWS
            val cellSize = minOf(cellByWidth, cellByHeight)
            val gridWidth = cellSize * GRID_COLUMNS

            val gridModifier = if (!isEditMode) {
                Modifier
                    .width(gridWidth)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { enterEditMode() },
                    )
            } else {
                Modifier.width(gridWidth)
            }

            ModularPlayerGrid(
                layout = activeLayout,
                style = activeStyle,
                metadata = mediaMetadata,
                playerConnection = playerConnection,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                isSeeking = isSeeking,
                onSeek = onSeek,
                onSeekEnd = onSeekEnd,
                sliderPosition = sliderPosition,
                cellSize = cellSize,
                isEditMode = isEditMode,
                selectedSlotIndex = selectedSlotIndex,
                onSlotSelected = { index -> selectedSlotIndex = index },
                dragInfo = dragInfo,
                onDragStart = { slotIndex, _, _ ->
                    val slot = editingSlots.getOrNull(slotIndex) ?: return@ModularPlayerGrid
                    selectedSlotIndex = slotIndex
                    dragInfo = DragInfo(
                        slotIndex = slotIndex,
                        typeId = slot.typeId,
                        startRow = slot.row,
                        startCol = slot.col,
                        offsetX = 0f,
                        offsetY = 0f,
                    )
                },
                onDrag = { dx, dy ->
                    dragInfo?.let { info ->
                        val newOffsetX = info.offsetX + dx
                        val newOffsetY = info.offsetY + dy

                        val cellSizePx = with(density) { cellSize.toPx() }
                        val colDelta = (newOffsetX / cellSizePx).roundToInt()
                        val rowDelta = (newOffsetY / cellSizePx).roundToInt()

                        if (colDelta != 0 || rowDelta != 0) {
                            val targetRow = (info.startRow + rowDelta).coerceIn(0, GRID_ROWS - 1)
                            val targetCol = (info.startCol + colDelta).coerceIn(0, GRID_COLUMNS - 1)
                            val slot = editingSlots.getOrNull(info.slotIndex)

                            if (slot != null && (targetRow != slot.row || targetCol != slot.col)) {
                                val moved = tryMoveSlot(info.slotIndex, targetRow, targetCol)
                                if (moved) {
                                    dragInfo = info.copy(
                                        offsetX = 0f,
                                        offsetY = 0f,
                                        startRow = targetRow,
                                        startCol = targetCol,
                                    )
                                    return@let
                                }
                            }
                        }

                        dragInfo = info.copy(
                            offsetX = newOffsetX,
                            offsetY = newOffsetY,
                        )
                    }
                },
                onDragEnd = {
                    dragInfo?.let { info ->
                        val slot = editingSlots.getOrNull(info.slotIndex)
                        if (slot != null) {
                            val cellSizePx = with(density) { cellSize.toPx() }
                            val colDelta = (info.offsetX / cellSizePx).roundToInt()
                            val rowDelta = (info.offsetY / cellSizePx).roundToInt()
                            val targetRow = (info.startRow + rowDelta).coerceIn(0, GRID_ROWS - 1)
                            val targetCol = (info.startCol + colDelta).coerceIn(0, GRID_COLUMNS - 1)
                            moveSlot(info.slotIndex, targetRow, targetCol)
                        }
                        dragInfo = null
                    }
                },
                onDoubleTap = { slotIndex ->
                    if (slotIndex >= 0) {
                        val s = editingSlots.getOrNull(slotIndex) ?: return@ModularPlayerGrid
                        val nextW = if (s.width < 4) s.width + 1 else 1
                        resizeSlot(slotIndex, nextW, s.height)
                    }
                },
                modifier = gridModifier,
            )
        }

        AnimatedVisibility(
            visible = isEditMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        ) {
            EditModeToolbar(
                selectedSlotIndex = selectedSlotIndex,
                editingSlots = editingSlots,
                showPalette = showPalette,
                onTogglePalette = { showPalette = !showPalette },
                onRemoveComponent = {
                    if (selectedSlotIndex in editingSlots.indices) {
                        editingSlots = editingSlots.toMutableList().apply { removeAt(selectedSlotIndex) }
                        selectedSlotIndex = -1
                    }
                },
                onAddComponent = { type ->
                    val pos = findEmptyCell(editingSlots, GRID_ROWS, GRID_COLUMNS)
                    if (pos != null) {
                        val newSlot = PlayerGridSlot(
                            typeId = type.id,
                            row = pos.first,
                            col = pos.second,
                            width = type.defaultWidth,
                            height = type.defaultHeight,
                        )
                        editingSlots = editingSlots + newSlot
                        selectedSlotIndex = editingSlots.size - 1
                    }
                },
                onResizeSlot = { index, width, height ->
                    resizeSlot(index, width, height)
                },
                onPresetSelected = { preset ->
                    val newLayout = PlayerLayoutRepository.getLayout(preset)
                    editingSlots = newLayout.slots.toMutableList()
                    editingTextSizeScale = newLayout.textSizeScale
                    editingPlayButtonScale = newLayout.playButtonScale
                    editingShowTimeOnSeekBar = newLayout.showTimeOnSeekBar
                    selectedSlotIndex = -1
                    showPalette = false
                },
                onSave = { saveLayout() },
                onCancel = { exitEditMode() },
                onReset = {
                    val classic = DefaultPlayerLayouts.classic
                    editingSlots = classic.slots.toMutableList()
                    editingTextSizeScale = classic.textSizeScale
                    editingPlayButtonScale = classic.playButtonScale
                    editingShowTimeOnSeekBar = classic.showTimeOnSeekBar
                    selectedSlotIndex = -1
                    showPalette = false
                },
                textSizeScale = editingTextSizeScale,
                onTextSizeScaleChange = { editingTextSizeScale = it },
                playButtonScale = editingPlayButtonScale,
                onPlayButtonScaleChange = { editingPlayButtonScale = it },
                showTimeOnSeekBar = editingShowTimeOnSeekBar,
                onShowTimeOnSeekBarChange = { editingShowTimeOnSeekBar = it },
            )
        }
    }
}

}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditModeToolbar(
    selectedSlotIndex: Int,
    editingSlots: List<PlayerGridSlot>,
    showPalette: Boolean,
    onTogglePalette: () -> Unit,
    onRemoveComponent: () -> Unit,
    onAddComponent: (PlayerComponentType) -> Unit,
    onResizeSlot: (Int, Int, Int) -> Unit,
    onPresetSelected: (PlayerLayoutPreset) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    textSizeScale: Float,
    onTextSizeScaleChange: (Float) -> Unit,
    playButtonScale: Float,
    onPlayButtonScaleChange: (Float) -> Unit,
    showTimeOnSeekBar: Boolean,
    onShowTimeOnSeekBarChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal,
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onTogglePalette,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (showPalette) stringResource(R.string.modular_done_adding)
                        else stringResource(R.string.modular_add_component),
                        fontSize = 11.sp,
                    )
                }

                if (selectedSlotIndex in editingSlots.indices) {
                    OutlinedButton(
                        onClick = onRemoveComponent,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.modular_remove_component), fontSize = 11.sp)
                    }
                }
            }

            if (showPalette) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PlayerComponentType.entries) { type ->
                        ComponentPaletteChipSmall(
                            type = type,
                            onClick = { onAddComponent(type) },
                        )
                    }
                }
            }

            if (selectedSlotIndex in editingSlots.indices) {
                Spacer(Modifier.height(8.dp))
                val selectedSlot = editingSlots[selectedSlotIndex]
                val currentWidth = selectedSlot.width
                val currentHeight = selectedSlot.height

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("W", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (w in 1..4) {
                        val slotW = selectedSlot.copy(width = w, height = currentHeight)
                        val collidesW = editingSlots.withIndex().any { (i, other) ->
                            i != selectedSlotIndex && slotW.overlaps(other)
                        }
                        val oobW = slotW.col + w > GRID_COLUMNS
                        FilledTonalButton(
                            onClick = { onResizeSlot(selectedSlotIndex, w, currentHeight) },
                            enabled = !collidesW && !oobW,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = "$w",
                                fontSize = 11.sp,
                                color = if (w == currentWidth)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("H", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (h in 1..4) {
                        val slotH = selectedSlot.copy(width = currentWidth, height = h)
                        val collidesH = editingSlots.withIndex().any { (i, other) ->
                            i != selectedSlotIndex && slotH.overlaps(other)
                        }
                        val oobH = slotH.row + h > GRID_ROWS
                        FilledTonalButton(
                            onClick = { onResizeSlot(selectedSlotIndex, currentWidth, h) },
                            enabled = !collidesH && !oobH,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = "$h",
                                fontSize = 11.sp,
                                color = if (h == currentHeight)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    PlayerLayoutPreset.CLASSIC to R.string.modular_layout_classic,
                    PlayerLayoutPreset.COMPACT to R.string.modular_layout_compact,
                    PlayerLayoutPreset.MINIMAL to R.string.modular_layout_minimal,
                ).forEach { (preset, labelRes) ->
                    FilledTonalButton(
                        onClick = { onPresetSelected(preset) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(text = stringResource(labelRes), fontSize = 10.sp, maxLines = 1)
                    }
                }
            }

            Text("Text size", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("0.5x", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = textSizeScale,
                    onValueChange = onTextSizeScaleChange,
                    valueRange = 0.5f..2f,
                    modifier = Modifier.weight(1f),
                )
                Text("2x", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Play button size", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("0.5x", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = playButtonScale,
                    onValueChange = onPlayButtonScaleChange,
                    valueRange = 0.5f..2f,
                    modifier = Modifier.weight(1f),
                )
                Text("2x", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Show time on seek bar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(
                    checked = showTimeOnSeekBar,
                    onCheckedChange = onShowTimeOnSeekBarChange,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.modular_reset_layout), fontSize = 11.sp)
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(stringResource(R.string.modular_done_adding), fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ComponentPaletteChipSmall(
    type: PlayerComponentType,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(type.previewIconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(type.displayNameRes),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
            )
        }
    }
}

private fun findEmptyCell(slots: List<PlayerGridSlot>, rows: Int, cols: Int): Pair<Int, Int>? {
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val occupied = slots.any { s -> s.overlaps(PlayerGridSlot("", r, c, 1, 1)) }
            if (!occupied) return r to c
        }
    }
    return null
}
