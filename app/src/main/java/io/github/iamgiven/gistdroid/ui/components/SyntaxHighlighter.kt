package io.github.iamgiven.gistdroid.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** Token colors, modeled after GitHub's Primer "prettylights" syntax theme. */
@Immutable
data class CodeColors(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val constant: Color,
    val entity: Color,
    val tag: Color,
    val variable: Color,
) {
    companion object {
        val GitHubLight = CodeColors(
            keyword = Color(0xFFCF222E),
            string = Color(0xFF0A3069),
            comment = Color(0xFF6E7781),
            constant = Color(0xFF0550AE),
            entity = Color(0xFF8250DF),
            tag = Color(0xFF116329),
            variable = Color(0xFF953800),
        )
        val GitHubDark = CodeColors(
            keyword = Color(0xFFFF7B72),
            string = Color(0xFFA5D6FF),
            comment = Color(0xFF8B949E),
            constant = Color(0xFF79C0FF),
            entity = Color(0xFFD2A8FF),
            tag = Color(0xFF7EE787),
            variable = Color(0xFFFFA657),
        )
    }
}

/** Lightweight regex-based highlighter; good enough for most popular languages. */
object SyntaxHighlighter {

    private enum class Family { SLASH, HASH, DASH, MARKUP, JSON, PLAIN }

    private val KEYWORDS = """
        abstract and as assert async await break case catch class const continue def default defer
        del do elif else enum except export extends final finally fn for from fun func function
        global go goto if impl implements import in init inline instanceof interface internal is lambda
        let loop match mod module mut namespace new not object of open or override package
        pass private protected pub public raise readonly return sealed static struct suspend
        switch throw throws trait try type typealias typedef typeof unsafe use val var
        void when where while with yield int long float double char boolean bool byte short string
        unsigned signed sizeof echo fi then done esac local data companion lateinit crate dyn ref
        elif until select from insert update delete into values create table join left right
        inner outer on group order by limit having distinct union alter drop index primary key
        foreign references export source alias unset readonly declare
    """.trimIndent().split(Regex("\\s+")).toSet()

    private val CONSTANTS = setOf(
        "true", "false", "null", "nil", "None", "True", "False", "undefined", "NaN", "Infinity",
        "this", "self", "super", "it", "NULL", "TRUE", "FALSE",
    )

    private val SQL_KEYWORDS = setOf(
        "select", "from", "where", "insert", "update", "delete", "into", "values", "create", "table",
        "join", "left", "right", "inner", "outer", "on", "group", "order", "by", "limit", "having",
        "distinct", "union", "alter", "drop", "index", "primary", "key", "foreign", "references",
        "and", "or", "not", "as", "in", "is", "set", "asc", "desc", "count", "default",
    )

    private val PYTHON_LINE = Regex("^(def|class|import|from) .*")

    /** Guesses the language family from content when the file has no extension. */
    private fun guessFamily(code: String): Family {
        val head = code.trimStart()
        if (head.isEmpty()) return Family.SLASH
        if (head.startsWith("#!") || head.startsWith("$ ")) return Family.HASH
        if (head.startsWith("<")) return Family.MARKUP
        if (head.startsWith("{") || head.startsWith("[")) return Family.JSON
        val sample = code.lineSequence().take(200).map { it.trimStart() }.toList()
        val hash = sample.count { it.startsWith("#") }
        val slash = sample.count { it.startsWith("//") || it.startsWith("/*") }
        val pythonish = sample.any { PYTHON_LINE.matches(it) && !it.endsWith(";") }
        return if (hash > slash || pythonish) Family.HASH else Family.SLASH
    }

    private fun familyOf(filename: String, code: String): Family {
        val lower = filename.lowercase()
        val ext = lower.substringAfterLast('.', "")
        if (lower == "dockerfile" || lower == "makefile" || lower.endsWith(".env")) return Family.HASH
        return when (ext) {
            "kt", "kts", "java", "c", "h", "cpp", "cc", "cxx", "hpp", "cs", "js", "jsx", "mjs", "cjs",
            "ts", "tsx", "go", "rs", "swift", "dart", "scala", "php", "css", "scss", "less", "groovy",
            "gradle", "m", "mm", "zig", "v", "sol", "proto", "jsonc" -> Family.SLASH
            "py", "rb", "sh", "bash", "zsh", "fish", "yaml", "yml", "toml", "r", "pl", "conf", "ini",
            "cfg", "ps1", "nix", "ex", "exs", "cmake", "properties", "tf" -> Family.HASH
            "sql", "lua", "hs", "elm" -> Family.DASH
            "html", "htm", "xml", "svg", "vue", "svelte", "xaml", "plist" -> Family.MARKUP
            "json" -> Family.JSON
            "" -> guessFamily(code)
            "txt", "md", "markdown", "log", "csv" -> Family.PLAIN
            else -> Family.SLASH
        }
    }

