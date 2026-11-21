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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.data.remote.Gist
import io.github.iamgiven.gistdroid.ui.appViewModel
import io.github.iamgiven.gistdroid.ui.components.EmptyState
import io.github.iamgiven.gistdroid.ui.components.MetaChip
import io.github.iamgiven.gistdroid.ui.components.SearchField
import io.github.iamgiven.gistdroid.ui.components.parseIso
import io.github.iamgiven.gistdroid.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GistListScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onGoToSettings: () -> Unit,
) {
    val vm = appViewModel { GistListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(state.error, state.gists.isEmpty()) {
        val err = state.error
        if (err != null && state.gists.isNotEmpty()) {
            snackbar.showSnackbar(err)
            vm.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Gist", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.loggedIn == true) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New gist") },
                    expanded = !listState.canScrollBackward,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (state.loggedIn) {
                null -> Unit
                false -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "Not connected",
                    message = "Connect your GitHub account with a Personal Access Token to view and save gists. Local snippets work without an account.",
                ) {
                    Button(onClick = onGoToSettings) { Text("Connect account") }
                }
                true -> {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SearchField(state.query, vm::setQuery, "Search gists…")
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterOption("Mine", state.filter == GistFilter.MINE) { vm.setFilter(GistFilter.MINE) }
                            FilterOption("Starred", state.filter == GistFilter.STARRED) { vm.setFilter(GistFilter.STARRED) }
                        }
                    }
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = vm::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val items = state.visibleGists
                        when {
                            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                            state.error != null && state.gists.isEmpty() -> EmptyState(
                                icon = Icons.Outlined.ErrorOutline,
                                title = "Couldn't load",
                                message = state.error.orEmpty(),
                            ) { Button(onClick = vm::retry) { Text("Retry") } }
                            items.isEmpty() -> EmptyState(
                                icon = if (state.filter == GistFilter.STARRED) Icons.Outlined.StarOutline else Icons.Outlined.Description,
                                title = if (state.query.isNotBlank()) "No results" else "No gists yet",
                                message = when {
                                    state.query.isNotBlank() -> "No gists match \"${state.query}\""
                                    state.filter == GistFilter.STARRED -> "Gists you star will show up here."
                                    else -> "Create your first gist with the button below."
                                },
                            )
                            else -> LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(items, key = { it.id }) { gist ->
                                    GistItem(
                                        gist,
                                        showOwner = state.filter == GistFilter.STARRED,
                                        onClick = { onOpen(gist.id) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                if (!state.endReached && state.query.isBlank()) {
                                    item(key = "loader") {
                                        LaunchedEffect(state.gists.size) { vm.loadMore() }
                                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                            CircularProgressIndicator(Modifier.size(28.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
    )
}

@Composable
private fun GistItem(gist: Gist, showOwner: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                gist.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                gist.files.keys.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetaChip(
                    if (gist.public) Icons.Outlined.Public else Icons.Outlined.Lock,
                    if (gist.public) "Public" else "Secret",
                )
                MetaChip(Icons.Outlined.Description, "${gist.files.size} file")
                MetaChip(Icons.Outlined.Schedule, relativeTime(parseIso(gist.updatedAt)))
            }
            val owner = gist.owner?.login
            if (showOwner && owner != null) {
                Text(
                    "@$owner",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
