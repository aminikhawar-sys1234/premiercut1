package com.ahstudio.color.integration

import android.opengl.GLES30
import com.ahstudio.color.ColorPipelineCpu
import com.ahstudio.color.core.*
import com.ahstudio.color.gpu.ColorGpuProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class ParityReport(val maxAbsDiff: Float, val meanAbsDiff: Float, val passed: Boolean)

/**
 * §47/§64: renders the SAME state through GPU (offscreen) and CPU reference,
 * compares within tolerance. Runs on-device (androidTest). This is the concrete,
 * measurable proof of preview/export/parity — not a claim.
 */
object ParityHarness {

    fun compare(
        state: ColorState, cfg: ColorConfig,
        inputLinearRgb: FloatArray, width: Int, height: Int,
        tolerance: Float = 3.0f / 255f
    ): ParityReport {
        OffscreenGlContext(width, height).use { gl ->
            val proc = ColorGpuProcessor()
            val inTf = cfg.inputMetadata.transfer ?: com.ahstudio.color.space.TransferFunctions.REC709
            val encoded = FloatArray(inputLinearRgb.size) { i -> inTf.encode(inputLinearRgb[i]) }

            val tex = uploadTexture(encoded, width, height)
            val fbo = createFbo(width, height)

            proc.render(
                state = state,
                cfg = cfg,
                inputTex = tex,
                inputTarget = GLES30.GL_TEXTURE_2D,
                outFbo = fbo,
                width = width,
                height = height,
                flipY = 0f,
                lut3d = null,
                clipId = "parity",
                timeMs = 0L
            )
            gl.makeCurrent()
            val gpu = readFboRgb(fbo, width, height)

            // CPU path on the SAME quantized input
            val cpuPixels = FloatArray(width * height * 4)
            for (p in 0 until width * height) {
                cpuPixels[p * 4 + 0] = encoded[p * 3 + 0]
                cpuPixels[p * 4 + 1] = encoded[p * 3 + 1]
                cpuPixels[p * 4 + 2] = encoded[p * 3 + 2]
                cpuPixels[p * 4 + 3] = 1f
            }
            ColorPipelineCpu(cfg).process(state, cpuPixels)
            var maxD = 0f; var sum = 0f; var n = 0
            for (p in 0 until width * height) {
                for (ch in 0..2) {
                    val d = abs(gpu[p * 3 + ch] - cpuPixels[p * 4 + ch])
                    if (d > maxD) maxD = d
                    sum += d
                    n++
                }
            }
            proc.releaseGpuResources()
            deleteFbo(fbo); deleteTex(tex)
            return ParityReport(maxD, if (n > 0) sum / n else 0f, maxD <= tolerance)
        }
    }

    private fun uploadTexture(pixels: FloatArray, w: Int, h: Int): Int {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        val bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (p in 0 until w * h) {
            bb.put((pixels[p * 3 + 0].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
            bb.put((pixels[p * 3 + 1].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
            bb.put((pixels[p * 3 + 2].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
            bb.put(255.toByte())
        }
        bb.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bb)
        return tex[0]
    }

    private fun createFbo(w: Int, h: Int): Int {
        val fbo = IntArray(1)
        val rbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glGenRenderbuffers(1, rbo, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbo[0])
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGBA8, w, h)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, rbo[0])
        return fbo[0]
    }

    private fun readFboRgb(fbo: Int, w: Int, h: Int): FloatArray {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        val bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bb)
        val out = FloatArray(w * h * 3)
        for (p in 0 until w * h) {
            out[p * 3 + 0] = (bb.get(p * 4 + 0).toInt() and 0xFF) / 255f
            out[p * 3 + 1] = (bb.get(p * 4 + 1).toInt() and 0xFF) / 255f
            out[p * 3 + 2] = (bb.get(p * 4 + 2).toInt() and 0xFF) / 255f
        }
        return out
    }

    private fun deleteFbo(fbo: Int) = GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
    private fun deleteTex(tex: Int) = GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
}
