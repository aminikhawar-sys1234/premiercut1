package com.example

import com.example.engine.export.ExportPipelineMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareExportMetricsTest {
  @Test
  fun zeroCopyFastPathRequiresNoGpuReadback() {
    val metrics = ExportPipelineMetrics()
    metrics.onFrameRendered(1_000_000L)
    metrics.onFrameEncoded(2_000_000L)
    val snapshot = metrics.snapshot()
    assertEquals(1L, snapshot.renderedFrames)
    assertEquals(1L, snapshot.encodedFrames)
    assertTrue(snapshot.zeroCopyFastPath)
  }

  @Test
  fun gpuReadbackDisablesFastPath() {
    val metrics = ExportPipelineMetrics()
    metrics.onGpuToCpuCopy()
    assertTrue(!metrics.snapshot().zeroCopyFastPath)
  }
}
