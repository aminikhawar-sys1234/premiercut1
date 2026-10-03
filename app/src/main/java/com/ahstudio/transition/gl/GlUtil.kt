package com.ahstudio.transition.gl

import android.opengl.GLES30
import kotlin.math.abs

object GlUtil {
    const val TEXTURE_2D = GLES30.GL_TEXTURE_2D
    /** GL_TEXTURE_EXTERNAL_OES lives in GLES11Ext; re-declared here for a single import point. */
    const val TEXTURE_EXTERNAL_OES = 0x8D65

    fun checkGlError(where: String) {
        var err = GLES30.glGetError()
        while (err != GLES30.GL_NO_ERROR) {
            throw IllegalStateException("GL error 0x${Integer.toHexString(err)} at $where")
        }
    }

    fun identityMat4(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f)

    fun isIdentity(m: FloatArray, eps: Float = 1e-5f): Boolean {
        if (m.size != 16) return false
        val i = identityMat4()
        for (k in 0 until 16) if (abs(m[k] - i[k]) > eps) return false
        return true
    }
}
