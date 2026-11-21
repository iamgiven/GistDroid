package io.github.iamgiven.gistdroid.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.iamgiven.gistdroid.AppContainer
import io.github.iamgiven.gistdroid.data.local.Snippet
import io.github.iamgiven.gistdroid.data.local.SnippetFile
import io.github.iamgiven.gistdroid.data.remote.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditorTarget { LOCAL, GIST }

data class EditableFile(val key: Long, val name: String, val content: String)

data class EditorState(
    val loading: Boolean = false,
    val description: String = "",
    val isPublic: Boolean = false,
    val files: List<EditableFile> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    /** Diisi setelah berhasil simpan: id lokal / id gist. */
    val savedId: String? = null,
    val loadFailed: Boolean = false,
)

class EditorViewModel(
    private val c: AppContainer,
    val target: EditorTarget,
    private val id: String?,
) : ViewModel() {
    val isNew = id == null
    private var nextKey = 0L
    private var originalNames: Set<String> = emptySet()
    private var original: Snippet? = null
    private var snapshot: Triple<String, Boolean, List<Pair<String, String>>>? = null

    private val _state = MutableStateFlow(EditorState(loading = id != null))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        if (id == null) {
            val shared = c.pendingShare.getAndUpdate { null }
            _state.update {
                it.copy(
                    description = shared?.subject.orEmpty(),
                    files = listOf(newFile(content = shared?.text.orEmpty())),
                )
            }
            takeSnapshot()
        } else load(id)
    }

    private fun newFile(name: String = "", content: String = "") = EditableFile(nextKey++, name, content)

    private fun load(id: String) {
        viewModelScope.launch {
            try {
                when (target) {
                    EditorTarget.LOCAL -> {
                        val s = c.snippetDao.get(id.toLong()) ?: error("Snippet tidak ditemukan")
                        original = s
                        _state.update {
                            it.copy(
                                loading = false,
                                description = s.description,
                                isPublic = s.isPublic,
                                files = s.files.map { f -> newFile(f.name, f.content) }.ifEmpty { listOf(newFile()) },
                            )
                        }
                    }
                    EditorTarget.GIST -> {
                        val g = c.api.getGist(id)
                        originalNames = g.files.keys
                        _state.update {
                            it.copy(
                                loading = false,
                                description = g.description.orEmpty(),
                                isPublic = g.public,
                                files = g.files.map { (name, f) -> newFile(name, f.content.orEmpty()) },
                            )
                        }
                    }
                }
                takeSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true, error = e.friendlyMessage()) }
            }
        }
    }

    private fun current() = _state.value.let { s -> Triple(s.description, s.isPublic, s.files.map { it.name to it.content }) }
    private fun takeSnapshot() { snapshot = current() }
    fun isDirty(): Boolean = !_state.value.loading && snapshot != null && snapshot != current()

    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setPublic(v: Boolean) = _state.update { it.copy(isPublic = v) }
    fun addFile() = _state.update { it.copy(files = it.files + newFile()) }
    fun removeFile(key: Long) = _state.update { it.copy(files = it.files.filterNot { f -> f.key == key }) }
    fun updateFile(key: Long, name: String? = null, content: String? = null) = _state.update {
        it.copy(files = it.files.map { f ->
            if (f.key == key) f.copy(name = name ?: f.name, content = content ?: f.content) else f
        })
    }
    fun clearError() = _state.update { it.copy(error = null) }

    /** Validasi & normalisasi nama file. Mengembalikan pesan error atau null. */
    private fun prepareFiles(): Pair<List<Pair<String, String>>?, String?> {
        val files = _state.value.files
        if (files.isEmpty()) return null to "Tambahkan minimal satu file"
        val result = files.mapIndexed { i, f ->
            val name = f.name.trim().ifBlank { if (files.size == 1) "snippet.txt" else "file${i + 1}.txt" }
            name to f.content
        }
        if (result.map { it.first }.toSet().size != result.size) return null to "Nama file tidak boleh sama"
        if (result.any { it.first.contains('/') }) return null to "Nama file tidak boleh mengandung '/'"
        if (target == EditorTarget.GIST && result.any { it.second.isBlank() }) {
            return null to "Isi file tidak boleh kosong untuk Gist"
        }
        if (result.all { it.second.isBlank() }) return null to "Snippet masih kosong"
        return result to null
    }

    fun save() {
        if (_state.value.saving) return
        val (files, error) = prepareFiles()
        if (files == null) {
            _state.update { it.copy(error = error) }
            return
        }
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val savedId = when (target) {
                    EditorTarget.LOCAL -> {
                        val now = System.currentTimeMillis()
                        val base = original
                        val snippet = Snippet(
                            id = base?.id ?: 0,
                            description = s.description.trim(),
                            files = files.map { SnippetFile(it.first, it.second) },
                            isPublic = s.isPublic,
                            gistId = base?.gistId,
                            createdAt = base?.createdAt ?: now,
                            updatedAt = now,
                        )
                        c.snippetDao.upsert(snippet).toString()
                    }
                    EditorTarget.GIST -> {
                        val gist = if (id == null) {
                            c.api.createGist(s.description.trim(), s.isPublic, files)
                        } else {
                            c.api.updateGist(id, s.description.trim(), files, originalNames)
                        }
                        c.notifyGistChanged()
                        gist.id
                    }
                }
                takeSnapshot()
                _state.update { it.copy(saving = false, savedId = savedId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.friendlyMessage()) }
            }
        }
    }
}
