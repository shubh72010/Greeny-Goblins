/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.home

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import javax.inject.Inject

class ObserveHomePresentationPreferencesUseCase
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) {
        operator fun invoke(): Flow<HomePresentationPreferences> {
            val quickPicksAndSpeedDial =
                combine(repository.showQuickPicks, repository.showSpeedDial) { q, s -> q to s }
            val keepListeningAndAccount =
                combine(repository.showKeepListening, repository.showAccountPlaylists) { k, a -> k to a }
            val forgottenAndSimilar =
                combine(repository.showForgottenFavorites, repository.showSimilar) { f, s -> f to s }
            val sectionVisibility =
                combine(
                    quickPicksAndSpeedDial,
                    keepListeningAndAccount,
                    forgottenAndSimilar,
                    repository.showRemoteSections,
                ) { qs, ka, fs, remote ->
                    SectionVisibility(
                        showQuickPicks = qs.first,
                        showSpeedDial = qs.second,
                        showKeepListening = ka.first,
                        showAccountPlaylists = ka.second,
                        showForgottenFavorites = fs.first,
                        showSimilar = fs.second,
                        showRemoteSections = remote,
                    )
                }

            return combine(
                repository.showCategoryChips,
                repository.quickPicksDisplayMode,
                repository.showTonalBackdrop,
                sectionVisibility,
            ) { showCategoryChips, quickPicksDisplayMode, showTonalBackdrop, sections ->
                HomePresentationPreferences(
                    showCategoryChips = showCategoryChips,
                    quickPicksDisplayMode = quickPicksDisplayMode,
                    showTonalBackdrop = showTonalBackdrop,
                    showQuickPicks = sections.showQuickPicks,
                    showSpeedDial = sections.showSpeedDial,
                    showKeepListening = sections.showKeepListening,
                    showAccountPlaylists = sections.showAccountPlaylists,
                    showForgottenFavorites = sections.showForgottenFavorites,
                    showSimilar = sections.showSimilar,
                    showRemoteSections = sections.showRemoteSections,
                )
            }
        }
    }

private data class SectionVisibility(
    val showQuickPicks: Boolean,
    val showSpeedDial: Boolean,
    val showKeepListening: Boolean,
    val showAccountPlaylists: Boolean,
    val showForgottenFavorites: Boolean,
    val showSimilar: Boolean,
    val showRemoteSections: Boolean,
)

@Immutable
data class HomePresentationPreferences(
    val showCategoryChips: Boolean,
    val quickPicksDisplayMode: QuickPicksDisplayMode,
    val showTonalBackdrop: Boolean,
    val showQuickPicks: Boolean,
    val showSpeedDial: Boolean,
    val showKeepListening: Boolean,
    val showAccountPlaylists: Boolean,
    val showForgottenFavorites: Boolean,
    val showSimilar: Boolean,
    val showRemoteSections: Boolean,
)
