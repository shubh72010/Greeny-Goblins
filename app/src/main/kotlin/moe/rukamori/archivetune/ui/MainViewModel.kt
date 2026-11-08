package moe.rukamori.archivetune.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import moe.rukamori.archivetune.data.model.*
import moe.rukamori.archivetune.auth.GoogleAccountSession

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    val home = MutableStateFlow<List<HomeShelf>>(emptyList())
    val homeLoadingMore = MutableStateFlow(false)
    val library = MutableStateFlow<List<Song>>(emptyList())
    val search = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchFilter = MutableStateFlow(SearchFilter.ALL)
    val detailPage = MutableStateFlow<DetailPage?>(null)
    val detailStack = MutableStateFlow<List<DetailPage>>(emptyList())
    val signedIn = MutableStateFlow(false)
    val googleAccounts = MutableStateFlow<List<GoogleAccountSession>>(emptyList())
    val activeAccountId = MutableStateFlow<String?>(null)
    val activeProfileId = MutableStateFlow<String?>(null)
    val profiles = MutableStateFlow<List<GoogleAccountSession>>(emptyList())
    val account = MutableStateFlow<Any?>(null)

    fun loadMoreHome() {}
    fun refresh(feed: Any? = null) {}
    fun clearDetail() { detailPage.value = null }
    fun closeDetail() { detailPage.value = null }
    fun playSongs(songs: List<Song>, index: Int = 0) {}
    fun beginRadioQueue(song: Song) {}
    fun searchFor(q: String) { search.value = q }
    fun loadHistory() {}
    fun submitSearch() {}
    fun toggleLike(videoId: String?) {}
    fun openMoodGenre(item: Any?) {}
    fun onWebSession(session: Any?, mode: Any?, cb: (Boolean) -> Unit) { cb(true) }

    object Feed {
        val HOME = Any()
        val SEARCH = Any()
        val LIBRARY = Any()
    }
}
