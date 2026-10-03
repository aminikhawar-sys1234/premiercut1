package com.example.engine.plugin

import android.util.Log
import com.example.domain.plugin.InstalledPlugin
import com.example.domain.plugin.PluginManifest
import com.example.domain.plugin.PluginValidationResult
import java.io.File

/**
 * Validates plugin manifests, API versions, schema integrity, and file dependencies.
 */
object PluginValidator {
  private const val TAG = "PluginValidator"
  private const val CURRENT_ENGINE_API_VERSION = "1.0.0"

  fun validatePlugin(manifest: PluginManifest, installDir: File): PluginValidationResult {
    if (manifest.id.isBlank()) {
      return PluginValidationResult.Error("Plugin manifest missing unique 'id'.")
    }
    if (manifest.name.isBlank()) {
      return PluginValidationResult.Error("Plugin manifest missing 'name'.")
    }

    // Check version format
    if (!isValidVersionFormat(manifest.version)) {
      return PluginValidationResult.Error("Invalid version string format '${manifest.version}'. Must be semantic (e.g. 1.0.0).")
    }

    // Check minimum app version compatibility
    if (isVersionHigher(manifest.minimumAppVersion, CURRENT_ENGINE_API_VERSION)) {
      return PluginValidationResult.Error("Plugin requires app API version ${manifest.minimumAppVersion}, but current engine version is $CURRENT_ENGINE_API_VERSION.")
    }

    // Verify item files exist if declared
    for (item in manifest.items) {
      if (item.file.isNotBlank()) {
        val itemFile = File(installDir, item.file)
        if (!itemFile.exists()) {
          Log.w(TAG, "Plugin item asset file missing: ${item.file} in plugin ${manifest.id}")
        }
      }
    }

    val installedPlugin = InstalledPlugin(
      manifest = manifest,
      installDirAbsolutePath = installDir.absolutePath,
      isEnabled = true,
      installedTimestamp = System.currentTimeMillis()
    )

    return PluginValidationResult.Success(
      installedPlugin = installedPlugin,
      message = "Plugin '${manifest.name}' validated successfully (v${manifest.version})."
    )
  }

  private fun isValidVersionFormat(version: String): Boolean {
    return version.matches(Regex("""^\d+\.\d+(\.\d+)?.*$"""))
  }

  private fun isVersionHigher(v1: String, v2: String): Boolean {
    val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
    val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(parts1.size, parts2.size)) {
      val p1 = parts1.getOrElse(i) { 0 }
      val p2 = parts2.getOrElse(i) { 0 }
      if (p1 > p2) return true
      if (p1 < p2) return false
    }
    return false
  }
}
