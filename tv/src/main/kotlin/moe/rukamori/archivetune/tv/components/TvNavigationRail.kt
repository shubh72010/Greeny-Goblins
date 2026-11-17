package moe.rukamori.archivetune.tv.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.tv.theme.TvAccentCoral
import moe.rukamori.archivetune.tv.theme.TvFocusedBorder
import moe.rukamori.archivetune.tv.theme.TvInk
import moe.rukamori.archivetune.tv.theme.TvPaper
import moe.rukamori.archivetune.tv.theme.TvSurfaceVariant
import moe.rukamori.archivetune.tv.theme.TvTextSecondary

enum class TvNavTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun TvNavigationRail(
    selectedTab: TvNavTab,
    onTabSelected: (TvNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    var hasFocusInRail by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (hasFocusInRail) 200.dp else 72.dp,
        label = "tvRailWidth"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(TvInk)
            .padding(vertical = 24.dp, horizontal = 12.dp)
            .onFocusChanged { hasFocusInRail = it.hasFocus },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Logo / Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(TvAccentCoral)
            )
            if (hasFocusInRail) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "JusPlayer TV",
                    color = TvPaper,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        TvNavTab.values().forEach { tab ->
            TvNavRailItem(
                tab = tab,
                isSelected = selectedTab == tab,
                isRailExpanded = hasFocusInRail,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun TvNavRailItem(
    tab: TvNavTab,
    isSelected: Boolean,
    isRailExpanded: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isFocused -> TvSurfaceVariant
                    isSelected -> TvAccentCoral.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
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
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = when {
                isFocused -> TvAccentCoral
                isSelected -> TvAccentCoral
                else -> TvTextSecondary
            },
            modifier = Modifier.size(24.dp)
        )

        AnimatedVisibility(visible = isRailExpanded) {
            Row {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = tab.label,
                    color = if (isFocused || isSelected) Color.White else TvTextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}
