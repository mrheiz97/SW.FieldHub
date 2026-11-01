package de.stumpfworks.fieldhub

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_events", indices = [Index("workspaceId")])
internal data class LocalEvent(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val scheduleLabel: String,
    val location: String,
    val description: String,
    val type: String
)

@Entity(tableName = "local_tasks", indices = [Index("workspaceId")])
internal data class LocalTask(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val description: String,
    val priority: String,
    val done: Boolean = false
)

@Dao
internal interface FieldHubDao {
    @Query("SELECT * FROM local_events WHERE workspaceId = :workspaceId ORDER BY scheduleLabel, title")
    fun events(workspaceId: String): Flow<List<LocalEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEvent(event: LocalEvent)

    @Delete
    suspend fun deleteEvent(event: LocalEvent)

    @Query("SELECT * FROM local_tasks WHERE workspaceId = :workspaceId ORDER BY done ASC, title")
    fun tasks(workspaceId: String): Flow<List<LocalTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(task: LocalTask)

    @Delete
    suspend fun deleteTask(task: LocalTask)
}

@Database(entities = [LocalEvent::class, LocalTask::class], version = 1, exportSchema = false)
internal abstract class FieldHubDatabase : RoomDatabase() {
    abstract fun dao(): FieldHubDao
    companion object {
        @Volatile private var instance: FieldHubDatabase? = null
        fun get(context: Context): FieldHubDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, FieldHubDatabase::class.java, "fieldhub-local.db"
                ).build().also { instance = it }
            }
    }
}
