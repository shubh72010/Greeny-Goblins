package moe.rukamori.archivetune.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class CapturedSession(val id: String = "")
data class YouTubeProfile(val id: String = "", val name: String = "", val thumbnailUrl: String? = null, val profileId: String? = null, val isBrandAccount: Boolean = false)
data class GoogleAccountSession(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val avatar: String? = null,
    val profileId: String? = null,
    val accountId: String? = null,
    val signedIn: Boolean = false,
    val isBrandAccount: Boolean = false,
    val isProfileLocked: Boolean = false,
    val profiles: List<YouTubeProfile> = emptyList(),
)
enum class WebSessionMode { IN_APP, EXTERNAL, SIGN_IN }

@Composable
fun YtMusicLoginScreen(
    mode: WebSessionMode = WebSessionMode.IN_APP,
    configureWebView: Any? = null,
    captureRequest: Int = 0,
    onPageReady: (Boolean) -> Unit = {},
    onCaptured: (Any?) -> Unit = {},
) {}
