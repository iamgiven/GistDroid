package io.github.iamgiven.gistdroid.data

import android.content.Context
import android.util.LruCache
import io.github.iamgiven.gistdroid.data.remote.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Card previews (first lines of a gist file).
 *
 * Raw gist URLs contain the revision SHA, so a URL's content never changes: previews are cached
 * in memory and on disk indefinitely, and a changed gist simply gets a new URL.
 * Network fetches are limited to a few at a time and only download the first ~1KB.
 */
class PreviewRepository(context: Context, private val api: GitHubApi) {
    private val memory = LruCache<String, String>(300)
    private val dir = File(context.cacheDir, "previews")
    private val network = Semaphore(4)

    /** Synchronous memory lookup so already-seen previews render without a loading state. */
    fun peek(rawUrl: String): String? = memory.get(rawUrl)

    suspend fun get(rawUrl: String): String? {
        memory.get(rawUrl)?.let { return it }
        val file = File(dir, sha1(rawUrl))
        withContext(Dispatchers.IO) { file.takeIf { it.exists() }?.readText() }?.let {
            memory.put(rawUrl, it)
            return it
        }
        val text = network.withPermit {
            runCatching { api.fetchPreview(rawUrl) }.getOrNull()
        } ?: return null
        val preview = firstLines(text)
        memory.put(rawUrl, preview)
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                file.writeText(preview)
            }
        }
        return preview
    }

    private fun firstLines(text: String): String =
        text.trimStart('\n', '\r').lineSequence().take(PREVIEW_LINES).joinToString("\n").trimEnd()

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val PREVIEW_LINES = 3
    }
}
