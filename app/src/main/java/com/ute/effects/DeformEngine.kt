package com.ute.effects

/** GPU vertex-shader warps. All params animate through keyframes. */
data class DeformSpec(
    val type: DeformType = DeformType.NONE,
    val amount: Float = 0.3f,       // normalized strength
    val radiusPx: Float = 300f,     // arc / bulge radius
    val frequency: Float = 2f,      // wave frequency
    val phase: Float = 0f,          // wave phase at t=0
    val wavePxPerSec: Float = 0f,   // deterministic wave animation
)

enum class DeformType { NONE, ARC, WAVE, BULGE, FISHEYE, PERSPECTIVE }
