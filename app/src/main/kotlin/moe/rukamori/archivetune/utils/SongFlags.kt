/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License
 */
package moe.rukamori.archivetune.utils

/** Permanent (no expiry) set of song ids serialized as a comma-separated string. */
fun parseSongIdSet(raw: String?): Set<String> =
    raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

fun serializeSongIdSet(ids: Set<String>): String = ids.joinToString(",")

/** Returns the new serialized value with [songId] toggled. */
fun toggleSongIdInSet(raw: String?, songId: String): String {
    val set = parseSongIdSet(raw).toMutableSet()
    if (!set.add(songId)) set.remove(songId)
    return serializeSongIdSet(set)
}

fun isSongInSet(raw: String?, songId: String): Boolean =
    songId in parseSongIdSet(raw)