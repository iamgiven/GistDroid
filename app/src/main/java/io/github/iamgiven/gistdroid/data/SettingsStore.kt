package io.github.iamgiven.gistdroid.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    /** Legacy plaintext token from v1.0.2 and earlier; migrated on startup. */
    private val legacyTokenKey = stringPreferencesKey("token")
    /** Token encrypted with [TokenCipher] (Android Keystore). */
    private val tokenKey = stringPreferencesKey("token_enc")
    private val loginKey = stringPreferencesKey("login")
    private val avatarKey = stringPreferencesKey("avatar_url")
    private val themeKey = stringPreferencesKey("theme")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val wrapKey = booleanPreferencesKey("wrap_code")

    @Volatile
    private var decrypted: Pair<String, String?>? = null

    private fun decryptCached(encrypted: String): String? {
        decrypted?.let { (enc, plain) -> if (enc == encrypted) return plain }
        return TokenCipher.decrypt(encrypted).also { decrypted = encrypted to it }
    }

    val token: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[tokenKey]?.let(::decryptCached)?.takeIf { it.isNotBlank() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val login: Flow<String?> = context.dataStore.data
        .map { prefs -> if (prefs[tokenKey] != null) prefs[loginKey] else null }
        .distinctUntilChanged()

    val avatarUrl: Flow<String?> = context.dataStore.data
        .map { prefs -> if (prefs[tokenKey] != null) prefs[avatarKey] else null }
        .distinctUntilChanged()

    suspend fun setAvatarUrl(url: String?) {
        context.dataStore.edit { if (url == null) it.remove(avatarKey) else it[avatarKey] = url }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }.distinctUntilChanged()

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[dynamicKey] ?: true }.distinctUntilChanged()

    suspend fun setAccount(token: String, login: String) {
        val encrypted = withContext(Dispatchers.Default) { TokenCipher.encrypt(token) }
        context.dataStore.edit {
            it[tokenKey] = encrypted
            it[loginKey] = login
            it.remove(legacyTokenKey)
        }
    }

    suspend fun clearAccount() {
        context.dataStore.edit {
            it.remove(tokenKey)
            it.remove(legacyTokenKey)
            it.remove(loginKey)
            it.remove(avatarKey)
        }
        decrypted = null
        withContext(Dispatchers.Default) { TokenCipher.deleteKey() }
    }

    /** Encrypts a plaintext token left over from an older version. */
    suspend fun migrateLegacyToken() {
        val prefs = context.dataStore.data.first()
        val legacy = prefs[legacyTokenKey] ?: return
        val encrypted = withContext(Dispatchers.Default) { TokenCipher.encrypt(legacy) }
        context.dataStore.edit {
            it[tokenKey] = encrypted
            it.remove(legacyTokenKey)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    val wrapCode: Flow<Boolean> = context.dataStore.data.map { it[wrapKey] ?: false }.distinctUntilChanged()

    suspend fun setWrapCode(enabled: Boolean) {
        context.dataStore.edit { it[wrapKey] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicKey] = enabled }
    }
}
