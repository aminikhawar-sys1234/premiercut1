package com.ute.core

import android.util.Log

sealed class TextEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class FontNotFound(msg: String) : TextEngineException(msg)
    class ShapingFailure(msg: String, cause: Throwable? = null) : TextEngineException(msg, cause)
    class LayoutFailure(msg: String) : TextEngineException(msg)
    class GpuContextLost(msg: String) : TextEngineException(msg)
    class CapabilityUnsupported(msg: String) : TextEngineException(msg)
    class SerializationFailure(msg: String, cause: Throwable? = null) : TextEngineException(msg, cause)
    class InvalidInput(msg: String) : TextEngineException(msg)
}

/**
 * A text feature failing must NEVER crash the host app. Every render/shape/layout
 * entry point is wrapped: on failure we log once per key and render nothing (or the
 * plain-glyph fallback) instead of throwing.
 */
object Safe {
    private val reported = HashSet<String>()

    inline fun <T> critical(tag: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        report(tag, t); fallback
    }

    inline fun <T> critical(tag: String, block: () -> T?): T? = try {
        block()
    } catch (t: Throwable) {
        report(tag, t); null
    }

    fun report(tag: String, t: Throwable) {
        if (reported.add(tag)) { // dedupe: don't spam logcat at 60fps
            Log.e("UTE/$tag", "Recovered from failure: ${t.message}", t)
        }
    }
}
