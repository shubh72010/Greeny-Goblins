package moe.rukamori.archivetune.data

object AppUpdateChecker {
    data class UpdateInfo(val version: String = "", val notes: String? = null)
    sealed class DownloadState {
        object IDLE : DownloadState()
        data class Downloading(val fraction: Float = 0f) : DownloadState()
        object DONE : DownloadState()
        object Ready : DownloadState()
        object Idle : DownloadState()
        data class Failed(val message: String = "") : DownloadState()
    }
    val downloadState = kotlinx.coroutines.flow.MutableStateFlow(DownloadState.IDLE)
    val download = kotlinx.coroutines.flow.MutableStateFlow(DownloadState.IDLE)
    val available = kotlinx.coroutines.flow.MutableStateFlow<UpdateInfo?>(null)
    fun isNewer(): Boolean = false
    fun apkAssetUrl(): String = ""
    fun downloadApk(context: Any? = null) {}
}
