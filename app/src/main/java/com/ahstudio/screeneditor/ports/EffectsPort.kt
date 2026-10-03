package com.ahstudio.screeneditor.ports

interface EffectsPort {
    fun chainFor(layerId: String): EffectChainRef?
    fun setParam(layerId: String, chain: EffectChainRef, paramId: String, value: Float)
}
