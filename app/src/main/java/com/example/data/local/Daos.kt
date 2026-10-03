package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
  @Query("SELECT * FROM projects ORDER BY lastEditedTime DESC")
  fun getAllProjects(): Flow<List<ProjectEntity>>

  @Query("SELECT * FROM projects WHERE isDraft = 1 ORDER BY lastEditedTime DESC")
  fun getDrafts(): Flow<List<ProjectEntity>>

  @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
  suspend fun getProjectById(id: String): ProjectEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProject(project: ProjectEntity)

  @Update
  suspend fun updateProject(project: ProjectEntity)

  @Query("DELETE FROM projects WHERE id = :id")
  suspend fun deleteProjectById(id: String)

  @Query("UPDATE projects SET name = :newName, lastEditedTime = :time WHERE id = :id")
  suspend fun renameProject(id: String, newName: String, time: Long = System.currentTimeMillis())

  @Query("UPDATE projects SET hasMissingMedia = :hasMissing WHERE id = :id")
  suspend fun updateMissingMediaStatus(id: String, hasMissing: Boolean)
}

@Dao
interface CrashRecoveryDao {
  @Query("SELECT * FROM crash_recovery WHERE id = 'active_session' LIMIT 1")
  suspend fun getActiveSession(): CrashRecoveryEntity?

  @Query("SELECT * FROM crash_recovery WHERE id = 'active_session' LIMIT 1")
  fun getActiveSessionFlow(): Flow<CrashRecoveryEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveSession(session: CrashRecoveryEntity)

  @Query("DELETE FROM crash_recovery WHERE id = 'active_session'")
  suspend fun clearSession()
}

@Dao
interface ExportedVideoDao {
  @Query("SELECT * FROM exported_videos ORDER BY timestamp DESC")
  fun getAllExportedVideos(): Flow<List<ExportedVideoEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExportedVideo(video: ExportedVideoEntity)

  @Query("DELETE FROM exported_videos WHERE id = :id")
  suspend fun deleteExportedVideo(id: String)
}

@Dao
interface AccountDao {
  @Query("SELECT * FROM user_accounts ORDER BY lastActiveTimestamp DESC")
  fun getAllAccounts(): Flow<List<UserAccountEntity>>

  @Query("SELECT * FROM user_accounts WHERE isCurrent = 1 LIMIT 1")
  fun getCurrentAccount(): Flow<UserAccountEntity?>

  @Query("SELECT * FROM user_accounts WHERE isCurrent = 1 LIMIT 1")
  suspend fun getCurrentAccountSync(): UserAccountEntity?

  @Query("SELECT * FROM user_accounts WHERE uid = :uid LIMIT 1")
  suspend fun getAccountByUid(uid: String): UserAccountEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateAccount(account: UserAccountEntity)

  @Query("UPDATE user_accounts SET isCurrent = CASE WHEN uid = :activeUid THEN 1 ELSE 0 END, lastActiveTimestamp = CASE WHEN uid = :activeUid THEN :now ELSE lastActiveTimestamp END")
  suspend fun setActiveAccount(activeUid: String, now: Long = System.currentTimeMillis())

  @Query("UPDATE user_accounts SET displayName = :name, bio = :bio, customHandle = :handle WHERE uid = :uid")
  suspend fun updateProfileInfo(uid: String, name: String, bio: String, handle: String)

  @Query("DELETE FROM user_accounts WHERE uid = :uid")
  suspend fun deleteAccount(uid: String)

  @Query("UPDATE user_accounts SET isCurrent = 0")
  suspend fun clearActiveAccount()
}

@Dao
interface OAuthConnectionDao {
  @Query("SELECT * FROM oauth_connections")
  fun getAllConnections(): Flow<List<OAuthConnectionEntity>>

  @Query("SELECT * FROM oauth_connections WHERE platformId = :platformId LIMIT 1")
  suspend fun getConnection(platformId: String): OAuthConnectionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveConnection(connection: OAuthConnectionEntity)

  @Query("UPDATE oauth_connections SET isConnected = 0, accessToken = '', refreshToken = NULL WHERE platformId = :platformId")
  suspend fun disconnectPlatform(platformId: String)
}
