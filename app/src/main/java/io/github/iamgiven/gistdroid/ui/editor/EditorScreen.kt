package io.github.iamgiven.gistdroid.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.iamgiven.gistdroid.ui.components.DragHandleIcon
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.iamgiven.gistdroid.ui.appViewModel
import io.github.iamgiven.gistdroid.ui.components.EmptyState
import io.github.iamgiven.gistdroid.ui.components.HighlightTransformation
import io.github.iamgiven.gistdroid.ui.components.MonoStyle
import io.github.iamgiven.gistdroid.ui.components.rememberCodeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    target: EditorTarget,
    id: String?,
    onClose: () -> Unit,
    onSaved: (savedId: String, isNew: Boolean) -> Unit,
) {
    val vm = appViewModel { EditorViewModel(it, target, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val codeColors = rememberCodeColors()
    val lazyState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(lazyState) { from, to ->
        val f = from.key as? Long ?: return@rememberReorderableLazyListState
        val t = to.key as? Long ?: return@rememberReorderableLazyListState
        vm.moveFile(f, t)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val requestClose: () -> Unit = {
        if (vm.isDirty()) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(enabled = !state.saving) { requestClose() }

    LaunchedEffect(state.savedId) { state.savedId?.let { onSaved(it, vm.isNew) } }
    LaunchedEffect(state.error) {
        val err = state.error
        if (err != null && !state.loadFailed) {
            snackbar.showSnackbar(err)
            vm.clearError()
        }
    }

    val title = when {
        target == EditorTarget.GIST && vm.isNew -> "New gist"
        target == EditorTarget.GIST -> "Edit gist"
        vm.isNew -> "New snippet"
        else -> "Edit snippet"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = requestClose, enabled = !state.saving) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(24.dp), strokeWidth = 2.5.dp)
                    } else if (!state.loading && !state.loadFailed) {
                        Button(onClick = vm::save, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (target == EditorTarget.GIST) "Save to Gist" else "Save")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.loadFailed -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "Couldn't open",
                    message = state.error.orEmpty(),
                ) { Button(onClick = onClose) { Text("Back") } }
                else -> LazyColumn(
                    state = lazyState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().imePadding(),
                ) {
                    item(key = "desc") {
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = vm::setDescription,
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    item(key = "visibility") {
                        val canChange = !(target == EditorTarget.GIST && !vm.isNew)
                        OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                            ListItem(
                                headlineContent = { Text(if (state.isPublic) "Public gist" else "Secret gist") },
                                supportingContent = {
                                    Text(
                                        when {
                                            !canChange -> "Visibility can't be changed after a gist is created"
                                            state.isPublic -> "Discoverable by anyone"
                                            else -> "Only visible to people with the link"
                                        }
                                    )
                                },
                                leadingContent = {
                                    Icon(if (state.isPublic) Icons.Outlined.Public else Icons.Outlined.Lock, null)
                                },
                                trailingContent = {
                                    Switch(checked = state.isPublic, onCheckedChange = vm::setPublic, enabled = canChange)
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                    items(state.files, key = { it.key }) { file ->
                      ReorderableItem(reorderState, key = file.key) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                        OutlinedCard(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.shadow(elevation, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.files.size > 1) {
                                        DragHandleIcon(
                                            Modifier
                                                .padding(end = 4.dp)
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                )
                                        )
                                    }
                                    OutlinedTextField(
                                        value = file.name,
                                        onValueChange = { vm.updateFile(file.key, name = it) },
                                        label = { Text("File name") },
                                        placeholder = { Text("e.g. main.kt") },
                                        leadingIcon = { Icon(Icons.Outlined.Description, null) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            autoCorrectEnabled = false,
                                            keyboardType = KeyboardType.Uri,
                                        ),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    if (state.files.size > 1) {
                                        IconButton(onClick = { vm.removeFile(file.key) }) {
                                            Icon(Icons.Outlined.Delete, "Remove file", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                val transformation = remember(file.name, codeColors) {
                                    HighlightTransformation(file.name, codeColors)
                                }
                                OutlinedTextField(
                                    value = file.content,
                                    onValueChange = { vm.updateFile(file.key, content = it) },
                                    placeholder = { Text("Write or paste code here…", style = MonoStyle) },
                                    textStyle = MonoStyle,
                                    minLines = 8,
                                    visualTransformation = transformation,
                                    keyboardOptions = KeyboardOptions(
                                        autoCorrectEnabled = false,
                                        capitalization = KeyboardCapitalization.None,
                                        keyboardType = KeyboardType.Ascii,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                )
                            }
                        }
                      }
                    }
                    item(key = "add") {
                        OutlinedButton(onClick = vm::addFile, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add file")
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes will be lost.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}
