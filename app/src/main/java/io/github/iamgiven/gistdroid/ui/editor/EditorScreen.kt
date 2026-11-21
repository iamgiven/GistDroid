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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.ui.appViewModel
import io.github.iamgiven.gistdroid.ui.components.CodeColors
import io.github.iamgiven.gistdroid.ui.components.DragHandleIcon
import io.github.iamgiven.gistdroid.ui.components.EmptyState
import io.github.iamgiven.gistdroid.ui.components.HighlightTransformation
import io.github.iamgiven.gistdroid.ui.components.MonoStyle
import io.github.iamgiven.gistdroid.ui.components.SyntaxHighlighter
import io.github.iamgiven.gistdroid.ui.components.cardBorder
import io.github.iamgiven.gistdroid.ui.components.languageFromName
import io.github.iamgiven.gistdroid.ui.components.rememberCodeColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Editor for a snippet or gist. The main page edits the description, visibility and the list of
 * files; each file is edited on its own full-screen page so long files don't get in the way.
 */
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
    var editingKey by rememberSaveable { mutableStateOf<Long?>(null) }
    val codeColors = rememberCodeColors()
    val lazyState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(lazyState) { from, to ->
        val f = from.key as? Long ?: return@rememberReorderableLazyListState
        val t = to.key as? Long ?: return@rememberReorderableLazyListState
        vm.moveFile(f, t)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LaunchedEffect(state.savedId) { state.savedId?.let { onSaved(it, vm.isNew) } }
    LaunchedEffect(state.error) {
        val err = state.error
        if (err != null && !state.loadFailed) {
            snackbar.showSnackbar(err)
            vm.clearError()
        }
    }

    // Full-screen page for a single file.
    val editing = state.files.firstOrNull { it.key == editingKey }
    if (editing != null) {
        FileEditorPage(
            file = editing,
            codeColors = codeColors,
            onNameChange = { vm.updateFile(editing.key, name = it) },
            onContentChange = { vm.updateFile(editing.key, content = it) },
            onDelete = if (state.files.size > 1) {
                {
                    editingKey = null
                    vm.removeFile(editing.key)
                }
            } else null,
            onDone = { editingKey = null },
        )
        return
    }

    val requestClose: () -> Unit = {
        if (vm.isDirty()) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(enabled = !state.saving) { requestClose() }

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
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = cardBorder(),
                        ) {
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
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                    item(key = "filesLabel") {
                        Text(
                            "Files",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    items(state.files, key = { it.key }) { file ->
                        ReorderableItem(reorderState, key = file.key) { isDragging ->
                            FileRow(
                                file = file,
                                codeColors = codeColors,
                                dragging = isDragging,
                                onOpen = { editingKey = file.key },
                                onDelete = if (state.files.size > 1) {
                                    { vm.removeFile(file.key) }
                                } else null,
                                dragHandle = if (state.files.size > 1) {
                                    {
                                        DragHandleIcon(
                                            Modifier.draggableHandle(
                                                onDragStarted = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                            )
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                    item(key = "add") {
                        OutlinedButton(
                            onClick = { editingKey = vm.addFile() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
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

/** Compact row for one file in the editor's file list. */
@Composable
private fun FileRow(
    file: EditableFile,
    codeColors: CodeColors,
    dragging: Boolean,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    dragHandle: (@Composable () -> Unit)?,
) {
    val lines = remember(file.content) { if (file.content.isEmpty()) 0 else file.content.count { it == '\n' } + 1 }
    val firstLine = remember(file.content, file.name, codeColors) {
        file.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?.let { SyntaxHighlighter.highlight(it, file.name, codeColors) }
    }
    val shape = RoundedCornerShape(24.dp)
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().shadow(if (dragging) 8.dp else 0.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = cardBorder(),
    ) {
        Row(
            Modifier.padding(start = if (dragHandle != null) 4.dp else 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dragHandle?.invoke()
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Text(
                    file.name.ifBlank { "Untitled file" },
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = if (file.name.isBlank()) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        languageFromName(file.name),
                        if (lines == 0) "Empty" else "$lines ${if (lines == 1) "line" else "lines"}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (firstLine != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        firstLine,
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onOpen) { Icon(Icons.Outlined.Edit, "Edit file") }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, "Remove file", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Full-screen page that edits a single file's name and content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileEditorPage(
    file: EditableFile,
    codeColors: CodeColors,
    onNameChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(file.name.ifBlank { "New file" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, "Remove file", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = onDone, modifier = Modifier.padding(end = 4.dp)) { Text("Done") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = file.name,
                onValueChange = onNameChange,
                label = { Text("File name") },
                placeholder = { Text("e.g. main.kt") },
                leadingIcon = { Icon(Icons.Outlined.Description, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(12.dp))
            val transformation = remember(file.name, codeColors) { HighlightTransformation(file.name, codeColors) }
            OutlinedTextField(
                value = file.content,
                onValueChange = onContentChange,
                placeholder = { Text("Write or paste code here…", style = MonoStyle) },
                textStyle = MonoStyle,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                ),
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Remove file?") },
            text = { Text("\"${file.name.ifBlank { "Untitled file" }}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
