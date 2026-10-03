package com.ahstudio.screeneditor.layers

import android.graphics.PointF
import com.ahstudio.screeneditor.ports.LayerView
import com.ahstudio.screeneditor.ports.LayersPort
import com.ahstudio.screeneditor.ports.SizeF
import com.ahstudio.screeneditor.transform.CropRect
import com.ahstudio.screeneditor.transform.ScreenEditorTransformEngine
import com.ahstudio.screeneditor.transform.Transform2D
import kotlinx.coroutines.flow.StateFlow

class ScreenEditorLayerManager(private val port: LayersPort) {
    val layers: StateFlow<List<LayerView>> = port.layers

    fun count(): Int = port.layers.value.size

    fun orderedBackToFront(): List<LayerView> = port.layers.value

    fun transformOf(id: String): Transform2D = port.transformOf(id)

    fun setTransformLive(id: String, t: Transform2D) {
        port.setTransform(id, t)
    }

    fun setTransform(id: String, t: Transform2D) {
        port.setTransform(id, t)
    }

    fun cropOf(id: String): CropRect = port.cropOf(id)

    fun setCrop(id: String, c: CropRect) {
        port.setCrop(id, c)
    }

    fun cropUvOf(id: String): FloatArray = port.cropOf(id).let { floatArrayOf(it.l, it.t, it.r, it.b) }

    fun sourceSizeOf(id: String): SizeF = port.sourceSizeOf(id)

    fun setVisible(id: String, v: Boolean) {
        port.setVisible(id, v)
    }

    fun setLocked(id: String, v: Boolean) {
        port.setLocked(id, v)
    }

    fun setOpacity(id: String, o: Float) {
        port.setOpacity(id, o)
    }

    fun reorder(from: Int, to: Int) {
        port.reorder(from, to)
    }

    fun isSelectable(id: String): Boolean =
        port.layerById(id)?.let { it.visible && !it.locked } ?: false

    fun hitShapes(transformEngine: ScreenEditorTransformEngine): List<Pair<String, Array<PointF>>> {
        val list = mutableListOf<Pair<String, Array<PointF>>>()
        for (layer in orderedBackToFront()) {
            if (!layer.visible) continue
            val corners = Array(4) { PointF() }
            val t = transformOf(layer.id)
            val size = sourceSizeOf(layer.id)
            val crop = cropOf(layer.id)
            transformEngine.boundingBox(t, size.width, size.height, crop, corners)
            list.add(layer.id to corners)
        }
        return list
    }
}
