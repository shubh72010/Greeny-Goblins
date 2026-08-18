/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.constants.QuickPicksDisplayModeKey
import moe.rukamori.archivetune.constants.ShowHomeAccountPlaylistsKey
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.constants.ShowHomeForgottenFavoritesKey
import moe.rukamori.archivetune.constants.ShowHomeKeepListeningKey
import moe.rukamori.archivetune.constants.ShowHomeQuickPicksKey
import moe.rukamori.archivetune.constants.ShowHomeRemoteSectionsKey
import moe.rukamori.archivetune.constants.ShowHomeSimilarKey
import moe.rukamori.archivetune.constants.ShowHomeSpeedDialKey
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

class HomeRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        val showCategoryChips: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeCategoryChipsKey] ?: true }
                .distinctUntilChanged()

        val quickPicksDisplayMode: Flow<QuickPicksDisplayMode> =
            context.dataStore.data
                .map { preferences ->
                    preferences[QuickPicksDisplayModeKey].toEnum(QuickPicksDisplayMode.CARD)
                }.distinctUntilChanged()

        val showTonalBackdrop: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[DisableBlurKey] == false }
                .distinctUntilChanged()

        val showQuickPicks: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeQuickPicksKey] ?: true }
                .distinctUntilChanged()

        val showSpeedDial: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeSpeedDialKey] ?: true }
                .distinctUntilChanged()

        val showKeepListening: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeKeepListeningKey] ?: true }
                .distinctUntilChanged()

        val showAccountPlaylists: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeAccountPlaylistsKey] ?: true }
                .distinctUntilChanged()

        val showForgottenFavorites: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeForgottenFavoritesKey] ?: true }
                .distinctUntilChanged()

        val showSimilar: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeSimilarKey] ?: true }
                .distinctUntilChanged()

        val showRemoteSections: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeRemoteSectionsKey] ?: true }
                .distinctUntilChanged()
    }
