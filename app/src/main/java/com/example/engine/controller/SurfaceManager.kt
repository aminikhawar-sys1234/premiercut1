package com.example.engine.controller

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView

/** Lifecycle-safe preview surface adapter. It never owns playback state. */
class SurfaceManager(
  private val playbackController: PlaybackController
) {
  companion object {
    private const val TAG = "SurfaceManager"
  }

  private var activeSurface: Surface? = null
  private var isSurfaceAvailable = false
  private var lastValidFrame: Bitmap? = null

  val isAvailable: Boolean get() = isSurfaceAvailable && activeSurface?.isValid == true
  var onSurfaceAvailabilityChanged: ((Boolean) -> Unit)? = null

  fun attachSurfaceView(surfaceView: SurfaceView) {
    surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
      override fun surfaceCreated(holder: SurfaceHolder) = handleSurfaceCreated(holder.surface)
      override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
        handleSurfaceChanged(holder.surface, width, height)
      override fun surfaceDestroyed(holder: SurfaceHolder) = handleSurfaceDestroyed(holder.surface)
    })
    if (surfaceView.holder.surface.isValid) handleSurfaceCreated(surfaceView.holder.surface)
  }

  fun attachTextureView(textureView: TextureView) {
    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        handleSurfaceCreated(Surface(st))
      }
      override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit
      override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        activeSurface?.let(::handleSurfaceDestroyed)
        return true
      }
      override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
    }
    if (textureView.isAvailable && textureView.surfaceTexture != null) {
      handleSurfaceCreated(Surface(textureView.surfaceTexture))
    }
  }

  fun handleSurfaceCreated(surface: Surface) {
    if (!surface.isValid) {
      Log.w(TAG, "Ignoring invalid preview surface")
      return
    }
    activeSurface?.takeIf { it !== surface }?.let { old ->
      try { old.release() } catch (_: Exception) { }
    }
    activeSurface = surface
    isSurfaceAvailable = true
    playbackController.setSurface(surface)
    onSurfaceAvailabilityChanged?.invoke(true)
  }

  fun handleSurfaceChanged(surface: Surface, width: Int, height: Int) {
    if (surface.isValid && surface !== activeSurface) {
      activeSurface = surface
      isSurfaceAvailable = true
      playbackController.setSurface(surface)
    }
  }

  fun handleSurfaceDestroyed(surface: Surface) {
    if (surface === activeSurface || !surface.isValid) {
      isSurfaceAvailable = false
      playbackController.clearSurface()
      activeSurface = null
      onSurfaceAvailabilityChanged?.invoke(false)
    }
  }

  fun storeLastValidFrame(bitmap: Bitmap?) {
    if (bitmap != null && !bitmap.isRecycled) lastValidFrame = bitmap
  }

  fun getLastValidFrame(): Bitmap? = lastValidFrame

  fun release() {
    isSurfaceAvailable = false
    playbackController.clearSurface()
    activeSurface = null
    lastValidFrame = null
  }
}
