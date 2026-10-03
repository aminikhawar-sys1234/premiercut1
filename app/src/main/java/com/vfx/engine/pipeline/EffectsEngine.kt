package com.vfx.engine.pipeline

import com.vfx.engine.core.EngineConfig
import com.vfx.engine.core.Microseconds
import com.vfx.engine.core.stack.EffectStack
import com.vfx.engine.core.stack.StackEvaluator

data class RenderRequest(
  val pts: Microseconds,
  val width: Int,
  val height: Int,
  val stack: EffectStack,
  val isExporting: Boolean = false
)

data class RenderExecutionPlan(
  val request: RenderRequest,
  val activePassesCount: Int
)

class EffectsEngine(val config: EngineConfig = EngineConfig()) {

  fun prepareExecutionPlan(request: RenderRequest): RenderExecutionPlan {
    val activeParams = StackEvaluator.evaluateStackAtTime(request.stack, request.pts)
    return RenderExecutionPlan(
      request = request,
      activePassesCount = activeParams.size
    )
  }
}
