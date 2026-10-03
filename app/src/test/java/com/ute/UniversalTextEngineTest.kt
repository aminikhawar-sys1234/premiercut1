package com.ute

import androidx.test.core.app.ApplicationProvider
import com.ute.animation.AnimatableProperty
import com.ute.animation.Easing
import com.ute.animation.KeyframeSpec
import com.ute.animation.Track
import com.ute.color.ColorMath
import com.ute.core.TimeRange
import com.ute.core.Vec2
import com.ute.model.*
import com.ute.motion.MotionPreset
import com.ute.motion.MotionSpec
import com.ute.motion.MotionTextEngine
import com.ute.motion.MotionUnit
import com.ute.serialization.TextDocumentCodec
import com.ute.style.ReusableStyle
import com.ute.style.TextStyleEngine
import com.ute.text3d.Triangulator
import com.ute.unicode.BidiEngine
import com.ute.unicode.Script
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UniversalTextEngineTest {

    @Test
    fun testBidiAndScriptDetection() {
        val bidi = BidiEngine()
        assertEquals(Script.ARABIC, com.ute.unicode.ScriptDetector.detect("مرحبا بك"))
        assertEquals(Script.LATIN, com.ute.unicode.ScriptDetector.detect("Hello World"))
        assertEquals(Script.DEVANAGARI, com.ute.unicode.ScriptDetector.detect("नमस्ते दुनिया"))

        assertEquals(Direction.RTL, bidi.resolveDirection(Direction.AUTO, "مرحبا"))
        assertEquals(Direction.LTR, bidi.resolveDirection(Direction.AUTO, "Hello"))
    }

    @Test
    fun testEasingCurves() {
        val linear = Easing.Linear
        assertEquals(0.5f, linear.transform(0.5f), 0.001f)

        val bezier = Easing.EaseInOut
        assertEquals(0f, bezier.transform(0f), 0.001f)
        assertEquals(1f, bezier.transform(1f), 0.001f)
        assertTrue(bezier.transform(0.5f) in 0.4f..0.6f)

        val spring = Easing.Spring(zeta = 0.35f, omega = 12f)
        assertEquals(0f, spring.transform(0f), 0.001f)
    }

    @Test
    fun testTrackEvaluation() {
        val track = Track(listOf(
            KeyframeSpec(0.0, 0f, Easing.Linear),
            KeyframeSpec(1.0, 100f, Easing.Linear),
            KeyframeSpec(2.0, 200f, Easing.Linear)
        ))
        assertEquals(0f, track.evaluate(-0.5), 0.01f)
        assertEquals(50f, track.evaluate(0.5), 0.01f)
        assertEquals(100f, track.evaluate(1.0), 0.01f)
        assertEquals(150f, track.evaluate(1.5), 0.01f)
        assertEquals(200f, track.evaluate(2.5), 0.01f)
    }

    @Test
    fun testColorMathOklab() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()

        val mixed = ColorMath.mixOklab(red, blue, 0.5f)
        assertNotEquals(0, mixed)

        val contrast = ColorMath.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        assertTrue(contrast > 20f)
    }

    @Test
    fun testTriangulation() {
        val quad = listOf(
            Vec2(0f, 0f),
            Vec2(10f, 0f),
            Vec2(10f, 10f),
            Vec2(0f, 10f)
        )
        val indices = Triangulator.triangulate(quad, emptyList())
        assertEquals(6, indices.size) // 2 triangles = 6 indices
    }

    @Test
    fun testDocumentSerializationRoundTrip() {
        val layer = TextLayer(
            id = "layer-1",
            content = "Universal Text Engine",
            style = TextStyle(fontFamily = "sans-serif-black", sizePx = 72f, weight = FontWeight.BLACK),
            appearance = Appearance(
                fill = PaintSpec.Solid(0xFF00E5FF.toInt()),
                outline = OutlineSpec(color = 0xFF000000.toInt(), widthPx = 4f),
                shadow = ShadowSpec(distancePx = 10f, softnessPx = 5f)
            ),
            motion = MotionSpec(preset = MotionPreset.POP, unit = MotionUnit.CHARACTER, durationSec = 0.8),
            timing = TimeRange(0.0, 5.0)
        )
        val doc = TextDocument(
            id = "doc-test",
            layers = listOf(layer),
            durationSec = 5.0,
            canvasWidthPx = 1920,
            canvasHeightPx = 1080
        )

        val json = TextDocumentCodec.encode(doc)
        val decoded = TextDocumentCodec.decode(json)

        assertEquals(doc.id, decoded.id)
        assertEquals(doc.layers.size, decoded.layers.size)
        val decLayer = decoded.layers[0]
        assertEquals("layer-1", decLayer.id)
        assertEquals("Universal Text Engine", decLayer.content)
        assertEquals(72f, decLayer.style.sizePx, 0.01f)
        assertEquals(MotionPreset.POP, decLayer.motion?.preset)
    }

    @Test
    fun testStyleEnginePresets() {
        val engine = TextStyleEngine()
        val presets = engine.presets()
        assertTrue(presets.isNotEmpty())

        val base = TextStyle(fontFamily = "sans-serif", sizePx = 40f)
        val composed = engine.compose(base, TextStyle(sizePx = 80f))
        assertEquals(80f, composed.sizePx, 0.01f)
    }
}
