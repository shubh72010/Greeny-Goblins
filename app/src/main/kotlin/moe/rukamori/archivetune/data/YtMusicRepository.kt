package moe.rukamori.archivetune.data

import moe.rukamori.archivetune.data.model.SearchFilter
import moe.rukamori.archivetune.data.model.SearchResult

object YtMusicRepository {
    suspend fun search(q: String, filter: SearchFilter): Result<List<SearchResult>> = Result.success(emptyList())
    suspend fun radio(videoId: String?): Result<List<moe.rukamori.archivetune.data.model.Song>> = Result.success(emptyList())
}
