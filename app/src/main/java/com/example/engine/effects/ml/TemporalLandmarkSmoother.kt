package com.example.engine.effects.ml

import kotlin.math.abs
import kotlin.math.max

/** Motion-aware temporal state: adaptive EMA + velocity prediction, not frame averaging. */
class TemporalLandmarkSmoother(
  private val staticAlpha: Float = 0.16f,
  private val motionAlpha: Float = 0.72f,
  private val maxPredictionMs: Long = 120L
) {
  data class State(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var confidence: Float,
    var previousX: Float,
    var previousY: Float,
    var lastTimestampMs: Long,
    var lastValidTimestampMs: Long
  )

  private val states = mutableMapOf<Long, State>()

  @Synchronized
  fun update(id: Long, x: Float, y: Float, confidence: Float, timestampMs: Long): State {
    val c = confidence.coerceIn(0f, 1f)
    val old = states[id]
    if (old == null) {
      val s = State(id, x, y, 0f, 0f, c, x, y, timestampMs, timestampMs)
      states[id] = s
      return s
    }
    val dt = max(1L, timestampMs - old.lastTimestampMs).toFloat() / 1000f
    val rawVx = (x - old.x) / dt
    val rawVy = (y - old.y) / dt
    val speed = abs(rawVx) + abs(rawVy)
    val alpha = (staticAlpha + (motionAlpha - staticAlpha) * (speed / (speed + 0.8f))).coerceIn(staticAlpha, motionAlpha)
    old.previousX = old.x
    old.previousY = old.y
    old.vx = old.vx * 0.35f + rawVx * 0.65f
    old.vy = old.vy * 0.35f + rawVy * 0.65f
    val predictedX = x + old.vx * dt * 0.20f
    val predictedY = y + old.vy * dt * 0.20f
    old.x += (predictedX - old.x) * alpha
    old.y += (predictedY - old.y) * alpha
    old.confidence = c
    old.lastTimestampMs = timestampMs
    old.lastValidTimestampMs = timestampMs
    return old
  }

  @Synchronized
  fun predict(id: Long, timestampMs: Long): State? {
    val s = states[id] ?: return null
    val dtMs = (timestampMs - s.lastValidTimestampMs).coerceIn(0L, maxPredictionMs)
    val dt = dtMs / 1000f
    s.x += s.vx * dt * 0.35f
    s.y += s.vy * dt * 0.35f
    s.confidence *= 0.82f
    s.lastTimestampMs = timestampMs
    return s
  }

  @Synchronized
  fun reset(id: Long) { states.remove(id) }

  @Synchronized
  fun resetAll() { states.clear() }

  @Synchronized
  fun prune(nowMs: Long, timeoutMs: Long = 500L) {
    states.entries.removeAll { nowMs - it.value.lastValidTimestampMs > timeoutMs }
  }
}
