package com.ahstudio.color.cache

import com.ahstudio.color.core.ColorConfig
import com.ahstudio.color.core.ColorState
import com.ahstudio.color.gpu.ColorShaderManager

/**
 * §60: memoizes the structural pipeline decision. Structural changes (new feature
 * booleans) are rare; this avoids recomputing the VariantKey + define-string path
 * on every frame. Uniform-only changes never touch this class.
 */
class ColorPipelineCache {
    private data class Key(
        val stateFp: Int, val cfgFp: Int, val external: Boolean,
        val lut3dCapable: Boolean, val resBucket: Int
    )
    private var lastKey: Key? = null
    private var lastValue: ColorShaderManager.VariantKey? = null

    @Synchronized
    fun resolve(
        state: ColorState, cfg: ColorConfig, external: Boolean,
        lut3dCapable: Boolean, width: Int,
        compute: () -> ColorShaderManager.VariantKey
    ): ColorShaderManager.VariantKey {
        val resBucket = if (width >= 3840) 4 else if (width >= 1920) 2 else 1
        val k = Key(state.fingerprint(), cfg.hashCode(), external, lut3dCapable, resBucket)
        if (k == lastKey && lastValue != null) return lastValue!!
        val v = compute()
        lastKey = k; lastValue = v
        return v
    }
}
