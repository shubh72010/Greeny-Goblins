package moe.rukamori.archivetune.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.tv.theme.TvAccentCoral
import moe.rukamori.archivetune.tv.theme.TvFocusedBorder
import moe.rukamori.archivetune.tv.theme.TvPaper
import moe.rukamori.archivetune.tv.theme.TvSurfaceVariant
import moe.rukamori.archivetune.tv.theme.TvTextSecondary

@Composable
fun TvSettingsScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            color = TvPaper,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(8.dp))

        TvSettingTile(
            title = "Audio Quality",
            description = "High (Opus 256kbps / AAC High)",
            onClick = { /* Toggle quality */ }
        )

        TvSettingTile(
            title = "Display Mode",
            description = "Leanback 16:9 Optimized TV Interface",
            onClick = { /* Display options */ }
        )

        TvSettingTile(
            title = "Engine Architecture",
            description = "Ported from BitChord TV for JusPlayer (ArchiveTune core)",
            onClick = { /* Engine info */ }
        )

        TvSettingTile(
            title = "About JusPlayer TV",
            description = "Version 15.0.0-tv · Ad-free, private, offline-first YouTube Music for Android TV",
            onClick = { /* About dialog */ }
        )
    }
}

@Composable
private fun TvSettingTile(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurfaceVariant)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, TvFocusedBorder, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (isFocused) TvAccentCoral else TvPaper,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = TvTextSecondary,
                fontSize = 14.sp
            )
        }
    }
}
