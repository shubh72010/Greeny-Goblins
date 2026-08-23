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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.AudioOffload
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.AutoDownloadOnLikeKey
import moe.rukamori.archivetune.constants.AutoSkipNextOnErrorKey
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothAllowedDevicesKey
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothDelayDefault
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothDelayMax
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothDelayMin
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothDelaySecondsKey
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeGaplessKey
import moe.rukamori.archivetune.constants.DeviceMutePlaybackRecoveryVolumeKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.FlashlightBeatEngineModeKey
import moe.rukamori.archivetune.constants.FlashlightModeKey
import moe.rukamori.archivetune.constants.FlashlightThresholdKey
import moe.rukamori.archivetune.constants.FlashlightVisualizerEnabledKey
import moe.rukamori.archivetune.constants.GlyphBrightnessKey
import moe.rukamori.archivetune.constants.GlyphGammaKey
import moe.rukamori.archivetune.constants.GlyphIdleBreathingKey
import moe.rukamori.archivetune.constants.GlyphPresetKey
import moe.rukamori.archivetune.constants.GlyphVisualizerEnabledKey
import moe.rukamori.archivetune.constants.GlyphVisualizerGainKey
import moe.rukamori.archivetune.constants.HapticVisualizerEnabledKey
import moe.rukamori.archivetune.constants.HapticVisualizerIntensityKey
import moe.rukamori.archivetune.constants.HapticVisualizerModeKey
import moe.rukamori.archivetune.visualizer.BeatEngineMode
import moe.rukamori.archivetune.visualizer.DeviceProfile
import moe.rukamori.archivetune.visualizer.TorchMode
import moe.rukamori.archivetune.constants.HISTORY_DURATION_DEFAULT
import moe.rukamori.archivetune.constants.HistoryDuration
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PermanentShuffleKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.constants.PlayerStreamClientKey
import moe.rukamori.archivetune.constants.PoTokenGvsKey
import moe.rukamori.archivetune.constants.PoTokenPlayerKey
import moe.rukamori.archivetune.constants.SeekExtraSeconds
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.WakelockKey
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.ArtistSeparatorsDialog
import moe.rukamori.archivetune.ui.component.CrossfadeSliderPreference
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.NumberPickerPreference
import moe.rukamori.archivetune.playback.haptic.HapticVisualizerMode
import moe.rukamori.archivetune.ui.component.EnumSegmentedPreference
import moe.rukamori.archivetune.ui.component.NumberPickerPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SliderPreference
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TagsManagementDialog
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(navController: NavController) {
    val (audioQuality, onAudioQualityChange) =
        rememberEnumPreference(
            AudioQualityKey,
            defaultValue = AudioQuality.AUTO,
        )
    val (playerStreamClient, onPlayerStreamClientChange) =
        rememberEnumPreference(
            PlayerStreamClientKey,
            defaultValue = PlayerStreamClient.JUSPLAYER_ENGINE,
        )
    val (lowDataMode, onLowDataModeChange) =
        rememberPreference(
            LowDataModeKey,
            defaultValue = true,
        )
    val (persistentQueue, onPersistentQueueChange) =
        rememberPreference(
            PersistentQueueKey,
            defaultValue = true,
        )
    val (permanentShuffle, onPermanentShuffleChange) =
        rememberPreference(
            PermanentShuffleKey,
            defaultValue = false,
        )
    val (skipSilence, onSkipSilenceChange) =
        rememberPreference(
            SkipSilenceKey,
            defaultValue = false,
        )
    val (audioNormalization, onAudioNormalizationChange) =
        rememberPreference(
            AudioNormalizationKey,
            defaultValue = true,
        )
    val (audioOffload, onAudioOffloadChange) =
        rememberPreference(
            AudioOffload,
            defaultValue = false,
        )

    val (hapticVisualizerEnabled, onHapticVisualizerEnabledChange) =
        rememberPreference(
            HapticVisualizerEnabledKey,
            defaultValue = false,
        )
    val (hapticVisualizerMode, onHapticVisualizerModeChange) =
        rememberPreference(
            HapticVisualizerModeKey,
            defaultValue = HapticVisualizerMode.CONTINUOUS.name,
        )
    val hapticVisualizerModeValue =
        remember(hapticVisualizerMode) {
            runCatching { HapticVisualizerMode.valueOf(hapticVisualizerMode) }
                .getOrDefault(HapticVisualizerMode.CONTINUOUS)
        }
    val (hapticVisualizerIntensity, onHapticVisualizerIntensityChange) =
        rememberPreference(
            HapticVisualizerIntensityKey,
            defaultValue = 100,
        )

    val (glyphEnabled, onGlyphEnabledChange) = rememberPreference(GlyphVisualizerEnabledKey, defaultValue = false)
    val (glyphBrightness, onGlyphBrightnessChange) = rememberPreference(GlyphBrightnessKey, defaultValue = 4095)
    val (glyphGamma, onGlyphGammaChange) = rememberPreference(GlyphGammaKey, defaultValue = 2.2f)
    val (glyphIdle, onGlyphIdleChange) = rememberPreference(GlyphIdleBreathingKey, defaultValue = false)
    val (glyphPreset, onGlyphPresetChange) = rememberPreference(GlyphPresetKey, defaultValue = "np1")
    val (glyphGain, onGlyphGainChange) = rememberPreference(GlyphVisualizerGainKey, defaultValue = 1.0f)
    val (flashlightEnabled, onFlashlightEnabledChange) = rememberPreference(FlashlightVisualizerEnabledKey, defaultValue = false)
    val (flashlightMode, onFlashlightModeChange) = rememberPreference(FlashlightModeKey, defaultValue = TorchMode.AMPLITUDE.name)
    val (flashlightBeatMode, onFlashlightBeatModeChange) = rememberPreference(FlashlightBeatEngineModeKey, defaultValue = BeatEngineMode.SMOOTH.name)
    val (flashlightThreshold, onFlashlightThresholdChange) = rememberPreference(FlashlightThresholdKey, defaultValue = 0.15f)
    val isGlyphSupported = remember { DeviceProfile.detectDevice() != DeviceProfile.DEVICE_UNKNOWN }

    val (seekExtraSeconds, onSeekExtraSeconds) =
        rememberPreference(
            SeekExtraSeconds,
            defaultValue = false,
        )

    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) =
        rememberPreference(
            AutoDownloadOnLikeKey,
            defaultValue = true,
        )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) =
        rememberPreference(
            AutoSkipNextOnErrorKey,
            defaultValue = false,
        )
    val (pauseOnDeviceMute, onPauseOnDeviceMuteChange) =
        rememberPreference(
            PauseOnDeviceMuteKey,
            defaultValue = true,
        )
    val (
        deviceMutePlaybackRecoveryVolume,
        onDeviceMutePlaybackRecoveryVolumeChange,
    ) =
        rememberPreference(
            DeviceMutePlaybackRecoveryVolumeKey,
            defaultValue = 0,
        )
    val (autoStartOnBluetooth, onAutoStartOnBluetoothChange) =
        rememberPreference(
            AutoStartOnBluetoothKey,
            defaultValue = false,
        )
    val (autoStartDelay, onAutoStartDelayChange) =
        rememberPreference(
            AutoStartOnBluetoothDelaySecondsKey,
            defaultValue = AutoStartOnBluetoothDelayDefault,
        )
    val (autoStartAllowedDevices, onAutoStartAllowedDevicesChange) =
        rememberPreference(
            AutoStartOnBluetoothAllowedDevicesKey,
            defaultValue = emptySet(),
        )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) =
        rememberPreference(
            StopMusicOnTaskClearKey,
            defaultValue = false,
        )
    val (historyDuration, onHistoryDurationChange) =
        rememberPreference(
            HistoryDuration,
            defaultValue = HISTORY_DURATION_DEFAULT,
        )

    val (crossfadeEnabled, onCrossfadeEnabledChange) =
        rememberPreference(
            CrossfadeEnabledKey,
            defaultValue = true,
        )
    val (crossfadeDurationSeconds, onCrossfadeDurationSecondsChange) =
        rememberPreference(
            CrossfadeDurationKey,
            defaultValue = 5f,
        )
    val (crossfadeGapless, onCrossfadeGaplessChange) =
        rememberPreference(
            CrossfadeGaplessKey,
            defaultValue = true,
        )

    val (artistSeparators, onArtistSeparatorsChange) =
        rememberPreference(
            ArtistSeparatorsKey,
            defaultValue = ",;/&",
        )
    val (externalDownloaderEnabled, onExternalDownloaderEnabledChange) =
        rememberPreference(
            ExternalDownloaderEnabledKey,
            defaultValue = false,
        )
    val (externalDownloaderPackage, onExternalDownloaderPackageChange) =
        rememberPreference(
            ExternalDownloaderPackageKey,
            defaultValue = "",
        )

    val (wakelockEnabled, onWakelockChange) =
        rememberPreference(
            WakelockKey,
            defaultValue = false,
        )
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, defaultValue = "")
    val (poTokenGvs, _) = rememberPreference(PoTokenGvsKey, defaultValue = "")
    val (poTokenPlayer, _) = rememberPreference(PoTokenPlayerKey, defaultValue = "")
    val isArchiveTuneExtractorEnabled =
        remember(innerTubeCookie, poTokenGvs, poTokenPlayer) {
            hasYouTubeLoginCookie(innerTubeCookie) &&
                poTokenGvs.isNotBlank() &&
                poTokenPlayer.isNotBlank()
        }
    val playerStreamClients =
        remember {
            listOf(
                PlayerStreamClient.WEB_REMIX,
                PlayerStreamClient.ARCHIVETUNE_EXTRACTOR,
                PlayerStreamClient.JUSPLAYER_ENGINE,
            )
        }
    val selectedPlayerStreamClient =
        if (playerStreamClient in playerStreamClients) {
            playerStreamClient
        } else {
            PlayerStreamClient.WEB_REMIX
        }
    val audioQualityEnabled = selectedPlayerStreamClient != PlayerStreamClient.ARCHIVETUNE_EXTRACTOR
    val isPlayerStreamClientEnabled =
        remember(isArchiveTuneExtractorEnabled) {
            { client: PlayerStreamClient ->
                client != PlayerStreamClient.ARCHIVETUNE_EXTRACTOR ||
                    isArchiveTuneExtractorEnabled
            }
        }

    var showArtistSeparatorsDialog by remember { mutableStateOf(false) }
    var showTagsManagementDialog by remember { mutableStateOf(false) }
    var showExternalDownloaderPackageDialog by remember { mutableStateOf(false) }
    var showBnmvNotInstalledDialog by remember { mutableStateOf(false) }
    var showBluetoothDevicePicker by remember { mutableStateOf(false) }
    val bluetoothPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(playerStreamClient, isArchiveTuneExtractorEnabled) {
        if (
            playerStreamClient !in playerStreamClients ||
            (
                playerStreamClient == PlayerStreamClient.ARCHIVETUNE_EXTRACTOR &&
                    !isArchiveTuneExtractorEnabled
            )
        ) {
            onPlayerStreamClientChange(PlayerStreamClient.WEB_REMIX)
        }
    }

    if (showArtistSeparatorsDialog) {
        ArtistSeparatorsDialog(
            currentSeparators = artistSeparators,
            onDismiss = { showArtistSeparatorsDialog = false },
            onSave = { newSeparators ->
                onArtistSeparatorsChange(newSeparators)
                showArtistSeparatorsDialog = false
            },
        )
    }

    if (showTagsManagementDialog) {
        TagsManagementDialog(
            onDismiss = { showTagsManagementDialog = false },
        )
    }

    if (showExternalDownloaderPackageDialog) {
        TextFieldDialog(
            initialTextFieldValue =
                androidx.compose.ui.text.input
                    .TextFieldValue(externalDownloaderPackage),
            onDone = { pkg ->
                onExternalDownloaderPackageChange(pkg)
                showExternalDownloaderPackageDialog = false
            },
            onDismiss = { showExternalDownloaderPackageDialog = false },
            singleLine = true,
            maxLines = 1,
        )
    }

    if (showBnmvNotInstalledDialog) {
        val ctx = LocalContext.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBnmvNotInstalledDialog = false },
            title = { Text(stringResource(R.string.bnmv_dialog_not_installed_title)) },
            text = { Text(stringResource(R.string.bnmv_dialog_not_installed_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showBnmvNotInstalledDialog = false
                        runCatching {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(moe.rukamori.archivetune.visualizer.bnmv.BnmvConstants.GITHUB_RELEASES_URL),
                            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                            ctx.startActivity(intent)
                        }
                    },
                ) { Text(stringResource(R.string.bnmv_dialog_install)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBnmvNotInstalledDialog = false }) {
                    Text(stringResource(R.string.bnmv_dialog_dismiss))
                }
            },
        )
    }

    if (showBluetoothDevicePicker) {
        moe.rukamori.archivetune.ui.component.BluetoothDevicePickerDialog(
            currentSelected = autoStartAllowedDevices,
            onDismiss = { showBluetoothDevicePicker = false },
            onConfirm = { selected ->
                onAutoStartAllowedDevicesChange(selected)
                showBluetoothDevicePicker = false
            },
            onRequestPermission = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_and_audio)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.player)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.audio_quality)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        selectedValue = audioQuality,
                        onValueSelected = onAudioQualityChange,
                        isEnabled = audioQualityEnabled,
                        valueText = {
                            when (it) {
                                AudioQuality.HIGHEST -> stringResource(R.string.audio_quality_max)
                                AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                                AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                                AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                            }
                        },
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.player_stream_client)) },
                        description = stringResource(R.string.player_stream_client_desc),
                        icon = { Icon(painterResource(R.drawable.integration), null) },
                        selectedValue = selectedPlayerStreamClient,
                        values = playerStreamClients,
                        onValueSelected = onPlayerStreamClientChange,
                        isValueEnabled = isPlayerStreamClientEnabled,
                        valueText = {
                            when (it) {
                                PlayerStreamClient.WEB_REMIX -> {
                                    stringResource(R.string.player_stream_client_web_remix)
                                }

                                PlayerStreamClient.ARCHIVETUNE_EXTRACTOR -> {
                                    stringResource(
                                        R.string.player_stream_client_archivetune_extractor,
                                    )
                                }

                                PlayerStreamClient.JUSPLAYER_ENGINE -> {
                                    stringResource(R.string.player_stream_client_jusplayer_engine)
                                }

                                else -> {
                                    stringResource(R.string.player_stream_client_web_remix)
                                }
                            }
                        },
                        valueDescription = {
                            when (it) {
                                PlayerStreamClient.WEB_REMIX -> {
                                    stringResource(R.string.player_stream_client_web_remix_desc)
                                }

                                PlayerStreamClient.ARCHIVETUNE_EXTRACTOR -> {
                                    if (isArchiveTuneExtractorEnabled) {
                                        stringResource(
                                            R.string.player_stream_client_archivetune_extractor_desc,
                                        )
                                    } else {
                                        stringResource(
                                            R.string.player_stream_client_archivetune_extractor_login_required,
                                        )
                                    }
                                }

                                PlayerStreamClient.JUSPLAYER_ENGINE -> {
                                    stringResource(R.string.player_stream_client_jusplayer_engine_desc)
                                }

                                else -> {
                                    stringResource(R.string.player_stream_client_web_remix_desc)
                                }
                            }
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.mori_cipher_settings_title)) },
                        description = stringResource(R.string.mori_cipher_settings_description),
                        icon = { Icon(painterResource(R.drawable.security), null) },
                        onClick = { navController.navigate("settings/player/chiper") },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.low_data_mode_title)) },
                        description = stringResource(R.string.low_data_mode_description),
                        icon = { Icon(painterResource(R.drawable.android_cell), null) },
                        checked = lowDataMode,
                        onCheckedChange = onLowDataModeChange,
                    )
                }

                item {
                    SliderPreference(
                        title = { Text(stringResource(R.string.history_duration)) },
                        icon = { Icon(painterResource(R.drawable.history), null) },
                        value = historyDuration,
                        onValueChange = onHistoryDurationChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.audio_crossfade_title)) },
                        description = stringResource(R.string.audio_crossfade_description),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = crossfadeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                onAudioOffloadChange(false)
                            }
                            onCrossfadeEnabledChange(enabled)
                        },
                    )
                }

                item {
                    CrossfadeSliderPreference(
                        valueSeconds = crossfadeDurationSeconds,
                        onValueChange = onCrossfadeDurationSecondsChange,
                        isEnabled = crossfadeEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.crossfade_gapless_title)) },
                        description = stringResource(R.string.crossfade_gapless_description),
                        icon = { Icon(painterResource(R.drawable.fast_forward), null) },
                        checked = crossfadeGapless,
                        onCheckedChange = onCrossfadeGaplessChange,
                        isEnabled = crossfadeEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.skip_silence)) },
                        icon = { Icon(painterResource(R.drawable.fast_forward), null) },
                        checked = skipSilence,
                        onCheckedChange = onSkipSilenceChange,
                        isEnabled = !audioOffload,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.audio_normalization)) },
                        icon = { Icon(painterResource(R.drawable.volume_up), null) },
                        checked = audioNormalization,
                        onCheckedChange = onAudioNormalizationChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.audio_offload)) },
                        description = stringResource(R.string.audio_offload_desc),
                        icon = { Icon(painterResource(R.drawable.speed), null) },
                        checked = audioOffload,
                        onCheckedChange = { enabled ->
                            onAudioOffloadChange(enabled)
                            if (enabled) {
                                onSkipSilenceChange(false)
                                onCrossfadeEnabledChange(false)
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.seek_seconds_addup)) },
                        description = stringResource(R.string.seek_seconds_addup_description),
                        icon = { Icon(painterResource(R.drawable.arrow_forward), null) },
                        checked = seekExtraSeconds,
                        onCheckedChange = onSeekExtraSeconds,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pause_on_device_mute)) },
                        description = stringResource(R.string.pause_on_device_mute_desc),
                        icon = { Icon(painterResource(R.drawable.volume_off), null) },
                        checked = pauseOnDeviceMute,
                        onCheckedChange = onPauseOnDeviceMuteChange,
                    )
                }

                item(visible = pauseOnDeviceMute) {
                    val context = LocalContext.current
                    val disabledLabel = stringResource(R.string.device_mute_recovery_volume_disabled)
                    val recoveryVolumeText =
                        remember(context, disabledLabel) {
                            { value: Int ->
                                if (value == 0) {
                                    disabledLabel
                                } else {
                                    context.getString(R.string.percentage_format, value)
                                }
                            }
                        }
                    NumberPickerPreference(
                        title = { Text(stringResource(R.string.device_mute_recovery_volume)) },
                        icon = { Icon(painterResource(R.drawable.volume_up), null) },
                        value = deviceMutePlaybackRecoveryVolume,
                        onValueChange = onDeviceMutePlaybackRecoveryVolumeChange,
                        minValue = 0,
                        maxValue = 100,
                        valueText = recoveryVolumeText,
                        isEnabled = pauseOnDeviceMute,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_start_on_bluetooth)) },
                        description = stringResource(R.string.auto_start_on_bluetooth_desc),
                        icon = { Icon(painterResource(R.drawable.bluetooth), null) },
                        checked = autoStartOnBluetooth,
                        onCheckedChange = onAutoStartOnBluetoothChange,
                    )
                }

                item(visible = autoStartOnBluetooth) {
                    val ctxDelay = LocalContext.current
                    val delayValueText = remember(ctxDelay) {
                        { v: Int -> ctxDelay.getString(R.string.auto_start_on_bluetooth_delay_seconds, v) }
                    }
                    NumberPickerPreference(
                        title = { Text(stringResource(R.string.auto_start_on_bluetooth_delay)) },
                        icon = { Icon(painterResource(R.drawable.timer), null) },
                        value = autoStartDelay.coerceIn(AutoStartOnBluetoothDelayMin, AutoStartOnBluetoothDelayMax),
                        onValueChange = onAutoStartDelayChange,
                        minValue = AutoStartOnBluetoothDelayMin,
                        maxValue = AutoStartOnBluetoothDelayMax,
                        valueText = delayValueText,
                        isEnabled = autoStartOnBluetooth,
                    )
                }

                item(visible = autoStartOnBluetooth) {
                    val desc = if (autoStartAllowedDevices.isEmpty()) {
                        stringResource(R.string.auto_start_on_bluetooth_allowed_devices_all)
                    } else {
                        stringResource(R.string.auto_start_on_bluetooth_allowed_devices_count, autoStartAllowedDevices.size)
                    }
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.auto_start_on_bluetooth_allowed_devices)) },
                        description = desc,
                        icon = { Icon(painterResource(R.drawable.bluetooth), null) },
                        onClick = { showBluetoothDevicePicker = true },
                    )
                }
            }

            PreferenceGroup(
                title = stringResource(R.string.bnmv_integration),
            ) {
                // External BNMV status + install CTA
                item {
                    val ctx = LocalContext.current
                    val isInstalled = remember { moe.rukamori.archivetune.visualizer.bnmv.BnmvController.isInstalled(ctx) }
                    if (!isInstalled) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.bnmv_not_installed)) },
                            description = stringResource(R.string.bnmv_not_installed_desc),
                            icon = { Icon(painterResource(R.drawable.download), null) },
                            onClick = {
                                runCatching {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(moe.rukamori.archivetune.visualizer.bnmv.BnmvConstants.PLAY_STORE_URL),
                                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    ctx.startActivity(intent)
                                }
                            },
                        )
                    } else {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.bnmv_open)) },
                            description = stringResource(R.string.bnmv_connected) + " — " + stringResource(R.string.bnmv_disconnected).substringBefore(" ("),
                            icon = { Icon(painterResource(R.drawable.download), null) },
                            onClick = {
                                runCatching {
                                    val launch = ctx.packageManager.getLaunchIntentForPackage(
                                        moe.rukamori.archivetune.visualizer.bnmv.BnmvConstants.PACKAGE_NAME,
                                    )
                                    if (launch != null) ctx.startActivity(launch) else {
                                        moe.rukamori.archivetune.visualizer.bnmv.BnmvController.start(ctx)
                                    }
                                }
                            },
                        )
                    }
                }

                // Local haptics (still in-process, not BNMV external — kept for users without BNMV)
                item {
                    val ctx = LocalContext.current
                    SwitchPreference(
                        title = { Text(stringResource(R.string.haptic_visualizer)) },
                        description = stringResource(R.string.haptic_visualizer_desc) + " (local, also drives external BNMV haptics via UDP when BNMV is installed)",
                        icon = { Icon(painterResource(R.drawable.vibration), null) },
                        checked = hapticVisualizerEnabled,
                        onCheckedChange = { enabled ->
                            onHapticVisualizerEnabledChange(enabled)
                            if (enabled && !moe.rukamori.archivetune.visualizer.bnmv.BnmvController.isInstalled(ctx)) {
                                showBnmvNotInstalledDialog = true
                            }
                        },
                    )
                }

                item(visible = hapticVisualizerEnabled) {
                    EnumSegmentedPreference(
                        title = { Text(stringResource(R.string.haptic_visualizer_mode)) },
                        description = stringResource(R.string.haptic_visualizer_mode_desc),
                        icon = { Icon(painterResource(R.drawable.vibration), null) },
                        selectedValue = hapticVisualizerModeValue,
                        onValueSelected = { mode -> onHapticVisualizerModeChange(mode.name) },
                        valueText = {
                            when (it) {
                                HapticVisualizerMode.CONTINUOUS -> {
                                    stringResource(R.string.haptic_visualizer_mode_continuous)
                                }

                                HapticVisualizerMode.BEAT -> {
                                    stringResource(R.string.haptic_visualizer_mode_beat)
                                }
                            }
                        },
                    )
                }

                item(visible = hapticVisualizerEnabled) {
                    val context = LocalContext.current
                    NumberPickerPreference(
                        title = { Text(stringResource(R.string.haptic_visualizer_intensity)) },
                        icon = { Icon(painterResource(R.drawable.vibration), null) },
                        value = hapticVisualizerIntensity,
                        onValueChange = onHapticVisualizerIntensityChange,
                        minValue = 10,
                        maxValue = 150,
                        valueText = { value -> context.getString(R.string.percentage_format, value) },
                    )
                }

                // External Glyph via BNMV (broadcasts + UDP NETWORK source)
                item {
                    val ctx = LocalContext.current
                    val isInstalled = remember { moe.rukamori.archivetune.visualizer.bnmv.BnmvController.isInstalled(ctx) }
                    SwitchPreference(
                        title = { Text("Glyph Music Visualizer (external)") },
                        description = if (!isInstalled) stringResource(R.string.bnmv_not_installed_desc)
                        else if (isGlyphSupported) "Stream to external BNMV via UDP (NETWORK source, 60 FPS) — Glyph Interface for Nothing phones"
                        else "External BNMV only — no local bundling. Install BNMV to use Glyph on Nothing phones",
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        checked = glyphEnabled,
                        onCheckedChange = { enabled ->
                            onGlyphEnabledChange(enabled)
                            // Drive external toggle eagerly for instant feedback
                            moe.rukamori.archivetune.visualizer.bnmv.BnmvController.toggleFeature(
                                ctx, moe.rukamori.archivetune.visualizer.bnmv.BnmvConstants.ACTION_TOGGLE_GLYPHS, enabled,
                            )
                            if (enabled && !moe.rukamori.archivetune.visualizer.bnmv.BnmvController.isInstalled(ctx)) {
                                showBnmvNotInstalledDialog = true
                            }
                        },
                    )
                }
                item(visible = glyphEnabled) {
                    val ctx = LocalContext.current
                    ListPreference(
                        title = { Text("Glyph Preset (external)") },
                        description = "Preset key sent via ACTION_SET_PRESET to external BNMV",
                        icon = { Icon(painterResource(R.drawable.style), null) },
                        selectedValue = glyphPreset,
                        values = listOf("np1", "np1-bass-flash", "np1-center-bass", "np1-spectrum", "np2", "np2-bass", "np2a", "np3-circle", "np3-alternating"),
                        onValueSelected = { key ->
                            onGlyphPresetChange(key)
                            moe.rukamori.archivetune.visualizer.bnmv.BnmvController.setPreset(ctx, key)
                        },
                        valueText = { it },
                    )
                }

                // External Flashlight via BNMV
                item {
                    val ctx = LocalContext.current
                    SwitchPreference(
                        title = { Text("Flashlight Visualizer (external)") },
                        description = "Pulse device torch via external BNMV (ACTION_TOGGLE_TORCH + UDP)",
                        icon = { Icon(painterResource(R.drawable.bolt), null) },
                        checked = flashlightEnabled,
                        onCheckedChange = { enabled ->
                            onFlashlightEnabledChange(enabled)
                            moe.rukamori.archivetune.visualizer.bnmv.BnmvController.toggleFeature(
                                ctx, moe.rukamori.archivetune.visualizer.bnmv.BnmvConstants.ACTION_TOGGLE_TORCH, enabled,
                            )
                            if (enabled && !moe.rukamori.archivetune.visualizer.bnmv.BnmvController.isInstalled(ctx)) {
                                showBnmvNotInstalledDialog = true
                            }
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.queue)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.persistent_queue)) },
                        description = stringResource(R.string.persistent_queue_desc),
                        icon = { Icon(painterResource(R.drawable.queue_music), null) },
                        checked = persistentQueue,
                        onCheckedChange = onPersistentQueueChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.permanent_shuffle)) },
                        description = stringResource(R.string.permanent_shuffle_desc),
                        icon = { Icon(painterResource(R.drawable.shuffle), null) },
                        checked = permanentShuffle,
                        onCheckedChange = onPermanentShuffleChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_download_on_like)) },
                        description = stringResource(R.string.auto_download_on_like_desc),
                        icon = { Icon(painterResource(R.drawable.download), null) },
                        checked = autoDownloadOnLike,
                        onCheckedChange = onAutoDownloadOnLikeChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                        description = stringResource(R.string.auto_skip_next_on_error_desc),
                        icon = { Icon(painterResource(R.drawable.skip_next), null) },
                        checked = autoSkipNextOnError,
                        onCheckedChange = onAutoSkipNextOnErrorChange,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.misc)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                        icon = { Icon(painterResource(R.drawable.clear_all), null) },
                        checked = stopMusicOnTaskClear,
                        onCheckedChange = onStopMusicOnTaskClearChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.wakelock)) },
                        description = stringResource(R.string.wakelock_desc),
                        icon = { Icon(painterResource(R.drawable.bolt), null) },
                        checked = wakelockEnabled,
                        onCheckedChange = onWakelockChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.artist_separators)) },
                        description = artistSeparators.map { "\"$it\"" }.joinToString("  "),
                        icon = { Icon(painterResource(R.drawable.artist), null) },
                        onClick = { showArtistSeparatorsDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.manage_playlist_tags)) },
                        description = stringResource(R.string.manage_playlist_tags_desc),
                        icon = { Icon(painterResource(R.drawable.style), null) },
                        onClick = { showTagsManagementDialog = true },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.external_downloader)) },
                        description = stringResource(R.string.external_downloader_desc),
                        icon = { Icon(painterResource(R.drawable.download), null) },
                        checked = externalDownloaderEnabled,
                        onCheckedChange = onExternalDownloaderEnabledChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.external_downloader_package)) },
                        description = externalDownloaderPackage.ifEmpty { stringResource(R.string.external_downloader_package_desc) },
                        icon = { Icon(painterResource(R.drawable.integration), null) },
                        onClick = { showExternalDownloaderPackageDialog = true },
                        isEnabled = externalDownloaderEnabled,
                    )
                }
            }
        }
    }
}
