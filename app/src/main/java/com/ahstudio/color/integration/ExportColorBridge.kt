package com.ahstudio.color.integration

import com.ahstudio.color.core.ColorConfig
import com.ahstudio.color.core.ColorFrame

/**
 * §47 PARITY GUARANTEE: export uses the SAME ColorPipelineRenderer/ColorGpuProcessor
 * instance as preview. Only the target FBO differs (MediaCodec encoder-input surface).
 *
 * §48 flow: Timeline -> CompositionEngine -> [THIS: ColorEngine grade] -> EncoderSurface
 *           -> Muxer -> MP4. No CPU bitmaps, no glReadPixels in the export path.
 */
class ExportColorBridge(private val adapter: CompositionColorAdapter) {

    fun beginExport() { /* optional: raise cache budgets, pre-upload LUT textures */ }

    fun gradeFrameForExport(
        clipId: String, timeMs: Long, frame: ColorFrame,
        encoderFbo: Int, cfg: ColorConfig
    ) {
        adapter.gradeClip(clipId, timeMs, frame, encoderFbo, cfg, flipY = 0f)
    }

    fun gradeAdjustmentForExport(
        layerId: String, timeMs: Long, composite: ColorFrame, encoderFbo: Int, cfg: ColorConfig
    ) = adapter.gradeComposite(layerId, timeMs, composite, encoderFbo, cfg, flipY = 0f)

    fun endExport() { /* flush CoalescingSink sessions, release transient caches if needed */ }
}
