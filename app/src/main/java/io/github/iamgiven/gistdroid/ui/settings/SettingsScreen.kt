package io.github.iamgiven.gistdroid.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.view.WindowManager
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.iamgiven.gistdroid.ui.components.CardShape
import io.github.iamgiven.gistdroid.ui.components.ListHeader
import io.github.iamgiven.gistdroid.ui.components.SectionLabel
import io.github.iamgiven.gistdroid.ui.components.cardBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    /** Decoded avatar; works with a gist-only token since profile pictures are public. */
    private val _avatar = MutableStateFlow<ImageBitmap?>(null)
    val avatar: StateFlow<ImageBitmap?> = _avatar.asStateFlow()

    init {
        viewModelScope.launch {
            c.settings.login.collect { login ->
                if (login == null) {
                    _avatar.value = null
                    return@collect
                }
                runCatching {
                    // Accounts connected before avatars were stored: look the URL up once.
                    val url = c.settings.avatarUrl.first() ?: c.api.getUser().avatarUrl?.also { c.settings.setAvatarUrl(it) }
                    if (url != null) {
                        val sized = url + (if ('?' in url) "&" else "?") + "s=160"
                        val bytes = c.api.fetchBytes(sized)
                        _avatar.value = withContext(Dispatchers.Default) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                    }
                }
            }
        }
    }
    val theme: StateFlow<ThemeMode> = c.settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val dynamic: StateFlow<Boolean> = c.settings.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val wrapCode: StateFlow<Boolean> = c.settings.wrapCode.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _connect = MutableStateFlow(ConnectState())
    val connect: StateFlow<ConnectState> = _connect.asStateFlow()

    fun connect(rawToken: String, onSuccess: () -> Unit) {
        val token = rawToken.trim()
        if (token.isEmpty()) {
            _connect.value = ConnectState(error = "Token is empty")
            return
        }
        viewModelScope.launch {
            _connect.value = ConnectState(verifying = true)
            try {
                val user = c.api.verifyToken(token)
                c.settings.setAccount(token, user.login)
                c.settings.setAvatarUrl(user.avatarUrl)
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
    fun setWrapCode(enabled: Boolean) = viewModelScope.launch { c.settings.setWrapCode(enabled) }
    fun clearError() = _connect.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val login by vm.login.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val dynamic by vm.dynamic.collectAsStateWithLifecycle()
    val wrapCode by vm.wrapCode.collectAsStateWithLifecycle()
    val connect by vm.connect.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
            title = { Text("Sign out?") },
            text = {
                Text("Your token will be removed from this device. Local snippets are kept. You'll need a token to connect again.")
            },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.logout() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
    }

    Scaffold(topBar = { ListHeader("Settings") }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item { SectionLabel("GitHub account") }
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = cardBorder(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val user = login
                    if (user != null) {
                        ListItem(
                            headlineContent = { Text("@$user", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Connected to GitHub") },
                            leadingContent = { Avatar(user, avatar) },
                            trailingContent = {
                                TextButton(onClick = { confirmSignOut = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sign out")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    } else {
                        TokenForm(
                            verifying = connect.verifying,
                            error = connect.error,
                            onConnect = { entered -> vm.connect(entered) { context.clearClipboardIfEquals(entered) } },
                            onTyping = vm::clearError,
                            onCreateToken = {
                                uriHandler.openUri("https://github.com/settings/tokens/new?scopes=gist&description=GistDroid")
                            },
                        )
                    }
                }
            }

            item { SectionLabel("Appearance") }
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = cardBorder(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        }
                        val options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
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
                            Row(
                                Modifier.toggleable(value = dynamic, role = Role.Switch, onValueChange = { vm.setDynamic(it) }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Dynamic color", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Match your wallpaper colors (Material You)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = dynamic, onCheckedChange = null)
                            }
                        }
                        Row(
                            Modifier.toggleable(value = wrapCode, role = Role.Switch, onValueChange = { vm.setWrapCode(it) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Wrap long lines", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Default word wrap for code (can be toggled per file)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = wrapCode, onCheckedChange = null)
                        }
                    }
                }
            }

            item { SectionLabel("About") }
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = cardBorder(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text("GistDroid") },
                        supportingContent = { Text("Version $version · GitHub Gist client") },
                        leadingContent = { Icon(Icons.Outlined.Info, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text("Source code") },
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
private fun Avatar(login: String, image: ImageBitmap?) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (image != null) {
                Image(image, "@$login", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    login.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
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
    // Plain remember (not rememberSaveable) so the token never ends up in the saved-state Bundle.
    var token by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    SecureWindow()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect with a token", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create a Personal Access Token (classic) with the \"gist\" scope, then paste it below. The token is encrypted and stored only on this device.",
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
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show")
                }
            },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onConnect(token) }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCreateToken) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create token")
            }
            Button(onClick = { onConnect(token) }, enabled = !verifying && token.isNotBlank()) {
                if (verifying) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Connect")
                }
            }
        }
    }
}

/** Blocks screenshots, screen recording and the recents thumbnail while the token form is visible. */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Removes the token from the clipboard after it was pasted, so other apps can't read it later. */
private fun Context.clearClipboardIfEquals(token: String) {
    val cm = getSystemService(ClipboardManager::class.java) ?: return
    runCatching {
        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (clip == token.trim()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
