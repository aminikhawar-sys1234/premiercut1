package com.example

import com.example.engine.export.AsyncFramePipelineMetrics
import com.example.engine.export.FramePacketQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AsyncFramePipelineEngineTest {
  @Test fun `bounded queue preserves frame order`() {
    val queue = FramePacketQueue<Int>(3)
    val cancelled = AtomicBoolean(false)
    queue.put(0, cancelled); queue.put(1, cancelled); queue.put(2, cancelled)
    assertEquals(0, queue.take(cancelled))
    assertEquals(1, queue.take(cancelled))
    assertEquals(2, queue.take(cancelled))
    assertEquals(0, queue.depth())
  }

  @Test fun `queue remains bounded`() {
    val queue = FramePacketQueue<Int>(3)
    val cancelled = AtomicBoolean(false)
    repeat(3) { assertTrue(queue.put(it, cancelled)) }
    assertEquals(3, queue.depth())
  }

  @Test fun `metrics expose zero copy counters`() {
    val metrics = AsyncFramePipelineMetrics()
    metrics.decodedFrames.incrementAndGet()
    metrics.gpuFrames.incrementAndGet()
    metrics.zeroCopyFrames.incrementAndGet()
    val snapshot = metrics.snapshot()
    assertEquals(1L, snapshot["decodedFrames"])
    assertEquals(1L, snapshot["gpuFrames"])
    assertEquals(1L, snapshot["zeroCopyFrames"])
    assertEquals(0L, snapshot["gpuToCpuCopies"])
  }
}
