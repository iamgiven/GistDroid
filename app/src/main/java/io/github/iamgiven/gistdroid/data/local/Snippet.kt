package io.github.iamgiven.gistdroid.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SnippetFile(val name: String, val content: String)

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val files: List<SnippetFile>,
    val isPublic: Boolean = false,
    val gistId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val title: String
        get() = description.takeIf { it.isNotBlank() } ?: files.firstOrNull()?.name ?: "Tanpa judul"
}

class Converters {
    private val serializer = ListSerializer(SnippetFile.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun filesToString(files: List<SnippetFile>): String = json.encodeToString(serializer, files)

    @TypeConverter
    fun stringToFiles(value: String): List<SnippetFile> =
        runCatching { json.decodeFromString(serializer, value) }.getOrDefault(emptyList())
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE id = :id")
    fun observe(id: Long): Flow<Snippet?>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun get(id: Long): Snippet?

    @Query("SELECT * FROM snippets WHERE gistId = :gistId LIMIT 1")
    suspend fun findByGistId(gistId: String): Snippet?

    @Query("SELECT id FROM snippets WHERE gistId = :gistId LIMIT 1")
    fun observeIdByGistId(gistId: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: Snippet): Long

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Snippet::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snippetDao(): SnippetDao
}
