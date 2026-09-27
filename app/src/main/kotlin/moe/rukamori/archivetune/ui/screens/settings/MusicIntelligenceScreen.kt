/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.AnalysisWithSong
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import java.util.Date

private val KEY_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicIntelligenceScreen(navController: NavController) {
    val database = LocalDatabase.current
    val analyses by database.allMusicAnalyses().collectAsState(initial = emptyList())

    Scaffold { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsEditorialHeader(
                title = stringResource(R.string.music_intelligence),
                onBack = { navController.navigateUp() },
            )
        if (analyses.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.waves),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_analyses_yet),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.no_analyses_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                            ),
                        ),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        end = 16.dp,
                        bottom = SettingsDimensions.ScreenBottomPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(analyses, key = { it.analysis.videoId }) { item ->
                    AnalysisCard(item = item)
                }
            }
        }
        }
    }
}

@Composable
private fun AnalysisCard(item: AnalysisWithSong) {
    val cardShape = RoundedCornerShape(20.dp)
    val analysis = item.analysis
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val database = LocalDatabase.current
    var windowsLabel by remember { mutableStateOf<String?>(null) }
    val analyzedLabel =
        remember(analysis.analyzedAt) {
            android.text.format.DateFormat.getDateFormat(context).format(Date(analysis.analyzedAt))
        }
    // Mode matters as much as the root: a bare letter can't distinguish
    // A major from A minor, which is exactly what made key output look wrong.
    val keyLabel =
        analysis.chromaKey?.let { pc ->
            val root = KEY_NAMES[pc.coerceIn(0, 11)]
            val mode =
                when (analysis.chromaScale?.lowercase()) {
                    "minor" -> "m"
                    "major" -> ""
                    else -> null
                }
            if (mode == null) root else root + mode
        } ?: "–"
    val bpmLabel = analysis.bpm?.let { "${it.toInt()}" } ?: "–"
    val analysisLabel =
        if (analysis.analysisSource == "essentia" && analysis.analysisError != null) {
            "Essentia error"
        } else {
            null
        }
    LaunchedEffect(expanded, analysis.videoId) {
        if (expanded && windowsLabel == null) {
            val rows = runCatching { database.getMusicAnalysisSamples(analysis.videoId) }.getOrNull()
            windowsLabel = rows
                ?.takeIf { it.isNotEmpty() }
                // Windows are spectral-only by design (no per-window tempo), so a 0
                // BPM is "not measured here", not a failed reading — render as such.
                ?.joinToString(" · ") { "${(it.fraction * 100).toInt()}%→${it.bpm.takeIf { b -> b > 0 }?.toInt() ?: "–"}" }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable { expanded = !expanded }
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.songTitle ?: analysis.videoId,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("BPM $bpmLabel · Key $keyLabel · ${analysis.sampleCount} samples · ${(analysis.confidence * 100).toInt()}%")
                        analysisLabel?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                DspRow("RMS", analysis.rms)
                DspRow("Bass", analysis.bass)
                DspRow("Mids", analysis.mids)
                DspRow("Treble", analysis.treble)
                DspRow("Brightness", analysis.brightness)
                DspRow("Flux", analysis.spectralFlux)
                DspRow("Onsets", analysis.onsetDensity)
                DspRow("Rhythm", analysis.rhythmicity)
                Spacer(modifier = Modifier.height(6.dp))
                windowsLabel?.let {
                    Text(
                        text = "windows $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "$analyzedLabel · v${analysis.analysisVersion} · ${analysis.analysisSource}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                )
                analysis.analysisError?.let { err ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DspRow(label: String, value: Float) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.width(72.dp),
        )
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(value.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(value * 100).toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.width(28.dp),
        )
    }
}
