package io.github.iamgiven.gistdroid

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.github.iamgiven.gistdroid.data.SettingsStore
import io.github.iamgiven.gistdroid.data.local.AppDatabase
import io.github.iamgiven.gistdroid.data.remote.GitHubApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

class GistDroidApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

data class SharedText(val subject: String?, val text: String)

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "gistdroid.db").build()
    val snippetDao = db.snippetDao()
    val api = GitHubApi { settings.token.first() }

    /** Bertambah setiap kali gist di server berubah, supaya layar lain memuat ulang. */
    val gistChanges = MutableStateFlow(0)
    fun notifyGistChanged() = gistChanges.update { it + 1 }

    /** Teks yang dibagikan dari aplikasi lain, menunggu dibuka di editor. */
    val pendingShare = MutableStateFlow<SharedText?>(null)
}
