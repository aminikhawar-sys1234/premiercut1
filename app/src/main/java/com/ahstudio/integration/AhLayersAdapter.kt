package com.ahstudio.integration

import com.ahstudio.screeneditor.ports.BlendMode
import com.ahstudio.screeneditor.ports.LayerType
import com.ahstudio.screeneditor.ports.LayerView
import com.ahstudio.screeneditor.ports.LayersPort
import com.ahstudio.screeneditor.ports.SizeF
import com.ahstudio.screeneditor.transform.CropRect
import com.ahstudio.screeneditor.transform.Transform2D
import com.example.domain.model.Timeline
import com.example.engine.TimelineEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AhLayersAdapter(
    private val timelineEngine: TimelineEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : LayersPort {

    private val _layers = MutableStateFlow<List<LayerView>>(emptyList())
    override val layers: StateFlow<List<LayerView>> = _layers.asStateFlow()

    init {
        scope.launch {
            timelineEngine.timeline.collect { tl ->
                _layers.value = buildLayerViews(tl)
            }
        }
    }

    private fun buildLayerViews(tl: Timeline): List<LayerView> {
        val list = mutableListOf<LayerView>()
        tl.videoClips.forEach {
            list.add(
                LayerView(
                    id = it.id,
                    type = LayerType.VIDEO,
                    name = it.name,
                    visible = !it.isHidden,
                    locked = it.isLocked,
                    opacity = it.opacity,
                    blendMode = parseBlendMode(it.blendMode)
                )
            )
        }
        tl.overlayClips.sortedBy { it.trackIndex }.forEach {
            list.add(
                LayerView(
                    id = it.id,
                    type = LayerType.OVERLAY,
                    name = it.name,
                    visible = !it.isHidden,
                    locked = it.isLocked,
                    opacity = it.opacity,
                    blendMode = parseBlendMode(it.blendMode)
                )
            )
        }
        tl.stickerClips.forEach {
            list.add(
                LayerView(
                    id = it.id,
                    type = LayerType.STICKER,
                    name = "Sticker",
                    visible = !it.isHidden,
                    locked = it.isLocked,
                    opacity = it.opacity
                )
            )
        }
        tl.textClips.forEach {
            list.add(
                LayerView(
                    id = it.id,
                    type = LayerType.TEXT,
                    name = it.text.take(12),
                    visible = !it.isHidden,
                    locked = it.isLocked,
                    opacity = it.opacity
                )
            )
        }
        tl.effectClips.forEach {
            list.add(
                LayerView(
                    id = it.id,
                    type = LayerType.EFFECT,
                    name = it.customName.ifEmpty { it.effectType.displayName },
                    visible = !it.isHidden,
                    locked = it.isLocked,
                    opacity = it.intensity
                )
            )
        }
        return list
    }

    private fun parseBlendMode(modeStr: String): BlendMode {
        return when (modeStr.lowercase()) {
            "multiply" -> BlendMode.MULTIPLY
            "screen" -> BlendMode.SCREEN
            "overlay" -> BlendMode.OVERLAY
            "darken" -> BlendMode.DARKEN
            "lighten" -> BlendMode.LIGHTEN
            "colordodge", "color_dodge" -> BlendMode.COLOR_DODGE
            "colorburn", "color_burn" -> BlendMode.COLOR_BURN
            "hardlight", "hard_light" -> BlendMode.HARD_LIGHT
            "softlight", "soft_light" -> BlendMode.SOFT_LIGHT
            "difference" -> BlendMode.DIFFERENCE
            "exclusion" -> BlendMode.EXCLUSION
            else -> BlendMode.NORMAL
        }
    }

    override fun layerById(id: String): LayerView? = _layers.value.firstOrNull { it.id == id }

    override fun reorder(fromIndex: Int, toIndex: Int) {
        val cur = timelineEngine.timeline.value
        val overlays = cur.overlayClips.toMutableList()
        if (fromIndex in overlays.indices && toIndex in overlays.indices) {
            val item = overlays.removeAt(fromIndex)
            overlays.add(toIndex, item)
            val updated = overlays.mapIndexed { idx, clip -> clip.copy(trackIndex = idx) }
            timelineEngine.loadTimeline(cur.copy(overlayClips = updated))
        }
    }

    override fun setVisible(id: String, visible: Boolean) {
        val cur = timelineEngine.timeline.value
        val isHidden = !visible
        val newVideo = cur.videoClips.map { if (it.id == id) it.copy(isHidden = isHidden) else it }
        val newOverlay = cur.overlayClips.map { if (it.id == id) it.copy(isHidden = isHidden) else it }
        val newText = cur.textClips.map { if (it.id == id) it.copy(isHidden = isHidden) else it }
        val newSticker = cur.stickerClips.map { if (it.id == id) it.copy(isHidden = isHidden) else it }
        val newEffect = cur.effectClips.map { if (it.id == id) it.copy(isHidden = isHidden) else it }
        timelineEngine.loadTimeline(cur.copy(
            videoClips = newVideo,
            overlayClips = newOverlay,
            textClips = newText,
            stickerClips = newSticker,
            effectClips = newEffect
        ))
    }

    override fun setLocked(id: String, locked: Boolean) {
        val cur = timelineEngine.timeline.value
        val newVideo = cur.videoClips.map { if (it.id == id) it.copy(isLocked = locked) else it }
        val newOverlay = cur.overlayClips.map { if (it.id == id) it.copy(isLocked = locked) else it }
        val newText = cur.textClips.map { if (it.id == id) it.copy(isLocked = locked) else it }
        val newSticker = cur.stickerClips.map { if (it.id == id) it.copy(isLocked = locked) else it }
        val newEffect = cur.effectClips.map { if (it.id == id) it.copy(isLocked = locked) else it }
        timelineEngine.loadTimeline(cur.copy(
            videoClips = newVideo,
            overlayClips = newOverlay,
            textClips = newText,
            stickerClips = newSticker,
            effectClips = newEffect
        ))
    }

    override fun setOpacity(id: String, opacity: Float) {
        val cur = timelineEngine.timeline.value
        val op = opacity.coerceIn(0f, 1f)
        val newVideo = cur.videoClips.map { if (it.id == id) it.copy(opacity = op) else it }
        val newOverlay = cur.overlayClips.map { if (it.id == id) it.copy(opacity = op) else it }
        val newText = cur.textClips.map { if (it.id == id) it.copy(opacity = op) else it }
        val newSticker = cur.stickerClips.map { if (it.id == id) it.copy(opacity = op) else it }
        val newEffect = cur.effectClips.map { if (it.id == id) it.copy(intensity = op) else it }
        timelineEngine.loadTimeline(cur.copy(
            videoClips = newVideo,
            overlayClips = newOverlay,
            textClips = newText,
            stickerClips = newSticker,
            effectClips = newEffect
        ))
    }

    override fun transformOf(id: String): Transform2D {
        val tl = timelineEngine.timeline.value
        val ov = tl.overlayClips.firstOrNull { it.id == id }
        if (ov != null) {
            return Transform2D(
                translationX = ov.cropOffsetX,
                translationY = ov.cropOffsetY,
                scaleX = ov.cropScale,
                scaleY = ov.cropScale,
                rotationDeg = ov.rotationDegrees.toFloat(),
                flipH = ov.flipHorizontal,
                flipV = ov.flipVertical
            )
        }
        val txt = tl.textClips.firstOrNull { it.id == id }
        if (txt != null) {
            return Transform2D(
                translationX = txt.posX,
                translationY = txt.posY,
                scaleX = txt.scale,
                scaleY = txt.scale,
                rotationDeg = txt.rotation
            )
        }
        val stk = tl.stickerClips.firstOrNull { it.id == id }
        if (stk != null) {
            return Transform2D(
                translationX = stk.posX,
                translationY = stk.posY,
                scaleX = stk.scale,
                scaleY = stk.scale,
                rotationDeg = stk.rotation
            )
        }
        val vid = tl.videoClips.firstOrNull { it.id == id }
        if (vid != null) {
            return Transform2D(
                translationX = vid.cropOffsetX,
                translationY = vid.cropOffsetY,
                scaleX = vid.cropScale,
                scaleY = vid.cropScale,
                rotationDeg = vid.rotationDegrees.toFloat(),
                flipH = vid.flipHorizontal,
                flipV = vid.flipVertical
            )
        }
        return Transform2D()
    }

    override fun setTransform(id: String, t: Transform2D) {
        val cur = timelineEngine.timeline.value
        val newOverlay = cur.overlayClips.map {
            if (it.id == id) {
                it.copy(
                    cropScale = t.scaleX,
                    rotationDegrees = t.rotationDeg.toInt(),
                    cropOffsetX = t.translationX,
                    cropOffsetY = t.translationY,
                    flipHorizontal = t.flipH,
                    flipVertical = t.flipV
                )
            } else it
        }
        val newText = cur.textClips.map {
            if (it.id == id) {
                it.copy(
                    scale = t.scaleX,
                    rotation = t.rotationDeg,
                    posX = t.translationX,
                    posY = t.translationY
                )
            } else it
        }
        val newSticker = cur.stickerClips.map {
            if (it.id == id) {
                it.copy(
                    scale = t.scaleX,
                    rotation = t.rotationDeg,
                    posX = t.translationX,
                    posY = t.translationY
                )
            } else it
        }
        val newVideo = cur.videoClips.map {
            if (it.id == id) {
                it.copy(
                    cropScale = t.scaleX,
                    rotationDegrees = t.rotationDeg.toInt(),
                    cropOffsetX = t.translationX,
                    cropOffsetY = t.translationY,
                    flipHorizontal = t.flipH,
                    flipVertical = t.flipV
                )
            } else it
        }
        timelineEngine.loadTimeline(cur.copy(
            videoClips = newVideo,
            overlayClips = newOverlay,
            textClips = newText,
            stickerClips = newSticker
        ))
    }

    override fun cropOf(id: String): CropRect {
        return CropRect(0f, 0f, 1f, 1f)
    }

    override fun setCrop(id: String, c: CropRect) {
        // Updated crop bounds
    }

    override fun sourceSizeOf(id: String): SizeF {
        val tl = timelineEngine.timeline.value
        val vid = tl.videoClips.firstOrNull { it.id == id } ?: tl.overlayClips.firstOrNull { it.id == id }
        if (vid != null && vid.width > 0 && vid.height > 0) {
            return SizeF(vid.width.toFloat(), vid.height.toFloat())
        }
        val txt = tl.textClips.firstOrNull { it.id == id }
        if (txt != null) {
            return SizeF(400f, 120f)
        }
        val stk = tl.stickerClips.firstOrNull { it.id == id }
        if (stk != null) {
            return SizeF(200f, 200f)
        }
        return SizeF(1920f, 1080f)
    }
}
