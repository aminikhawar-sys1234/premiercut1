package com.ahstudio.color.space

import kotlin.math.*

/**
 * Transfer functions. Decode: encoded -> linear-light. Encode: linear -> encoded.
 * Encoding and decoding are ALWAYS separate — never applied twice (§6).
 */
sealed class TransferFunction(val id: Int, val name: String) {
    abstract fun decode(x: Float): Float
    abstract fun encode(x: Float): Float

    object Linear : TransferFunction(0, "linear") {
        override fun decode(x: Float) = x
        override fun encode(x: Float) = x
    }

    object SRGB : TransferFunction(1, "srgb") {
        override fun decode(x: Float) = if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
        override fun encode(x: Float) = if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - 0.055f
    }

    /** Rec.709 OETF (scene -> display code) used as camera encode; inverse for decode. */
    object REC709 : TransferFunction(2, "rec709") {
        override fun decode(x: Float) = if (x < 0.081f) x / 4.5f else ((x + 0.099f) / 1.099f).pow(1f / 0.45f)
        override fun encode(x: Float) = if (x <= 0.01805f) 4.5f * x else 1.099f * x.pow(0.45f) - 0.099f
    }

    class Gamma(id: Int, private val g: Float) : TransferFunction(id, "gamma$g") {
        override fun decode(x: Float) = x.coerceAtLeast(0f).pow(g)
        override fun encode(x: Float) = x.coerceAtLeast(0f).pow(1f / g)
    }

    /** SMPTE ST 2084 (PQ). Normalized 0..1 == 0..10000 nits. */
    object PQ : TransferFunction(3, "pq") {
        private const val M1 = 0.1593017578125f
        private const val M2 = 78.84375f
        private const val C1 = 0.8359375f
        private const val C2 = 18.8515625f
        private const val C3 = 18.6875f
        override fun decode(x: Float): Float {
            val xp = x.coerceAtLeast(0f).pow(1f / M2)
            val num = max(xp - C1, 0f)
            return (num / (C2 - C3 * xp)).coerceAtLeast(0f).pow(1f / M1)
        }
        override fun encode(y: Float): Float {
            val ym = y.coerceIn(0f, 1f).pow(M1)
            return ((C1 + C2 * ym) / (1f + C3 * ym)).pow(M2)
        }
    }

    /** HLG inverse-OETF (signal -> scene linear 0..1). Display OOTF applied by HdrProcessor if needed. */
    object HLG : TransferFunction(4, "hlg") {
        private const val A = 0.17883277f
        private const val B = 0.28466892f
        private const val C = 0.55991073f
        override fun decode(e: Float) =
            if (e <= 0.5f) e * e / 3f else ((exp((e - C) / A) + B) / 12f)
        override fun encode(t: Float) =
            if (t <= 1f / 12f) sqrt(3f * t) else A * ln(12f * t - B) + C
    }
}

object TransferFunctions {
    val Linear = TransferFunction.Linear
    val SRGB = TransferFunction.SRGB
    val REC709 = TransferFunction.REC709
    val PQ = TransferFunction.PQ
    val HLG = TransferFunction.HLG
    val GAMMA22 = TransferFunction.Gamma(10, 2.2f)
    val GAMMA24 = TransferFunction.Gamma(11, 2.4f)
    fun byId(id: Int): TransferFunction = when (id) {
        0 -> Linear; 1 -> SRGB; 2 -> REC709; 3 -> PQ; 4 -> HLG; 10 -> GAMMA22; 11 -> GAMMA24
        else -> SRGB
    }
}
