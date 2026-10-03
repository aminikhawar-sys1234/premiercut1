package com.ute.scene3d

import com.ute.core.MathUtil

sealed class Light {
    abstract val color: Int
    abstract val intensity: Float
    data class Ambient(override val color: Int = 0xFFFFFFFF.toInt(), override val intensity: Float = 0.35f) : Light()
    data class Directional(override val color: Int = 0xFFFFFFFF.toInt(), override val intensity: Float = 1f,
                           val dirX: Float = -0.4f, val dirY: Float = -0.6f, val dirZ: Float = -1f) : Light()
    data class Point(override val color: Int = 0xFFFFFFFF.toInt(), override val intensity: Float = 1f,
                     val x: Float = 100f, val y: Float = 100f, val z: Float = 300f,
                     val attenuation: Float = 0.002f) : Light()
    data class Spot(override val color: Int, override val intensity: Float,
                    val x: Float, val y: Float, val z: Float,
                    val dirX: Float, val dirY: Float, val dirZ: Float,
                    val coneDeg: Float = 30f, val softness: Float = 0.4f) : Light()
    data class Rim(override val color: Int = 0xFFBFE9FF.toInt(), override val intensity: Float = 0.5f) : Light()
}

/** Sanitized light rig — up to 4 lights + ambient are uploaded as uniforms. */
data class LightRig(val lights: List<Light>) {
    init { require(lights.count { it !is Light.Ambient } <= 4) { "max 4 dynamic lights" } }
    fun sanitized(): List<Light> = lights.map { l ->
        when (l) {
            is Light.Directional -> l.copy(intensity = MathUtil.sanitize(l.intensity, 1f).coerceIn(0f, 4f))
            is Light.Point -> l.copy(intensity = MathUtil.sanitize(l.intensity, 1f).coerceIn(0f, 4f))
            else -> l
        }
    }
}
