package com.ahstudio.transition.gl

import android.opengl.GLES30

/** Compiled program with per-name uniform location cache (queried once, not per frame). */
class GlProgram(val handle: Int) {
    private val locations = HashMap<String, Int>()

    fun location(name: String): Int =
        locations.getOrPut(name) { GLES30.glGetUniformLocation(handle, name) }

    fun use() = GLES30.glUseProgram(handle)

    fun bind(name: String, v: Float) { val l = location(name); if (l >= 0) GLES30.glUniform1f(l, v) }
    fun bindInt(name: String, v: Int) { val l = location(name); if (l >= 0) GLES30.glUniform1i(l, v) }
    fun bindVec2(name: String, x: Float, y: Float) { val l = location(name); if (l >= 0) GLES30.glUniform2f(l, x, y) }
    fun bindVec3(name: String, x: Float, y: Float, z: Float) { val l = location(name); if (l >= 0) GLES30.glUniform3f(l, x, y, z) }
    fun bindVec4(name: String, x: Float, y: Float, z: Float, w: Float) { val l = location(name); if (l >= 0) GLES30.glUniform4f(l, x, y, z, w) }
    fun bindMat4(name: String, m: FloatArray) {
        val l = location(name)
        if (l >= 0) GLES30.glUniformMatrix4fv(l, 1, false, m, 0)
    }

    /** Binds texture to unit if (and only if) the shader declares the sampler (GLSL strips unused). */
    fun bindTexture(name: String, unit: Int, texId: Int, target: Int) {
        if (location(name) < 0) return
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, texId)
        GLES30.glUniform1i(location(name), unit)
    }

    fun close() { if (handle != 0) GLES30.glDeleteProgram(handle) }
}
