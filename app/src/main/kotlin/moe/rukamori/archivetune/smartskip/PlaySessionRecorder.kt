/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.smartskip

import moe.rukamori.archivetune.db.entities.PlaySession
import moe.rukamori.archivetune.db.entities.SeekRecord

/**
 * Buffers one media item's playback and turns it into a [PlaySession] plus its
 * forward seeks. Pure: no Android, no clock, no database — every input is a
 * timestamp passed in by the caller, so the accounting is unit tested instead of
 * trusted to listener ordering.
 *
 * Played time is accumulated from [setPlaying] spans on the monotonic clock, so a
 * screen-off pause or a buffering stall never counts as listening.
 */
class PlaySessionRecorder {
    private var songId: String? = null
    private var startedAt = 0L
    private var startPositionMs = 0L
    private var durationMs = 0L
    private var playedMs = 0L
    private var playingSinceElapsedMs: Long? = null
    private val seeks = mutableListOf<SeekRecord>()
    private var lastSeekElapsedMs = 0L

    data class Finished(
        val session: PlaySession,
        val seeks: List<SeekRecord>,
    )

    val activeSongId: String?
        get() = songId

    fun startItem(
        songId: String?,
        positionMs: Long,
        nowElapsedMs: Long,
        wallClockMs: Long,
    ) {
        this.songId = songId?.trim()?.takeIf { it.isNotEmpty() }
        startPositionMs = positionMs.coerceAtLeast(0L)
        startedAt = wallClockMs
        durationMs = 0L
        playedMs = 0L
        playingSinceElapsedMs = null
        seeks.clear()
        lastSeekElapsedMs = 0L
    }

    /** Called whenever the player reports a real duration for the current item. */
    fun setDuration(durationMs: Long) {
        if (durationMs > 0L) this.durationMs = durationMs
    }

    fun setPlaying(
        playing: Boolean,
        nowElapsedMs: Long,
    ) {
        if (playing) {
            if (playingSinceElapsedMs == null) playingSinceElapsedMs = nowElapsedMs
        } else {
            flush(nowElapsedMs)
        }
    }

    fun recordSeek(
        fromMs: Long,
        toMs: Long,
        nowElapsedMs: Long,
        wallClockMs: Long,
    ) {
        val id = songId ?: return
        if (toMs - fromMs < MIN_FORWARD_MS) return
        if (lastSeekElapsedMs != 0L && nowElapsedMs - lastSeekElapsedMs < DEDUPE_MS) return
        lastSeekElapsedMs = nowElapsedMs
        seeks +=
            SeekRecord(
                songId = id,
                fromMs = fromMs.coerceAtLeast(0L),
                toMs = toMs,
                at = wallClockMs,
            )
    }

    fun finishItem(
        endedNaturally: Boolean,
        nowElapsedMs: Long,
    ): Finished? {
        val id = songId ?: return null
        flush(nowElapsedMs)
        val finished =
            Finished(
                session =
                    PlaySession(
                        songId = id,
                        startedAt = startedAt,
                        startPositionMs = startPositionMs,
                        playedMs = playedMs,
                        durationMs = durationMs,
                        endedNaturally = endedNaturally,
                    ),
                seeks = seeks.toList(),
            )
        songId = null
        seeks.clear()
        playedMs = 0L
        return finished
    }

    private fun flush(nowElapsedMs: Long) {
        val since = playingSinceElapsedMs ?: return
        playedMs += (nowElapsedMs - since).coerceAtLeast(0L)
        playingSinceElapsedMs = null
    }

    companion object {
        /** Shorter than this is a scrub, not a skip. */
        const val MIN_FORWARD_MS = 3_000L

        /** Slider drags emit several discontinuities for one gesture. */
        const val DEDUPE_MS = 1_500L
    }
}
