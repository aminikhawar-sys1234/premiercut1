package com.example.engine.composition.gpu

import android.opengl.GLES20
import com.example.engine.effects.ml.AdvancedHumanAnalysis
import com.example.engine.effects.ml.DeformationGraph
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Reusable GLES2 deformation pass. The source texture stays GPU resident;
 * only vertex positions/UVs are uploaded. The subject mask is sampled in the
 * fragment shader so background pixels are never displaced by the deformation.
 */
class GpuDeformationRenderer {
  private var program = 0
  private var positionBuffer: FloatBuffer? = null
  private var uvBuffer: FloatBuffer? = null

  fun init() {
    if (program != 0) return
    program = GlShaderUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
  }

  fun render(
    textureId: Int,
    maskTextureId: Int,
    width: Int,
    height: Int,
    mesh: AdvancedHumanAnalysis.FaceMeshState,
    parameters: DeformationGraph.Parameters
  ) {
    if (program == 0 || textureId <= 0 || width <= 0 || height <= 0 || mesh.vertices.isEmpty()) return
    val safe = parameters.safe()
    val vertices = FloatArray(mesh.triangles.size * 6)
    val uvs = FloatArray(vertices.size)
    var k = 0
    var u = 0
    val bounds = mesh.bounds
    val center = DeformationGraph.Vec2(bounds.centerX(), bounds.centerY())
    val radius = maxOf(bounds.width(), bounds.height()) * safe.radius * .5f
    for (t in mesh.triangles) {
      val ids = intArrayOf(t.a, t.b, t.c)
      for (id in ids) {
        val v = mesh.vertices.getOrNull(id) ?: continue
        val original = DeformationGraph.Vec2(v.x, v.y)
        val warped = DeformationGraph.scaleRegion(
          original, center,
          1f - .18f * safe.intensity * safe.horizontalScale,
          1f + .08f * safe.intensity * safe.verticalScale,
          radius,
          safe.falloff
        )
        val out = DeformationGraph.maskWeightedWarp(original, warped, 1f, safe.maskStrength)
        val p = DeformationGraph.safePoint(out)
        vertices[k++] = (p.x / width) * 2f - 1f
        vertices[k++] = 1f - (p.y / height) * 2f
        uvs[u++] = (v.x / width).coerceIn(0f, 1f)
        uvs[u++] = (v.y / height).coerceIn(0f, 1f)
      }
    }
    positionBuffer = direct(vertices.copyOf(k))
    uvBuffer = direct(uvs.copyOf(u))

    GLES20.glUseProgram(program)
    val aPos = GLES20.glGetAttribLocation(program, "aPosition")
    val aUv = GLES20.glGetAttribLocation(program, "aUv")
    val src = GLES20.glGetUniformLocation(program, "uTexture")
    val mask = GLES20.glGetUniformLocation(program, "uMask")
    GLES20.glEnableVertexAttribArray(aPos)
    GLES20.glEnableVertexAttribArray(aUv)
    GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)
    GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, uvBuffer)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    GLES20.glUniform1i(src, 0)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
    GLES20.glUniform1i(mask, 1)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, k / 2)
    GLES20.glDisableVertexAttribArray(aPos)
    GLES20.glDisableVertexAttribArray(aUv)
  }

  fun release() {
    if (program != 0) GLES20.glDeleteProgram(program)
    program = 0
    positionBuffer = null
    uvBuffer = null
  }

  private fun direct(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }

  companion object {
    private const val VERTEX_SHADER = """
      attribute vec2 aPosition;
      attribute vec2 aUv;
      varying vec2 vUv;
      void main(){ vUv=aUv; gl_Position=vec4(aPosition,0.0,1.0); }
    """
    private const val FRAGMENT_SHADER = """
      precision mediump float;
      uniform sampler2D uTexture;
      uniform sampler2D uMask;
      varying vec2 vUv;
      void main(){
        vec4 src=texture2D(uTexture,vUv);
        float m=texture2D(uMask,vUv).r;
        gl_FragColor=vec4(src.rgb,src.a*m);
      }
    """
  }
}
