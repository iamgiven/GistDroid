package io.github.iamgiven.gistdroid.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.iamgiven.gistdroid.AppContainer
import io.github.iamgiven.gistdroid.data.ThemeMode
import io.github.iamgiven.gistdroid.data.remote.friendlyMessage
import io.github.iamgiven.gistdroid.ui.appViewModel
import io.github.iamgiven.gistdroid.ui.components.SectionLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectState(val verifying: Boolean = false, val error: String? = null)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val login: StateFlow<String?> = c.settings.login.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val theme: StateFlow<ThemeMode> = c.settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val dynamic: StateFlow<Boolean> = c.settings.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _connect = MutableStateFlow(ConnectState())
    val connect: StateFlow<ConnectState> = _connect.asStateFlow()

    fun connect(rawToken: String, onSuccess: () -> Unit) {
        val token = rawToken.trim()
        if (token.isEmpty()) {
            _connect.value = ConnectState(error = "Token masih kosong")
            return
        }
        viewModelScope.launch {
            _connect.value = ConnectState(verifying = true)
            try {
                val user = c.api.getUser(token)
                c.settings.setAccount(token, user.login)
                _connect.value = ConnectState()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connect.value = ConnectState(error = e.friendlyMessage())
            }
        }
    }

    fun logout() = viewModelScope.launch { c.settings.clearAccount() }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setThemeMode(mode) }
    fun setDynamic(enabled: Boolean) = viewModelScope.launch { c.settings.setDynamicColor(enabled) }
    fun clearError() = _connect.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val login by vm.login.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val dynamic by vm.dynamic.collectAsStateWithLifecycle()
    val connect by vm.connect.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Setelan", fontWeight = FontWeight.SemiBold) }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item { SectionLabel("Akun GitHub") }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    val user = login
                    if (user != null) {
                        ListItem(
                            headlineContent = { Text("@$user", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Terhubung ke GitHub") },
                            leadingContent = { Avatar(user) },
                            trailingContent = {
                                TextButton(onClick = { vm.logout() }) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Keluar")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    } else {
                        TokenForm(
                            verifying = connect.verifying,
                            error = connect.error,
                            onConnect = { vm.connect(it) {} },
                            onTyping = vm::clearError,
                            onCreateToken = {
                                uriHandler.openUri("https://github.com/settings/tokens/new?scopes=gist&description=GistDroid")
                            },
                        )
                    }
                }
            }

            item { SectionLabel("Tampilan") }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text("Tema", style = MaterialTheme.typography.bodyLarge)
                        }
                        val options = listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.LIGHT to "Terang", ThemeMode.DARK to "Gelap")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    selected = theme == mode,
                                    onClick = { vm.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                ) { Text(label) }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Warna dinamis", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Ikuti warna wallpaper (Material You)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = dynamic, onCheckedChange = { vm.setDynamic(it) })
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Tentang") }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("GistDroid") },
                        supportingContent = { Text("Versi $version · klien GitHub Gist") },
                        leadingContent = { Icon(Icons.Outlined.Info, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text("Kode sumber") },
                        supportingContent = { Text("github.com/iamgiven/GistDroid") },
                        leadingContent = { Icon(Icons.Outlined.Code, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                        modifier = Modifier
                            .clickable { uriHandler.openUri("https://github.com/iamgiven/GistDroid") }
                            .padding(bottom = 4.dp),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(login: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                login.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TokenForm(
    verifying: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
    onTyping: () -> Unit,
    onCreateToken: () -> Unit,
) {
    var token by rememberSaveable { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hubungkan dengan token", style = MaterialTheme.typography.titleMedium)
        Text(
            "Buat Personal Access Token (classic) dengan scope \"gist\", lalu tempel di bawah. Token hanya disimpan di HP ini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; onTyping() },
            label = { Text("Personal Access Token") },
            leadingIcon = { Icon(Icons.Outlined.Key, null) },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Tampilkan")
                }
            },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConnect(token) }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCreateToken) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Buat token")
            }
            Button(onClick = { onConnect(token) }, enabled = !verifying && token.isNotBlank()) {
                if (verifying) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Hubungkan")
                }
            }
        }
    }
}
