package io.github.iamgiven.gistdroid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.iamgiven.gistdroid.ui.editor.EditorScreen
import io.github.iamgiven.gistdroid.ui.editor.EditorTarget
import io.github.iamgiven.gistdroid.ui.gists.GistDetailScreen
import io.github.iamgiven.gistdroid.ui.gists.GistListScreen
import io.github.iamgiven.gistdroid.ui.local.LocalDetailScreen
import io.github.iamgiven.gistdroid.ui.local.LocalListScreen
import io.github.iamgiven.gistdroid.ui.settings.SettingsScreen

private object Routes {
    const val GISTS = "gists"
    const val LOCAL = "local"
    const val SETTINGS = "settings"
    const val GIST_DETAIL = "gist/{id}"
    const val LOCAL_DETAIL = "snippet/{id}"
    const val EDITOR = "editor/{target}/{id}"

    fun gist(id: String) = "gist/$id"
    fun snippet(id: Long) = "snippet/$id"
    fun editor(target: EditorTarget, id: String?) = "editor/${target.name}/${id ?: NEW}"
    const val NEW = "new"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val topLevels = listOf(
    TopLevel(Routes.GISTS, "Gist", Icons.Outlined.Cloud, Icons.Filled.Cloud),
    TopLevel(Routes.LOCAL, "Lokal", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    TopLevel(Routes.SETTINGS, "Setelan", Icons.Outlined.Settings, Icons.Filled.Settings),
)

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun GistDroidNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute == null || topLevels.any { it.route == currentRoute }

    // Teks yang dibagikan dari aplikasi lain → buka editor snippet lokal.
    val container = appContainer()
    val pendingShare by container.pendingShare.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) nav.navigate(Routes.editor(EditorTarget.LOCAL, null))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(showBar, enter = expandVertically(), exit = shrinkVertically()) {
                NavigationBar {
                    topLevels.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) nav.navigateTopLevel(item.route) },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.GISTS,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.GISTS) {
                GistListScreen(
                    onOpen = { nav.navigate(Routes.gist(it)) },
                    onCreate = { nav.navigate(Routes.editor(EditorTarget.GIST, null)) },
                    onGoToSettings = { nav.navigateTopLevel(Routes.SETTINGS) },
                )
            }
            composable(Routes.LOCAL) {
                LocalListScreen(
                    onOpen = { nav.navigate(Routes.snippet(it)) },
                    onCreate = { nav.navigate(Routes.editor(EditorTarget.LOCAL, null)) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.GIST_DETAIL) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                GistDetailScreen(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editor(EditorTarget.GIST, id)) },
                    onOpenLocal = { nav.navigate(Routes.snippet(it)) },
                )
            }
            composable(
                Routes.LOCAL_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                LocalDetailScreen(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editor(EditorTarget.LOCAL, id.toString())) },
                    onOpenGist = { nav.navigate(Routes.gist(it)) },
                )
            }
            composable(Routes.EDITOR) { entry ->
                val target = EditorTarget.valueOf(entry.arguments?.getString("target") ?: EditorTarget.LOCAL.name)
                val id = entry.arguments?.getString("id")?.takeIf { it != Routes.NEW }
                EditorScreen(
                    target = target,
                    id = id,
                    onClose = { nav.popBackStack() },
                    onSaved = { savedId, isNew ->
                        nav.popBackStack()
                        if (isNew) {
                            when (target) {
                                EditorTarget.GIST -> nav.navigate(Routes.gist(savedId))
                                EditorTarget.LOCAL -> nav.navigate(Routes.snippet(savedId.toLong()))
                            }
                        }
                    },
                )
            }
        }
    }
}
