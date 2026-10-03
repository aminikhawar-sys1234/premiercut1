package com.example.domain

import android.content.Context
import com.example.auth.AuthManager
import com.example.auth.AuthResult
import com.example.auth.StorageBreakdown
import com.example.data.local.OAuthConnectionEntity
import com.example.data.local.UserAccountEntity
import com.example.data.presets.VideoTemplate
import com.example.domain.model.AspectRatio
import com.example.domain.model.AudioClip
import com.example.domain.model.FilterSettings
import com.example.domain.model.FilterType
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

object StudioAccountManager {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var authManager: AuthManager? = null

  // Real Account & Auth State
  private val _currentAccount = MutableStateFlow<UserAccountEntity?>(null)
  val currentAccount: StateFlow<UserAccountEntity?> = _currentAccount.asStateFlow()

  private val _allAccounts = MutableStateFlow<List<UserAccountEntity>>(emptyList())
  val allAccounts: StateFlow<List<UserAccountEntity>> = _allAccounts.asStateFlow()

  private val _oauthConnections = MutableStateFlow<List<OAuthConnectionEntity>>(emptyList())
  val oauthConnections: StateFlow<List<OAuthConnectionEntity>> = _oauthConnections.asStateFlow()

  private val _storageBreakdown = MutableStateFlow(StorageBreakdown(0L, 0L, 0L, 0L))
  val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _authError = MutableStateFlow<String?>(null)
  val authError: StateFlow<String?> = _authError.asStateFlow()

  // Custom Video Templates Management
  private val _savedTemplateIds = MutableStateFlow<Set<String>>(emptySet())
  val savedTemplateIds: StateFlow<Set<String>> = _savedTemplateIds.asStateFlow()

  private val _customTemplates = MutableStateFlow<List<VideoTemplate>>(emptyList())
  val customTemplates: StateFlow<List<VideoTemplate>> = _customTemplates.asStateFlow()

  fun init(context: Context) {
    com.example.data.firebase.FirebaseTemplateManager.init(context.applicationContext)
    if (authManager == null) {
      val manager = AuthManager.getInstance(context.applicationContext)
      authManager = manager

      scope.launch {
        manager.currentAccount.collect { account ->
          _currentAccount.value = account
        }
      }

      scope.launch {
        manager.allAccounts.collect { list ->
          _allAccounts.value = list
        }
      }

      scope.launch {
        manager.oauthConnections.collect { list ->
          _oauthConnections.value = list
        }
      }

      scope.launch {
        manager.storageBreakdown.collect { breakdown ->
          _storageBreakdown.value = breakdown
        }
      }

      scope.launch {
        manager.isLoading.collect { loading ->
          _isLoading.value = loading
        }
      }

      scope.launch {
        manager.authError.collect { err ->
          _authError.value = err
        }
      }
    }
  }

  suspend fun signInWithGoogle(activityContext: Context): AuthResult {
    val manager = authManager ?: AuthManager.getInstance(activityContext.applicationContext).also { authManager = it }
    return manager.signInWithGoogle(activityContext)
  }

  suspend fun signInWithEmail(email: String, pass: String): AuthResult {
    val manager = authManager ?: throw IllegalStateException("AuthManager not initialized")
    return manager.signInWithEmail(email, pass)
  }

  suspend fun registerWithEmail(email: String, pass: String, name: String): AuthResult {
    val manager = authManager ?: throw IllegalStateException("AuthManager not initialized")
    return manager.registerWithEmail(email, pass, name)
  }

  suspend fun sendPasswordReset(email: String): Result<Unit> {
    val manager = authManager ?: throw IllegalStateException("AuthManager not initialized")
    return manager.sendPasswordReset(email)
  }

  suspend fun updateProfile(name: String, bio: String, handle: String): Result<Unit> {
    val manager = authManager ?: return Result.failure(IllegalStateException("AuthManager not initialized"))
    return manager.updateProfile(name, bio, handle)
  }

  suspend fun switchActiveAccount(uid: String) {
    authManager?.switchAccount(uid)
  }

  suspend fun removeAccount(uid: String) {
    authManager?.removeAccount(uid)
  }

  suspend fun signOut() {
    authManager?.signOut()
  }

  suspend fun connectOAuthPlatform(
    platformId: String,
    accountHandle: String,
    accountId: String,
    accessToken: String,
    refreshToken: String? = null
  ) {
    authManager?.connectOAuthPlatform(platformId, accountHandle, accountId, accessToken, refreshToken)
  }

  suspend fun disconnectOAuthPlatform(platformId: String) {
    authManager?.disconnectOAuthPlatform(platformId)
  }

  suspend fun refreshStorageUsage(): StorageBreakdown {
    return authManager?.calculateRealStorageUsage() ?: StorageBreakdown(0L, 0L, 0L, 0L)
  }

  suspend fun clearRealAppCache(): Long {
    return authManager?.clearRealAppCache() ?: 0L
  }

  fun toggleSavedTemplate(templateId: String) {
    val current = _savedTemplateIds.value.toMutableSet()
    if (current.contains(templateId)) {
      current.remove(templateId)
    } else {
      current.add(templateId)
    }
    _savedTemplateIds.value = current
  }

  fun saveProjectAsTemplate(
    title: String,
    category: String,
    description: String,
    timeline: Timeline,
    aspectRatio: AspectRatio
  ): VideoTemplate {
    val newId = "cust_tpl_${UUID.randomUUID().toString().take(8)}"
    val dur = timeline.totalDurationMs.coerceAtLeast(3000L)
    val template = VideoTemplate(
      id = newId,
      title = title.ifBlank { "Untitled Template" },
      category = if (category.isBlank()) "User-Created" else category,
      description = description.ifBlank { "Custom template created in AH Video Studio" },
      aspectRatio = aspectRatio,
      resolution = Resolution.RES_1080P,
      fps = FrameRate.FPS_30,
      durationMs = dur,
      thumbnailGradientStart = 0xFF3B82F6,
      thumbnailGradientEnd = 0xFF8B5CF6,
      iconEmoji = "🎬",
      audioTitle = "Project Soundtrack",
      savedTimeline = timeline.copy(),
      createTimeline = { _, _ -> timeline.copy() }
    )

    _customTemplates.value = listOf(template) + _customTemplates.value
    val currentSaved = _savedTemplateIds.value.toMutableSet()
    currentSaved.add(newId)
    _savedTemplateIds.value = currentSaved
    return template
  }

  fun duplicateCustomTemplate(templateId: String): VideoTemplate? {
    val target = _customTemplates.value.find { it.id == templateId }
      ?: com.example.data.firebase.FirebaseTemplateManager.templates.value.find { it.id == templateId }
      ?: return null

    val copyId = "cust_tpl_${UUID.randomUUID().toString().take(8)}"
    val duplicated = target.copy(
      id = copyId,
      title = "${target.title} (Copy)",
      category = if (target.category.isBlank()) "User-Created" else target.category,
      savedTimeline = target.savedTimeline ?: target.createDefaultTimeline()
    )

    _customTemplates.value = listOf(duplicated) + _customTemplates.value
    val currentSaved = _savedTemplateIds.value.toMutableSet()
    currentSaved.add(copyId)
    _savedTemplateIds.value = currentSaved
    return duplicated
  }

  fun deleteCustomTemplate(templateId: String) {
    _customTemplates.value = _customTemplates.value.filterNot { it.id == templateId }
    val currentSaved = _savedTemplateIds.value.toMutableSet()
    currentSaved.remove(templateId)
    _savedTemplateIds.value = currentSaved
  }
}
