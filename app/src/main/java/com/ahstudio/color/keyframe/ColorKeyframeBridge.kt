package com.ahstudio.color.keyframe

import com.ahstudio.color.core.ColorState

/**
 * §30: uses the EXISTING KeyframeAnimationEngine. No new keyframe system.
 * The host registers each animatable path as a track; this bridge reads values at time.
 */
interface KeyframeTrackReader {
    /** Returns animated value at time, or null if no keyframes exist on this track. */
    fun floatValue(trackId: String, timeMs: Long): Float?
}

class ColorKeyframeBridge(private val reader: KeyframeTrackReader) {

    fun trackId(clipId: String, path: String) = "color/$clipId/$path"

    /** Produce the effective state for a frame: base state overridden by animated values. */
    fun evaluate(base: ColorState, clipId: String, timeMs: Long): ColorState {
        var s = base
        fun f(path: String, current: Float): Float =
            reader.floatValue(trackId(clipId, path), timeMs) ?: current

        s = s.copy(
            exposure = f("exposure", s.exposure),
            contrast = f("contrast", s.contrast),
            contrastPivot = f("contrastPivot", s.contrastPivot),
            highlights = f("highlights", s.highlights),
            shadows = f("shadows", s.shadows),
            whites = f("whites", s.whites),
            blacks = f("blacks", s.blacks),
            temperature = f("temperature", s.temperature),
            tint = f("tint", s.tint),
            saturation = f("saturation", s.saturation),
            vibrance = f("vibrance", s.vibrance),
            skinProtect = f("skinProtect", s.skinProtect),
            splitTone = s.splitTone.copy(
                shadowHue = f("splitTone.shadowHue", s.splitTone.shadowHue),
                shadowSat = f("splitTone.shadowSat", s.splitTone.shadowSat),
                highlightHue = f("splitTone.highlightHue", s.splitTone.highlightHue),
                highlightSat = f("splitTone.highlightSat", s.splitTone.highlightSat),
                balance = f("splitTone.balance", s.splitTone.balance),
                strength = f("splitTone.strength", s.splitTone.strength)
            )
        )
        return s
    }
}
