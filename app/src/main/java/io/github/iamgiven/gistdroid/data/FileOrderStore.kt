package io.github.iamgiven.gistdroid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.fileOrderStore by preferencesDataStore(name = "file_order")

/**
 * GitHub always returns gist files sorted by name and has no API to change that, so the
 * custom file order of each gist is kept on the device (gist id -> file names).
 */
class FileOrderStore(private val context: Context) {

    val orders: Flow<Map<String, List<String>>> = context.fileOrderStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) -> (value as? String)?.let { key.name to it.split('\n') } }.toMap()
    }.distinctUntilChanged()

    fun order(gistId: String): Flow<List<String>?> = orders.map { it[gistId] }.distinctUntilChanged()

    suspend fun set(gistId: String, names: List<String>) {
        context.fileOrderStore.edit { it[stringPreferencesKey(gistId)] = names.joinToString("\n") }
    }

    suspend fun remove(gistId: String) {
        context.fileOrderStore.edit { it.remove(stringPreferencesKey(gistId)) }
    }
}

/** Sorts [names] by the saved [order]; names not in the order keep their relative position at the end. */
fun applyOrder(names: Collection<String>, order: List<String>?): List<String> {
    if (order.isNullOrEmpty()) return names.toList()
    val known = order.filter { it in names }
    return known + names.filter { it !in known }
}
