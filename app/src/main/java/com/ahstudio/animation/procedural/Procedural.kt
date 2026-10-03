package com.ahstudio.animation.procedural

import com.ahstudio.animation.keyframes.EvaluatedValue
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.math.lerp
import com.ahstudio.animation.math.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object Procedural {
    // splitmix64 finalizer -- deterministic, seedable
    fun hash64(x: Long): Long {
        var z = x + 0x9e3779b97f4a7c15UL.toLong()
        z = (z xor (z ushr 30)) * 0xbf58476d1ce4e5b9UL.toLong()
        z = (z xor (z ushr 27)) * 0x94d049bb133111ebUL.toLong()
        return z xor (z ushr 31)
    }
    fun rand01(seed: Long, index: Long): Double =
        ((hash64(seed xor (index * 0x2545F4914F6CDD1DUL.toLong())) ushr 11) and 0x1FFFFFFFFFFFFFL).toDouble() / 9007199254740992.0

    /** Smooth 1D value-noise in [-1,1] -- a pure function of (seed, x): safe at ANY seek position. */
    fun valueNoise(seed: Long, x: Double): Double {
        val i = floor(x); val f = x - i
        val a = rand01(seed, i.toLong()) * 2.0 - 1.0
        val b = rand01(seed, i.toLong() + 1L) * 2.0 - 1.0
        return lerp(a, b, smoothstep(f))
    }
}

enum class Waveform { SINE, SQUARE, TRIANGLE, SAWTOOTH }

class Oscillator(
    val seed: Long,
    val freqHz: Double,
    val amplitude: Double,
    val waveform: Waveform = Waveform.SINE,
    val phaseDeg: Double = 0.0
) {
    fun value(timeMs: Long): Double {
        val t = timeMs / 1000.0
        val ph = 2.0 * PI * freqHz * t + phaseDeg * PI / 180.0
        val base = when (waveform) {
            Waveform.SINE -> sin(ph)
            Waveform.SQUARE -> if (sin(ph) >= 0) 1.0 else -1.0
            Waveform.TRIANGLE -> 2.0 / PI * (2.0 * ((ph / (2.0 * PI)) % 1.0) - 1.0).let { abs(it) * 2.0 - 1.0 }
            Waveform.SAWTOOTH -> 2.0 * ((ph / (2.0 * PI)) % 1.0) - 1.0
        }
        return amplitude * base + (seed * 0.0)
    }
}

/** AE-style wiggle -- multi-octave deterministic noise. No integration => random-access safe. */
class Wiggle(
    val seed: Long,
    val freqHz: Double,
    val amplitude: Double,
    val octaves: Int = 2
) {
    fun value(timeMs: Long): Double {
        val t = timeMs / 1000.0
        var amp = 1.0; var freq = freqHz.coerceAtLeast(1e-6)
        var sum = 0.0; var norm = 0.0
        for (o in 0 until octaves.coerceIn(1, 6)) {
            sum += amp * Procedural.valueNoise(seed + o * 7919L, t * freq)
            norm += amp
            amp *= 0.5; freq *= 2.0
        }
        return amplitude * (sum / norm)
    }
    /** Independent X/Y channels (different seed offsets). */
    fun vec2(timeMs: Long): Vec2 =
        Vec2(
            Wiggle(seed, freqHz, amplitude, octaves).value(timeMs),
            Wiggle(seed + 104729L, freqHz, amplitude, octaves).value(timeMs)
        )
}

/**
 * Closed-form damped harmonic oscillator -- EXACT value at ANY t without stepping.
 * Seek-safe alternative to iterated physics integration.
 */
class Spring(
    val stiffness: Double,   // k  (>0)
    val damping: Double,     // c  (>=0)
    val mass: Double = 1.0
) {
    fun value(from: Double, to: Double, tSec: Double, initialVelocity: Double = 0.0): Double {
        if (tSec <= 0.0) return from
        val k = if (stiffness.isFinite() && stiffness > 0.0) stiffness else 0.0
        if (k <= 0.0) return lerp(from, to, tSec.coerceAtMost(1.0)) // graceful: linear settle
        val m = if (mass.isFinite() && mass > 0.0) mass else 1.0
        val c = if (damping.isFinite() && damping >= 0.0) damping else 0.0
        val w0 = sqrt(k / m)
        val zeta = c / (2.0 * sqrt(k * m))
        val d = from - to
        return to + when {
            zeta < 1.0 -> {                                       // underdamped
                val wd = w0 * sqrt(1.0 - zeta * zeta)
                val e = exp(-zeta * w0 * tSec)
                e * (d * cos(wd * tSec) + ((initialVelocity + zeta * w0 * d) / wd) * sin(wd * tSec))
            }
            abs(zeta - 1.0) < 1e-9 -> {                           // critically damped
                val e = exp(-w0 * tSec)
                e * (d + (initialVelocity + w0 * d) * tSec)
            }
            else -> {                                             // overdamped
                val r1 = -w0 * (zeta - sqrt(zeta * zeta - 1.0))
                val r2 = -w0 * (zeta + sqrt(zeta * zeta - 1.0))
                val a = (initialVelocity - d * r2) / (r1 - r2)
                val b = d - a
                a * exp(r1 * tSec) + b * exp(r2 * tSec)
            }
        }
    }
    fun velocity(from: Double, to: Double, tMs: Long, v0: Double = 0.0): Double {
        val h = 1.0 // 1ms central difference -- stable
        val a = value(from, to, (tMs - h) / 1000.0, v0)
        val b = value(from, to, (tMs + h) / 1000.0, v0)
        return (b - a) / (2.0 * h / 1000.0)
    }
}
