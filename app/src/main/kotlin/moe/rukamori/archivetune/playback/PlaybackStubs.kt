package moe.rukamori.archivetune.playback

import androidx.media3.session.MediaController
import moe.rukamori.archivetune.data.model.Song

// shortcut: playback stubs for TV port
object EqLayout { val STANDARD = "standard" }
enum class EqualizerPreset { FLAT, BASS, TREBLE, TREBLE_BOOST, TREBLE_CUT, VOCAL, CLASSICAL, ELECTRONIC, HIP_HOP, JAZZ, LOUDNESS, ROCK, SPOKEN_WORD, SMALL_SPEAKERS, LATE_NIGHT, BASS_BOOST, BASS_CUT, ACOUSTIC }
data class PlaybackPosition(val positionMs: Long = 0, val durationMs: Long = 0)
data class PlayerState(val isPlaying: Boolean = false, val currentSong: Song? = null)
data class MusicLink(val url: String = "") {
    companion object { fun consume(intent: Any?) {} }
}

fun playSongs(songs: List<Song>, index: Int = 0) {}
fun beginRadioQueue(song: Song) {}
fun commitRadioQueue() {}
fun toggleAutoplay() {}
fun toggleShuffle() {}
fun toMediaItem(song: Song): Any? = null
fun rememberMediaController(): Any? = null
fun rememberPlayerState(controller: Any? = null): PlayerState = PlayerState()


fun MediaController.playSongs(songs: List<moe.rukamori.archivetune.data.model.Song>, index: Int = 0) {}

object QueueShuffle {
    val enabled = kotlinx.coroutines.flow.MutableStateFlow(false)
}
