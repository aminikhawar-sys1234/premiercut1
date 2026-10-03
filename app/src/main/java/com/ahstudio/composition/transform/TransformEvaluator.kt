package com.ahstudio.composition.transform

import com.ahstudio.composition.graph.CompositionLayer
import com.ahstudio.composition.graph.Transform2D

object TransformEvaluator {
    fun evaluate(t: Transform2D, timeUs: Long, parent: Affine2 = Affine2.Identity): Affine2 {
        val ax = t.anchorX.valueAt(timeUs) { p, c, f -> p + (c - p) * f }
        val ay = t.anchorY.valueAt(timeUs) { p, c, f -> p + (c - p) * f }
        val sx = t.scaleX.valueAt(timeUs) { p, c, f -> p + (c - p) * f } * (if (t.flipH) -1f else 1f)
        val sy = t.scaleY.valueAt(timeUs) { p, c, f -> p + (c - p) * f } * (if (t.flipV) -1f else 1f)
        var m = Affine2.translation(-ax, -ay)
        m = Affine2.scale(sx, sy) * m
        m = Affine2.skewY(t.skewY.valueAt(timeUs) { p, c, f -> p + (c - p) * f }) *
            Affine2.skewX(t.skewX.valueAt(timeUs) { p, c, f -> p + (c - p) * f }) * m
        m = Affine2.rotationDeg(t.rotationDeg.valueAt(timeUs) { p, c, f -> p + (c - p) * f }) * m
        m = Affine2.translation(ax, ay) * m
        m = Affine2.translation(
            t.positionX.valueAt(timeUs) { p, c, f -> p + (c - p) * f },
            t.positionY.valueAt(timeUs) { p, c, f -> p + (c - p) * f }
        ) * m
        return parent * m
    }

    fun worldOf(
        layer: CompositionLayer,
        lookup: (Long) -> CompositionLayer?,
        timeUs: Long = 0L,
    ): Affine2 {
        var world = evaluate(layer.transform, timeUs)
        var cur = layer.parentId
        val seen = HashSet<Long>()
        while (cur != null && seen.add(cur.value)) {
            val p = lookup(cur.value) ?: break
            world = evaluate(p.transform, timeUs) * world
            cur = p.parentId
        }
        return world
    }
}

object FitTransform {
    fun contain(mediaW: Float, mediaH: Float, compW: Float, compH: Float): Affine2 {
        val s = minOf(compW / mediaW, compH / mediaH)
        return Affine2.translation((compW - mediaW * s) / 2f, (compH - mediaH * s) / 2f) * Affine2.scale(s, s)
    }
}
