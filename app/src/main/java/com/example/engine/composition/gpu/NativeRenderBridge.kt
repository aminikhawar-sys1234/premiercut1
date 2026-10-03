package com.example.engine.composition.gpu

import android.util.Log

enum class NativeBlendMode(val id: Int) { NORMAL(0), ADDITIVE(1), MULTIPLY(2), SCREEN(3), PREMULTIPLIED(4) }
enum class NativeLayerType(val id: Int) { BASE_VIDEO(0), VIDEO(1), IMAGE_STICKER(2), EFFECT_OVERLAY(3), TEXT(4) }
enum class NativeEffectType(val id: Int) { NONE(0), COLOR_ADJUST(1), VIGNETTE(2), GLITCH(3), CHROMATIC_ABERRATION(4), SHARPEN(5) }

data class NativeLayer(
  val id: Long = 0L, val textureId: Int = 0, val type: NativeLayerType = NativeLayerType.BASE_VIDEO,
  val isVisible: Boolean = true, val zOrder: Int = 0, val posX: Float = 0f, val posY: Float = 0f,
  val scaleX: Float = 1f, val scaleY: Float = 1f, val rotation: Float = 0f,
  val width: Float = 1f, val height: Float = 1f, val opacity: Float = 1f,
  val uOffset: Float = 0f, val vOffset: Float = 0f, val uScale: Float = 1f, val vScale: Float = 1f,
  val blendMode: NativeBlendMode = NativeBlendMode.NORMAL, val useCustomMatrix: Boolean = false,
  val transformMatrix: FloatArray? = null
)

object NativeRenderBridge {
  private const val TAG = "NativeRenderBridge"
  private const val LAYER_STRIDE = 35
  private var isLibraryLoaded = false
  val isLoaded: Boolean get() = isLibraryLoaded

  init { loadLibrary() }

  @Synchronized fun loadLibrary(): Boolean {
    if (isLibraryLoaded) return true
    return try { System.loadLibrary("ah_engine"); isLibraryLoaded = true; true }
    catch (e: Throwable) { Log.d(TAG, "Native ah_engine not loaded, fallback enabled", e); false }
  }

  fun init(width: Int, height: Int): Long {
    if (!isLibraryLoaded && !loadLibrary()) return 0L
    return try { nativeInit(width, height) }
    catch (e: Throwable) { Log.e(TAG, "nativeInit failed", e); 0L }
  }

  fun resize(handle: Long, width: Int, height: Int) {
    if (handle != 0L) runCatching { nativeResize(handle, width, height) }.onFailure { Log.e(TAG, "nativeResize failed", it) }
  }

  /** Direct decoder SurfaceTexture/OES path. No Bitmap or CPU pixel readback is performed. */
  fun renderExternalTexture(handle: Long, textureId: Int, texMatrix: FloatArray? = null) {
    if (handle == 0L || textureId <= 0) return
    try { nativeRenderExternalTexture(handle, textureId, texMatrix) } catch (e: Throwable) { Log.e(TAG, "nativeRenderExternalTexture failed", e) }
  }

  fun renderFrame(handle: Long, layers: List<NativeLayer>) {
    if (handle == 0L || layers.isEmpty()) return
    val buffer = FloatArray(layers.size * LAYER_STRIDE)
    var o = 0
    for (layer in layers) {
      buffer[o] = layer.id.toFloat(); buffer[o+1] = layer.textureId.toFloat(); buffer[o+2] = layer.type.id.toFloat(); buffer[o+3] = if (layer.isVisible) 1f else 0f; buffer[o+4] = layer.zOrder.toFloat()
      buffer[o+5] = layer.posX; buffer[o+6] = layer.posY; buffer[o+7] = layer.scaleX; buffer[o+8] = layer.scaleY; buffer[o+9] = layer.rotation; buffer[o+10] = layer.width; buffer[o+11] = layer.height; buffer[o+12] = layer.opacity
      buffer[o+13] = layer.uOffset; buffer[o+14] = layer.vOffset; buffer[o+15] = layer.uScale; buffer[o+16] = layer.vScale; buffer[o+17] = layer.blendMode.id.toFloat(); buffer[o+18] = if (layer.useCustomMatrix && layer.transformMatrix != null) 1f else 0f
      if (layer.useCustomMatrix && layer.transformMatrix != null && layer.transformMatrix.size >= 16) System.arraycopy(layer.transformMatrix, 0, buffer, o+19, 16)
      o += LAYER_STRIDE
    }
    try { nativeRenderFrame(handle, buffer, layers.size) } catch (e: Throwable) { Log.e(TAG, "nativeRenderFrame failed", e) }
  }

  fun beginOffscreen(handle: Long) {
    if (handle != 0L) runCatching { nativeBeginOffscreen(handle) }
  }

  fun endOffscreen(handle: Long): Int {
    return if (handle != 0L) runCatching { nativeEndOffscreen(handle) }.getOrDefault(0) else 0
  }

  fun applyEffect(handle: Long, effectType: NativeEffectType, intensity: Float = 1.0f, param1: Float = 0f, param2: Float = 0f, timeMs: Float = 0f) {
    if (handle != 0L && effectType != NativeEffectType.NONE) {
      runCatching { nativeApplyEffect(handle, effectType.id, intensity, param1, param2, timeMs) }.onFailure { Log.e(TAG, "nativeApplyEffect failed", it) }
    }
  }

  fun getLastFrameMs(handle: Long): Float {
    return if (handle != 0L) runCatching { nativeGetLastFrameMs(handle) }.getOrDefault(0.0f) else 0.0f
  }

  fun onContextLost(handle: Long) {
    if (handle != 0L) runCatching { nativeOnContextLost(handle) }
  }

  fun release(handle: Long) {
    if (handle != 0L) runCatching { nativeRelease(handle) }
  }

  private external fun nativeInit(width: Int, height: Int): Long
  private external fun nativeResize(handle: Long, width: Int, height: Int)
  private external fun nativeRenderFrame(handle: Long, layerData: FloatArray, layerCount: Int)
  private external fun nativeRenderExternalTexture(handle: Long, textureId: Int, texMatrix: FloatArray?)
  private external fun nativeBeginOffscreen(handle: Long)
  private external fun nativeEndOffscreen(handle: Long): Int
  private external fun nativeApplyEffect(handle: Long, effectType: Int, intensity: Float, param1: Float, param2: Float, timeMs: Float)
  private external fun nativeGetLastFrameMs(handle: Long): Float
  private external fun nativeOnContextLost(handle: Long)
  private external fun nativeRelease(handle: Long)
}
