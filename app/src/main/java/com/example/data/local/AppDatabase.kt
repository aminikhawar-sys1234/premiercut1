package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    ProjectEntity::class,
    ExportedVideoEntity::class,
    CrashRecoveryEntity::class,
    UserAccountEntity::class,
    OAuthConnectionEntity::class
  ],
  version = 3,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun projectDao(): ProjectDao
  abstract fun exportedVideoDao(): ExportedVideoDao
  abstract fun crashRecoveryDao(): CrashRecoveryDao
  abstract fun accountDao(): AccountDao
  abstract fun oauthConnectionDao(): OAuthConnectionDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Add new project configuration and metadata columns with safe default values
        db.execSQL("ALTER TABLE projects ADD COLUMN sampleRate INTEGER NOT NULL DEFAULT 48000")
        db.execSQL("ALTER TABLE projects ADD COLUMN canvasColor INTEGER NOT NULL DEFAULT -16777216")
        db.execSQL("ALTER TABLE projects ADD COLUMN hasMissingMedia INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE projects ADD COLUMN extraMetadataJson TEXT NOT NULL DEFAULT '{}'")

        // Create crash recovery table to protect unsaved timeline changes
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS crash_recovery (
            id TEXT NOT NULL PRIMARY KEY,
            projectId TEXT NOT NULL,
            projectName TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            timelineJson TEXT NOT NULL,
            settingsJson TEXT NOT NULL,
            isDirty INTEGER NOT NULL DEFAULT 1
          )
        """.trimIndent())
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS user_accounts (
            uid TEXT NOT NULL PRIMARY KEY,
            email TEXT NOT NULL,
            displayName TEXT NOT NULL,
            photoUrl TEXT,
            providerId TEXT NOT NULL,
            bio TEXT NOT NULL DEFAULT '',
            customHandle TEXT NOT NULL DEFAULT '',
            lastActiveTimestamp INTEGER NOT NULL,
            isCurrent INTEGER NOT NULL DEFAULT 0,
            idToken TEXT,
            refreshToken TEXT,
            avatarColor INTEGER NOT NULL DEFAULT -16725249
          )
        """.trimIndent())
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS oauth_connections (
            platformId TEXT NOT NULL PRIMARY KEY,
            platformName TEXT NOT NULL,
            platformIcon TEXT NOT NULL,
            accountHandle TEXT NOT NULL DEFAULT '',
            accountId TEXT NOT NULL DEFAULT '',
            accessToken TEXT NOT NULL DEFAULT '',
            refreshToken TEXT,
            expiresAtTimestamp INTEGER NOT NULL DEFAULT 0,
            grantedScopes TEXT NOT NULL DEFAULT '',
            connectedAtTimestamp INTEGER NOT NULL DEFAULT 0,
            isConnected INTEGER NOT NULL DEFAULT 0
          )
        """.trimIndent())
      }
    }

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "ah_video_studio.db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}

