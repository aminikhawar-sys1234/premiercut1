package com.ahstudio.color.diagnostics

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

class HistogramResult(val r: IntArray, val g: IntArray, val b: IntArray, val luma: IntArray, val sampleCount: Long) {
    fun clippedHighlights(): Float = luma[255].toFloat() / maxOf(sampleCount, 1)
    fun clippedBlacks(): Float = luma[0].toFloat() / maxOf(sampleCount, 1)
}

/** §35: histogram — samples the ALREADY-GRADED frame. Never blocks UI thread. */
class HistogramAnalyzer {
    suspend fun compute(pixels: IntArray, stride: Int = 4): HistogramResult = withContext(Dispatchers.Default) {
        val r = IntArray(256); val g = IntArray(256); val b = IntArray(256); val l = IntArray(256)
        var n = 0L
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            val rr = Color.red(p); val gg = Color.green(p); val bb = Color.blue(p)
            r[rr]++; g[gg]++; b[bb]++
            l[(0.2126f * rr + 0.7152f * gg + 0.0722f * bb).toInt().coerceIn(0, 255)]++
            n++; i += stride
        }
        HistogramResult(r, g, b, l, n)
    }
}

/** §36: luma waveform — width columns × 256 levels. Diagnostic only. */
class WaveformAnalyzer {
    suspend fun compute(pixels: IntArray, width: Int, height: Int): Array<IntArray> = withContext(Dispatchers.Default) {
        val out = Array(width) { IntArray(256) }
        for (y in 0 until height) for (x in 0 until width) {
            val p = pixels[y * width + x]
            val l = (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)).toInt().coerceIn(0, 255)
            out[x][l]++
        }
        out
    }
}

/** §37: vectorscope — plots (B-Y, R-Y); skin-tone line at ~147°,33° I-line drawn by the UI layer. */
class VectorscopeAnalyzer(private val size: Int = 256) {
    suspend fun compute(pixels: IntArray): Bitmap = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val acc = FloatArray(size * size)
        for (p in pixels) {
            val r = Color.red(p) / 255f; val g = Color.green(p) / 255f; val b = Color.blue(p) / 255f
            val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val pb = (b - y) * 2f
            val pr = (r - y) * 2f
            val px = (size / 2f + pb * (size / 2f)).toInt().coerceIn(0, size - 1)
            val py = (size / 2f - pr * (size / 2f)).toInt().coerceIn(0, size - 1)
            acc[py * size + px] += 1f
        }
        val maxA = acc.maxOrNull() ?: 1f
        val colors = IntArray(size * size) { i ->
            if (acc[i] <= 0f) Color.TRANSPARENT
            else Color.argb((min(1f, acc[i] / (maxA * 0.25f)) * 255f).toInt(), 120, 255, 160)
        }
        bmp.setPixels(colors, 0, size, 0, 0, size, size)
        bmp
    }
}

/** §38: false color — monitoring-only shader pass (hooked via uDebug.z in the main shader). */
object FalseColor {
    const val NOTE = "Enabled through ColorConfig.debugStage + uDebug.z false-color mode. Never destructive."
}
