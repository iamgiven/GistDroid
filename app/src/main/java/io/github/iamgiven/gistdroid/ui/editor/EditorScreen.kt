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
import androidx.compose.material.icons.outlined.Save
import androidx.compose.runtime.key
import io.github.iamgiven.gistdroid.data.DEFAULT_CODE_FONT_SIZE
import io.github.iamgiven.gistdroid.ui.components.monoStyle
import io.github.iamgiven.gistdroid.ui.components.BadgeTone
import io.github.iamgiven.gistdroid.ui.components.StatusBadge
import io.github.iamgiven.gistdroid.ui.components.tone
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import io.github.iamgiven.gistdroid.ui.appContainer
import io.github.iamgiven.gistdroid.ui.components.CodeEditor
import io.github.iamgiven.gistdroid.ui.components.previewOf
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
    initialFile: String? = null,
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

    // Opened from the code viewer's Edit button: jump straight to that file once it's loaded.
    var initialHandled by rememberSaveable { mutableStateOf(initialFile == null) }
    LaunchedEffect(state.loading, state.files.size) {
        if (!initialHandled && !state.loading && state.files.isNotEmpty()) {
            state.files.firstOrNull { it.name == initialFile }?.let { editingKey = it.key }
            initialHandled = true
        }
    }

    // While a file requested by the viewer is loading, show a blank page instead of the file list.
    if (!initialHandled && state.loading) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow))
        return
    }

    // Full-screen page for a single file (the viewer's file is shown immediately, before the effect above runs).
    val editing = state.files.firstOrNull { it.key == editingKey }
        ?: if (!initialHandled) state.files.firstOrNull { it.name == initialFile } else null
    if (editing != null) {
        key(editing.key) {
            FileEditorPage(
                file = editing,
                onSave = { name, content -> vm.updateFile(editing.key, name = name, content = content) },
                onDelete = if (state.files.size > 1) {
                    {
                        editingKey = null
                        vm.removeFile(editing.key)
                    }
                } else null,
                onClose = {
                    // Drop a freshly added file that was never filled in.
                    val current = vm.state.value.files.firstOrNull { it.key == editing.key }
                    if (current != null && current.name.isBlank() && current.content.isBlank() &&
                        vm.state.value.files.size > 1
                    ) vm.removeFile(editing.key)
                    initialHandled = true
                    editingKey = null
                },
            )
        }
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
                    // Local snippets pick their visibility in the upload dialog instead.
                    if (target == EditorTarget.GIST) item(key = "visibility") {
                        val canChange = vm.isNew
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
                                changeLabel = vm.changeLabel(file),
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

/** Card for one file in the editor's file list: header with actions, then a short preview. */
@Composable
private fun FileRow(
    file: EditableFile,
    changeLabel: String?,
    codeColors: CodeColors,
    dragging: Boolean,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    dragHandle: (@Composable () -> Unit)?,
) {
    val lines = remember(file.content) { if (file.content.isEmpty()) 0 else file.content.count { it == '\n' } + 1 }
    val preview = remember(file.content, file.name, codeColors) { previewOf(file.content, file.name, codeColors) }
    val shape = RoundedCornerShape(24.dp)
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (dragging) Modifier.shadow(8.dp, shape) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = cardBorder(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = if (dragHandle != null) 4.dp else 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dragHandle != null) {
                dragHandle()
            } else {
                Icon(
                    Icons.Outlined.Description, null,
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.name.ifBlank { "Untitled file" },
                        style = MaterialTheme.typography.titleSmall,
                        fontStyle = if (file.name.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (changeLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(changeLabel tone BadgeTone.BLUE, compact = true)
                    }
                }
                Text(
                    listOfNotNull(
                        languageFromName(file.name),
                        if (lines == 0) "Empty" else "$lines ${if (lines == 1) "line" else "lines"}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Edit, "Edit file", Modifier.size(20.dp))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Delete, "Remove file", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (preview != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Text(
                preview,
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Full-screen page that edits one file, styled like the full-screen code viewer.
 * Edits are kept in a draft until saved with the floppy button; leaving with unsaved changes asks
 * whether to save, discard or keep editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileEditorPage(
    file: EditableFile,
    onSave: (name: String, content: String) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(file.name) }
    var content by remember { mutableStateOf(file.content) }
    val dirty = name != file.name || content != file.content
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val container = appContainer()
    val wrapDefault by container.settings.wrapCode.collectAsStateWithLifecycle(initialValue = false)
    val fontSize by container.settings.codeFontSize.collectAsStateWithLifecycle(initialValue = DEFAULT_CODE_FONT_SIZE)
    var wrapOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val wrap = wrapOverride ?: wrapDefault
    val nameFocus = remember { FocusRequester() }
    val lines = remember(content) { content.count { it == '\n' } + 1 }
    LaunchedEffect(Unit) { if (file.name.isBlank() && file.content.isBlank()) nameFocus.requestFocus() }

    val leave: () -> Unit = { if (dirty) confirmLeave = true else onClose() }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                            decorationBox = { inner ->
                                Box {
                                    if (name.isEmpty()) {
                                        Text(
                                            "File name (e.g. main.kt)",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        Text(
                            listOfNotNull(
                                languageFromName(name),
                                "$lines ${if (lines == 1) "line" else "lines"}",
                                if (dirty) "Unsaved" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { wrapOverride = !wrap }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.WrapText,
                            if (wrap) "Disable word wrap" else "Enable word wrap",
                            tint = if (wrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Delete, "Remove file", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = { onSave(name, content) }, enabled = dirty) {
                        Icon(Icons.Outlined.Save, "Save file")
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
                .imePadding()
        ) {
            CodeEditor(
                value = content,
                onValueChange = { content = it },
                filename = name,
                wrap = wrap,
                style = monoStyle(fontSize),
            )
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            icon = { Icon(Icons.Outlined.Save, null) },
            title = { Text("Save changes?") },
            text = { Text("\"${name.ifBlank { "Untitled file" }}\" has unsaved changes.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onSave(name, content)
                    onClose()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        confirmLeave = false
                        onClose()
                    }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Remove file?") },
            text = { Text("\"${name.ifBlank { "Untitled file" }}\" will be removed.") },
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
