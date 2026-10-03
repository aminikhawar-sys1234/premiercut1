package com.vfx.engine.core.serialization

import com.vfx.engine.core.stack.EffectStack

data class Preset(
  val id: String,
  val name: String,
  val category: String,
  val stack: EffectStack
)

object EffectSerializer {
  fun serializePresetToJson(preset: Preset): String {
    return """{"id":"${preset.id}","name":"${preset.name}","category":"${preset.category}"}"""
  }
}
