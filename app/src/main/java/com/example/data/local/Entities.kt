package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey val id: String,
  val name: String,
  val durationMs: Long,
  val lastEditedTime: Long,
  val thumbnailPath: String,
  val aspectRatio: String = "9:16",
  val resolution: String = "1080p",
  val fps: Int = 30,
  val timelineJson: String,
  val isDraft: Boolean = true,
  // Migrated fields with default values for backward compatibility
  val sampleRate: Int = 48000,
  val canvasColor: Long = 0xFF000000,
  val hasMissingMedia: Boolean = false,
  val extraMetadataJson: String = "{}"
)

@Entity(tableName = "crash_recovery")
data class CrashRecoveryEntity(
  @PrimaryKey val id: String = "active_session",
  val projectId: String,
  val projectName: String,
  val timestamp: Long,
  val timelineJson: String,
  val settingsJson: String = "{}",
  val isDirty: Boolean = true
)

@Entity(tableName = "exported_videos")
data class ExportedVideoEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val title: String,
  val filePath: String,
  val durationMs: Long,
  val resolution: String,
  val fps: Int,
  val timestamp: Long,
  val fileSizeBytes: Long
)

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
  @PrimaryKey val uid: String,
  val email: String,
  val displayName: String,
  val photoUrl: String? = null,
  val providerId: String = "google.com",
  val bio: String = "",
  val customHandle: String = "",
  val lastActiveTimestamp: Long = System.currentTimeMillis(),
  val isCurrent: Boolean = false,
  val idToken: String? = null,
  val refreshToken: String? = null,
  val avatarColor: Long = 0xFF00E5FF
)

@Entity(tableName = "oauth_connections")
data class OAuthConnectionEntity(
  @PrimaryKey val platformId: String,
  val platformName: String,
  val platformIcon: String,
  val accountHandle: String = "",
  val accountId: String = "",
  val accessToken: String = "",
  val refreshToken: String? = null,
  val expiresAtTimestamp: Long = 0L,
  val grantedScopes: String = "",
  val connectedAtTimestamp: Long = 0L,
  val isConnected: Boolean = false
)
