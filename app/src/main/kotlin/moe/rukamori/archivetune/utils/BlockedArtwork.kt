package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.constants.HiddenCoverArtSongIdsKey
import moe.rukamori.archivetune.utils.isSongInSet
import moe.rukamori.archivetune.utils.parseSongHides
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide set of song ids whose artwork (cover + canvas) is blocked.
 * Populated from preferences at startup and kept in sync by a collector in App.
 */
object BlockedArtwork {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids

    fun isBlocked(songId: String): Boolean = songId.isNotBlank() && songId in _ids.value

    fun update(raw: String?) {
        _ids.value = parseSongIdSet(raw)
    }
}