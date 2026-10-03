package com.vfx.engine.effects

import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.EffectSnapshot
import com.vfx.engine.core.effect.WorkingSpace
import com.vfx.engine.core.params.FloatP
import com.vfx.engine.gpu.GpuEffectRuntime
import com.vfx.engine.gpu.GpuTexture
import com.vfx.engine.gpu.glsl.EffectGlsl
import com.vfx.engine.gpu.glsl.ShaderTemplate
import com.vfx.engine.graph.GraphBuilder

object TemporalEffects {

    fun all(): List<EffectDefinition> = listOf(
        EffectDefinition(
            id = "temporal.motion_trail",
            name = "Motion Trail",
            category = EffectCategory.TEMPORAL,
            params = listOf(
                FloatP("decay", "Decay", 0.1f, 0.99f, 0.01f, 0.85f),
                FloatP("intensity", "Intensity", 0f, 1f, 0.01f, 1f)
            ),
            description = "Creates persistent ghostly motion blur trails using temporal ping-pong buffers.",
            runtimeFactory = { MotionTrailRuntime() }
        ),
        EffectDefinition(
            id = "temporal.frame_accumulation",
            name = "Frame Accumulation",
            category = EffectCategory.TEMPORAL,
            params = listOf(
                FloatP("decay", "Decay", 0.5f, 0.99f, 0.01f, 0.95f),
                FloatP("intensity", "Intensity", 0f, 1f, 0.01f, 1f)
            ),
            description = "Long exposure style frame accumulation effect.",
            runtimeFactory = { FrameAccumulationRuntime() }
        )
    )

    class MotionTrailRuntime : GpuEffectRuntime {
        override val workingSpace: WorkingSpace = WorkingSpace.GAMMA

        override fun buildPasses(builder: GraphBuilder, input: GpuTexture, s: EffectSnapshot): GpuTexture {
            val ctx = builder.renderCtx
            val slot = ctx.temporal.acquireHistory(s.effectId, input.width, input.height)
            val es3 = ctx.caps.glesMajor >= 3

            val program = ctx.programs.get(
                "temporal.motion_trail",
                ShaderTemplate.vertex(es3),
                ShaderTemplate.fragment(es3, EffectGlsl.MOTION_TRAIL)
            )

            builder.addSecondaryInput("u_history", slot.read.texture)
            val accumulated = builder.addPassPersistent("motion_trail.accum", program, input, slot.write) { p ->
                p.setTexture("u_history", slot.read.texture)
                p.setFloat("u_decay", s.float("decay"))
                p.setFloat("u_intensity", s.intensity * s.float("intensity"))
                p.setFloat("u_warpIntensity", 0f)
            }
            slot.swap()
            return accumulated
        }
    }

    class FrameAccumulationRuntime : GpuEffectRuntime {
        override val workingSpace: WorkingSpace = WorkingSpace.GAMMA

        override fun buildPasses(builder: GraphBuilder, input: GpuTexture, s: EffectSnapshot): GpuTexture {
            val ctx = builder.renderCtx
            val slot = ctx.temporal.acquireHistory(s.effectId, input.width, input.height)
            val es3 = ctx.caps.glesMajor >= 3

            val program = ctx.programs.get(
                "temporal.accumulate",
                ShaderTemplate.vertex(es3),
                ShaderTemplate.fragment(es3, EffectGlsl.FRAME_ACCUMULATE)
            )

            builder.addSecondaryInput("u_history", slot.read.texture)
            val accumulated = builder.addPassPersistent("frame_accum.accum", program, input, slot.write) { p ->
                p.setTexture("u_history", slot.read.texture)
                p.setFloat("u_decay", s.float("decay"))
                p.setFloat("u_intensity", s.intensity * s.float("intensity"))
                p.setFloat("u_warpIntensity", 0f)
            }
            slot.swap()
            return accumulated
        }
    }
}
