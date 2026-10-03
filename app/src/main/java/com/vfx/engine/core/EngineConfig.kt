package com.vfx.engine.core

/**
 * Global configuration policies for the VFX Effects Engine.
 */
data class EngineConfig(
  val maxVramCacheMb: Int = 512,
  val enableHighPrecisionFloatingPoint: Boolean = true,
  val maxParallelRenderThreads: Int = 4,
  val allowResourceAliasing: Boolean = true,
  val enableGpuQueryTiming: Boolean = false,
  val defaultColorSpace: com.vfx.engine.core.frame.ColorSpace = com.vfx.engine.core.frame.ColorSpace.BT709
)
