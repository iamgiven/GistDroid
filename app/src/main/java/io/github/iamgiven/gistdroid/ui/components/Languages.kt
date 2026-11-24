package io.github.iamgiven.gistdroid.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import io.github.iamgiven.gistdroid.R

/**
 * The languages of a gist or snippet: the one that makes up most of it, plus the rest.
 * Names follow GitHub Linguist, as the Gist API reports them ("Python", "Shell", "C++").
 */
data class LanguageSummary(val primary: String?, val others: List<String>) {
    /** "Python", "Python +1", or null when no file has a known language. */
    val label: String?
        get() = primary?.let { if (others.isEmpty()) it else "$it +${others.size}" }
}

object Languages {
    // Logos from Simple Icons (CC0 1.0), see THIRD_PARTY_NOTICES.md.
    private val logos: Map<String, Int> = mapOf(
        "Python" to R.drawable.ic_lang_python,
        "Shell" to R.drawable.ic_lang_gnubash,
        "Kotlin" to R.drawable.ic_lang_kotlin,
        "JavaScript" to R.drawable.ic_lang_javascript,
        "TypeScript" to R.drawable.ic_lang_typescript,
        "TSX" to R.drawable.ic_lang_typescript,
        "Go" to R.drawable.ic_lang_go,
        "Rust" to R.drawable.ic_lang_rust,
        "Ruby" to R.drawable.ic_lang_ruby,
        "PHP" to R.drawable.ic_lang_php,
        "C" to R.drawable.ic_lang_c,
        "C++" to R.drawable.ic_lang_cplusplus,
        "Swift" to R.drawable.ic_lang_swift,
        "Dart" to R.drawable.ic_lang_dart,
        "HTML" to R.drawable.ic_lang_html5,
        "CSS" to R.drawable.ic_lang_css,
        "Markdown" to R.drawable.ic_lang_markdown,
        "JSON" to R.drawable.ic_lang_json,
        "JSON with Comments" to R.drawable.ic_lang_json,
        "YAML" to R.drawable.ic_lang_yaml,
        "Dockerfile" to R.drawable.ic_lang_docker,
        "Lua" to R.drawable.ic_lang_lua,
        "Java" to R.drawable.ic_lang_openjdk,
        "TOML" to R.drawable.ic_lang_toml,
        "XML" to R.drawable.ic_lang_xml,
    )

    private val byExtension: Map<String, String> = buildMap {
        fun lang(language: String, vararg exts: String) = exts.forEach { this[it] = language }
        lang("Python", "py", "pyw", "pyi")
        lang("Shell", "sh", "bash", "zsh", "fish", "ksh")
        lang("Kotlin", "kt", "kts")
        lang("JavaScript", "js", "mjs", "cjs", "jsx")
        lang("TypeScript", "ts", "mts", "cts")
        lang("TSX", "tsx")
        lang("Go", "go")
        lang("Rust", "rs")
        lang("Ruby", "rb")
        lang("PHP", "php")
        lang("C", "c", "h")
        lang("C++", "cpp", "cc", "cxx", "hpp", "hh", "hxx")
        lang("Swift", "swift")
        lang("Dart", "dart")
        lang("HTML", "html", "htm")
        lang("CSS", "css")
        lang("Markdown", "md", "markdown")
        lang("JSON", "json")
        lang("YAML", "yml", "yaml")
        lang("Lua", "lua")
        lang("Java", "java")
        lang("TOML", "toml")
        lang("XML", "xml")
        lang("PowerShell", "ps1", "psm1")
        lang("Batchfile", "bat", "cmd")
        lang("SQL", "sql")
        lang("Text", "txt")
    }

    /** Languages that only lead when a gist has nothing else, so a README never outranks the code. */
    private val minor = setOf("Text", "Markdown")

    /** The API's language when it has one, else a guess from the file name. */
    fun detect(apiLanguage: String?, filename: String): String? {
        apiLanguage?.takeIf { it.isNotBlank() }?.let { return it }
        val name = filename.substringAfterLast('/')
        if (name.equals("Dockerfile", ignoreCase = true) || name.startsWith("Dockerfile.")) return "Dockerfile"
        val ext = name.substringAfterLast('.', "").lowercase()
        return byExtension[ext]
    }

    /**
     * Ranks languages by their total size across [files] (language to size in bytes).
     * Ties keep file order, so a gist of equal parts is led by its first file.
     */
    fun summarize(files: List<Pair<String?, Long>>): LanguageSummary {
        val weights = LinkedHashMap<String, Long>()
        for ((language, size) in files) {
            if (language != null) weights[language] = (weights[language] ?: 0L) + size
        }
        val ranked = weights.entries
            .sortedWith(compareBy<Map.Entry<String, Long>> { it.key in minor }.thenByDescending { it.value })
            .map { it.key }
        return LanguageSummary(ranked.firstOrNull(), ranked.drop(1))
    }

    /** The language's logo, or a generic Material icon when there is none. */
    @Composable
    fun painterFor(language: String?): Painter {
        val logo = language?.let { logos[it] }
        return if (logo != null) painterResource(logo) else rememberVectorPainter(fallbackFor(language))
    }

    private fun fallbackFor(language: String?): ImageVector = when (language) {
        "PowerShell", "Batchfile" -> Icons.Outlined.Terminal
        null, "Text" -> Icons.Outlined.Description
        else -> Icons.Outlined.Code
    }
}
