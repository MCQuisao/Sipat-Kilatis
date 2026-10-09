package com.example.sipatkilatis.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * On-device database (a file in the app's private folder). Nothing in it is ever uploaded:
 * the app has no internet permission at all.
 */

/** One scanned message. Enums are stored by name; flags and highlights as small JSON strings. */
@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val text: String,
    val source: String,          // MessageSource name: SMS / NOTIFICATION / MANUAL
    val verdict: String,         // Verdict name
    val score: Float,
    val mlScore: Float,
    val urlScore: Float,
    val rulesScore: Float,
    val flagsJson: String,       // [{"id","en","fil"}, ...]
    val highlightsJson: String,  // [[first,last], ...]
    val timestamp: Long,
    val userFeedback: String = "NONE",   // NONE / MARKED_SAFE / REPORTED
)

@Entity(tableName = "trusted_contacts")
data class TrustedContactEntity(
    @PrimaryKey val sender: String,
    val addedAt: Long,
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun get(id: Long): ScanEntity?

    @Insert
    suspend fun insert(scan: ScanEntity): Long

    @Query("UPDATE scans SET userFeedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: Long, feedback: String)

    @Query("SELECT * FROM scans WHERE userFeedback != 'NONE' ORDER BY timestamp DESC")
    suspend fun withFeedback(): List<ScanEntity>

    @Query("DELETE FROM scans")
    suspend fun clear()

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(scan: ScanEntity)

    @Query("SELECT * FROM trusted_contacts ORDER BY addedAt")
    fun observeTrusted(): Flow<List<TrustedContactEntity>>

    @Query("SELECT * FROM trusted_contacts")
    suspend fun trustedList(): List<TrustedContactEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrusted(contact: TrustedContactEntity)

    @Query("DELETE FROM trusted_contacts WHERE sender = :sender")
    suspend fun removeTrusted(sender: String)
}

@Database(entities = [ScanEntity::class, TrustedContactEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sipat_kilatis.db").build()
    }
}
