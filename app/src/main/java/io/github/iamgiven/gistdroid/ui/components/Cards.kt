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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
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
import androidx.compose.ui.graphics.luminance
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

/**
 * Top header for top-level screens: logo + title, then optional controls (search, filters).
 * The gap below the controls belongs to the header, so scrolled content never touches them.
 */
@Composable
fun ListHeader(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
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
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            if (content != null) {
                Column(Modifier.padding(horizontal = 16.dp), content = content)
                Spacer(Modifier.height(HEADER_GAP))
            }
        }
    }
}

/** Gap between the header title and the search field; also used below the search field. */
val HEADER_GAP = 12.dp

enum class BadgeTone { BLUE, RED, GREEN, NEUTRAL }

/** A status such as "Public"; [icon] is shown by the inline [StatusLabel], the pill [StatusBadge] uses a dot. */
data class Badge(val text: String, val tone: BadgeTone, val icon: ImageVector? = null)

infix fun String.tone(tone: BadgeTone) = Badge(this, tone)

/** Badge for a gist's visibility: public = blue, secret = red. */
fun visibilityBadge(public: Boolean) =
    if (public) Badge("Public", BadgeTone.BLUE, Icons.Outlined.Public)
    else Badge("Secret", BadgeTone.RED, Icons.Outlined.Lock)

@Composable
private fun BadgeTone.color(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (this) {
        BadgeTone.BLUE -> MaterialTheme.colorScheme.primary
        BadgeTone.RED -> if (dark) Color(0xFFFF7B72) else Color(0xFFCF222E)
        BadgeTone.GREEN -> if (dark) Color(0xFF3FB950) else Color(0xFF1A7F37)
        BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Small outlined status pill, e.g. "● PUBLIC", tinted by its [BadgeTone]. */
@Composable
fun StatusBadge(badge: Badge, compact: Boolean = false) {
    val text = badge.text
    val fg = badge.tone.color()
    Surface(
        shape = CircleShape,
        color = fg.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 2.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(if (compact) 5.dp else 6.dp).background(fg, CircleShape))
            Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
            Text(
                text.uppercase(),
                color = fg,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
    }
}

/** Compact inline status, e.g. a lock icon followed by "Secret", tinted by its [BadgeTone]. */
@Composable
fun StatusLabel(badge: Badge) {
    val fg = badge.tone.color()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (badge.icon != null) {
            Icon(badge.icon, null, Modifier.size(14.dp), tint = fg)
            Spacer(Modifier.width(4.dp))
        }
        Text(badge.text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private val ListCardShape = RoundedCornerShape(20.dp)
private val PreviewShape = RoundedCornerShape(10.dp)

/**
 * Card used by both the gist list and the local snippet list: language logo, title and status,
 * then a divider, a metadata row (language label first) and a code preview of up to three lines.
 */
@Composable
fun SnippetCard(
    title: String,
    language: LanguageSummary,
    badge: Badge?,
    preview: AnnotatedString?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewPlaceholder: Boolean = false,
    meta: @Composable RowScope.() -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ListCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Languages.painterFor(language.primary),
                    contentDescription = language.primary,
                    modifier = Modifier.size(22.dp),
                    tint = muted,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    Spacer(Modifier.width(10.dp))
                    StatusLabel(badge)
                }
            }
            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                language.label?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
                }
                meta()
            }
            if (preview != null || previewPlaceholder) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = PreviewShape,
                    border = cardBorder(),
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    Crossfade(targetState = preview, label = "preview") { text ->
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (text == null) {
                                SkeletonLines(listOf(0.8f, 0.55f, 0.68f))
                            } else {
                                Text(
                                    text,
                                    style = MonoStyle.copy(fontSize = 12.sp),
                                    color = muted,
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
        shape = ListCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(skeletonColor(), CircleShape))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    SkeletonLines(listOf(0.6f), MaterialTheme.typography.titleMedium.lineHeight)
                }
            }
            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            SkeletonLines(listOf(0.55f), MaterialTheme.typography.labelMedium.lineHeight)
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = PreviewShape,
                border = cardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    SkeletonLines(listOf(0.8f, 0.55f, 0.68f))
                }
            }
        }
    }
}

/** Summary card at the top of a detail screen: status badges, description, metadata and actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailHeaderCard(
    badges: List<Badge>,
    description: String?,
    meta: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { badges.forEach { StatusBadge(it) } }
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(description, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(
                Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { meta() }
            if (actions != null) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
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
                val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
