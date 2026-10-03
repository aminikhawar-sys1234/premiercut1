package com.vfx.engine.gpu

import android.opengl.GLES30
import com.vfx.engine.core.EffectEngineException
import com.vfx.engine.core.cache.LruCache

/** Linked program with lazy uniform-location cache, typed setters, auto texture-unit allocation. */
class ShaderProgram(val name: String, programHandle: Int) {

    var program: Int = programHandle; private set
    private val locations = HashMap<String, Int>()
    private val usedUnits = HashSet<Int>()

    fun use() = GLES30.glUseProgram(program)

    private fun loc(n: String): Int = locations.getOrPut(n) { GLES30.glGetUniformLocation(program, n) }

    fun setFloat(n: String, v: Float) { loc(n).let { if (it >= 0) GLES30.glUniform1f(it, v) } }
    fun setInt(n: String, v: Int) { loc(n).let { if (it >= 0) GLES30.glUniform1i(it, v) } }
    fun setBool(n: String, v: Boolean) = setInt(n, if (v) 1 else 0)
    fun setVec2(n: String, x: Float, y: Float) { loc(n).let { if (it >= 0) GLES30.glUniform2f(it, x, y) } }
    fun setVec3(n: String, x: Float, y: Float, z: Float) { loc(n).let { if (it >= 0) GLES30.glUniform3f(it, x, y, z) } }
    fun setVec4(n: String, v: FloatArray) { loc(n).let { if (it >= 0) GLES30.glUniform4fv(it, 1, v, 0) } }
    fun setMat3(n: String, m: FloatArray) { loc(n).let { if (it >= 0) GLES30.glUniformMatrix3fv(it, 1, false, m, 0) } }
    fun setMat4(n: String, m: FloatArray) { loc(n).let { if (it >= 0) GLES30.glUniformMatrix4fv(it, 1, false, m, 0) } }
    fun setFloatArray(n: String, v: FloatArray) { loc(n).let { if (it >= 0) GLES30.glUniform1fv(it, v.size, v, 0) } }

    /** Binds texture to a free unit and points the sampler uniform at it. */
    fun setTexture(n: String, tex: GpuTexture, requestedUnit: Int = -1): Int {
        val unit = if (requestedUnit >= 0) requestedUnit else nextFreeUnit()
        tex.bind(unit)
        loc(n).let { if (it >= 0) GLES30.glUniform1i(it, unit) }
        usedUnits.add(unit)
        return unit
    }

    /** Raw GL texture id binding (external OES decoder input). */
    fun setRawTexture(n: String, target: Int, textureId: Int, requestedUnit: Int = -1): Int {
        val unit = if (requestedUnit >= 0) requestedUnit else nextFreeUnit()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, textureId)
        loc(n).let { if (it >= 0) GLES30.glUniform1i(it, unit) }
        usedUnits.add(unit)
        return unit
    }

    private fun nextFreeUnit(): Int { var u = 0; while (u in usedUnits) u++; return u }

    internal fun resetTextureBindings() = usedUnits.clear()

    fun release() {
        if (program != 0) { GLES30.glDeleteProgram(program); program = 0 }
    }
    fun abandon() { program = 0 }
}

/** Compiles + links with structured errors carrying driver logs. */
class ShaderCompiler {

    fun compileVertex(src: String, name: String): Int = compile(GLES30.GL_VERTEX_SHADER, src, "$name.vs")
    fun compileFragment(src: String, name: String): Int = compile(GLES30.GL_FRAGMENT_SHADER, src, "$name.fs")

    private fun compile(type: Int, src: String, name: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw EffectEngineException.ShaderCompile(name, log)
        }
        return shader
    }

    fun link(vs: Int, fs: Int, name: String, attribBindings: Map<Int, String> = DEFAULT_BINDINGS): ShaderProgram {
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        attribBindings.forEach { (location, attr) -> GLES30.glBindAttribLocation(p, location, attr) }
        GLES30.glLinkProgram(p)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            throw EffectEngineException.ShaderLink(name, log)
        }
        return ShaderProgram(name, p)
    }

    companion object {
        val DEFAULT_BINDINGS = mapOf(0 to "a_pos", 1 to "a_texCoord")

        fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(shader)
                GLES30.glDeleteShader(shader)
                throw EffectEngineException.ShaderCompile("GLShader", log ?: "Unknown compilation error")
            }
            return shader
        }

        fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
            val program = GLES30.glCreateProgram()
            GLES30.glAttachShader(program, vertexShader)
            GLES30.glAttachShader(program, fragmentShader)
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES30.glGetProgramInfoLog(program)
                GLES30.glDeleteProgram(program)
                throw EffectEngineException.ShaderLink("GLProgram", log ?: "Link error")
            }
            return program
        }
    }
}

/**
 * Compiled-program cache. Key = name + vertex/fragment source hash → deterministic.
 * Context loss → drop references without GL calls (all handles already dead).
 */
class ShaderCache(private val compiler: ShaderCompiler) {

    private val cache = LruCache<String, ShaderProgram>(256)

    fun get(
        name: String,
        vertexSource: String,
        fragmentSource: String,
        attribBindings: Map<Int, String> = ShaderCompiler.DEFAULT_BINDINGS
    ): ShaderProgram {
        val key = "$name|${vertexSource.hashCode()}|${fragmentSource.hashCode()}"
        cache.get(key)?.let { return it }
        val vs = compiler.compileVertex(vertexSource, name)
        val fs = compiler.compileFragment(fragmentSource, name)
        val prog = compiler.link(vs, fs, name, attribBindings)
        cache.put(key, prog)
        return prog
    }

    fun onContextLost() = cache.clear(releaser = null)   // no GL calls after loss
    fun releaseAll() = cache.clear { it.release() }
}
