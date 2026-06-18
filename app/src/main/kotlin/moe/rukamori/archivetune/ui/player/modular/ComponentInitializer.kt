package moe.rukamori.archivetune.ui.player.modular

import moe.rukamori.archivetune.ui.player.modular.components.registerCoverArtComponent
import moe.rukamori.archivetune.ui.player.modular.components.registerPlaybackControlsComponents
import moe.rukamori.archivetune.ui.player.modular.components.registerSeekBarComponent
import moe.rukamori.archivetune.ui.player.modular.components.registerSecondaryControlsComponents
import moe.rukamori.archivetune.ui.player.modular.components.registerTitleArtistComponents
import moe.rukamori.archivetune.ui.player.modular.components.registerVolumeComponent

object ComponentInitializer {
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        registerCoverArtComponent()
        registerTitleArtistComponents()
        registerPlaybackControlsComponents()
        registerSeekBarComponent()
        registerSecondaryControlsComponents()
        registerVolumeComponent()
    }
}
