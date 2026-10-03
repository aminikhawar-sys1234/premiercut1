package com.ahstudio.composition.integration

import android.graphics.Bitmap
import com.ahstudio.composition.graph.CompositionLayer

/** TIME: wire to MasterTimelineClock. Engine never owns a clock. */
interface CompositionTimeSource { val currentTimeUs: Long }

/** MEDIA: wire to Media3/MediaCodec pipeline. Engine consumes decoded frames — never decodes. */
interface LayerSourceBridge {
    data class VideoFrame(val oesTextureId: Int, val uvXform: FloatArray, val width: Int, val height: Int, val version: Long)
    data class BitmapFrame(val bitmap: Bitmap, val version: Long)
    /** Engine computes mediaTimeUs = trimIn + (compT - start)*speed. Return null if not ready/missing (graceful). */
    fun acquireVideoFrame(layer: CompositionLayer, mediaTimeUs: Long): VideoFrame?
    fun acquireBitmap(layer: CompositionLayer): BitmapFrame?
    fun releaseVideoFrame(frame: VideoFrame) {}
}

interface ImageSourceBridge { fun acquireBitmap(layer: CompositionLayer): LayerSourceBridge.BitmapFrame? }

interface TextRasterizerBridge {
    /** Rasterized text as RGBA texture (TextEngine rasterizes; engine uploads/caches). */
    fun rasterize(layer: CompositionLayer, timeUs: Long): RasterizedFrame?
    data class RasterizedFrame(val textureId: Int, val uvXform: FloatArray, val width: Int, val height: Int)
}

interface EffectsBridge {
    data class EffectPass(val effectId: Long, val stage: Int, val paramsHash: Long)
    fun resolvePasses(layer: CompositionLayer, timeUs: Long): List<EffectPass>
    fun applyPass(pass: EffectPass, inputTex: Int, w: Int, h: Int, pool: com.ahstudio.composition.gpu.GpuResourcePool): Int
}

/** EDITING: wire to CommandManager for undo/redo (Section 32). */
interface CommandBridge { fun execute(cmd: Any); fun undo(); fun redo() }

/** EXPORT: wire to export engine's input surface / Transformer. SAME evaluation path as preview. */
interface ExportSurfaceBridge { fun encoderInputSurface(width: Int, height: Int): android.view.Surface }

/** No-op bridges for JVM tests / missing features (graceful degradation). */
object NoopBridges {
    val time = object : CompositionTimeSource { override val currentTimeUs get() = 0L }
    val media = object : LayerSourceBridge {
        override fun acquireVideoFrame(layer: CompositionLayer, mediaTimeUs: Long): LayerSourceBridge.VideoFrame? = null
        override fun acquireBitmap(layer: CompositionLayer): LayerSourceBridge.BitmapFrame? = null
    }
    val text = object : TextRasterizerBridge { override fun rasterize(layer: CompositionLayer, timeUs: Long): TextRasterizerBridge.RasterizedFrame? = null }
    val image = object : ImageSourceBridge { override fun acquireBitmap(layer: CompositionLayer): LayerSourceBridge.BitmapFrame? = null }
    val effects = object : EffectsBridge {
        override fun resolvePasses(layer: CompositionLayer, timeUs: Long) = emptyList<EffectsBridge.EffectPass>()
        override fun applyPass(pass: EffectsBridge.EffectPass, inputTex: Int, w: Int, h: Int, pool: com.ahstudio.composition.gpu.GpuResourcePool) = inputTex
    }
}
