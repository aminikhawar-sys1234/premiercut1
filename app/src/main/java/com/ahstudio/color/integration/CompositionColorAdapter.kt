package com.ahstudio.color.integration

import com.ahstudio.color.ColorEngine
import com.ahstudio.color.core.ColorConfig
import com.ahstudio.color.core.ColorFrame
import com.ahstudio.color.gpu.ColorPipelineRenderer

/**
 * Bridges the Color Engine into the EXISTING CompositionEngine / GpuCompositionRenderer.
 * It CONSUMES renderer output — it never replaces the compositor.
 *
 * §51 Clip-local:   gradeClip(clipId, ...) after that clip's layer texture is composed.
 * §52 Adjustment:   gradeComposite(adjustmentLayerId, ...) on the composite texture of the
 *                   layers the adjustment layer spans (per existing layer order).
 */
class CompositionColorAdapter(
    private val engine: ColorEngine,
    private val renderer: ColorPipelineRenderer
) {
    fun gradeClip(
        clipId: String, timeMs: Long, frame: ColorFrame,
        outFbo: Int, cfg: ColorConfig, flipY: Float = 1f
    ) {
        val s = engine.evaluate(clipId, timeMs)
        renderer.configure(s, cfg)
        renderer.render(
            frame = frame,
            outFbo = outFbo,
            flipY = flipY,
            lut3d = s.lut?.let { engine.resolveLut3d(it.lutId) },
            clipId = clipId,
            timeMs = timeMs
        )
    }

    /** Adjustment layer grade — state stored under "adj:<layerId>" so clips stay isolated. */
    fun gradeComposite(
        adjustmentLayerId: String, timeMs: Long, composite: ColorFrame,
        outFbo: Int, cfg: ColorConfig, flipY: Float = 1f
    ) {
        val s = engine.evaluate("adj:$adjustmentLayerId", timeMs)
        renderer.configure(s, cfg)
        renderer.render(
            frame = composite,
            outFbo = outFbo,
            flipY = flipY,
            lut3d = s.lut?.let { engine.resolveLut3d(it.lutId) },
            clipId = "adj:$adjustmentLayerId",
            timeMs = timeMs
        )
    }
}
