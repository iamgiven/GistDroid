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
import io.github.iamgiven.gistdroid.data.remote.Gist
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
) {
    val vm = appViewModel { GistDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val localId by vm.localId.collectAsStateWithLifecycle()
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
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                    },
                    actions = {
                        if (gist != null) {
                            IconButton(onClick = vm::toggleStar, enabled = !state.busy) {
                                Icon(
                                    if (state.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    "Bintang",
                                    tint = if (state.starred) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isOwner) {
                                IconButton(onClick = onEdit, enabled = !state.busy) { Icon(Icons.Default.Edit, "Edit") }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Lainnya") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (localId != null) "Perbarui salinan lokal" else "Simpan ke lokal") },
                                        leadingIcon = { Icon(Icons.Default.Download, null) },
                                        onClick = { menuOpen = false; vm.saveLocal() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Buka di browser") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                        onClick = { menuOpen = false; uriHandler.openUri(gist.htmlUrl) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Bagikan tautan") },
                                        leadingIcon = { Icon(Icons.Default.Share, null) },
                                        onClick = { menuOpen = false; context.shareText(gist.htmlUrl, gist.title) },
                                    )
                                    if (isOwner) {
                                        DropdownMenuItem(
                                            text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
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
                    title = "Gagal memuat gist",
                    message = state.error.orEmpty(),
                ) { Button(onClick = vm::load) { Text("Coba lagi") } }
                else -> GistContent(gist, localId, onOpenLocal)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Hapus gist?") },
            text = { Text("Gist akan dihapus permanen dari GitHub. Salinan lokal (jika ada) tidak ikut terhapus.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun GistContent(gist: Gist, localId: Long?, onOpenLocal: (Long) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!gist.description.isNullOrBlank()) {
                    Text(gist.description, style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(
                        if (gist.public) Icons.Outlined.Public else Icons.Outlined.Lock,
                        if (gist.public) "Publik" else "Rahasia",
                    )
                    MetaChip(Icons.Outlined.Description, "${gist.files.size} file")
                    MetaChip(Icons.Outlined.Schedule, relativeTime(parseIso(gist.updatedAt)))
                }
                if (localId != null) {
                    TextButton(
                        onClick = { onOpenLocal(localId) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(16.dp))
                        Text("  Ada salinan lokal — buka")
                    }
                }
            }
        }
        items(gist.files.entries.toList(), key = { it.key }) { (name, file) ->
            FileCard(filename = name, content = file.content.orEmpty(), language = file.language)
        }
    }
}
