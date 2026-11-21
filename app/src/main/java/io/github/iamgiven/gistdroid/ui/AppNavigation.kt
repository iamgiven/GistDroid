package io.github.iamgiven.gistdroid.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import io.github.iamgiven.gistdroid.ui.components.AppNavigationBar
import io.github.iamgiven.gistdroid.ui.components.NavItem
import io.github.iamgiven.gistdroid.ui.editor.EditorScreen
import io.github.iamgiven.gistdroid.ui.viewer.CodeViewerScreen
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
    const val EDITOR = "editor/{target}/{id}?file={file}"
    const val VIEWER = "viewer"

    fun gist(id: String) = "gist/$id"
    fun snippet(id: Long) = "snippet/$id"
    fun editor(target: EditorTarget, id: String?, file: String? = null) =
        "editor/${target.name}/${id ?: NEW}" + (file?.let { "?file=" + Uri.encode(it) } ?: "")
    const val NEW = "new"
}

private val topLevels = listOf(
    NavItem(Routes.GISTS, "Gist", Icons.Outlined.Cloud, Icons.Filled.Cloud),
    NavItem(Routes.LOCAL, "Local", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    NavItem(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
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

    // Text shared from other apps → open the local snippet editor.
    val container = appContainer()
    val pendingShare by container.pendingShare.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) nav.navigate(Routes.editor(EditorTarget.LOCAL, null))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                AppNavigationBar(topLevels, currentRoute) { item ->
                    if (item.route != currentRoute) nav.navigateTopLevel(item.route)
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.GISTS,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
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
                    onViewFile = { container.viewerFile.value = it; nav.navigate(Routes.VIEWER) },
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
                    onViewFile = { container.viewerFile.value = it; nav.navigate(Routes.VIEWER) },
                )
            }
            composable(Routes.VIEWER) {
                CodeViewerScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { source, filename ->
                        // Replace the viewer with the editor, so saving returns to the refreshed detail screen.
                        nav.popBackStack()
                        nav.navigate(
                            Routes.editor(if (source.isGist) EditorTarget.GIST else EditorTarget.LOCAL, source.id, filename)
                        )
                    },
                )
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("file") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val target = EditorTarget.valueOf(entry.arguments?.getString("target") ?: EditorTarget.LOCAL.name)
                val id = entry.arguments?.getString("id")?.takeIf { it != Routes.NEW }
                EditorScreen(
                    initialFile = entry.arguments?.getString("file"),
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
