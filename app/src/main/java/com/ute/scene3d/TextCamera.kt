package com.ute.scene3d

import com.ute.core.Mat4

/** Camera whose every parameter is animatable via keyframes (CAMERA_* properties). */
data class TextCamera(
    val projection: Projection = Projection.PERSPECTIVE,
    val fovDeg: Float = 40f,
    val x: Float = 0f, val y: Float = 0f, val z: Float = 600f,
    val rotXDeg: Float = 0f, val rotYDeg: Float = 0f, val rotZDeg: Float = 0f,
    val zoom: Float = 1f,
    val near: Float = 1f, val far: Float = 10000f,
) {
    enum class Projection { PERSPECTIVE, ORTHOGRAPHIC }

    fun viewProj(viewportW: Float, viewportH: Float): Mat4 {
        val aspect = viewportW / viewportH.coerceAtLeast(1f)
        val proj = when (projection) {
            Projection.PERSPECTIVE -> Mat4.perspective(fovDeg / zoom.coerceAtLeast(0.01f), aspect, near, far)
            Projection.ORTHOGRAPHIC -> Mat4.ortho(-viewportW / 2 / zoom, viewportW / 2 / zoom,
                -viewportH / 2 / zoom, viewportH / 2 / zoom, near, far)
        }
        val view = Mat4.multiply(
            Mat4.multiply(Mat4.translation(-x, -y, -z),
                Mat4.multiply(Mat4.rotationY(rotYDeg), Mat4.rotationX(rotXDeg))),
            Mat4.rotationZ(rotZDeg))
        return Mat4.multiply(proj, view)
    }
}
