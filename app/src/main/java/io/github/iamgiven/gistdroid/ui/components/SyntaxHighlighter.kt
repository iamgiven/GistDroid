package io.github.iamgiven.gistdroid.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@Immutable
data class CodeColors(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val type: Color,
    val annotation: Color,
)

/** Highlighter ringan berbasis regex; cukup untuk sebagian besar bahasa populer. */
object SyntaxHighlighter {

    private enum class Family { SLASH, HASH, DASH, MARKUP, JSON, PLAIN }

    private val KEYWORDS = """
        abstract and as assert async await break case catch class const continue def default defer
        del do elif else enum except export extends false final finally fn for from fun func function
        global go goto if impl implements import in init inline instanceof interface internal is lambda
        let loop match mod module mut namespace new nil none not null object of open or override package
        pass private protected pub public raise readonly return sealed self static struct super suspend
        switch this throw throws trait true try type typealias typedef typeof undefined unsafe use val var
        void when where while with yield int long float double char boolean bool byte short string
        unsigned signed sizeof echo fi then done esac local data companion lateinit crate dyn ref
        True False None
        select from where insert update delete into values create table join left right inner outer on
        group order by limit having distinct union alter drop index primary key foreign references
    """.trimIndent().split(Regex("\\s+")).toSet()

    private val CASE_INSENSITIVE_SQL = setOf(
        "select", "from", "where", "insert", "update", "delete", "into", "values", "create", "table",
        "join", "left", "right", "inner", "outer", "on", "group", "order", "by", "limit", "having",
        "distinct", "union", "alter", "drop", "index", "primary", "key", "foreign", "references",
        "and", "or", "not", "null", "as", "in", "is", "set",
    )

    private fun familyOf(filename: String): Family {
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
            "" , "txt", "md", "markdown", "log", "csv" -> Family.PLAIN
            else -> Family.SLASH
        }
    }

    private const val STRINGS =
        "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`"
    private const val NUMBER =
        "\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)[fFlLuU]?\\b"
    private const val ANNOTATION = "@[A-Za-z_][\\w.]*"
    private const val WORD = "\\b[A-Za-z_]\\w*\\b"

    private val regexCache = HashMap<Family, Regex>()

    private fun regexFor(family: Family): Regex = regexCache.getOrPut(family) {
        val comment = when (family) {
            Family.SLASH -> "//[^\\n]*|/\\*[\\s\\S]*?\\*/"
            Family.HASH -> "#[^\\n]*"
            Family.DASH -> "--[^\\n]*"
            Family.MARKUP -> "<!--[\\s\\S]*?-->"
            else -> "(?!x)x"
        }
        // Grup: 1=komentar 2=string 3=anotasi/tag 4=angka 5=kata
        val third = if (family == Family.MARKUP) "</?[A-Za-z][\\w:.-]*|/?>" else ANNOTATION
        Regex("($comment)|($STRINGS)|($third)|($NUMBER)|($WORD)")
    }

    fun highlight(code: String, filename: String, colors: CodeColors): AnnotatedString {
        val family = familyOf(filename)
        if (family == Family.PLAIN || code.length > 150_000) return AnnotatedString(code)
        val regex = regexFor(family)
        val sql = filename.lowercase().endsWith(".sql")

        val commentStyle = SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
        val stringStyle = SpanStyle(color = colors.string)
        val annotationStyle = SpanStyle(color = colors.annotation)
        val numberStyle = SpanStyle(color = colors.number)
        val keywordStyle = SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold)
        val functionStyle = SpanStyle(color = colors.function)
        val typeStyle = SpanStyle(color = colors.type)

        return buildAnnotatedString {
            append(code)
            for (m in regex.findAll(code)) {
                val range = m.range
                val g = m.groups
                val style = when {
                    g[1] != null -> commentStyle
                    g[2] != null -> if (family == Family.JSON && isJsonKey(code, range.last)) functionStyle else stringStyle
                    g[3] != null -> if (family == Family.MARKUP) keywordStyle else annotationStyle
                    g[4] != null -> numberStyle
                    g[5] != null -> {
                        val word = m.value
                        when {
                            family == Family.MARKUP -> null
                            word in KEYWORDS || (sql && word.lowercase() in CASE_INSENSITIVE_SQL) -> keywordStyle
                            family == Family.JSON -> null
                            nextNonSpace(code, range.last + 1) == '(' -> functionStyle
                            word[0].isUpperCase() -> typeStyle
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

    private fun isJsonKey(code: String, lastIndex: Int): Boolean = nextNonSpace(code, lastIndex + 1) == ':'
}
