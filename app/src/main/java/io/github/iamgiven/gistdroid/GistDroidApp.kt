package io.github.iamgiven.gistdroid

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.github.iamgiven.gistdroid.data.BackupManager
import io.github.iamgiven.gistdroid.data.FileOrderStore
import io.github.iamgiven.gistdroid.data.PreviewRepository
import io.github.iamgiven.gistdroid.data.SettingsStore
import io.github.iamgiven.gistdroid.data.local.AppDatabase
import io.github.iamgiven.gistdroid.data.remote.GitHubApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class GistDroidApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.settings.migrateLegacyToken() }
    }
}

data class SharedText(val subject: String?, val text: String)

/** File opened in the full-screen code viewer. */
data class ViewerFile(
    val filename: String,
    val content: String,
    val language: String?,
    /** Last modification time (millis), if known. */
    val updatedAt: Long? = null,
    /** Gist revisions, newest first (empty for local snippets). */
    val revisions: List<Revision> = emptyList(),
    /** Link to the gist's revisions page on GitHub. */
    val revisionsUrl: String? = null,
    /** Where the "Edit" button leads; null when the file isn't editable. */
    val edit: EditSource? = null,
)

data class Revision(val version: String, val committedAt: Long, val additions: Int, val deletions: Int, val user: String?)

data class EditSource(val isGist: Boolean, val id: String)

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(context)
    val fileOrder = FileOrderStore(context)
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "gistdroid.db").build()
    val snippetDao = db.snippetDao()
    val api = GitHubApi { settings.token.first() }
    val previews = PreviewRepository(context, api)
    val backup = BackupManager(context, this)

    /** Incremented whenever a gist changes on the server so other screens reload. */
    val gistChanges = MutableStateFlow(0)
    fun notifyGistChanged() = gistChanges.update { it + 1 }

    private var linksVerified = false

    /**
     * Unlinks local snippets whose gist was deleted (in this app or on github.com).
     * Only a definite 404 unlinks; network errors leave links alone.
     */
    suspend fun verifyGistLinks(ids: List<String>? = null, force: Boolean = false) {
        if (settings.token.first() == null) return
        if (ids == null) {
            if (linksVerified && !force) return
            linksVerified = true
        }
        val toCheck = ids ?: snippetDao.linkedGistIds()
        coroutineScope {
            val limit = Semaphore(4)
            toCheck.map { id ->
                launch { limit.withPermit { if (api.gistExists(id) == false) snippetDao.unlinkGist(id) } }
            }
        }
    }

    val viewerFile = MutableStateFlow<ViewerFile?>(null)

    /** Text shared from another app, waiting to be opened in the editor. */
    val pendingShare = MutableStateFlow<SharedText?>(null)
}
