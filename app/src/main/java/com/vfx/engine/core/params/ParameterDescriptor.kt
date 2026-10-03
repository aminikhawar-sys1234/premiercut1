package com.vfx.engine.core.params

import com.vfx.engine.core.math.Color
import com.vfx.engine.core.math.Vec2
import com.vfx.engine.core.math.Vec3
import com.vfx.engine.core.math.Vec4

data class ParamConstraints(
  val min: Float = 0f,
  val max: Float = 1f,
  val step: Float = 0.01f,
  val isLogarithmic: Boolean = false
)

sealed class ParameterDescriptor(val name: String, val defaultValue: ParameterValue) {
  class FloatParam(name: String, default: Float, val constraints: ParamConstraints) :
    ParameterDescriptor(name, ParameterValue.FloatVal(default))

  class IntParam(name: String, default: Int, val min: Int, val max: Int) :
    ParameterDescriptor(name, ParameterValue.IntVal(default))

  class Vec2Param(name: String, default: Vec2) :
    ParameterDescriptor(name, ParameterValue.Vec2Val(default))

  class ColorParam(name: String, default: Color) :
    ParameterDescriptor(name, ParameterValue.ColorVal(default))

  class BoolParam(name: String, default: Boolean) :
    ParameterDescriptor(name, ParameterValue.BoolVal(default))
}

sealed class ParameterValue {
  data class FloatVal(val value: Float) : ParameterValue()
  data class IntVal(val value: Int) : ParameterValue()
  data class Vec2Val(val value: Vec2) : ParameterValue()
  data class Vec3Val(val value: Vec3) : ParameterValue()
  data class Vec4Val(val value: Vec4) : ParameterValue()
  data class ColorVal(val value: Color) : ParameterValue()
  data class BoolVal(val value: Boolean) : ParameterValue()
  data class TextureRefVal(val textureId: String) : ParameterValue()
}

class ParamMap(private val map: Map<String, ParameterValue> = emptyMap()) {
  fun getFloat(name: String, default: Float = 0f): Float {
    return (map[name] as? ParameterValue.FloatVal)?.value ?: default
  }

  fun getInt(name: String, default: Int = 0): Int {
    return (map[name] as? ParameterValue.IntVal)?.value ?: default
  }

  fun getVec2(name: String, default: Vec2 = Vec2()): Vec2 {
    return (map[name] as? ParameterValue.Vec2Val)?.value ?: default
  }

  fun getColor(name: String, default: Color = Color()): Color {
    return (map[name] as? ParameterValue.ColorVal)?.value ?: default
  }

  fun getBool(name: String, default: Boolean = false): Boolean {
    return (map[name] as? ParameterValue.BoolVal)?.value ?: default
  }

  fun copyWith(updates: Map<String, ParameterValue>): ParamMap {
    return ParamMap(map + updates)
  }
}
