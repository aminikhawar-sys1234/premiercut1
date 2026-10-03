package com.example.domain.plugin

import android.content.Context

enum class PluginLifecycleState {
  UNLOADED,
  INITIALIZED,
  ENABLED,
  DISABLED,
  ERROR
}

/**
 * Core interface for dynamic video editing extension modules.
 * Handles lifecycle events, capability registration, and state transitions.
 */
interface PluginModule {
  val id: String
  val name: String
  val version: String
  val category: PluginCategory
  val state: PluginLifecycleState

  /**
   * Initializes the plugin module with Application or UI Context.
   */
  fun onInitialize(context: Context)

  /**
   * Called when the plugin module is enabled.
   */
  fun onEnable()

  /**
   * Called when the plugin module is disabled.
   */
  fun onDisable()

  /**
   * Called when the plugin module is uninstalled or unloaded from memory.
   */
  fun onUnload()

  /**
   * Retrieves asset manifests and capabilities provided by this extension module.
   */
  fun getCapabilities(): List<PluginItemManifest>
}

/**
 * Standard Default Implementation of PluginModule for ZIP extension packages.
 */
open class StandardPluginModule(
  val plugin: InstalledPlugin
) : PluginModule {
  override val id: String = plugin.manifest.id
  override val name: String = plugin.manifest.name
  override val version: String = plugin.manifest.version
  override val category: PluginCategory = plugin.manifest.category

  private var _state: PluginLifecycleState = if (plugin.isEnabled) PluginLifecycleState.ENABLED else PluginLifecycleState.DISABLED
  override val state: PluginLifecycleState get() = _state

  override fun onInitialize(context: Context) {
    if (_state == PluginLifecycleState.UNLOADED) {
      _state = PluginLifecycleState.INITIALIZED
    }
  }

  override fun onEnable() {
    _state = PluginLifecycleState.ENABLED
  }

  override fun onDisable() {
    _state = PluginLifecycleState.DISABLED
  }

  override fun onUnload() {
    _state = PluginLifecycleState.UNLOADED
  }

  override fun getCapabilities(): List<PluginItemManifest> {
    return plugin.manifest.items
  }
}
