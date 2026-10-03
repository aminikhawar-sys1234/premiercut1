package com.ahstudio.screeneditor.transform

import com.ahstudio.screeneditor.viewport.ScreenEditorViewport
import kotlin.math.abs

class TransformSnapping(private val viewport: ScreenEditorViewport) {

    data class Result(
        val corrected: Transform2D,
        val activeGuidesX: List<Float>,
        val activeGuidesY: List<Float>
    )

    fun snap(
        t: Transform2D,
        srcW: Float,
        srcH: Float,
        crop: CropRect,
        enabled: Boolean,
        thresholdEditorPx: Float = 12f
    ): Result {
        if (!enabled) return Result(t, emptyList(), emptyList())
        val ew = srcW * crop.width * t.scaleX
        val eh = srcH * crop.height * t.scaleY
        val left = t.translationX - ew * t.anchorX
        val right = left + ew
        val top = t.translationY - eh * t.anchorY
        val bottom = top + eh
        val cx = left + ew / 2f
        val cy = top + eh / 2f
        val w = viewport.editorWidth
        val h = viewport.editorHeight

        val guideXs = listOf(0f, w / 2f, w)
        val guideYs = listOf(0f, h / 2f, h)

        var bestX: Pair<Float, Float>? = null // (guide, delta)
        for (g in guideXs) {
            for (p in listOf(left, cx, right)) {
                val d = g - p
                if (abs(d) <= thresholdEditorPx && (bestX == null || abs(d) < abs(bestX!!.second))) {
                    bestX = g to d
                }
            }
        }

        var bestY: Pair<Float, Float>? = null
        for (g in guideYs) {
            for (p in listOf(top, cy, bottom)) {
                val d = g - p
                if (abs(d) <= thresholdEditorPx && (bestY == null || abs(d) < abs(bestY!!.second))) {
                    bestY = g to d
                }
            }
        }

        val nx = t.translationX + (bestX?.second ?: 0f)
        val ny = t.translationY + (bestY?.second ?: 0f)
        val ax = bestX?.first
        val ay = bestY?.first

        val gx = listOfNotNull(ax)
        val gy = listOfNotNull(ay)

        return Result(t.copy(translationX = nx, translationY = ny), gx, gy)
    }
}
