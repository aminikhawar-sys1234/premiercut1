package com.ahstudio.color.hdr

import com.ahstudio.color.core.ToneMapMode
import kotlin.math.max

/** CPU tone mappers — identical math to the GLSL versions (§40). */
object ToneMappers {
    fun apply(mode: ToneMapMode, c: FloatArray): FloatArray = when (mode) {
        ToneMapMode.NONE -> c
        ToneMapMode.ACES -> FloatArray(3) {
            val x = max(c[it], 0f)
            ((x * (2.51f * x + 0.03f)) / (x * (2.43f * x + 0.59f) + 0.14f)).coerceIn(0f, 1f)
        }
        ToneMapMode.REINHARD -> FloatArray(3) {
            val x = max(c[it], 0f)
            x * (1f + x / 16f) / (1f + x)
        }
        ToneMapMode.HABLE -> {
            fun f(x: Float): Float {
                val A = 0.15f; val B = 0.50f; val C = 0.10f; val D = 0.20f; val E = 0.02f; val F = 0.30f
                return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F
            }
            val w = f(11.2f)
            FloatArray(3) { f(max(c[it], 0f) * 2f) / w }
        }
    }
}
