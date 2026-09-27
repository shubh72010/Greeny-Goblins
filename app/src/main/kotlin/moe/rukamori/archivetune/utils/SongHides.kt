/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlin.time.Duration

/**
 * Serialized as `songId:expiryEpochMillis,…`. Entries that already lapsed are
 * dropped on read, so a hide costs nothing once it expires.
 */
fun parseSongHides(raw: String, now: Long = System.currentTimeMillis()): Map<String, Long> =
    raw.split(",")
        .mapNotNull { entry ->
            val songId = entry.substringBefore(':')
            val until = entry.substringAfter(':').toLongOrNull() ?: return@mapNotNull null
            if (songId.isEmpty() || until <= now) null else songId to until
        }.toMap()

fun serializeSongHides(hides: Map<String, Long>): String =
    hides.entries.joinToString(",") { "${it.key}:${it.value}" }

/** Returns the new serialized value, hiding [songId] for [duration] or clearing it when null. */
fun setSongHide(
    raw: String,
    songId: String,
    duration: Duration?,
    now: Long = System.currentTimeMillis(),
): String =
    serializeSongHides(
        parseSongHides(raw, now).toMutableMap().apply {
            if (duration == null) remove(songId) else put(songId, now + duration.inWholeMilliseconds)
        },
    )
