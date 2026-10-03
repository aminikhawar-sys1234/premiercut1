package com.ahstudio.color.gpu

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.ahstudio.color.cache.ColorPipelineCache
import com.ahstudio.color.core.ColorConfig
import com.ahstudio.color.core.ColorState
import com.ahstudio.color.lut.Lut3D
import com.ahstudio.color.lut.LutCache
import com.ahstudio.color.primary.WhiteBalance
import com.ahstudio.color.space.ColorSpaceConverter
import com.ahstudio.color.space.TransferFunctions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

interface MaskProvider {
    /** R-channel mask texture sampled at vUv, or null if unavailable this frame. */
    fun maskTexture(clipId: String, maskId: String, timeMs: Long): Int?
}

/**
 * Single GPU color processor. ONE INSTANCE serves BOTH preview and export (§47):
 * identical shader variants, identical uniform packing, identical LUT textures.
 * All methods must run on the GL thread (GlDispatcher).
 */
class ColorGpuProcessor(
    private val shaderManager: ColorShaderManager = ColorShaderManager(),
    private val lutTextures: LutTextureManager = LutTextureManager(),
    private val lutCache: LutCache = LutCache(),
    private val maskProvider: MaskProvider? = null
) {
    private val pipelineCache = ColorPipelineCache()
    private var vao = 0
    private var vbo = 0
    private var initialized = false

    // Cached curve textures (§49: no per-frame uploads)
    private var curveTex = 0
    private var lumaCurveTex = 0
    private var curvesBakedFor = Int.MIN_VALUE

    fun ensureInit() {
        if (initialized) return
        val vaoArr = IntArray(1); GLES30.glGenVertexArrays(1, vaoArr, 0); vao = vaoArr[0]
        val vboArr = IntArray(1); GLES30.glGenBuffers(1, vboArr, 0); vbo = vboArr[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val bb = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f).forEach { bb.putFloat(it) }
        bb.position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 32, bb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        initialized = true
    }

    /**
     * Draw the color grade.
     * @param inputTex source texture (2D or EXTERNAL_OES)
     * @param outFbo framebuffer to render into (0 = default)
     * @param lut3d resolved 3D LUT or null (host resolves id -> data via LutRepository)
     */
    fun render(
        state: ColorState,
        cfg: ColorConfig,
        inputTex: Int,
        inputTarget: Int,
        outFbo: Int,
        width: Int,
        height: Int,
        flipY: Float,
        lut3d: Lut3D?,
        clipId: String = "",
        timeMs: Long = 0L
    ) {
        ensureInit()
        val caps = GpuCaps.detect()
        val key = pipelineCache.resolve(
            state, cfg,
            inputTarget == GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            caps.glVersionMajor >= 3,
            width
        ) {
            shaderManager.variantKey(
                state, cfg,
                inputTarget == GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                caps.glVersionMajor >= 3,
                maskProvider != null
            )
        }
        val prog = shaderManager.getProgram(key)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outFbo)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(prog.id)
        GLES30.glBindVertexArray(vao)

        // Input texture — unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(inputTarget, inputTex)
        if (inputTarget != GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            GLES30.glUniform1i(prog.loc("uInput"), 0)

        GLES30.glUniform1f(prog.loc("uFlipY"), flipY)

        // Matrices & luma
        val work = cfg.workingSpace
        val inSpace = cfg.inputMetadata.colorSpace ?: work
        GLES30.glUniformMatrix3fv(prog.loc("uInToWork"), 1, false, ColorSpaceConverter.inputToWorking(inSpace, work), 0)
        GLES30.glUniformMatrix3fv(prog.loc("uWorkToOut"), 1, false, ColorSpaceConverter.workingToOutput(work, cfg.outputSpace), 0)
        GLES30.glUniformMatrix3fv(prog.loc("uMixer"), 1, false, mixerMatrix(state), 0)
        val lum = work.lumaCoefficients()
        GLES30.glUniform4f(prog.loc("uLuma"), lum[0], lum[1], lum[2],
            if (cfg.inputMetadata.range == com.ahstudio.color.core.ColorRange.LIMITED && cfg.inputMetadata.isMetadataReliable) 1f else 0f)

        // Primary
        GLES30.glUniform4f(prog.loc("uPrimary"),
            2.0.pow(state.exposure.toDouble()).toFloat(),
            state.contrast, state.contrastPivot, state.saturation)
        GLES30.glUniform4f(prog.loc("uTone"), state.highlights, state.shadows, state.whites, state.blacks)
        val wb = WhiteBalance.computeGains(state.temperature, state.tint, work)
        GLES30.glUniform4f(prog.loc("uWb"), wb[0], wb[1], wb[2], 0f)
        GLES30.glUniform4f(prog.loc("uVib"), state.vibrance, state.skinProtect, 30f / 360f, 0f)

        // Output config
        val inTf = cfg.inputMetadata.transfer ?: TransferFunctions.REC709
        GLES30.glUniform4f(prog.loc("uOut"),
            if (state.hdr.gamutCompress || cfg.outputSpace !== work) 1f else 0f,
            if (cfg.hdrOutput) 0f else state.hdr.toneMap.id.toFloat(),
            inTf.id.toFloat(),
            cfg.outputTransfer.id.toFloat())
        GLES30.glUniform4f(prog.loc("uMisc"),
            cfg.workingTransfer.id.toFloat(),
            state.splitTone.strength,
            if (state.bw.enabled) 1f else 0f,
            state.bw.strength)
        GLES30.glUniform4f(prog.loc("uSplit"),
            state.splitTone.shadowHue, state.splitTone.shadowSat,
            state.splitTone.highlightHue, state.splitTone.highlightSat)
        GLES30.glUniform4f(prog.loc("uSplitBal"), state.splitTone.balance, 0f, 0f, 0f)
        val bwSum = (state.bw.weightR + state.bw.weightG + state.bw.weightB).coerceAtLeast(1e-4f)
        GLES30.glUniform3f(prog.loc("uBw"),
            state.bw.weightR / bwSum,
            state.bw.weightG / bwSum,
            state.bw.weightB / bwSum)

        // Curves — bake once per state change (§49)
        if (key.hasCurves) {
            if (curvesBakedFor != state.curves.hashCode()) bakeCurveTextures(state)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curveTex)
            GLES30.glUniform1i(prog.loc("uCurveTex"), 1)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lumaCurveTex)
            GLES30.glUniform1i(prog.loc("uLumaCurveTex"), 2)
        }

        // Wheels
        if (key.hasWheels) {
            GLES30.glUniform4f(prog.loc("uWheelLift"), state.wheels.lift.x, state.wheels.lift.y, state.wheels.lift.z, state.wheels.lift.master)
            GLES30.glUniform4f(prog.loc("uWheelGamma"), state.wheels.gamma.x, state.wheels.gamma.y, state.wheels.gamma.z, state.wheels.gamma.master)
            GLES30.glUniform4f(prog.loc("uWheelGain"), state.wheels.gain.x, state.wheels.gain.y, state.wheels.gain.z, state.wheels.gain.master)
            GLES30.glUniform4f(prog.loc("uWheelOffset"), state.wheels.offset.x, state.wheels.offset.y, state.wheels.offset.z, state.wheels.offset.master)
        }

        // HSL
        if (key.hasHsl) {
            val arr = FloatArray(32)
            val bands = state.hsl.toArray()
            val centers = com.ahstudio.color.hsl.HslEngine.CENTERS
            bands.forEachIndexed { i, b ->
                arr[i * 4 + 0] = centers[i]; arr[i * 4 + 1] = b.hueShift
                arr[i * 4 + 2] = b.sat; arr[i * 4 + 3] = b.lum
            }
            GLES30.glUniform4fv(prog.loc("uHslBands[0]"), 8, arr, 0)
        }

        // LUT — texture uploaded once per LUT (§34)
        if (key.hasLut3d && lut3d != null) {
            val texId = lutTextures.getOrCreate(lut3d, state.lut!!.lutId, caps.supportsFloatTextures)
            val p = lutTextures.lutParams(lut3d)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
            GLES30.glUniform1i(prog.loc("uLut3d"), 3)
            GLES30.glUniform3f(prog.loc("uLutScale"), p[0], p[1], p[2])
            GLES30.glUniform3f(prog.loc("uLutOffset"), p[3], p[4], p[5])
            GLES30.glUniform1f(prog.loc("uLutIntensity"), state.lut!!.intensity)
        }

        // Secondaries
        val secs = state.secondaries.filter { it.enabled }.take(2)
        if (key.secCount > 0) {
            val hue = FloatArray(8); val sat = FloatArray(8); val lumA = FloatArray(8); val corr = FloatArray(8)
            secs.forEachIndexed { i, s ->
                hue[i*4+0]=s.hueCenter; hue[i*4+1]=s.hueWidth; hue[i*4+2]=s.hueSoftness; hue[i*4+3]=if (s.invert)1f else 0f
                sat[i*4+0]=s.satCenter; sat[i*4+1]=s.satWidth; sat[i*4+2]=s.satSoftness
                lumA[i*4+0]=s.lumCenter; lumA[i*4+1]=s.lumWidth; lumA[i*4+2]=s.lumSoftness
                corr[i*4+0]=s.gainR; corr[i*4+1]=s.gainG; corr[i*4+2]=s.gainB; corr[i*4+3]=s.intensity
            }
            GLES30.glUniform4fv(prog.loc("uSecHue[0]"), 2, hue, 0)
            GLES30.glUniform4fv(prog.loc("uSecSat[0]"), 2, sat, 0)
            GLES30.glUniform4fv(prog.loc("uSecLum[0]"), 2, lumA, 0)
            GLES30.glUniform4fv(prog.loc("uSecCorr[0]"), 2, corr, 0)

            if (key.secMask) {
                val cfgs = FloatArray(8)
                secs.forEachIndexed { i, s ->
                    val tex = s.maskId?.let { maskProvider?.maskTexture(clipId, it, timeMs) }
                    if (tex != null) {
                        GLES30.glActiveTexture(GLES30.GL_TEXTURE4 + i)
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
                        GLES30.glUniform1i(prog.loc("uSecMask[$i]"), 4 + i)
                        cfgs[i * 4 + 0] = 1f; cfgs[i * 4 + 1] = if (s.invert) 1f else 0f
                    }
                }
                GLES30.glUniform4fv(prog.loc("uSecMaskCfg[0]"), 2, cfgs, 0)
            }
        }

        // Debug taps (§68) — disabled in production builds
        GLES30.glUniform4f(prog.loc("uDebug"),
            if (cfg.debugStage >= 0) 1f else 0f, cfg.debugStage.toFloat(), 0f, 0f)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun bakeCurveTextures(state: ColorState) {
        val (main, luma) = com.ahstudio.color.curves.CurveBaker.bakeCombined(state.curves)
        curveTex = uploadCurveTex(curveTex, main, 4)
        lumaCurveTex = uploadCurveTex(lumaCurveTex, luma, 1)
        curvesBakedFor = state.curves.hashCode()
    }

    private fun uploadCurveTex(existing: Int, data: FloatArray, comps: Int): Int {
        val tex = if (existing != 0) existing else IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val fmt = if (comps == 4) GLES30.GL_RGBA else GLES30.GL_RED
        val bb = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
        for (v in data) bb.put((v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte())
        bb.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, fmt, com.ahstudio.color.curves.CurveBaker.TEX_SIZE, 1, 0, fmt, GLES30.GL_UNSIGNED_BYTE, bb)
        return tex
    }

    private fun mixerMatrix(state: ColorState): FloatArray {
        val m = state.mixer
        return floatArrayOf(m.rr, m.rg, m.rb, m.gr, m.gg, m.gb, m.br, m.bg, m.bb)
    }

    /** Context loss / project close / Activity recreation (§34, §67). */
    fun releaseGpuResources() {
        if (curveTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(curveTex), 0); curveTex = 0 }
        if (lumaCurveTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(lumaCurveTex), 0); lumaCurveTex = 0 }
        lutTextures.releaseAll()
        shaderManager.releaseAll()
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); vao = 0 }
        if (vbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0); vbo = 0 }
        initialized = false
        curvesBakedFor = Int.MIN_VALUE
    }

    fun getLutCache(): LutCache = lutCache
}
