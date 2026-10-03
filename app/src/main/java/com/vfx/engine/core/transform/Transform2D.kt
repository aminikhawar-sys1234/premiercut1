package com.vfx.engine.core.transform

import com.vfx.engine.core.math.Mat3
import com.vfx.engine.core.math.Vec2
import kotlin.math.cos
import kotlin.math.sin

/** How content maps into a canvas of a different aspect ratio. */
enum class FitMode { CONTAIN, COVER, STRETCH }

/**
 * Layer transform. Position/scale/rotation are relative to the output canvas;
 * anchor is in normalized frame coords. All Y values are in image space
 * (y-down at presentation). Flips are explicit — no accidental vertical inversion:
 * the present pass applies flipY ONCE for GL's y-up textures, then this matrix
 * operates purely in y-down image space.
 *
 * Matrix order (applied to output UV, first listed = applied first):
 *   crop -> flip -> anchor-translate -> rotate -> scale -> position
 */
data class Transform2D(
    var posX: Float = 0f, var posY: Float = 0f,
    var scaleX: Float = 1f, var scaleY: Float = 1f,
    var rotationDeg: Float = 0f,
    var anchorX: Float = 0.5f, var anchorY: Float = 0.5f,
    var cropX: Float = 0f, var cropY: Float = 0f, var cropW: Float = 1f, var cropH: Float = 1f,
    var flipH: Boolean = false, var flipV: Boolean = false
) {
    val translation: Vec2 get() = Vec2(posX, posY)
    val scale: Vec2 get() = Vec2(scaleX, scaleY)
    val rotationDegrees: Float get() = rotationDeg
    val anchorPoint: Vec2 get() = Vec2(anchorX, anchorY)
    val skew: Vec2 get() = Vec2(0f, 0f)

    fun hasCrop(): Boolean = cropX != 0f || cropY != 0f || cropW != 1f || cropH != 1f

    fun isIdentity(): Boolean = !hasCrop() && !flipH && !flipV &&
        posX == 0f && posY == 0f && scaleX == 1f && scaleY == 1f && rotationDeg == 0f

    /**
     * Builds the UV matrix mapping output quad UV (0..1, y-down) -> input sampling UV.
     * [outAspect] = outputWidth / outputHeight (used for correct rotation of
     * non-square frames when the host scales via [fit]).
     */
    fun toUvMatrix(outAspect: Float): Mat3 {
        var m = Mat3.identity()
        // 1) Crop: 0..1 output maps into the crop rect of the input.
        if (hasCrop()) m = m * Mat3.translation(cropX, cropY) * Mat3.scale(cropW, cropH)
        // 2) Flips around center.
        if (flipH) m = m * Mat3.translation(1f, 0f) * Mat3.scale(-1f, 1f)
        if (flipV) m = m * Mat3.translation(0f, 1f) * Mat3.scale(1f, -1f)
        // 3) Anchor -> rotate -> scale -> position (standard 2D hierarchy).
        val rad = Math.toRadians(rotationDeg.toDouble()).toFloat()
        m = m *
            Mat3.translation(anchorX + posX, anchorY + posY) *
            Mat3.rotation(-rad) *                      // image-space y-down => negate GL rotation
            Mat3.scale(scaleX, scaleY) *
            Mat3.translation(-anchorX, -anchorY)
        return m
    }

    /** Convenience: apply CONTAIN/COVER aspect scaling into current scale. */
    fun fit(canvasAspect: Float, contentAspect: Float, mode: FitMode) {
        when (mode) {
            FitMode.STRETCH -> { scaleX = 1f; scaleY = 1f }
            FitMode.CONTAIN -> if (contentAspect > canvasAspect) {
                scaleY = canvasAspect / contentAspect
            } else {
                scaleX = contentAspect / canvasAspect
            }
            FitMode.COVER -> if (contentAspect > canvasAspect) {
                scaleX = contentAspect / canvasAspect
            } else {
                scaleY = canvasAspect / contentAspect
            }
        }
    }
}

object CoordinateSystem {
  fun uToNdc(u: Float): Float = (u * 2f) - 1.0f
  fun vToNdc(v: Float): Float = 1.0f - (v * 2f)
}
