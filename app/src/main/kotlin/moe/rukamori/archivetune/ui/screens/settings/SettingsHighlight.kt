package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay

const val SETTINGS_HIGHLIGHT_ARG = "highlight"

@Composable
fun HighlightablePreference(
    modifier: Modifier = Modifier,
    highlightKey: String,
    highlight: String?,
    content: @Composable () -> Unit
) {
    val isHighlighted = highlight?.equals(highlightKey, ignoreCase = true) == true
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val pulse by animateFloatAsState(
        targetValue = if (isHighlighted) 1f else 0f,
        label = "highlightPulse"
    )
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            // small delay to wait for layout
            delay(180)
            try { bringIntoViewRequester.bringIntoView() } catch (_: Exception) {}
        }
    }
    val highlightModifier = if (isHighlighted) {
        Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .border(
                BorderStroke((2.dp * pulse), MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(28.dp)
            )
            .graphicsLayer { scaleX = 1f + 0.015f * pulse; scaleY = 1f + 0.015f * pulse }
            .clip(RoundedCornerShape(28.dp))
    } else {
        Modifier
    }
    Box(modifier = modifier.then(highlightModifier)) {
        content()
    }
}
