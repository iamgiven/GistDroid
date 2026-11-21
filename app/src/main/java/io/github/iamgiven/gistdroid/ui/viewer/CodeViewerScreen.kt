package io.github.iamgiven.gistdroid.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.ui.appContainer
import io.github.iamgiven.gistdroid.ui.components.CodeView
import io.github.iamgiven.gistdroid.ui.components.languageFromName
import io.github.iamgiven.gistdroid.ui.components.toast

/** Full-screen, read-only view of a single file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(onBack: () -> Unit) {
    val container = appContainer()
    val file by container.viewerFile.collectAsStateWithLifecycle()
    val wrapDefault by container.settings.wrapCode.collectAsStateWithLifecycle(initialValue = false)
    var wrapOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val wrap = wrapOverride ?: wrapDefault
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val f = file
    if (f == null) {
        // Nothing to show (e.g. the process was restarted) - go back.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val lines = remember(f.content) { f.content.count { it == '\n' } + 1 }
    val subtitle = listOfNotNull(
        f.language ?: languageFromName(f.filename),
        "$lines ${if (lines == 1) "line" else "lines"}",
    ).joinToString(" · ")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(f.filename, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { wrapOverride = !wrap }) {
                        Icon(
                            Icons.AutoMirrored.Filled.WrapText,
                            if (wrap) "Disable word wrap" else "Enable word wrap",
                            tint = if (wrap) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(f.content))
                        context.toast("${f.filename} copied")
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
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
            CodeView(code = f.content, filename = f.filename, wrap = wrap)
        }
    }
}
