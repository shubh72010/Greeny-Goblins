/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    LaunchedEffect(this) {
        var prevIndex = firstVisibleItemIndex
        var prevOffset = firstVisibleItemScrollOffset
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex != prevIndex) {
                    isScrollingUp = prevIndex > currentIndex
                } else if (currentOffset != prevOffset) {
                    isScrollingUp = prevOffset > currentOffset
                }
                prevIndex = currentIndex
                prevOffset = currentOffset
            }
    }
    return isScrollingUp
}

@Composable
fun LazyGridState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    LaunchedEffect(this) {
        var prevIndex = firstVisibleItemIndex
        var prevOffset = firstVisibleItemScrollOffset
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex != prevIndex) {
                    isScrollingUp = prevIndex > currentIndex
                } else if (currentOffset != prevOffset) {
                    isScrollingUp = prevOffset > currentOffset
                }
                prevIndex = currentIndex
                prevOffset = currentOffset
            }
    }
    return isScrollingUp
}

@Composable
fun ScrollState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    LaunchedEffect(this) {
        var prevValue = value
        snapshotFlow { value }
            .collect { currentValue ->
                if (currentValue != prevValue) {
                    isScrollingUp = prevValue > currentValue
                    prevValue = currentValue
                }
            }
    }
    return isScrollingUp
}
