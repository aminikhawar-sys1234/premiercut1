package com.example.engine.export

import java.util.concurrent.atomic.AtomicLong

/** Lightweight thread-safe instrumentation for the hardware export pipeline. */
class ExportPipelineMetrics {
  private val renderedFrames = AtomicLong(0)
  private val encodedFrames = AtomicLong(0)
  private val droppedFrames = AtomicLong(0)
  private val duplicatedFrames = AtomicLong(0)
  private val gpuToCpuCopies = AtomicLong(0)
  private val cpuToGpuCopies = AtomicLong(0)
  private val gpuRenderNanos = AtomicLong(0)
  private val encodeNanos = AtomicLong(0)

  fun onFrameRendered(durationNanos: Long) {
    renderedFrames.incrementAndGet()
    gpuRenderNanos.addAndGet(durationNanos.coerceAtLeast(0L))
  }

  fun onFrameEncoded(durationNanos: Long) {
    encodedFrames.incrementAndGet()
    encodeNanos.addAndGet(durationNanos.coerceAtLeast(0L))
  }

  fun onFrameDropped() { droppedFrames.incrementAndGet() }
  fun onFrameDuplicated() { duplicatedFrames.incrementAndGet() }
  fun onGpuToCpuCopy() { gpuToCpuCopies.incrementAndGet() }
  fun onCpuToGpuCopy() { cpuToGpuCopies.incrementAndGet() }

  fun snapshot(): Snapshot = Snapshot(
    renderedFrames = renderedFrames.get(),
    encodedFrames = encodedFrames.get(),
    droppedFrames = droppedFrames.get(),
    duplicatedFrames = duplicatedFrames.get(),
    gpuToCpuCopies = gpuToCpuCopies.get(),
    cpuToGpuCopies = cpuToGpuCopies.get(),
    totalGpuRenderNanos = gpuRenderNanos.get(),
    totalEncodeNanos = encodeNanos.get()
  )

  data class Snapshot(
    val renderedFrames: Long,
    val encodedFrames: Long,
    val droppedFrames: Long,
    val duplicatedFrames: Long,
    val gpuToCpuCopies: Long,
    val cpuToGpuCopies: Long,
    val totalGpuRenderNanos: Long,
    val totalEncodeNanos: Long
  ) {
    val zeroCopyFastPath: Boolean get() = gpuToCpuCopies == 0L
  }
}
