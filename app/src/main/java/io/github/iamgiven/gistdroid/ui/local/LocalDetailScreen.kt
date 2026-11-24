package io.github.iamgiven.gistdroid.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.iamgiven.gistdroid.ui.components.BadgeTone
import io.github.iamgiven.gistdroid.ui.components.DetailHeaderCard
import io.github.iamgiven.gistdroid.ui.theme.LocalFabColors
import androidx.compose.material3.Button
import io.github.iamgiven.gistdroid.ui.components.tone
import io.github.iamgiven.gistdroid.ui.components.visibilityBadge
import io.github.iamgiven.gistdroid.ui.components.DragHandleIcon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import io.github.iamgiven.gistdroid.ui.components.fileCount
import io.github.iamgiven.gistdroid.ui.components.moved
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.EditSource
import io.github.iamgiven.gistdroid.ViewerFile
import io.github.iamgiven.gistdroid.ui.appViewModel
import io.github.iamgiven.gistdroid.ui.components.FileCard
import io.github.iamgiven.gistdroid.ui.components.MetaChip
import io.github.iamgiven.gistdroid.ui.components.relativeTime
import io.github.iamgiven.gistdroid.ui.components.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenGist: (String) -> Unit,
    onViewFile: (ViewerFile) -> Unit,
) {
    val vm = appViewModel { LocalDetailViewModel(it, id) }
    val snippet by vm.snippet.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmUpload by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val s = snippet
    val lazyState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val savedFiles = s?.files.orEmpty()
    var files by remember(savedFiles) { mutableStateOf(savedFiles) }
    val reorderState = rememberReorderableLazyListState(lazyState) { from, to ->
        val f = files.firstOrNull { FILE_KEY + it.name == from.key } ?: return@rememberReorderableLazyListState
        val t = files.firstOrNull { FILE_KEY + it.name == to.key } ?: return@rememberReorderableLazyListState
        files = files.moved(f, t)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(s?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        if (s != null) {
                            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = { menuOpen = false; confirmDelete = true },
                                    )
                                }
                            }
                        }
                    },
                )
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (s == null) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            state = lazyState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                val gistId = s.gistId
                DetailHeaderCard(
                    badges = listOf(
                        if (gistId != null) "On Gist" tone BadgeTone.BLUE else "Local only" tone BadgeTone.NEUTRAL,
                    ) + listOfNotNull(if (gistId != null) visibilityBadge(s.isPublic) else null),
                    description = s.description,
                    meta = {
                        MetaChip(Icons.Outlined.Description, fileCount(s.files.size))
                        MetaChip(Icons.Outlined.Schedule, "Updated ${relativeTime(s.updatedAt)}")
                    },
                    actions = {
                        Button(
                            onClick = { confirmUpload = true },
                            enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalFabColors.current.container,
                                contentColor = LocalFabColors.current.content,
                            ),
                        ) {
                            Icon(Icons.Default.CloudUpload, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(if (gistId != null) "Sync to Gist" else "Upload to Gist")
                        }
                        if (gistId != null) {
                            OutlinedButton(onClick = { onOpenGist(gistId) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Open Gist")
                            }
                        }
                    },
                )
            }
            items(files, key = { FILE_KEY + it.name }) { file ->
                ReorderableItem(reorderState, key = FILE_KEY + file.name) { isDragging ->
                    FileCard(
                        filename = file.name,
                        content = file.content,
                        language = null,
                        dragging = isDragging,
                        onFullscreen = {
                            onViewFile(
                                ViewerFile(
                                    filename = file.name,
                                    content = file.content,
                                    language = null,
                                    updatedAt = s.updatedAt,
                                    edit = EditSource(isGist = false, id = id.toString()),
                                )
                            )
                        },
                        dragHandle = if (files.size > 1) {
                            {
                                DragHandleIcon(
                                    Modifier.draggableHandle(
                                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = { if (files != savedFiles) vm.reorder(files) },
                                    )
                                )
                            }
                        } else null,
                    )
                }
            }
        }
    }

    if (confirmUpload && s != null) {
        val linked = s.gistId != null
        var uploadPublic by rememberSaveable { mutableStateOf(s.isPublic) }
        AlertDialog(
            onDismissRequest = { confirmUpload = false },
            icon = { Icon(Icons.Default.CloudUpload, null) },
            title = { Text(if (linked) "Sync to Gist?" else "Upload to Gist?") },
            text = {
                if (linked) {
                    Text("The linked gist on GitHub will be overwritten with this snippet's description and files.")
                } else {
                    Column(Modifier.selectableGroup()) {
                        Text("A new gist with ${fileCount(s.files.size)} will be created on your GitHub account.")
                        Spacer(Modifier.height(12.dp))
                        VisibilityOption(
                            selected = !uploadPublic,
                            icon = Icons.Outlined.Lock,
                            title = "Secret",
                            subtitle = "Only people with the link can see it",
                            onSelect = { uploadPublic = false },
                        )
                        VisibilityOption(
                            selected = uploadPublic,
                            icon = Icons.Outlined.Public,
                            title = "Public",
                            subtitle = "Anyone can find it on GitHub",
                            onSelect = { uploadPublic = true },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "GitHub doesn't allow changing this after the gist is created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpload = false
                    if (linked) vm.upload() else vm.upload(public = uploadPublic)
                }) { Text(if (linked) "Sync" else "Upload") }
            },
            dismissButton = { TextButton(onClick = { confirmUpload = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Delete snippet?") },
            text = { Text("The snippet will be removed from this device. The linked GitHub gist (if any) will not be deleted.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** One radio row in the upload dialog's visibility choice. */
@Composable
private fun VisibilityOption(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val FILE_KEY = "file:"
