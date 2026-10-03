package com.example.engine.effects.ml

import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalLandmarkSmootherTest {
  @Test fun smoothingKeepsStateAndVelocity() {
    val s = TemporalLandmarkSmoother()
    s.update(1L, 0f, 0f, 1f, 0L)
    val next = s.update(1L, 1f, 0f, 1f, 33L)
    assertTrue(next.x in 0f..1f)
    assertTrue(next.vx > 0f)
  }

  @Test fun missingTrackCanBePredictedBriefly() {
    val s = TemporalLandmarkSmoother()
    s.update(7L, 0f, 0f, 1f, 0L)
    s.update(7L, 1f, 0f, 1f, 33L)
    assertTrue(s.predict(7L, 80L) != null)
  }
}
