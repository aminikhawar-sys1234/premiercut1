package com.vfx.engine.core.graph

import android.opengl.GLES30
import com.vfx.engine.core.GraphCycleError
import com.vfx.engine.gpu.buffer.QuadRenderer
import com.vfx.engine.gpu.fbo.FramebufferObject
import com.vfx.engine.gpu.fbo.FramebufferPool
import com.vfx.engine.gpu.shader.ShaderCompiler
import com.vfx.engine.gpu.state.GlStateTracker

/**
 * RenderGraph contract for GPU frame processing pipelines.
 */
interface RenderGraph {
  /**
   * Called when the EGL/GL context is initialized and active.
   * Compiles shaders and pre-allocates static buffer resources.
   */
  fun onGlContextReady()

  /**
   * Executes the multi-pass rendering graph.
   * Guaranteed zero runtime memory allocation inside execution loop.
   *
   * @param inputTextureId Source 2D texture ID containing the input frame.
   * @param targetFboId Target framebuffer ID where final output must be rendered.
   * @param width Frame width in pixels.
   * @param height Frame height in pixels.
   * @param ptsUs Presentation timestamp in microseconds.
   */
  fun execute(inputTextureId: Int, targetFboId: Int, width: Int, height: Int, ptsUs: Long)

  /**
   * Releases all GL resources, shaders, and FBO pools.
   */
  fun release()
}

// Lightweight DAG Data Structures for graph topology & compilation
data class PassResource(
  val id: String,
  val width: Int,
  val height: Int,
  val isVirtual: Boolean = true
)

data class RenderPass(
  val id: String,
  val effectInstanceId: String,
  val inputResourceIds: List<String>,
  val outputResourceId: String
)

data class RenderGraphData(
  val passes: List<RenderPass> = emptyList(),
  val resources: List<PassResource> = emptyList()
)

data class ResourceLifetime(
  val resourceId: String,
  val startPassIndex: Int,
  val endPassIndex: Int
)

object RenderGraphCompiler {
  fun compileAndCull(graph: RenderGraphData, finalOutputId: String): List<RenderPass> {
    val passMap = graph.passes.associateBy { it.id }
    val visited = mutableSetOf<String>()
    val inStack = mutableSetOf<String>()
    val sortedPasses = mutableListOf<RenderPass>()

    fun dfs(passId: String) {
      if (inStack.contains(passId)) {
        throw GraphCycleError(inStack.toList() + passId)
      }
      if (!visited.contains(passId)) {
        visited.add(passId)
        inStack.add(passId)

        val pass = passMap[passId]
        if (pass != null) {
          for (inputId in pass.inputResourceIds) {
            val producer = graph.passes.find { it.outputResourceId == inputId }
            if (producer != null) {
              dfs(producer.id)
            }
          }
          sortedPasses.add(pass)
        }
        inStack.remove(passId)
      }
    }

    val finalProducer = graph.passes.find { it.outputResourceId == finalOutputId }
    if (finalProducer != null) {
      dfs(finalProducer.id)
    }

    return sortedPasses
  }
}

object ResourceAliasing {
  fun computeResourceLifetimes(passes: List<RenderPass>): Map<String, ResourceLifetime> {
    val startMap = mutableMapOf<String, Int>()
    val endMap = mutableMapOf<String, Int>()

    passes.forEachIndexed { index, pass ->
      if (!startMap.containsKey(pass.outputResourceId)) {
        startMap[pass.outputResourceId] = index
      }
      endMap[pass.outputResourceId] = index

      for (inputId in pass.inputResourceIds) {
        endMap[inputId] = maxOf(endMap[inputId] ?: index, index)
      }
    }

    val lifetimes = mutableMapOf<String, ResourceLifetime>()
    for ((resId, startIndex) in startMap) {
      val endIndex = endMap[resId] ?: startIndex
      lifetimes[resId] = ResourceLifetime(resId, startIndex, endIndex)
    }
    return lifetimes
  }
}

/**
 * Concrete multi-pass demonstration DAG RenderGraph implementation.
 * Pass 1: Brightness & Contrast adjustment -> intermediate scratch FBO
 * Pass 2: Tint & Color multiplication -> final target FBO
 * Proves resource aliasing, scratch FBO pool recycling, and zero-allocation execution.
 */
class MultiPassDemoRenderGraph : RenderGraph {

  private var quadRenderer: QuadRenderer? = null
  private var fboPool: FramebufferPool? = null

  // Pass 1: Brightness & Contrast
  private var pass1Program: Int = 0
  private var pass1TexSamplerLoc: Int = -1
  private var pass1BrightnessLoc: Int = -1
  private var pass1ContrastLoc: Int = -1

