package io.github.iamgiven.gistdroid.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardShape = RoundedCornerShape(28.dp)
private val InnerShape = RoundedCornerShape(20.dp)

@Composable
fun cardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

/** The app's "</>" mark without background: chevrons follow the text color, the slash uses primary. */
@Composable
fun AppLogo(modifier: Modifier = Modifier.size(30.dp)) {
    val chevron = MaterialTheme.colorScheme.onSurface
    val slash = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        // Same geometry as the launcher icon, cropped to its 56-unit content box.
        val k = size.minDimension / 56f
        fun p(x: Float, y: Float) = Offset((x - 26f) * k, (y - 26f) * k)
        val stroke = Stroke(width = 6.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val chevrons = Path().apply {
            moveTo(p(41f, 41f).x, p(41f, 41f).y); lineTo(p(29f, 54f).x, p(29f, 54f).y); lineTo(p(41f, 67f).x, p(41f, 67f).y)
            moveTo(p(67f, 41f).x, p(67f, 41f).y); lineTo(p(79f, 54f).x, p(79f, 54f).y); lineTo(p(67f, 67f).x, p(67f, 67f).y)
        }
        drawPath(chevrons, chevron, style = stroke)
        drawLine(slash, p(58.5f, 37f), p(49.5f, 71f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

/** Top header for top-level screens: logo + title, then optional controls (search, filters). */
@Composable
fun ListHeader(title: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            Row(
                Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = HEADER_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLogo(Modifier.size(30.dp))
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(horizontal = 16.dp), content = content)
        }
    }
}

/** Gap between the header title and the search field; also used below the search field. */
val HEADER_GAP = 12.dp

/** Small outlined status pill, e.g. "● PUBLIC". [active] uses the primary color. */
@Composable
fun StatusBadge(text: String, active: Boolean) {
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(fg, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text.uppercase(),
                color = fg,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
    }
}

/** Card used by both the gist list and the local snippet list. */
@Composable
fun SnippetCard(
    title: String,
    icon: ImageVector,
    badge: Pair<String, Boolean>?,
    preview: AnnotatedString?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewPlaceholder: Boolean = false,
    meta: @Composable RowScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    Spacer(Modifier.width(10.dp))
                    StatusBadge(badge.first, badge.second)
                }
            }
            HorizontalDivider(
                Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), content = meta)
            if (preview != null || previewPlaceholder) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = InnerShape,
                    border = cardBorder(),
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    Crossfade(targetState = preview, label = "preview") { text ->
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (text == null) {
                                SkeletonLines(listOf(0.8f, 0.55f, 0.68f))
                            } else {
                                Text(
                                    text,
                                    style = MonoStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Placeholder shaped like [SnippetCard], shown while the list loads. */
@Composable
fun SkeletonCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).background(skeletonColor(), CircleShape))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    SkeletonLines(listOf(0.6f), MaterialTheme.typography.titleMedium.lineHeight)
                }
            }
            HorizontalDivider(
                Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            SkeletonLines(listOf(0.55f), MaterialTheme.typography.labelMedium.lineHeight)
            Spacer(Modifier.height(14.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = InnerShape,
                border = cardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    SkeletonLines(listOf(0.8f, 0.55f, 0.68f))
                }
            }
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Bottom navigation with a wide pill indicator that wraps both icon and label. */
@Composable
fun AppNavigationBar(items: List<NavItem>, selectedRoute: String?, onSelect: (NavItem) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(64.dp),
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "navIndicator",
                )
                val fg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "navContent",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(bg)
                        .clickable { onSelect(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (selected) item.selectedIcon else item.icon, null, tint = fg)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        color = fg,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
