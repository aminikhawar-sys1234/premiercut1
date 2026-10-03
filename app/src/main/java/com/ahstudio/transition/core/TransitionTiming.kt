package com.ahstudio.transition.core

/**
 * Deterministic timing. Progress is a pure function of (timelineTime, start, end, easing).
 * No frame counting. No internal clock. Transition window is half-open [startMs, endMs):
 * at endMs the host renders Clip B through its normal path, and progress→1 as t→end⁻
 * guarantees visual continuity.
 */
data class TransitionTiming private constructor(
    val startMs: Long,
    val endMs: Long,
    val easing: Easing,
) {
    val durationMs: Long get() = endMs - startMs

    fun rawProgressAt(timelineTimeMs: Long): Float =
        ((timelineTimeMs - startMs).toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)

    fun easedProgressAt(timelineTimeMs: Long): Float = easing.evaluate(rawProgressAt(timelineTimeMs))

    fun isActiveAt(timelineTimeMs: Long): Boolean =
        timelineTimeMs >= startMs && timelineTimeMs < endMs

    companion object {
        /** Validation-first factory; invalid ranges return Err instead of throwing. */
        fun create(startMs: Long, endMs: Long, easing: Easing = Easing.linear()):
                TransitionResult<TransitionTiming> = when {
            endMs <= startMs -> TransitionResult.Err(
                TransitionError.Validation("Transition duration must be > 0 (start=$startMs, end=$endMs)"))
            endMs - startMs > TransitionEngineMetadata.MAX_DURATION_MS -> TransitionResult.Err(
                TransitionError.Validation("Duration ${endMs - startMs}ms exceeds cap ${TransitionEngineMetadata.MAX_DURATION_MS}"))
            else -> TransitionResult.Ok(TransitionTiming(startMs, endMs, easing))
        }
    }
}