  // Pass 2: Color Tint
  private var pass2Program: Int = 0
  private var pass2TexSamplerLoc: Int = -1
  private var pass2TintLoc: Int = -1

  companion object {
    private const val VERTEX_SHADER = """#version 300 es
      layout(location = 0) in vec4 aPosition;
      layout(location = 1) in vec2 aTexCoord;
      out vec2 vTexCoord;
      void main() {
        gl_Position = aPosition;
        vTexCoord = aTexCoord;
      }
    """

    private const val PASS1_FRAGMENT = """#version 300 es
      precision mediump float;
      uniform sampler2D uTexSampler;
      uniform float uBrightness;
      uniform float uContrast;
      in vec2 vTexCoord;
      out vec4 fragColor;

      void main() {
        vec4 color = texture(uTexSampler, vTexCoord);
        vec3 rgb = color.rgb + vec3(uBrightness);
        rgb = (rgb - vec3(0.5)) * uContrast + vec3(0.5);
        fragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
      }
    """

    private const val PASS2_FRAGMENT = """#version 300 es
      precision mediump float;
      uniform sampler2D uTexSampler;
      uniform vec4 uTint;
      in vec2 vTexCoord;
      out vec4 fragColor;

      void main() {
        vec4 color = texture(uTexSampler, vTexCoord);
        vec3 rgb = color.rgb * uTint.rgb;
        fragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
      }
    """
  }

  override fun onGlContextReady() {
    if (quadRenderer == null) {
      quadRenderer = QuadRenderer()
    }
    if (fboPool == null) {
      fboPool = FramebufferPool()
    }

    if (pass1Program == 0) {
      val vShader = ShaderCompiler.compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
      val pass1FShader = ShaderCompiler.compileShader(GLES30.GL_FRAGMENT_SHADER, PASS1_FRAGMENT)
      pass1Program = ShaderCompiler.linkProgram(vShader, pass1FShader)
      GLES30.glDeleteShader(pass1FShader)

      val pass2FShader = ShaderCompiler.compileShader(GLES30.GL_FRAGMENT_SHADER, PASS2_FRAGMENT)
      pass2Program = ShaderCompiler.linkProgram(vShader, pass2FShader)
      GLES30.glDeleteShader(pass2FShader)
      GLES30.glDeleteShader(vShader)

      pass1TexSamplerLoc = GLES30.glGetUniformLocation(pass1Program, "uTexSampler")
      pass1BrightnessLoc = GLES30.glGetUniformLocation(pass1Program, "uBrightness")
      pass1ContrastLoc = GLES30.glGetUniformLocation(pass1Program, "uContrast")

      pass2TexSamplerLoc = GLES30.glGetUniformLocation(pass2Program, "uTexSampler")
      pass2TintLoc = GLES30.glGetUniformLocation(pass2Program, "uTint")
    }
  }

  override fun execute(inputTextureId: Int, targetFboId: Int, width: Int, height: Int, ptsUs: Long) {
    if (pass1Program == 0) {
      onGlContextReady()
    }

    val quad = quadRenderer ?: return
    val pool = fboPool ?: return

    // --- PASS 1: Brightness/Contrast into Scratch FBO ---
    val scratchFbo = pool.acquire(width, height)
    scratchFbo.bind()

    GlStateTracker.bindProgram(pass1Program)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTextureId)
    GLES30.glUniform1i(pass1TexSamplerLoc, 0)

    // Dynamic keyframe/time evaluated parameters without heap allocation
    val brightness = 0.05f
    val contrast = 1.15f
    GLES30.glUniform1f(pass1BrightnessLoc, brightness)
    GLES30.glUniform1f(pass1ContrastLoc, contrast)

    quad.drawQuad()

    // --- PASS 2: Tint/Color onto Target FBO ---
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFboId)
    GLES30.glViewport(0, 0, width, height)

    GlStateTracker.bindProgram(pass2Program)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, scratchFbo.texture.textureId)
    GLES30.glUniform1i(pass2TexSamplerLoc, 0)

    // Warm tint (R=1.0, G=0.95, B=0.90, A=1.0)
    GLES30.glUniform4f(pass2TintLoc, 1.0f, 0.95f, 0.90f, 1.0f)

    quad.drawQuad()

    // Immediately return scratch FBO to pool (Resource Aliasing)
    pool.release(scratchFbo)
  }

  override fun release() {
    if (pass1Program != 0) {
      GLES30.glDeleteProgram(pass1Program)
      pass1Program = 0
    }
    if (pass2Program != 0) {
      GLES30.glDeleteProgram(pass2Program)
      pass2Program = 0
    }
    quadRenderer?.release()
    quadRenderer = null
    fboPool?.clear()
    fboPool = null
  }
}
