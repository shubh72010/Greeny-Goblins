import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates the deep settings-search index by scanning the settings screens for
 * preference call sites, so every list item is searchable without a hand-kept
 * duplicate list. See SettingsSearchScanner for the extraction rules.
 *
 * The build logs the item count, because the failure mode of a source scanner is silent
 * under-coverage rather than a compile error. The parser is covered by
 * SettingsSearchScannerTest.
 */
abstract class SettingsSearchIndexTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val settingsSourceDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val navigationSourceFile: RegularFileProperty

    /** Composable names to skip, e.g. the hub which builds its own dynamic list. */
    @get:Input
    abstract val excludedScreenFunctions: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val entries =
            SettingsSearchScanner.scan(
                settingsSourceDirectory.get().asFile,
                navigationSourceFile.get().asFile,
                excludedScreenFunctions.get(),
            )
        val outputFile = outputDirectory.get().asFile.resolve("SettingsSearchIndex.g.kt")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(SettingsSearchScanner.render(entries))
        logger.lifecycle("Settings search index: ${entries.size} items -> ${outputFile.name}")
    }
}

internal data class ScannedSetting(
    val key: String,
    val route: String,
    val titleExpr: String,
    val subtitleExpr: String?,
    val iconName: String,
    val keywords: List<String>,
)

internal object SettingsSearchScanner {
    private const val DEFAULT_ICON = "settings"
    private const val LOOKAHEAD_LINES = 60
    private const val HIGHLIGHT_ARG = "highlight"

    /** Rows per generated function; keeps each under the 64 KB JVM method limit. */
    private const val CHUNK_SIZE = 25

    /** `fun Name(` at column 0, i.e. a top-level composable/function declaration. */
    private val topLevelFun = Regex("""^(?:(?:public|internal|private)\s+)?fun\s+([A-Z][A-Za-z0-9]*)\s*\(""", RegexOption.MULTILINE)

    /** Row components. `HighlightablePreference` is a wrapper, not a row. */
    private val rowCall = Regex("""^(\s*)([A-Z][A-Za-z0-9]*(?:Preference|Entry))\(""")

    private val groupOpen = Regex("""\bPreferenceGroup\s*\(""")

    private val groupTitle =
        Regex("""PreferenceGroup\(\s*title\s*=\s*(?:stringResource\(\s*R\.string\.([A-Za-z0-9_]+)\s*\)|"([^"]*)")""", RegexOption.DOT_MATCHES_ALL)

    private val highlightKey = Regex("""HighlightablePreference\(\s*highlightKey\s*=\s*"([^"]+)\"""")

    private val routeLiteral = Regex("""(?:route\s*=\s*|composable\(\s*)"([^"]+)\"""")

    private val pascalCall = Regex("""\b([A-Z][A-Za-z0-9]*)\s*\(""")

    /** `title = { Text(stringResource(R.string.x)) }` or `title = stringResource(R.string.x)`. */
    private val titleRes =
        Regex("""title\s*=\s*(?:\{\s*Text\(\s*)?stringResource\(\s*R\.string\.([A-Za-z0-9_]+)""")

