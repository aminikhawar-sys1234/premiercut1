package com.ahstudio.captions.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

object CaptionCanvasRenderer {

    fun draw(canvas: Canvas, renderables: List<RenderableCaption>, density: Float) {
        for (rc in renderables) {
            val style = rc.style
            val save = canvas.save()
            try {
                val cx = rc.layout.anchorXPx + rc.layout.textBounds.width() / 2f
                val cy = rc.layout.anchorYPx + rc.layout.textBounds.height() / 2f
                canvas.rotate(rc.rotationDegrees, cx, cy)
                canvas.scale(rc.scale, rc.scale, cx, cy)

                rc.style.background?.let { bg ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = withAlpha(bg.colorArgb.toInt(), bg.opacity)
                    }
                    val r = style.cornerRadiusDp * density
                    canvas.drawRoundRect(rc.layout.backgroundRect!!, r, r, paint)
                }
                val alphaSave = canvas.saveLayerAlpha(null, (style.opacity * rc.animation.alpha * 255).toInt())
                canvas.translate(rc.layout.anchorXPx, rc.layout.anchorYPx)
                rc.layout.staticLayout.draw(canvas)

                rc.karaokeProgress?.let { progress ->
                    rc.highlight ?: return@let
                    drawKaraokeFill(canvas, rc, progress)
                }

                canvas.restoreToCount(alphaSave)
            } finally {
                canvas.restoreToCount(save)
            }
        }
    }

    private fun drawKaraokeFill(canvas: Canvas, rc: RenderableCaption, progress: Float) {
        val layout = rc.layout.staticLayout
        val paint = layout.paint
        var wordCounter = 0
        for (line in 0 until layout.lineCount) {
            val lineWords = rc.clip.lines.getOrNull(line)?.words ?: continue
            val inLine = rc.highlight!!.activeWordIndex - wordCounter
            wordCounter += lineWords.size
            if (inLine < 0 || inLine >= lineWords.size) continue

            val lineStart = layout.getLineStart(line)
            val lineText = layout.text.substring(lineStart, layout.getLineEnd(line))
            val globalRange = rc.layout.wordCharRanges.getOrNull(rc.highlight.activeWordIndex) ?: return
            val prefixLen = (globalRange.first - lineStart).coerceIn(0, lineText.length)
            val prefixW = paint.measureText(lineText, 0, prefixLen)
            val wordW = paint.measureText(lineText, prefixLen, (prefixLen + lineWords[inLine].text.length).coerceAtMost(lineText.length))
            val isRtl = com.ahstudio.captions.core.language.BidiTextEngine
                .baseDirection(lineText) == com.ahstudio.captions.core.language.TextDirection.RTL

            val baseline = layout.getLineBaseline(line)
            val fm = paint.fontMetrics
            val top = baseline + fm.ascent; val bottom = baseline + fm.descent
            val fillW = wordW * progress.coerceIn(0f, 1f)
            val rect = if (!isRtl) {
                val x0 = layout.getLineLeft(line) + prefixW
                RectF(x0, top, x0 + fillW, bottom)
            } else {
                val xEnd = layout.getLineRight(line) - prefixW
                RectF(xEnd - fillW, top, xEnd, bottom)
            }
            val p = Paint().apply { color = 0x66E8FF00.toInt() }
            canvas.drawRect(rect, p)
            return
        }
    }

    private fun withAlpha(color: Int, opacity: Float): Int =
        Color.argb((Color.alpha(color) * opacity.coerceIn(0f, 1f)).toInt(),
            Color.red(color), Color.green(color), Color.blue(color))
}
