package io.github.iamgiven.gistdroid.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class Gist(
    val id: String,
    val description: String? = null,
    val public: Boolean = true,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val files: Map<String, GistFile> = emptyMap(),
    val owner: GitHubUser? = null,
) {
    val title: String
        get() = description?.takeIf { it.isNotBlank() } ?: files.keys.firstOrNull() ?: id
}

@Serializable
data class GistFile(
    val filename: String? = null,
    val language: String? = null,
    val size: Long = 0,
    @SerialName("raw_url") val rawUrl: String? = null,
    val truncated: Boolean = false,
    val content: String? = null,
)

@Serializable
data class FileContent(val content: String)

@Serializable
data class CreateGistRequest(
    val description: String,
    val public: Boolean,
    val files: Map<String, FileContent>,
)

@Serializable
data class UpdateGistRequest(
    val description: String,
    val files: Map<String, FileContent?>,
)

@Serializable
internal data class ErrorBody(val message: String? = null)
