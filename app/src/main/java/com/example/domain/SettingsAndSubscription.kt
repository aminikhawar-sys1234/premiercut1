package com.example.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
  val language: String = "English",
  val autoSaveIntervalSec: Int = 15,
  val defaultResolution: String = "1080p",
  val previewQualityProxy: Boolean = false,
  val timelineSnapping: Boolean = true,
  val rippleEditing: Boolean = true,
  val userEmail: String = "creator@ahvideostudio.com",
  val userName: String = "Pro Creator",
  val isProSubscriber: Boolean = true,
  val cloudSyncEnabled: Boolean = false,
  val openEditorDirectlyOnNewProject: Boolean = true,
  val isDarkTheme: Boolean = false,
  val autoSaveEnabled: Boolean = true,
  val notificationsEnabled: Boolean = true,
  val videoQuality: String = "1080p"
)

object StudioPreferencesManager {
  private val _settings = MutableStateFlow(UserSettings())
  val settings: StateFlow<UserSettings> = _settings.asStateFlow()

  fun updateOpenEditorDirectly(enabled: Boolean) {
    _settings.value = _settings.value.copy(openEditorDirectlyOnNewProject = enabled)
  }

  fun updateLanguage(lang: String) {
    _settings.value = _settings.value.copy(language = lang)
  }

  fun updateTheme(isDark: Boolean) {
    _settings.value = _settings.value.copy(isDarkTheme = isDark)
  }

  fun updateAutoSave(enabled: Boolean) {
    _settings.value = _settings.value.copy(autoSaveEnabled = enabled)
  }

  fun updateNotifications(enabled: Boolean) {
    _settings.value = _settings.value.copy(notificationsEnabled = enabled)
  }

  fun updateVideoQuality(quality: String) {
    _settings.value = _settings.value.copy(videoQuality = quality, defaultResolution = quality)
  }

  fun updateSnapping(enabled: Boolean) {
    _settings.value = _settings.value.copy(timelineSnapping = enabled)
  }

  fun updateProxyMode(enabled: Boolean) {
    _settings.value = _settings.value.copy(previewQualityProxy = enabled)
  }

  fun toggleProSubscription() {
    _settings.value = _settings.value.copy(isProSubscriber = !_settings.value.isProSubscriber)
  }

  fun clearAppCache(): Long {
    // Returns cleared bytes
    return 48_500_000L // 48.5 MB
  }
}
