/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ShowStreamSourceBadgeKey
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * Small pill badge that shows the **actual** stream source used for the current playback.
 *
 * Sources come from `MusicService.activeStreamSource`:
 * - `JUSPLAYER_ENGINE • NewPipe` / `JUSPLAYER_ENGINE (cached)` — direct JPE
 * - `WEB_REMIX (fallback)` / `WEB_REMIX (fallback cached)` — JPE failed, InnerTube fallback
 * - `ARCHIVETUNE_EXTRACTOR` / `ARCHIVETUNE_EXTRACTOR (cached)`
 * - `WEB_REMIX` / `WEB_REMIX (cached)` / other `PlayerStreamClient` names for normal path
 *
 * Use via:
 * ```
 * StreamSourceBadge(modifier = Modifier.padding(...))
 * ```
 */
@Composable
fun StreamSourceBadge(
    modifier: Modifier = Modifier,
) {
    val (showBadge) = rememberPreference(ShowStreamSourceBadgeKey, defaultValue = true)
    if (!showBadge) return
    val playerConnection = LocalPlayerConnection.current ?: return
    val rawSource by playerConnection.activeStreamSource.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    val (label, containerColor, contentColor, iconRes) = when {
        rawSource.contains("JUSPLAYER_ENGINE") && rawSource.contains("NewPipe") ->
            Quadruple(
                "JPE • NewPipe",
                colorScheme.tertiaryContainer,
                colorScheme.onTertiaryContainer,
                R.drawable.integration,
            )

        rawSource.contains("JUSPLAYER_ENGINE") ->
            Quadruple(
                "JPE • NewPipe (cached)",
                colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                colorScheme.onTertiaryContainer,
                R.drawable.integration,
            )

        rawSource.contains("ARCHIVETUNE_EXTRACTOR") ->
            Quadruple(
                "Extractor",
                colorScheme.secondaryContainer,
                colorScheme.onSecondaryContainer,
                R.drawable.integration,
            )

        rawSource.contains("WEB_REMIX") && rawSource.contains("fallback") ->
            Quadruple(
                "Web Remix (fallback)",
                colorScheme.errorContainer.copy(alpha = 0.9f),
                colorScheme.onErrorContainer,
                R.drawable.error,
            )

        rawSource.contains("WEB_REMIX") ->
            Quadruple(
                "Web Remix",
                colorScheme.secondaryContainer,
                colorScheme.onSecondaryContainer,
                R.drawable.graphic_eq,
            )

        rawSource.contains("ANDROID_VR") ->
            Quadruple(
                "Android VR",
                colorScheme.primaryContainer,
                colorScheme.onPrimaryContainer,
                R.drawable.graphic_eq,
            )

        rawSource.contains("IOS") ->
            Quadruple(
                "iOS",
                colorScheme.primaryContainer,
                colorScheme.onPrimaryContainer,
                R.drawable.graphic_eq,
            )

        rawSource.contains("TVHTML5") ->
            Quadruple(
                "TV HTML5",
                colorScheme.primaryContainer,
                colorScheme.onPrimaryContainer,
                R.drawable.graphic_eq,
            )

        else ->
            Quadruple(
                rawSource.ifBlank { "Unknown" },
                colorScheme.surfaceVariant,
                colorScheme.onSurfaceVariant,
                R.drawable.graphic_eq,
            )
    }

    val iconPainter = painterResource(iconRes)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                ),
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 1,
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
