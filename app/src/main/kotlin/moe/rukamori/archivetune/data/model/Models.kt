package moe.rukamori.archivetune.data.model

// Minimal BitChord-compatible data models for TV port.
// shortcut: shim data classes; fields added as needed by TV code.

data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val videoId: String? = null,
    val browseId: String? = null,
    val durationText: String? = null,
    val subtitle: String? = null,
    val album: String? = null,
    val albumName: String? = null,
    val fromAutoplay: Boolean = false,
    val isExplicit: Boolean = false,
    val localPath: String? = null,
    val localUri: String? = null,
)

enum class BrowseType { ALBUM, ARTIST, PLAYLIST, OTHER }

data class BrowseItem(
    val title: String = "",
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val browseId: String? = null,
    val type: BrowseType = BrowseType.OTHER,
)

data class ShelfItem(
    val title: String = "",
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val videoId: String? = null,
    val browseId: String? = null,
    val isVideo: Boolean = false,
    val song: Song? = null,
)

data class HomeShelf(
    val title: String = "",
    val items: List<ShelfItem> = emptyList(),
    val browseId: String? = null,
)

data class DetailPage(
    val title: String = "",
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val browseId: String? = null,
    val items: List<ShelfItem> = emptyList(),
)

data class MoodGenre(
    val title: String = "",
    val color: Int = 0,
    val thumbnailUrl: String? = null,
    val browseId: String? = null,
)

data class MoodGenreSection(
    val title: String = "",
    val items: List<MoodGenre> = emptyList(),
)

enum class EntityType { TRACK, ALBUM, ARTIST, PLAYLIST, OTHER }

enum class LikeStatus { LIKE, DISLIKE, INDIFFERENT }

enum class PlaylistPrivacy { PUBLIC, PRIVATE, UNLISTED }

data class LikeState(val status: LikeStatus = LikeStatus.INDIFFERENT) {
    companion object {
        val overrides = kotlinx.coroutines.flow.MutableStateFlow<Map<String, LikeStatus>>(emptyMap())
    }
}

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    data object Empty : UiState<Nothing>()
}

data class SearchHistoryEntity(val query: String = "", val timestamp: Long = 0)

data class NerdStats(
    val codec: String = "",
    val bitrate: String = "",
    val sampleRate: String = "",
    val bitDepth: String = "",
    val isDolbyAtmos: Boolean = false,
    val isHiRes: Boolean = false,
) {
    companion object {
        val current = kotlinx.coroutines.flow.MutableStateFlow<NerdStats?>(null)
    }
    data class Snapshot(
        val sampleRateHz: Int = 0,
        val channels: Int = 0,
        val bitDepth: Int = 0,
    )
}

// Extension helpers
fun Song.artworkAt(px: Int): String? = thumbnailUrl
fun String?.artworkAt(px: Int): String? = this


enum class SearchFilter(val label: String, val params: String?) {
    ALL("All", null),
    SONGS("Songs", null),
    VIDEOS("Videos", null),
    ALBUMS("Albums", null),
    ARTISTS("Artists", null),
    PLAYLISTS("Playlists", null),
}

sealed interface SearchResult {
    data class TopTrack(val song: Song) : SearchResult
    data class Track(val song: Song) : SearchResult
    data class Browse(val item: BrowseItem) : SearchResult
}

fun moe.rukamori.archivetune.data.model.Song.toMediaItem(): Any? = null
