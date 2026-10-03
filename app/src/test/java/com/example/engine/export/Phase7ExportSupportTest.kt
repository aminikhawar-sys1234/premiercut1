package com.example.engine.export

import com.example.domain.model.Resolution
import com.example.domain.model.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase7ExportSupportTest {
  @Test
  fun longTimelineUsesChunking() {
    val timeline = Timeline(videoClips = listOf(com.example.domain.model.VideoClip(name = "clip", timelineStartMs = 0L, durationMs = 60_000L)))
    val policy = Phase7RenderPolicyResolver.resolve(ExportConfig(), timeline, 4096L)
    assertTrue(policy.useChunking)
    assertEquals(10_000L, policy.chunkDurationMs)
    assertTrue(policy.allowSoftwareFallback)
  }

  @Test
  fun fourKUsesShorterChunks() {
    val timeline = Timeline(videoClips = listOf(com.example.domain.model.VideoClip(name = "clip", timelineStartMs = 0L, durationMs = 5_000L)))
    val config = ExportConfig(resolution = Resolution.RES_4K)
    val policy = Phase7RenderPolicyResolver.resolve(config, timeline, 4096L)
    assertTrue(policy.useChunking)
    assertEquals(5_000L, policy.chunkDurationMs)
  }

  @Test
  fun checkpointIsAtomicAndOnlyReturnsCompletedExistingOutput() {
    val dir = createTempDir(prefix = "phase7-checkpoint-")
    try {
      val store = Phase7ExportCheckpointStore(dir)
      val output = File(dir, "final.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
      store.save("abc", output.absolutePath, 5000L, completed = false)
      assertNull(store.findCompleted("abc"))
      store.save("abc", output.absolutePath, 5000L, completed = true)
      assertNotNull(store.findCompleted("abc"))
      assertNull(store.findCompleted("different"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun retryPolicyStopsAtLimitAndBacksOff() {
    val first = Phase7RetryPolicy.next(0, 3)
    val second = Phase7RetryPolicy.next(1, 3)
    val exhausted = Phase7RetryPolicy.next(3, 3)
    assertTrue(first.shouldRetry)
    assertTrue(second.delayMs > first.delayMs)
    assertTrue(!exhausted.shouldRetry)
  }
}
