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
import io.github.iamgiven.gistdroid.ui.components.MetaChip
import io.github.iamgiven.gistdroid.ui.components.MonoStyle
import io.github.iamgiven.gistdroid.ui.components.SearchField
import io.github.iamgiven.gistdroid.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalListScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val vm = appViewModel { LocalListViewModel(it) }
    val snippets by vm.snippets.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Local Snippets", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scrollBehavior,
            )
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
            SearchField(
                query, { vm.query.value = it }, "Search title, file name, or content…",
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                snippet.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = snippet.files.firstOrNull()?.content?.trim()?.lineSequence()?.take(3)?.joinToString("\n")
            if (!preview.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        preview,
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (snippet.gistId != null) MetaChip(Icons.Outlined.CloudDone, "On Gist")
                else MetaChip(Icons.Outlined.PhoneAndroid, "Local")
                MetaChip(Icons.Outlined.Description, "${snippet.files.size} file")
                MetaChip(Icons.Outlined.Schedule, relativeTime(snippet.updatedAt))
            }
        }
    }
}
