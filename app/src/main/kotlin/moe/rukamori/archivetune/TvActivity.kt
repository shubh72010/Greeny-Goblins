package moe.rukamori.archivetune

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import moe.rukamori.archivetune.data.listentogether.JamInviteLink
import moe.rukamori.archivetune.data.model.*
import moe.rukamori.archivetune.playback.MusicLink
import moe.rukamori.archivetune.playback.PlayerDeepLink
import moe.rukamori.archivetune.playback.rememberMediaController
import moe.rukamori.archivetune.playback.rememberPlayerState
import moe.rukamori.archivetune.ui.MainViewModel
import moe.rukamori.archivetune.ui.theme.BitChordTheme
import moe.rukamori.archivetune.ui.tv.TvApp
import moe.rukamori.archivetune.ui.tv.display.TvRefreshRateController
import moe.rukamori.archivetune.ui.tv.theme.BitChordTvTheme

/**
 * Dedicated TV Activity for Android TV, Google TV, and Fire TV devices.
 * Launched via `android.intent.category.LEANBACK_LAUNCHER`.
 */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Attach TV Refresh Rate and Display Controller
        TvRefreshRateController.attach(this)

        // Make window full edge-to-edge for 1080p/4K television screens
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setContent {
            BitChordTvTheme {
                // The phone's Material theme too, so the components shared with
                // it (lyrics, mood sleeves) are set in SF Pro on its type scale.
                BitChordTheme(darkTheme = true) {
                    val mediaController = rememberMediaController()
                    val playerState = rememberPlayerState(mediaController)

                    TvApp(
                        viewModel = viewModel,
                        mediaController = mediaController,
                        playerState = playerState,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLinks(intent)
    }

    private fun consumeLinks(intent: Intent) {
        PlayerDeepLink.consume(intent)
        JamInviteLink.consume(intent)
        MusicLink.consume(intent)
    }

    override fun onDestroy() {
        // Closing the app (back out of Home, or the task being swiped away)
        // pauses the music rather than leaving it playing behind the launcher.
        if (isFinishing) {
            runCatching {
                val future = androidx.media3.session.MediaController.Builder(
                    applicationContext,
                    androidx.media3.session.SessionToken(
                        applicationContext,
                        android.content.ComponentName(
                            applicationContext,
                            moe.rukamori.archivetune.playback.PlaybackService::class.java,
                        ),
                    ),
                ).buildAsync()
                future.addListener({
                    runCatching { future.get().run { pause(); release() } }
                }, androidx.core.content.ContextCompat.getMainExecutor(applicationContext))
            }
        }
        super.onDestroy()
        TvRefreshRateController.detach()
    }
}
