package com.example.engine.composition.gpu

import android.opengl.GLES20
import android.util.Log

/**
 * High-performance Ping-Pong Framebuffer (FBO) manager for multi-pass OpenGL ES rendering.
 *
 * Maintains two allocated [GlFramebuffer] instances (FBO A and FBO B).
 * In each render pass:
 *  - The shader reads from the current read texture.
 *  - The output is written to the current write FBO.
 *  - [swap] is called to alternate read and write targets for the next sequential pass.
 *
 * Guarantees zero memory allocations per frame and ensures texture recycling across pipeline runs.
 */
class GlPingPongFbo {
  companion object {
    private const val TAG = "GlPingPongFbo"
  }

  val fboA = GlFramebuffer()
  val fboB = GlFramebuffer()

  private var isSwapped = false
  var width: Int = 0
    private set
  var height: Int = 0
    private set

  /**
   * Prepares both framebuffers with the requested dimensions.
   * If the dimensions have not changed and FBOs are already valid, this is a no-op (zero allocation).
   */
  fun setup(w: Int, h: Int) {
    if (w <= 0 || h <= 0) return
    if (w == width && h == height) return

    width = w
    height = h
    fboA.setup(w, h)
    fboB.setup(w, h)
    isSwapped = false
    Log.d(TAG, "Initialized Ping-Pong FBOs with dimensions: ${w}x$h")
  }

  /**
   * Resets the swap state so that [fboA] is the initial write target.
   */
  fun reset() {
    isSwapped = false
  }

  /**
   * Swaps the roles of FBO A and FBO B for the next render pass.
   */
  fun swap() {
    isSwapped = !isSwapped
  }

  /**
   * Returns the FBO currently designated as the render write target.
   */
  fun getWriteFbo(): GlFramebuffer {
    return if (!isSwapped) fboA else fboB
  }

  /**
   * Returns the 2D texture ID of the FBO containing the output from the previous render pass.
   */
  fun getReadTextureId(): Int {
    return if (!isSwapped) fboB.getTextureId() else fboA.getTextureId()
  }

  /**
   * Returns the FBO currently designated as the read source.
   */
  fun getReadFbo(): GlFramebuffer {
    return if (!isSwapped) fboB else fboA
  }

  /**
   * Releases all underlying OpenGL textures and framebuffers.
   */
  fun release() {
    fboA.release()
    fboB.release()
    width = 0
    height = 0
    isSwapped = false
  }
}
