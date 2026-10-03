package com.ahstudio.integration

import com.ahstudio.screeneditor.ports.ClipView
import com.ahstudio.screeneditor.ports.TimelinePort
import com.ahstudio.screeneditor.ports.TrackKind
import com.ahstudio.screeneditor.ports.TrackView
import com.ahstudio.screeneditor.ports.TrimEdge
import com.example.engine.TimelineEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AhTimelineAdapter(
    private val timelineEngine: TimelineEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : TimelinePort {

    private val _positionUs = MutableStateFlow(timelineEngine.currentPositionMs.value * 1000L)
    override val positionUs: StateFlow<Long> = _positionUs.asStateFlow()

    private val _durationUs = MutableStateFlow(timelineEngine.timeline.value.totalDurationMs * 1000L)
    override val durationUs: StateFlow<Long> = _durationUs.asStateFlow()

    init {
        scope.launch {
            timelineEngine.currentPositionMs.collect { posMs ->
                _positionUs.value = posMs * 1000L
            }
        }
        scope.launch {
            timelineEngine.timeline.collect { tl ->
                _durationUs.value = tl.totalDurationMs * 1000L
            }
        }
    }

    override fun clipsAt(us: Long): List<ClipView> {
        val ms = us / 1000L
        val tl = timelineEngine.timeline.value
        val result = mutableListOf<ClipView>()

        tl.videoClips.filter { ms in it.timelineStartMs..(it.timelineStartMs + it.durationMs) }.forEach {
            result.add(
                ClipView(
                    id = it.id,
                    trackId = "track_main_video",
                    layerId = it.id,
                    startUs = it.timelineStartMs * 1000L,
                    durationUs = it.durationMs * 1000L,
                    locked = it.isLocked,
                    enabled = !it.isHidden
                )
            )
        }

        tl.overlayClips.filter { ms in it.timelineStartMs..(it.timelineStartMs + it.durationMs) }.forEach {
            result.add(
                ClipView(
                    id = it.id,
                    trackId = "track_overlay_${it.trackIndex}",
                    layerId = it.id,
                    startUs = it.timelineStartMs * 1000L,
                    durationUs = it.durationMs * 1000L,
                    locked = it.isLocked,
                    enabled = !it.isHidden
                )
            )
        }

        tl.textClips.filter { ms in it.timelineStartMs..(it.timelineStartMs + it.durationMs) }.forEach {
            result.add(
                ClipView(
                    id = it.id,
                    trackId = "track_text",
                    layerId = it.id,
                    startUs = it.timelineStartMs * 1000L,
                    durationUs = it.durationMs * 1000L,
                    locked = it.isLocked,
                    enabled = !it.isHidden
                )
            )
        }

        tl.stickerClips.filter { ms in it.timelineStartMs..(it.timelineStartMs + it.durationMs) }.forEach {
            result.add(
                ClipView(
                    id = it.id,
                    trackId = "track_sticker",
                    layerId = it.id,
                    startUs = it.timelineStartMs * 1000L,
                    durationUs = it.durationMs * 1000L,
                    locked = it.isLocked,
                    enabled = !it.isHidden
                )
            )
        }

        tl.effectClips.filter { ms in it.timelineStartMs..(it.timelineStartMs + it.durationMs) }.forEach {
            result.add(
                ClipView(
                    id = it.id,
                    trackId = "track_effect",
                    layerId = it.id,
                    startUs = it.timelineStartMs * 1000L,
                    durationUs = it.durationMs * 1000L,
                    locked = it.isLocked,
                    enabled = !it.isHidden
                )
            )
        }

        return result
    }

    override fun trackViews(): List<TrackView> {
        val tl = timelineEngine.timeline.value
        val list = mutableListOf<TrackView>()
        tl.trackSettings.forEach { (type, setting) ->
            val kind = when (type) {
                com.example.domain.model.TrackType.MAIN_VIDEO -> TrackKind.VIDEO
                com.example.domain.model.TrackType.OVERLAY, com.example.domain.model.TrackType.ELEMENT, com.example.domain.model.TrackType.ADJUSTMENT -> TrackKind.OVERLAY
                com.example.domain.model.TrackType.AUDIO, com.example.domain.model.TrackType.MUSIC, com.example.domain.model.TrackType.SFX -> TrackKind.AUDIO
                com.example.domain.model.TrackType.TEXT, com.example.domain.model.TrackType.CAPTION -> TrackKind.TEXT
                com.example.domain.model.TrackType.EFFECT -> TrackKind.EFFECT
                com.example.domain.model.TrackType.STICKER -> TrackKind.STICKER
                com.example.domain.model.TrackType.SHAPE -> TrackKind.OVERLAY
            }
            list.add(
                TrackView(
                    id = type.name,
                    kind = kind,
                    locked = setting.isLocked,
                    muted = setting.isMuted
                )
            )
        }
        return list
    }

    override fun clipById(clipId: String): ClipView? {
        val tl = timelineEngine.timeline.value
        tl.videoClips.firstOrNull { it.id == clipId }?.let {
            return ClipView(it.id, "track_main_video", it.id, it.timelineStartMs * 1000L, it.durationMs * 1000L, it.isLocked, !it.isHidden)
        }
        tl.overlayClips.firstOrNull { it.id == clipId }?.let {
            return ClipView(it.id, "track_overlay_${it.trackIndex}", it.id, it.timelineStartMs * 1000L, it.durationMs * 1000L, it.isLocked, !it.isHidden)
        }
        tl.textClips.firstOrNull { it.id == clipId }?.let {
            return ClipView(it.id, "track_text", it.id, it.timelineStartMs * 1000L, it.durationMs * 1000L, it.isLocked, !it.isHidden)
        }
        tl.stickerClips.firstOrNull { it.id == clipId }?.let {
            return ClipView(it.id, "track_sticker", it.id, it.timelineStartMs * 1000L, it.durationMs * 1000L, it.isLocked, !it.isHidden)
        }
        tl.effectClips.firstOrNull { it.id == clipId }?.let {
            return ClipView(it.id, "track_effect", it.id, it.timelineStartMs * 1000L, it.durationMs * 1000L, it.isLocked, !it.isHidden)
        }
        return null
    }

    override fun moveClip(clipId: String, newStartUs: Long, targetTrackId: String?): Boolean {
        val newStartMs = newStartUs / 1000L
        return try {
            timelineEngine.moveClip(clipId, newStartMs, snap = false)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun trimClip(clipId: String, edge: TrimEdge, newBoundaryUs: Long): Boolean {
        val boundaryMs = newBoundaryUs / 1000L
        val clip = clipById(clipId) ?: return false
        val startMs = clip.startUs / 1000L
        return try {
            when (edge) {
                TrimEdge.START -> timelineEngine.trimClipLeft(clipId, boundaryMs, snap = false)
                TrimEdge.END -> timelineEngine.trimClipRight(clipId, (boundaryMs - startMs).coerceAtLeast(100L), snap = false)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun snapTimeUs(us: Long): Long {
        val ms = us / 1000L
        val snappedMs = timelineEngine.calculateSnap(ms).snappedPosMs
        return snappedMs * 1000L
    }
}
