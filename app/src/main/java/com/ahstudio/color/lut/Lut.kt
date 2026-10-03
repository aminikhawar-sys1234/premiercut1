package com.ahstudio.color.lut

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import kotlin.math.floor

sealed class Lut {
    abstract val title: String
    abstract val domainMin: FloatArray
    abstract val domainMax: FloatArray
}

class Lut1D(
    override val title: String,
    val size: Int,
    /** RGB triplets, size entries, linear in input domain. */
    val data: FloatArray,
    domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    domainMax: FloatArray = floatArrayOf(1f, 1f, 1f)
) : Lut() {
    override val domainMin = domainMin; override val domainMax = domainMax
    init { require(data.size == size * 3) { "1D LUT data count mismatch" } }
}

class Lut3D(
    override val title: String,
    val size: Int,
    /** Cube order: RED fastest, then GREEN, then BLUE (.cube spec). size^3 * 3 floats. */
    val data: FloatArray,
    domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    domainMax: FloatArray = floatArrayOf(1f, 1f, 1f)
) : Lut() {
    override val domainMin = domainMin; override val domainMax = domainMax
    init { require(data.size == size * size * size * 3) { "3D LUT data count mismatch" } }
}

class LutParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Robust .cube parser (§18, §55). Rejects malformed LUTs safely — never crashes the app.
 */
object CubeParser {

    fun parse(stream: InputStream, nameHint: String = ""): Lut {
        try {
            BufferedReader(InputStreamReader(stream)).use { return parse(it, nameHint) }
        } catch (e: LutParseException) { throw e }
        catch (e: IOException) { throw LutParseException("I/O error reading LUT", e) }
        catch (e: Exception) { throw LutParseException("Malformed LUT: ${e.message}", e) }
    }

    fun parse(reader: Reader, nameHint: String = ""): Lut {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        var title = nameHint
        var size1d = -1; var size3d = -1
        var dMin = floatArrayOf(0f, 0f, 0f); var dMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>(4096)

        bufferedReader.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val sp = line.split(Regex("\\s+"))
            when (sp[0].uppercase()) {
                "TITLE" -> title = line.substringAfter("TITLE").trim().trim('"')
                "LUT_1D_SIZE" -> size1d = sp.getOrNull(1)?.toIntOrNull()
                    ?: throw LutParseException("Invalid LUT_1D_SIZE")
                "LUT_3D_SIZE" -> size3d = sp.getOrNull(1)?.toIntOrNull()
                    ?: throw LutParseException("Invalid LUT_3D_SIZE")
                "LUT_1D_INPUT_RANGE", "DOMAIN_MIN" -> {
                    dMin = floatArrayOf(f(sp, 1), f(sp, 2), f(sp, 3))
                }
                "LUT_1D_OUTPUT_RANGE", "DOMAIN_MAX" -> {
                    dMax = floatArrayOf(f(sp, 1), f(sp, 2), f(sp, 3))
                }
                else -> {
                    // Data line: 3 floats (1D) or 3 floats (3D)
                    if (sp.size == 3) {
                        values.add(f(sp, 0)); values.add(f(sp, 1)); values.add(f(sp, 2))
                    } else throw LutParseException("Bad data line: '$line'")
                }
            }
        }

        LutValidator.validate(size1d, size3d, values, dMin, dMax)

        return if (size3d > 0)
            Lut3D(title, size3d, values.toFloatArray(), dMin, dMax)
        else
            Lut1D(title, size1d, values.toFloatArray(), dMin, dMax)
    }

    private fun f(sp: List<String>, i: Int): Float =
        sp.getOrNull(i)?.toFloatOrNull() ?: throw LutParseException("Non-numeric token '${sp.getOrNull(i)}'")
}

