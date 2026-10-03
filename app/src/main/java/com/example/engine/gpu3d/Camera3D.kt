package com.example.engine.gpu3d

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D Virtual Camera for OpenGL ES 3.0 viewport compositing.
 *
 * Computes View (V) and Projection (P) matrices for perspective 3D rendering.
 */
class Camera3D(
    var fov: Float = 60f,
    var near: Float = 0.1f,
    var far: Float = 1000f,
    var position: FloatArray = floatArrayOf(0f, 0f, 5f),
    var target: FloatArray = floatArrayOf(0f, 0f, 0f),
    var up: FloatArray = floatArrayOf(0f, 1f, 0f)
) {
    /**
     * Computes the 4x4 Perspective Projection Matrix based on the viewport aspect ratio.
     * Supports dynamic near and far planes to prevent z-clipping during close zoom or long depth.
     */
    fun getProjectionMatrix(
        aspectRatio: Float,
        dynamicNear: Float? = null,
        dynamicFar: Float? = null
    ): FloatArray {
        val proj = FloatArray(16)
        val validAspect = if (aspectRatio <= 0f) 1.0f else aspectRatio
        val effectiveNear = (dynamicNear ?: near).coerceAtLeast(0.005f)
        val effectiveFar = (dynamicFar ?: far).coerceAtLeast(effectiveNear + 1.0f)
        Matrix.perspectiveM(proj, 0, fov, validAspect, effectiveNear, effectiveFar)
        return proj
    }

    /**
     * Computes the 4x4 View Matrix representing the camera's eye position,
     * look-at orientation, and up vector.
     */
    fun getViewMatrix(): FloatArray {
        val view = FloatArray(16)
        Matrix.setLookAtM(
            view, 0,
            position[0], position[1], position[2],
            target[0], target[1], target[2],
            up[0], up[1], up[2]
        )
        return view
    }

    /**
     * Computes the combined Model-View-Projection (MVP) Matrix:
     * MVP = Projection * View * Model
     */
    fun getMVPMatrix(modelMatrix: FloatArray, aspectRatio: Float): FloatArray {
        val view = getViewMatrix()
        val proj = getProjectionMatrix(aspectRatio)
        val vp = FloatArray(16)
        val mvp = FloatArray(16)

        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        Matrix.multiplyMM(mvp, 0, vp, 0, modelMatrix, 0)
        return mvp
    }

    /**
     * Applies dynamic parallax tilt based on accelerometer or keyframe coordinates.
     */
    fun applyParallaxTilt(pitchDegrees: Float, yawDegrees: Float, distance: Float = 5f) {
        val pitchRad = Math.toRadians(pitchDegrees.toDouble())
        val yawRad = Math.toRadians(yawDegrees.toDouble())

        position[0] = (distance * sin(yawRad) * cos(pitchRad)).toFloat()
        position[1] = (distance * sin(pitchRad)).toFloat()
        position[2] = (distance * cos(yawRad) * cos(pitchRad)).toFloat()
    }
}
