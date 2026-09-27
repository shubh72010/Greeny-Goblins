/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audio.analysis

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fires checkpoint callbacks as playback crosses 20/50/80% of the current track.
 * Riding alongside playback only: polls currentPosition, never seeks/pauses/buffers.
 *
 * Lifecycle: [attach] on service create, [release] on service destroy.
 * State resets on track change and re-arms on seek-back.
 */
class CheckpointSampler(
    private val scope: CoroutineScope,
    private val player: Player,
    private val onCheckpoint: (mediaId: String, fraction: Double, positionMs: Long, durationMs: Long) -> Unit,
    /** Fired when a track ends (advance or natural end) with windows possibly unfinalized. */
    private val onTrackEnd: (oldMediaId: String?) -> Unit = {},
) : Player.Listener {
    private var pollJob: Job? = null
    private var mediaId: String? = null
    private var lastPosMs: Long = 0L
    private val fired = mutableSetOf<Double>()
    var extraFractions: List<Double> = emptyList()
        private set

    fun attach() {
        player.addListener(this)
        startPoll()
    }

    fun release() {
        pollJob?.cancel()
        pollJob = null
        runCatching { player.removeListener(this) }
    }

    /** Queue one adaptive extra sample at the largest evidence gap. Fires when reached. */
    fun requestExtraSample(): Double? {
        val next = CheckpointMath.largestGapMidpoint(fired.toSet())
        if (extraFractions.size >= CheckpointMath.MAX_EXTRA_SAMPLES) return null
        extraFractions = extraFractions + next
        return next
    }

    fun markExtraFired(fraction: Double) {
        fired.add(fraction)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val old = mediaId
        resetFor(mediaItem?.mediaId)
        if (old != null && old != mediaItem?.mediaId) runCatching { onTrackEnd(old) }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            lastPosMs = newPosition.positionMs
            rearmSeekedBack(newPosition.positionMs)
        }
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            val old = mediaId
            resetFor(player.currentMediaItem?.mediaId)
            if (old != null) runCatching { onTrackEnd(old) }
        }
    }

    private fun allFractions(): List<Double> = CheckpointMath.BASE_FRACTIONS + extraFractions

    private fun resetFor(newMediaId: String?) {
        mediaId = newMediaId
        fired.clear()
        extraFractions = emptyList()
        lastPosMs = 0L
    }

    private fun rearmSeekedBack(posMs: Long) {
        val durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        fired.removeAll { f -> posMs < (durationMs * f).toLong() }
    }

    private fun startPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                pollOnce()
                delay(CheckpointMath.POLL_MS)
            }
        }
    }

    private fun pollOnce() {
        val current = player.currentMediaItem
        if (current?.mediaId != mediaId) resetFor(current?.mediaId)
        val id = mediaId ?: return
        if (!player.playWhenReady || player.playbackState != Player.STATE_READY) return
        val durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        if (!CheckpointMath.isEligible(durationMs)) return
        val posMs = player.currentPosition.coerceAtLeast(0L)
        val crossed = CheckpointMath.crossedFractions(lastPosMs, posMs, durationMs, allFractions(), fired)
        lastPosMs = posMs
        for (f in crossed) {
            fired.add(f)
            runCatching { onCheckpoint(id, f, posMs, durationMs) }
        }
    }
}
