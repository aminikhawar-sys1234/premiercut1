package com.ahstudio.screeneditor.bridge

import com.ahstudio.screeneditor.ports.KeyableProperty
import com.ahstudio.screeneditor.ports.KeyframePort
import com.ahstudio.screeneditor.ports.KeyframeView
import com.ahstudio.screeneditor.ports.RemoveKeyframeCommand
import com.ahstudio.screeneditor.ports.SetKeyframeCommand

class ScreenEditorKeyframeBridge(
    private val port: KeyframePort,
    private val undo: ScreenEditorCommandUndoRedoBridge
) {
    fun sample(layerId: String, prop: KeyableProperty, us: Long, fallback: Float): Float =
        port.sample(layerId, prop, us, fallback)

    fun hasTrack(layerId: String, prop: KeyableProperty): Boolean =
        port.hasTrack(layerId, prop)

    fun onPropertyChanged(
        layerId: String,
        prop: KeyableProperty,
        us: Long,
        value: Float,
        autoKeyframe: Boolean
    ) {
        if (port.hasTrack(layerId, prop) || autoKeyframe) {
            undo.execute(SetKeyframeCommand(layerId, prop, us, value))
        }
    }

    fun removeKeyframe(layerId: String, prop: KeyableProperty, us: Long) {
        undo.execute(RemoveKeyframeCommand(layerId, prop, us))
    }

    fun trackFlagsFor(layerId: String): Int {
        var flags = 0
        KeyableProperty.values().forEachIndexed { index, prop ->
            if (port.hasTrack(layerId, prop)) {
                flags = flags or (1 shl index)
            }
        }
        return flags
    }

    fun keys(layerId: String, prop: KeyableProperty): List<KeyframeView> =
        port.keys(layerId, prop)
}
