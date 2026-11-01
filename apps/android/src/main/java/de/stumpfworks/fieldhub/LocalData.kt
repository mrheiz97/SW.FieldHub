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
    val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val consentSignature: String = ""
)

@Entity(tableName = "recruiting_activities", indices = [Index("contactId")])
internal data class RecruitingActivity(
    @PrimaryKey val id: String,
    val contactId: String,
    val text: String,
    val createdAt: Long
)

@Entity(tableName = "recruiting_followups", indices = [Index("contactId")])
internal data class RecruitingFollowup(
    @PrimaryKey val id: String,
    val contactId: String,
    val title: String,
    val dueAt: Long,
    val done: Boolean = false
)

@Dao
internal interface FieldHubDao {
    @Query("SELECT * FROM recruiting_followups WHERE contactId = :contactId ORDER BY done ASC, dueAt ASC")
    fun followups(contactId: String): Flow<List<RecruitingFollowup>>

    @Query("SELECT * FROM recruiting_followups ORDER BY done ASC, dueAt ASC")
    fun allFollowups(): Flow<List<RecruitingFollowup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFollowup(followup: RecruitingFollowup)

    @Query("UPDATE recruiting_followups SET done = :done WHERE id = :id")
    suspend fun setFollowupDone(id: String, done: Boolean)

    @Query("DELETE FROM recruiting_followups WHERE contactId = :contactId")
    suspend fun deleteFollowups(contactId: String)

    @Query("SELECT * FROM recruiting_activities WHERE contactId = :contactId ORDER BY createdAt DESC")
    fun activities(contactId: String): Flow<List<RecruitingActivity>>

    @Insert
    suspend fun addActivity(activity: RecruitingActivity)

    @Query("DELETE FROM recruiting_activities WHERE contactId = :contactId")
    suspend fun deleteActivities(contactId: String)

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

@Database(entities = [LocalEvent::class, LocalTask::class, RecruitingContact::class, RecruitingActivity::class, RecruitingFollowup::class], version = 6, exportSchema = false)
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
        private val migration3to4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recruiting_contacts ADD COLUMN consentSignature TEXT NOT NULL DEFAULT ''")
            }
        }
        private val migration4to5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS recruiting_activities (id TEXT NOT NULL PRIMARY KEY, contactId TEXT NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recruiting_activities_contactId ON recruiting_activities(contactId)")
            }
        }
        private val migration5to6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS recruiting_followups (id TEXT NOT NULL PRIMARY KEY, contactId TEXT NOT NULL, title TEXT NOT NULL, dueAt INTEGER NOT NULL, done INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recruiting_followups_contactId ON recruiting_followups(contactId)")
            }
        }
        @Volatile private var instance: FieldHubDatabase? = null
        fun get(context: Context): FieldHubDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, FieldHubDatabase::class.java, "fieldhub-local.db"
                ).addMigrations(migration1to2, migration2to3, migration3to4, migration4to5, migration5to6).build().also { instance = it }
            }
    }
}
