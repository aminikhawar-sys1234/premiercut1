package com.ahstudio.screeneditor.composition

import android.graphics.Matrix
import com.ahstudio.screeneditor.bridge.ScreenEditorKeyframeBridge
import com.ahstudio.screeneditor.layers.ScreenEditorLayerManager
import com.ahstudio.screeneditor.ports.EffectsPort
import com.ahstudio.screeneditor.ports.KeyableProperty
import com.ahstudio.screeneditor.ports.LayerType
import com.ahstudio.screeneditor.ports.RendererPort
import com.ahstudio.screeneditor.ports.TextPort
import com.ahstudio.screeneditor.transform.ScreenEditorTransformEngine
import com.ahstudio.screeneditor.transform.Transform2D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenEditorCompositionEngine(
    private val layers: ScreenEditorLayerManager,
    private val keyframes: ScreenEditorKeyframeBridge,
    private val effects: EffectsPort,
    private val text: TextPort,
    private val transformEngine: ScreenEditorTransformEngine,
    private val renderer: RendererPort,
    private val scope: CoroutineScope
) {
    private val _frame = MutableStateFlow(CompositionFrame(0L, 0L, emptyList()))
    val frame: StateFlow<CompositionFrame> = _frame.asStateFlow()
    private var dirty = true
    private var version = 0L

    fun onPositionChanged(us: Long) {
        dirty = true
        rebuild(us)
    }

    fun invalidate() {
        dirty = true
        rebuild(_frame.value.timelineUs)
    }

    private fun rebuild(us: Long) {
        if (!dirty) return
        dirty = false
        val resolved = ArrayList<ResolvedLayer>(layers.count())
        for (lv in layers.orderedBackToFront()) {
            if (!lv.visible) continue
            val base = layers.transformOf(lv.id)
            // Sample keyframes at CTI
            val t = Transform2D(
                translationX = keyframes.sample(lv.id, KeyableProperty.POSITION_X, us, base.translationX),
                translationY = keyframes.sample(lv.id, KeyableProperty.POSITION_Y, us, base.translationY),
                scaleX = keyframes.sample(lv.id, KeyableProperty.SCALE_X, us, base.scaleX),
                scaleY = keyframes.sample(lv.id, KeyableProperty.SCALE_Y, us, base.scaleY),
                rotationDeg = keyframes.sample(lv.id, KeyableProperty.ROTATION, us, base.rotationDeg),
                anchorX = base.anchorX,
                anchorY = base.anchorY,
                flipH = base.flipH,
                flipV = base.flipV
            )
            val opacity = keyframes.sample(lv.id, KeyableProperty.OPACITY, us, lv.opacity)
            val m = Matrix()
            val size = layers.sourceSizeOf(lv.id)
            transformEngine.toMatrix(t, size.width, size.height, layers.cropOf(lv.id), m)
            resolved.add(
                ResolvedLayer(
                    id = lv.id,
                    type = lv.type,
                    matrix = m,
                    cropUv = layers.cropUvOf(lv.id),
                    opacity = opacity,
                    blendMode = lv.blendMode,
                    visible = true,
                    effectChain = effects.chainFor(lv.id),
                    textState = if (lv.type == LayerType.TEXT) text.stateFor(lv.id) else null,
                    keyframeFlags = keyframes.trackFlagsFor(lv.id)
                )
            )
        }
        val nextVersion = ++version
        val newFrame = CompositionFrame(nextVersion, us, resolved)
        _frame.value = newFrame
        renderer.submit(newFrame)
    }
}
