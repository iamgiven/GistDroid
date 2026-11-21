package io.github.iamgiven.gistdroid.ui.gists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.iamgiven.gistdroid.AppContainer
import io.github.iamgiven.gistdroid.data.local.Snippet
import io.github.iamgiven.gistdroid.data.local.SnippetFile
import io.github.iamgiven.gistdroid.data.remote.Gist
import io.github.iamgiven.gistdroid.data.remote.GitHubApi
import io.github.iamgiven.gistdroid.data.remote.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GistFilter { MINE, STARRED }

data class GistListState(
    val loggedIn: Boolean? = null,
    val gists: List<Gist> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val filter: GistFilter = GistFilter.MINE,
    val query: String = "",
) {
    val visibleGists: List<Gist>
        get() = if (query.isBlank()) gists else gists.filter { g ->
            g.description.orEmpty().contains(query, true) || g.files.keys.any { it.contains(query, true) }
        }
}

class GistListViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(GistListState())
    val state: StateFlow<GistListState> = _state.asStateFlow()
    private var page = 1
    private var job: Job? = null

    init {
        viewModelScope.launch {
            combine(c.settings.token, c.gistChanges) { token, _ -> token }.collect { token ->
                _state.update { it.copy(loggedIn = token != null) }
                if (token != null) load(reset = true)
                else {
                    job?.cancel()
                    _state.update { it.copy(gists = emptyList(), loading = false, refreshing = false) }
                }
            }
        }
    }

    fun refresh() = load(reset = true, pullToRefresh = true)
    fun retry() = load(reset = true)

    fun setFilter(filter: GistFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter, gists = emptyList(), endReached = false) }
        load(reset = true)
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.refreshing || s.endReached || s.error != null) return
        load(reset = false)
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun load(reset: Boolean, pullToRefresh: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            val target = if (reset) 1 else page + 1
            _state.update {
                it.copy(
                    loading = reset && !pullToRefresh && it.gists.isEmpty(),
                    refreshing = pullToRefresh,
                    loadingMore = !reset,
                    error = null,
                )
            }
            try {
                val items = c.api.listGists(target, _state.value.filter == GistFilter.STARRED)
                page = target
                _state.update {
                    it.copy(
                        gists = if (reset) items else (it.gists + items).distinctBy { g -> g.id },
                        endReached = items.size < GitHubApi.PER_PAGE,
                        loading = false, refreshing = false, loadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.friendlyMessage(), loading = false, refreshing = false, loadingMore = false)
                }
            }
        }
    }
}

data class GistDetailState(
    val gist: Gist? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val starred: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false,
)

class GistDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(GistDetailState())
    val state: StateFlow<GistDetailState> = _state.asStateFlow()

    val localId: StateFlow<Long?> = c.snippetDao.observeIdByGistId(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        load()
        viewModelScope.launch { c.gistChanges.drop(1).collect { if (!_state.value.deleted) load() } }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val gist = c.api.getGist(id)
                val starred = runCatching { c.api.isStarred(id) }.getOrDefault(false)
                _state.update { it.copy(gist = gist, starred = starred, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun toggleStar() = runBusy {
        val target = !_state.value.starred
        c.api.setStarred(id, target)
        _state.update { it.copy(starred = target, message = if (target) "Diberi bintang" else "Bintang dihapus") }
    }

    fun delete() = runBusy {
        c.api.deleteGist(id)
        _state.update { it.copy(deleted = true) }
        c.notifyGistChanged()
    }

    fun saveLocal() = runBusy {
        val gist = _state.value.gist ?: return@runBusy
        val existing = c.snippetDao.findByGistId(id)
        val now = System.currentTimeMillis()
        c.snippetDao.upsert(
            Snippet(
                id = existing?.id ?: 0,
                description = gist.description.orEmpty(),
                files = gist.files.map { (name, f) -> SnippetFile(name, f.content.orEmpty()) },
                isPublic = gist.public,
                gistId = gist.id,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
        _state.update {
            it.copy(message = if (existing != null) "Salinan lokal diperbarui" else "Disimpan ke penyimpanan lokal")
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.friendlyMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
