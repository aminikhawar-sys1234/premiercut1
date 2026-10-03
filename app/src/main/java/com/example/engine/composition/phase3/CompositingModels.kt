package com.example.engine.composition.phase3

import kotlin.math.cos
import kotlin.math.sin

data class NormalizedTransform(
  val x: Float = 0f,
  val y: Float = 0f,
  val scaleX: Float = 1f,
  val scaleY: Float = 1f,
  val rotationDegrees: Float = 0f,
  val anchorX: Float = 0.5f,
  val anchorY: Float = 0.5f
) {
  fun matrix(): FloatArray {
    val r = Math.toRadians(rotationDegrees.toDouble())
    val c = cos(r).toFloat(); val s = sin(r).toFloat()
    return floatArrayOf(
      c * scaleX, -s * scaleY, 0f, x,
      s * scaleX, c * scaleY, 0f, y,
      0f, 0f, 1f, 0f,
      0f, 0f, 0f, 1f
    )
  }
}

enum class Phase3BlendMode { NORMAL }

data class VisualLayer(
  val id: String,
  val trackOrder: Int,
  val enabled: Boolean = true,
  val opacity: Float = 1f,
  val transform: NormalizedTransform = NormalizedTransform(),
  val blendMode: Phase3BlendMode = Phase3BlendMode.NORMAL
)

object LayerResolver {
  fun activeAt(layers: Collection<VisualLayer>): List<VisualLayer> = layers
    .asSequence()
    .filter { it.enabled && it.opacity > 0f }
    .sortedWith(compareBy<VisualLayer> { it.trackOrder }.thenBy { it.id })
    .map { it.copy(opacity = it.opacity.coerceIn(0f, 1f)) }
    .toList()
}
