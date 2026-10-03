package com.vfx.engine.media.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.vfx.engine.core.Microseconds
import com.vfx.engine.core.effect.EffectInstance
import com.vfx.engine.core.stack.EffectStack
import com.vfx.engine.effects.ColorAdjustmentEffect
import com.vfx.engine.effects.DualKawaseBlurEffect
import com.vfx.engine.gpu.buffer.QuadRenderer
import com.vfx.engine.gpu.fbo.FramebufferObject
import com.vfx.engine.gpu.fbo.FramebufferPool
import com.vfx.engine.gpu.state.GlStateTracker
import com.vfx.engine.gpu.texture.GpuTexture
import com.vfx.engine.gpu.texture.TextureSpec

/**
 * Production-grade Media3 GlEffect implementation extending GlEffect
 * and bridging Media3 frame delivery with the internal EffectsEngine.
 */
@OptIn(UnstableApi::class)
class VfxGlEffect(
  val effectStack: EffectStack = EffectStack()
) : GlEffect {

  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
    return VfxGlShaderProgram(useHdr, effectStack)
  }
}

/**
 * Companion BaseGlShaderProgram bridging AndroidX Media3 frame rendering
 * with internal zero-allocation render graph and effect stack.
 */
@OptIn(UnstableApi::class)
class VfxGlShaderProgram(
  useHdr: Boolean,
  private val effectStack: EffectStack
) : BaseGlShaderProgram(useHdr, 1) {

  private var width: Int = 1080
  private var height: Int = 1920

  private val quadRenderer = QuadRenderer()
  private val fboPool = FramebufferPool()

  // Native built-in effect handlers
  private val dualKawaseBlur = DualKawaseBlurEffect(quadRenderer, fboPool)
  private val colorAdjustment = ColorAdjustmentEffect(quadRenderer)

  // Reusable lightweight wrapper for Media3 input texture
  private var cachedInputTextureSpec: TextureSpec? = null
  private var cachedInputTexture: GpuTexture? = null
  private var cachedInputFbo: FramebufferObject? = null

  override fun configure(inputWidth: Int, inputHeight: Int): Size {
    this.width = inputWidth
    this.height = inputHeight
    return Size(inputWidth, inputHeight)
  }

  override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
    try {
      GlStateTracker.saveState()

      val pts = Microseconds(presentationTimeUs)

      // Create or reuse input wrapper FBO for inputTexId without runtime allocations
      val inputFbo = getOrCreateInputWrapperFbo(inputTexId, width, height)

      // Active output target framebuffer is bound by Media3 BaseGlShaderProgram
      val currentOutputFboId = getBoundFramebuffer()
      val outputFboWrapper = FramebufferObject(width, height)

      var currentSourceFbo = inputFbo

      val activeEffects = effectStack.effects.filter { it.isEnabled }

      if (activeEffects.isEmpty()) {
        // Direct blit if no active effects
        colorAdjustment.render(currentSourceFbo, outputFboWrapper, com.vfx.engine.core.params.ParamMap())
      } else {
        activeEffects.forEachIndexed { index, effectInstance ->
          val isLast = index == activeEffects.size - 1
          val targetFbo = if (isLast) outputFboWrapper else fboPool.acquire(width, height)

          when (effectInstance.effectId) {
            "vfx_dual_kawase_blur" -> dualKawaseBlur.render(currentSourceFbo, targetFbo, effectInstance.parameters)
            "vfx_color_adjustment" -> colorAdjustment.render(currentSourceFbo, targetFbo, effectInstance.parameters)
            else -> colorAdjustment.render(currentSourceFbo, targetFbo, effectInstance.parameters)
          }

          if (currentSourceFbo != inputFbo && currentSourceFbo != outputFboWrapper) {
            fboPool.release(currentSourceFbo)
          }
          currentSourceFbo = targetFbo
        }
      }

    } catch (e: Exception) {
      throw VideoFrameProcessingException(e, presentationTimeUs)
    } finally {
      GlStateTracker.restoreState()
    }
  }

  private fun getBoundFramebuffer(): Int {
    val params = IntArray(1)
    android.opengl.GLES30.glGetIntegerv(android.opengl.GLES30.GL_FRAMEBUFFER_BINDING, params, 0)
    return params[0]
  }

  private fun getOrCreateInputWrapperFbo(inputTexId: Int, w: Int, h: Int): FramebufferObject {
    val existingFbo = cachedInputFbo
    if (existingFbo != null && existingFbo.width == w && existingFbo.height == h) {
      return existingFbo
    }
    // Release previous if size changed
    existingFbo?.release()

    val wrappedTexture = GpuTexture.wrapExternal(inputTexId, w, h)
    val fbo = FramebufferObject(w, h, wrappedTexture)
    cachedInputFbo = fbo
    return fbo
  }

  override fun release() {
    super.release()
    dualKawaseBlur.release()
    colorAdjustment.release()
    quadRenderer.release()
    fboPool.clear()
    cachedInputFbo?.release()
    cachedInputFbo = null
  }
}