    private val titleLiteral = Regex("""title\s*=\s*(?:\{\s*Text\(\s*)?"([^"]+)"""")

    private val descriptionRes = Regex("""description\s*=\s*stringResource\(\s*R\.string\.([A-Za-z0-9_]+)""")
    private val iconRes = Regex("""icon\s*=\s*[\s\S]{0,200}?painterResource\(\s*R\.drawable\.([A-Za-z0-9_]+)""")

    /** Rows that navigate straight to a nested settings screen instead of highlighting. */
    private val settingsNavigate =
        Regex("""navigate\(\s*"(settings/[A-Za-z0-9_/]*)"""", RegexOption.DOT_MATCHES_ALL)

    fun scan(settingsDir: File, navigationFile: File, excluded: List<String>): List<ScannedSetting> {
        val routes = parseRoutes(navigationFile.readText(), excluded)
        val entries = mutableListOf<ScannedSetting>()
        settingsDir.listFiles { file -> file.extension == "kt" }?.sortedBy { it.name }?.forEach { file ->
            entries += scanFile(file.readText(), routes)
        }
        return dedupe(entries)
    }

    /**
     * Maps composable name -> nav route by pairing each `composable("...")` route with the
     * screen call in its own body, so routes stay automatic too. The block is bounded by the
     * next `composable(`; without that bound the window bleeds and mis-pairs routes.
     */
    internal fun parseRoutes(text: String, excluded: List<String>): Map<String, String> {
        val routes = LinkedHashMap<String, String>()
        var cursor = 0
        while (true) {
            val start = text.indexOf("composable(", cursor)
            if (start < 0) break
            val next = text.indexOf("composable(", start + 1)
            cursor = if (next < 0) text.length else next
            val block = text.substring(start, cursor)
            val route = routeLiteral.find(block)?.groupValues?.get(1) ?: continue
            // The body is a single screen call, so the last PascalCase call in the block is it.
            val screen = pascalCall.findAll(block).lastOrNull()?.groupValues?.get(1) ?: continue
            if (screen in excluded) continue
            routes[screen] = route.substringBefore('?')
        }
        return routes
    }

    /**
     * Scans a whole file, not one function: screens delegate their body to private
     * composables in the same file (`LastFMSettings` -> `LastFmSettingsSuccess`), so rows
     * live outside the entry function. Files holding several screens are attributed by
     * the nearest preceding top-level declaration.
     *
     * ponytail: rows with a runtime-computed title (`title = "Edit $title"`) and screens
     * with bespoke row components outside the `*Preference`/`*Entry` set are not indexed.
     * A real Kotlin parser (KSP) is the upgrade if the misses start to matter.
     */
    private fun scanFile(text: String, routes: Map<String, String>): List<ScannedSetting> {
        val declaredScreens =
            topLevelFun
                .findAll(text)
                .map { it.groupValues[1] }
                .filter { it in routes }
                .toList()
        if (declaredScreens.isEmpty()) return emptyList()

        val lines = text.lines()
        val declarations =
            topLevelFun
                .findAll(text)
                .map { it.groupValues[1] to text.substring(0, it.range.first).count { ch -> ch == '\n' } }
                .toList()
        val entries = mutableListOf<ScannedSetting>()
        // Section title as (Kotlin expression, plain words for search keywords).
        var sectionExpr: String? = null
        var sectionWords: String? = null
        var pendingHighlight: String? = null

        for ((index, line) in lines.withIndex()) {
            if (groupOpen.find(line) != null) {
                // `title` often sits on the next line, so look a few lines past the call.
                val match = groupTitle.find(lines.drop(index).take(4).joinToString("\n"))
                sectionExpr = null
                sectionWords = null
                if (match != null) {
                    val resName = match.groupValues[1]
                    if (resName.isNotEmpty()) {
                        sectionExpr = "stringResource(R.string.$resName)"
                        sectionWords = resName
                    } else {
                        sectionExpr = literal(match.groupValues[2])
                        sectionWords = match.groupValues[2]
                    }
                }
            }
            highlightKey.find(line)?.let { pendingHighlight = it.groupValues[1] }

            val row = rowCall.find(line) ?: continue
            if (row.groupValues[2] == "HighlightablePreference") continue

            val screen =
                declarations.lastOrNull { it.second <= index && it.first in routes }?.first
                    ?: declaredScreens.first()
            val baseRoute = routes.getValue(screen)

            // Arguments may sit on the row's own line or below it, up to the next row/group.
            val window = buildString {
                append(line).append('\n')
                for (next in lines.drop(index + 1).take(LOOKAHEAD_LINES)) {
                    if (rowCall.find(next) != null || groupOpen.find(next) != null) break
                    append(next).append('\n')
                }
            }

            val resTitle = titleRes.find(window)
            val literalTitle = if (resTitle == null) titleLiteral.find(window)?.groupValues?.get(1) else null
            val title =
                resTitle?.let { "stringResource(R.string.${it.groupValues[1]})" }
                    ?: literalTitle?.let(::literal)
                    ?: continue
            val slug = resTitle?.groupValues?.get(1) ?: literalTitle?.let(::slugify) ?: continue

            val icon = iconRes.find(window)?.groupValues?.get(1) ?: DEFAULT_ICON
            val highlight = pendingHighlight
            pendingHighlight = null
            val segment = baseRoute.substringAfterLast('/')
            val words = sectionWords ?: segment
            val section = sectionExpr ?: "\"${humanize(segment)}\""
            val route =
                when {
                    highlight != null -> "$baseRoute?$HIGHLIGHT_ARG=$highlight"
                    else -> settingsNavigate.find(window)?.groupValues?.get(1) ?: baseRoute
                }

            entries +=
                ScannedSetting(
                    key = "$screen.$slug",
                    route = route,
                    titleExpr = title,
                    subtitleExpr =
                        descriptionRes
                            .find(window)
                            ?.let { "stringResource(R.string.${it.groupValues[1]})" }
                            ?: section,
                    iconName = icon,
                    keywords = keywordsFor(slug, words),
                )
        }
        return entries
    }

    /** Resource names carry words the display title drops: `enable_dynamic_theme`. */
    private fun keywordsFor(titleSlug: String, sectionWords: String): List<String> =
        (titleSlug.split('_') + sectionWords.split(Regex("[^A-Za-z0-9]+")))
            .map { it.lowercase() }
            .filter { it.length > 2 }
            .distinct()

    private fun humanize(routeSegment: String): String =
        routeSegment.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /** Quotes a literal, or null when it is interpolated and so not statically knowable. */
    private fun literal(text: String): String? {
        if (text.contains('$') || text.contains('`')) return null
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private fun slugify(expr: String): String =
        expr.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.joinToString("_")

    private fun dedupe(entries: List<ScannedSetting>): List<ScannedSetting> {
        val seen = mutableSetOf<String>()
        return entries.map { entry ->
            var key = entry.key
            var suffix = 2
            while (!seen.add(key)) key = "${entry.key}_$suffix".also { suffix++ }
            entry.copy(key = key)
        }
    }

    fun render(entries: List<ScannedSetting>): String =
        buildString {
            appendLine(
                """
                /*
                 * JusPlayer (2026)
                 * © Følius — github.com.rukamori
                 * GPL-3.0 License | Contributors: see git history
                 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
                 */

                // GENERATED by SettingsSearchIndexTask — do not edit.
                @file:Suppress("RedundantVisibilityModifier")

                package moe.rukamori.archivetune.ui.screens.settings

                import androidx.compose.material3.MaterialTheme
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.res.painterResource
                import androidx.compose.ui.res.stringResource
                import androidx.navigation.NavController
                import moe.rukamori.archivetune.R

                /**
                 * Every preference row found in the settings screens, with the route that
                 * reveals it. Adding a preference to a settings screen is all it takes for it
                 * to become searchable.
                 *
                 * Emitted in chunks because a single function inlining one composable call per
                 * row blows past the 64 KB JVM method limit.
                 */
                @Composable
                internal fun buildGeneratedSettingsSearchItems(navController: NavController): List<SettingsItem> =
                    buildList {
                """.trimIndent(),
            )
            val chunks = entries.chunked(CHUNK_SIZE)
            chunks.forEachIndexed { chunkIndex, _ ->
                appendLine("                        addAll(settingsSearchChunk$chunkIndex(navController))")
            }
            appendLine("                    }")
            chunks.forEachIndexed { chunkIndex, chunk ->
                appendLine()
                appendLine("                @Composable")
                appendLine("                private fun settingsSearchChunk$chunkIndex(navController: NavController): List<SettingsItem> {")
                appendLine("                    val accent = MaterialTheme.colorScheme.secondary")
                appendLine("                    return listOf(")
                chunk.forEachIndexed { index, entry ->
                    appendLine("                        SettingsItem(")
                    appendLine("                            key = \"${entry.key}\",")
                    appendLine("                            icon = painterResource(R.drawable.${entry.iconName}),")
                    appendLine("                            title = ${entry.titleExpr},")
                    appendLine("                            subtitle = ${entry.subtitleExpr ?: "null"},")
                    appendLine("                            keywords = listOf(${entry.keywords.joinToString(", ") { "\"$it\"" }}),")
                    appendLine("                            accentColor = accent,")
                    appendLine("                            onClick = { navController.navigate(\"${entry.route}\") },")
                    appendLine("                        )${if (index == chunk.lastIndex) "" else ","}")
                }
                appendLine("                    )")
                appendLine("                }")
            }
            appendLine("")
        }
}
