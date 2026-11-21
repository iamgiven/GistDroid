package io.github.iamgiven.gistdroid.ui.components

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

@Composable
fun rememberCodeColors(): CodeColors {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        CodeColors(
            keyword = cs.primary,
            string = cs.tertiary,
            comment = cs.onSurfaceVariant.copy(alpha = 0.7f),
            number = cs.error.copy(alpha = 0.85f),
            function = cs.secondary,
            type = cs.primary.copy(alpha = 0.8f),
            annotation = cs.tertiary.copy(alpha = 0.8f),
        )
    }
}

/** Transformasi visual untuk highlight di dalam text field editor. */
class HighlightTransformation(
    private val filename: String,
    private val colors: CodeColors,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = if (text.length > 30_000) text
        else SyntaxHighlighter.highlight(text.text, filename, colors)
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) =
        other is HighlightTransformation && other.filename == filename && other.colors == colors

    override fun hashCode() = filename.hashCode() * 31 + colors.hashCode()
}

@Composable
fun CodeView(code: String, filename: String, modifier: Modifier = Modifier) {
    val colors = rememberCodeColors()
    val highlighted = remember(code, filename, colors) { SyntaxHighlighter.highlight(code, filename, colors) }
    val lineNumbers = remember(code) {
        val count = code.count { it == '\n' } + 1
        (1..count).joinToString("\n")
    }
    Row(modifier.fillMaxWidth()) {
        Text(
            text = lineNumbers,
            style = MonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            textAlign = TextAlign.End,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        )
        SelectionContainer(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = highlighted,
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
fun FileCard(filename: String, content: String, language: String?, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    OutlinedCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Description, null,
                Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val lang = language ?: languageFromName(filename)
            if (lang != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        lang,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(content))
                context.toast("$filename disalin")
            }) {
                Icon(Icons.Default.ContentCopy, "Salin", Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CodeView(content, filename)
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Hapus") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    icon, null,
                    Modifier.padding(20.dp).size(40.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

@Composable
fun MetaChip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(PaddingValues(start = 4.dp, top = 20.dp, bottom = 8.dp)),
    )
}

fun languageFromName(filename: String): String? = when (filename.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> "Kotlin"
    "java" -> "Java"
    "js", "mjs", "cjs" -> "JavaScript"
    "jsx" -> "JSX"
    "ts" -> "TypeScript"
    "tsx" -> "TSX"
    "py" -> "Python"
    "rb" -> "Ruby"
    "go" -> "Go"
    "rs" -> "Rust"
    "c", "h" -> "C"
    "cpp", "cc", "hpp" -> "C++"
    "cs" -> "C#"
    "php" -> "PHP"
    "swift" -> "Swift"
    "dart" -> "Dart"
    "sh", "bash", "zsh" -> "Shell"
    "sql" -> "SQL"
    "html", "htm" -> "HTML"
    "css" -> "CSS"
    "scss" -> "SCSS"
    "xml" -> "XML"
    "json" -> "JSON"
    "yaml", "yml" -> "YAML"
    "toml" -> "TOML"
    "md", "markdown" -> "Markdown"
    "lua" -> "Lua"
    else -> null
}

fun parseIso(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

fun relativeTime(millis: Long): String =
    if (millis <= 0) "" else DateUtils.getRelativeTimeSpanString(
        millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Context.shareText(text: String, subject: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    startActivity(Intent.createChooser(intent, "Bagikan"))
}
