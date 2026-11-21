package io.github.iamgiven.gistdroid.ui.local

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.iamgiven.gistdroid.AppContainer
import io.github.iamgiven.gistdroid.data.local.Snippet
import io.github.iamgiven.gistdroid.data.local.SnippetFile
import io.github.iamgiven.gistdroid.data.remote.ApiException
import io.github.iamgiven.gistdroid.data.remote.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocalListViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    init {
        viewModelScope.launch { c.verifyGistLinks() }
    }

    /** Creates a snippet from files picked on the device; reports the new id or an error message. */
    fun importFiles(uris: List<Uri>, onDone: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { c.backup.importFiles(uris) }
                .onSuccess(onDone)
                .onFailure { onError(it.message ?: "Couldn't import the files") }
        }
    }

    /** null = still loading. */
    val snippets: StateFlow<List<Snippet>?> = combine(c.snippetDao.observeAll(), query) { list, q ->
        if (q.isBlank()) list else list.filter { s ->
            s.description.contains(q, true) ||
                s.files.any { it.name.contains(q, true) || it.content.contains(q, true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

data class LocalDetailState(
    val busy: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false,
)

class LocalDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    /** null while loading or after deletion. */
    val snippet: StateFlow<Snippet?> = c.snippetDao.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _state = MutableStateFlow(LocalDetailState())
    val state: StateFlow<LocalDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            c.snippetDao.get(id)?.gistId?.let { c.verifyGistLinks(listOf(it)) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            _state.update { it.copy(deleted = true) }
            c.snippetDao.delete(id)
        }
    }

    fun upload() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val s = c.snippetDao.get(id) ?: return@launch
                val files = s.files.filter { it.content.isNotBlank() }.map { it.name to it.content }
                if (files.isEmpty()) {
                    _state.update { it.copy(message = "No non-empty files to upload") }
                    return@launch
                }
                val existingId = s.gistId
                val gist = if (existingId != null) {
                    try {
                        val remote = c.api.getGist(existingId, fetchTruncated = false)
                        c.api.updateGist(existingId, s.description, files, remote.files.keys)
                    } catch (e: ApiException) {
                        if (e.code == 404) c.api.createGist(s.description, s.isPublic, files) else throw e
                    }
                } else {
                    c.api.createGist(s.description, s.isPublic, files)
                }
                c.snippetDao.upsert(s.copy(gistId = gist.id))
                c.fileOrder.set(gist.id, s.files.map { it.name })
                c.notifyGistChanged()
                _state.update {
                    it.copy(message = if (existingId == gist.id) "Gist updated" else "Uploaded to Gist")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.friendlyMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Saves a new file order (also used for the linked gist on this device). */
    fun reorder(files: List<SnippetFile>) {
        viewModelScope.launch {
            val s = c.snippetDao.get(id) ?: return@launch
            c.snippetDao.upsert(s.copy(files = files))
            s.gistId?.let { gistId -> c.fileOrder.set(gistId, files.map { it.name }) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
