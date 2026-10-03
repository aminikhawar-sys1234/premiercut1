package com.example

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.EffectClip
import com.example.domain.model.EffectType
import com.example.domain.model.KeyframeInterpolation
import com.example.engine.composition.Phase4EffectParameterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4EffectParameterEngineTest {

  @Test
  fun emptyTrackFallsBackToEffectIntensity() {
    val effect = EffectClip(effectType = EffectType.GLOW, intensity = 0.73f)
    assertEquals(0.73f, Phase4EffectParameterEngine.evaluateIntensity(effect, 500L), 0.0001f)
  }

  @Test
  fun linearEvaluationUsesClipLocalTime() {
    val effect = EffectClip(
      effectType = EffectType.GLOW,
      timelineStartMs = 1_000L,
      keyframes = listOf(
        ClipKeyframe(timeMs = 0L, effectParam = 0f),
        ClipKeyframe(timeMs = 2_000L, effectParam = 1f)
      )
    )
    assertEquals(0.25f, Phase4EffectParameterEngine.evaluateIntensity(effect, 1_500L), 0.0001f)
  }

  @Test
  fun holdKeepsPreviousValueUntilNextKey() {
    val keys = listOf(
      ClipKeyframe(timeMs = 0L, effectParam = 0.2f, interpolation = KeyframeInterpolation.HOLD),
      ClipKeyframe(timeMs = 1_000L, effectParam = 0.9f)
    )
    assertEquals(0.2f, Phase4EffectParameterEngine.evaluateEffectParam(keys, 750L), 0.0001f)
  }

  @Test
  fun cubicBezierIsBoundedAndMonotonicForStandardCurve() {
    val keys = listOf(
      ClipKeyframe(timeMs = 0L, effectParam = 0f, interpolation = KeyframeInterpolation.CUBIC_BEZIER),
      ClipKeyframe(timeMs = 1_000L, effectParam = 1f)
    )
    var previous = 0f
    for (t in 0..1_000 step 25) {
      val value = Phase4EffectParameterEngine.evaluateEffectParam(keys, t.toLong())
      assertTrue(value in 0f..1f)
      assertTrue(value + 0.001f >= previous)
      previous = value
    }
  }

  @Test
  fun upsertReplacesNearDuplicateAndRemoveDeletesIt() {
    val original = listOf(ClipKeyframe(timeMs = 1_000L, effectParam = 0.2f))
    val updated = Phase4EffectParameterEngine.upsertEffectParam(original, 1_008L, 0.8f, toleranceMs = 16L)
    assertEquals(1, updated.size)
    assertEquals(0.8f, updated.single().effectParam, 0.0001f)

    val removed = Phase4EffectParameterEngine.removeEffectParamAt(updated, 1_004L, toleranceMs = 16L)
    assertTrue(removed.isEmpty())
  }
}
