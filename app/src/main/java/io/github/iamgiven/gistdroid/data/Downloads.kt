package io.github.iamgiven.gistdroid.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import androidx.core.content.FileProvider
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Whether saving to Download needs the legacy storage permission (Android 9 and older). */
val needsLegacyStoragePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

/**
 * Saves [content] as [filename] in the public Download folder and returns the saved file name
 * (Android adds " (1)" etc. when a file with that name already exists).
 */
suspend fun saveToDownloads(context: Context, filename: String, content: String): String = withContext(Dispatchers.IO) {
    val name = filename.ifBlank { "snippet.txt" }.replace('/', '_')
    val ext = name.substringAfterLast('.', "").lowercase()
    // octet-stream keeps the name as-is; other types may get an extension appended.
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create the file")
        try {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: error("Couldn't write the file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else name
        } ?: name
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        var file = File(dir, name)
        var n = 1
        val base = name.substringBeforeLast('.', name)
        val suffix = if ('.' in name) "." + name.substringAfterLast('.') else ""
        while (file.exists()) file = File(dir, "$base (${n++})$suffix")
        file.writeText(content)
        file.name
    }
}

/**
 * Shares [content] as an actual file named [filename] (via a FileProvider URI to a cache copy),
 * so receiving apps get an attachment instead of plain text.
 */
suspend fun shareAsFile(context: Context, filename: String, content: String) {
    val name = filename.ifBlank { "snippet.txt" }.replace('/', '_')
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() } // keep only the latest shared file
        }
        File(dir, name).apply { writeText(content) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val ext = name.substringAfterLast('.', "").lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "text/plain"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, name)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share file"))
}
