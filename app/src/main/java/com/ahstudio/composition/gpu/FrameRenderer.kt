package com.ahstudio.composition.gpu

import android.graphics.Bitmap
import android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES
import android.opengl.GLES30.*
import android.opengl.Matrix
import com.ahstudio.composition.cache.FrameCache
import com.ahstudio.composition.core.CompositionProfiler
import com.ahstudio.composition.core.CompositionResolution
import com.ahstudio.composition.core.GpuAllocationException
import com.ahstudio.composition.core.RenderConfig
import com.ahstudio.composition.core.SkippedLayer
import com.ahstudio.composition.graph.*
import com.ahstudio.composition.integration.*
import com.ahstudio.composition.mask.PathTessellator
import com.ahstudio.composition.transform.TransformEvaluator
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GL-thread-only executor. Owns all passes: Source→Transform→Effects→Mask→Matte→Blend→Adjust→Output. */
class FrameRenderer(
    private val pool: GpuResourcePool,
    private val shaderCache: ShaderCache,
    private val uploadCache: TextureUploadCache,
    private val profiler: CompositionProfiler,
    private val mediaBridge: LayerSourceBridge,
    private val textBridge: TextRasterizerBridge,
    private val imageBridge: ImageSourceBridge,
    private val effectsBridge: EffectsBridge,
) {
    private val quadVbo: Int
    private val frameCache: FrameCache
    private val maskCache = LinkedHashMap<Long, Int>(8, 0.75f, true)
    private var generation: Long = 0

    init {
        val vbo = IntArray(1)
        glGenBuffers(1, vbo, 0)
        quadVbo = vbo[0]
        val bb = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder())
        val f = bb.asFloatBuffer()
        f.put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)).position(0)
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo)
        glBufferData(GL_ARRAY_BUFFER, 4 * 2 * 4, f, GL_STATIC_DRAW)
        frameCache = FrameCache(0)
    }

    fun setCacheBudget(bytes: Long) {
        frameCache.clear { recycleHard(it) }
    }

    data class Env(val config: RenderConfig, val generation: Long)

    fun renderFrame(
        graph: com.ahstudio.composition.core.GraphRef,
        timeUs: Long,
        config: RenderConfig,
        precomp: (CompositionId, Long, RenderConfig, Int) -> Int?,
    ): Pair<Int, List<SkippedLayer>> {
        val t0 = System.nanoTime()
        profiler.resetForFrame()
        val w = config.output.width
        val h = config.output.height
        val skipped = ArrayList<SkippedLayer>()
        val stateGen = graph.invalidator.currentGeneration() * 31 + config.hashCode()
        generation = stateGen

        val ck = FrameCache.Key(graph.id.value, timeUs, w, h, stateGen)
        if (config.enableFrameCache) {
            frameCache.get(ck)?.let {
                profiler.cacheHits.incrementAndGet()
                return it.textureId to skipped
            }
        }
        profiler.cacheMisses.incrementAndGet()

        val plan = com.ahstudio.composition.core.CompositionPlanner.plan(graph.graph, timeUs)

        var backdrop = pool.acquire(w, h).also {
            bindFbo(it)
            glClearColor(config.clearColor[0], config.clearColor[1], config.clearColor[2], config.clearColor[3])
            glClear(GL_COLOR_BUFFER_BIT)
        }
        val emittedMatte = HashSet<Long>()

        for (op in plan.ops) {
            try {
                when (op) {
                    is com.ahstudio.composition.core.CompositionPlanner.Op.Adjust -> {
                        backdrop = adjustPass(op.layer, timeUs, backdrop, w, h)
                    }
                    is com.ahstudio.composition.core.CompositionPlanner.Op.DrawLayer -> {
                        val l = op.layer
                        var tex = renderLayerChain(l, timeUs, w, h, precomp) ?: run {
                            skipped += SkippedLayer(l.id, op.skipReason ?: "no source")
                            continue
                        }
                        if (op.hasMatte) {
                            val mId = l.trackMatteLayer!!.value
                            val mTex = if (emittedMatte.add(mId)) {
                                renderLayerChain(graph.graph.layer(LayerId(mId))!!, timeUs, w, h, precomp)
                            } else {
                                maskCacheGet(mId)
                            }
                            if (mTex == null) {
                                skipped += SkippedLayer(l.id, "matte source unavailable")
                                continue
                            }
                            maskCachePut(mId, mTex)
                            tex = matteCombine(tex, mTex, l.trackMatteMode, w, h)
                        }
                        val opac = l.opacity.valueAt(timeUs) { p, c, f -> p + (c - p) * f }.coerceIn(0f, 1f)
                        backdrop = blendPass(backdrop, tex, l.blendMode, l.compositeOp, opac, w, h)
                        profiler.layersRendered.incrementAndGet()
                    }
                    is com.ahstudio.composition.core.CompositionPlanner.Op.Skip -> {
                        profiler.layersSkipped.incrementAndGet()
                    }
                }
            } catch (e: GpuAllocationException) {
                skipped += SkippedLayer(LayerId(-1), "gpu pressure: ${e.message}")
            } catch (e: Exception) {
                skipped += SkippedLayer(LayerId(-1), "layer failed: ${e.message}")
            }
        }

        if (config.enableFrameCache) {
            val owned = pool.acquire(w, h)
            copyTex(backdrop.tex, owned, w, h)
            frameCache.put(ck, FrameCache.Entry(owned.tex, w.toLong() * h * 4), evict = { recycleHard(it) })
        }
        profiler.lastRenderNs.set(System.nanoTime() - t0)
        return backdrop.tex to skipped
    }

    private fun renderLayerChain(
        l: CompositionLayer,
        t: Long,
        w: Int,
        h: Int,
        precomp: (CompositionId, Long, RenderConfig, Int) -> Int?,
    ): Int? {
        val src = acquireSource(l, t, w, h, precomp) ?: return null
        var tex = drawTransformed(src, l, t, w, h)
        for (p in effectsBridge.resolvePasses(l, t)) {
            tex = effectsBridge.applyPass(p, tex, w, h, pool)
            profiler.gpuPasses.incrementAndGet()
        }
        if (l.masks.isNotEmpty()) {
            maskChain(l, t, w, h)?.let { maskTex -> tex = multiplyMask(tex, maskTex, w, h) }
        }
        return tex
    }

    private fun acquireSource(
        l: CompositionLayer,
        t: Long,
        w: Int,
        h: Int,
        precomp: (CompositionId, Long, RenderConfig, Int) -> Int?,
    ): Source? = when (val p = l.payload) {
        is LayerPayload.Video -> {
            val mediaT = p.trimInUs + ((t - l.startTimeUs) * p.speed).toLong()
            mediaBridge.acquireVideoFrame(l, mediaT)?.let { f ->
                Source(f.oesTextureId, isOes = true, mediaW = f.width, mediaH = f.height, uv = f.uvXform)
            }
        }
        is LayerPayload.Image -> imageBridge.acquireBitmap(l)?.let { b ->
            Source(uploadBitmap(p.sourceUri, b.bitmap, b.version), false, b.bitmap.width, b.bitmap.height, FLIP_V)
        }
        is LayerPayload.Text -> textBridge.rasterize(l, t)?.let { r ->
            Source(r.textureId, false, r.width, r.height, r.uvXform)
        }
        is LayerPayload.Shape -> rasterShape(l, p, w, h)
        is LayerPayload.PreComp -> {
            val childT = p.timeMapper.parentToChild(t - l.startTimeUs)
            precomp(p.compositionRef, childT, RenderConfig(CompositionResolution(w, h), CompositionResolution(w, h)), 0)?.let { tex ->
                Source(tex, false, w, h, IDENTITY_UV)
            }
        }
        LayerPayload.Null -> null
    }

    private fun rasterShape(l: CompositionLayer, p: LayerPayload.Shape, w: Int, h: Int): Source? {
        val pts = PathTessellator.flatten(p.pathData)
        val tris = PathTessellator.triangulate(pts)
        if (tris.isEmpty()) return null
        val fbo = pool.acquire(w, h); bindFbo(fbo); glClearColor(0f, 0f, 0f, 0f); glClear(GL_COLOR_BUFFER_BIT)
        val prog = shaderCache.get("solid", Shaders.VERT, Shaders.SOLID_FS); glUseProgram(prog)
        glUniformMatrix4fv(glGetUniformLocation(prog, "uClipFromMedia"), 1, false, compToNdc(w, h), 0)
        glUniform2f(glGetUniformLocation(prog, "uMediaSize"), 1f, 1f)
        glUniform4f(glGetUniformLocation(prog, "uUvXform"), 1f, 1f, 0f, 0f)
        val a = ((p.fillColor ushr 24) and 0xFF) / 255f
        val r = ((p.fillColor ushr 16) and 0xFF) / 255f
        val g = ((p.fillColor ushr 8) and 0xFF) / 255f
        val b = (p.fillColor and 0xFF) / 255f
        glUniform4f(glGetUniformLocation(prog, "uColor"), r * a, g * a, b * a, a)
        val buf = ByteBuffer.allocateDirect(tris.size * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (tri in tris) { buf.put(tri.a.x).put(tri.a.y).put(tri.b.x).put(tri.b.y).put(tri.c.x).put(tri.c.y) }
        buf.position(0)
        val vbo = IntArray(1); glGenBuffers(1, vbo, 0)
        glBindBuffer(GL_ARRAY_BUFFER, vbo[0]); glBufferData(GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GL_STREAM_DRAW)
        val loc = glGetAttribLocation(prog, "aPos"); glEnableVertexAttribArray(loc)
        glVertexAttribPointer(loc, 2, GL_FLOAT, false, 0, 0)
        glDrawArrays(GL_TRIANGLES, 0, tris.size * 3)
        glDisableVertexAttribArray(loc); glDeleteBuffers(1, vbo, 0)
        profiler.gpuPasses.incrementAndGet()
        return Source(fbo.tex, false, w, h, IDENTITY_UV)
    }

    private fun uploadBitmap(key: String, b: Bitmap, version: Long): Int =
        uploadCache.get("$key#$version", b, version) ?: throw GpuAllocationException("bitmap upload failed")

    private fun drawTransformed(src: Source, l: CompositionLayer, t: Long, w: Int, h: Int): Int {
        val fbo = pool.acquire(w, h)
        bindFbo(fbo)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_BLEND)
        val prog = shaderCache.get(if (src.isOes) "oes" else "copy", Shaders.VERT, if (src.isOes) Shaders.COPY_OES_FS else Shaders.COPY_FS)
        glUseProgram(prog)
        val world = TransformEvaluator.worldOf(l, { id -> lookupParent(l, id) }, t)
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, compToNdc(w, h), 0, world.toColumnMajor4(), 0)
        setQuadAttrib(prog)
        glUniformMatrix4fv(glGetUniformLocation(prog, "uClipFromMedia"), 1, false, mvp, 0)
        glUniform2f(glGetUniformLocation(prog, "uMediaSize"), src.mediaW.toFloat(), src.mediaH.toFloat())
        glUniform4f(glGetUniformLocation(prog, "uUvXform"), src.uv[0], src.uv[1], src.uv[2], src.uv[3])
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(if (src.isOes) GL_TEXTURE_EXTERNAL_OES else GL_TEXTURE_2D, src.textureId)
        glUniform1i(glGetUniformLocation(prog, "uTex"), 0)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        profiler.gpuPasses.incrementAndGet()
        return fbo.tex
    }

    private var parentLookup: ((Long) -> CompositionLayer?)? = null
    fun setParentLookup(f: (Long) -> CompositionLayer?) { parentLookup = f }
    private fun lookupParent(l: CompositionLayer, id: Long): CompositionLayer? = parentLookup?.invoke(id)

    private fun adjustPass(l: CompositionLayer, t: Long, backdrop: GpuResourcePool.PooledFbo, w: Int, h: Int): GpuResourcePool.PooledFbo {
        var tex = backdrop.tex
        for (p in effectsBridge.resolvePasses(l, t)) {
            tex = effectsBridge.applyPass(p, tex, w, h, pool)
        }
        profiler.gpuPasses.incrementAndGet()
        val out = pool.acquire(w, h)
        bindFbo(out)
        fullscreen("copy", Shaders.COPY_FS, tex, w, h)
        return out
    }

    private fun blendPass(
        backdrop: GpuResourcePool.PooledFbo,
        layerTex: Int,
        mode: BlendMode,
        op: CompositeOp,
        opacity: Float,
        w: Int,
        h: Int,
    ): GpuResourcePool.PooledFbo {
        val out = pool.acquire(w, h)
        bindFbo(out)
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        val prog = shaderCache.get("blend", Shaders.VERT, Shaders.BLEND_FS)
        glUseProgram(prog)
        setQuadAttrib(prog)
        fullscreenMatrix(prog, w, h)
        bindTex(prog, "uBackdrop", backdrop.tex, 0)
        bindTex(prog, "uLayer", layerTex, 1)
        glUniform1i(glGetUniformLocation(prog, "uBlendMode"), mode.ordinal)
        glUniform1i(glGetUniformLocation(prog, "uCompositeOp"), op.ordinal)
        glUniform1f(glGetUniformLocation(prog, "uOpacity"), opacity)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisable(GL_BLEND)
        profiler.gpuPasses.incrementAndGet()
        pool.release(backdrop)
        return out
    }

    private fun matteCombine(srcTex: Int, matteTex: Int, mode: TrackMatteMode, w: Int, h: Int): Int {
        val out = pool.acquire(w, h)
        bindFbo(out)
        fullscreen("matte", Shaders.MATTE_FS, srcTex, w, h, extra = { prog ->
            bindTex(prog, "uMatte", matteTex, 1)
            glUniform1i(glGetUniformLocation(prog, "uMode"), mode.ordinal)
        })
        return out.tex
    }

    private fun maskChain(l: CompositionLayer, t: Long, w: Int, h: Int): Int? {
        var acc: GpuResourcePool.PooledFbo? = null
        for (m in l.masks) {
            val shapeFbo = rasterMaskShape(m, t, w, h) ?: continue
            if (m.featherPx > 0.5f) blurAlpha(shapeFbo, m.featherPx / 2f, w, h)
            val prev = acc
            val out = pool.acquire(w, h)
            bindFbo(out)
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            fullscreen("maskCombine", Shaders.MASK_COMBINE_FS, prev?.tex ?: solidZero(w, h), w, h, extra = { prog ->
                bindTex(prog, "uShape", shapeFbo.tex, 1)
                glUniform1i(glGetUniformLocation(prog, "uMode"), m.mode.ordinal)
                glUniform1f(glGetUniformLocation(prog, "uOpacity"), m.opacity)
                glUniform1f(glGetUniformLocation(prog, "uInvert"), if (m.inverted) 1f else 0f)
            })
            prev?.let { pool.release(it) }
            pool.release(shapeFbo)
            acc = out
        }
        return acc?.tex
    }

    private fun rasterMaskShape(m: MaskInstance, t: Long, w: Int, h: Int): GpuResourcePool.PooledFbo? {
        val path = com.ahstudio.composition.mask.PathAnim.samplePath(m, t)
        val pts = PathTessellator.flatten(path)
        val expanded = PathTessellator.offsetPolygon(pts, m.expansionPx)
        val tris = PathTessellator.triangulate(expanded)
        val fbo = pool.acquire(w, h)
        bindFbo(fbo)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        if (tris.isNotEmpty()) {
            val prog = shaderCache.get("solid", Shaders.VERT, Shaders.SOLID_FS)
            glUseProgram(prog)
            glUniformMatrix4fv(glGetUniformLocation(prog, "uClipFromMedia"), 1, false, compToNdc(w, h), 0)
            glUniform2f(glGetUniformLocation(prog, "uMediaSize"), 1f, 1f)
            glUniform4f(glGetUniformLocation(prog, "uUvXform"), 1f, 1f, 0f, 0f)
            glUniform4f(glGetUniformLocation(prog, "uColor"), 1f, 1f, 1f, 1f)
            val buf = ByteBuffer.allocateDirect(tris.size * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (tri in tris) { buf.put(tri.a.x).put(tri.a.y).put(tri.b.x).put(tri.b.y).put(tri.c.x).put(tri.c.y) }
            buf.position(0)
            val vbo = IntArray(1)
            glGenBuffers(1, vbo, 0)
            glBindBuffer(GL_ARRAY_BUFFER, vbo[0])
            glBufferData(GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GL_STREAM_DRAW)
            val loc = glGetAttribLocation(prog, "aPos")
            glEnableVertexAttribArray(loc)
            glVertexAttribPointer(loc, 2, GL_FLOAT, false, 0, 0)
            glDrawArrays(GL_TRIANGLES, 0, tris.size * 3)
            glDisableVertexAttribArray(loc)
            glDeleteBuffers(1, vbo, 0)
            profiler.gpuPasses.incrementAndGet()
        }
        return fbo
    }

    private fun multiplyMask(layerTex: Int, maskTex: Int, w: Int, h: Int): Int {
        val out = pool.acquire(w, h)
        bindFbo(out)
        fullscreen("maskMul", Shaders.MASK_MULTIPLY_FS, layerTex, w, h, extra = { bindTex(it, "uMask", maskTex, 1) })
        return out.tex
    }

    private fun blurAlpha(fbo: GpuResourcePool.PooledFbo, sigma: Float, w: Int, h: Int) {
        val radius = sigma.times(3f).toInt().coerceIn(1, 32)
        for (axis in 0..1) {
            val tmp = pool.acquire(w, h)
            bindFbo(tmp)
            fullscreen("blur", Shaders.BLUR_FS, fbo.tex, w, h, extra = { prog ->
                glUniform2f(glGetUniformLocation(prog, "uStep"), if (axis == 0) 1f / w else 0f, if (axis == 0) 0f else 1f / h)
                glUniform1i(glGetUniformLocation(prog, "uRadius"), radius)
                glUniform1f(glGetUniformLocation(prog, "uSigma"), sigma)
            })
            pool.release(fbo)
            copyInto(fbo, tmp, w, h)
            pool.release(tmp)
        }
    }

    private fun fullscreen(key: String, fs: String, srcTex: Int, w: Int, h: Int, extra: ((Int) -> Unit)? = null) {
        val prog = shaderCache.get(key, Shaders.VERT, fs)
        glUseProgram(prog)
        setQuadAttrib(prog)
        fullscreenMatrix(prog, w, h)
        bindTex(prog, "uTex", srcTex, 0)
        extra?.invoke(prog)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        profiler.gpuPasses.incrementAndGet()
    }

    private fun copyTex(src: Int, dst: GpuResourcePool.PooledFbo, w: Int, h: Int) {
        bindFbo(dst)
        fullscreen("copy", Shaders.COPY_FS, src, w, h)
    }

    private fun copyInto(dst: GpuResourcePool.PooledFbo, srcFbo: GpuResourcePool.PooledFbo, w: Int, h: Int) = copyTex(srcFbo.tex, dst, w, h)

    private fun solidZero(w: Int, h: Int): Int = pool.acquire(w, h).also {
        bindFbo(it)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
    }.tex

    private fun setQuadAttrib(prog: Int) {
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo)
        val loc = glGetAttribLocation(prog, "aPos")
        glEnableVertexAttribArray(loc)
        glVertexAttribPointer(loc, 2, GL_FLOAT, false, 0, 0)
    }

    private fun fullscreenMatrix(prog: Int, w: Int, h: Int) {
        glUniformMatrix4fv(glGetUniformLocation(prog, "uClipFromMedia"), 1, false, FS_NDC, 0)
        glUniform2f(glGetUniformLocation(prog, "uMediaSize"), 1f, 1f)
        glUniform4f(glGetUniformLocation(prog, "uUvXform"), 1f, 1f, 0f, 0f)
    }

    private fun bindTex(prog: Int, name: String, tex: Int, unit: Int) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, tex)
        glUniform1i(glGetUniformLocation(prog, name), unit)
    }

    private fun bindFbo(f: GpuResourcePool.PooledFbo) {
        glBindFramebuffer(GL_FRAMEBUFFER, f.fbo)
        glViewport(0, 0, f.w, f.h)
    }

    private fun maskCachePut(id: Long, tex: Int) { maskCache[id] = tex }
    private fun maskCacheGet(id: Long): Int? = maskCache[id]
    fun clearFrameMatteCache() = maskCache.clear()

    private fun recycleHard(e: FrameCache.Entry) { glDeleteTextures(1, intArrayOf(e.textureId), 0) }

    fun present(outTex: Int, w: Int, h: Int) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(0, 0, w, h)
        fullscreen("copy", Shaders.COPY_FS, outTex, w, h)
    }

    fun release() {
        glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        pool.releaseAll()
    }

    data class Source(val textureId: Int, val isOes: Boolean, val mediaW: Int, val mediaH: Int, val uv: FloatArray)

    companion object {
        val IDENTITY_UV = floatArrayOf(1f, 1f, 0f, 0f)
        val FLIP_V = floatArrayOf(1f, -1f, 0f, 1f)
        private val FS_NDC = floatArrayOf(2f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, -1f, 0f, 1f)
        fun compToNdc(w: Int, h: Int) = floatArrayOf(2f / w, 0f, 0f, 0f, 0f, -2f / h, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 1f, 0f, 1f)
    }
}
