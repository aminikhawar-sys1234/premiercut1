package com.ahstudio.screeneditor.viewport

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

class ScreenEditorViewport {

    /** Project/editor space = project pixel space (e.g., 1920×1080). Set from the open project. */
    var editorWidth = 1920f
        set(value) {
            field = value.coerceAtLeast(1f)
            invalidate()
        }

    var editorHeight = 1080f
        set(value) {
            field = value.coerceAtLeast(1f)
            invalidate()
        }

    var minScale = 0.05f
    var maxScale = 12f

    private var scale = 1f
    private var tx = 0f   // screen = editor * scale + translation
    private var ty = 0f

    private val screenBounds = RectF()          // preview view area, set on layout
    private val matrix = Matrix()               // cached; rebuilt only when dirty
    private val inverse = Matrix()
    private var dirty = true

    private val _state = MutableStateFlow(ViewportState(scale, tx, ty))
    val state: StateFlow<ViewportState> = _state.asStateFlow()

    data class ViewportState(val scale: Float, val tx: Float, val ty: Float)

    fun onScreenBoundsChanged(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        screenBounds.set(0f, 0f, w.toFloat(), h.toFloat())
        fit()
    }

    fun fit() {
        if (screenBounds.isEmpty) return
        scale = min(screenBounds.width() / editorWidth, screenBounds.height() / editorHeight)
        tx = (screenBounds.width() - editorWidth * scale) / 2f
        ty = (screenBounds.height() - editorHeight * scale) / 2f
        invalidate()
    }

    fun reset() = fit()

    /** Focal-preserving zoom: the editor point under (fx,fy) stays under (fx,fy). */
    fun zoomAt(fx: Float, fy: Float, factor: Float) {
        val s = (scale * factor).coerceIn(minScale, maxScale)
        if (s == scale) return
        val ex = (fx - tx) / scale
        val ey = (fy - ty) / scale
        scale = s
        tx = fx - ex * scale
        ty = fy - ey * scale
        clampPan()
        invalidate()
    }

    fun zoomIn() {
        zoomAt(screenBounds.centerX(), screenBounds.centerY(), 1.25f)
    }

    fun zoomOut() {
        zoomAt(screenBounds.centerX(), screenBounds.centerY(), 0.8f)
    }

    fun panBy(dxScreen: Float, dyScreen: Float) {
        tx += dxScreen
        ty += dyScreen
        clampPan()
        invalidate()
    }

    // ---- Coordinate conversion (allocation-free with out params) ----
    fun screenToEditorX(sx: Float): Float = if (scale != 0f) (sx - tx) / scale else 0f
    fun screenToEditorY(sy: Float): Float = if (scale != 0f) (sy - ty) / scale else 0f
    fun editorToScreenX(ex: Float): Float = ex * scale + tx
    fun editorToScreenY(ey: Float): Float = ey * scale + ty

    fun screenToEditor(out: PointF, sx: Float, sy: Float) {
        out.set(screenToEditorX(sx), screenToEditorY(sy))
    }

    fun editorToScreen(out: PointF, ex: Float, ey: Float) {
        out.set(editorToScreenX(ex), editorToScreenY(ey))
    }

    /** Normalized [0..1] over the project frame. */
    fun screenToNormalized(out: PointF, sx: Float, sy: Float) {
        val w = if (editorWidth > 0f) editorWidth else 1f
        val h = if (editorHeight > 0f) editorHeight else 1f
        out.set(screenToEditorX(sx) / w, screenToEditorY(sy) / h)
    }

    fun normalizedToEditor(out: PointF, nx: Float, ny: Float) {
        out.set(nx * editorWidth, ny * editorHeight)
    }

    /** GPU-compositor path: hand the existing renderer the model/view matrix. No bitmaps. */
    fun acquireMatrix(m: Matrix): Matrix {
        ensureMatrix()
        m.set(matrix)
        return m
    }

    private fun clampPan() {
        if (screenBounds.isEmpty) return
        val w = editorWidth * scale
        val h = editorHeight * scale

        tx = if (w <= screenBounds.width()) {
            (screenBounds.width() - w) / 2f
        } else {
            tx.coerceIn(screenBounds.width() - w, 0f)
        }

        ty = if (h <= screenBounds.height()) {
            (screenBounds.height() - h) / 2f
        } else {
            ty.coerceIn(screenBounds.height() - h, 0f)
        }
    }

    private fun ensureMatrix() {
        if (!dirty) return
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(tx, ty)
        if (!matrix.invert(inverse)) {
            inverse.set(matrix)
        }
        dirty = false
    }

    private fun invalidate() {
        dirty = true
        _state.value = ViewportState(scale, tx, ty)
    }
}
