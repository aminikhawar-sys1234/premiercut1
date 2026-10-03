package com.vfx.engine.gpu

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Owns the EGL context. ALL GPU work happens on this thread.
 * Public engine APIs marshal here — callers never touch GL directly.
 */
class GlThread(name: String = "vfx-gl") {

    private val thread = HandlerThread(name).apply { start() }
    val handler = Handler(thread.looper)
    val isCurrentThread: Boolean get() = Thread.currentThread() === thread

    fun post(block: () -> Unit) = handler.post(block)

    /** Runs [block] on the GL thread and waits for the result. Re-entrant safe. */
    fun <T> await(timeoutMs: Long = 15_000, block: () -> T): T {
        if (isCurrentThread) return block()
        val latch = CountDownLatch(1)
        var result: T? = null
        var error: Throwable? = null
        handler.post {
            try { result = block() } catch (t: Throwable) { error = t } finally { latch.countDown() }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS))
            throw TimeoutException("GL task exceeded ${timeoutMs}ms")
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    class TimeoutException(message: String) : RuntimeException(message)

    fun quitSafely() = thread.quitSafely()
}
