package com.ute.effects

import com.ute.core.MathUtil

data class EffectSpec(
    val type: EffectType,
    val intensity: Float = 1f,     // effect-specific 0..1+
    val speed: Float = 1f,         // for animated effects, units per second
    val seed: Int = 0,             // deterministic randomness
)

enum class EffectType {
    BLUR, GLOW, NEON, GLITCH, RGB_SPLIT, CHROMATIC_ABERRATION,
    NOISE, GRAIN, WAVE, DISTORTION, PIXELATE, REVEAL,
}

/** An ordered, stackable effect chain with sanitized parameters. */
data class EffectStack(val effects: List<EffectSpec>) {
    fun intensityOf(type: EffectType): Float =
        effects.firstOrNull { it.type == type }?.let { MathUtil.sanitize(it.intensity, 0f).coerceIn(0f, 4f) } ?: 0f
}
