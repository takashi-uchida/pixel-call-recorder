package com.callrecorder.pixel.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import android.util.Log
import com.callrecorder.pixel.data.dao.RecordingDao
import com.callrecorder.pixel.data.model.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room Database for the Call Recorder application.
 * Manages the local storage of call recordings and metadata.
 */
@Database(
    entities = [Recording::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Recording.Converters::class)
abstract class CallRecorderDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: CallRecorderDatabase? = null

        private const val TAG = "CallRecorderDatabase"
        private const val DATABASE_NAME = "call_recorder_database"

        /**
         * Migration from version 1 to 2
         * Adds indexes for better query performance
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating database from version 1 to 2")
                
                // Add indexes for better query performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_phoneNumber ON recordings(phoneNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_recordingDate ON recordings(recordingDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_contactName ON recordings(contactName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_isIncoming ON recordings(isIncoming)")
                
                Log.d(TAG, "Database migration completed successfully")
            }
        }

        /**
         * Gets the singleton instance of the database
         */
        fun getDatabase(context: Context): CallRecorderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CallRecorderDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration() // Only for development
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Database callback for initialization and maintenance
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d(TAG, "Database created successfully")
                
                // Add indexes on initial creation
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_phoneNumber ON recordings(phoneNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_recordingDate ON recordings(recordingDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_contactName ON recordings(contactName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recordings_isIncoming ON recordings(isIncoming)")
                
                // Perform any initial setup in background
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        performInitialSetup(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.d(TAG, "Database opened")
                
                // Perform maintenance tasks
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        performMaintenanceTasks(database)
                    }
                }
            }

            /**
             * Performs initial database setup
             */
            private suspend fun performInitialSetup(database: CallRecorderDatabase) {
                try {
                    Log.d(TAG, "Performing initial database setup")
                    // Add any initial data or setup tasks here
                    // For example, creating default settings or cleaning up orphaned files
                } catch (e: Exception) {
                    Log.e(TAG, "Error during initial database setup", e)
                }
            }

            /**
             * Performs routine maintenance tasks
             */
            private suspend fun performMaintenanceTasks(database: CallRecorderDatabase) {
                try {
                    Log.d(TAG, "Performing database maintenance tasks")
                    
                    // Check for orphaned records (recordings without files)
                    val dao = database.recordingDao()
                    val allRecordings = dao.getAllRecordingsList()
                    var orphanedCount = 0
                    
                    allRecordings.forEach { recording ->
                        val file = java.io.File(recording.filePath)
                        if (!file.exists()) {
                            dao.deleteRecordingById(recording.id)
                            orphanedCount++
                            Log.d(TAG, "Removed orphaned recording: ${recording.id}")
                        }
                    }
                    
                    if (orphanedCount > 0) {
                        Log.i(TAG, "Cleaned up $orphanedCount orphaned recordings")
                    }
                    
                    // Log database statistics
                    val totalRecordings = dao.getRecordingCount()
                    val totalSize = dao.getTotalFileSize() ?: 0L
                    Log.i(TAG, "Database stats - Recordings: $totalRecordings, Total size: ${totalSize / (1024 * 1024)}MB")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during database maintenance", e)
                }
            }
        }

        /**
         * Clears the database instance (for testing purposes)
         */
        fun clearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        /**
         * Performs database integrity check
         */
        fun performIntegrityCheck(context: Context): DatabaseIntegrityResult {
            return try {
                val database = getDatabase(context)
                val dao = database.recordingDao()
                
                val allRecordings = kotlinx.coroutines.runBlocking { dao.getAllRecordingsList() }
                var validCount = 0
                var invalidCount = 0
                val issues = mutableListOf<String>()
                
                allRecordings.forEach { recording ->
                    if (recording.isValid()) {
                        val file = java.io.File(recording.filePath)
                        if (file.exists()) {
                            validCount++
                        } else {
                            invalidCount++
                            issues.add("Missing file for recording: ${recording.id}")
                        }
                    } else {
                        invalidCount++
                        issues.add("Invalid recording data: ${recording.id}")
                    }
                }
                
                DatabaseIntegrityResult.Success(validCount, invalidCount, issues)
            } catch (e: Exception) {
                Log.e(TAG, "Error during integrity check", e)
                DatabaseIntegrityResult.Error(e.message ?: "Unknown error")
            }
        }
    }
}

/**
 * Sealed class representing database integrity check results
 */
sealed class DatabaseIntegrityResult {
    data class Success(
        val validRecordings: Int,
        val invalidRecordings: Int,
        val issues: List<String>
    ) : DatabaseIntegrityResult()
    
    data class Error(val message: String) : DatabaseIntegrityResult()
}
