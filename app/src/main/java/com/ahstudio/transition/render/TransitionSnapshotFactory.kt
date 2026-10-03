package com.ahstudio.transition.render

import com.ahstudio.transition.core.AlphaMode
import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.ResolvedParameter
import com.ahstudio.transition.core.TransitionDefinition
import com.ahstudio.transition.core.TransitionError
import com.ahstudio.transition.core.TransitionInstance
import com.ahstudio.transition.core.TransitionRenderSnapshot
import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.core.TransitionTiming

sealed class SnapshotResult {
    object Inactive : SnapshotResult()
    data class Ready(val snapshot: TransitionRenderSnapshot) : SnapshotResult()
    data class Failed(val error: TransitionError) : SnapshotResult()
}

object TransitionSnapshotFactory {

    // Single-entry memo: parameter resolution is immutable per (definition, overrides);
    // avoids rebuilding maps every frame (§31). Same definition+overrides ⇒ same result.
    private val memoLock = Any()
    private var memoDefId: String? = null
    private var memoOverrides: Map<String, ParamValue>? = null
    private var memoResult: Pair<Map<String, ResolvedParameter>, List<String>>? = null

    private fun resolveCached(definition: TransitionDefinition,
                              overrides: Map<String, ParamValue>):
            Pair<Map<String, ResolvedParameter>, List<String>> =
        synchronized(memoLock) {
            val existing = memoResult
            if (definition.id == memoDefId && overrides == memoOverrides && existing != null) existing
            else {
                val r = definition.resolveParameters(overrides)
                memoDefId = definition.id
                memoOverrides = overrides
                memoResult = r
                r
            }
        }

    fun create(definition: TransitionDefinition, instance: TransitionInstance,
               timelineTimeMs: Long, outputWidth: Int, outputHeight: Int,
               playbackDirection: Int): SnapshotResult {
        if (outputWidth <= 0 || outputHeight <= 0)
            return SnapshotResult.Failed(
                TransitionError.Validation("Invalid output size ${outputWidth}x${outputHeight}"))
        val timing = when (val r = TransitionTiming.create(instance.startMs, instance.endMs, instance.easing)) {
            is TransitionResult.Ok -> r.value
            is TransitionResult.Err -> return SnapshotResult.Failed(r.error)
        }
        if (!instance.enabled) return SnapshotResult.Inactive
        if (timelineTimeMs < instance.startMs || timelineTimeMs >= instance.endMs)
            return SnapshotResult.Inactive

        val (params, warnings) = resolveCached(definition, instance.parameters)

        return SnapshotResult.Ready(TransitionRenderSnapshot(
            instanceId = instance.instanceId,
            definitionId = definition.id,
            transitionName = definition.name,
            startMs = instance.startMs,
            endMs = instance.endMs,
            timelineTimeMs = timelineTimeMs,
            rawProgress = timing.rawProgressAt(timelineTimeMs),
            progress = timing.easedProgressAt(timelineTimeMs),
            parameters = params,
            shaders = definition.shaders,
            graph = definition.graph,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            outputPremultiplied = definition.alphaMode == AlphaMode.PREMULTIPLIED,
            playbackDirection = playbackDirection,
            easing = instance.easing,
            warnings = warnings,
        ))
    }
}
