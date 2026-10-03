package com.ahstudio.screeneditor.ports

import com.ahstudio.screeneditor.transform.CropRect
import com.ahstudio.screeneditor.transform.Transform2D
import kotlinx.coroutines.flow.StateFlow

enum class LayerType {
    VIDEO, IMAGE, TEXT, OVERLAY, SHAPE, EFFECT, STICKER
}

data class LayerView(
    val id: String,
    val type: LayerType,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL
)

interface LayersPort {
    val layers: StateFlow<List<LayerView>>
    fun layerById(id: String): LayerView?
    fun reorder(fromIndex: Int, toIndex: Int)
    fun setVisible(id: String, visible: Boolean)
    fun setLocked(id: String, locked: Boolean)
    fun setOpacity(id: String, opacity: Float)
    fun transformOf(id: String): Transform2D
    fun setTransform(id: String, t: Transform2D)
    fun cropOf(id: String): CropRect
    fun setCrop(id: String, c: CropRect)
    fun sourceSizeOf(id: String): SizeF
}
