package io.github.iamgiven.gistdroid.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val loginKey = stringPreferencesKey("login")
    private val themeKey = stringPreferencesKey("theme")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")

    val token: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[tokenKey]?.takeIf { it.isNotBlank() } }
        .distinctUntilChanged()

    val login: Flow<String?> = context.dataStore.data.map { it[loginKey] }.distinctUntilChanged()

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }.distinctUntilChanged()

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[dynamicKey] ?: true }.distinctUntilChanged()

    suspend fun setAccount(token: String, login: String) {
        context.dataStore.edit {
            it[tokenKey] = token
            it[loginKey] = login
        }
    }

    suspend fun clearAccount() {
        context.dataStore.edit {
            it.remove(tokenKey)
            it.remove(loginKey)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicKey] = enabled }
    }
}
