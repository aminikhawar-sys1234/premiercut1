package com.example.engine.effects.ml

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** Reusable, deterministic deformation primitives shared by all human effects. */
object DeformationGraph {
  data class Parameters(
    val intensity: Float = 0f,
    val radius: Float = 1f,
    val falloff: Float = 2f,
    val strength: Float = 1f,
    val symmetry: Float = 1f,
    val horizontalScale: Float = 1f,
    val verticalScale: Float = 1f,
    val maskStrength: Float = 1f
  ) {
    fun safe() = copy(
      intensity = intensity.coerceIn(0f, 1f),
      radius = radius.coerceAtLeast(0.0001f),
      falloff = falloff.coerceIn(.25f, 8f),
      strength = strength.coerceIn(-1f, 1f),
      symmetry = symmetry.coerceIn(0f, 1f),
      horizontalScale = horizontalScale.coerceIn(.1f, 3f),
      verticalScale = verticalScale.coerceIn(.1f, 3f),
      maskStrength = maskStrength.coerceIn(0f, 1f)
    )
  }

  data class Vec2(val x: Float, val y: Float)

  fun radialWarp(p: Vec2, center: Vec2, radius: Float, strength: Float, falloff: Float = 2f): Vec2 {
    val dx = p.x - center.x
    val dy = p.y - center.y
    val d = sqrt(dx * dx + dy * dy)
    if (d <= 0f || radius <= 0f) return p
    val w = exp(-((d / radius) * (d / radius)) * falloff) * strength
    val scale = 1f + w
    return Vec2(center.x + dx * scale, center.y + dy * scale)
  }

  fun directionalWarp(p: Vec2, center: Vec2, direction: Vec2, radius: Float, amount: Float, falloff: Float = 2f): Vec2 {
    val dx = p.x - center.x
    val dy = p.y - center.y
    val d = sqrt(dx * dx + dy * dy)
    val w = exp(-((d / max(radius, .0001f)) * (d / max(radius, .0001f))) * falloff) * amount
    return Vec2(p.x + direction.x * w, p.y + direction.y * w)
  }

  fun scaleRegion(p: Vec2, center: Vec2, sx: Float, sy: Float, radius: Float, falloff: Float = 2f): Vec2 {
    val dx = p.x - center.x
    val dy = p.y - center.y
    val d = sqrt(dx * dx + dy * dy)
    val w = exp(-((d / max(radius, .0001f)) * (d / max(radius, .0001f))) * falloff)
    return Vec2(center.x + dx * (1f + (sx - 1f) * w), center.y + dy * (1f + (sy - 1f) * w))
  }

  fun stretchRegion(p: Vec2, center: Vec2, axis: Vec2, amount: Float, radius: Float): Vec2 =
    directionalWarp(p, center, axis, radius, amount)

  fun compressRegion(p: Vec2, center: Vec2, axis: Vec2, amount: Float, radius: Float): Vec2 =
    directionalWarp(p, center, axis, radius, -amount)

  fun pinchRegion(p: Vec2, center: Vec2, radius: Float, amount: Float): Vec2 =
    radialWarp(p, center, radius, -amount)

  fun bulgeRegion(p: Vec2, center: Vec2, radius: Float, amount: Float): Vec2 =
    radialWarp(p, center, radius, amount)

  fun smoothFalloff(distance: Float, radius: Float, exponent: Float = 2f): Float {
    if (radius <= 0f) return 0f
    val n = (distance / radius).coerceIn(0f, 1f)
    return exp(-n * n * exponent)
  }

  fun maskWeightedWarp(original: Vec2, warped: Vec2, mask: Float, strength: Float): Vec2 {
    val w = (mask * strength).coerceIn(0f, 1f)
    return Vec2(original.x + (warped.x - original.x) * w, original.y + (warped.y - original.y) * w)
  }

  fun safePoint(p: Vec2): Vec2 = Vec2(
    if (p.x.isFinite()) p.x.coerceIn(-2f, 2f) else 0f,
    if (p.y.isFinite()) p.y.coerceIn(-2f, 2f) else 0f
  )
}
