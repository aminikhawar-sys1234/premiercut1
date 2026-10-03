package com.ahstudio.composition.gpu

import android.graphics.Bitmap
import android.opengl.GLES30.*
import java.util.concurrent.ConcurrentLinkedQueue

/** GPU memory discipline (Section 26): pooled textures/FBOs, cached programs, no per-frame allocation. */
class GpuResourcePool(private val maxBytes: Long = 96L * 1024 * 1024) {
    class PooledFbo(val fbo: Int, var tex: Int, var w: Int, var h: Int, var generation: Long = 0)
    private val free = ConcurrentLinkedQueue<PooledFbo>()
    private var usedBytes = 0L
    var texturesCreated = 0L; var poolHits = 0L; var poolMisses = 0L; private set

    fun acquire(w: Int, h: Int): PooledFbo {
        while (true) {
            val p = free.poll() ?: break
            if (p.w == w && p.h == h) { p.generation++; poolHits++; return p }
            deleteTex(p.tex); usedBytes -= p.w.toLong() * p.h * 4
        }
        val tex = IntArray(1); glGenTextures(1, tex, 0)
        glBindTexture(GL_TEXTURE_2D, tex[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        val fbo = IntArray(1); glGenFramebuffers(1, fbo, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0]); glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex[0], 0)
        usedBytes += w.toLong() * h * 4; texturesCreated++; poolMisses++
        return PooledFbo(fbo[0], tex[0], w, h)
    }

    fun release(p: PooledFbo) {
        if (usedBytes <= maxBytes) free.add(p)
        else { deleteTex(p.tex); usedBytes -= p.w.toLong() * p.h * 4 }
    }

    private fun deleteTex(t: Int) = glDeleteTextures(1, intArrayOf(t), 0)

    fun releaseAll() {
        free.forEach { deleteTex(it.tex); glDeleteFramebuffers(1, intArrayOf(it.fbo), 0) }
        free.clear()
        usedBytes = 0
    }
}

class ShaderCache {
    private val programs = HashMap<String, Int>()
    fun get(key: String, vs: String, fs: String): Int = programs.getOrPut(key) { compile(vs, fs) }

    private fun compile(vs: String, fs: String): Int {
        fun sh(type: Int, src: String): Int {
            val s = glCreateShader(type); glShaderSource(s, src); glCompileShader(s)
            val ok = IntArray(1); glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] == GL_TRUE) { "Shader compile failed: ${glGetShaderInfoLog(s)}" }
            return s
        }
        val p = glCreateProgram()
        glAttachShader(p, sh(GL_VERTEX_SHADER, vs)); glAttachShader(p, sh(GL_FRAGMENT_SHADER, fs))
        glBindAttribLocation(p, 0, "aPos"); glLinkProgram(p)
        val ok = IntArray(1); glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        check(ok[0] == GL_TRUE) { "Program link failed: ${glGetProgramInfoLog(p)}" }
        return p
    }
}

/** Bitmap upload cache (Section 22): never re-upload the same asset every frame. */
class TextureUploadCache(private val maxEntries: Int = 64) {
    private val map = LinkedHashMap<String, Pair<Int, Long>>(16, 0.75f, true)
    fun get(key: String, bitmap: Bitmap?, version: Long): Int? {
        val hit = map[key]
        if (hit != null && hit.second == version) return hit.first
        if (hit != null) { glDeleteTextures(1, intArrayOf(hit.first), 0); map.remove(key) }
        if (bitmap == null) return null
        val tex = upload(bitmap); map[key] = tex to version; evictIfNeeded(); return tex
    }

    private fun evictIfNeeded() {
        val it = map.entries.iterator()
        while (map.size > maxEntries && it.hasNext()) {
            glDeleteTextures(1, intArrayOf(it.next().value.first), 0)
            it.remove()
        }
    }

    private fun upload(b: Bitmap): Int {
        val t = IntArray(1); glGenTextures(1, t, 0); glBindTexture(GL_TEXTURE_2D, t[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        android.opengl.GLUtils.texImage2D(GL_TEXTURE_2D, 0, b, 0)
        return t[0]
    }
}
