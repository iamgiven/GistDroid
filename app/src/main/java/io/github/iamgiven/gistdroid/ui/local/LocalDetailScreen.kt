package io.github.iamgiven.gistdroid.ui.local

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
import androidx.compose.foundation.lazy.itemsIndexed
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
) {
    val vm = appViewModel { LocalDetailViewModel(it, id) }
    val snippet by vm.snippet.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val s = snippet
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
                                        text = { Text("Share content") },
                                        leadingIcon = { Icon(Icons.Default.Share, null) },
                                        onClick = {
                                            menuOpen = false
                                            val text = if (s.files.size == 1) s.files[0].content
                                            else s.files.joinToString("\n\n") { "// ${it.name}\n${it.content}" }
                                            context.shareText(text, s.title)
                                        },
                                    )
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.description.isNotBlank()) {
                        Text(s.description, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (s.gistId != null) MetaChip(Icons.Outlined.CloudDone, "Linked to Gist")
                        else MetaChip(Icons.Outlined.PhoneAndroid, "Local only")
                        MetaChip(
                            if (s.isPublic) Icons.Outlined.Public else Icons.Outlined.Lock,
                            if (s.isPublic) "Public" else "Secret",
                        )
                        MetaChip(Icons.Outlined.Description, "${s.files.size} file")
                    }
                    MetaChip(Icons.Outlined.Schedule, "Updated ${relativeTime(s.updatedAt)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = vm::upload, enabled = !state.busy) {
                            Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
                            Text(if (s.gistId != null) "  Sync to Gist" else "  Upload to Gist")
                        }
                        val gistId = s.gistId
                        if (gistId != null) {
                            OutlinedButton(onClick = { onOpenGist(gistId) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                                Text("  Open Gist")
                            }
                        }
                    }
                }
            }
            itemsIndexed(s.files, key = { index, f -> "$index-${f.name}" }) { _, file ->
                FileCard(filename = file.name, content = file.content, language = null)
            }
        }
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
