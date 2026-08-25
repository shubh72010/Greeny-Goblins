/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.SearchManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import me.bush.translator.Translator
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.TranslatorLang
import moe.rukamori.archivetune.utils.TranslatorLanguages
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn as SearchLazyColumn

private data class EditableLine(
    val timeMs: Long?, // null = plain line, >=0 = synced
    var text: String,
)

private fun formatLrcTimestamp(timeMs: Long): String {
    val minutes = timeMs / 60_000L
    val seconds = (timeMs % 60_000L) / 1_000L
    val cs = (timeMs % 1_000L) / 10L
    return "[%02d:%02d.%02d]".format(Locale.US, minutes, seconds, cs)
}

private fun formatTimestampForEdit(timeMs: Long): String {
    val minutes = timeMs / 60_000L
    val seconds = (timeMs % 60_000L) / 1_000L
    val cs = (timeMs % 1_000L) / 10L
    return "%02d:%02d.%02d".format(Locale.US, minutes, seconds, cs)
}

private fun parseTimestampInput(input: String): Long? {
    val t = input.trim().removePrefix("[").removeSuffix("]").trim()
    if (t.isEmpty()) return null
    // mm:ss.cc or mm:ss.mmm or mm:ss
    val regex = Regex("""(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?""")
    val m = regex.matchEntire(t) ?: return null
    val min = m.groupValues[1].toLongOrNull() ?: return null
    val sec = m.groupValues[2].toLongOrNull() ?: return null
    if (sec >= 60) return null
    val fracStr = m.groupValues[3]
    var ms = 0L
    if (fracStr.isNotEmpty()) {
        val frac = fracStr.toLongOrNull() ?: return null
        ms = when (fracStr.length) {
            1 -> frac * 100
            2 -> frac * 10
            3 -> frac
            else -> return null
        }
        if (ms >= 1000) return null
    }
    return min * 60_000L + sec * 1_000L + ms
}

private fun buildInitialLines(raw: String?): MutableList<EditableLine> {
    if (raw.isNullOrBlank() || raw == "LYRICS_NOT_FOUND") {
        return mutableListOf(EditableLine(null, ""))
    }
    if (isTtml(raw)) {
        // Convert TTML -> LRC-ish editable lines (time preserved, word sync lost)
        val entries = runCatching { LyricsUtils.parseTtml(raw) }.getOrDefault(emptyList())
        if (entries.isNotEmpty()) {
            return entries.map { EditableLine(it.time, it.text) }.toMutableList()
        }
        // fallback to plain
        return raw.lines().map { EditableLine(null, it) }.toMutableList()
    }
    if (LyricsUtils.isLineSyncedLrc(raw)) {
        val entries = runCatching { LyricsUtils.parseLyrics(raw) }.getOrDefault(emptyList())
        if (entries.isNotEmpty()) {
            return entries.map { EditableLine(it.time, it.text) }.toMutableList()
        }
    }
    // plain
    val lines = raw.lines()
    // keep blank separation? filter trailing blank but keep interior
    return lines.map { EditableLine(null, it) }.toMutableList().ifEmpty { mutableListOf(EditableLine(null, "")) }
}

private fun buildLyricsString(lines: List<EditableLine>): String {
    val hasSynced = lines.any { it.timeMs != null && it.timeMs >= 0 }
    return if (hasSynced) {
        lines.joinToString("\n") { line ->
            val t = line.timeMs
            if (t != null && t >= 0) "${formatLrcTimestamp(t)}${line.text}" else line.text
        }
    } else {
        lines.joinToString("\n") { it.text }
    }
}

