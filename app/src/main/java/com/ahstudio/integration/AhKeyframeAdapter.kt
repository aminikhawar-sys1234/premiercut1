package com.ahstudio.integration

import com.ahstudio.screeneditor.ports.KeyableProperty
import com.ahstudio.screeneditor.ports.KeyframePort
import com.ahstudio.screeneditor.ports.KeyframeView
import com.example.domain.model.ClipKeyframe
import com.example.engine.KeyframeInterpolator
import com.example.engine.TimelineEngine

class AhKeyframeAdapter(
    private val timelineEngine: TimelineEngine
) : KeyframePort {

    override fun hasTrack(layerId: String, prop: KeyableProperty): Boolean {
        val kfs = getKeyframes(layerId)
        return kfs.isNotEmpty()
    }

    override fun keys(layerId: String, prop: KeyableProperty): List<KeyframeView> {
        val kfs = getKeyframes(layerId)
        return kfs.map { kf ->
            val v = when (prop) {
                KeyableProperty.POSITION_X -> kf.posX
                KeyableProperty.POSITION_Y -> kf.posY
                KeyableProperty.SCALE_X -> kf.scaleX
                KeyableProperty.SCALE_Y -> kf.scaleY
                KeyableProperty.ROTATION -> kf.rotation
                KeyableProperty.OPACITY -> kf.opacity
                KeyableProperty.CROP_L -> 0f
                KeyableProperty.CROP_T -> 0f
                KeyableProperty.CROP_R -> 1f
                KeyableProperty.CROP_B -> 1f
            }
            KeyframeView(timeUs = kf.timeMs * 1000L, value = v)
        }
    }

    override fun upsert(layerId: String, prop: KeyableProperty, timeUs: Long, value: Float) {
        val timeMs = timeUs / 1000L
        val kf = ClipKeyframe(timeMs = timeMs)
        timelineEngine.addKeyframeToClip(layerId, kf)
    }

    override fun remove(layerId: String, prop: KeyableProperty, timeUs: Long) {
        val timeMs = timeUs / 1000L
        val kfs = getKeyframes(layerId)
        val target = kfs.firstOrNull { Math.abs(it.timeMs - timeMs) <= 33L }
        if (target != null) {
            timelineEngine.deleteKeyframe(target.id)
        }
    }

    override fun sample(layerId: String, prop: KeyableProperty, timeUs: Long, fallback: Float): Float {
        val timeMs = timeUs / 1000L
        val tl = timelineEngine.timeline.value
        val vid = tl.videoClips.firstOrNull { it.id == layerId }
        if (vid != null) {
            val interp = KeyframeInterpolator.interpolate(vid, timeMs)
            return when (prop) {
                KeyableProperty.POSITION_X -> interp.posX
                KeyableProperty.POSITION_Y -> interp.posY
                KeyableProperty.SCALE_X -> interp.scaleX
                KeyableProperty.SCALE_Y -> interp.scaleY
                KeyableProperty.ROTATION -> interp.rotation
                KeyableProperty.OPACITY -> interp.opacity
                else -> fallback
            }
        }
        val ov = tl.overlayClips.firstOrNull { it.id == layerId }
        if (ov != null) {
            val interp = KeyframeInterpolator.interpolate(ov, timeMs)
            return when (prop) {
                KeyableProperty.POSITION_X -> interp.posX
                KeyableProperty.POSITION_Y -> interp.posY
                KeyableProperty.SCALE_X -> interp.scaleX
                KeyableProperty.SCALE_Y -> interp.scaleY
                KeyableProperty.ROTATION -> interp.rotation
                KeyableProperty.OPACITY -> interp.opacity
                else -> fallback
            }
        }
        val stk = tl.stickerClips.firstOrNull { it.id == layerId }
        if (stk != null) {
            val interp = KeyframeInterpolator.interpolate(stk, timeMs)
            return when (prop) {
                KeyableProperty.POSITION_X -> interp.posX
                KeyableProperty.POSITION_Y -> interp.posY
                KeyableProperty.SCALE_X -> interp.scaleX
                KeyableProperty.SCALE_Y -> interp.scaleY
                KeyableProperty.ROTATION -> interp.rotation
                KeyableProperty.OPACITY -> interp.opacity
                else -> fallback
            }
        }
        return fallback
    }

    private fun getKeyframes(layerId: String): List<ClipKeyframe> {
        val tl = timelineEngine.timeline.value
        tl.videoClips.firstOrNull { it.id == layerId }?.let { return it.keyframes }
        tl.overlayClips.firstOrNull { it.id == layerId }?.let { return it.keyframes }
        tl.textClips.firstOrNull { it.id == layerId }?.let { return it.keyframes }
        tl.stickerClips.firstOrNull { it.id == layerId }?.let { return it.keyframes }
        tl.effectClips.firstOrNull { it.id == layerId }?.let { return it.keyframes }
        return emptyList()
    }
}
