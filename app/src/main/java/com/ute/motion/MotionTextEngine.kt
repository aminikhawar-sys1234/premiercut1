package com.ute.motion

import com.ute.core.MathUtil
import com.ute.layout.TextLayout
import kotlin.math.*

/**
 * Deterministic kinetic typography. Everything is computed from the layer-local
 * timestamp — the same timestamp in preview and export yields byte-identical
 * frames. "Random" effects use a seeded hash of (seed, unitIndex), never
 * kotlin.random's global state.
 */
class MotionTextEngine {

    fun evaluate(spec: MotionSpec?, layout: TextLayout, tLocal: Double): List<GlyphMotion> {
        val totalGlyphs = layout.lines.sumOf { it.glyphs.size }
        if (spec == null || spec.preset == MotionPreset.NONE || layout.isEmpty || totalGlyphs == 0) {
            return (0 until totalGlyphs).map { GlyphMotion() }
        }

        val units = groupUnits(layout, spec.unit)
        val states = HashMap<Int, GlyphMotion>()

        val lineOf = IntArray(totalGlyphs)
        val xOf = FloatArray(totalGlyphs)
        var gi = 0
        layout.lines.forEachIndexed { li, line ->
            line.glyphs.forEach { g -> lineOf[gi] = li; xOf[gi] = g.x; gi++ }
        }
        val maxX = xOf.maxOrNull() ?: 0f
        val maxLine = (lineOf.maxOrNull() ?: 0).coerceAtLeast(1)

        for ((ui, unit) in units.withIndex()) {
            val unitT = tLocal - ui * spec.staggerSec
            val progress = unitProgress(spec, unitT)
            for (idx in unit) {
                states[idx] = computeGlyph(
                    spec, progress, idx,
                    xOf.getOrElse(idx) { 0f } / maxX.coerceAtLeast(1f),
                    lineOf.getOrElse(idx) { 0 }.toFloat() / maxLine,
                    unitT
                )
            }
        }

        return (0 until totalGlyphs).map { states[it] ?: GlyphMotion() }
    }

    private fun unitProgress(spec: MotionSpec, unitT: Double): Float {
        val p = (unitT / spec.durationSec).coerceIn(0.0, 1.0).toFloat()
        return MathUtil.sanitize(spec.easing.transform(p), p)
    }