private enum class EditorTranslationSource { AI_TRANSLATION, TRANSLATION }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLyricsEditor(
    initialLyrics: String?,
    mediaMetadata: MediaMetadata,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    var lines by remember(initialLyrics) { mutableStateOf(buildInitialLines(initialLyrics)) }
    // track which line was just added to focus it
    var focusIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTtmlSource = remember(initialLyrics) { initialLyrics != null && isTtml(initialLyrics) }
    val hasSynced = remember(lines) { lines.any { it.timeMs != null } }

    // Search / Translate / Import state for integrated actions
    var showSearchInput by rememberSaveable { mutableStateOf(false) }
    var showSearchResult by rememberSaveable { mutableStateOf(false) }
    var showTranslate by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var expandedSearchResultId by rememberSaveable { mutableStateOf<String?>(null) }
    val lyricsSearchState by viewModel.lyricsSearchState.collectAsStateWithLifecycle()
    val isAiTranslating by viewModel.isAiTranslating.collectAsStateWithLifecycle()
    val (aiProvider) = rememberEnumPreference(AiProviderKey, AiProvider.NONE)
    val (aiApiKey) = rememberPreference(AiApiKeyKey, "")
    val (aiCustomEndpoint) = rememberPreference(AiCustomEndpointKey, "")
    val (aiValidationStatus) = rememberEnumPreference(AiApiValidationStatusKey, AiApiValidationStatus.UNKNOWN)
    val currentDraft = remember(lines) { buildLyricsString(lines) }
    val isTranslateEnabled = currentDraft.isNotBlank() && currentDraft != LyricsEntity.LYRICS_NOT_FOUND
    val isAiProviderConfigured = aiProvider != AiProvider.NONE
    val isAiTranslationEnabled = isTranslateEnabled && isAiProviderConfigured && aiApiKey.isNotBlank() && (aiProvider != AiProvider.CUSTOM || aiCustomEndpoint.isNotBlank()) && aiValidationStatus != AiApiValidationStatus.FAILED
    var translationJob by remember { mutableStateOf<Job?>(null) }
    var isStandardTranslating by remember { mutableStateOf(false) }
    var isDialogAiTranslationRunning by rememberSaveable { mutableStateOf(false) }
    val searchMediaMetadata = mediaMetadata
    var titleField by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(mediaMetadata.title)) }
    var artistField by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(mediaMetadata.artists.joinToString { it.name })) }
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.aiTranslationEvents.collect { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(isAiTranslating, isDialogAiTranslationRunning) {
        if (isDialogAiTranslationRunning && !isAiTranslating) {
            isDialogAiTranslationRunning = false
            showTranslate = false
            // on AI done, viewModel saved to DB — also update local draft
            // poll currentLyrics via viewModel? For now, fetch from viewModel's last translation is saved; we just keep editing
        }
    }

    var importDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(buildLyricsString(lines))) }
    LaunchedEffect(showImport, lines) { if (showImport) importDraft = TextFieldValue(buildLyricsString(lines)) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                if (content.isNotBlank()) importDraft = TextFieldValue(content)
                else Toast.makeText(context, "Empty file", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── In-memory drafts (last 5) + swipe undo/redo ──
    var drafts by remember(initialLyrics) { mutableStateOf(listOf(buildInitialLines(initialLyrics).map { it.copy() })) }
    var draftPos by remember(initialLyrics) { mutableStateOf(0) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val database = moe.rukamori.archivetune.LocalDatabase.current
    val historyFlow = remember(mediaMetadata.id) { database.historyForSong(mediaMetadata.id, 3) }
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var textDebounceJob by remember { mutableStateOf<Job?>(null) }
    // keep drafts in sync when initialLyrics changes (e.g., after import/search)
    LaunchedEffect(initialLyrics) {
        val fresh = buildInitialLines(initialLyrics).map { it.copy() }
        drafts = listOf(fresh)
        draftPos = 0
        // also reset lines if it was stale
        if (lines != fresh) lines = fresh.toMutableList()
    }

    fun pushDraft(next: List<EditableLine>) {
        val copy = next.map { it.copy() }
        if (copy == drafts.getOrNull(draftPos)) return
        // cull: keep last 4 before pos + new = 5 max, drop redo branch
        val base = drafts.take(draftPos + 1)
        val trimmed = if (base.size >= 5) base.takeLast(4) else base
        drafts = trimmed + listOf(copy)
        draftPos = drafts.lastIndex
        if (drafts.size > 5) {
            drafts = drafts.takeLast(5)
            draftPos = 4
        }
    }
    fun undo() {
        textDebounceJob?.cancel()
        if (draftPos > 0) {
            draftPos--
            lines = drafts[draftPos].map { it.copy() }.toMutableList()
        } else Toast.makeText(context, "No earlier draft", Toast.LENGTH_SHORT).show()
    }
    fun redo() {
        textDebounceJob?.cancel()
        if (draftPos < drafts.lastIndex) {
            draftPos++
            lines = drafts[draftPos].map { it.copy() }.toMutableList()
        } else Toast.makeText(context, "No later draft", Toast.LENGTH_SHORT).show()
    }

    fun copyToClipboard() {
        val text = buildLyricsString(lines)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("lyrics", text))
        Toast.makeText(context, "Copied ${text.lines().size} lines", Toast.LENGTH_SHORT).show()
    }
    fun shareAsFile() {
        try {
            val text = buildLyricsString(lines)
            val dir = java.io.File(context.cacheDir, "shared_lyrics").apply { mkdirs() }
            val safeName = mediaMetadata.title.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(48).ifBlank { "lyrics" }
            val file = java.io.File(dir, "$safeName.lrc")
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, text); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.startActivity(Intent.createChooser(intent, "Share lyrics"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(focusIndex) {
        focusIndex?.let { idx ->
            // scroll to make the new line visible
            if (idx in lines.indices) listState.animateScrollToItem(idx)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onHorizontalDrag = { _, d -> acc += d },
                    onDragEnd = {
                        when {
                            acc > 120 -> undo()
                            acc < -120 -> redo()
                        }
                        acc = 0f
                    },
                )
            },
    ) {
        // Header bar inside editor
        Surface(
            color = Color.Black.copy(alpha = 0.22f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (isTtmlSource) {
                    Text(
                        text = "TTML → LRC",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                IconButton(onClick = { showImport = true }, modifier = Modifier.size(36.dp)) {
                    Icon(painter = painterResource(R.drawable.input), contentDescription = "Import", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showTranslate = true }, modifier = Modifier.size(36.dp)) {
                    Icon(painter = painterResource(R.drawable.translate), contentDescription = stringResource(R.string.translate), tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showSearchInput = true }, modifier = Modifier.size(36.dp)) {
                    Icon(painter = painterResource(R.drawable.search), contentDescription = stringResource(R.string.search), tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(2.dp))
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(android.R.string.cancel), color = Color.White) }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = {
                        val out = buildLyricsString(lines)
                        onSave(out)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.save)) }
            }
        }

        // Drafts / Export / History toolbar
        Surface(
            color = Color.Black.copy(alpha = 0.14f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::undo, modifier = Modifier.size(36.dp), enabled = draftPos > 0) { Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = "Undo", tint = if (draftPos > 0) Color.White else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = ::redo, modifier = Modifier.size(36.dp), enabled = draftPos < drafts.lastIndex) { Icon(painter = painterResource(R.drawable.arrow_forward), contentDescription = "Redo", tint = if (draftPos < drafts.lastIndex) Color.White else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(18.dp)) }
                    Text(text = "${draftPos + 1}/${drafts.size}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showHistory = true }, modifier = Modifier.size(36.dp)) { Icon(painter = painterResource(R.drawable.history), contentDescription = "History", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = ::copyToClipboard, modifier = Modifier.size(36.dp)) { Icon(painter = painterResource(R.drawable.copy), contentDescription = "Copy", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = ::shareAsFile, modifier = Modifier.size(36.dp)) { Icon(painter = painterResource(R.drawable.share), contentDescription = "Share", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp)) }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = lines,
                key = { index, item -> "${index}_${item.timeMs}_${item.text.hashCode()}" },
            ) { index, line ->
                val fr = remember { FocusRequester() }
                LaunchedEffect(focusIndex) {
                    if (focusIndex == index) fr.requestFocus()
                }
                var timeText by remember(line.timeMs) {
                    mutableStateOf(line.timeMs?.let { formatTimestampForEdit(it) } ?: "")
                }
                var timeError by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { new ->
                            timeText = new
                            val parsed = parseTimestampInput(new)
                            timeError = new.isNotBlank() && parsed == null
                            if (!timeError) {
                                val newMs = if (new.isBlank()) null else parsed
                                if (newMs != line.timeMs) {
                                    val newLines = lines.toMutableList().also { it[index] = it[index].copy(timeMs = newMs) }
                                    pushDraft(newLines)
                                    lines = newLines
                                }
                            }
                        },
                        modifier = Modifier.width(108.dp),
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                        placeholder = { Text("--:--.--", color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        isError = timeError,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (timeError) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.6f),
                            unfocusedBorderColor = if (timeError) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.18f),
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = line.text,
                        onValueChange = { new ->
                            val newLines = lines.toMutableList().also { it[index] = it[index].copy(text = new) }
                            lines = newLines
                            textDebounceJob?.cancel()
                            textDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(700)
                                pushDraft(newLines)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(fr),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        placeholder = { Text("Empty line", color = Color.White.copy(alpha = 0.4f)) },
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                    IconButton(
                        onClick = {
                            val newLines = lines.toMutableList().also { it.removeAt(index) }.let { if (it.isEmpty()) mutableListOf(EditableLine(null, "")) else it }
                            pushDraft(newLines)
                            lines = newLines
                            focusManager.clearFocus()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.delete),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = {
                            val newTime = if (hasSynced) {
                                // append 3s after last timed line
                                val last = lines.lastOrNull { it.timeMs != null }?.timeMs ?: 0L
                                last + 3000L
                            } else null
                            val newLines = lines.toMutableList().also { it.add(EditableLine(newTime, "")) }
                            pushDraft(newLines)
                            lines = newLines
                            focusIndex = lines.lastIndex
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(R.drawable.add), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add line", color = Color.White)
                    }
                    if (hasSynced) {
                        TextButton(
                            onClick = {
                                // add plain line (no timestamp) — will be saved without prefix
                                val newLines = lines.toMutableList().also { it.add(EditableLine(null, "")) }
                                pushDraft(newLines)
                                lines = newLines
                                focusIndex = lines.lastIndex
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("Add plain", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Integrated Search ──
        if (showSearchInput) {
            EditorSearchInputDialog(
                titleField = titleField,
                onTitleFieldChange = { titleField = it },
                artistField = artistField,
                onArtistFieldChange = { artistField = it },
                onDismiss = { showSearchInput = false },
                onSearchOnline = {
                    showSearchInput = false
                    try {
                        context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, "${artistField.text} ${titleField.text} lyrics") })
                    } catch (_: Exception) {}
                },
                onSearch = {
                    viewModel.search(searchMediaMetadata.id, titleField.text, artistField.text, searchMediaMetadata.album?.title, searchMediaMetadata.duration)
                    showSearchResult = true
                    if (!isNetworkAvailable) Toast.makeText(context, context.getString(R.string.error_no_internet), Toast.LENGTH_SHORT).show()
                },
            )
        }
        if (showSearchResult) {
            EditorSearchResultDialog(
                state = lyricsSearchState,
                expandedResultId = expandedSearchResultId,
                onExpandedResultChange = { id -> expandedSearchResultId = if (expandedSearchResultId == id) null else id },
                onRefetch = {
                    expandedSearchResultId = null
                    viewModel.search(searchMediaMetadata.id, titleField.text, artistField.text, searchMediaMetadata.album?.title, searchMediaMetadata.duration)
                },
                onResultSelected = { result ->
                    showSearchResult = false
                    showSearchInput = false
                    viewModel.cancelSearch()
                    viewModel.resetSearchState()
                    // populate editor with selected lyrics instead of saving directly
                    val newLines = buildInitialLines(result.lyrics)
                    pushDraft(newLines)
                    lines = newLines
                    focusIndex = null
                },
                onDismiss = {
                    expandedSearchResultId = null
                    showSearchResult = false
                    viewModel.resetSearchState()
                },
            )
        }
        if (showTranslate) {
            EditorTranslateDialog(
                currentDraft = currentDraft,
                mediaMetadata = mediaMetadata,
                isAiTranslationEnabled = isAiTranslationEnabled,
                isAiTranslating = isAiTranslating,
                isStandardTranslating = isStandardTranslating,
                onStandardTranslatingChange = { isStandardTranslating = it },
                translationJob = translationJob,
                onTranslationJobChange = { translationJob = it },
                isDialogAiTranslationRunning = isDialogAiTranslationRunning,
                onDialogAiRunningChange = { isDialogAiTranslationRunning = it },
                onDismiss = { showTranslate = false },
                onTranslated = { translated ->
                    // translated is full lyrics string — replace editor content
                    val newLines = buildInitialLines(translated)
                    pushDraft(newLines)
                    lines = newLines
                    showTranslate = false
                },
                viewModel = viewModel,
            )
        }
        if (showImport) {
            BasicAlertDialog(
                onDismissRequest = { showImport = false },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false),
                modifier = Modifier.padding(24.dp).navigationBarsPadding().imePadding(),
            ) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E1E1E), tonalElevation = 0.dp, modifier = Modifier.widthIn(max = 560.dp).heightIn(max = 560.dp)) {
                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Icon(painter = painterResource(R.drawable.input), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) } }
                            Text(text = "Import lyrics", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showImport = false }) { Icon(painter = painterResource(R.drawable.close), contentDescription = stringResource(R.string.close), tint = Color.White) }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = importDraft,
                            onValueChange = { importDraft = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                            placeholder = { Text("Paste LRC or plain lyrics here…", color = Color.White.copy(alpha = 0.4f)) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White.copy(alpha = 0.6f), unfocusedBorderColor = Color.White.copy(alpha = 0.18f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            maxLines = 20,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                                    if (clip.isNotBlank()) importDraft = TextFieldValue(clip) else Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) { Icon(painter = painterResource(R.drawable.copy), contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Paste") }
                            FilledTonalButton(
                                onClick = { importLauncher.launch("*/*") },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) { Icon(painter = painterResource(R.drawable.snippet_folder), contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("File") }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showImport = false }, shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Cancel", color = Color.White) }
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    val text = importDraft.text
                                    if (text.isBlank()) { Toast.makeText(context, "Nothing to import", Toast.LENGTH_SHORT).show(); return@Button }
                                    val newLines = buildInitialLines(text)
                                    pushDraft(newLines)
                                    lines = newLines
                                    showImport = false
                                    Toast.makeText(context, "Imported ${text.lines().size} lines", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Replace") }
                        }
                    }
                }
            }
        }
        if (showHistory) {
            BasicAlertDialog(onDismissRequest = { showHistory = false }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false), modifier = Modifier.padding(24.dp).navigationBarsPadding().imePadding()) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E1E1E), tonalElevation = 0.dp, modifier = Modifier.widthIn(max = 520.dp).heightIn(max = 520.dp)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Icon(painter = painterResource(R.drawable.history), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) } }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "History", style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Text(text = "Last 3 saves · swipe left/right to undo/redo", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                            }
                            IconButton(onClick = { showHistory = false }) { Icon(painter = painterResource(R.drawable.close), contentDescription = stringResource(R.string.close), tint = Color.White) }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (history.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("No history yet — Save to create", color = Color.White.copy(alpha = 0.6f)) }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                                history.forEach { h ->
                                    val preview = h.lyrics.lines().take(3).joinToString(" / ").take(80).ifBlank { "(empty)" }
                                    val date = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(h.createdAt))
                                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth(), onClick = {
                                        val newLines = buildInitialLines(h.lyrics)
                                        pushDraft(newLines)
                                        lines = newLines
                                        showHistory = false
                                        Toast.makeText(context, "Restored $date", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = date, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                            Text(text = preview, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(text = "${h.lyrics.lines().size} lines · ${h.lyrics.length} chars", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showHistory = false }, shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Close", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorSearchInputDialog(
    titleField: TextFieldValue,
    onTitleFieldChange: (TextFieldValue) -> Unit,
    artistField: TextFieldValue,
    onArtistFieldChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onSearchOnline: () -> Unit,
    onSearch: () -> Unit,
) {
    val configuration = LocalContext.current.resources.configuration
    val useStackedActions = configuration.screenWidthDp < 600
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding()) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E1E1E), tonalElevation = 0.dp, modifier = Modifier.widthIn(max = 520.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.12f), contentColor = Color.White, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(painter = painterResource(R.drawable.search), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)) } }
                    Text(text = stringResource(R.string.search_lyrics), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(painter = painterResource(R.drawable.close), contentDescription = stringResource(R.string.close), tint = Color.White) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = titleField, onValueChange = onTitleFieldChange, singleLine = true, label = { Text(stringResource(R.string.song_title), color = Color.White.copy(alpha = 0.7f)) }, leadingIcon = { Icon(painter = painterResource(R.drawable.music_note), contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }, trailingIcon = if (titleField.text.isNotEmpty()) {{ IconButton(onClick = { onTitleFieldChange(TextFieldValue()) }) { Icon(painter = painterResource(R.drawable.close), contentDescription = stringResource(R.string.clear), tint = Color.White.copy(alpha = 0.7f)) } }} else null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White.copy(alpha = 0.6f), unfocusedBorderColor = Color.White.copy(alpha = 0.18f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f)), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }))
                    OutlinedTextField(value = artistField, onValueChange = onArtistFieldChange, singleLine = true, label = { Text(stringResource(R.string.song_artists), color = Color.White.copy(alpha = 0.7f)) }, leadingIcon = { Icon(painter = painterResource(R.drawable.artist), contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }, trailingIcon = if (artistField.text.isNotEmpty()) {{ IconButton(onClick = { onArtistFieldChange(TextFieldValue()) }) { Icon(painter = painterResource(R.drawable.close), contentDescription = stringResource(R.string.clear), tint = Color.White.copy(alpha = 0.7f)) } }} else null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White.copy(alpha = 0.6f), unfocusedBorderColor = Color.White.copy(alpha = 0.18f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f)), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }))
                }
                if (useStackedActions) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Button(onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Icon(painter = painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.search)) }
                        FilledTonalButton(onClick = onSearchOnline, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White), shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Icon(painter = painterResource(R.drawable.language), contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.search_online)) }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = onSearchOnline, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White), shapes = ButtonDefaults.shapes(shape = androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShape, pressedShape = androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonPressShape)) { Icon(painter = painterResource(R.drawable.language), contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.search_online)) }
                        androidx.compose.material3.Button(onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shapes = ButtonDefaults.shapes(shape = androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShape, pressedShape = androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonPressShape)) { Icon(painter = painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.search)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorSearchResultDialog(
    state: moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState,
    expandedResultId: String?,
    onExpandedResultChange: (String) -> Unit,
    onRefetch: () -> Unit,
    onResultSelected: (moe.rukamori.archivetune.viewmodels.LyricsSearchResultUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp).imePadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).heightIn(max = LocalContext.current.resources.displayMetrics.heightPixels.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFF1E1E1E), tonalElevation = 0.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // header
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.08f)) {
                        Row(modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.12f)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Icon(painter = painterResource(R.drawable.manage_search), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) } }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.search_lyrics), style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val subtitle = when (state) { is moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Loading -> stringResource(R.string.lyrics_searching_providers); is moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Empty -> stringResource(R.string.lyrics_not_found); is moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Error -> stringResource(state.messageResId); is moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Success -> stringResource(R.string.lyrics_search_results_count, state.results.size) }
                                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            val isSearching = state == moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Loading || state is moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Success && state.isSearching
                            if (isSearching) LoadingIndicator(modifier = Modifier.size(28.dp), color = Color.White)
                            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(painter = painterResource(R.drawable.close), contentDescription = stringResource(R.string.close), tint = Color.White) }
                        }
                    }
                    SearchLazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (state) {
                            moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Loading -> item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { LoadingIndicator(modifier = Modifier.size(40.dp), color = Color.White); Text(stringResource(R.string.lyrics_searching_providers), color = Color.White.copy(alpha = 0.7f)) } } }
                            moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Empty -> item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.lyrics_not_found), color = Color.White) } }
                            is moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Error -> item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { Text(stringResource(state.messageResId), color = Color.White) } }
                            is moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState.Success -> {
                                items(state.results.size) { idx ->
                                    val result = state.results[idx]
                                    val isExpanded = result.id == expandedResultId
                                    val containerColor = if (isExpanded) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f)
                                    val contentColor = Color.White
                                    Surface(onClick = { onResultSelected(result) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = containerColor, contentColor = contentColor, border = androidx.compose.foundation.BorderStroke(1.dp, if (isExpanded) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = result.providerName, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(text = if (result.isWordSynced) stringResource(R.string.lyrics_word_sync) else if (result.isLineSynced) stringResource(R.string.lyrics_synced_badge) else stringResource(R.string.lyrics_search_plain_badge), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                                                }
                                                IconButton(onClick = { onExpandedResultChange(result.id) }, modifier = Modifier.size(36.dp)) { Icon(painter = painterResource(if (isExpanded) R.drawable.expand_less else R.drawable.expand_more), contentDescription = null, tint = Color.White) }
                                            }
                                            Text(text = result.preview, maxLines = if (isExpanded) 8 else 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorTranslateDialog(
    currentDraft: String,
    mediaMetadata: MediaMetadata,
    isAiTranslationEnabled: Boolean,
    isAiTranslating: Boolean,
    isStandardTranslating: Boolean,
    onStandardTranslatingChange: (Boolean) -> Unit,
    translationJob: Job?,
    onTranslationJobChange: (Job?) -> Unit,
    isDialogAiTranslationRunning: Boolean,
    onDialogAiRunningChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTranslated: (String) -> Unit,
    viewModel: LyricsMenuViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalContext.current.resources.configuration
    val defaultLanguageCode = remember(configuration) { configuration.locales.get(0).getDisplayLanguage(Locale.ENGLISH).uppercase(Locale.US).replace(' ', '_') }
    val (targetLanguage, setTargetLanguage) = rememberPreference(TranslatorTargetLangKey, defaultLanguageCode)
    val (aiProvider) = rememberEnumPreference(AiProviderKey, AiProvider.NONE)
    val (aiApiKey) = rememberPreference(AiApiKeyKey, "")
    val (aiCustomEndpoint) = rememberPreference(AiCustomEndpointKey, "")
    val (aiValidationStatus) = rememberEnumPreference(AiApiValidationStatusKey, AiApiValidationStatus.UNKNOWN)
    val isTranslateEnabled = currentDraft.isNotBlank() && currentDraft != LyricsEntity.LYRICS_NOT_FOUND
    val languages by produceState(initialValue = emptyList<TranslatorLang>()) { withContext(Dispatchers.IO) { value = TranslatorLanguages.load(context) } }
    var sourceExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var selectedSource by rememberSaveable { mutableStateOf(if (isAiTranslationEnabled) EditorTranslationSource.AI_TRANSLATION else EditorTranslationSource.TRANSLATION) }
    var selectedLanguageCode by rememberSaveable { mutableStateOf(targetLanguage.ifBlank { defaultLanguageCode }) }
    val selectedLanguageName = languages.firstOrNull { it.code == selectedLanguageCode }?.name ?: selectedLanguageCode
    val canUseSelectedSource = selectedSource != EditorTranslationSource.AI_TRANSLATION || isAiTranslationEnabled
    val isTranslationInProgress = isStandardTranslating || isAiTranslating
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(currentDraft)) }
    LaunchedEffect(currentDraft) { textFieldValue = TextFieldValue(currentDraft) }
    BasicAlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false), modifier = Modifier.padding(24.dp).navigationBarsPadding().imePadding()) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E1E1E), tonalElevation = 0.dp, modifier = Modifier.widthIn(max = 560.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(painter = painterResource(R.drawable.translate), contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                Text(text = stringResource(R.string.translate), style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = textFieldValue, onValueChange = { textFieldValue = it }, enabled = !isTranslationInProgress, singleLine = false, label = { Text(stringResource(R.string.lyrics), color = Color.White.copy(alpha = 0.7f)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 220.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White.copy(alpha = 0.6f), unfocusedBorderColor = Color.White.copy(alpha = 0.18f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.source), color = Color.White.copy(alpha = 0.8f), modifier = Modifier.width(96.dp))
                        ExposedDropdownMenuBox(expanded = sourceExpanded, onExpandedChange = { if (!isTranslationInProgress) sourceExpanded = it }, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(value = when (selectedSource) { EditorTranslationSource.AI_TRANSLATION -> stringResource(R.string.ai_translation_menu); EditorTranslationSource.TRANSLATION -> stringResource(R.string.translate) }, onValueChange = {}, enabled = !isTranslationInProgress, readOnly = true, singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White.copy(alpha = 0.6f), unfocusedBorderColor = Color.White.copy(alpha = 0.18f), focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.ai_translation_menu)) }, enabled = isAiTranslationEnabled, onClick = { selectedSource = EditorTranslationSource.AI_TRANSLATION; sourceExpanded = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.translate)) }, onClick = { selectedSource = EditorTranslationSource.TRANSLATION; sourceExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.language_label), color = Color.White.copy(alpha = 0.8f), modifier = Modifier.width(96.dp))
                        ExposedDropdownMenuBox(expanded = languageExpanded, onExpandedChange = { if (!isTranslationInProgress) languageExpanded = it }, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(value = selectedLanguageName, onValueChange = {}, enabled = !isTranslationInProgress, readOnly = true, singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White.copy(alpha = 0.6f), unfocusedBorderColor = Color.White.copy(alpha = 0.18f), focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) { languages.forEach { lang -> DropdownMenuItem(text = { Text(lang.name) }, onClick = { selectedLanguageCode = lang.code; setTargetLanguage(lang.code); languageExpanded = false }) } }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { translationJob?.cancel(); onTranslationJobChange(null); onStandardTranslatingChange(false); if (isAiTranslating) viewModel.cancelAiTranslation(); onDialogAiRunningChange(false); onDismiss() }, shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text(stringResource(android.R.string.cancel), color = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(enabled = !isTranslationInProgress && canUseSelectedSource, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White), onClick = {
                        val inputText = textFieldValue.text
                        val languageCode = selectedLanguageCode
                        val languageName = selectedLanguageName
                        setTargetLanguage(languageCode)
                        when (selectedSource) {
                            EditorTranslationSource.AI_TRANSLATION -> { onDialogAiRunningChange(true); viewModel.translateLyricsWithAi(mediaMetadata = mediaMetadata, lyrics = inputText, targetLanguage = languageCode) }
                            EditorTranslationSource.TRANSLATION -> {
                                onStandardTranslatingChange(true)
                                val job = scope.launch {
                                    try {
                                        val lang = try { Language(languageCode) } catch (e: Exception) { try { Language(languageName) } catch (_: Exception) { null } }
                                        if (lang == null) { Toast.makeText(context, context.getString(R.string.unsupported_language, languageName), Toast.LENGTH_SHORT).show(); return@launch }
                                        val translated = withContext(Dispatchers.IO) {
                                            val doc = moe.rukamori.archivetune.ai.AiLyricsDocumentParser.parse(inputText)
                                            if (doc.segments.isEmpty()) inputText else { val tr = Translator(); val out = mutableMapOf<Int, String>(); doc.segments.forEach { seg -> out[seg.id] = tr.translateBlocking(seg.text, lang).translatedText }; doc.rebuild(out) }
                                        }
                                        onTranslated(translated)
                                    } catch (e: Exception) { Toast.makeText(context, context.getString(R.string.translation_failed) + ": " + (e.localizedMessage ?: e.toString()), Toast.LENGTH_SHORT).show() } finally { onStandardTranslatingChange(false); onTranslationJobChange(null) }
                                }
                                onTranslationJobChange(job)
                            }
                        }
                    }, shapes = ButtonDefaults.shapes()) { if (isTranslationInProgress) { LoadingIndicator(modifier = Modifier.size(18.dp), color = Color.White); Spacer(Modifier.width(8.dp)) }; Text(stringResource(R.string.translate)) }
                }
            }
        }
    }
}
