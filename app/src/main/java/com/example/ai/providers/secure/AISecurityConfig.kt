package com.example.ai.providers.secure

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

/**
 * Secure AI configuration and backend abstraction layer.
 *
 * CRITICAL SECURITY:
 * Private API keys must never be hardcoded into the Android APK.
 * This class abstracts request routing through an optional secure backend proxy
 * or retrieves securely configured runtime credentials.
 */
object AISecurityConfig {

  private const val PREFS_NAME = "ah_ai_security_config"
  private const val KEY_BACKEND_PROXY_URL = "backend_proxy_url"
  private const val KEY_CUSTOM_TOKEN = "custom_auth_token"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun getBackendProxyUrl(context: Context): String {
    return getPrefs(context).getString(KEY_BACKEND_PROXY_URL, "") ?: ""
  }

  fun setBackendProxyUrl(context: Context, url: String) {
    getPrefs(context).edit().putString(KEY_BACKEND_PROXY_URL, url.trim()).apply()
  }

  fun getCustomAuthToken(context: Context): String {
    return getPrefs(context).getString(KEY_CUSTOM_TOKEN, "") ?: ""
  }

  fun setCustomAuthToken(context: Context, token: String) {
    getPrefs(context).edit().putString(KEY_CUSTOM_TOKEN, token.trim()).apply()
  }

  /**
   * Returns the active API key or authorization token.
   * Prioritizes runtime-configured token, then injected BuildConfig secret if valid.
   * Returns empty string if not configured.
   */
  fun getEffectiveApiKey(context: Context? = null): String {
    if (context != null) {
      val custom = getCustomAuthToken(context)
      if (custom.isNotBlank()) return custom
    }
    return try {
      val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
      val key = field.get(null) as? String ?: ""
      if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") key else ""
    } catch (e: Throwable) {
      ""
    }
  }

  /**
   * Checks if either a secure backend proxy or direct API credentials are configured.
   */
  fun isConfigured(context: Context? = null): Boolean {
    if (context != null && getBackendProxyUrl(context).isNotBlank()) return true
    return getEffectiveApiKey(context).isNotBlank()
  }

  /**
   * Describes the active connection method for transparency and security audits.
   */
  fun getConnectionModeDescription(context: Context): String {
    val proxy = getBackendProxyUrl(context)
    if (proxy.isNotBlank()) {
      return "Secure Backend Proxy ($proxy)"
    }
    val key = getEffectiveApiKey(context)
    if (key.isNotBlank()) {
      return "Direct API Authentication (Configured)"
    }
    return "Offline / Not Configured"
  }
}
