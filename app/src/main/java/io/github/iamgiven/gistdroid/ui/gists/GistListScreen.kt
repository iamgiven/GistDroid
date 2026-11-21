package io.github.iamgiven.gistdroid.ui.gists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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
import io.github.iamgiven.gistdroid.ui.components.ListHeader
import io.github.iamgiven.gistdroid.ui.components.CARD_GAP
import io.github.iamgiven.gistdroid.ui.components.HEADER_GAP
import androidx.compose.material.icons.outlined.Code
import io.github.iamgiven.gistdroid.ui.components.SkeletonCard
import io.github.iamgiven.gistdroid.ui.components.SnippetCard
import io.github.iamgiven.gistdroid.ui.components.fileCount
import io.github.iamgiven.gistdroid.ui.components.previewOf
import io.github.iamgiven.gistdroid.ui.components.rememberCodeColors
import androidx.compose.material.icons.outlined.Person
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
    val previews by vm.previews.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Filter chips hide while scrolling down and come back when scrolling up (or at the top).
    var chipsVisible by rememberSaveable { mutableStateOf(true) }
    val chipsScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) chipsVisible = false
                else if (available.y > 6f) chipsVisible = true
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState.canScrollBackward) { if (!listState.canScrollBackward) chipsVisible = true }
    LaunchedEffect(state.filter) { chipsVisible = true }

    LaunchedEffect(state.error, state.gists.isEmpty()) {
        val err = state.error
        if (err != null && state.gists.isNotEmpty()) {
            snackbar.showSnackbar(err)
            vm.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(chipsScroll),
        topBar = {
            ListHeader("Gists") {
                if (state.loggedIn == true) {
                    SearchField(state.query, vm::setQuery, "Search gists…")
                    AnimatedVisibility(
                        visible = chipsVisible,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterOption("My gists", state.filter == GistFilter.MINE) { vm.setFilter(GistFilter.MINE) }
                            FilterOption("Starred", state.filter == GistFilter.STARRED) { vm.setFilter(GistFilter.STARRED) }
                        }
                    }
                }
            }
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
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = vm::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val items = state.visibleGists
                        when {
                            state.loading -> LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = HEADER_GAP, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                                userScrollEnabled = false,
                                modifier = Modifier.fillMaxSize(),
                            ) { items(6) { SkeletonCard() } }
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
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = HEADER_GAP, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(items, key = { it.id }) { gist ->
                                    val url = vm.previewSource(gist)?.second
                                    LaunchedEffect(url) { vm.loadPreview(gist) }
                                    val preview = url?.let { previews[it] ?: vm.cachedPreview(gist) }
                                    GistItem(
                                        gist,
                                        preview = preview,
                                        loadingPreview = url != null && preview == null && url !in previews,
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
private fun GistItem(
    gist: Gist,
    preview: FilePreview?,
    loadingPreview: Boolean,
    showOwner: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCodeColors()
    val highlighted = remember(preview, colors) { preview?.let { previewOf(it.content, it.filename, colors) } }
    val owner = gist.owner?.login
    SnippetCard(
        title = gist.title,
        icon = Icons.Outlined.Code,
        badge = if (gist.public) "Public" to true else "Secret" to false,
        preview = highlighted,
        previewPlaceholder = loadingPreview,
        onClick = onClick,
        modifier = modifier,
    ) {
        MetaChip(Icons.Outlined.Description, fileCount(gist.files.size))
        if (showOwner && owner != null) MetaChip(Icons.Outlined.Person, owner)
        else MetaChip(Icons.Outlined.Schedule, relativeTime(parseIso(gist.updatedAt)))
    }
}
