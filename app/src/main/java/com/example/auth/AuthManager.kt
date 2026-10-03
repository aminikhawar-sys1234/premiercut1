package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.BuildConfig
import com.example.data.local.AccountDao
import com.example.data.local.AppDatabase
import com.example.data.local.OAuthConnectionDao
import com.example.data.local.OAuthConnectionEntity
import com.example.data.local.UserAccountEntity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

sealed class AuthResult {
  data class Success(val user: UserAccountEntity) : AuthResult()
  data class Error(val message: String) : AuthResult()
  object Cancelled : AuthResult()
}

data class StorageBreakdown(
  val cacheBytes: Long,
  val internalFilesBytes: Long,
  val exportFilesBytes: Long,
  val totalStorageBytes: Long
) {
  val formattedTotal: String get() = formatBytes(totalStorageBytes)
  val formattedCache: String get() = formatBytes(cacheBytes)
  val formattedExports: String get() = formatBytes(exportFilesBytes)

  companion object {
    fun formatBytes(bytes: Long): String {
      if (bytes <= 0L) return "0 KB"
      val kb = bytes / 1024.0
      val mb = kb / 1024.0
      val gb = mb / 1024.0
      return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.1f KB", kb)
      }
    }
  }
}

class AuthManager private constructor(private val appContext: Context) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val db = AppDatabase.getDatabase(appContext)
  private val accountDao: AccountDao = db.accountDao()
  private val oauthDao: OAuthConnectionDao = db.oauthConnectionDao()

  private val credentialManager = CredentialManager.create(appContext)
  private val firebaseAuth: FirebaseAuth? = try {
    ensureFirebaseInitialized(appContext)
    if (FirebaseApp.getApps(appContext).isNotEmpty()) {
      FirebaseAuth.getInstance()
    } else {
      null
    }
  } catch (t: Throwable) {
    Log.w("AuthManager", "FirebaseAuth not initialized or offline: ${t.message}")
    null
  }

  val allAccounts: StateFlow<List<UserAccountEntity>> = accountDao.getAllAccounts()
    .stateIn(scope, SharingStarted.Eagerly, emptyList())

  val currentAccount: StateFlow<UserAccountEntity?> = accountDao.getCurrentAccount()
    .stateIn(scope, SharingStarted.Eagerly, null)

  val oauthConnections: StateFlow<List<OAuthConnectionEntity>> = oauthDao.getAllConnections()
    .stateIn(scope, SharingStarted.Eagerly, emptyList())

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _authError = MutableStateFlow<String?>(null)
  val authError: StateFlow<String?> = _authError.asStateFlow()

  private val _storageBreakdown = MutableStateFlow(StorageBreakdown(0L, 0L, 0L, 0L))
  val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

  init {
    // Sync initial social platforms
    scope.launch {
      initializeOAuthPlatformsIfNeeded()
      calculateRealStorageUsage()
    }

    // Listen to Firebase Auth changes if available
    try {
      firebaseAuth?.addAuthStateListener { auth ->
        val user = auth.currentUser
        scope.launch {
          if (user != null) {
            syncFirebaseUserToLocal(user)
          } else {
            // If Firebase is signed out, check if we have an active local account
            val current = accountDao.getCurrentAccountSync()
            if (current != null && current.providerId != "offline") {
              accountDao.clearActiveAccount()
            }
          }
        }
      }
    } catch (t: Throwable) {
      Log.w("AuthManager", "addAuthStateListener error: ${t.message}")
    }
  }

  private fun ensureFirebaseInitialized(context: Context) {
    if (FirebaseApp.getApps(context).isEmpty()) {
      try {
        val apiKey = runCatching { BuildConfig.FIREBASE_API_KEY }.getOrDefault("")
        val projectId = runCatching { BuildConfig.FIREBASE_PROJECT_ID }.getOrDefault("")
        if (apiKey.isNotBlank() && projectId.isNotBlank()) {
          val options = FirebaseOptions.Builder()
            .setApiKey(apiKey)
            .setApplicationId("1:368906369830:android:7d8a9f0e1b2c3d4e")
            .setProjectId(projectId)
            .build()
          FirebaseApp.initializeApp(context, options)
        }
      } catch (e: Exception) {
        Log.e("AuthManager", "Failed to explicitly initialize FirebaseApp", e)
      }
    }
  }

  private suspend fun initializeOAuthPlatformsIfNeeded() {
    val platforms = listOf(
      OAuthConnectionEntity(
        platformId = "youtube",
        platformName = "YouTube Studio",
        platformIcon = "🔴",
        grantedScopes = "https://www.googleapis.com/auth/youtube.upload"
      ),
      OAuthConnectionEntity(
        platformId = "tiktok",
        platformName = "TikTok Creator",
        platformIcon = "🎵",
        grantedScopes = "video.upload,user.info.basic"
      ),
      OAuthConnectionEntity(
        platformId = "instagram",
        platformName = "Instagram Reels",
        platformIcon = "📸",
        grantedScopes = "instagram_basic,instagram_content_publish"
      ),
      OAuthConnectionEntity(
        platformId = "twitter",
        platformName = "X / Twitter",
        platformIcon = "🐦",
        grantedScopes = "tweet.read,tweet.write"
      )
    )

    for (p in platforms) {
      val existing = oauthDao.getConnection(p.platformId)
      if (existing == null) {
        oauthDao.saveConnection(p)
      }
    }
  }

  suspend fun signInWithGoogle(activityContext: Context): AuthResult = withContext(Dispatchers.IO) {
    _isLoading.value = true
    _authError.value = null
    try {
      val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.OAUTH_CLIENT_ID)
        .setAutoSelectEnabled(false)
        .build()

      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

      val result: GetCredentialResponse = credentialManager.getCredential(
        context = activityContext,
        request = request
      )

      val credential = result.credential
      if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleIdTokenCredential.idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val auth = firebaseAuth ?: throw IllegalStateException("Firebase is not initialized")
        val authResult = auth.signInWithCredential(firebaseCredential).await()
        val user = authResult.user
        if (user != null) {
          val entity = syncFirebaseUserToLocal(user, customIdToken = idToken)
          // Automatically link YouTube if user signs in with Google
          oauthDao.saveConnection(
            OAuthConnectionEntity(
              platformId = "youtube",
              platformName = "YouTube Studio",
              platformIcon = "🔴",
              accountHandle = user.email ?: user.displayName ?: "Connected Account",
              accountId = user.uid,
              accessToken = idToken,
              grantedScopes = "https://www.googleapis.com/auth/youtube.upload",
              connectedAtTimestamp = System.currentTimeMillis(),
              isConnected = true
            )
          )
          _isLoading.value = false
          return@withContext AuthResult.Success(entity)
        } else {
          _isLoading.value = false
          _authError.value = "Sign in failed: No user returned"
          return@withContext AuthResult.Error("No user returned from Google")
        }
      } else {
        _isLoading.value = false
        _authError.value = "Unsupported credential received"
        return@withContext AuthResult.Error("Unexpected credential type")
      }
    } catch (e: GetCredentialCancellationException) {
      _isLoading.value = false
      return@withContext AuthResult.Cancelled
    } catch (e: Exception) {
      _isLoading.value = false
      val msg = e.localizedMessage ?: "Google Sign-In failed"
      _authError.value = msg
      Log.e("AuthManager", "Google Sign-In failed", e)
      return@withContext AuthResult.Error(msg)
    }
  }

  suspend fun signInWithEmail(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
    _isLoading.value = true
    _authError.value = null
    try {
      val auth = firebaseAuth ?: throw IllegalStateException("Firebase is not initialized")
      val res = auth.signInWithEmailAndPassword(email.trim(), password).await()
      val user = res.user ?: throw IllegalStateException("Firebase returned null user")
      val entity = syncFirebaseUserToLocal(user)
      _isLoading.value = false
      AuthResult.Success(entity)
    } catch (e: Exception) {
      _isLoading.value = false
      val msg = e.localizedMessage ?: "Sign in failed"
      _authError.value = msg
      AuthResult.Error(msg)
    }
  }

  suspend fun registerWithEmail(email: String, password: String, displayName: String): AuthResult = withContext(Dispatchers.IO) {
    _isLoading.value = true
    _authError.value = null
    try {
      val auth = firebaseAuth ?: throw IllegalStateException("Firebase is not initialized")
      val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
      val user = res.user ?: throw IllegalStateException("Firebase returned null user")
      
      val profileUpdates = UserProfileChangeRequest.Builder()
        .setDisplayName(displayName.trim().ifBlank { email.substringBefore("@") })
        .build()
      user.updateProfile(profileUpdates).await()

      val entity = syncFirebaseUserToLocal(user)
      _isLoading.value = false
      AuthResult.Success(entity)
    } catch (e: Exception) {
      _isLoading.value = false
      val msg = e.localizedMessage ?: "Registration failed"
      _authError.value = msg
      AuthResult.Error(msg)
    }
  }

  suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val auth = firebaseAuth ?: throw IllegalStateException("Firebase is not initialized")
      auth.sendPasswordResetEmail(email.trim()).await()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun updateProfile(displayName: String, bio: String, customHandle: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val current = currentAccount.value ?: return@withContext Result.failure(IllegalStateException("No active account"))
      val user = firebaseAuth?.currentUser
      if (user != null && user.uid == current.uid) {
        val updates = UserProfileChangeRequest.Builder()
          .setDisplayName(displayName)
          .build()
        user.updateProfile(updates).await()
      }
      accountDao.updateProfileInfo(current.uid, displayName, bio, customHandle)
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun switchAccount(uid: String) = withContext(Dispatchers.IO) {
    val target = accountDao.getAccountByUid(uid) ?: return@withContext
    accountDao.setActiveAccount(uid)
  }

  suspend fun removeAccount(uid: String) = withContext(Dispatchers.IO) {
    val isCur = currentAccount.value?.uid == uid
    accountDao.deleteAccount(uid)
    if (isCur) {
      val remaining = accountDao.getAllAccounts()
      // We take the first if available or sign out
      firebaseAuth?.signOut()
    }
  }

  suspend fun signOut() = withContext(Dispatchers.IO) {
    _isLoading.value = true
    try {
      firebaseAuth?.signOut()
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
      accountDao.clearActiveAccount()
    } catch (e: Exception) {
      Log.e("AuthManager", "Sign out error", e)
    } finally {
      _isLoading.value = false
    }
  }

  suspend fun connectOAuthPlatform(
    platformId: String,
    accountHandle: String,
    accountId: String,
    accessToken: String,
    refreshToken: String? = null
  ) = withContext(Dispatchers.IO) {
    val existing = oauthDao.getConnection(platformId)
    val updated = (existing ?: OAuthConnectionEntity(
      platformId = platformId,
      platformName = platformId.replaceFirstChar { it.uppercase() },
      platformIcon = "🔗"
    )).copy(
      accountHandle = accountHandle.ifBlank { "@authorized_user" },
      accountId = accountId,
      accessToken = accessToken,
      refreshToken = refreshToken,
      connectedAtTimestamp = System.currentTimeMillis(),
      isConnected = true
    )
    oauthDao.saveConnection(updated)
  }

  suspend fun disconnectOAuthPlatform(platformId: String) = withContext(Dispatchers.IO) {
    oauthDao.disconnectPlatform(platformId)
  }

  private suspend fun syncFirebaseUserToLocal(
    user: FirebaseUser,
    customIdToken: String? = null
  ): UserAccountEntity {
    val uid = user.uid
    val email = user.email ?: "${uid.take(8)}@creator.io"
    val displayName = user.displayName?.ifBlank { null } ?: email.substringBefore("@")
    val photoUrl = user.photoUrl?.toString()
    val providerId = user.providerData.getOrNull(1)?.providerId ?: user.providerId

    // Color derived consistently from UID
    val colorPalette = listOf(
      0xFF00E5FF, 0xFF9333EA, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF3B82F6
    )
    val colorIndex = kotlin.math.abs(uid.hashCode()) % colorPalette.size
    val avatarColor = colorPalette[colorIndex]

    val existing = accountDao.getAccountByUid(uid)
    val entity = UserAccountEntity(
      uid = uid,
      email = email,
      displayName = displayName,
      photoUrl = photoUrl,
      providerId = providerId,
      bio = existing?.bio ?: "Mobile Creator & Video Specialist",
      customHandle = existing?.customHandle ?: "@${email.substringBefore("@")}",
      lastActiveTimestamp = System.currentTimeMillis(),
      isCurrent = true,
      idToken = customIdToken ?: existing?.idToken,
      avatarColor = avatarColor
    )

    accountDao.insertOrUpdateAccount(entity)
    accountDao.setActiveAccount(uid)
    return entity
  }

  suspend fun calculateRealStorageUsage(): StorageBreakdown = withContext(Dispatchers.IO) {
    val cacheSize = getFolderSize(appContext.cacheDir) + getFolderSize(appContext.codeCacheDir)
    val filesSize = getFolderSize(appContext.filesDir)
    val externalSize = appContext.getExternalFilesDir(null)?.let { getFolderSize(it) } ?: 0L
    val total = cacheSize + filesSize + externalSize

    val breakdown = StorageBreakdown(
      cacheBytes = cacheSize,
      internalFilesBytes = filesSize,
      exportFilesBytes = externalSize,
      totalStorageBytes = total
    )
    _storageBreakdown.value = breakdown
    breakdown
  }

  suspend fun clearRealAppCache(): Long = withContext(Dispatchers.IO) {
    val initialCache = getFolderSize(appContext.cacheDir)
    deleteDirectoryContents(appContext.cacheDir)
    deleteDirectoryContents(appContext.codeCacheDir)
    calculateRealStorageUsage()
    initialCache
  }

  private fun getFolderSize(file: File?): Long {
    if (file == null || !file.exists()) return 0L
    if (file.isFile) return file.length()
    var size = 0L
    val children = file.listFiles() ?: return 0L
    for (child in children) {
      size += getFolderSize(child)
    }
    return size
  }

  private fun deleteDirectoryContents(file: File?): Boolean {
    if (file == null || !file.exists()) return false
    val children = file.listFiles() ?: return false
    var allDeleted = true
    for (child in children) {
      if (child.isDirectory) {
        deleteDirectoryContents(child)
      }
      if (!child.delete()) {
        allDeleted = false
      }
    }
    return allDeleted
  }

  companion object {
    @Volatile
    private var INSTANCE: AuthManager? = null

    fun getInstance(context: Context): AuthManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: AuthManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
