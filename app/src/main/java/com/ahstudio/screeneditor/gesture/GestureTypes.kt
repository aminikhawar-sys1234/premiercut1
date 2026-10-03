package com.ahstudio.screeneditor.gesture

import android.graphics.RectF
import com.ahstudio.screeneditor.ports.TrimEdge

enum class GestureOwner {
    NONE,
    VIEWPORT,
    OBJECT_MOVE,
    OBJECT_SCALE_CORNER,
    OBJECT_SCALE_EDGE,
    OBJECT_ROTATE,
    OBJECT_TWO_FINGER,
    TIMELINE_SCRUB,
    CLIP_DRAG,
    CLIP_TRIM
}

sealed interface GestureEvent {
    data class Tap(val screenX: Float, val screenY: Float) : GestureEvent
    data class LongPress(val screenX: Float, val screenY: Float) : GestureEvent
    data class Drag(val dx: Float, val dy: Float) : GestureEvent
    data class Pinch(val focusX: Float, val focusY: Float, val scale: Float, val rotationDelta: Float) : GestureEvent
    data object End : GestureEvent
    data object Cancel : GestureEvent
}

/** Host supplies screen regions so the gesture engine never depends on Compose/View internals. */
class EditorRegions(
    val canvas: RectF = RectF(),
    val timeline: RectF = RectF()
)

interface TimelineHitTester {
    sealed interface Zone {
        data object Playhead : Zone
        data class ClipBody(val clipId: String) : Zone
        data class ClipEdge(val clipId: String, val edge: TrimEdge) : Zone
        data object Empty : Zone
    }
    fun hit(screenX: Float, screenY: Float, timelineRect: RectF): Zone
}
