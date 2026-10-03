package com.ahstudio.screeneditor.bridge

import com.ahstudio.screeneditor.ports.PlaybackPort
import com.ahstudio.screeneditor.ports.TimelinePort
import com.ahstudio.screeneditor.ports.TrimEdge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ScreenEditorTimelineBridge(
    private val timeline: TimelinePort,
    private val playback: PlaybackPort
) {
    private val _isScrubbing = MutableStateFlow(false)
    val isScrubbing: StateFlow<Boolean> = _isScrubbing.asStateFlow()

    @Volatile
    private var lastCommandedUs = -1L

    /**
     * THE position flow the whole Screen Editor observes.
     * While scrubbing, only values we commanded pass through -> playhead can NEVER
     * jump back to a stale engine position (bounce-back suppression).
     */
    val observedPositionUs: Flow<Long> =
        combine(timeline.positionUs, _isScrubbing) { pos, scrubbing ->
            if (scrubbing) {
                if (pos == lastCommandedUs) pos else lastCommandedUs.coerceAtLeast(0L)
            } else {
                pos
            }
        }.distinctUntilChanged()

    /** Timeline touch began -> immediate pause, then scrub follows the finger. */
    fun beginScrub() {
        if (_isScrubbing.value) return
        playback.pause() // pause FIRST - before any seek
        lastCommandedUs = timeline.positionUs.value
        _isScrubbing.value = true
    }

    /** Finger move. Coalesced by caller; value snapped if desired. */
    fun scrubTo(us: Long) {
        if (!_isScrubbing.value) return
        val maxDuration = timeline.durationUs.value.coerceAtLeast(0L)
        val target = us.coerceIn(0L, if (maxDuration > 0L) maxDuration else Long.MAX_VALUE)
        lastCommandedUs = target
        playback.seekTo(target) // preview follows CTI; playback stays paused
    }

    /** Finger lifted -> CTI STAYS at final touched position. Nothing restores the old position. */
    fun endScrub() {
        if (!_isScrubbing.value) return
        val finalPos = lastCommandedUs
        if (finalPos >= 0L) {
            playback.seekTo(finalPos) // settle final frame
        }
        _isScrubbing.value = false // re-open normal position observation
    }

    // ---- Clip interactions: all mutation routes through the existing NLE pipeline ----
    fun moveClip(clipId: String, newStartUs: Long, targetTrackId: String? = null): Boolean {
        return timeline.moveClip(clipId, timeline.snapTimeUs(newStartUs), targetTrackId)
    }

    fun trimClip(clipId: String, edge: TrimEdge, newBoundaryUs: Long): Boolean {
        return timeline.trimClip(clipId, edge, timeline.snapTimeUs(newBoundaryUs))
    }
}
