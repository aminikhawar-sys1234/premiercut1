package androidx.media3.effect

import android.content.Context
import android.opengl.GLES30
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import com.vfx.engine.core.graph.RenderGraph
import com.vfx.engine.gpu.state.GlStateTracker

/**
 * Custom Media3 [GlShaderProgram] integrating a multi-pass [RenderGraph] with zero-allocation
 * per-frame execution. Placed in `androidx.media3.effect` package to access Media3 internal
 * package-private `outputTexturePool` and texture handles.
 */
@OptIn(UnstableApi::class)
class RenderGraphGlShaderProgram(
  context: Context,
  useHdr: Boolean,
  renderGraphProvider: () -> RenderGraph
) : BaseGlShaderProgram(useHdr, 1) {

  private val renderGraph: RenderGraph
  private var width: Int = 1080
  private var height: Int = 1920

  private var scratchFboId: Int = 0

  init {
    try {
      renderGraph = renderGraphProvider()
      renderGraph.onGlContextReady()

      val fbos = IntArray(1)
      GLES30.glGenFramebuffers(1, fbos, 0)
      scratchFboId = fbos[0]
      if (scratchFboId == 0) {
        throw VideoFrameProcessingException("Failed to generate GL framebuffer handle for RenderGraph")
      }
    } catch (e: Exception) {
      throw VideoFrameProcessingException(e)
    }
  }

  override fun configure(inputWidth: Int, inputHeight: Int): Size {
    this.width = inputWidth
    this.height = inputHeight
    return Size(inputWidth, inputHeight)
  }

  override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
    try {
      // 1. Fetch output texture wrapper from Media3 internal texture pool
      val outputTexture = outputTexturePool.useTexture()

      // 2. Protect & Isolate OpenGL state
      GlStateTracker.saveState()

      // 3. Bind scratch FBO & attach Media3 output texture to GL_COLOR_ATTACHMENT0
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, scratchFboId)
      GLES30.glFramebufferTexture2D(
        GLES30.GL_FRAMEBUFFER,
        GLES30.GL_COLOR_ATTACHMENT0,
        GLES30.GL_TEXTURE_2D,
        outputTexture.texId,
        0
      )

      // 4. Validate Framebuffer Completeness
      val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
      if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
        throw VideoFrameProcessingException(
          "Framebuffer incomplete in RenderGraphGlShaderProgram: 0x${Integer.toHexString(status)}",
          presentationTimeUs
        )
      }

      // 5. Set glViewport to target dimensions
      GLES30.glViewport(0, 0, width, height)

      // 6. Execute RenderGraph multi-pass pipeline directly onto scratch FBO
      renderGraph.execute(inputTexId, scratchFboId, width, height, presentationTimeUs)

    } catch (e: Exception) {
      if (e is VideoFrameProcessingException) {
        throw e
      } else {
        throw VideoFrameProcessingException(e, presentationTimeUs)
      }
    } finally {
      // 7. Restore GL state to preserve downstream Media3 pipeline state
      GlStateTracker.restoreState()
    }
  }

  override fun release() {
    try {
      if (scratchFboId != 0) {
        val fbos = intArrayOf(scratchFboId)
        GLES30.glDeleteFramebuffers(1, fbos, 0)
        scratchFboId = 0
      }
      renderGraph.release()
    } catch (e: Exception) {
      // Swallow GL errors on shutdown
    } finally {
      super.release()
    }
  }
}
