package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import kotlin.math.PI
import kotlin.math.sin

private const val INTRO_MS = 1500

/**
 * Full-screen startup veil: brand icon over a 3-tone blurred gradient with a
 * wave, shown until the service binds, then exits upward.
 *
 * Choreography (single 1500ms clock): 0-250ms icon fades in; 250-700ms it
 * breathes apart (ghost layer); 700-1100ms a light band sweeps through;
 * 1100-1500ms the wave rises. The exit slide then continues the wave's
 * upward motion into the app reveal.
 */
@Composable
fun StartupCover(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    // Master clock 0..1 over INTRO_MS; phases derived below, no extra state holders.
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(1f, tween(INTRO_MS, easing = LinearEasing)) }
    val t = clock.value

    // Phase windows as 0..1 progress values (clamped).
    val appear = (t / 0.17f).coerceIn(0f, 1f)
    val morph = ((t - 0.17f) / 0.3f).coerceIn(0f, 1f)
    val sweep = ((t - 0.47f) / 0.26f).coerceIn(0f, 1f)
    val rise = ((t - 0.73f) / 0.27f).coerceIn(0f, 1f)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(600)) + fadeOut(tween(400)),
    ) {
        Box(
            modifier = modifier.fillMaxSize().background(Color(0xFF0B0B10)),
            contentAlignment = Alignment.Center,
        ) {
            // Slow drift so the veil stays alive if loading outlasts the intro.
            val drift by rememberInfiniteTransition(label = "startupDrift").animateFloat(
                initialValue = 0f,
                targetValue = (2 * PI).toFloat(),
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(6000, easing = LinearEasing)),
                label = "drift",
            )
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // 3-tone "blur": overlapping radial blobs (no RenderEffect, works back to API 26).
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF7C4DFF).copy(alpha = 0.55f), Color.Transparent), radius = w * 0.7f),
                    radius = w * 0.7f,
                    center = Offset(w * 0.5f + sin(drift) * 40f, h * 0.28f),
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF00E5C0).copy(alpha = 0.4f), Color.Transparent), radius = w * 0.6f),
                    radius = w * 0.6f,
                    center = Offset(w * 0.15f, h * 0.62f + sin(drift + 2f) * 40f),
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFFF9E4D).copy(alpha = 0.38f), Color.Transparent), radius = w * 0.6f),
                    radius = w * 0.6f,
                    center = Offset(w * 0.88f, h * 0.66f - sin(drift + 4f) * 40f),
                )
                // Wave rising from the bottom; crest climbs with the `rise` phase.
                val waveY = h * (0.74f - 0.19f * rise)
                val wave = Path().apply {
                    moveTo(0f, waveY)
                    cubicTo(w * 0.25f, waveY - h * 0.06f, w * 0.35f, waveY + h * 0.06f, w * 0.55f, waveY)
                    cubicTo(w * 0.75f, waveY - h * 0.06f, w * 0.85f, waveY + h * 0.05f, w, waveY - h * 0.02f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(wave, Color.White.copy(alpha = 0.07f))
                drawPath(wave, Color.White.copy(alpha = 0.16f), style = Stroke(width = 3.dp.toPx()))
            }
            // Light band sweeping through during the `sweep` phase.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().alpha(if (sweep in 0.01f..0.99f) 1f else 0f)) {
                val travel = maxWidth * 1.6f
                Box(
                    Modifier
                        .offset(x = -travel * 0.4f + travel * sweep)
                        .width(220.dp)
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent))),
                )
            }
            // Center brand mark: fades in, breathes apart via a ghost layer, settles with a spring.
            var settled by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { settled = true }
            val iconScale by animateFloatAsState(
                targetValue = (if (settled) 1f else 0.7f) * (1f + 0.04f * sin(morph * PI.toFloat())),
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 220f),
                label = "startupIcon",
            )
            val painter = painterResource(R.drawable.app_icon_small)
            Image(
                painter = painter,
                contentDescription = null,
                alpha = 0.35f * sin(morph * PI.toFloat()),
                modifier = Modifier.size(112.dp).offset(x = (14 * sin(morph * PI.toFloat())).dp).scale(iconScale),
            )
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(112.dp).scale(iconScale).alpha(appear),
            )
        }
    }
}
