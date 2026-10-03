package com.ahstudio.color.primary

/**
 * Every constant shared between the CPU reference pipeline and GLSL shader.
 * The GPU shader is GENERATED with these injected as #defines — parity by construction.
 */
object PipelineConsts {
    const val MASK_HI0 = 0.35f; const val MASK_HI1 = 0.95f
    const val MASK_SH0 = 0.05f; const val MASK_SH1 = 0.65f
    const val MASK_WH0 = 0.55f; const val MASK_WH1 = 1.0f
    const val MASK_BL0 = 0.0f;  const val MASK_BL1 = 0.45f

    const val TONE_HI_UP = 0.6f;  const val TONE_HI_DOWN = 0.8f
    const val TONE_SH_UP = 0.35f; const val TONE_SH_DOWN = 0.9f
    const val TONE_WH_UP = 0.3f;  const val TONE_WH_DOWN = 0.7f
    const val TONE_BL_UP = 0.2f;  const val TONE_BL_DOWN = 0.8f

    const val WHEEL_LIFT_K = 0.25f
    const val WHEEL_GAMMA_K = 0.5f
    const val WHEEL_OFFSET_K = 0.25f

    const val HSL_CORE = 0.055f     // ~20deg full-weight core
    const val HSL_FALLOFF = 0.055f  // ~20deg smooth falloff (56deg wide bands, overlapping)

    const val SKIN_HUE_DEG = 30f
    const val SKIN_WIDTH_DEG = 35f

    const val SPLIT_SPREAD = 0.4f
    const val LOG_EPS = 1e-6f

    fun glslDefines(): String = """
#define MASK_HI0 ${MASK_HI0}f
#define MASK_HI1 ${MASK_HI1}f
#define MASK_SH0 ${MASK_SH0}f
#define MASK_SH1 ${MASK_SH1}f
#define MASK_WH0 ${MASK_WH0}f
#define MASK_WH1 ${MASK_WH1}f
#define MASK_BL0 ${MASK_BL0}f
#define MASK_BL1 ${MASK_BL1}f
#define TONE_HI_UP ${TONE_HI_UP}f
#define TONE_HI_DOWN ${TONE_HI_DOWN}f
#define TONE_SH_UP ${TONE_SH_UP}f
#define TONE_SH_DOWN ${TONE_SH_DOWN}f
#define TONE_WH_UP ${TONE_WH_UP}f
#define TONE_WH_DOWN ${TONE_WH_DOWN}f
#define TONE_BL_UP ${TONE_BL_UP}f
#define TONE_BL_DOWN ${TONE_BL_DOWN}f
#define WHEEL_LIFT_K ${WHEEL_LIFT_K}f
#define WHEEL_GAMMA_K ${WHEEL_GAMMA_K}f
#define WHEEL_OFFSET_K ${WHEEL_OFFSET_K}f
#define HSL_CORE ${HSL_CORE}f
#define HSL_FALLOFF ${HSL_FALLOFF}f
#define SKIN_HUE ${SKIN_HUE_DEG}f
#define SKIN_WIDTH ${SKIN_WIDTH_DEG}f
#define SPLIT_SPREAD ${SPLIT_SPREAD}f
#define LOG_EPS ${LOG_EPS}
""".trimIndent()
}
