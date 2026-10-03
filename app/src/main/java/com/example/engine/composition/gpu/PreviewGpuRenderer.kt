package com.example.engine.composition.gpu

/**
 * Phase 2 GPU preview facade. Reuses the existing native renderer and bridge rather than
 * introducing a second rendering stack or changing existing UI design.
 */
class PreviewGpuRenderer {
  private var nativeHandle: Long = 0L

  @Synchronized
  fun initialize(width: Int, height: Int): Boolean {
    if (nativeHandle == 0L) {
      nativeHandle = NativeRenderBridge.init(width.coerceAtLeast(1), height.coerceAtLeast(1))
    } else {
      NativeRenderBridge.resize(nativeHandle, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }
    return isInitialized()
  }

  @Synchronized
  fun resize(width: Int, height: Int) {
    if (nativeHandle != 0L) NativeRenderBridge.resize(nativeHandle, width.coerceAtLeast(1), height.coerceAtLeast(1))
  }

  @Synchronized
  fun render(layers: List<NativeLayer>) {
    if (nativeHandle != 0L && layers.isNotEmpty()) NativeRenderBridge.renderFrame(nativeHandle, layers)
  }

  @Synchronized
  fun onContextLost() {
    if (nativeHandle != 0L) {
      NativeRenderBridge.onContextLost(nativeHandle)
    }
  }

  @Synchronized
  fun release() {
    if (nativeHandle != 0L) {
      NativeRenderBridge.release(nativeHandle)
      nativeHandle = 0L
    }
  }

  fun isInitialized(): Boolean = nativeHandle != 0L
}
