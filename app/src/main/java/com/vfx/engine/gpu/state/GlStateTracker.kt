package com.vfx.engine.gpu.state

import android.opengl.GLES30

object GlCapabilities {
  var maxTextureSize: Int = 4096
    private set
  var supportsFloatTextures: Boolean = true
    private set

  fun queryCapabilities() {
    val params = IntArray(1)
    GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, params, 0)
    maxTextureSize = params[0]
  }
}

/**
 * Production-grade OpenGL ES 3.0 state tracker and state saver.
 * Caches GL state and allows saving/restoring GL_VIEWPORT, GL_BLEND, GL_FRAMEBUFFER_BINDING,
 * GL_ACTIVE_TEXTURE, GL_CURRENT_PROGRAM, and GL_VERTEX_ARRAY_BINDING to avoid state pollution.
 * Zero heap allocation during push/pop operations.
 */
object GlStateTracker {
  private var cachedProgram: Int = -1
  private var cachedFbo: Int = -1

  // Pre-allocated IntArrays for zero-allocation GL state queries
  private val tempInt1 = IntArray(1)
  private val tempInt4 = IntArray(4)

  // Saved State Snapshot Slots (supports single-level save/restore without allocations)
  private val savedViewport = IntArray(4)
  private var savedProgram: Int = 0
  private var savedFbo: Int = 0
  private var savedActiveTexture: Int = GLES30.GL_TEXTURE0
  private var savedVao: Int = 0
  private var savedVbo: Int = 0
  private var savedBlendEnabled: Boolean = false
  private var savedBlendSrcRgb: Int = GLES30.GL_ONE
  private var savedBlendDstRgb: Int = GLES30.GL_ZERO
  private var savedBlendSrcAlpha: Int = GLES30.GL_ONE
  private var savedBlendDstAlpha: Int = GLES30.GL_ZERO
  private var savedBlendEquationRgb: Int = GLES30.GL_FUNC_ADD
  private var savedBlendEquationAlpha: Int = GLES30.GL_FUNC_ADD

  fun bindProgram(programId: Int) {
    if (cachedProgram != programId) {
      cachedProgram = programId
      GLES30.glUseProgram(programId)
    }
  }

  fun bindFramebuffer(fboId: Int) {
    if (cachedFbo != fboId) {
      cachedFbo = fboId
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
    }
  }

  /**
   * Queries and caches current GLES30 state parameters.
   * Zero heap allocations.
   */
  fun saveState() {
    // 1. Viewport
    GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, savedViewport, 0)

    // 2. Program
    GLES30.glGetIntegerv(GLES30.GL_CURRENT_PROGRAM, tempInt1, 0)
    savedProgram = tempInt1[0]

    // 3. Framebuffer binding
    GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, tempInt1, 0)
    savedFbo = tempInt1[0]

    // 4. Active Texture Unit
    GLES30.glGetIntegerv(GLES30.GL_ACTIVE_TEXTURE, tempInt1, 0)
    savedActiveTexture = tempInt1[0]

    // 5. VAO / VBO
    GLES30.glGetIntegerv(GLES30.GL_VERTEX_ARRAY_BINDING, tempInt1, 0)
    savedVao = tempInt1[0]
    GLES30.glGetIntegerv(GLES30.GL_ARRAY_BUFFER_BINDING, tempInt1, 0)
    savedVbo = tempInt1[0]

    // 6. Blend state
    savedBlendEnabled = GLES30.glIsEnabled(GLES30.GL_BLEND)
    GLES30.glGetIntegerv(GLES30.GL_BLEND_SRC_RGB, tempInt1, 0)
    savedBlendSrcRgb = tempInt1[0]
    GLES30.glGetIntegerv(GLES30.GL_BLEND_DST_RGB, tempInt1, 0)
    savedBlendDstRgb = tempInt1[0]
    GLES30.glGetIntegerv(GLES30.GL_BLEND_SRC_ALPHA, tempInt1, 0)
    savedBlendSrcAlpha = tempInt1[0]
    GLES30.glGetIntegerv(GLES30.GL_BLEND_DST_ALPHA, tempInt1, 0)
    savedBlendDstAlpha = tempInt1[0]
    GLES30.glGetIntegerv(GLES30.GL_BLEND_EQUATION_RGB, tempInt1, 0)
    savedBlendEquationRgb = tempInt1[0]
    GLES30.glGetIntegerv(GLES30.GL_BLEND_EQUATION_ALPHA, tempInt1, 0)
    savedBlendEquationAlpha = tempInt1[0]
  }

  /**
   * Restores GL state saved by saveState().
   * Zero heap allocations.
   */
  fun restoreState() {
    // 1. Viewport
    GLES30.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3])

    // 2. Program
    bindProgram(savedProgram)

    // 3. Framebuffer
    bindFramebuffer(savedFbo)

    // 4. Active Texture
    GLES30.glActiveTexture(savedActiveTexture)

    // 5. VAO / VBO
    GLES30.glBindVertexArray(savedVao)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, savedVbo)

    // 6. Blend
    if (savedBlendEnabled) {
      GLES30.glEnable(GLES30.GL_BLEND)
      GLES30.glBlendFuncSeparate(savedBlendSrcRgb, savedBlendDstRgb, savedBlendSrcAlpha, savedBlendDstAlpha)
      GLES30.glBlendEquationSeparate(savedBlendEquationRgb, savedBlendEquationAlpha)
    } else {
      GLES30.glDisable(GLES30.GL_BLEND)
    }
  }

  inline fun <T> withStatePreserved(block: () -> T): T {
    saveState()
    try {
      return block()
    } finally {
      restoreState()
    }
  }

  fun resetCache() {
    cachedProgram = -1
    cachedFbo = -1
  }
}
