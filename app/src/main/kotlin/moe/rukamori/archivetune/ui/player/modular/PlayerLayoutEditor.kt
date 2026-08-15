package moe.rukamori.archivetune.ui.player.modular

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R

private fun findEmptyCell(slots: List<PlayerGridSlot>, rows: Int, cols: Int): Pair<Int, Int>? {
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val occupied = slots.any { s -> s.overlaps(PlayerGridSlot("", r, c, 1, 1)) }
            if (!occupied) return r to c
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerLayoutEditor(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    var editingLayout by remember { mutableStateOf(DefaultPlayerLayouts.classic) }

    LaunchedEffect(Unit) {
        val preset = PlayerLayoutRepository.getLayoutPreset(context)
        editingLayout = if (preset == PlayerLayoutPreset.CUSTOM) {
            PlayerLayoutRepository.getCustomLayout(context) ?: PlayerLayoutRepository.getLayout(PlayerLayoutPreset.CLASSIC)
        } else {
            PlayerLayoutRepository.getLayout(preset)
        }
        loaded = true
    }

    var gridSlots by remember(editingLayout, loaded) {
        mutableStateOf(editingLayout.slots.toMutableList())
    }
    var selectedSlotIndex by remember { mutableStateOf(-1) }
    var showPalette by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            ),
    ) {
        Text(
            text = stringResource(R.string.modular_layout_editor),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                PlayerLayoutPreset.CLASSIC to R.string.modular_layout_classic,
                PlayerLayoutPreset.COMPACT to R.string.modular_layout_compact,
                PlayerLayoutPreset.MINIMAL to R.string.modular_layout_minimal,
            ).forEach { (preset, labelRes) ->
                FilledTonalButton(
                    onClick = {
                        val newLayout = PlayerLayoutRepository.getLayout(preset)
                        editingLayout = newLayout
                        gridSlots = newLayout.slots.toMutableList()
                        selectedSlotIndex = -1
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = stringResource(labelRes), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cellByWidth = maxWidth / GRID_COLUMNS
            val cellByHeight = maxHeight / GRID_ROWS
            val cellSize = minOf(cellByWidth, cellByHeight)
            val gridWidth = cellSize * GRID_COLUMNS

            Box(
                modifier = Modifier
                    .width(gridWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                Column(modifier = Modifier.padding(2.dp)) {
                    var row = 0
                    while (row < GRID_ROWS) {
                        val rowSlots = gridSlots.filter { it.row == row }
                        if (rowSlots.isEmpty()) {
                            Box(modifier = Modifier.height(cellSize).fillMaxWidth())
                            row++
                            continue
                        }
                        val maxRow = rowSlots.maxOf { it.bottom }
                        val rowHeight = cellSize * (maxRow - row)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                        ) {
                            var col = 0
                            while (col < GRID_COLUMNS) {
                                val slot = rowSlots.firstOrNull { it.col == col }
                                if (slot != null && slot.col == col) {
                                    val index = gridSlots.indexOf(slot)
                                    val type = PlayerComponentType.entries.find { it.id == slot.typeId }
                                    val sw = cellSize * slot.width
                                    val sh = cellSize * slot.height

                                    Box(
                                        modifier = Modifier
                                            .width(sw)
                                            .height(sh)
                                            .padding(1.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (selectedSlotIndex == index)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                                            )
                                            .clickable { selectedSlotIndex = index },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(2.dp),
                                        ) {
                                            if (type != null) {
                                                Icon(
                                                    painter = painterResource(type.previewIconRes),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(if (slot.width >= 2) 20.dp else 14.dp),
                                                    tint = if (selectedSlotIndex == index)
                                                        MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (slot.width >= 2) {
                                                    Text(
                                                        text = stringResource(type.displayNameRes),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 8.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                            }
                        }
                        row = maxRow
                    }
                                    }
                                    col += slot.width
                                } else {
                                    Box(modifier = Modifier.width(cellSize).height(cellSize).padding(1.dp))
                                    col += 1
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (selectedSlotIndex in gridSlots.indices) {
                val selectedSlot = gridSlots[selectedSlotIndex]
                val selectedType = PlayerComponentType.entries.find { it.id == selectedSlot.typeId }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedType != null) {
                                Icon(
                                    painter = painterResource(selectedType.previewIconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(selectedType?.displayNameRes ?: R.string.empty_space),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "Position: row ${selectedSlot.row}, col ${selectedSlot.col}  |  Size: ${selectedSlot.width}x${selectedSlot.height}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val updated = gridSlots.toMutableList()
                                if (selectedSlotIndex in updated.indices) {
                                    updated.removeAt(selectedSlotIndex)
                                    gridSlots = updated
                                    selectedSlotIndex = -1
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.modular_remove_component))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (showPalette) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.modular_add_component),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PlayerComponentType.entries) { type ->
                                ComponentPaletteChip(
                                    type = type,
                                    onClick = {
                                        val pos = findEmptyCell(gridSlots, GRID_ROWS, GRID_COLUMNS)
                                        if (pos != null) {
                                            val newSlot = PlayerGridSlot(
                                                typeId = type.id,
                                                row = pos.first,
                                                col = pos.second,
                                                width = type.defaultWidth,
                                                height = type.defaultHeight,
                                            )
                                            val updated = gridSlots.toMutableList()
                                            updated.add(newSlot)
                                            gridSlots = updated
                                            selectedSlotIndex = gridSlots.size - 1
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { showPalette = !showPalette },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(if (showPalette) stringResource(R.string.modular_done_adding) else stringResource(R.string.modular_add_component))
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val finalLayout = editingLayout.copy(slots = gridSlots)
                    editingLayout = finalLayout
                    scope.launch {
                        PlayerLayoutRepository.setLayoutPreset(context, PlayerLayoutPreset.CUSTOM)
                        PlayerLayoutRepository.saveCustomLayout(context, finalLayout)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text(stringResource(R.string.modular_save_layout))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val classic = DefaultPlayerLayouts.classic
                    editingLayout = classic
                    gridSlots = classic.slots.toMutableList()
                    selectedSlotIndex = -1
                    scope.launch {
                        PlayerLayoutRepository.setLayoutPreset(context, PlayerLayoutPreset.CLASSIC)
                        PlayerLayoutRepository.saveCustomLayout(context, classic)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.modular_reset_layout))
            }
        }
    }
}

@Composable
private fun ComponentPaletteChip(
    type: PlayerComponentType,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(type.previewIconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(type.displayNameRes),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 9.sp,
            )
        }
    }
}
