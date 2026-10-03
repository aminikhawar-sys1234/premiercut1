package com.ahstudio.animation.diagnostics

import java.util.concurrent.atomic.AtomicLong

/** Lightweight evaluation profiler. Zero-allocation when disabled. */
class AnimationProfiler {
    @Volatile var enabled: Boolean = false
    val evaluations = AtomicLong()
    val propertiesEvaluated = AtomicLong()
    val totalNanos = AtomicLong()

    inline fun <T> measure(block: () -> T): T {
        if (!enabled) return block()
        val t0 = System.nanoTime()
        try { return block() } finally {
            totalNanos.addAndGet(System.nanoTime() - t0)
            evaluations.incrementAndGet()
        }
    }
    fun report(): String {
        val n = evaluations.get()
        val avgUs = if (n == 0L) 0.0 else totalNanos.get() / n / 1000.0
        return "evals=$n avgMicros=$avgUs propsEvaluated=${propertiesEvaluated.get()}"
    }
    fun reset() { evaluations.set(0); totalNanos.set(0); propertiesEvaluated.set(0) }
}
