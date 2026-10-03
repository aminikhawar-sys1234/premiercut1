package com.example.engine.plugin

import android.content.Context
import android.graphics.ColorMatrix
import android.util.Log
import com.example.domain.plugin.InstalledPlugin
import com.example.domain.plugin.PluginCategory
import com.example.domain.plugin.PluginItemManifest
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class DynamicPluginEffect(
  val pluginId: String,
  val itemId: String,
  val colorMatrix: ColorMatrix?,
  val customGlslShader: String?,
  val parameters: Map<String, Any>
)

/**
 * Sandboxed Execution Engine for versioned Plugins (Filters, Effects, Transitions, Text Templates).
 * Safely executes plugin code/shaders with error containment and fallback handling.
 */
class PluginExecutionEngine(private val context: Context) {
  private val tag = "PluginExecutionEngine"
  private val activePluginEffects = ConcurrentHashMap<String, DynamicPluginEffect>()

  fun registerPluginEffect(plugin: InstalledPlugin, item: PluginItemManifest) {
    try {
      val colorMatrix = item.colorMatrix?.let { ColorMatrix(it) }
      val shaderFile = plugin.getItemFile(item)
      val customShader = if (shaderFile != null && shaderFile.name.endsWith(".glsl", ignoreCase = true)) {
        shaderFile.readText()
      } else null

      val effect = DynamicPluginEffect(
        pluginId = plugin.manifest.id,
        itemId = item.id,
        colorMatrix = colorMatrix,
        customGlslShader = customShader,
        parameters = item.parameters
      )
      activePluginEffects["${plugin.manifest.id}:${item.id}"] = effect
      Log.d(tag, "Registered dynamic plugin effect: ${item.name} (${item.id})")
    } catch (e: Exception) {
      Log.e(tag, "Failed to register plugin effect ${item.id}", e)
    }
  }

  fun getPluginEffect(pluginId: String, itemId: String): DynamicPluginEffect? {
    return activePluginEffects["$pluginId:$itemId"]
  }

  fun evaluatePluginFilter(item: PluginItemManifest): ColorMatrix? {
    return try {
      item.colorMatrix?.let { ColorMatrix(it) }
    } catch (e: Exception) {
      Log.w(tag, "Error evaluating plugin filter ${item.id}", e)
      null
    }
  }

  fun clear() {
    activePluginEffects.clear()
  }
}
