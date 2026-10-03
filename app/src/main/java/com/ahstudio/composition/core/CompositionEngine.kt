package com.ahstudio.composition.core

import android.opengl.GLES30.*
import android.view.Surface
import com.ahstudio.composition.cache.CacheInvalidator
import com.ahstudio.composition.gpu.*
import com.ahstudio.composition.graph.*
import com.ahstudio.composition.integration.*

/**
 * CompositionEngine — the ONE subsystem: Timeline State → Composition → LayerGraph →
 * Evaluation → Effects → Masks → Transforms → Blend → RenderGraph → Surface (Preview OR Export).
 * Preview & export call the SAME renderFrame path.
 */
class CompositionEngine(
    private val gpuEnv: GpuEnvironment,
    val timeSource: CompositionTimeSource,
    private val mediaBridge: LayerSourceBridge,
    private val textBridge: TextRasterizerBridge,
    private val imageBridge: ImageSourceBridge,
    private val effectsBridge: EffectsBridge,
) {
    private val compositions = LinkedHashMap<Long, CompositionGraph>()
    private var config: RenderConfig = RenderConfig(CompositionResolution.P1080, CompositionResolution.P1080)
    private var renderer: FrameRenderer? = null
    private var pool: GpuResourcePool? = null
    private var previewSurface: EglWindowSurface? = null
    private var encoderSurface: EglWindowSurface? = null
    private val activeStack = HashSet<Long>()
    private val profiler = CompositionProfiler()

    fun createComposition(id: CompositionId): CompositionGraph {
        val g = CompositionGraph(id)
        compositions[id.value] = g
        return g
    }
    fun graph(id: CompositionId): CompositionGraph? = compositions[id.value]
    fun setConfig(c: RenderConfig) { config = c }

    fun attachPreview(surface: Surface) = gpuEnv.runOnGlThread(await = true) { attachInternal(surface) { previewSurface = it } }
    fun attachEncoder(surface: Surface) = gpuEnv.runOnGlThread(await = true) { attachInternal(surface) { encoderSurface = it } }
    fun detachPreview() = gpuEnv.runOnGlThread(await = true) { previewSurface?.release(env.display); previewSurface = null }
    fun detachEncoder() = gpuEnv.runOnGlThread(await = true) { encoderSurface?.release(env.display); encoderSurface = null }

    private fun attachInternal(surface: Surface, setter: (EglWindowSurface) -> Unit) {
        ensureRenderer()
        setter(EglWindowSurface(env.display, env.config, env.context, surface))
    }

    fun renderFrame(timeUs: Long = timeSource.currentTimeUs, toEncoder: Boolean = false): CompositionResult {
        val primary = compositions.entries.firstOrNull() ?: return CompositionResult.Failed(timeUs, listOf())
        return renderComposition(primary.value.compositionId, timeUs, toEncoder)
    }

    fun renderComposition(compId: CompositionId, timeUs: Long, toEncoder: Boolean = false): CompositionResult {
        return gpuEnv.runOnGlThread(await = true) {
            val graph = compositions[compId.value] ?: return@runOnGlThread CompositionResult.Failed(timeUs, listOf())
            val r = renderer ?: return@runOnGlThread CompositionResult.Failed(timeUs, listOf())
            val surface = (if (toEncoder) encoderSurface else previewSurface)
                ?: return@runOnGlThread CompositionResult.Failed(timeUs, listOf())
            surface.makeCurrent(env.display, env.context)
            val w = config.output.width; val h = config.output.height
            glViewport(0, 0, w, h)

            val outTex = renderCompToTexture(compId, timeUs, config, depth = 0)
            if (outTex == null) return@runOnGlThread CompositionResult.Failed(timeUs, listOf(CompositionError.CircularMatte(listOf(LayerId(-1)))))

            presentToCurrentSurface(r, outTex, w, h)
            surface.swap(env.display)

            val stats = profiler.snapshot(config.comp, config.output)
            if (lastSkipped.isNotEmpty()) CompositionResult.Partial(timeUs, lastSkipped.toList(), stats)
            else CompositionResult.Ok(timeUs, lastWasCached, stats)
        }
    }

    internal fun renderCompToTexture(compId: CompositionId, timeUs: Long, cfg: RenderConfig, depth: Int): Int? {
        if (depth > 8) return null
        if (!activeStack.add(compId.value)) return null
        try {
            val graph = compositions[compId.value] ?: return null
            val r = renderer ?: return null
            r.setParentLookup { pid -> graph.layer(LayerId(pid)) }
            val (tex, skipped) = r.renderFrame(
                GraphRef(graph, CacheInvalidator(graph)), timeUs, cfg
            ) { ref, childT, childCfg, d -> renderCompToTexture(ref, childT, childCfg, depth + 1) }
            lastSkipped.addAll(skipped)
            return tex
        } finally { activeStack.remove(compId.value) }
    }

    private var lastSkipped = ArrayList<SkippedLayer>()
    private var lastWasCached = false

    private fun ensureRenderer() {
        if (renderer != null) return
        val p = GpuResourcePool(config.maxGpuPoolBytes)
        pool = p
        renderer = FrameRenderer(p, ShaderCache(), TextureUploadCache(), profiler, mediaBridge, textBridge, imageBridge, effectsBridge)
    }

    private val env get() = (gpuEnv as DefaultGpuEnvironment).expose()

    fun diagnostics(): RenderStats = profiler.snapshot(config.comp, config.output)

    fun release() = gpuEnv.runOnGlThread(await = true) {
        previewSurface?.release(env.display); encoderSurface?.release(env.display)
        previewSurface = null; encoderSurface = null
        renderer?.release(); renderer = null; pool = null
    }
}

class GraphRef(val graph: CompositionGraph, val invalidator: CacheInvalidator) {
    val id: CompositionId get() = graph.compositionId
}

fun presentToCurrentSurface(r: FrameRenderer, outTex: Int, w: Int, h: Int) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
    glViewport(0, 0, w, h)
    r.present(outTex, w, h)
}
