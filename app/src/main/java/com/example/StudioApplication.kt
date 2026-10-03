package com.example

import android.app.Application
import android.util.Log
import com.example.domain.StudioAccountManager
import com.example.engine.NativeEngineLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StudioApplication : Application() {
  companion object {
    lateinit var instance: StudioApplication
      private set
  }

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    instance = this
    
    // Global safety uncaught exception handler: logs error and prevents unnecessary hard crashes
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("StudioApplication", "Handled uncaught exception on thread: ${thread.name}: ${throwable.message}", throwable)
      if (thread == android.os.Looper.getMainLooper().thread) {
        defaultHandler?.uncaughtException(thread, throwable)
      }
    }

    // 1. Safe Firebase App Initialization with explicit BuildConfig parameters
    try {
      if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
        val apiKey = runCatching { BuildConfig.FIREBASE_API_KEY }.getOrDefault("")
        val projectId = runCatching { BuildConfig.FIREBASE_PROJECT_ID }.getOrDefault("")
        if (apiKey.isNotBlank() && projectId.isNotBlank()) {
          val options = com.google.firebase.FirebaseOptions.Builder()
            .setApiKey(apiKey)
            .setApplicationId("1:368906369830:android:7d8a9f0e1b2c3d4e")
            .setProjectId(projectId)
            .build()
          com.google.firebase.FirebaseApp.initializeApp(this, options)
        }
      }
    } catch (t: Throwable) {
      Log.w("StudioApplication", "FirebaseApp initialization offline fallback: ${t.message}")
    }

    // 2. Safe Native Library Loader
    try {
      NativeEngineLoader.loadLibrary()
    } catch (t: Throwable) {
      Log.w("StudioApplication", "Native library loader skipped/failed", t)
    }

    // 3. Safe Asynchronous Studio Account & Firebase Initialization (off main thread)
    appScope.launch {
      try {
        StudioAccountManager.init(applicationContext)
      } catch (t: Throwable) {
        Log.w("StudioApplication", "StudioAccountManager init skipped/failed", t)
      }
      try {
        com.example.data.firebase.FirebaseEffectsManager.init(applicationContext)
      } catch (t: Throwable) {
        Log.w("StudioApplication", "FirebaseEffectsManager init skipped/failed", t)
      }
      try {
        com.example.data.firebase.FirebaseTemplateManager.init(applicationContext)
      } catch (t: Throwable) {
        Log.w("StudioApplication", "FirebaseTemplateManager init skipped/failed", t)
      }
      try {
        com.example.data.repository.NotificationRepository.init(applicationContext)
      } catch (t: Throwable) {
        Log.w("StudioApplication", "NotificationRepository init skipped/failed", t)
      }
    }
  }
}
