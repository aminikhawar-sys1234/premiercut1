package com.ute.motion

import com.ute.animation.Easing

enum class MotionUnit { DOCUMENT, PARAGRAPH, LINE, WORD, CHARACTER, GLYPH }

enum class MotionPreset {
    NONE, TYPEWRITER, POP, BOUNCE, SLIDE_IN, SLIDE_OUT, ZOOM_IN, ZOOM_OUT,
    ROTATE_IN, ELASTIC, WAVE, STAGGER_FADE, CASCADE_UP, CASCADE_DOWN,
    RANDOM_REVEAL, BLUR_REVEAL, TRACKING_REVEAL, MASK_REVEAL, DIRECTIONAL_REVEAL,
    KINETIC_SCALE_PULSE, GLITCH_IN,
}

enum class RevealDirection { LTR, RTL, TTB, BTT, CENTER_OUT }

data class MotionSpec(
    val preset: MotionPreset = MotionPreset.NONE,
    val unit: MotionUnit = MotionUnit.CHARACTER,
    val durationSec: Double = 0.6,          // per-unit duration
    val staggerSec: Double = 0.05,          // per-unit offset
    val easing: Easing = Easing.EaseOut,
    val direction: RevealDirection = RevealDirection.LTR,
    val distancePx: Float = 40f,            // slide distance
    val seed: Int = 1,                      // deterministic randomness (random reveal, glitch)
    val loop: Boolean = false,
)

/** Per-glyph animated state computed for one frame. */
data class GlyphMotion(
    val dx: Float = 0f, val dy: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val blurPx: Float = 0f,
    val extraTrackingPx: Float = 0f,
    val visible: Boolean = true,
)
