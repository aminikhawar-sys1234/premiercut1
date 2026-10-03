package com.ahstudio.screeneditor.composition

import android.graphics.Matrix
import com.ahstudio.screeneditor.ports.BlendMode
import com.ahstudio.screeneditor.ports.EffectChainRef
import com.ahstudio.screeneditor.ports.LayerType
import com.ahstudio.screeneditor.ports.TextStateRef

data class CompositionFrame(
    val version: Long,
    val timelineUs: Long,
    val layers: List<ResolvedLayer>
)

data class ResolvedLayer(
    val id: String,
    val type: LayerType,
    val matrix: Matrix,
    val cropUv: FloatArray,
    val opacity: Float,
    val blendMode: BlendMode,
    val visible: Boolean,
    val effectChain: EffectChainRef?,
    val textState: TextStateRef?,
    val keyframeFlags: Int
)
