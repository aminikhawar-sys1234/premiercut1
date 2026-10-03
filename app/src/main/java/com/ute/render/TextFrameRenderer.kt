package com.ute.render

import android.opengl.GLES30.*
import com.ute.animation.LayerAnimator
import com.ute.color.ColorEngine
import com.ute.core.Mat4
import com.ute.core.ResourceRegistry
import com.ute.core.Safe
import com.ute.core.Time
import com.ute.device.DeviceCapabilities
import com.ute.fonts.FontEngine
import com.ute.glyphs.GlyphAtlas
import com.ute.gpu.*
import com.ute.layout.TextLayout
import com.ute.layout.TextLayoutEngine
import com.ute.model.*
import com.ute.motion.MotionTextEngine
import com.ute.scene3d.LightRig
import com.ute.scene3d.TextCamera
import com.ute.scene3d.TextMaterial
import com.ute.shaping.PlatformHarfBuzzShaper
import com.ute.shaping.ShapingPipeline
import com.ute.text3d.ExtrusionBuilder
import com.ute.unicode.BidiEngine

/**
 * THE single render path. Preview, thumbnails, and export all call
 * [renderFrame] — same shaping, same layout, same GPU pipeline, same
 * timestamp math. Parity is structural, not aspirational.
 */
class TextFrameRenderer(
    val fonts: FontEngine,
    capabilities: DeviceCapabilities,
    private val registry: ResourceRegistry,
) {
    private val shaping = ShapingPipeline(PlatformHarfBuzzShaper(), fonts)
    private val bidi = BidiEngine()
    private val layoutEngine = TextLayoutEngine(shaping, bidi)
    private val motionEngine = MotionTextEngine()
    private val colors = ColorEngine()
    private val atlas = GlyphAtlas(registry, sizePx = capabilities.atlasSize)
    private val textRenderer = GlTextRenderer(atlas, capabilities)
    private val meshRenderer = GlMeshRenderer()
    private val frameBuilder = FrameBuilder(fonts, colors).apply {
        renderSessionAtlasRect = { glyph ->
            atlas.getOrRasterize(glyph.font, glyph.clusterText, sdf = true)?.let { e ->
                floatArrayOf(e.u, e.v, e.uw, e.vh)
            }
        }
    }
    private var postChain: GlPostChain? = null
    private val layoutCache = HashMap<Long, TextLayout>()
    private val animators = HashMap<String, LayerAnimator>()

    private fun layoutKey(layer: TextLayer): Long {
        var h = 1125899906842597L
        for (s in listOf(layer.content, layer.style.toString(), layer.layout.toString(),
            layer.language, layer.direction.name)) h = 31 * h + s.hashCode()
        return h
    }

    private fun getLayout(layer: TextLayer): TextLayout =
        layoutCache.getOrPut(layoutKey(layer)) { layoutEngine.layout(layer.content, layer.style, layer.layout, fonts) }

    private fun getAnimator(layer: TextLayer): LayerAnimator =
        animators.getOrPut(layer.id) { LayerAnimator(layer.tracks) }

    /**
     * Render the document at [timeUsMicros] (composition time). No wall clock.
     */
    fun renderFrame(document: TextDocument, timeUsMicros: Long, target: RenderTarget) = Safe.critical("renderFrame", Unit) {
        val tSec = Time.toSeconds(timeUsMicros)
        atlas.beginFrame(timeUsMicros)

        glViewport(0, 0, target.width, target.height)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        val vp2d = Mat4.ortho(0f, target.width.toFloat(), target.height.toFloat(), 0f, -1f, 1f)

        val layers = document.layersVisibleAt(tSec).sortedBy { document.layers.indexOf(it) }
        for (layer in layers) {
            val local = layer.timing.local(tSec)
            val animator = getAnimator(layer)
            val state = animator.evaluate(layer, local)

            val layout = getLayout(layer)
            val motions = motionEngine.evaluate(layer.motion, layout, local)

            if (layer.text3d != null && layer.text3d.extrusionDepthPx > 0f) {
                render3DLayer(layer, layout, state, tSec, target)
            } else {
                val rd = frameBuilder.build2D(layout, layer, state, motions, tSec, target.width, target.height)
                if (rd.needsBloom) {
                    val chain = postChain ?: GlPostChain(target.width, target.height).also { postChain = it }
                    chain.ensure()
                    textRenderer.drawLayer(rd.instances, rd.instanceCount, vp2d, rd.layerMatrix, rd.uniforms)
                } else {
                    textRenderer.drawLayer(rd.instances, rd.instanceCount, vp2d, rd.layerMatrix, rd.uniforms)
                }
            }
        }
    }

    private fun render3DLayer(
        layer: TextLayer, layout: TextLayout,
        state: com.ute.animation.ResolvedLayerState, tSec: Double, target: RenderTarget,
    ) = Safe.critical("render3D", Unit) {
        val cfg = layer.text3d ?: return@critical
        val material = TextMaterial.PRESETS[cfg.materialId] ?: TextMaterial("fallback")
        val depth = state.extrusionDepthPx ?: cfg.extrusionDepthPx
        val cam = TextCamera(z = (target.height * 1.4f))
        val viewProj = cam.viewProj(target.width.toFloat(), target.height.toFloat())

        val paint = (fonts.find(layer.style.fontFamily) ?: fonts.systemDefault())
            .newPaint(state.fontSizePx)

        for (line in layout.lines) {
            val path = android.graphics.Path()
            for (g in line.glyphs) {
                paint.getTextPath(g.clusterText, 0, g.clusterText.length, g.x, -line.baselineY, path)
            }
            val mesh = ExtrusionBuilder().build(path, depth, cfg.bevelWidthPx, cfg.bevelSteps)
            val model = Mat4.multiply(
                Mat4.trs2d(state.x, state.y, state.rotationDeg, state.scaleX, state.scaleY),
                Mat4.translation(0f, target.height / 2f, 0f)
            )
            meshRenderer.drawMesh(
                mesh, model, viewProj, material,
                LightRig(listOf(
                    com.ute.scene3d.Light.Ambient(),
                    com.ute.scene3d.Light.Directional(intensity = 0.9f),
                    com.ute.scene3d.Light.Rim()
                )),
                floatArrayOf(cam.x, cam.y, cam.z),
                gradientEnabled = layer.appearance.fill is PaintSpec.Gradient
            )
        }
    }

    fun onContextLost() {
        registry.onContextLost()
        layoutCache.clear()
        postChain = null
    }

    fun onContextRestored() { registry.onContextRestored() }

    fun renderTextPreview(document: TextDocument, timeUsMicros: Long, eglSurface: EGLSurfaceProxy) =
        renderFrame(document, timeUsMicros, RenderTarget.Surface(eglSurface.width, eglSurface.height))

    fun renderTextFrame(document: TextDocument, timeUsMicros: Long, w: Int, h: Int) =
        renderFrame(document, timeUsMicros, RenderTarget.Offscreen(w, h))

    fun renderTextExport(document: TextDocument, timeUsMicros: Long, w: Int, h: Int) =
        renderFrame(document, timeUsMicros, RenderTarget.Surface(w, h))
}
