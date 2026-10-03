package com.vfx.engine.core.curve

import com.vfx.engine.core.math.MathUtils

object BezierEvaluator {
  fun evaluateCubicBezier(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val u = 1f - t
    val tt = t * t
    val uu = u * u
    val uuu = uu * u
    val ttt = tt * t
    return (uuu * p0) + (3f * uu * t * p1) + (3f * u * tt * p2) + (ttt * p3)
  }
}

class SplineCurve(private val points: List<Pair<Float, Float>>) {
  fun evaluateAt(x: Float): Float {
    if (points.isEmpty()) return 0f
    if (points.size == 1) return points.first().second
    val sorted = points.sortedBy { it.first }
    if (x <= sorted.first().first) return sorted.first().second
    if (x >= sorted.last().first) return sorted.last().second

    for (i in 0 until sorted.size - 1) {
      val p0 = sorted[i]
      val p1 = sorted[i + 1]
      if (x >= p0.first && x <= p1.first) {
        val t = (x - p0.first) / (p1.first - p0.first)
        return MathUtils.lerp(p0.second, p1.second, t)
      }
    }
    return sorted.last().second
  }
}
