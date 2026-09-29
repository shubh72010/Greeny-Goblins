/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SubsonicBaseUrlKey
import moe.rukamori.archivetune.constants.SubsonicEnabledKey
import moe.rukamori.archivetune.constants.SubsonicMusicFolderKey
import moe.rukamori.archivetune.constants.SubsonicPasswordKey
import moe.rukamori.archivetune.constants.SubsonicStrictOnlyKey
import moe.rukamori.archivetune.constants.SubsonicUsernameKey
import moe.rukamori.archivetune.subsonic.SubsonicClient
import moe.rukamori.archivetune.subsonic.SubsonicConfig
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * Phase 1 OpenSubsonic server settings (single server).
 * Compatible with Navidrome, Airsonic-Advanced, Gonic, Ampache, …
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsonicSettings(navController: NavController) {
    val (enabled, onEnabledChange) = rememberPreference(SubsonicEnabledKey, false)
    val (baseUrl, onBaseUrlChange) = rememberPreference(SubsonicBaseUrlKey, "")
    val (username, onUsernameChange) = rememberPreference(SubsonicUsernameKey, "")
    val (password, onPasswordChange) = rememberPreference(SubsonicPasswordKey, "")
    val (musicFolder, onMusicFolderChange) = rememberPreference(SubsonicMusicFolderKey, "")
    val (strictOnly, onStrictOnlyChange) = rememberPreference(SubsonicStrictOnlyKey, false)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { SubsonicClient() }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Field?>(null) }

    Scaffold { innerPadding ->
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            SettingsEditorialHeader(
                title = stringResource(R.string.subsonic_integration),
                onBack = { navController.navigateUp() },
            )
            PreferenceGroup(title = stringResource(R.string.general)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.subsonic_enable)) },
                        description = stringResource(R.string.subsonic_enable_description),
                        icon = { Icon(painterResource(R.drawable.storage), null) },
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(if (baseUrl.isBlank()) "—" else baseUrl) },
                        description = stringResource(R.string.subsonic_server_url_description),
                        icon = { Icon(painterResource(R.drawable.link), null) },
                        onClick = { editing = Field.Url },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(if (username.isBlank()) "—" else username) },
                        description = stringResource(R.string.username),
                        icon = { Icon(painterResource(R.drawable.account), null) },
                        onClick = { editing = Field.Username },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.password)) },
                        description = if (password.isBlank()) "—" else "••••••••",
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        onClick = { editing = Field.Password },
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.subsonic_strict_only)) },
                        description = stringResource(R.string.subsonic_strict_only_description),
                        icon = { Icon(painterResource(R.drawable.music_note), null) },
                        checked = strictOnly,
                        onCheckedChange = onStrictOnlyChange,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.subsonic_test_connection)) },
                        description = status,
                        icon = { Icon(painterResource(R.drawable.update), null) },
                        onClick = {
                            if (testing) return@PreferenceEntry
                            testing = true
                            status = null
                            scope.launch {
                                val config = SubsonicConfig(baseUrl.trim(), username.trim(), password)
                                val ok = runCatching { client.ping(config) }.getOrDefault(false)
                                status =
                                    context.getString(
                                        if (ok) R.string.subsonic_test_success else R.string.subsonic_test_failed,
                                    )
                                testing = false
                            }
                        },
                    )
                }
            }
            PreferenceGroup(title = stringResource(R.string.subsonic_libraries_title)) {
                item {
                    PreferenceEntry(
                        title = { Text(if (musicFolder.isBlank()) "—" else musicFolder) },
                        description = stringResource(R.string.subsonic_library_description),
                        icon = { Icon(painterResource(R.drawable.library_music), null) },
                        onClick = { editing = Field.MusicFolder },
                    )
                }
            }
            InfoLabel(text = stringResource(R.string.subsonic_compat_note))
        }
    }

    editing?.let { field ->
        val initial =
            when (field) {
                Field.Url -> baseUrl
                Field.Username -> username
                Field.Password -> password
                Field.MusicFolder -> musicFolder
            }
        TextFieldDialog(
            initialTextFieldValue = TextFieldValue(initial),
            onDone = { data ->
                when (field) {
                    Field.Url -> onBaseUrlChange(SubsonicClient.normalizeBaseUrl(data.trim()))
                    Field.Username -> onUsernameChange(data.trim())
                    Field.Password -> onPasswordChange(data)
                    Field.MusicFolder -> onMusicFolderChange(data.trim())
                }
                editing = null
            },
            onDismiss = { editing = null },
            singleLine = true,
            maxLines = 1,
            isInputValid = { if (field == Field.MusicFolder) true else it.isNotEmpty() },
        )
    }
}

private enum class Field { Url, Username, Password, MusicFolder }
