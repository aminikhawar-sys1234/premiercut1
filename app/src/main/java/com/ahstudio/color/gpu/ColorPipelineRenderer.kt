package com.ahstudio.color.gpu

import com.ahstudio.color.core.ColorConfig
import com.ahstudio.color.core.ColorFrame
import com.ahstudio.color.core.ColorState
import com.ahstudio.color.lut.Lut3D

/**
 * §47 PREVIEW/EXPORT PARITY: this exact class draws the grade for both paths.
 * Preview: target = preview surface FBO.  Export: target = MediaCodec encoder-input FBO.
 * Same state, same shader, same LUT textures (per GL context), identical evaluation.
 *
 * // [AH-STUDIO-INTEGRATION]
 *  - Preview: call from your GpuCompositionRenderer after compositing a layer
 *    (or after the adjustment-layer composite) — pass the composed texture.
 *  - Export: call from your export EGL surface loop before eglSwapBuffers on the encoder surface.
 */
class ColorPipelineRenderer(private val processor: ColorGpuProcessor) {

    fun configure(state: ColorState, cfg: ColorConfig) {
        this.state = state
        this.config = cfg
    }

    fun compile() { processor.ensureInit() }

    fun render(
        frame: ColorFrame,
        outFbo: Int,
        flipY: Float = 1f,
        lut3d: Lut3D? = null,
        clipId: String = "",
        timeMs: Long = 0L
    ) {
        val s = state ?: return
        val c = config ?: return
        processor.render(s, c, frame.textureId, frame.textureTarget, outFbo, frame.width, frame.height, flipY, lut3d, clipId, timeMs)
    }

    /** GL context destroyed (Activity rotation, export teardown). */
    fun onContextDestroyed() = processor.releaseGpuResources()

    private var state: ColorState? = null
    private var config: ColorConfig? = null
}
