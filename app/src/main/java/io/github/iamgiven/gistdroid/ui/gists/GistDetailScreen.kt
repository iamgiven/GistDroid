package io.github.iamgiven.gistdroid.ui.gists

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.ViewerFile
import io.github.iamgiven.gistdroid.data.applyOrder
import io.github.iamgiven.gistdroid.data.remote.Gist
import io.github.iamgiven.gistdroid.ui.components.BadgeTone
import io.github.iamgiven.gistdroid.ui.components.DetailHeaderCard
import io.github.iamgiven.gistdroid.ui.components.tone
import io.github.iamgiven.gistdroid.ui.components.visibilityBadge
import io.github.iamgiven.gistdroid.ui.components.DragHandleIcon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import io.github.iamgiven.gistdroid.ui.components.fileCount
import io.github.iamgiven.gistdroid.ui.components.moved
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.iamgiven.gistdroid.ui.appContainer
import io.github.iamgiven.gistdroid.ui.appViewModel
import io.github.iamgiven.gistdroid.ui.components.EmptyState
import io.github.iamgiven.gistdroid.ui.components.FileCard
import io.github.iamgiven.gistdroid.ui.components.MetaChip
import io.github.iamgiven.gistdroid.ui.components.parseIso
import io.github.iamgiven.gistdroid.ui.components.relativeTime
import io.github.iamgiven.gistdroid.ui.components.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GistDetailScreen(
    id: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenLocal: (Long) -> Unit,
    onViewFile: (ViewerFile) -> Unit,
) {
    val vm = appViewModel { GistDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val localId by vm.localId.collectAsStateWithLifecycle()
    val order by vm.order.collectAsStateWithLifecycle()
    val login by appContainer().settings.login.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val gist = state.gist
    val isOwner = gist != null && (gist.owner == null || gist.owner.login == login)

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(gist?.title ?: "Gist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        if (gist != null) {
                            IconButton(onClick = vm::toggleStar, enabled = !state.busy) {
                                Icon(
                                    if (state.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    "Star",
                                    tint = if (state.starred) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isOwner) {
                                IconButton(onClick = onEdit, enabled = !state.busy) { Icon(Icons.Default.Edit, "Edit") }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (localId != null) "Update local copy" else "Save locally") },
                                        leadingIcon = { Icon(Icons.Default.Download, null) },
                                        onClick = { menuOpen = false; vm.saveLocal() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open in browser") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                        onClick = { menuOpen = false; uriHandler.openUri(gist.htmlUrl) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share link") },
                                        leadingIcon = { Icon(Icons.Default.Share, null) },
                                        onClick = { menuOpen = false; context.shareText(gist.htmlUrl, gist.title) },
                                    )
                                    if (isOwner) {
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
                        }
                    },
                )
                if (state.busy || (state.loading && gist != null)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                gist == null && state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                gist == null -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "Couldn't load gist",
                    message = state.error.orEmpty(),
                ) { Button(onClick = vm::load) { Text("Retry") } }
                else -> GistContent(gist, order, localId, onOpenLocal, onReorder = vm::saveOrder, onViewFile = onViewFile)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Delete gist?") },
            text = { Text("The gist will be permanently deleted from GitHub. Your local copy (if any) will be kept.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GistContent(
    gist: Gist,
    order: List<String>?,
    localId: Long?,
    onOpenLocal: (Long) -> Unit,
    onReorder: (List<String>) -> Unit,
    onViewFile: (ViewerFile) -> Unit,
) {
    val lazyState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val savedNames = remember(gist, order) { applyOrder(gist.files.keys, order) }
    var names by remember(savedNames) { mutableStateOf(savedNames) }
    val reorderState = rememberReorderableLazyListState(lazyState) { from, to ->
        val f = (from.key as? String)?.removePrefix(FILE_KEY) ?: return@rememberReorderableLazyListState
        val t = (to.key as? String)?.takeIf { it.startsWith(FILE_KEY) }?.removePrefix(FILE_KEY)
            ?: return@rememberReorderableLazyListState
        names = names.moved(f, t)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LazyColumn(
        state = lazyState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            DetailHeaderCard(
                badges = buildList {
                    add(visibilityBadge(gist.public))
                    if (localId != null) add("Saved locally" tone BadgeTone.GREEN)
                },
                description = gist.description,
                meta = {
                    MetaChip(Icons.Outlined.Description, fileCount(gist.files.size))
                    MetaChip(Icons.Outlined.Schedule, "Updated ${relativeTime(parseIso(gist.updatedAt))}")
                    gist.owner?.login?.let { MetaChip(Icons.Outlined.Person, it) }
                },
                actions = if (localId != null) {
                    {
                        OutlinedButton(onClick = { onOpenLocal(localId) }) {
                            Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Open local copy")
                        }
                    }
                } else null,
            )
        }
        items(names, key = { FILE_KEY + it }) { name ->
            val file = gist.files[name] ?: return@items
            ReorderableItem(reorderState, key = FILE_KEY + name) { isDragging ->
                FileCard(
                    filename = name,
                    content = file.content.orEmpty(),
                    language = file.language,
                    dragging = isDragging,
                    onFullscreen = { onViewFile(ViewerFile(name, file.content.orEmpty(), file.language)) },
                    dragHandle = if (names.size > 1) {
                        {
                            DragHandleIcon(
                                Modifier.draggableHandle(
                                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = { if (names != savedNames) onReorder(names) },
                                )
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

private const val FILE_KEY = "file:"