    private fun groupUnits(layout: TextLayout, unit: MotionUnit): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        var flat = 0
        when (unit) {
            MotionUnit.DOCUMENT, MotionUnit.PARAGRAPH -> {
                out.add((0 until layout.lines.sumOf { it.glyphs.size }).toList())
            }
            MotionUnit.LINE -> {
                layout.lines.forEach { l ->
                    out.add((flat until flat + l.glyphs.size).toList())
                    flat += l.glyphs.size
                }
            }
            MotionUnit.WORD, MotionUnit.CHARACTER, MotionUnit.GLYPH -> {
                var current = ArrayList<Int>()
                layout.lines.forEach { line ->
                    line.glyphs.forEach { g ->
                        val isSpace = g.clusterText.isBlank()
                        if (unit == MotionUnit.WORD && isSpace && current.isNotEmpty()) {
                            out.add(current)
                            current = ArrayList()
                        }
                        current.add(flat++)
                    }
                }
                if (current.isNotEmpty()) out.add(current)
            }
        }
        return out
    }

    private fun computeGlyph(
        spec: MotionSpec, p: Float, glyphIndex: Int,
        normX: Float, normY: Float, unitT: Double,
    ): GlyphMotion {
        return when (spec.preset) {
            MotionPreset.TYPEWRITER -> GlyphMotion(opacity = if (p > 0.02f) 1f else 0f, visible = p > 0.02f)
            MotionPreset.POP -> GlyphMotion(
                scaleX = overshoot(p, 1.18f), scaleY = overshoot(p, 1.18f),
                opacity = p.coerceIn(0f, 1f)
            )
            MotionPreset.BOUNCE -> GlyphMotion(
                dy = -(1f - bounce(p)) * spec.distancePx,
                opacity = p.coerceIn(0f, 1f)
            )
            MotionPreset.ELASTIC -> GlyphMotion(
                scaleX = elastic(p), scaleY = elastic(p), opacity = p.coerceIn(0f, 1f)
            )
            MotionPreset.SLIDE_IN, MotionPreset.DIRECTIONAL_REVEAL -> {
                val (dx, dy) = directionOffset(spec.direction, (1f - p) * spec.distancePx)
                GlyphMotion(dx = dx, dy = dy, opacity = p.coerceIn(0f, 1f))
            }
            MotionPreset.ZOOM_IN -> GlyphMotion(scaleX = 0.3f + 0.7f * p, scaleY = 0.3f + 0.7f * p, opacity = p.coerceIn(0f, 1f))
            MotionPreset.ZOOM_OUT -> GlyphMotion(scaleX = 1.6f - 0.6f * p, scaleY = 1.6f - 0.6f * p, opacity = p.coerceIn(0f, 1f))
            MotionPreset.ROTATE_IN -> GlyphMotion(rotationDeg = (1f - p) * 90f, scaleX = p, scaleY = p, opacity = p.coerceIn(0f, 1f))
            MotionPreset.WAVE -> GlyphMotion(
                dy = sin(normX * 6.28f * spec.distancePx.coerceAtMost(3f) + (unitT * spec.distancePx.coerceAtMost(4f)).toFloat()) * 8f * spec.distancePx.coerceAtMost(2f),
                opacity = 1f
            )
            MotionPreset.STAGGER_FADE -> GlyphMotion(opacity = p.coerceIn(0f, 1f))
            MotionPreset.CASCADE_UP -> GlyphMotion(dy = (1f - p) * spec.distancePx, opacity = p.coerceIn(0f, 1f))
            MotionPreset.CASCADE_DOWN -> GlyphMotion(dy = -(1f - p) * spec.distancePx, opacity = p.coerceIn(0f, 1f))
            MotionPreset.RANDOM_REVEAL -> {
                val h = hash01(spec.seed, glyphIndex)
                val local = ((unitT - h * spec.staggerSec * 8) / spec.durationSec).coerceIn(0.0, 1.0).toFloat()
                val pp = spec.easing.transform(local)
                GlyphMotion(opacity = pp, scaleX = 0.5f + 0.5f * pp, scaleY = 0.5f + 0.5f * pp)
            }
            MotionPreset.BLUR_REVEAL -> GlyphMotion(
                opacity = p.coerceIn(0f, 1f),
                blurPx = (1f - p) * 10f * spec.distancePx.coerceAtMost(2f)
            )
            MotionPreset.TRACKING_REVEAL -> GlyphMotion(
                extraTrackingPx = (1f - p) * spec.distancePx * 4f, opacity = p.coerceIn(0f, 1f)
            )
            MotionPreset.MASK_REVEAL -> GlyphMotion(
                scaleY = p, opacity = if (p <= 0f) 0f else 1f
            )
            MotionPreset.KINETIC_SCALE_PULSE -> {
                val phase = (unitT * 2.0).toFloat()
                val s = 1f + 0.08f * sin(phase + normX * 6.28f)
                GlyphMotion(scaleX = s, scaleY = s, opacity = 1f)
            }
            MotionPreset.GLITCH_IN -> {
                val h = hash01(spec.seed, glyphIndex)
                val glitch = if (p < 0.85f && hash01(spec.seed * 31, (unitT * 30).toInt()) > 0.7f) 1f else 0f
                GlyphMotion(dx = glitch * (h - 0.5f) * 24f, opacity = if (p > 0.02f) 1f else 0f)
            }
            MotionPreset.SLIDE_OUT -> {
                val (dx, dy) = directionOffset(spec.direction, p * spec.distancePx)
                GlyphMotion(dx = dx, dy = dy, opacity = 1f - p.coerceIn(0f, 1f))
            }
            MotionPreset.NONE -> GlyphMotion()
        }
    }

    private fun directionOffset(d: RevealDirection, dist: Float): Pair<Float, Float> = when (d) {
        RevealDirection.LTR -> dist to 0f
        RevealDirection.RTL -> -dist to 0f
        RevealDirection.TTB -> 0f to -dist
        RevealDirection.BTT -> 0f to dist
        RevealDirection.CENTER_OUT -> 0f to 0f
    }

    private fun overshoot(p: Float, max: Float) = 1f + (max - 1f) * sin(p * Math.PI.toFloat()).let { if (p >= 1f) 0f else it }

    private fun bounce(t: Float): Float {
        if (t >= 1f) return 1f
        val n1 = 7.5625f; val d1 = 2.75f
        return when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> { val x = t - 1.5f / d1; n1 * x * x + 0.75f }
            t < 2.5f / d1 -> { val x = t - 2.25f / d1; n1 * x * x + 0.9375f }
            else -> { val x = t - 2.625f / d1; n1 * x * x + 0.984375f }
        }
    }

    private fun elastic(t: Float): Float {
        if (t <= 0f) return 0f; if (t >= 1f) return 1f
        val c4 = (2 * Math.PI / 3).toFloat()
        return Math.pow(2.0, (-10 * t).toDouble()).toFloat() *
               sin((t * 10f - 0.75f) * c4) + 1f
    }

    private fun hash01(seed: Int, i: Int): Float {
        var h = (seed * 0x9E3779B9.toInt()) xor (i * 0x85EBCA6B.toInt())
        h = h xor (h ushr 13); h *= 0xC2B2AE35.toInt(); h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
