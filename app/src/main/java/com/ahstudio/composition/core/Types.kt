package com.ahstudio.composition.core

import com.ahstudio.composition.graph.CompositionError
import com.ahstudio.composition.graph.LayerId

data class CompositionResolution(val width: Int, val height: Int) {
    init { require(width > 0 && height > 0) }
    val aspect: Float get() = width.toFloat() / height
    fun scaled(factor: Float) = CompositionResolution(
        (width * factor).toInt().coerceAtLeast(16), (height * factor).toInt().coerceAtLeast(16))
    companion object {
        val P1080 = CompositionResolution(1920, 1080)
        val P2160 = CompositionResolution(3840, 2160)
    }
}

data class RenderConfig(
    val comp: CompositionResolution,
    val output: CompositionResolution,
    val clearColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f),
    val enableFrameCache: Boolean = true,
    val maxFrameCacheBytes: Long = 64L * 1024 * 1024,
    val maxGpuPoolBytes: Long = 96L * 1024 * 1024,
) {
    override fun equals(other: Any?) = other is RenderConfig && other.hashCode() == hashCode()
    override fun hashCode() = (comp.width * 31 + comp.height) * 31 + (output.width * 31 + output.height)
}

class GpuAllocationException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)

data class SkippedLayer(val layerId: LayerId, val reason: String)

data class RenderStats(
    val renderMs: Double, val gpuPasses: Int, val cacheHit: Boolean,
    val liveTextures: Int, val gpuBytesEstimate: Long,
)

sealed class CompositionResult {
    data class Ok(val timeUs: Long, val cached: Boolean, val stats: RenderStats) : CompositionResult()
    data class Partial(val timeUs: Long, val skipped: List<SkippedLayer>, val stats: RenderStats) : CompositionResult()
    data class Failed(val timeUs: Long, val errors: List<CompositionError>) : CompositionResult()
}

/** Development-only diagnostics (Section 42). Never surfaced in production UI. */
class CompositionProfiler {
    val lastRenderNs = java.util.concurrent.atomic.AtomicLong(0)
    val gpuPasses = java.util.concurrent.atomic.AtomicLong(0)
    val cacheHits = java.util.concurrent.atomic.AtomicLong(0)
    val cacheMisses = java.util.concurrent.atomic.AtomicLong(0)
    val liveTextures = java.util.concurrent.atomic.AtomicLong(0)
    val gpuBytesEstimate = java.util.concurrent.atomic.AtomicLong(0)
    val layersRendered = java.util.concurrent.atomic.AtomicLong(0)
    val layersSkipped = java.util.concurrent.atomic.AtomicLong(0)
    fun snapshot(comp: CompositionResolution, out: CompositionResolution): RenderStats = RenderStats(
        renderMs = lastRenderNs.get() / 1_000_000.0, gpuPasses = gpuPasses.get().toInt(),
        cacheHit = cacheHits.get() > cacheMisses.get(), liveTextures = liveTextures.get().toInt(),
        gpuBytesEstimate = gpuBytesEstimate.get())
    fun resetForFrame() { gpuPasses.set(0); layersRendered.set(0); layersSkipped.set(0) }
}
