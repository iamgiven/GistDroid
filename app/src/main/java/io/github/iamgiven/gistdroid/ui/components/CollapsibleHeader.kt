package io.github.iamgiven.gistdroid.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * Scroll-linked collapsing section (like a browser's address bar): it slides away 1:1 with the
 * finger while scrolling down, comes back 1:1 while scrolling up, and settles fully open or
 * closed when the gesture ends.
 */
@Stable
class CollapsibleState {
    /** 0 = fully shown, -height = fully hidden. */
    var offset by mutableFloatStateOf(0f)
        internal set
    internal var height = 0f

    val fraction: Float get() = if (height <= 0f) 1f else 1f + offset / height

    fun show() {
        offset = 0f
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (height <= 0f) return Offset.Zero
            val old = offset
            offset = (offset + available.y).coerceIn(-height, 0f)
            return Offset(0f, offset - old)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (offset != 0f && offset != -height) {
                val target = if (fraction > 0.5f) 0f else -height
                animate(offset, target, animationSpec = spring(stiffness = 600f)) { value, _ -> offset = value }
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberCollapsibleState() = remember { CollapsibleState() }

@Composable
fun Collapsible(state: CollapsibleState, content: @Composable () -> Unit) {
    Box(
        Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                state.height = placeable.height.toFloat()
                val offset = state.offset.coerceAtLeast(-state.height)
                val visible = (placeable.height + offset).roundToInt().coerceAtLeast(0)
                layout(placeable.width, visible) { placeable.place(0, offset.roundToInt()) }
            }
            .graphicsLayer { alpha = state.fraction.coerceIn(0f, 1f) }
    ) { content() }
}
