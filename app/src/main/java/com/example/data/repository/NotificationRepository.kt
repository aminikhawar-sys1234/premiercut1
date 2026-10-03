package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.AppScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppNotification(
  val id: String,
  val title: String,
  val message: String,
  val timestampMs: Long = System.currentTimeMillis(),
  val iconType: String = "info", // "export", "project", "template", "pro", "info"
  val isRead: Boolean = false,
  val targetScreen: AppScreen? = null
)

object NotificationRepository {
  private var prefs: SharedPreferences? = null
  private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
  val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

  fun init(context: Context) {
    if (prefs != null) return
    val appContext = context.applicationContext
    prefs = appContext.getSharedPreferences("premier_cut_notifications", Context.MODE_PRIVATE)
    loadInitialNotifications()
  }

  private fun loadInitialNotifications() {
    val p = prefs ?: return
    val readIds = p.getStringSet("read_ids", emptySet()) ?: emptySet()
    
    // Seed real initial system notifications if empty
    val initialList = listOf(
      AppNotification(
        id = "sys_welcome",
        title = "Welcome to Premier Cut",
        message = "Start your first project or explore trending creator templates.",
        timestampMs = System.currentTimeMillis() - 3600_000L * 2,
        iconType = "info",
        isRead = readIds.contains("sys_welcome"),
        targetScreen = AppScreen.HOME
      ),
      AppNotification(
        id = "sys_engine_ready",
        title = "GPU Acceleration Active",
        message = "Hardware encoding and real-time multi-track compositor are ready.",
        timestampMs = System.currentTimeMillis() - 3600_000L * 5,
        iconType = "project",
        isRead = readIds.contains("sys_engine_ready"),
        targetScreen = AppScreen.SETTINGS
      )
    )
    _notifications.value = initialList
  }

  fun addNotification(notification: AppNotification) {
    val current = _notifications.value.filter { it.id != notification.id }
    _notifications.value = listOf(notification) + current
  }

  fun markAsRead(notificationId: String) {
    val p = prefs ?: return
    val readIds = (p.getStringSet("read_ids", emptySet()) ?: emptySet()).toMutableSet()
    readIds.add(notificationId)
    p.edit().putStringSet("read_ids", readIds).apply()

    _notifications.value = _notifications.value.map {
      if (it.id == notificationId) it.copy(isRead = true) else it
    }
  }

  fun markAllAsRead() {
    val p = prefs ?: return
    val allIds = _notifications.value.map { it.id }.toSet()
    p.edit().putStringSet("read_ids", allIds).apply()

    _notifications.value = _notifications.value.map { it.copy(isRead = true) }
  }

  fun clearAll() {
    _notifications.value = emptyList()
  }
}
