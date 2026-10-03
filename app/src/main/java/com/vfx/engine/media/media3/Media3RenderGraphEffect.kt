package com.vfx.engine.media.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.RenderGraphGlShaderProgram
import com.vfx.engine.core.graph.RenderGraph

/**
 * Production-grade Media3 [GlEffect] wrapping a custom multi-pass DAG [RenderGraph].
 */
@OptIn(UnstableApi::class)
class Media3RenderGraphEffect(
  val renderGraphProvider: () -> RenderGraph
) : GlEffect {

  constructor(renderGraph: RenderGraph) : this({ renderGraph })

  @Throws(VideoFrameProcessingException::class)
  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
    return try {
      RenderGraphGlShaderProgram(context, useHdr, renderGraphProvider)
    } catch (e: VideoFrameProcessingException) {
      throw e
    } catch (e: Exception) {
      throw VideoFrameProcessingException("Failed to initialize RenderGraphGlShaderProgram", e)
    }
  }
}
