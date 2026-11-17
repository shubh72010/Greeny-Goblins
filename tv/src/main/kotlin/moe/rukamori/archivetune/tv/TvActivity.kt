package moe.rukamori.archivetune.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.tv.theme.TvTheme
import javax.inject.Inject

@AndroidEntryPoint
class TvActivity : ComponentActivity() {

    @Inject
    lateinit var database: MusicDatabase

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicService.MusicBinder) {
                playerConnection = PlayerConnection(this@TvActivity, service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to JusPlayer's existing playback service
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            CompositionLocalProvider(
                LocalDatabase provides database,
                LocalPlayerConnection provides playerConnection
            ) {
                TvTheme {
                    TvApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(serviceConnection)
        } catch (_: Exception) {
        }
    }
}
