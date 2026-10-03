package com.ahstudio.integration

import com.ahstudio.screeneditor.ports.EffectChainRef
import com.ahstudio.screeneditor.ports.EffectsPort
import com.example.engine.TimelineEngine

class AhEffectsAdapter(
    private val timelineEngine: TimelineEngine
) : EffectsPort {

    override fun chainFor(layerId: String): EffectChainRef? {
        val tl = timelineEngine.timeline.value
        val eff = tl.effectClips.firstOrNull { it.id == layerId || it.targetClipId == layerId }
        return if (eff != null) {
            EffectChainRef(chainId = eff.id, effectIds = listOf(eff.effectType.name))
        } else null
    }

    override fun setParam(layerId: String, chain: EffectChainRef, paramId: String, value: Float) {
        val cur = timelineEngine.timeline.value
        val newEffects = cur.effectClips.map {
            if (it.id == layerId) it.copy(intensity = value.coerceIn(0f, 1f)) else it
        }
        timelineEngine.loadTimeline(cur.copy(effectClips = newEffects))
    }
}
