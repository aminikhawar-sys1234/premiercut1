package com.ahstudio.transition.render

import com.ahstudio.transition.core.TransitionDefinition
import com.ahstudio.transition.core.TransitionError
import com.ahstudio.transition.core.TransitionInstance
import com.ahstudio.transition.core.TransitionRenderSnapshot
import com.ahstudio.transition.diag.TransitionDiagnostics
import com.ahstudio.transition.gl.FramebufferPool
import com.ahstudio.transition.gl.TransitionShaderCache
import com.ahstudio.transition.integration.TransitionFrames
import com.ahstudio.transition.provider.TransitionRegistry
import com.ahstudio.transition.validation.ShaderValidator
import com.ahstudio.transition.validation.TransitionValidationResult

/**
 * THE single authoritative transition runtime (§1, §40). One instance per GL context:
 * preview and export each own an engine, sharing one immutable TransitionRegistry —
 * which is exactly how preview/export parity is guaranteed (§19): same definitions,
 * same snapshot factory, same renderer, same shaders. Only the per-context program
 * cache and FBO pool differ, and both rebuild lazily and deterministically.
 */
class TransitionEngine(
    val registry: TransitionRegistry,
    private val diagnostics: TransitionDiagnostics = TransitionDiagnostics(),
) {
    private val shaderCache = TransitionShaderCache(diagnostics)
    private val fboPool = FramebufferPool(diagnostics)
    private val renderer = TransitionRenderer(shaderCache, fboPool, diagnostics)

    fun validateDefinition(definition: TransitionDefinition): TransitionValidationResult =
        ShaderValidator.validateDefinition(definition)

    fun definitions(): List<TransitionDefinition> = registry.all()

    fun snapshot(definitionId: String, instance: TransitionInstance, timelineTimeMs: Long,
                 outputWidth: Int, outputHeight: Int, playbackDirection: Int): SnapshotResult {
        val definition = registry.definition(definitionId)
            ?: return SnapshotResult.Failed(
                TransitionError.Validation("Unknown transition definition '$definitionId'"))
        return TransitionSnapshotFactory.create(
            definition, instance, timelineTimeMs, outputWidth, outputHeight, playbackDirection)
    }

    /** Must be called on the GL thread of the owning context. */
    fun render(snapshot: TransitionRenderSnapshot, frames: TransitionFrames, outputFbo: Int) =
        renderer.render(snapshot, frames, outputFbo)

    fun onContextLost() {
        shaderCache.onContextLost()
        fboPool.destroyAll()
        renderer.releaseGpuResources()
    }

    fun diagnostics(): TransitionDiagnostics = diagnostics

    fun diagnosticsSnapshot(): String =
        diagnostics.dump(shaderCache.hits, shaderCache.misses, fboPool.idleCount, fboPool.liveCount)
}
