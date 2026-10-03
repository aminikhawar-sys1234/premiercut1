package com.vfx.engine.gpu

import com.vfx.engine.core.effect.EffectRuntime
import com.vfx.engine.core.effect.EffectSnapshot
import com.vfx.engine.graph.GraphBuilder

/**
 * GPU rendering contract, extends the core lifecycle contract.
 * Runtimes append passes into the frame's [GraphBuilder] and return their output texture.
 * Must be deterministic for identical (input, snapshot). Owned by the GL thread.
 */
interface GpuEffectRuntime : EffectRuntime {
    fun buildPasses(builder: GraphBuilder, input: GpuTexture, snapshot: EffectSnapshot): GpuTexture
}
