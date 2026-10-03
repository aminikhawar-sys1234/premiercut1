package com.ahstudio.screeneditor.interaction

import android.graphics.PointF
import com.ahstudio.screeneditor.bridge.ScreenEditorTimelineBridge
import com.ahstudio.screeneditor.core.ScreenEditorController
import com.ahstudio.screeneditor.gesture.EditorRegions
import com.ahstudio.screeneditor.gesture.GestureEvent
import com.ahstudio.screeneditor.gesture.GestureOwner
import com.ahstudio.screeneditor.gesture.ScreenEditorGestureEngine
import com.ahstudio.screeneditor.ports.TimelinePort
import com.ahstudio.screeneditor.transform.CropRect
import com.ahstudio.screeneditor.transform.ScreenEditorTransformEngine
import com.ahstudio.screeneditor.transform.Transform2D
import com.ahstudio.screeneditor.transform.TransformSnapping
import com.ahstudio.screeneditor.viewport.ScreenEditorViewport
import kotlin.math.hypot

class ScreenEditorInteractionEngine(
    private val controller: ScreenEditorController,
    private val viewport: ScreenEditorViewport,
    private val transformEngine: ScreenEditorTransformEngine,
    private val snapping: TransformSnapping,
    private val gestureEngine: ScreenEditorGestureEngine,
    private val timelineBridge: ScreenEditorTimelineBridge,
    private val timelinePort: TimelinePort,
    private val densityPxPerDp: Float,
    private val regionsProvider: () -> EditorRegions = { EditorRegions() }
) {
    private var gestureStart: ScreenEditorTransformEngine.GestureStart? = null
    private var transformOriginal: Transform2D? = null
    private var activeLayerId: String? = null
    private var cornerStartDist = 0f
    private val corners = Array(4) { PointF() }
    private var activeHandle: Gizmo.Handle? = null

    init {
        gestureEngine.onEvent = { event, owner -> handle(event, owner) }
        gestureEngine.objectHitTester = { sx, sy -> classifyObjectTouch(sx, sy) }
    }

    fun classifyObjectTouch(screenX: Float, screenY: Float): GestureOwner {
        val selectedId = controller.state.value.selectedLayerId
        if (selectedId != null && controller.layers.isSelectable(selectedId)) {
            val t = controller.layers.transformOf(selectedId)
            val size = controller.layers.sourceSizeOf(selectedId)
            val crop = controller.layers.cropOf(selectedId)
            transformEngine.boundingBox(t, size.width, size.height, crop, corners)

            // Convert corners to screen space
            val screenCorners = Array(4) { i ->
                PointF(
                    viewport.editorToScreenX(corners[i].x),
                    viewport.editorToScreenY(corners[i].y)
                )
            }

            val handle = Gizmo.hitHandle(screenX, screenY, screenCorners, densityPxPerDp)
            if (handle != null) {
                activeHandle = handle
                return when (handle) {
                    Gizmo.Handle.ROTATE -> GestureOwner.OBJECT_ROTATE
                    Gizmo.Handle.TOP_LEFT, Gizmo.Handle.TOP_RIGHT,
                    Gizmo.Handle.BOTTOM_RIGHT, Gizmo.Handle.BOTTOM_LEFT -> GestureOwner.OBJECT_SCALE_CORNER
                    Gizmo.Handle.TOP_EDGE, Gizmo.Handle.BOTTOM_EDGE,
                    Gizmo.Handle.LEFT_EDGE, Gizmo.Handle.RIGHT_EDGE -> GestureOwner.OBJECT_SCALE_EDGE
                    else -> GestureOwner.OBJECT_MOVE
                }
            }

            val ex = viewport.screenToEditorX(screenX)
            val ey = viewport.screenToEditorY(screenY)
            if (Gizmo.pointInQuad(ex, ey, corners)) {
                activeHandle = Gizmo.Handle.BODY
                return GestureOwner.OBJECT_MOVE
            }
        }

        val ex = viewport.screenToEditorX(screenX)
        val ey = viewport.screenToEditorY(screenY)
        val hitId = Gizmo.hitLayerAt(
            ex,
            ey,
            controller.layers.hitShapes(transformEngine),
            controller.layers::isSelectable
        )
        if (hitId != null) {
            controller.selectLayer(hitId)
            activeHandle = Gizmo.Handle.BODY
            return GestureOwner.OBJECT_MOVE
        }

        return GestureOwner.NONE
    }

    private fun handle(e: GestureEvent, owner: GestureOwner) {
        when (e) {
            is GestureEvent.Tap -> onTap(e)
            is GestureEvent.LongPress -> onLongPress(e)
            is GestureEvent.Pinch -> when (owner) {
                GestureOwner.VIEWPORT -> viewport.zoomAt(e.focusX, e.focusY, e.scale)
                GestureOwner.OBJECT_TWO_FINGER -> applyTwoFingerObject(e)
                else -> {}
            }
            is GestureEvent.Drag -> when (owner) {
                GestureOwner.VIEWPORT -> viewport.panBy(e.dx, e.dy)
                GestureOwner.OBJECT_MOVE -> applyMove(e)
                GestureOwner.OBJECT_SCALE_CORNER -> applyCornerScale(e)
                GestureOwner.OBJECT_ROTATE -> applyRotate(e)
                GestureOwner.TIMELINE_SCRUB -> {}
                GestureOwner.CLIP_DRAG -> {}
                GestureOwner.CLIP_TRIM -> {}
                else -> {}
            }
            GestureEvent.End -> onGestureEnd(owner)
            GestureEvent.Cancel -> onGestureCancel(owner)
        }
    }

    private fun onTap(e: GestureEvent.Tap) {
        val r = regionsProvider()
        if (r.timeline.contains(e.screenX, e.screenY)) {
            val width = r.timeline.width().coerceAtLeast(1f)
            val fraction = ((e.screenX - r.timeline.left) / width).coerceIn(0f, 1f)
            val targetUs = (fraction * timelinePort.durationUs.value).toLong()
            timelineBridge.beginScrub()
            timelineBridge.scrubTo(targetUs)
            timelineBridge.endScrub()
            return
        }

        val ex = viewport.screenToEditorX(e.screenX)
        val ey = viewport.screenToEditorY(e.screenY)
        val hitId = Gizmo.hitLayerAt(
            ex,
            ey,
            controller.layers.hitShapes(transformEngine),
            controller.layers::isSelectable
        )
        controller.selectLayer(hitId)
    }

    private fun onLongPress(e: GestureEvent.LongPress) {
        val ex = viewport.screenToEditorX(e.screenX)
        val ey = viewport.screenToEditorY(e.screenY)
        val hitId = Gizmo.hitLayerAt(
            ex,
            ey,
            controller.layers.hitShapes(transformEngine),
            controller.layers::isSelectable
        )
        if (hitId != null) {
            controller.selectLayer(hitId)
        }
    }

    private fun applyTwoFingerObject(e: GestureEvent.Pinch) {
        ensureGestureStarted(PointF(e.focusX, e.focusY), 100f, 0f)
        val s = gestureStart ?: return
        val id = activeLayerId ?: return

        var t = transformEngine.applyTwoFinger(
            s,
            PointF(e.focusX, e.focusY),
            s.startSpan * e.scale,
            s.startAngleRad + e.rotationDelta,
            0.05f,
            20f
        )
        t = snapping.snap(t, s.srcW, s.srcH, s.crop, controller.state.value.snappingEnabled).corrected
        controller.updateTransformLive(id, t)
    }

    private fun applyMove(e: GestureEvent.Drag) {
        ensureGestureStarted(PointF(0f, 0f), 0f, 0f)
        val s = gestureStart ?: return
        val id = activeLayerId ?: return

        val grabScreenX = viewport.editorToScreenX(s.grabEditor.x) + e.dx
        val grabScreenY = viewport.editorToScreenY(s.grabEditor.y) + e.dy
        var t = transformEngine.applyMove(s, PointF(grabScreenX, grabScreenY))
        t = snapping.snap(t, s.srcW, s.srcH, s.crop, controller.state.value.snappingEnabled).corrected
        controller.updateTransformLive(id, t)
    }

    private fun applyCornerScale(e: GestureEvent.Drag) {
        ensureGestureStarted(PointF(0f, 0f), 0f, 0f)
        val s = gestureStart ?: return
        val id = activeLayerId ?: return

        val primaryScreenX = viewport.editorToScreenX(s.grabEditor.x) + e.dx
        val primaryScreenY = viewport.editorToScreenY(s.grabEditor.y) + e.dy
        val t = transformEngine.applyCornerScale(
            s,
            cornerStartDist,
            PointF(primaryScreenX, primaryScreenY),
            0.05f,
            20f
        )
        controller.updateTransformLive(id, t)
    }

    private fun applyRotate(e: GestureEvent.Drag) {
        ensureGestureStarted(PointF(0f, 0f), 0f, 0f)
        val s = gestureStart ?: return
        val id = activeLayerId ?: return

        val primaryScreenX = viewport.editorToScreenX(s.grabEditor.x) + e.dx
        val primaryScreenY = viewport.editorToScreenY(s.grabEditor.y) + e.dy
        val centerEditor = PointF(s.start.translationX, s.start.translationY)
        val t = transformEngine.applyRotation(
            s,
            centerEditor,
            PointF(primaryScreenX, primaryScreenY)
        )
        controller.updateTransformLive(id, t)
    }

    private fun ensureGestureStarted(primaryScreen: PointF, span: Float, angle: Float) {
        if (gestureStart != null) return
        val id = controller.state.value.selectedLayerId ?: return
        val t = controller.layers.transformOf(id)
        val size = controller.layers.sourceSizeOf(id)
        val crop = controller.layers.cropOf(id)
        transformOriginal = t
        activeLayerId = id
        cornerStartDist = hypot(size.width * crop.width * t.scaleX / 2f, size.height * crop.height * t.scaleY / 2f)
        gestureStart = transformEngine.beginGesture(
            t,
            size.width,
            size.height,
            crop,
            primaryScreen,
            span,
            angle,
            primaryScreen
        )
        controller.beginTransform(id, t)
    }

    private fun onGestureEnd(owner: GestureOwner) {
        val id = activeLayerId
        if (owner in setOf(
                GestureOwner.OBJECT_MOVE,
                GestureOwner.OBJECT_TWO_FINGER,
                GestureOwner.OBJECT_SCALE_CORNER,
                GestureOwner.OBJECT_SCALE_EDGE,
                GestureOwner.OBJECT_ROTATE
            ) && id != null
        ) {
            controller.commitTransform(id, controller.layers.transformOf(id))
        }
        gestureStart = null
        transformOriginal = null
        activeLayerId = null
        activeHandle = null
    }

    private fun onGestureCancel(owner: GestureOwner) {
        val id = activeLayerId
        val orig = transformOriginal
        if (id != null && orig != null) {
            controller.cancelTransform(id, orig)
        }
        gestureStart = null
        transformOriginal = null
        activeLayerId = null
        activeHandle = null
    }
}
