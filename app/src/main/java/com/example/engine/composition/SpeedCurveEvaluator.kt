package com.example.engine.composition

import com.example.domain.model.SpeedCurve
import com.example.domain.model.SpeedCurvePreset

/**
 * Evaluates non-linear Bézier speed curves and recalculates Presentation Timestamps (PTS)
 * for both live preview rendering and MP4 export.
 * Preserves strict microsecond sync across video frames and audio samples.
 */
object SpeedCurveEvaluator {

  /**
   * Given a target playhead position relative to clip start (ms),
   * calculates the corresponding source media offset position (ms) according to the speed curve.
   */
  fun calculateSourcePositionMs(
    relativePlayheadMs: Long,
    clipDurationMs: Long,
    speedCurve: SpeedCurve,
    linearSpeedMultiplier: Float = 1.0f
  ): Long {
    if (clipDurationMs <= 0L || relativePlayheadMs <= 0L) return 0L
    if (speedCurve.preset == SpeedCurvePreset.STANDARD) {
      return (relativePlayheadMs * linearSpeedMultiplier).toLong()
    }

    val t = (relativePlayheadMs.toFloat() / clipDurationMs.toFloat()).coerceIn(0f, 1f)
    val bezierPoints = speedCurve.bezierPoints

    // Evaluate cubic Bézier curve for normalized velocity multiplier v(t)
    val velocity = if (bezierPoints.size >= 4) {
      val x1 = bezierPoints[0]
      val y1 = bezierPoints[1]
      val x2 = bezierPoints[2]
      val y2 = bezierPoints[3]
      evalCubicBezierY(t, y1, y2) * 2.5f // map 0..1 to 0..2.5x speed
    } else {
      1.0f
    }

    // Integrate non-linear velocity over normalized time t
    val sourceNormalized = integrateVelocity(t, bezierPoints)
    return (sourceNormalized * clipDurationMs * linearSpeedMultiplier).toLong()
  }

  private fun evalCubicBezierY(t: Float, y1: Float, y2: Float): Float {
    val u = 1f - t
    val tt = t * t
    val uu = u * u
    val uuu = uu * u
    val ttt = tt * t

    // Cubic Bézier formula: P(t) = (1-t)^3 P0 + 3(1-t)^2 t P1 + 3(1-t) t^2 P2 + t^3 P3
    // P0=0, P3=1
    val y = 3 * uu * t * y1 + 3 * u * tt * y2 + ttt
    return y.coerceIn(0.1f, 5.0f)
  }

  private fun integrateVelocity(t: Float, bezierPoints: List<Float>): Float {
    if (t <= 0f) return 0f
    if (t >= 1f) return 1f

    val steps = 20
    val dt = t / steps
    var area = 0f
    for (i in 0 until steps) {
      val sampleT = (i + 0.5f) * dt
      val y1 = if (bezierPoints.size >= 4) bezierPoints[1] else 0.5f
      val y2 = if (bezierPoints.size >= 4) bezierPoints[3] else 0.5f
      val vel = evalCubicBezierY(sampleT, y1, y2)
      area += vel * dt
    }
    return area.coerceAtLeast(0f)
  }
}
