package com.vfx.engine.effects

import android.opengl.GLES30
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.EffectSnapshot
import com.vfx.engine.core.effect.WorkingSpace
import com.vfx.engine.core.lut.CubeLut
import com.vfx.engine.core.lut.CubeLutParser
import com.vfx.engine.core.params.EnumP
import com.vfx.engine.core.params.FloatP
import com.vfx.engine.core.params.StringP
import com.vfx.engine.gpu.GpuEffectRuntime
import com.vfx.engine.gpu.GpuTexture
import com.vfx.engine.gpu.glsl.EffectGlsl
import com.vfx.engine.gpu.glsl.ShaderTemplate
import com.vfx.engine.graph.GraphBuilder

object LutEffects {

    val BUILTIN_PRESETS = listOf(
        "Warm Sunset", "Cool Teal", "Vintage Film", "Bleach Bypass", "Cyberpunk", "Moody B&W"
    )

    private val PRESET_FILES = mapOf(
        "Warm Sunset" to "luts/warm_sunset.cube",
        "Cool Teal" to "luts/cool_teal.cube",
        "Vintage Film" to "luts/vintage_film.cube",
        "Bleach Bypass" to "luts/bleach_bypass.cube",
        "Cyberpunk" to "luts/cyberpunk.cube",
        "Moody B&W" to "luts/moody_bw.cube"
    )

    fun all(): List<EffectDefinition> = listOf(
        EffectDefinition(
            id = "lut.preset",
            name = "LUT Preset",
            category = EffectCategory.LUT,
            params = listOf(
                EnumP("preset", "Preset", BUILTIN_PRESETS, 0),
                FloatP("intensity", "Intensity", 0f, 1f, 0.01f, 1f)
            ),
            description = "Applies a cinematic 3D color lookup table preset.",
            runtimeFactory = { LutRuntime(isPreset = true) }
        ),
        EffectDefinition(
            id = "lut.custom",
            name = "Custom 3D LUT",
            category = EffectCategory.LUT,
            params = listOf(
                StringP("path", "Cube File Path", ""),
                FloatP("intensity", "Intensity", 0f, 1f, 0.01f, 1f)
            ),
            description = "Loads and applies an Adobe / IRIDAS .cube 3D LUT.",
            runtimeFactory = { LutRuntime(isPreset = false) }
        )
    )

    class LutRuntime(private val isPreset: Boolean) : GpuEffectRuntime {
        override val workingSpace: WorkingSpace = WorkingSpace.GAMMA

        override fun buildPasses(builder: GraphBuilder, input: GpuTexture, s: EffectSnapshot): GpuTexture {
            val ctx = builder.renderCtx
            val lutCube: CubeLut = if (isPreset) {
                val presetName = s.string("preset")
                val path = PRESET_FILES[presetName] ?: "luts/warm_sunset.cube"
                ctx.lutEngine.loadAssetCube(path)
            } else {
                val path = s.string("path")
                if (path.isNotBlank()) {
                    ctx.lutEngine.loadAssetCube(path)
                } else {
                    CubeLut.identity3d(33)
                }
            }

            val lutTex = ctx.lutEngine.getCubeLut(lutCube)
            val is3d = lutTex.spec.target == GLES30.GL_TEXTURE_3D
            val es3 = ctx.caps.glesMajor >= 3 && is3d

            val program = ctx.programs.get(
                if (is3d) "lut.3d" else "lut.2d",
                ShaderTemplate.vertex(es3),
                ShaderTemplate.fragment(
                    es3 = es3,
                    effectBody = if (is3d) EffectGlsl.LUT_3D else EffectGlsl.LUT_2D_ATLAS,
                    chunkExtras = ""
                )
            )

            val lutSize = (lutCube.size3d ?: 33).toFloat()

            return builder.addPass("lut", program, input) { p ->
                p.setTexture("u_lut", lutTex)
                p.setFloat("u_lutSize", lutSize)
                p.setFloat("u_intensity", s.intensity * s.float("intensity"))
                p.setFloat("u_warpIntensity", 0f)
            }
        }
    }
}
