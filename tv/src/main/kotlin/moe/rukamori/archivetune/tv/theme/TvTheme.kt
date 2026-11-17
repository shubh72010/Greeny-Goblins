package moe.rukamori.archivetune.tv.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// JusDots Signature Palette: Ink, Coral, Sand
val TvInk = Color(0xFF141414)
val TvSurface = Color(0xFF1F1F1F)
val TvSurfaceVariant = Color(0xFF2A2A2A)
val TvAccentCoral = Color(0xFFF96749)
val TvAccentInk = Color(0xFFD9432C)
val TvPaper = Color(0xFFF5F4F2)
val TvTextSecondary = Color(0xFFAAAAAA)
val TvFocusedBorder = Color(0xFFF96749)

private val TvDarkColorScheme = darkColorScheme(
    primary = TvAccentCoral,
    onPrimary = Color.White,
    primaryContainer = TvAccentInk,
    onPrimaryContainer = Color.White,
    background = TvInk,
    onBackground = TvPaper,
    surface = TvSurface,
    onSurface = TvPaper,
    surfaceVariant = TvSurfaceVariant,
    onSurfaceVariant = TvTextSecondary,
)

@Composable
fun TvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content
    )
}
