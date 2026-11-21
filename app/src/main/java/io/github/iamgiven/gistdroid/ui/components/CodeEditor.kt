package io.github.iamgiven.gistdroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Width of a line-number gutter for [lineCount] lines (shared by the viewer and the editor). */
@Composable
fun gutterWidth(lineCount: Int, fontSize: TextUnit = MonoStyle.fontSize): Dp = with(LocalDensity.current) {
    // Monospace digits are ~0.62em wide.
    (fontSize * 0.62f).toDp() * lineCount.toString().length + 20.dp
}

/** [MonoStyle] scaled to [fontSize] (sp). */
fun monoStyle(fontSize: Float): TextStyle =
    MonoStyle.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.46f).sp)

private val CodePadding = 12.dp

/**
 * Full-screen code editor: line numbers, GitHub-style highlighting and optional word wrap.
 * Line numbers are drawn from the text layout, so they stay aligned with wrapped lines.
 */
@Composable
fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    filename: String,
    wrap: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MonoStyle,
) {
    val colors = rememberCodeColors()
    val transformation = remember(filename, colors) { HighlightTransformation(filename, colors) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val lineStarts = remember(value) {
        buildList {
            add(0)
            value.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
        }
    }
    val measurer = rememberTextMeasurer()
    val numberStyle = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
    val gutter = gutterWidth(lineStarts.size, style.fontSize)
    val gutterColor = MaterialTheme.colorScheme.surfaceContainer
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val textHeight = layout?.size?.height?.let { with(density) { it.toDp() } } ?: 0.dp
            val height = maxOf(textHeight + CodePadding * 2, viewportHeight)
            Canvas(
                Modifier
                    .width(gutter)
                    .height(height)
                    .background(gutterColor)
            ) {
                val l = layout ?: return@Canvas
                val padPx = CodePadding.toPx()
                val rightPx = size.width - 8.dp.toPx()
                lineStarts.forEachIndexed { i, start ->
                    if (start > l.layoutInput.text.length) return@forEachIndexed
                    val line = l.getLineForOffset(start)
                    val measured = measurer.measure((i + 1).toString(), numberStyle)
                    drawText(measured, topLeft = Offset(rightPx - measured.size.width, padPx + l.getLineTop(line)))
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = transformation,
                    onTextLayout = { layout = it },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier
                        .then(
                            if (wrap) Modifier.fillMaxWidth()
                            else Modifier.widthIn(min = viewportWidth - gutter)
                        )
                        .heightIn(min = viewportHeight)
                        .padding(CodePadding),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    "Write or paste code here…",
                                    style = style,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }
}
