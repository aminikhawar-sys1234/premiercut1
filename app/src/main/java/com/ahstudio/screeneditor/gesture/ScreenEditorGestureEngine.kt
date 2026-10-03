package com.ahstudio.screeneditor.gesture

import android.graphics.PointF
import android.os.SystemClock
import com.ahstudio.screeneditor.viewport.ScreenEditorViewport
import kotlin.math.abs
import kotlin.math.hypot

class ScreenEditorGestureEngine(
    private val viewport: ScreenEditorViewport,
    private val regions: () -> EditorRegions,
    private val timelineHitTester: TimelineHitTester,
    private val config: Config = Config()
) {
    data class Config(
        val touchSlopPx: Float = 16f,
        val tapTimeoutMs: Long = 220L,
        val longPressTimeoutMs: Long = 450L,
        val minScaleSpanDelta: Float = 4f,     // px - ignore micro span jitter
        val minRotationDeltaRad: Float = 0.03f
    )

    var owner: GestureOwner = GestureOwner.NONE
        private set

    private var pending: GestureOwner = GestureOwner.NONE
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isDecided = false
    private val lastCentroid = PointF()
    private var lastSpan = 0f
    private var lastAngle = 0f
    private var longPressFired = false
    private var hasObjectCandidate = false

    /** Callbacks wired by host / interaction engine. */
    var onEvent: (GestureEvent, GestureOwner) -> Unit = { _, _ -> }
    var objectHitTester: ((screenX: Float, screenY: Float) -> GestureOwner)? = null

    fun onTouch(snapshot: PointerSnapshot): GestureOwner {
        val r = regions()
        if (snapshot.count == 0) {
            release()
            return owner
        }

        if (snapshot.count == 1 && owner == GestureOwner.NONE && pending == GestureOwner.NONE) {
            onFirstDown(snapshot, r)
        }

        when (snapshot.count) {
            1 -> onSingleMove(snapshot, r)
            2 -> onTwoFinger(snapshot, r)
            else -> onThreePlus(snapshot, r)
        }
        return owner
    }

    fun onPointerUp(snapshot: PointerSnapshot) {
        if (snapshot.count == 0) {
            val elapsed = SystemClock.uptimeMillis() - downTime
            if (!isDecided && !longPressFired && elapsed < config.tapTimeoutMs) {
                onEvent(GestureEvent.Tap(downX, downY), GestureOwner.NONE)
            }
            if (owner != GestureOwner.NONE) {
                onEvent(GestureEvent.End, owner)
            }
            release()
            return
        }

        if (snapshot.count == 1 && (owner == GestureOwner.OBJECT_TWO_FINGER || owner == GestureOwner.VIEWPORT)) {
            lastCentroid.set(snapshot.xs[0], snapshot.ys[0])
        }
    }

    fun cancel() {
        if (owner != GestureOwner.NONE) {
            onEvent(GestureEvent.Cancel, owner)
        }
        release()
    }

    private fun onFirstDown(s: PointerSnapshot, r: EditorRegions) {
        downX = s.xs[0]
        downY = s.ys[0]
        downTime = s.eventTimeMs
        longPressFired = false
        isDecided = false
        pending = classify(downX, downY, r)
    }

    private fun classify(x: Float, y: Float, r: EditorRegions): GestureOwner {
        if (r.timeline.contains(x, y)) {
            return when (timelineHitTester.hit(x, y, r.timeline)) {
                is TimelineHitTester.Zone.Playhead -> GestureOwner.TIMELINE_SCRUB
                is TimelineHitTester.Zone.ClipEdge -> GestureOwner.CLIP_TRIM
                is TimelineHitTester.Zone.ClipBody -> GestureOwner.CLIP_DRAG
                TimelineHitTester.Zone.Empty -> GestureOwner.TIMELINE_SCRUB
            }
        }
        val objOwner = objectHitTester?.invoke(x, y)
        if (objOwner != null && objOwner != GestureOwner.NONE) {
            hasObjectCandidate = true
            return objOwner
        }
        hasObjectCandidate = false
        return GestureOwner.VIEWPORT
    }

    private fun onSingleMove(s: PointerSnapshot, r: EditorRegions) {
        val dx = s.xs[0] - downX
        val dy = s.ys[0] - downY
        val dist = hypot(dx, dy)

        if (!isDecided && dist > config.touchSlopPx) {
            owner = if (pending != GestureOwner.NONE) pending else GestureOwner.VIEWPORT
            isDecided = true
            lastCentroid.set(s.xs[0], s.ys[0])
            lastSpan = s.span()
            lastAngle = s.angleRad()
        }

        if (isDecided) {
            val deltaX = s.xs[0] - lastCentroid.x
            val deltaY = s.ys[0] - lastCentroid.y
            lastCentroid.set(s.xs[0], s.ys[0])
            onEvent(GestureEvent.Drag(deltaX, deltaY), owner)
        }
    }

    private fun onTwoFinger(s: PointerSnapshot, r: EditorRegions) {
        if (!isDecided) {
            owner = when (pending) {
                GestureOwner.CLIP_DRAG, GestureOwner.CLIP_TRIM, GestureOwner.TIMELINE_SCRUB -> pending
                GestureOwner.OBJECT_MOVE, GestureOwner.OBJECT_SCALE_CORNER,
                GestureOwner.OBJECT_SCALE_EDGE, GestureOwner.OBJECT_ROTATE -> GestureOwner.OBJECT_TWO_FINGER
                else -> GestureOwner.VIEWPORT
            }
            isDecided = true
            lastCentroid.set(s.centroid())
            lastSpan = s.span()
            lastAngle = s.angleRad()
            return
        }

        if (owner == GestureOwner.VIEWPORT) {
            val c = s.centroid()
            val spanDelta = s.span() - lastSpan
            val rotDelta = normalizeAngle(s.angleRad() - lastAngle)
            if (abs(spanDelta) >= config.minScaleSpanDelta || abs(rotDelta) >= config.minRotationDeltaRad) {
                val factor = if (lastSpan > 0f) s.span() / lastSpan else 1f
                onEvent(GestureEvent.Pinch(c.x, c.y, factor, rotDelta), owner)
                lastSpan = s.span()
                lastAngle = s.angleRad()
            }
            onEvent(GestureEvent.Drag(c.x - lastCentroid.x, c.y - lastCentroid.y), owner)
            lastCentroid.set(c)
        } else if (owner == GestureOwner.OBJECT_TWO_FINGER) {
            val c = s.centroid()
            val factor = if (lastSpan > 0f) s.span() / lastSpan else 1f
            val rotDelta = normalizeAngle(s.angleRad() - lastAngle)
            onEvent(GestureEvent.Pinch(c.x, c.y, factor, rotDelta), owner)
            lastCentroid.set(c)
            lastSpan = s.span()
            lastAngle = s.angleRad()
        }
    }

    private fun onThreePlus(s: PointerSnapshot, r: EditorRegions) {
        if (owner == GestureOwner.NONE || owner == GestureOwner.VIEWPORT) {
            owner = GestureOwner.VIEWPORT
            isDecided = true
            val c = s.centroid()
            onEvent(GestureEvent.Drag(c.x - lastCentroid.x, c.y - lastCentroid.y), owner)
            lastCentroid.set(c)
        }
    }

    private fun release() {
        owner = GestureOwner.NONE
        pending = GestureOwner.NONE
        isDecided = false
        longPressFired = false
        hasObjectCandidate = false
    }

    private fun normalizeAngle(angleRad: Float): Float {
        var a = angleRad
        while (a > Math.PI) a -= (2 * Math.PI).toFloat()
        while (a < -Math.PI) a += (2 * Math.PI).toFloat()
        return a
    }
}
