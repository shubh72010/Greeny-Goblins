/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.utils.BlockedArtwork
import java.util.concurrent.TimeUnit

internal suspend fun requestPlaybackWidgetUpdate(context: Context) {
    try {
        val token =
            SessionToken(
                context,
                ComponentName(context, MusicService::class.java),
            )
        val future = MediaController.Builder(context, token).buildAsync()
        val controller =
            withContext(Dispatchers.IO) {
                future.get(2, TimeUnit.SECONDS)
            }

        try {
            val mediaItem = controller.currentMediaItem
            val songId = mediaItem?.mediaId.orEmpty()
            if (songId.isNotBlank() && BlockedArtwork.isBlocked(songId)) {
                cleanBlockedArtworkFromWidgets(context, songId)
            }

            val serviceIntent = android.content.Intent(context, MusicService::class.java)
            context.bindService(
                serviceIntent,
                object : android.content.ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName?,
                        binder: android.os.IBinder?,
                    ) {
                        val service = (binder as? MusicService.MusicBinder)?.service
                        service?.updateWidget()
                        runCatching { context.unbindService(this) }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                },
                Context.BIND_AUTO_CREATE,
            )
        } finally {
            controller.release()
        }
    } catch (_: Exception) {
    }
}

internal suspend fun cleanBlockedArtworkFromWidgets(
    context: Context,
    songId: String,
) {
    if (!BlockedArtwork.isBlocked(songId)) return
    try {
        val manager = GlanceAppWidgetManager(context)
        val targets =
            listOf(
                MusicWidget::class.java to MusicWidget(),
                AlbumArtWidget::class.java to AlbumArtWidget(),
                NowPlayingCardWidget::class.java to NowPlayingCardWidget(),
                PlaybackCapsuleWidget::class.java to PlaybackCapsuleWidget(),
                PlaybackCommandWidget::class.java to PlaybackCommandWidget(),
                PlaybackDeckWidget::class.java to PlaybackDeckWidget(),
                PlaybackSpotlightWidget::class.java to PlaybackSpotlightWidget(),
                ListeningInsightsWidget::class.java to ListeningInsightsWidget(),
            )
        targets.forEach { (targetClass, targetWidget) ->
            val ids = manager.getGlanceIds(targetClass)
            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        remove(MusicWidgetKeys.ART_PATH)
                        remove(MusicWidgetKeys.DOMINANT_COLOR)
                        this[MusicWidgetKeys.SONG_ID] = songId
                    }
                }
                targetWidget.update(context, id)
            }
        }
    } catch (_: Exception) {
    }
}
