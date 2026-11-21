package io.github.iamgiven.gistdroid.ui.viewer

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import io.github.iamgiven.gistdroid.data.DEFAULT_CODE_FONT_SIZE
import io.github.iamgiven.gistdroid.data.MAX_CODE_FONT_SIZE
import io.github.iamgiven.gistdroid.data.MIN_CODE_FONT_SIZE
import io.github.iamgiven.gistdroid.data.needsLegacyStoragePermission
import io.github.iamgiven.gistdroid.data.saveToDownloads
import io.github.iamgiven.gistdroid.ui.components.monoStyle
import io.github.iamgiven.gistdroid.ui.components.shareText
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.EditSource
import io.github.iamgiven.gistdroid.ViewerFile
import io.github.iamgiven.gistdroid.ui.appContainer
import io.github.iamgiven.gistdroid.ui.components.CodeView
import io.github.iamgiven.gistdroid.ui.components.MonoStyle
import io.github.iamgiven.gistdroid.ui.components.languageFromName
import io.github.iamgiven.gistdroid.ui.components.relativeTime
import io.github.iamgiven.gistdroid.ui.components.toast

/** Full-screen, read-only view of a single file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(onBack: () -> Unit, onEdit: (EditSource, String) -> Unit) {
    val container = appContainer()
    val file by container.viewerFile.collectAsStateWithLifecycle()
    val wrapDefault by container.settings.wrapCode.collectAsStateWithLifecycle(initialValue = false)
    var wrapOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val wrap = wrapOverride ?: wrapDefault
    var infoOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val fontSize by container.settings.codeFontSize.collectAsStateWithLifecycle(initialValue = DEFAULT_CODE_FONT_SIZE)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val f = file
    if (f == null) {
        // Nothing to show (e.g. the process was restarted) - go back.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val download: () -> Unit = {
        scope.launch {
            runCatching { saveToDownloads(context, f.filename, f.content) }
                .onSuccess { context.toast("Saved to Download/$it") }
                .onFailure { context.toast("Couldn't save: ${it.message}") }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) download() else context.toast("Storage permission is needed to save files")
    }
    val lines = remember(f.content) { f.content.count { it == '\n' } + 1 }
    val language = f.language ?: languageFromName(f.filename)
    val subtitle = listOfNotNull(language, "$lines ${if (lines == 1) "line" else "lines"}").joinToString(" · ")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Tapping the name shows the file info card.
                    Box {
                        Row(
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { infoOpen = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f, fill = false)) {
                                Text(f.filename, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Default.ArrowDropDown, "File info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = infoOpen, onDismissRequest = { infoOpen = false }) {
                            FileInfoCard(f, language, lines)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(f.content))
                            context.toast("${f.filename} copied")
                        },
                        modifier = Modifier.size(40.dp),
                    ) { Icon(Icons.Default.ContentCopy, "Copy") }
                    val source = f.edit
                    if (source != null) {
                        IconButton(onClick = { onEdit(source, f.filename) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Edit, "Edit")
                        }
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Wrap lines") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.WrapText, null) },
                                trailingIcon = { Checkbox(checked = wrap, onCheckedChange = null) },
                                onClick = { wrapOverride = !wrap },
                            )
                            // Text size: stays open so it can be tapped repeatedly.
                            Row(
                                Modifier.padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.FormatSize, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${fontSize.toInt()} sp",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.width(48.dp),
                                )
                                IconButton(
                                    onClick = { scope.launch { container.settings.setCodeFontSize(fontSize - 1) } },
                                    enabled = fontSize > MIN_CODE_FONT_SIZE,
                                ) { Icon(Icons.Default.ZoomOut, "Zoom out") }
                                IconButton(
                                    onClick = { scope.launch { container.settings.setCodeFontSize(fontSize + 1) } },
                                    enabled = fontSize < MAX_CODE_FONT_SIZE,
                                ) { Icon(Icons.Default.ZoomIn, "Zoom in") }
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Download") },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = {
                                    moreOpen = false
                                    if (needsLegacyStoragePermission && ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        download()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    moreOpen = false
                                    context.shareText(f.content, f.filename)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
        ) {
            CodeView(code = f.content, filename = f.filename, wrap = wrap, style = monoStyle(fontSize))
        }
    }
}

/** Small card with details about the file and its gist's revision history. */
@Composable
private fun FileInfoCard(f: ViewerFile, language: String?, lines: Int) {
    val uriHandler = LocalUriHandler.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val green = if (dark) Color(0xFF3FB950) else Color(0xFF1A7F37)
    val red = if (dark) Color(0xFFFF7B72) else Color(0xFFCF222E)
    val context = LocalContext.current

    Column(
        Modifier
            .widthIn(min = 260.dp, max = 320.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(f.filename, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        InfoRow("Language", language ?: "Plain text")
        InfoRow("Lines", "%,d".format(lines))
        InfoRow("Characters", "%,d".format(f.content.length))
        f.updatedAt?.takeIf { it > 0 }?.let { millis ->
            InfoRow(
                "Last modified",
                DateUtils.formatDateTime(
                    context, millis,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                ),
            )
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(2.dp))
        Text(
            if (f.revisions.isEmpty()) "Gist revisions" else "Gist revisions (${f.revisions.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (f.revisions.isEmpty()) {
            Text(
                if (f.revisionsUrl == null) "Local snippet — no commit history." else "No revisions found.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                f.revisions.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.version.take(7),
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            relativeTime(r.committedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (r.additions > 0) {
                            Text("+${r.additions}", style = MaterialTheme.typography.labelMedium, color = green)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (r.deletions > 0) {
                            Text("−${r.deletions}", style = MaterialTheme.typography.labelMedium, color = red)
                        }
                    }
                }
            }
        }
        f.revisionsUrl?.let { url ->
            TextButton(
                onClick = { uriHandler.openUri(url) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("View on GitHub")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
