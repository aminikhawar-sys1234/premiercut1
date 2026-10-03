package com.example.engine.gpu3d

import android.opengl.Matrix
import kotlin.math.*

/**
 * Represents a full 3D spatial transformation for timeline layers, video clips, text, and overlays.
 *
 * Supports 3D Translation (X, Y, Z depth), Euler/Quaternion Rotations (Pitch, Yaw, Roll),
 * 3D Scale, and custom 3D Anchor Points.
 */
data class Transform3D(
    val translation: FloatArray = floatArrayOf(0f, 0f, 0f),
    val rotation: FloatArray = floatArrayOf(0f, 0f, 0f), // Pitch (X), Yaw (Y), Roll (Z) in degrees
    val scale: FloatArray = floatArrayOf(1f, 1f, 1f),
    val anchor: FloatArray = floatArrayOf(0f, 0f, 0f)
) {
    /**
     * Computes the 4x4 Model Matrix (M) incorporating anchor offset, scaling,
     * 3D Euler rotations (ZYX order), and translation.
     */
    fun getModelMatrix(): FloatArray {
        val model = FloatArray(16)
        Matrix.setIdentityM(model, 0)

        // 1. Translation in 3D space
        Matrix.translateM(model, 0, translation[0], translation[1], translation[2])

        // 2. Translate to Anchor Point
        if (anchor[0] != 0f || anchor[1] != 0f || anchor[2] != 0f) {
            Matrix.translateM(model, 0, anchor[0], anchor[1], anchor[2])
        }

        // 3. 3D Rotations (Euler angles: Pitch X, Yaw Y, Roll Z)
        if (rotation[2] != 0f) Matrix.rotateM(model, 0, rotation[2], 0f, 0f, 1f) // Roll (Z)
        if (rotation[1] != 0f) Matrix.rotateM(model, 0, rotation[1], 0f, 1f, 0f) // Yaw (Y)
        if (rotation[0] != 0f) Matrix.rotateM(model, 0, rotation[0], 1f, 0f, 0f) // Pitch (X)

        // 4. Translate back from Anchor Point
        if (anchor[0] != 0f || anchor[1] != 0f || anchor[2] != 0f) {
            Matrix.translateM(model, 0, -anchor[0], -anchor[1], -anchor[2])
        }

        // 5. 3D Scaling
        Matrix.scaleM(model, 0, scale[0], scale[1], scale[2])

        return model
    }

    /**
     * Precomputes 3x3 normal matrix on CPU: transpose(inverse(modelMatrix)).
     * Avoids runtime GLSL inverse() driver crashes on older Mali/Adreno GPUs.
     */
    fun getNormalMatrix(modelMatrix: FloatArray = getModelMatrix()): FloatArray {
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, modelMatrix, 0)) {
            Matrix.setIdentityM(inv, 0)
        }
        val normalMatrix = FloatArray(9)
        // Transpose of the upper-left 3x3 of the inverse matrix
        normalMatrix[0] = inv[0]; normalMatrix[1] = inv[4]; normalMatrix[2] = inv[8]
        normalMatrix[3] = inv[1]; normalMatrix[4] = inv[5]; normalMatrix[5] = inv[9]
        normalMatrix[6] = inv[2]; normalMatrix[7] = inv[6]; normalMatrix[8] = inv[10]
        return normalMatrix
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Transform3D
        if (!translation.contentEquals(other.translation)) return false
        if (!rotation.contentEquals(other.rotation)) return false
        if (!scale.contentEquals(other.scale)) return false
        if (!anchor.contentEquals(other.anchor)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = translation.contentHashCode()
        result = 31 * result + rotation.contentHashCode()
        result = 31 * result + scale.contentHashCode()
        result = 31 * result + anchor.contentHashCode()
        return result
    }
}
