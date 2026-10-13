/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.extensions

import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.pages.SearchSummary
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage

// shortcut: vendored from unpublished core c7b3095 (CI cannot fetch it); core gitlink stays on 215b098 until rukamori/core publishes the filter APIs, then delete this file and re-import from innertube.models.
// Title words blocked by the "Hide blocked titles" toggle (Content settings).
// ponytail: single hardcoded set on purpose — one toggle, no custom-list UI until asked.
val BLOCKED_TITLE_WORDS = setOf("montagem", "funk")

fun String.containsBlockedTitleWord(): Boolean {
    val lower = lowercase()
    return BLOCKED_TITLE_WORDS.any { it in lower }
}

fun <T : YTItem> List<T>.filterBlockedTitles(enabled: Boolean = true) =
    if (enabled) {
        filterNot { it.title.containsBlockedTitleWord() }
    } else {
        this
    }

fun BrowseResult.filterBlockedTitles(enabled: Boolean = true) =
    if (enabled) {
        copy(
            items =
                items.mapNotNull {
                    it.copy(
                        items =
                            it.items
                                .filterBlockedTitles()
                                .ifEmpty { return@mapNotNull null },
                    )
                },
        )
    } else {
        this
    }

fun HomePage.filterBlockedTitles(enabled: Boolean = true) =
    if (enabled) {
        copy(
            sections =
                sections.map {
                    it.copy(items = it.items.filterBlockedTitles())
                },
        )
    } else {
        this
    }

fun SearchSummaryPage.filterBlockedTitles(enabled: Boolean) =
    if (enabled) {
        SearchSummaryPage(
            summaries.mapNotNull { s ->
                SearchSummary(
                    title = s.title,
                    items = s.items.filterBlockedTitles().ifEmpty { return@mapNotNull null },
                )
            },
        )
    } else {
        this
    }
