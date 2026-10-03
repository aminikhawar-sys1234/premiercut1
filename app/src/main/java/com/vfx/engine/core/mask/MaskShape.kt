package com.vfx.engine.core.mask

import com.vfx.engine.core.math.Vec2

sealed class MaskShape {
  data class Rectangle(val width: Float, val height: Float, val cornerRadius: Float = 0f) : MaskShape()
  data class Circle(val radius: Float) : MaskShape()
  data class FreeformPath(val points: List<Vec2>) : MaskShape()
}

data class MaskConfig(
  val shape: MaskShape,
  val featherPx: Float = 0f,
  val isInverted: Boolean = false,
  val opacity: Float = 1.0f
)
