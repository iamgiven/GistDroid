package io.github.iamgiven.gistdroid.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.iamgiven.gistdroid.AppContainer
import io.github.iamgiven.gistdroid.data.applyOrder
import io.github.iamgiven.gistdroid.data.local.Snippet
import io.github.iamgiven.gistdroid.ui.components.moved
import kotlinx.coroutines.flow.first
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
    /** Set after a successful save: local id or gist id. */
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
                        val s = c.snippetDao.get(id.toLong()) ?: error("Snippet not found")
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
                        val names = applyOrder(g.files.keys, c.fileOrder.order(id).first())
                        _state.update {
                            it.copy(
                                loading = false,
                                description = g.description.orEmpty(),
                                isPublic = g.public,
                                files = names.map { name -> newFile(name, g.files[name]?.content.orEmpty()) },
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
    private fun takeSnapshot() {
        snapshot = current()
        originals = _state.value.files.associate { it.key to (it.name to it.content) }
    }

    /** File contents as last loaded/saved, to mark changed files in the list. */
    private var originals: Map<Long, Pair<String, String>> = emptyMap()

    /** "New" for added files, "Edited" for changed ones, null otherwise. */
    fun changeLabel(file: EditableFile): String? {
        val original = originals[file.key] ?: return "New"
        return if (original != (file.name to file.content)) "Edited" else null
    }
    fun isDirty(): Boolean = !_state.value.loading && snapshot != null && snapshot != current()

    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setPublic(v: Boolean) = _state.update { it.copy(isPublic = v) }
    /** Adds an empty file and returns its key. */
    fun addFile(): Long {
        val file = newFile()
        _state.update { it.copy(files = it.files + file) }
        return file.key
    }
    /**
     * Adds files picked on the device. Blank, unnamed files (like the one a new gist starts with)
     * are replaced; names that clash with existing files get a "-2", "-3"… suffix.
     */
    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val picked = c.backup.readTextFiles(uris)
                _state.update { s ->
                    val kept = s.files.filterNot { it.name.isBlank() && it.content.isBlank() }
                    val used = kept.map { it.name.trim() }.toMutableSet()
                    val added = picked.map { f ->
                        var name = f.name
                        var n = 2
                        while (!used.add(name)) {
                            name = f.name.substringBeforeLast('.', f.name) + "-$n" +
                                (if ('.' in f.name) "." + f.name.substringAfterLast('.') else "")
                            n++
                        }
                        newFile(name, f.content)
                    }
                    s.copy(files = kept + added)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't read the files") }
            }
        }
    }
    fun moveFile(fromKey: Long, toKey: Long) = _state.update { s ->
        val from = s.files.firstOrNull { it.key == fromKey }
        val to = s.files.firstOrNull { it.key == toKey }
        if (from == null || to == null) s else s.copy(files = s.files.moved(from, to))
    }
    fun removeFile(key: Long) = _state.update { it.copy(files = it.files.filterNot { f -> f.key == key }) }
    fun updateFile(key: Long, name: String? = null, content: String? = null) = _state.update {
        it.copy(files = it.files.map { f ->
            if (f.key == key) f.copy(name = name ?: f.name, content = content ?: f.content) else f
        })
    }
    fun clearError() = _state.update { it.copy(error = null) }

    /** Validates & normalizes file names. Returns the files or an error message. */
    private fun prepareFiles(): Pair<List<Pair<String, String>>?, String?> {
        val files = _state.value.files
        if (files.isEmpty()) return null to "Add at least one file"
        val result = files.mapIndexed { i, f ->
            val name = f.name.trim().ifBlank { if (files.size == 1) "snippet.txt" else "file${i + 1}.txt" }
            name to f.content
        }
        if (result.map { it.first }.toSet().size != result.size) return null to "File names must be unique"
        if (result.any { it.first.contains('/') }) return null to "File names can't contain '/'"
        if (target == EditorTarget.GIST && result.any { it.second.isBlank() }) {
            return null to "Gist files can't be empty"
        }
        if (result.all { it.second.isBlank() }) return null to "Snippet is empty"
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
                        c.fileOrder.set(gist.id, files.map { it.first })
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
