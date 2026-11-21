package io.github.iamgiven.gistdroid.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.ui.appContainer
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
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) CodeColors.GitHubDark else CodeColors.GitHubLight
}

/** Visual transformation that highlights code inside the editor text field. */
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

/** Files longer than this start truncated, with a "Show all" button. */
private const val PREVIEW_LINES = 20
private const val TRUNCATE_THRESHOLD = 30

/**
 * Read-only code view with GitHub-style highlighting and line numbers.
 * When [wrap] is on, each source line is laid out on its own row so the line number stays
 * aligned with the first visual line of a wrapped source line.
 */
@Composable
fun CodeView(code: String, filename: String, wrap: Boolean, maxLines: Int? = null, modifier: Modifier = Modifier) {
    val colors = rememberCodeColors()
    val highlighted = remember(code, filename, colors) { SyntaxHighlighter.highlight(code, filename, colors) }
    val lineStarts = remember(code) {
        buildList {
            add(0)
            code.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
        }
    }
    val shown = if (maxLines != null) minOf(maxLines, lineStarts.size) else lineStarts.size
    val visibleEnd = if (shown < lineStarts.size) lineStarts[shown] - 1 else code.length
    val numberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val gutterColor = MaterialTheme.colorScheme.surfaceContainer
    val gutterWidth = with(LocalDensity.current) {
        // Width of the widest line number in monospace + padding.
        (shown.toString().length * 8.sp.toDp().value).dp + 20.dp
    }

    if (wrap) {
        SelectionContainer(modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().height(12.dp)) {
                    Box(Modifier.width(gutterWidth).fillMaxHeight().background(gutterColor))
                }
                for (i in 0 until shown) {
                    val lineEnd = if (i + 1 < lineStarts.size) lineStarts[i + 1] - 1 else code.length
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        DisableSelection {
                            Text(
                                (i + 1).toString(),
                                style = MonoStyle,
                                color = numberColor,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .width(gutterWidth)
                                    .fillMaxHeight()
                                    .background(gutterColor)
                                    .padding(end = 8.dp),
                            )
                        }
                        Text(
                            highlighted.subSequence(lineStarts[i], lineEnd),
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = true,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().height(12.dp)) {
                    Box(Modifier.width(gutterWidth).fillMaxHeight().background(gutterColor))
                }
            }
        }
    } else {
        val numbers = remember(shown) { (1..shown).joinToString("\n") }
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Text(
                text = numbers,
                style = MonoStyle,
                color = numberColor,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .background(gutterColor)
                    .padding(end = 8.dp, top = 12.dp, bottom = 12.dp),
            )
            SelectionContainer(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (visibleEnd < code.length) highlighted.subSequence(0, visibleEnd) else highlighted,
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
fun FileCard(filename: String, content: String, language: String?, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val wrapDefault by appContainer().settings.wrapCode.collectAsStateWithLifecycle(initialValue = false)
    var wrapOverride by rememberSaveable(filename) { mutableStateOf<Boolean?>(null) }
    val wrap = wrapOverride ?: wrapDefault
    var collapsed by rememberSaveable(filename) { mutableStateOf(false) }
    val lineCount = remember(content) { content.count { it == '\n' } + 1 }
    val long = lineCount > TRUNCATE_THRESHOLD
    var showAll by rememberSaveable(filename) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")

    OutlinedCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed }
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.ExpandMore,
                if (collapsed) "Expand" else "Collapse",
                Modifier.size(22.dp).rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    filename,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val lang = language ?: languageFromName(filename)
                Text(
                    listOfNotNull(lang, "$lineCount ${if (lineCount == 1) "line" else "lines"}").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { wrapOverride = !wrap }) {
                Icon(
                    Icons.AutoMirrored.Filled.WrapText,
                    if (wrap) "Disable word wrap" else "Enable word wrap",
                    Modifier.size(20.dp),
                    tint = if (wrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(content))
                context.toast("$filename copied")
            }) {
                Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(visible = !collapsed, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CodeView(
                    code = content,
                    filename = filename,
                    wrap = wrap,
                    maxLines = if (long && !showAll) PREVIEW_LINES else null,
                )
                if (long) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    ) {
                        Icon(
                            if (showAll) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                            null,
                            Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (showAll) "Show less" else "Show all $lineCount lines")
                    }
                }
            }
        }
    }
}

/**
 * Top header for list screens: title + extra controls (search, filters) on a single surface,
 * so the whole header changes tint together when the list below is scrolled.
 */
@Composable
fun ListHeader(title: String, elevated: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val color by animateColorAsState(
        if (elevated) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
        label = "headerColor",
    )
    Surface(color = color) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(bottom = 8.dp)
        ) {
            Box(Modifier.height(64.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(horizontal = 16.dp), content = content)
        }
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") }
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
    startActivity(Intent.createChooser(intent, "Share"))
}
