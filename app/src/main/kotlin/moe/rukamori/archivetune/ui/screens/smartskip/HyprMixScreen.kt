/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.smartskip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HyprMixAutoSkipEnabledKey
import moe.rukamori.archivetune.constants.HyprMixLearningEnabledKey
import moe.rukamori.archivetune.constants.HyprMixRecordingEnabledKey
import moe.rukamori.archivetune.playback.queues.hyprMixQueue
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.HyprMixEntry
import moe.rukamori.archivetune.viewmodels.HyprMixViewModel
import java.util.Locale

@Composable
fun HyprMixScreen(
    navController: NavController,
    viewModel: HyprMixViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current

    val recordingPref = rememberPreference(HyprMixRecordingEnabledKey, false)
    val learningPref = rememberPreference(HyprMixLearningEnabledKey, false)
    val autoSkipPref = rememberPreference(HyprMixAutoSkipEnabledKey, false)

    var editTarget by remember { mutableStateOf<HyprMixEntry?>(null) }
    val songs = remember(state.entries) { state.entries.map { it.song } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hyprmix)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item(key = "switches") {
                Column {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.hyprmix_summary, state.entries.size, state.playCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { playerConnection?.playQueue(hyprMixQueue(songs, 0)) },
                            enabled = songs.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.hyprmix_play))
                        }
                    }
                    SwitchRow(
                        title = stringResource(R.string.hyprmix_record_title),
                        subtitle = stringResource(R.string.hyprmix_record_subtitle),
                        checked = recordingPref.value,
                        onCheckedChange = { recordingPref.value = it },
                    )
                    SwitchRow(
                        title = stringResource(R.string.hyprmix_learn_title),
                        subtitle = stringResource(R.string.hyprmix_learn_subtitle),
                        checked = learningPref.value,
                        onCheckedChange = { learningPref.value = it },
                    )
                    SwitchRow(
                        title = stringResource(R.string.hyprmix_autoskip_title),
                        subtitle = stringResource(R.string.hyprmix_autoskip_subtitle),
                        checked = autoSkipPref.value,
                        onCheckedChange = { autoSkipPref.value = it },
                    )
                    TextButton(
                        onClick = viewModel::forgetEverything,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.hyprmix_delete_all))
                    }
                }
            }

            if (state.entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.hyprmix_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            itemsIndexed(
                items = state.entries,
                key = { _, entry -> entry.song.id },
                contentType = { _, _ -> "hyprmix_song" },
            ) { index, entry ->
                HyprMixSongRow(
                    rank = index + 1,
                    entry = entry,
                    onClick = {
                        playerConnection?.playQueue(hyprMixQueue(songs, index))
                    },
                    onEditIntro = { editTarget = entry },
                )
            }
        }
    }

    editTarget?.let { entry ->
        IntroEditDialog(
            entry = entry,
            onDismiss = { editTarget = null },
            onSave = { introUntilMs ->
                viewModel.setIntro(entry.song.id, introUntilMs)
                editTarget = null
            },
            onReset = {
                viewModel.resetIntro(entry.song.id)
                editTarget = null
            },
        )
    }
}

@Composable
private fun HyprMixSongRow(
    rank: Int,
    entry: HyprMixEntry,
    onClick: () -> Unit,
    onEditIntro: () -> Unit,
) {
    val candidate = entry.candidate
    Column {
        SongListItem(
            song = entry.song,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
            showInLibraryIcon = true,
            trailingContent = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color =
                        when {
                            entry.manual -> MaterialTheme.colorScheme.tertiaryContainer
                            entry.introUntilMs > 0L -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    modifier =
                        Modifier
                            .clickable(onClick = onEditIntro)
                            .padding(start = 4.dp),
                ) {
                    Text(
                        text =
                            if (entry.introUntilMs > 0L) {
                                stringResource(R.string.hyprmix_intro_chip, makeTimeString(entry.introUntilMs))
                            } else {
                                stringResource(R.string.hyprmix_no_intro)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            },
        )
        Text(
            text =
                joinByBullet(
                    stringResource(R.string.hyprmix_rank, rank),
                    stringResource(R.string.hyprmix_plays, candidate.plays),
                    stringResource(R.string.hyprmix_finished, candidate.completed),
                    stringResource(R.string.hyprmix_skipped, candidate.quickSkips),
                    stringResource(R.string.hyprmix_after_skip, candidate.arrivals),
                    stringResource(R.string.hyprmix_score, String.format(Locale.US, "%.2f", candidate.score)),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
        )
        Text(
            text =
                joinByBullet(
                    if (entry.introUntilMs > 0L) {
                        stringResource(R.string.hyprmix_learned_from, entry.samples)
                    } else {
                        null
                    },
                    stringResource(
                        R.string.hyprmix_last_played,
                        (System.currentTimeMillis() - candidate.lastPlayedAt).coerceAtLeast(0L) / 86_400_000L,
                    ),
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IntroEditDialog(
    entry: HyprMixEntry,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onReset: () -> Unit,
) {
    var seconds by remember(entry.song.id) { mutableFloatStateOf(entry.introUntilMs / 1000f) }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.hyprmix_edit_intro_title)) },
        buttons = {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.hyprmix_reset))
            }
            TextButton(onClick = { onSave((seconds * 1000f).toLong()) }) {
                Text(stringResource(R.string.hyprmix_save))
            }
        },
    ) {
        Text(text = entry.song.song.title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text =
                if (entry.manual) {
                    stringResource(R.string.hyprmix_edit_intro_manual)
                } else {
                    stringResource(R.string.hyprmix_edit_intro_hint, entry.samples)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = seconds,
            onValueChange = { seconds = it },
            valueRange = 0f..60f,
            steps = 59,
        )
        Text(text = makeTimeString((seconds * 1000f).toLong()))
    }
}
