package moe.rukamori.archivetune.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WatchEntry(val name: String = "")
sealed class WatchUpdateState {
    object Idle : WatchUpdateState()
    data class Downloading(val progress: Int = 0) : WatchUpdateState()
    object Ready : WatchUpdateState()
}
sealed class WatchPairingState {
    object Idle : WatchPairingState()
    object Pairing : WatchPairingState()
    object Paired : WatchPairingState()
}
data class WatchOutputState(val connected: Boolean = false)
data class UpdateInfo(val version: String = "", val url: String = "")
