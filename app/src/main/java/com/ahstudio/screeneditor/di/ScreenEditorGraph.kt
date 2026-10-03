package com.ahstudio.screeneditor.di

import com.ahstudio.screeneditor.bridge.ScreenEditorCommandUndoRedoBridge
import com.ahstudio.screeneditor.bridge.ScreenEditorKeyframeBridge
import com.ahstudio.screeneditor.bridge.ScreenEditorTimelineBridge
import com.ahstudio.screeneditor.composition.ScreenEditorCompositionEngine
import com.ahstudio.screeneditor.core.ScreenEditorController
import com.ahstudio.screeneditor.layers.ScreenEditorLayerManager
import com.ahstudio.screeneditor.ports.CommandPort
import com.ahstudio.screeneditor.ports.EffectsPort
import com.ahstudio.screeneditor.ports.KeyframePort
import com.ahstudio.screeneditor.ports.LayersPort
import com.ahstudio.screeneditor.ports.PlaybackPort
import com.ahstudio.screeneditor.ports.RendererPort
import com.ahstudio.screeneditor.ports.TextPort
import com.ahstudio.screeneditor.ports.TimelinePort
import com.ahstudio.screeneditor.transform.ScreenEditorTransformEngine
import com.ahstudio.screeneditor.viewport.ScreenEditorViewport
import kotlinx.coroutines.CoroutineScope

object ScreenEditorGraph {
    @Volatile
    private var instance: ScreenEditorController? = null

    fun get(scope: CoroutineScope, ports: Ports): ScreenEditorController {
        return instance ?: synchronized(this) {
            instance ?: create(scope, ports).also { instance = it }
        }
    }

    fun reset() {
        synchronized(this) {
            instance = null
        }
    }

    fun create(scope: CoroutineScope, p: Ports): ScreenEditorController {
        // 1) Pure-Kotlin engines first - no GPU, no native, no surface dependency:
        val viewport = ScreenEditorViewport()
        val timeline = ScreenEditorTimelineBridge(p.timeline, p.playback)
        val layers = ScreenEditorLayerManager(p.layers)
        val undo = ScreenEditorCommandUndoRedoBridge(p.commands)
        val keyframes = ScreenEditorKeyframeBridge(p.keyframes, undo)
        val txEngine = ScreenEditorTransformEngine(viewport)
        val composition = ScreenEditorCompositionEngine(
            layers = layers,
            keyframes = keyframes,
            effects = p.effects,
            text = p.text,
            transformEngine = txEngine,
            renderer = p.renderer,
            scope = scope
        )
        val controller = ScreenEditorController(
            viewport = viewport,
            timelineBridge = timeline,
            layers = layers,
            keyframes = keyframes,
            composition = composition,
            undo = undo,
            timelinePort = p.timeline,
            scope = scope
        )
        controller.initialize(p.playback)
        return controller
    }

    data class Ports(
        val timeline: TimelinePort,
        val playback: PlaybackPort,
        val layers: LayersPort,
        val commands: CommandPort,
        val keyframes: KeyframePort,
        val effects: EffectsPort,
        val text: TextPort,
        val renderer: RendererPort
    )
}
