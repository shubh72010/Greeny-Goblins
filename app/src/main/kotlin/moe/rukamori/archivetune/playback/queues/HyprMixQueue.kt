/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.queues

import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.withHyprMix

const val HYPR_MIX_QUEUE_TITLE = "HyprMix"

/**
 * HyprMix queue: a plain [ListQueue] whose items carry [moe.rukamori.archivetune.extensions.ExtraHyprMix].
 * The extra is the feature's entire scope — no global playback flag to leak.
 */
fun hyprMixQueue(
    songs: List<Song>,
    startIndex: Int = 0,
) = ListQueue(
    title = HYPR_MIX_QUEUE_TITLE,
    items = songs.map { it.toMediaItem().withHyprMix() },
    startIndex = startIndex,
)