    private const val STRINGS =
        "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`"
    private const val NUMBER =
        "\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)[fFlLuU]?\\b"
    private const val WORD = "\\b[A-Za-z_]\\w*\\b"

    private val regexCache = HashMap<Family, Regex>()

    private fun regexFor(family: Family): Regex = regexCache.getOrPut(family) {
        val comment = when (family) {
            Family.SLASH -> "//[^\\n]*|/\\*[\\s\\S]*?\\*/"
            Family.HASH -> "#(?!!)[^\\n]*|#![^\\n]*"
            Family.DASH -> "--[^\\n]*"
            Family.MARKUP -> "<!--[\\s\\S]*?-->"
            else -> "(?!x)x"
        }
        // Groups: 1=comment 2=string 3=annotation/tag/variable 4=number 5=word
        val third = when (family) {
            Family.MARKUP -> "</?[A-Za-z][\\w:.-]*|/?>"
            Family.HASH -> "\\$\\{[^}\\n]*\\}|\\$[A-Za-z_]\\w*|\\$[0-9@#?*!$-]|@[A-Za-z_][\\w.]*"
            else -> "@[A-Za-z_][\\w.]*"
        }
        Regex("($comment)|($STRINGS)|($third)|($NUMBER)|($WORD)")
    }

    fun highlight(code: String, filename: String, colors: CodeColors): AnnotatedString {
        val family = familyOf(filename, code)
        if (family == Family.PLAIN || code.length > 150_000) return AnnotatedString(code)
        val regex = regexFor(family)
        val ext = filename.lowercase().substringAfterLast('.', "")
        val sql = ext == "sql"
        val yamlLike = ext == "yaml" || ext == "yml" || ext == "toml"

        val comment = SpanStyle(color = colors.comment)
        val string = SpanStyle(color = colors.string)
        val keyword = SpanStyle(color = colors.keyword)
        val constant = SpanStyle(color = colors.constant)
        val entity = SpanStyle(color = colors.entity)
        val tag = SpanStyle(color = colors.tag)
        val variable = SpanStyle(color = colors.variable)

        return buildAnnotatedString {
            append(code)
            for (m in regex.findAll(code)) {
                val range = m.range
                val g = m.groups
                val next = nextNonSpace(code, range.last + 1)
                val style = when {
                    g[1] != null -> comment
                    g[2] != null -> if ((family == Family.JSON || yamlLike) && next == ':' &&
                        isKeyPosition(code, range.first)
                    ) constant else string
                    g[3] != null -> when {
                        family == Family.MARKUP -> tag
                        m.value.startsWith("$") -> variable
                        family == Family.HASH -> entity // python decorators
                        else -> keyword // annotations
                    }
                    g[4] != null -> constant
                    g[5] != null -> {
                        val word = m.value
                        when {
                            family == Family.MARKUP -> if (next == '=') constant else null
                            word in CONSTANTS -> constant
                            family == Family.JSON -> null
                            sql && word.lowercase() in SQL_KEYWORDS -> keyword
                            word in KEYWORDS -> keyword
                            next == '(' -> entity
                            // YAML / TOML keys
                            yamlLike && next == ':' && isKeyPosition(code, range.first) -> tag
                            word[0].isUpperCase() && word.length > 1 && word.any { it.isLowerCase() } -> entity
                            else -> null
                        }
                    }
                    else -> null
                } ?: continue
                addStyle(style, range.first, range.last + 1)
            }
        }
    }

    private fun nextNonSpace(code: String, from: Int): Char? {
        var i = from
        while (i < code.length && (code[i] == ' ' || code[i] == '\t')) i++
        return code.getOrNull(i)
    }

    /** True if only whitespace, '-', '{' or ',' precede [start] on its line (i.e. it's a map key). */
    private fun isKeyPosition(code: String, start: Int): Boolean {
        var i = start - 1
        while (i >= 0 && code[i] != '\n') {
            val c = code[i]
            if (c != ' ' && c != '\t' && c != '-' && c != '{' && c != ',') return false
            i--
        }
        return true
    }
}
