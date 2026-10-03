package com.ahstudio.animation.keyframes

import com.ahstudio.animation.curves.CubicBezierTiming
import com.ahstudio.animation.easing.EasingType
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.math.isFinite

enum class InterpolationType { LINEAR, HOLD, EASE_IN, EASE_OUT, EASE_IN_OUT, BEZIER, CUSTOM_CURVE }
enum class TangentMode { FREE, ALIGNED, AUTO, FLAT, LINEAR, BROKEN }

@JvmInline value class KeyframeId(val raw: Long)

/**
 * Immutable keyframe. For scalar tracks use [value]; for VEC2 tracks use [vecValue].
 * Tangent slopes are value-per-second. Spatial handles are offsets relative to the
 * keyframe's own position (outHandle points forward, inHandle usually backward).
 */
data class Keyframe(
    val id: KeyframeId,
    val timeMs: Long,
    val value: Double = 0.0,
    val vecValue: Vec2? = null,
    val interpolation: InterpolationType = InterpolationType.LINEAR,
    val easing: EasingType = EasingType.LINEAR,          // !=LINEAR -> overrides default shape
    val bezier: CubicBezierTiming? = null,               // for CUSTOM_CURVE
    val inTangent: Double = 0.0,
    val outTangent: Double = 0.0,
    val tangentMode: TangentMode = TangentMode.AUTO,
    val spatialInHandle: Vec2? = null,
    val spatialOutHandle: Vec2? = null,
    val metadata: Map<String, String> = emptyMap()
)

object KeyframeFactory {
    /** Validates: rejects NaN/Inf instead of silently corrupting data. */
    fun create(
        id: KeyframeId, timeMs: Long, value: Double,
        interpolation: InterpolationType = InterpolationType.LINEAR
    ): Keyframe {
        require(timeMs != Long.MIN_VALUE) { "Invalid keyframe time" }
        require(isFinite(value)) { "Keyframe value must be finite (got $value)" }
        return Keyframe(id = id, timeMs = timeMs, value = value, interpolation = interpolation)
    }
    fun createVec2(
        id: KeyframeId, timeMs: Long, v: Vec2,
        interpolation: InterpolationType = InterpolationType.LINEAR,
        outHandle: Vec2? = null, inHandle: Vec2? = null
    ): Keyframe {
        require(isFinite(v)) { "Keyframe Vec2 value must be finite" }
        if (outHandle != null) require(isFinite(outHandle)) { "Non-finite out handle" }
        if (inHandle != null) require(isFinite(inHandle)) { "Non-finite in handle" }
        return Keyframe(
            id = id, timeMs = timeMs, vecValue = v, interpolation = interpolation,
            spatialOutHandle = outHandle, spatialInHandle = inHandle
        )
    }
}

sealed class ValidationIssue {
    data class NonFiniteValue(val detail: String) : ValidationIssue()
    data class InvalidHandle(val detail: String) : ValidationIssue()
    data class InvalidInterpolation(val detail: String) : ValidationIssue()
    data class DuplicateTimeRepaired(val detail: String) : ValidationIssue()
    data class UnsupportedVersion(val found: Int) : ValidationIssue()
    data class RepairedKeyframe(val detail: String) : ValidationIssue()
    data class RepairedTrack(val detail: String) : ValidationIssue()
    data class SkippedEntry(val detail: String) : ValidationIssue()
}

object KeyframeSanitizer {
    /** Returns repaired keyframe or null (dropped) -- never throws, never corrupts. */
    fun sanitize(kf: Keyframe): Pair<Keyframe?, ValidationIssue?> {
        if (!isFinite(kf.value)) return null to ValidationIssue.NonFiniteValue("kf ${kf.id.raw} value=NaN/Inf")
        kf.vecValue?.let { if (!isFinite(it)) return null to ValidationIssue.NonFiniteValue("kf ${kf.id.raw} vec=NaN/Inf") }
        var out = kf
        if (kf.bezier != null) {
            val b = kf.bezier
            val ok = b != null && isFinite(b.progress(0.5)) && isFinite(b.slope(0.5))
            if (!ok) out = out.copy(bezier = null, interpolation = InterpolationType.LINEAR)
        }
        if (!isFinite(out.inTangent)) out = out.copy(inTangent = 0.0)
        if (!isFinite(out.outTangent)) out = out.copy(outTangent = 0.0)
        if (out.spatialInHandle != null && !isFinite(out.spatialInHandle!!)) out = out.copy(spatialInHandle = null)
        if (out.spatialOutHandle != null && !isFinite(out.spatialOutHandle!!)) out = out.copy(spatialOutHandle = null)
        return out to null
    }
}
