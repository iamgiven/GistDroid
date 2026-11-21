package io.github.iamgiven.gistdroid

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.github.iamgiven.gistdroid.data.FileOrderStore
import io.github.iamgiven.gistdroid.data.SettingsStore
import io.github.iamgiven.gistdroid.data.local.AppDatabase
import io.github.iamgiven.gistdroid.data.remote.GitHubApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(context)
    val fileOrder = FileOrderStore(context)
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "gistdroid.db").build()
    val snippetDao = db.snippetDao()
    val api = GitHubApi { settings.token.first() }

    /** Incremented whenever a gist changes on the server so other screens reload. */
    val gistChanges = MutableStateFlow(0)
    fun notifyGistChanged() = gistChanges.update { it + 1 }

    /** Text shared from another app, waiting to be opened in the editor. */
    val pendingShare = MutableStateFlow<SharedText?>(null)
}
