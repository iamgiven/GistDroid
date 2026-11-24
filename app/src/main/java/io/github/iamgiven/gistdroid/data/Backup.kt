package io.github.iamgiven.gistdroid.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.iamgiven.gistdroid.AppContainer
import io.github.iamgiven.gistdroid.data.local.Snippet
import io.github.iamgiven.gistdroid.data.local.SnippetFile
import io.github.iamgiven.gistdroid.data.remote.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class BackupFile(val name: String, val content: String)

@Serializable
data class BackupSnippet(
    val description: String = "",
    val isPublic: Boolean = false,
    val gistId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val files: List<BackupFile> = emptyList(),
)

@Serializable
data class BackupGist(
    val id: String,
    val description: String = "",
    val public: Boolean = false,
    val updatedAt: String = "",
    val files: List<BackupFile> = emptyList(),
)

/** GistDroid backup file (JSON). */
@Serializable
data class Backup(
    val app: String = "GistDroid",
    val version: Int = 1,
    val exportedAt: String = "",
    val snippets: List<BackupSnippet> = emptyList(),
    val gists: List<BackupGist> = emptyList(),
)

data class ImportResult(val imported: Int, val skipped: Int)

class ImportException(message: String) : Exception(message)

/** Export/import of local snippets and gists, and importing code files from the device. */
class BackupManager(private val context: Context, private val c: AppContainer) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Writes a backup to [uri]. Gists are downloaded in full (all pages, all files) when
     * [includeGists] is set; [onProgress] reports (done, total) while doing so.
     */
    suspend fun export(
        uri: Uri,
        includeLocal: Boolean,
        includeGists: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Backup {
        val snippets = if (includeLocal) {
            c.snippetDao.getAll().map { s ->
                BackupSnippet(
                    description = s.description,
                    isPublic = s.isPublic,
                    gistId = s.gistId,
                    createdAt = s.createdAt,
                    updatedAt = s.updatedAt,
                    files = s.files.map { BackupFile(it.name, it.content) },
                )
            }
        } else emptyList()

        val gists = if (includeGists) {
            val list = buildList {
                var page = 1
                while (true) {
                    val items = c.api.listGists(page, starred = false)
                    addAll(items)
                    if (items.size < GitHubApi.PER_PAGE) break
                    page++
                }
            }
            val orders = c.fileOrder.orders.first()
            list.mapIndexed { i, summary ->
                onProgress(i, list.size)
                val g = c.api.getGist(summary.id)
                BackupGist(
                    id = g.id,
                    description = g.description.orEmpty(),
                    public = g.public,
                    updatedAt = g.updatedAt,
                    files = applyOrder(g.files.keys, orders[g.id]).map { name ->
                        BackupFile(name, g.files[name]?.content.orEmpty())
                    },
                )
            }.also { onProgress(list.size, list.size) }
        } else emptyList()

        val backup = Backup(exportedAt = Instant.now().toString(), snippets = snippets, gists = gists)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(json.encodeToString(Backup.serializer(), backup).toByteArray())
            } ?: throw ImportException("Couldn't write the backup file")
        }
        return backup
    }

    suspend fun read(uri: Uri): Backup = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw ImportException("Couldn't open the file")
        val backup = runCatching { json.decodeFromString(Backup.serializer(), text) }
            .getOrElse { throw ImportException("This isn't a GistDroid backup file") }
        if (backup.app != "GistDroid") throw ImportException("This isn't a GistDroid backup file")
        backup
    }

    /**
     * Adds the backup's snippets and gists as local snippets. Gists keep their link (so they show as
     * "On Gist"); anything identical to an existing snippet, or a gist that's already saved locally, is skipped.
     */
    suspend fun import(backup: Backup): ImportResult {
        val existing = c.snippetDao.getAll()
        val fingerprints = existing.map { fingerprint(it.description, it.files.map { f -> f.name to f.content }) }.toMutableSet()
        val linked = existing.mapNotNull { it.gistId }.toMutableSet()
        var imported = 0
        var skipped = 0
        val now = System.currentTimeMillis()

        suspend fun add(snippet: Snippet) {
            val fp = fingerprint(snippet.description, snippet.files.map { it.name to it.content })
            val gistId = snippet.gistId
            if (fp in fingerprints || (gistId != null && gistId in linked) || snippet.files.isEmpty()) {
                skipped++
                return
            }
            c.snippetDao.upsert(snippet)
            fingerprints += fp
            gistId?.let { linked += it }
            imported++
        }

        backup.snippets.forEach { s ->
            add(
                Snippet(
                    description = s.description,
                    files = s.files.map { SnippetFile(it.name, it.content) },
                    isPublic = s.isPublic,
                    gistId = s.gistId,
                    createdAt = s.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = s.updatedAt.takeIf { it > 0 } ?: now,
                )
            )
        }
        backup.gists.forEach { g ->
            val updated = runCatching { Instant.parse(g.updatedAt).toEpochMilli() }.getOrDefault(now)
            add(
                Snippet(
                    description = g.description,
                    files = g.files.map { SnippetFile(it.name, it.content) },
                    isPublic = g.public,
                    gistId = g.id,
                    createdAt = updated,
                    updatedAt = updated,
                )
            )
        }
        // Drop links to gists that no longer exist (or belong to another account).
        if (imported > 0) runCatching { c.verifyGistLinks(force = true) }
        return ImportResult(imported, skipped)
    }

    /** Reads text files picked on the device; throws [ImportException] for files that aren't text. */
    suspend fun readTextFiles(uris: List<Uri>): List<SnippetFile> = withContext(Dispatchers.IO) {
        uris.map { uri ->
            val name = displayName(uri) ?: "file.txt"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ImportException("Couldn't open $name")
            val text = bytes.decodeToString()
            if ('\u0000' in text || '�' in text) throw ImportException("$name doesn't look like a text file")
            SnippetFile(name, text)
        }
    }

    /** Creates one local snippet from code files picked on the device; returns its id. */
    suspend fun importFiles(uris: List<Uri>): Long {
        val files = readTextFiles(uris)
        // Make names unique within the snippet.
        val used = HashSet<String>()
        val unique = files.map { f ->
            var name = f.name
            var n = 2
            while (!used.add(name)) {
                name = f.name.substringBeforeLast('.', f.name) + "-$n" +
                    (if ('.' in f.name) "." + f.name.substringAfterLast('.') else "")
                n++
            }
            f.copy(name = name)
        }
        val now = System.currentTimeMillis()
        return c.snippetDao.upsert(
            Snippet(description = "", files = unique, isPublic = false, createdAt = now, updatedAt = now)
        )
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun fingerprint(description: String, files: List<Pair<String, String>>) =
        (description.trim() + "\u0001" + files.joinToString("\u0002") { it.first + "\u0003" + it.second }).hashCode()
}
