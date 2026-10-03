package com.ahstudio.screeneditor.transform

import android.graphics.Matrix
import android.graphics.PointF
import com.ahstudio.screeneditor.viewport.ScreenEditorViewport
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ScreenEditorTransformEngine(private val viewport: ScreenEditorViewport) {

    fun toMatrix(t: Transform2D, srcW: Float, srcH: Float, crop: CropRect, out: Matrix): Matrix {
        // Effective (cropped) source space:
        val ew = (srcW * crop.width).coerceAtLeast(1f)
        val eh = (srcH * crop.height).coerceAtLeast(1f)
        val pivotX = t.anchorX * ew
        val pivotY = t.anchorY * eh

        out.reset()
        out.postTranslate(t.translationX, t.translationY) // anchor lands at translation
        out.postRotate(t.rotationDeg)
        val sx = t.scaleX * (if (t.flipH) -1f else 1f)
        val sy = t.scaleY * (if (t.flipV) -1f else 1f)
        out.preScale(sx / ew, sy / eh) // normalize source -> unit -> scaled
        out.preTranslate(-pivotX, -pivotY)
        return out
    }

    /** Transformed bounding box corners in editor space (for gizmos + snapping + hit test). */
    fun boundingBox(t: Transform2D, srcW: Float, srcH: Float, crop: CropRect, outCorners: Array<PointF>) {
        val ew = (srcW * crop.width).coerceAtLeast(1f)
        val eh = (srcH * crop.height).coerceAtLeast(1f)
        val m = Matrix()
        toMatrix(t, srcW, srcH, crop, m)
        val pts = floatArrayOf(
            0f, 0f,
            ew, 0f,
            ew, eh,
            0f, eh
        )
        m.mapPoints(pts)
        for (i in 0..3) {
            outCorners[i].set(pts[i * 2], pts[i * 2 + 1])
        }
    }

    // ---- Gesture application (exact center-tracking for two-finger) ----
    class GestureStart(
        val start: Transform2D,
        val srcW: Float,
        val srcH: Float,
        val crop: CropRect,
        val startCentroidEditor: PointF,
        val startSpan: Float,
        val startAngleRad: Float,
        val grabEditor: PointF
    )

    fun beginGesture(
        t: Transform2D,
        srcW: Float,
        srcH: Float,
        crop: CropRect,
        centroidScreen: PointF,
        span: Float,
        angleRad: Float,
        primaryScreen: PointF
    ): GestureStart {
        val ce = PointF(viewport.screenToEditorX(centroidScreen.x), viewport.screenToEditorY(centroidScreen.y))
        val ge = PointF(viewport.screenToEditorX(primaryScreen.x), viewport.screenToEditorY(primaryScreen.y))
        return GestureStart(t, srcW, srcH, crop, ce, span, angleRad, ge)
    }

    /** Two-finger: scale + rotate + move with the fingers' centroid locked to the object's center. */
    fun applyTwoFinger(
        s: GestureStart,
        centroidScreen: PointF,
        spanNow: Float,
        angleNowRad: Float,
        minScale: Float,
        maxScale: Float
    ): Transform2D {
        val t0 = s.start
        val ew = (s.srcW * s.crop.width).coerceAtLeast(1f)
        val eh = (s.srcH * s.crop.height).coerceAtLeast(1f)
        val pivot = PointF(t0.anchorX * ew, t0.anchorY * eh)
        // Source-space center offset from pivot:
        val v0x = (ew / 2f - pivot.x) * t0.scaleX
        val v0y = (eh / 2f - pivot.y) * t0.scaleY

        val rotDelta = Math.toDegrees((angleNowRad - s.startAngleRad).toDouble()).toFloat()
        val k = if (s.startSpan > 0f) (spanNow / s.startSpan).coerceIn(minScale / t0.scaleX.coerceAtLeast(0.001f), maxScale / t0.scaleX.coerceAtLeast(0.001f)) else 1f

        val newRot = t0.rotationDeg + rotDelta
        val newSx = (t0.scaleX * k).coerceIn(minScale, maxScale)
        val newSy = (t0.scaleY * k).coerceIn(minScale, maxScale)

        val ce = PointF(viewport.screenToEditorX(centroidScreen.x), viewport.screenToEditorY(centroidScreen.y))
        val rad = Math.toRadians(newRot.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()

        // t' = centroid - R(θ')·S(s')·v̂0
        val scaleRatioX = if (t0.scaleX != 0f) newSx / t0.scaleX else 1f
        val scaleRatioY = if (t0.scaleY != 0f) newSy / t0.scaleY else 1f
        val tx = ce.x - (v0x * scaleRatioX * cos - v0y * scaleRatioY * sin)
        val ty = ce.y - (v0x * scaleRatioX * sin + v0y * scaleRatioY * cos)

        return t0.copy(
            translationX = tx,
            translationY = ty,
            scaleX = newSx,
            scaleY = newSy,
            rotationDeg = newRot
        )
    }

    /** One-finger move: grab point stays glued. */
    fun applyMove(s: GestureStart, primaryScreen: PointF): Transform2D {
        val gx = viewport.screenToEditorX(primaryScreen.x)
        val gy = viewport.screenToEditorY(primaryScreen.y)
        return s.start.copy(
            translationX = s.start.translationX + (gx - s.grabEditor.x),
            translationY = s.start.translationY + (gy - s.grabEditor.y)
        )
    }

    /** Corner handle: uniform scale about the anchor. */
    fun applyCornerScale(
        s: GestureStart,
        cornerStartDistEditor: Float,
        handleScreen: PointF,
        minScale: Float,
        maxScale: Float
    ): Transform2D {
        val t0 = s.start
        val px = t0.translationX
        val py = t0.translationY
        val hx = viewport.screenToEditorX(handleScreen.x)
        val hy = viewport.screenToEditorY(handleScreen.y)
        val d = hypot(hx - px, hy - py)
        val k = if (cornerStartDistEditor > 0f) d / cornerStartDistEditor else 1f
        return t0.copy(
            scaleX = (t0.scaleX * k).coerceIn(minScale, maxScale),
            scaleY = (t0.scaleY * k).coerceIn(minScale, maxScale)
        )
    }

    /** Rotation handle: angle around the object's start center. */
    fun applyRotation(s: GestureStart, centerEditor: PointF, handleScreen: PointF): Transform2D {
        val hx = viewport.screenToEditorX(handleScreen.x)
        val hy = viewport.screenToEditorY(handleScreen.y)
        val a = atan2(hy - centerEditor.y, hx - centerEditor.x)
        val delta = Math.toDegrees((a - s.startAngleRad).toDouble()).toFloat()
        return s.start.copy(rotationDeg = s.start.rotationDeg + delta)
    }

    fun flip(t: Transform2D, horizontal: Boolean): Transform2D =
        if (horizontal) t.copy(flipH = !t.flipH) else t.copy(flipV = !t.flipV)
}
