package io.github.iamgiven.gistdroid.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.data.local.Snippet
import io.github.iamgiven.gistdroid.ui.appViewModel
import io.github.iamgiven.gistdroid.ui.components.EmptyState
import io.github.iamgiven.gistdroid.ui.components.ListHeader
import io.github.iamgiven.gistdroid.ui.components.CARD_GAP
import io.github.iamgiven.gistdroid.ui.components.HEADER_GAP
import io.github.iamgiven.gistdroid.ui.components.SnippetCard
import io.github.iamgiven.gistdroid.ui.components.fileCount
import io.github.iamgiven.gistdroid.ui.components.previewOf
import io.github.iamgiven.gistdroid.ui.components.MetaChip
import io.github.iamgiven.gistdroid.ui.components.MonoStyle
import io.github.iamgiven.gistdroid.ui.components.SearchField
import io.github.iamgiven.gistdroid.ui.components.SyntaxHighlighter
import io.github.iamgiven.gistdroid.ui.components.rememberCodeColors
import androidx.compose.runtime.remember
import io.github.iamgiven.gistdroid.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalListScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val vm = appViewModel { LocalListViewModel(it) }
    val snippets by vm.snippets.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            ListHeader("Local Snippets") {
                SearchField(query, { vm.query.value = it }, "Search snippets…")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New snippet") },
                expanded = !listState.canScrollBackward,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val list = snippets
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() && query.isNotBlank() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No results",
                    message = "No snippets match \"$query\"",
                )
                list.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = "No snippets yet",
                    message = "Local snippets live on this device and can be uploaded to Gist anytime. You can also share text from other apps to GistDroid.",
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = HEADER_GAP, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { snippet ->
                        SnippetItem(snippet, onClick = { onOpen(snippet.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetItem(snippet: Snippet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCodeColors()
    val first = snippet.files.firstOrNull()
    val preview = remember(first, colors) { first?.let { previewOf(it.content, it.name, colors) } }
    SnippetCard(
        title = snippet.title,
        icon = if (snippet.gistId != null) Icons.Outlined.CloudDone else Icons.Outlined.PhoneAndroid,
        badge = if (snippet.gistId != null) "On Gist" to true else "Local" to false,
        preview = preview,
        onClick = onClick,
        modifier = modifier,
    ) {
        MetaChip(Icons.Outlined.Description, fileCount(snippet.files.size))
        MetaChip(Icons.Outlined.Schedule, relativeTime(snippet.updatedAt))
    }
}
