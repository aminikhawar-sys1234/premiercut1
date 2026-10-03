package com.ahstudio.transition.core

import com.ahstudio.transition.validation.TransitionValidationResult
import kotlin.math.roundToLong

enum class TransitionAlignment { CENTERED, A_BIASED, B_BIASED, CUSTOM }

/**
 * Places a transition of [durationMs] around the cut point (= Clip B's timeline start
 * for abutting clips). CENTERED → cut is midpoint; A_BIASED → ends at cut;
 * B_BIASED → starts at cut; CUSTOM → start = cut − anchor·duration.
 */
object TransitionPlacement {

    fun resolve(alignment: TransitionAlignment, durationMs: Long, cutPointMs: Long,
                customAnchor: Float = 0.5f): TransitionResult<Pair<Long, Long>> {
        if (durationMs <= 0) return TransitionResult.Err(
            TransitionError.Validation("durationMs must be > 0"))
        val start = when (alignment) {
            TransitionAlignment.CENTERED -> cutPointMs - durationMs / 2
            TransitionAlignment.A_BIASED -> cutPointMs - durationMs
            TransitionAlignment.B_BIASED -> cutPointMs
            TransitionAlignment.CUSTOM -> {
                if (customAnchor !in 0f..1f) return TransitionResult.Err(
                    TransitionError.Validation("customAnchor must be in [0,1], got $customAnchor"))
                cutPointMs - (customAnchor.toDouble() * durationMs).roundToLong()
            }
        }
        return TransitionResult.Ok(start to start + durationMs)
    }

    /**
     * Geometric validity against both clips' timeline spans. For abutting clips the
     * transition correctly straddles the cut (head in A, tail in B) — that is valid.
     * Constraints: starts within A, ends within B, duration fits both clip lengths.
     */
    fun validateAgainstClips(startMs: Long, endMs: Long,
                             clipA: ClosedRange<Long>, clipB: ClosedRange<Long>
    ): TransitionValidationResult {
        val errors = mutableListOf<String>()
        val dur = endMs - startMs
        if (dur <= 0) errors += "Non-positive duration ($dur ms)."
        if (startMs < clipA.start) errors += "Transition starts before Clip A begins ($startMs < ${clipA.start})."
        if (endMs > clipB.endInclusive) errors += "Transition ends after Clip B ends ($endMs > ${clipB.endInclusive})."
        val aLen = clipA.endInclusive - clipA.start
        val bLen = clipB.endInclusive - clipB.start
        if (dur > aLen) errors += "Duration ${dur}ms exceeds Clip A length ($aLen ms)."
        if (dur > bLen) errors += "Duration ${dur}ms exceeds Clip B length ($bLen ms)."
        return TransitionValidationResult(errors)
    }
}
