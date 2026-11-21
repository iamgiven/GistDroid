package io.github.iamgiven.gistdroid.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.iamgiven.gistdroid.AppContainer
import io.github.iamgiven.gistdroid.data.Backup
import io.github.iamgiven.gistdroid.data.ImportException
import io.github.iamgiven.gistdroid.data.remote.friendlyMessage
import io.github.iamgiven.gistdroid.ui.appViewModel
import io.github.iamgiven.gistdroid.ui.components.CardShape
import io.github.iamgiven.gistdroid.ui.components.cardBorder
import io.github.iamgiven.gistdroid.ui.components.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BackupState(
    /** Non-null while working; shown in a progress dialog. */
    val progress: String? = null,
    val fraction: Float? = null,
    /** A backup read from disk, waiting for the user to confirm the import. */
    val pending: Backup? = null,
    val message: String? = null,
)

class BackupViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    val localCount: StateFlow<Int> = c.snippetDao.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun export(uri: Uri, includeLocal: Boolean, includeGists: Boolean) = work("Exporting…") {
        val backup = c.backup.export(uri, includeLocal, includeGists) { done, total ->
            _state.update {
                it.copy(progress = "Downloading gists $done / $total…", fraction = if (total == 0) null else done / total.toFloat())
            }
        }
        val parts = listOfNotNull(
            if (includeLocal) "${backup.snippets.size} snippets" else null,
            if (includeGists) "${backup.gists.size} gists" else null,
        )
        "Exported ${parts.joinToString(" and ")}"
    }

    fun read(uri: Uri) = work("Reading backup…") {
        val backup = c.backup.read(uri)
        _state.update { it.copy(pending = backup) }
        null
    }

    fun confirmImport() {
        val backup = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        work("Importing…") {
            val r = c.backup.import(backup)
            buildString {
                append("Imported ${r.imported} ${if (r.imported == 1) "snippet" else "snippets"}")
                if (r.skipped > 0) append(", skipped ${r.skipped} already saved")
            }
        }
    }

    fun cancelImport() = _state.update { it.copy(pending = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun work(label: String, block: suspend () -> String?) {
        if (_state.value.progress != null) return
        viewModelScope.launch {
            _state.update { it.copy(progress = label, fraction = null) }
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                e.message
            } catch (e: Exception) {
                e.friendlyMessage()
            }
            _state.update { it.copy(progress = null, fraction = null, message = message) }
        }
    }
}

/** "Backup" card in Settings: export to / import from a JSON file. */
@Composable
fun BackupSection(loggedIn: Boolean) {
    val vm = appViewModel { BackupViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val localCount by vm.localCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var exportDialog by rememberSaveable { mutableStateOf(false) }
    var includeLocal by rememberSaveable { mutableStateOf(true) }
    var includeGists by rememberSaveable { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri, includeLocal, includeGists && loggedIn)
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.read(uri)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            context.toast(it)
            vm.consumeMessage()
        }
    }

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text("Export backup") },
            supportingContent = { Text("Save local snippets and your gists to a JSON file") },
            leadingContent = { Icon(Icons.Outlined.FileUpload, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { exportDialog = true },
        )
        HorizontalDivider(
            Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        ListItem(
            headlineContent = { Text("Import backup") },
            supportingContent = { Text("Restore from a GistDroid backup file") },
            leadingContent = { Icon(Icons.Outlined.FileDownload, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable {
                openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
        )
    }

    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { exportDialog = false },
            icon = { Icon(Icons.Outlined.FileUpload, null) },
            title = { Text("Export backup") },
            text = {
                Column {
                    Text("Choose what to include:")
                    Spacer(Modifier.height(8.dp))
                    CheckRow("Local snippets ($localCount)", includeLocal, enabled = true) { includeLocal = it }
                    CheckRow(
                        if (loggedIn) "My gists (downloaded from GitHub)" else "My gists (sign in first)",
                        includeGists && loggedIn,
                        enabled = loggedIn,
                    ) { includeGists = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = includeLocal || (includeGists && loggedIn),
                    onClick = {
                        exportDialog = false
                        createDocument.launch("gistdroid-backup-${LocalDate.now()}.json")
                    },
                ) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { exportDialog = false }) { Text("Cancel") } },
        )
    }

    state.pending?.let { backup ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            icon = { Icon(Icons.Outlined.FileDownload, null) },
            title = { Text("Import backup?") },
            text = {
                val parts = listOfNotNull(
                    backup.snippets.size.takeIf { it > 0 }?.let { "$it local ${if (it == 1) "snippet" else "snippets"}" },
                    backup.gists.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "gist" else "gists"}" },
                )
                Text(
                    if (parts.isEmpty()) "This backup is empty."
                    else "Found ${parts.joinToString(" and ")}. Everything is added to your local snippets; " +
                        "items you already have are skipped. Gists stay linked to GitHub."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = backup.snippets.isNotEmpty() || backup.gists.isNotEmpty(),
                    onClick = vm::confirmImport,
                ) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("Cancel") } },
        )
    }

    state.progress?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(label) },
            text = {
                val f = state.fraction
                if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
