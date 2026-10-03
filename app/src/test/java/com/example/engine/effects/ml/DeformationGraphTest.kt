package com.example.engine.effects.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeformationGraphTest {
  @Test fun safeParametersAreBounded() {
    val p = DeformationGraph.Parameters(
      intensity = 4f, radius = -1f, falloff = 20f,
      strength = 9f, symmetry = -1f, horizontalScale = 9f,
      verticalScale = -4f, maskStrength = 3f
    ).safe()
    assertEquals(1f, p.intensity, 0f)
    assertTrue(p.radius > 0f)
    assertEquals(8f, p.falloff, 0f)
    assertEquals(1f, p.strength, 0f)
    assertEquals(0f, p.symmetry, 0f)
  }

  @Test fun maskWeightedWarpInterpolates() {
    val a = DeformationGraph.Vec2(0f, 0f)
    val b = DeformationGraph.Vec2(10f, 10f)
    val out = DeformationGraph.maskWeightedWarp(a, b, .5f, 1f)
    assertEquals(5f, out.x, .0001f)
    assertEquals(5f, out.y, .0001f)
  }
}
