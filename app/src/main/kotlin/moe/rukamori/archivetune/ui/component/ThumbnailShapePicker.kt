/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AodThumbnailShape
import moe.rukamori.archivetune.ui.utils.toComposeShape

@Composable
fun AodThumbnailShape.label(): String =
    when (this) {
        AodThumbnailShape.ROUNDED -> stringResource(R.string.aod_shape_rounded)
        AodThumbnailShape.SQUARE -> stringResource(R.string.aod_shape_square)
        AodThumbnailShape.CIRCLE -> stringResource(R.string.aod_shape_circle)
        AodThumbnailShape.PILL -> stringResource(R.string.aod_shape_pill)
        AodThumbnailShape.ARCH -> stringResource(R.string.aod_shape_arch)
        AodThumbnailShape.SLANTED -> stringResource(R.string.aod_shape_slanted)
        AodThumbnailShape.DIAMOND -> stringResource(R.string.aod_shape_diamond)
        AodThumbnailShape.PENTAGON -> stringResource(R.string.aod_shape_pentagon)
        AodThumbnailShape.TRIANGLE -> stringResource(R.string.aod_shape_triangle)
        AodThumbnailShape.HEART -> stringResource(R.string.aod_shape_heart)
        AodThumbnailShape.FLOWER -> stringResource(R.string.aod_shape_flower)
        AodThumbnailShape.CLOVER_4 -> stringResource(R.string.aod_shape_clover_4)
        AodThumbnailShape.COOKIE_6 -> stringResource(R.string.aod_shape_cookie_6)
        AodThumbnailShape.COOKIE_9 -> stringResource(R.string.aod_shape_cookie_9)
        AodThumbnailShape.SUNNY -> stringResource(R.string.aod_shape_sunny)
        AodThumbnailShape.SOFT_BURST -> stringResource(R.string.aod_shape_soft_burst)
        AodThumbnailShape.GHOSTISH -> stringResource(R.string.aod_shape_ghostish)
        AodThumbnailShape.PIXEL_CIRCLE -> stringResource(R.string.aod_shape_pixel_circle)
    }

@Composable
fun ThumbnailShapePicker(
    title: String,
    description: String,
    selectedShape: AodThumbnailShape,
    cornerRadius: Float,
    onShapeSelected: (AodThumbnailShape) -> Unit,
    shapeRotation: Int = 0,
    modifier: Modifier = Modifier,
) {
    val shapes = remember { AodThumbnailShape.entries.toList() }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3,
                modifier = Modifier.fillMaxWidth(),
            ) {
                shapes.forEach { shape ->
                    ThumbnailShapeOption(
                        shape = shape,
                        modifier = Modifier.weight(1f),
                        selected = shape == selectedShape,
                        cornerRadius = cornerRadius,
                        shapeRotation = shapeRotation,
                        onClick = { onShapeSelected(shape) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailShapeOption(
    shape: AodThumbnailShape,
    modifier: Modifier = Modifier,
    selected: Boolean,
    cornerRadius: Float,
    shapeRotation: Int,
    onClick: () -> Unit,
) {
    val composeShape = shape.toComposeShape(cornerRadius = cornerRadius, startAngle = shapeRotation)
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier =
            modifier
                .heightIn(min = 112.dp)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                ),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = composeShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Text(
                text = shape.label(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
