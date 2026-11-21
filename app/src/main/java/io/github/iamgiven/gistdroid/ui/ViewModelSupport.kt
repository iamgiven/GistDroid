package io.github.iamgiven.gistdroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.iamgiven.gistdroid.AppContainer
import io.github.iamgiven.gistdroid.GistDroidApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as GistDroidApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
