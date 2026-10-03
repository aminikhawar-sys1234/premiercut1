package com.ahstudio.audio.master.dsp.dynamics

import com.ahstudio.audio.master.core.linToDb
import kotlin.math.abs
import kotlin.math.exp

class EnvelopeFollower {
    private var coefA = 0f; private var coefR = 0f; private var env = 0f
    fun prepare(sampleRate: Int, attackMs: Float, releaseMs: Float) {
        coefA = timeCoef(attackMs, sampleRate); coefR = timeCoef(releaseMs, sampleRate)
    }
    private fun timeCoef(ms: Float, sr: Int): Float =
        (1.0 - exp(-1000.0 / (ms.coerceAtLeast(0.01f) * sr))).toFloat()
    fun process(x: Float): Float {
        val a = abs(x)
        env += (if (a > env) coefA else coefR) * (a - env)
        return env
    }
    fun envDb(): Float = linToDb(env)
    fun envLin(): Float = env
    fun reset() { env = 0f }
}
