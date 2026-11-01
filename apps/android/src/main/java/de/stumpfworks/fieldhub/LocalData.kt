package de.stumpfworks.fieldhub

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_events", indices = [Index("workspaceId")])
internal data class LocalEvent(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val scheduleLabel: String,
    val location: String,
    val description: String,
    val type: String,
    @ColumnInfo(defaultValue = "0") val startAt: Long = 0L,
    @ColumnInfo(defaultValue = "'unknown'") val attendance: String = "unknown"
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

@Entity(tableName = "recruiting_contacts", indices = [Index("workspaceId")])
internal data class RecruitingContact(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val name: String,
    val phone: String,
    val email: String,
    val notes: String,
    val status: String,
    val consentToContact: Boolean,
    val consentRecordedAt: Long?,
    val createdAt: Long
)

@Dao
internal interface FieldHubDao {
    @Query("SELECT * FROM recruiting_contacts WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun contacts(workspaceId: String): Flow<List<RecruitingContact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveContact(contact: RecruitingContact)

    @Delete
    suspend fun deleteContact(contact: RecruitingContact)

    @Query("SELECT * FROM local_events WHERE workspaceId = :workspaceId ORDER BY scheduleLabel, title")
    fun events(workspaceId: String): Flow<List<LocalEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEvent(event: LocalEvent)

    @Delete
    suspend fun deleteEvent(event: LocalEvent)

    @Query("UPDATE local_events SET attendance = :status WHERE id = :eventId AND workspaceId = :workspaceId")
    suspend fun setEventAttendance(eventId: String, workspaceId: String, status: String)

    @Query("SELECT * FROM local_tasks WHERE workspaceId = :workspaceId ORDER BY done ASC, title")
    fun tasks(workspaceId: String): Flow<List<LocalTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(task: LocalTask)

    @Delete
    suspend fun deleteTask(task: LocalTask)
}

@Database(entities = [LocalEvent::class, LocalTask::class, RecruitingContact::class], version = 3, exportSchema = false)
internal abstract class FieldHubDatabase : RoomDatabase() {
    abstract fun dao(): FieldHubDao
    companion object {
        private val migration1to2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recruiting_contacts (
                        id TEXT NOT NULL PRIMARY KEY,
                        workspaceId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        email TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        status TEXT NOT NULL,
                        consentToContact INTEGER NOT NULL,
                        consentRecordedAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recruiting_contacts_workspaceId ON recruiting_contacts(workspaceId)")
            }
        }
        private val migration2to3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_events ADD COLUMN startAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_events ADD COLUMN attendance TEXT NOT NULL DEFAULT 'unknown'")
            }
        }
        @Volatile private var instance: FieldHubDatabase? = null
        fun get(context: Context): FieldHubDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, FieldHubDatabase::class.java, "fieldhub-local.db"
                ).addMigrations(migration1to2, migration2to3).build().also { instance = it }
            }
    }
}
