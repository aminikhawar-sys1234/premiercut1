package com.vfx.engine.graph

import com.vfx.engine.core.device.DeviceCapabilities
import com.vfx.engine.gpu.GpuContext
import com.vfx.engine.gpu.FramebufferPool
import com.vfx.engine.gpu.QuadRenderer
import com.vfx.engine.gpu.ShaderCache
import com.vfx.engine.gpu.lut.LutEngine
import com.vfx.engine.gpu.temporal.TemporalEngine

/** Per-frame context handed to effect runtimes. Long-lived GPU services live on GpuContext. */
class RenderContext(
    val gpu: GpuContext,
    val frameWidth: Int,
    val frameHeight: Int,
    val timeUs: Long,
    val quality: Quality
) {
    enum class Quality(val blurScale: Float) { FAST(0.5f), BALANCED(1f), HIGH(1f) }

    val pool: FramebufferPool get() = gpu.pool
    val programs: ShaderCache get() = gpu.shaderCache
    val quad: QuadRenderer get() = gpu.quad
    val caps: DeviceCapabilities get() = gpu.capabilities
    val temporal: TemporalEngine get() = gpu.temporal
    val lutEngine: LutEngine get() = gpu.lutEngine
}
