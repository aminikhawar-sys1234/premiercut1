package com.example.engine.controller

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.composition.ComposedFrame
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.composition.gpu.GpuCompositionRenderer

/**
 * Coordinates OpenGL ES preview rendering and compositing pipeline:
 * Decoded Video Frame -> Text -> Stickers -> Overlays -> Filters -> Effects -> Transitions -> Final Preview Frame.
 *
 * Reuses GPU framebuffers and texture objects without per-frame allocations.
 */
class GpuRenderManager(
  private val context: Context,
  private val compositionEngine: VideoCompositionEngine,
  private val renderCacheManager: RenderCacheManager
) {

  companion object {
    private const val TAG = "GpuRenderManager"
  }

  private var gpuRenderer: GpuCompositionRenderer? = null
  private var isRendererInitialized = false
  private var surfaceWidth = 1920
  private var surfaceHeight = 1080

  fun initialize(width: Int = 1920, height: Int = 1080) {
    surfaceWidth = width
    surfaceHeight = height
    try {
      gpuRenderer = GpuCompositionRenderer(context).apply {
        initGl()
      }
      isRendererInitialized = true
      Log.d(TAG, "GpuRenderManager initialized with dimensions: ${surfaceWidth}x${surfaceHeight}")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to initialize OpenGL composition renderer, running in direct surface mode", e)
      isRendererInitialized = false
    }
  }

  fun resize(width: Int, height: Int) {
    surfaceWidth = width
    surfaceHeight = height
  }

  fun composeFrame(timeline: Timeline, timelinePosMs: Long): ComposedFrame {
    return compositionEngine.evaluateFrame(timeline, timelinePosMs)
  }

  fun invalidateClip(clipId: String) {
    gpuRenderer?.invalidateClip(clipId)
    renderCacheManager.invalidateClip(clipId)
  }

  fun invalidateAll() {
    gpuRenderer?.invalidateAll()
    renderCacheManager.clear()
  }

  fun release() {
    try {
      gpuRenderer?.release()
    } catch (e: Exception) {
      Log.w(TAG, "Error releasing GPU composition renderer", e)
    }
    gpuRenderer = null
    isRendererInitialized = false
  }
}
