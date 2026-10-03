package com.ahstudio.transition.integration

import com.ahstudio.transition.core.TransitionInstance
import com.ahstudio.transition.core.TransitionRenderSnapshot
import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.render.SnapshotResult
import com.ahstudio.transition.render.TransitionEngine

/**
 * One decoded/processed source frame of a clip, ready for the transition pipeline.
 * [transformMatrix] is 4×4 column-major, mapping unit uv → sampling uv, encoding
 * crop/fit/rotation EXACTLY as the host's normal single-clip path displays this clip
 * (preview/export parity requirement). Identity matrix ⇒ pass the texture through.
 */
class AcquiredFrame(
    val textureId: Int,
    val target: Int,                 // GlUtil.TEXTURE_2D or GlUtil.TEXTURE_EXTERNAL_OES
    val width: Int,
    val height: Int,
    val transformMatrix: FloatArray, // length 16
    val sourceTimestampUs: Long,
)

interface TransitionFrameProvider {
    /** Contract: valid until releaseFrame; same GL thread as engine.render; must reflect
     *  the host's existing trim/speed/reverse/remap mapping for [timelineTimeMs]. */
    fun acquireFrame(clipId: String, timelineTimeMs: Long): AcquiredFrame
    fun releaseFrame(frame: AcquiredFrame)
}

class TransitionFrames internal constructor(
    val frameA: AcquiredFrame,
    val frameB: AcquiredFrame,
    private val provider: TransitionFrameProvider,
) : AutoCloseable {
    override fun close() { provider.releaseFrame(frameA); provider.releaseFrame(frameB) }

    companion object {
        fun acquire(provider: TransitionFrameProvider, clipAId: String, clipBId: String,
                    timelineTimeMs: Long): TransitionFrames =
            TransitionFrames(provider.acquireFrame(clipAId, timelineTimeMs),
                provider.acquireFrame(clipBId, timelineTimeMs), provider)
    }
}

/** Optional read-only view of host timeline state (validation, adapters). Not a clock. */
interface TransitionTimelineAdapter {
    fun clipTimelineRange(clipId: String): ClosedRange<Long>?
    fun currentTimelineTimeMs(): Long
    fun playbackDirection(): Int
}

/**
 * THE one render path for preview AND export (§17–§19). The host calls this from its
 * existing GL frame callback (preview) and its existing encoder-input-surface callback
 * (export). Returns null when the time is outside every transition window — host then
 * renders its normal single-clip path.
 */
class TransitionRenderGateway(
    private val engine: TransitionEngine,
    private val frameProvider: TransitionFrameProvider,
    private val timeline: TransitionTimelineAdapter,
) {
    fun renderActiveTransition(instances: List<TransitionInstance>, timelineTimeMs: Long,
                               outputFbo: Int, outputWidth: Int, outputHeight: Int):
            TransitionRenderSnapshot? {
        val active = instances.firstOrNull {
            it.enabled && timelineTimeMs >= it.startMs && timelineTimeMs < it.endMs
        } ?: return null
        return renderIfInTransition(active, timelineTimeMs, outputFbo, outputWidth, outputHeight)
    }

    fun renderIfInTransition(instance: TransitionInstance, timelineTimeMs: Long,
                             outputFbo: Int, outputWidth: Int, outputHeight: Int):
            TransitionRenderSnapshot? {
        val snapshotResult = engine.snapshot(
            instance.definitionId, instance, timelineTimeMs, outputWidth, outputHeight,
            timeline.playbackDirection())
        val snapshot = when (snapshotResult) {
            is SnapshotResult.Ready -> snapshotResult.snapshot
            else -> return null   // Inactive or Failed — host renders its normal path (fail-safe)
        }
        TransitionFrames.acquire(frameProvider, instance.outgoingClipId, instance.incomingClipId,
            timelineTimeMs).use { frames ->
            when (val renderResult = engine.render(snapshot, frames, outputFbo)) {
                is TransitionResult.Err -> engine.diagnostics().onError(renderResult.error)
                is TransitionResult.Ok -> Unit
            }
        }
        return snapshot
    }
}