/** Validation with explicit reasons (§18). */
object LutValidator {
    fun validate(size1d: Int, size3d: Int, values: List<Float>, dMin: FloatArray, dMax: FloatArray) {
        if (size1d < 0 && size3d < 0) throw LutParseException("Missing LUT size declaration")
        if (size1d >= 0 && size3d >= 0) throw LutParseException("Both 1D and 3D size declared — ambiguous")
        if (size3d > 0) {
            if (size3d !in 2..256) throw LutParseException("3D LUT size out of range: $size3d")
            if (values.size != size3d * size3d * size3d * 3)
                throw LutParseException("3D data count ${values.size} != ${size3d * size3d * size3d * 3}")
        } else {
            if (size1d !in 2..65536) throw LutParseException("1D LUT size out of range: $size1d")
            if (values.size != size1d * 3) throw LutParseException("1D data count ${values.size} != ${size1d * 3}")
        }
        for (i in dMin.indices) if (dMin[i] >= dMax[i]) throw LutParseException("Invalid domain")
        values.forEachIndexed { i, v ->
            if (!v.isFinite()) throw LutParseException("Non-finite value at index $i")
        }
    }
}

/** Trilinear 3D sampling with domain mapping (§19, §21). */
object LutSampler {

    fun sampleTrilinear(lut: Lut3D, rIn: Float, gIn: Float, bIn: Float): FloatArray {
        val n = lut.size
        fun toDomain(c: Float, ch: Int): Float =
            ((c - lut.domainMin[ch]) / (lut.domainMax[ch] - lut.domainMin[ch])).coerceIn(0f, 1f) * (n - 1)

        val x = toDomain(rIn, 0); val y = toDomain(gIn, 1); val z = toDomain(bIn, 2)
        val x0 = floor(x).toInt().coerceIn(0, n - 1); val x1 = (x0 + 1).coerceIn(0, n - 1)
        val y0 = floor(y).toInt().coerceIn(0, n - 1); val y1 = (y0 + 1).coerceIn(0, n - 1)
        val z0 = floor(z).toInt().coerceIn(0, n - 1); val z1 = (z0 + 1).coerceIn(0, n - 1)
        val fx = x - x0; val fy = y - y0; val fz = z - z0

        fun at(xi: Int, yi: Int, zi: Int): Int = (xi + yi * n + zi * n * n) * 3

        fun lerpc(i0: Int, i1: Int, t: Float, ch: Int) =
            lut.data[i0 + ch] * (1f - t) + lut.data[i1 + ch] * t

        val out = FloatArray(3)
        for (ch in 0..2) {
            val c00 = lerpc(at(x0, y0, z0), at(x1, y0, z0), fx, ch)
            val c10 = lerpc(at(x0, y1, z0), at(x1, y1, z0), fx, ch)
            val c01 = lerpc(at(x0, y0, z1), at(x1, y0, z1), fx, ch)
            val c11 = lerpc(at(x0, y1, z1), at(x1, y1, z1), fx, ch)
            val c0 = c00 * (1f - fy) + c10 * fy
            val c1 = c01 * (1f - fy) + c11 * fy
            out[ch] = c0 * (1f - fz) + c1 * fz
        }
        return out
    }

    fun sample1d(lut: Lut1D, r: Float, g: Float, b: Float): FloatArray {
        val n = lut.size
        val idx = floatArrayOf(r, g, b).mapIndexed { ch, c ->
            (((c - lut.domainMin[ch]) / (lut.domainMax[ch] - lut.domainMin[ch])).coerceIn(0f, 1f) * (n - 1))
        }
        val out = FloatArray(3)
        for (ch in 0..2) {
            val p = idx[ch]; val i0 = floor(p).toInt().coerceIn(0, n - 1)
            val i1 = (i0 + 1).coerceIn(0, n - 1); val t = p - i0
            out[ch] = lut.data[i0 * 3 + ch] * (1f - t) + lut.data[i1 * 3 + ch] * t
        }
        return out
    }
}

/** LRU LUT cache — bounded memory (§67). */
class LutCache(private val maxEntries: Int = 8) {
    private val map = LinkedHashMap<String, Lut>(16, 0.75f, true)
    @Synchronized
    fun get(id: String): Lut? = map[id]
    @Synchronized
    fun put(id: String, lut: Lut) {
        map[id] = lut
        while (map.size > maxEntries) map.remove(map.keys.first())
    }
    @Synchronized
    fun clear() = map.clear()
}
