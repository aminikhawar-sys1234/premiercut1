package com.vfx.engine.effects.base

import com.vfx.engine.core.effect.Effect
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.params.ParamMap
import com.vfx.engine.gpu.fbo.FramebufferObject

abstract class BaseEffect(override val definition: EffectDefinition) : Effect {
  abstract fun render(
    inputFbo: FramebufferObject,
    outputFbo: FramebufferObject,
    params: ParamMap
  )
}

abstract class SinglePassEffect(definition: EffectDefinition) : BaseEffect(definition)
abstract class MultiPassEffect(definition: EffectDefinition) : BaseEffect(definition)
