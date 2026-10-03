package com.ahstudio.audio.master.diagnostics

class AudioPerformanceMonitor(private val sampleRate: Int = 48_000) {
    private var ema = 0.0
    @Volatile var overrunCount = 0L; private set
    fun onBlockRendered(renderMs: Double, frames: Int) {
        ema = if (ema == 0.0) renderMs else ema * 0.9 + renderMs * 0.1
        if (renderMs > frames * 1000.0 / sampleRate) overrunCount++
    }
    val averageRenderMs: Double get() = ema
    fun reset() { ema = 0.0; overrunCount = 0 }
}

class AudioGlitchDetector {
    @Volatile var underrunCount = 0L; private set
    @Volatile var discontinuityCount = 0L; private set
    fun onUnderrun() { underrunCount++ }
    fun onDiscontinuity() { discontinuityCount++ }
    fun reset() { underrunCount = 0; discontinuityCount = 0 }
}

object AudioDebugLogger {
    @Volatile var enabled = false
    fun d(tag: String, msg: String) { if (enabled) runCatching { android.util.Log.d(tag, msg) } }
    fun w(tag: String, msg: String) { if (enabled) runCatching { android.util.Log.w(tag, msg) } }
    fun e(tag: String, msg: String, t: Throwable? = null) { if (enabled) runCatching { android.util.Log.e(tag, msg, t) } }
}

object AudioEngineDiagnostics {
    fun report(monitor: AudioPerformanceMonitor, glitches: AudioGlitchDetector,
               renderMs: Double, clippedSamples: Long): String = buildString {
        appendLine("=== AhStudioAudioMasterEngine diagnostics ===")
        appendLine("avgRenderMs=%.3f (last=%.3f)".format(monitor.averageRenderMs, renderMs))
        appendLine("blockOverruns=${monitor.overrunCount} underruns=${glitches.underrunCount} discontinuities=${glitches.discontinuityCount}")
        appendLine("clippedSamples=$clippedSamples")
    }
}
