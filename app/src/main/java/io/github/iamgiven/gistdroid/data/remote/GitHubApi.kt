package io.github.iamgiven.gistdroid.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String?) : Exception(message)
class NotLoggedInException : Exception("Belum terhubung ke GitHub")

class GitHubApi(private val tokenProvider: suspend () -> String?) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private class Result(val code: Int, val body: String)

    private suspend fun execute(
        method: String,
        url: String,
        body: String? = null,
        token: String? = null,
        requireAuth: Boolean = true,
    ): Result = withContext(Dispatchers.IO) {
        val authToken = token ?: tokenProvider()
        if (requireAuth && authToken == null) throw NotLoggedInException()
        val requestBody = when {
            body != null -> body.toRequestBody(JSON_TYPE)
            method == "PUT" || method == "POST" || method == "PATCH" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "GistDroid")
            .apply { if (authToken != null) header("Authorization", "Bearer $authToken") }
            .build()
        client.newCall(request).execute().use { response ->
            Result(response.code, response.body?.string().orEmpty())
        }
    }

    private fun Result.ensureOk(): Result {
        if (code !in 200..299) {
            val msg = runCatching { json.decodeFromString<ErrorBody>(body).message }.getOrNull()
            throw ApiException(code, msg)
        }
        return this
    }

    suspend fun getUser(token: String? = null): GitHubUser =
        json.decodeFromString(execute("GET", "$BASE/user", token = token).ensureOk().body)

    suspend fun listGists(page: Int, starred: Boolean): List<Gist> {
        val path = if (starred) "gists/starred" else "gists"
        return json.decodeFromString(
            execute("GET", "$BASE/$path?per_page=$PER_PAGE&page=$page").ensureOk().body
        )
    }

    /** Ambil gist lengkap. File yang terpotong (>1MB) diunduh dari raw_url. */
    suspend fun getGist(id: String, fetchTruncated: Boolean = true): Gist {
        val gist = json.decodeFromString<Gist>(execute("GET", "$BASE/gists/$id").ensureOk().body)
        if (!fetchTruncated || gist.files.values.none { it.truncated && it.rawUrl != null }) return gist
        val files = gist.files.mapValues { (_, file) ->
            if (file.truncated && file.rawUrl != null) {
                file.copy(content = execute("GET", file.rawUrl).ensureOk().body, truncated = false)
            } else file
        }
        return gist.copy(files = files)
    }

    suspend fun createGist(description: String, public: Boolean, files: List<Pair<String, String>>): Gist {
        val body = CreateGistRequest(
            description = description,
            public = public,
            files = files.associate { it.first to FileContent(it.second) },
        )
        return json.decodeFromString(
            execute("POST", "$BASE/gists", json.encodeToString(CreateGistRequest.serializer(), body))
                .ensureOk().body
        )
    }

    suspend fun updateGist(
        id: String,
        description: String,
        files: List<Pair<String, String>>,
        originalNames: Collection<String>,
    ): Gist {
        val newNames = files.map { it.first }.toSet()
        val map = LinkedHashMap<String, FileContent?>()
        originalNames.filter { it !in newNames }.forEach { map[it] = null }
        files.forEach { (name, content) -> map[name] = FileContent(content) }
        val body = UpdateGistRequest(description, map)
        return json.decodeFromString(
            execute("PATCH", "$BASE/gists/$id", json.encodeToString(UpdateGistRequest.serializer(), body))
                .ensureOk().body
        )
    }

    suspend fun deleteGist(id: String) {
        execute("DELETE", "$BASE/gists/$id").ensureOk()
    }

    suspend fun isStarred(id: String): Boolean = execute("GET", "$BASE/gists/$id/star").code == 204

    suspend fun setStarred(id: String, starred: Boolean) {
        execute(if (starred) "PUT" else "DELETE", "$BASE/gists/$id/star").ensureOk()
    }

    companion object {
        const val BASE = "https://api.github.com"
        const val PER_PAGE = 30
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

fun Throwable.friendlyMessage(): String = when (this) {
    is NotLoggedInException -> "Hubungkan akun GitHub dulu di Setelan"
    is ApiException -> when (code) {
        401 -> "Token tidak valid atau sudah kedaluwarsa"
        403 -> message ?: "Akses ditolak (mungkin kena rate limit)"
        404 -> "Gist tidak ditemukan"
        422 -> "Data tidak valid: ${message ?: "periksa nama file & isi"}"
        else -> message ?: "Error dari GitHub ($code)"
    }
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        "Tidak bisa terhubung ke GitHub. Periksa koneksi internet."
    else -> message ?: "Terjadi kesalahan"
}
