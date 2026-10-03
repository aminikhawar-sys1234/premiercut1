package com.ute

import android.content.Context
import com.ute.animation.AnimatableProperty
import com.ute.animation.KeyframeSpec
import com.ute.color.ColorMatchingEngine
import com.ute.core.ResourceRegistry
import com.ute.core.TimeRange
import com.ute.device.DeviceCapabilities
import com.ute.export.MediaCodecExportAdapter
import com.ute.fonts.FontEngine
import com.ute.model.*
import com.ute.render.EglCore
import com.ute.render.RenderTarget
import com.ute.render.TextFrameRenderer
import com.ute.serialization.TextDocumentCodec
import com.ute.style.TextStyleEngine
import com.ute.templates.TemplateEngine
import org.json.JSONObject

/**
 * The engine facade. Owns all subsystems and their lifecycles. Must be used
 * from a single GL thread; document/style objects are immutable value types
 * and safe to touch from any thread.
 */
class UniversalTextEngine private constructor(
    val fonts: FontEngine,
    val templates: TemplateEngine,
    val styles: TextStyleEngine,
    val colorMatching: ColorMatchingEngine,
    private val frameRenderer: TextFrameRenderer,
    private val egl: EglCore,
    val capabilities: DeviceCapabilities,
    private val registry: ResourceRegistry,
) {

    class Builder(private val context: Context) {
        private var budgetBytes: Long = 96L * 1024 * 1024
        fun memoryBudget(bytes: Long) = apply { budgetBytes = bytes }
        fun build(): UniversalTextEngine {
            val registry = ResourceRegistry(budgetBytes)
            val fonts = FontEngine(context.applicationContext)
            val caps = DeviceCapabilities.detect()
            val egl = EglCore().apply { initialize() }
            return UniversalTextEngine(
                fonts = fonts,
                templates = TemplateEngine(context.applicationContext).apply { loadFromAssets() },
                styles = TextStyleEngine(),
                colorMatching = ColorMatchingEngine(),
                frameRenderer = TextFrameRenderer(fonts, caps, registry),
                egl = egl,
                capabilities = caps,
                registry = registry,
            )
        }
    }

    companion object {
        fun create(context: Context, configure: Builder.() -> Unit = {}) =
            Builder(context).apply(configure).build()
    }

    // ---------- text creation ----------

    fun createLayer(text: String, language: String = "en",
                    style: TextStyle = TextStyle(), timing: TimeRange = TimeRange(0.0, 5.0)): TextLayer =
        TextLayer(id = "layer-${System.nanoTime()}", content = text, language = language,
            style = style, timing = timing,
            direction = com.ute.unicode.BidiEngine().resolveDirection(Direction.AUTO, text))

    fun createDocument(layers: List<TextLayer>, durationSec: Double,
                       widthPx: Int, heightPx: Int): TextDocument =
        TextDocument("doc-${System.nanoTime()}", layers, durationSec, widthPx, heightPx)

    // ---------- keyframes ----------

    fun track(vararg keys: Pair<Double, Float>, easing: com.ute.animation.Easing = com.ute.animation.Easing.EaseInOut) =
        keys.map { KeyframeSpec(it.first, it.second, easing) }

    fun withTracks(layer: TextLayer, tracks: Map<AnimatableProperty, List<KeyframeSpec>>) =
        layer.copy(tracks = tracks)

    // ---------- rendering (one path for everything) ----------

    fun renderPreviewFrame(document: TextDocument, timeUsMicros: Long, surface: android.view.Surface, w: Int, h: Int) {
        val es = egl.createWindowSurface(surface)
        try {
            egl.makeCurrent(es)
            frameRenderer.renderFrame(document, timeUsMicros, RenderTarget.Surface(w, h))
            egl.swap(es)
        } finally {
            egl.destroySurface(es)
        }
    }

    fun renderTextFrame(document: TextDocument, timeUsMicros: Long, w: Int, h: Int) =
        frameRenderer.renderFrame(document, timeUsMicros, RenderTarget.Offscreen(w, h))

    fun renderTextExport(document: TextDocument, timeUsMicros: Long, w: Int, h: Int) =
        frameRenderer.renderTextExport(document, timeUsMicros, w, h)

    fun exporter(): MediaCodecExportAdapter = MediaCodecExportAdapter(frameRenderer, egl)

    // ---------- serialization ----------

    fun serialize(document: TextDocument): String = TextDocumentCodec.encode(document).toString(2)
    fun deserialize(json: String): TextDocument = TextDocumentCodec.decode(JSONObject(json))

    // ---------- resource / lifecycle ----------

    fun onGpuContextLost() = frameRenderer.onContextLost()
    fun onGpuContextRestored() = frameRenderer.onContextRestored()
    fun release() { registry.enforceBudget { }; egl.release() }
}
