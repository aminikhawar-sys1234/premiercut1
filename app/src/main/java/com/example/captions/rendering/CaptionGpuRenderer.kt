package com.ahstudio.captions.rendering

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CaptionGpuRenderer(private val atlas: GlyphAtlas = GlyphAtlas()) {

    private val vertexSrc = """
        uniform mat4 uMVP;
        attribute vec4 aPosUV;
        attribute vec4 aColor;
        varying vec2 vUV; varying vec4 vColor;
        void main() { vUV = aPosUV.zw; vColor = aColor; gl_Position = uMVP * vec4(aPosUV.xy, 0.0, 1.0); }
    """.trimIndent()
    private val fragSrc = """
        precision mediump float;
        uniform sampler2D uTex; varying vec2 vUV; varying vec4 vColor;
        void main() { gl_FragColor = vColor * texture2D(uTex, vUV); }
    """.trimIndent()

    private var program = 0
    private var uMvp = 0; var uTexLoc = 0
    private var aPosUV = 0; var aColor = 0
    private val mvp = FloatArray(16)
    private val proj = FloatArray(16)
    private val model = FloatArray(16)

    private var whiteTex = 0

    fun ensureInitialized() {
        if (program != 0) return
        program = buildProgram(vertexSrc, fragSrc)
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uTexLoc = GLES20.glGetUniformLocation(program, "uTex")
        aPosUV = GLES20.glGetAttribLocation(program, "aPosUV")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0)
        whiteTex = t[0]
        val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        px.put(255.toByte()); px.put(255.toByte()); px.put(255.toByte()); px.put(255.toByte())
        px.rewind()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, whiteTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun drawFrame(renderables: List<RenderableCaption>, canvasW: Float, canvasH: Float, paintFactory: (style: com.ahstudio.captions.core.model.CaptionStyle) -> android.graphics.Paint) {
        ensureInitialized()
        GLES20.glViewport(0, 0, canvasW.toInt(), canvasH.toInt())
        Matrix.orthoM(proj, 0, 0f, canvasW, canvasH, 0f, -1f, 1f)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(uTexLoc, 0)

        for (rc in renderables) {
            val paint = paintFactory(rc.style)
            val fontKey = "${rc.style.id}|${paint.textSize}|${rc.style.bold}|${rc.style.italic}"
            val density = paint.textSize / rc.style.fontSizeSp
            Matrix.setIdentityM(model, 0)
            val cx = rc.layout.anchorXPx + rc.layout.textBounds.width() / 2f
            val cy = rc.layout.anchorYPx + rc.layout.textBounds.height() / 2f
            Matrix.translateM(model, 0, cx, cy, 0f)
            Matrix.rotateM(model, 0, rc.rotationDegrees, 0f, 0f, 1f)
            Matrix.scaleM(model, 0, rc.scale * rc.animation.scaleX, rc.scale * rc.animation.scaleY, 1f)
            Matrix.translateM(model, 0, -cx, -cy, 0f)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

            val globalAlpha = rc.style.opacity * rc.animation.alpha

            rc.layout.backgroundRect?.let { bg ->
                drawSolidQuad(bg, rc.style.background!!.colorArgb.toInt(),
                    (globalAlpha * rc.style.background.opacity).coerceIn(0f, 1f), rc.style.cornerRadiusDp * density)
            }

            val layout = rc.layout.staticLayout
            for (line in 0 until rc.layout.lineCount) {
                val lineStart = layout.getLineStart(line)
                val lineEnd = layout.getLineEnd(line)
                val lineText = layout.text.substring(lineStart, lineEnd)
                val lineLeft = layout.getLineLeft(line) + rc.layout.anchorXPx
                val lineRight = layout.getLineRight(line) + rc.layout.anchorXPx
                val baseline = layout.getLineBaseline(line) + rc.layout.anchorYPx
                val isRtl = com.ahstudio.captions.core.language.BidiTextEngine
                    .baseDirection(lineText) == com.ahstudio.captions.core.language.TextDirection.RTL
                val lineWords = rc.clip.lines.getOrNull(line)?.words ?: continue
                val placed = WordVisualPlacer.place(lineText, lineWords.map { it.text },
                    rangesForLine(rc, line), isRtl, paint, lineLeft, lineRight)

                for (pw in placed) {
                    val active = rc.highlight != null && pw.logicalIndex == rc.highlight.activeWordIndex
                    val fill = if (active) 0xFFE8FF00.toInt() else rc.style.colorArgb.toInt()
                    if (rc.style.shadowRadiusDp > 0f) {
                        atlas.getOrRasterize(lineWords[pw.logicalIndex].text, fontKey, GlyphAtlas.Variant.SHADOW, paint)?.let {
                            drawWord(it, pw.x, baseline, rc.style.shadowColorArgb.toInt(), globalAlpha * 0.6f)
                        }
                    }
                    if (rc.style.strokeWidthDp > 0f) {
                        atlas.getOrRasterize(lineWords[pw.logicalIndex].text, fontKey, GlyphAtlas.Variant.STROKE, paint)?.let {
                            drawWord(it, pw.x, baseline, rc.style.strokeColorArgb.toInt(), globalAlpha)
                        }
                    }
                    atlas.getOrRasterize(lineWords[pw.logicalIndex].text, fontKey, GlyphAtlas.Variant.FILL, paint)?.let { g ->
                        drawWord(g, pw.x, baseline, fill, globalAlpha)
                        rc.karaokeProgress?.let { progress ->
                            if (active && progress > 0f) {
                                val fw = (g.widthPx * progress).toInt().coerceAtLeast(1)
                                val top = (baseline - g.ascentPx).toInt()
                                val hgt = g.heightPx.toInt().coerceAtLeast(1)
                                val sy = canvasH.toInt() - (top + hgt)
                                GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                                GLES20.glScissor(pw.x.toInt(), sy, fw, hgt)
                                drawWord(g, pw.x, baseline, 0xFFE8FF00.toInt(), globalAlpha)
                                GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun rangesForLine(rc: RenderableCaption, line: Int): List<IntRange> {
        var idx = 0
        for (l in 0 until line) idx += rc.clip.lines.getOrNull(l)?.words?.size ?: 0
        val count = rc.clip.lines.getOrNull(line)?.words?.size ?: 0
        return (idx until (idx + count).coerceAtMost(rc.layout.wordCharRanges.size)).map { rc.layout.wordCharRanges[it] }
    }

    private fun drawWord(g: GlyphAtlas.WordGlyph, x: Float, baseline: Float, color: Int, alpha: Float) {
        val top = baseline - g.ascentPx
        val quad = floatArrayOf(
            x, top, g.u0, g.v0,
            x + g.widthPx, top, g.u1, g.v0,
            x, top + g.heightPx, g.u0, g.v1,
            x + g.widthPx, top + g.heightPx, g.u1, g.v1,
        )
        val rgba = floatArrayOf(
            ((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f, alpha,
        )
        val colors = FloatArray(16) { rgba[it % 4] }
        drawQuad(quad, colors, g.textureName)
    }

    private fun drawSolidQuad(r: android.graphics.RectF, color: Int, alpha: Float, cornerPx: Float) {
        val quad = floatArrayOf(r.left, r.top, 0f, 0f, r.right, r.top, 1f, 0f, r.left, r.bottom, 0f, 1f, r.right, r.bottom, 1f, 1f)
        val rgba = floatArrayOf(((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f, alpha)
        drawQuad(quad, FloatArray(16) { rgba[it % 4] }, whiteTex)
    }

    private fun drawQuad(quad: FloatArray, colors: FloatArray, tex: Int) {
        val vb = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(quad); position(0) }
        val cb = ByteBuffer.allocateDirect(colors.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(colors); position(0) }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glEnableVertexAttribArray(aPosUV)
        GLES20.glVertexAttribPointer(aPosUV, 4, GLES20.GL_FLOAT, false, 0, vb)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 0, cb)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosUV)
        GLES20.glDisableVertexAttribArray(aColor)
    }

    fun release() {
        atlas.release()
        if (program != 0) { GLES20.glDeleteProgram(program); program = 0 }
    }

    private fun buildProgram(v: String, f: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, v)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, f)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        val status = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "caption shader link failed" }
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        val st = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
        check(st[0] == GLES20.GL_TRUE) { "caption shader compile failed: ${GLES20.glGetShaderInfoLog(s)}" }
        return s
    }
}
