package com.vfx.engine.core.lut

import com.vfx.engine.core.EffectEngineException

/**
 * Parsed LUT data.
 * 3D layout: index = r + g*N + b*N*N  (N = size3d), rgb triplets, fastest->slowest
 *            exactly as the .cube spec stores rows.
 * 1D layout: size1d rgb triplets.
 */
data class CubeLut(
    val size3d: Int?,                 // 3D LUT size (e.g. 33) or null
    val size1d: Int?,                 // 1D LUT size or null
    val data3d: FloatArray?,          // rgb triplets: size3d^3 * 3 floats
    val data1d: FloatArray?,          // rgb triplets: size1d * 3 floats
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
    val title: String = ""
) {
    val is3D: Boolean get() = data3d != null
    val is1D: Boolean get() = data1d != null

    val size: Int get() = size3d ?: size1d ?: 0
    val tableData: FloatArray get() = data3d ?: data1d ?: floatArrayOf()

    fun entry3d(r: Int, g: Int, b: Int, out: FloatArray) {
        val n = size3d ?: throw EffectEngineException.InvalidLut("Not a 3D LUT")
        val i = (r + g * n + b * n * n) * 3
        out[0] = data3d!![i]; out[1] = data3d[i + 1]; out[2] = data3d[i + 2]
    }

    fun entry1d(i: Int, out: FloatArray) {
        val n = size1d ?: throw EffectEngineException.InvalidLut("Not a 1D LUT")
        val idx = i.coerceIn(0, n - 1) * 3
        out[0] = data1d!![idx]; out[1] = data1d[idx + 1]; out[2] = data1d[idx + 2]
    }

    /** CPU trilinear sample — reference for tests and thumbnail preview. */
    fun sample3dTrilinear(r: Float, g: Float, b: Float, out: FloatArray) {
        val n = size3d ?: throw EffectEngineException.InvalidLut("Not a 3D LUT")
        fun norm(v: Float, ch: Int): Float =
            ((v - domainMin[ch]) / (domainMax[ch] - domainMin[ch]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
        val fr = norm(r, 0) * (n - 1); val fg = norm(g, 1) * (n - 1); val fb = norm(b, 2) * (n - 1)
        val r0 = fr.toInt().coerceAtMost(n - 2).coerceAtLeast(0)
        val g0 = fg.toInt().coerceAtMost(n - 2).coerceAtLeast(0)
        val b0 = fb.toInt().coerceAtMost(n - 2).coerceAtLeast(0)
        val tr = fr - r0; val tg = fg - g0; val tb = fb - b0
        val c000 = FloatArray(3); val c100 = FloatArray(3); val c010 = FloatArray(3); val c110 = FloatArray(3)
        val c001 = FloatArray(3); val c101 = FloatArray(3); val c011 = FloatArray(3); val c111 = FloatArray(3)
        entry3d(r0, g0, b0, c000); entry3d(r0 + 1, g0, b0, c100)
        entry3d(r0, g0 + 1, b0, c010); entry3d(r0 + 1, g0 + 1, b0, c110)
        entry3d(r0, g0, b0 + 1, c001); entry3d(r0 + 1, g0, b0 + 1, c101)
        entry3d(r0, g0 + 1, b0 + 1, c011); entry3d(r0 + 1, g0 + 1, b0 + 1, c111)
        for (ch in 0..2) {
            val c00 = c000[ch] + (c100[ch] - c000[ch]) * tr
            val c10 = c010[ch] + (c110[ch] - c010[ch]) * tr
            val c01 = c001[ch] + (c101[ch] - c001[ch]) * tr
            val c11 = c011[ch] + (c111[ch] - c011[ch]) * tr
            val c0 = c00 + (c10 - c00) * tg
            val c1 = c01 + (c11 - c01) * tg
            out[ch] = c0 + (c1 - c0) * tb
        }
    }

    override fun equals(other: Any?): Boolean =
        other is CubeLut && other.size3d == size3d && other.size1d == size1d &&
            other.data3d?.contentEquals(data3d) == (data3d != null) &&
            other.data1d?.contentEquals(data1d) == (data1d != null)

    override fun hashCode(): Int {
        var h = size3d ?: 0
        h = h * 31 + (size1d ?: 0)
        h = h * 31 + (data3d?.contentHashCode() ?: 0)
        h = h * 31 + (data1d?.contentHashCode() ?: 0)
        return h
    }

    companion object {
        /** Identity 3D LUT — useful for tests and as a neutral fallback. */
        fun identity3d(n: Int = 33): CubeLut {
            val data = FloatArray(n * n * n * 3)
            for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
                val i = (r + g * n + b * n * n) * 3
                data[i] = r / (n - 1).toFloat()
                data[i + 1] = g / (n - 1).toFloat()
                data[i + 2] = b / (n - 1).toFloat()
            }
            return CubeLut(n, null, data, null)
        }
    }
}

/**
 * Parser for the .cube format (Adobe / IRIDAS).
 * Real recursive-line parser with strict validation — throws structured errors.
 */
object CubeLutParser {

    fun parse(text: String): CubeLut {
        var size3d: Int? = null
        var size1d: Int? = null
        var dmin = floatArrayOf(0f, 0f, 0f)
        var dmax = floatArrayOf(1f, 1f, 1f)
        var title = ""
        val table3 = ArrayList<Float>(4096)
        val table1 = ArrayList<Float>(256)

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val tokens = line.split(Regex("\\s+"))
            when (tokens[0].lowercase()) {
                "title" -> title = line.substring(tokens[0].length).trim().removeSurrounding("\"")
                "lut_3d_size" -> {
                    val n = tokens.getOrNull(1)?.toIntOrNull()
                        ?: throw EffectEngineException.InvalidLut("Bad LUT_3D_SIZE: $line")
                    if (n < 2 || n > 256) throw EffectEngineException.InvalidLut("LUT_3D_SIZE out of range: $n")
                    size3d = n
                }
                "lut_1d_size" -> {
                    val n = tokens.getOrNull(1)?.toIntOrNull()
                        ?: throw EffectEngineException.InvalidLut("Bad LUT_1D_SIZE: $line")
                    if (n < 2 || n > 65536) throw EffectEngineException.InvalidLut("LUT_1D_SIZE out of range: $n")
                    size1d = n
                }
                "domain_min" -> {
                    if (tokens.size < 4) throw EffectEngineException.InvalidLut("DOMAIN_MIN needs 3 values")
                    dmin = FloatArray(3) { tokens[it + 1].toFloatOrNull()
                        ?: throw EffectEngineException.InvalidLut("Bad DOMAIN_MIN: $line") }
                }
                "domain_max" -> {
                    if (tokens.size < 4) throw EffectEngineException.InvalidLut("DOMAIN_MAX needs 3 values")
                    dmax = FloatArray(3) { tokens[it + 1].toFloatOrNull()
                        ?: throw EffectEngineException.InvalidLut("Bad DOMAIN_MAX: $line") }
                }
                else -> {
                    if (tokens[0].toFloatOrNull() == null) return@forEach   // unknown keyword — skip
                    if (size3d != null && size1d == null) {
                        if (tokens.size !in 3..4) throw EffectEngineException.InvalidLut("Bad 3D row: $line")
                        for (t in 0..2) table3.add(tokens[t].toFloatOrNull()
                            ?: throw EffectEngineException.InvalidLut("Bad float in: $line"))
                    } else if (size1d != null) {
                        if (tokens.size !in 3..4) throw EffectEngineException.InvalidLut("Bad 1D row: $line")
                        for (t in 0..2) table1.add(tokens[t].toFloatOrNull()
                            ?: throw EffectEngineException.InvalidLut("Bad float in: $line"))
                    } else {
                        throw EffectEngineException.InvalidLut("LUT data before size declaration: $line")
                    }
                }
            }
        }

        size3d?.let { n ->
            val expected = n * n * n * 3
            if (table3.size != expected)
                throw EffectEngineException.InvalidLut("3D LUT expected $expected floats, got ${table3.size}")
            return CubeLut(n, null, table3.toFloatArray(), null, dmin, dmax, title)
        }
        size1d?.let { n ->
            val expected = n * 3
            if (table1.size < expected)
                throw EffectEngineException.InvalidLut("1D LUT expected $expected floats, got ${table1.size}")
            return CubeLut(null, n, null, table1.toFloatArray(), dmin, dmax, title)
        }
        throw EffectEngineException.InvalidLut("No LUT_3D_SIZE or LUT_1D_SIZE declared")
    }
}

object HaldClutParser {
  fun parseHaldSize(width: Int, height: Int): Int {
    return Math.round(Math.cbrt((width * height).toDouble())).toInt()
  }
}
