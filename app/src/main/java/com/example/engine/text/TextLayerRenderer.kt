package com.example.engine.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.example.domain.model.TextClip
import com.example.domain.model.WordTiming
import com.example.util.FontManager
import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

data class EvaluatedTextState(
  val visibleText: String,
  val posX: Float,
  val posY: Float,
  val scale: Float,
  val rotation: Float,
  val opacity: Float,
  val rot3DX: Float = 0f,
  val rot3DY: Float = 0f,
  val rot3DZ: Float = 0f,
  val depth3D: Float = 0f,
  val activeWordIndex: Int = -1,
  val words: List<WordTiming> = emptyList()
)

data class RenderedTextBounds(
  val widthPx: Float,
  val heightPx: Float,
  val centerXPx: Float,
  val centerYPx: Float,
  val rotation: Float,
  val scale: Float,
  val opacity: Float
)

object TextLayerRenderer {

  fun evaluateAnimation(clip: TextClip, currentPosMs: Long): EvaluatedTextState {
    val relTime = (currentPosMs - clip.timelineStartMs).coerceAtLeast(0L)
    val animDur = clip.animDurationMs.coerceAtLeast(100L)
    val progress = (relTime.toFloat() / animDur).coerceIn(0f, 1f)

    val baseTransform = if (clip.keyframes.isNotEmpty()) {
      com.example.engine.KeyframeInterpolator.interpolate(clip, relTime)
    } else null

    var animAlpha = baseTransform?.opacity ?: clip.opacity
    var animScale = baseTransform?.scale ?: clip.scale
    var animPosX = baseTransform?.posX ?: clip.posX
    var animPosY = baseTransform?.posY ?: clip.posY
    var animRotation = baseTransform?.rotation ?: clip.rotation
    var visibleText = clip.text

    var rot3DX = 0f
    var rot3DY = 0f
    var rot3DZ = 0f
    var depth3D = if (clip.is3D) clip.depth3D else 0f

    // 1. If custom/generated keyframes are present, they drive the transform directly
    if (clip.keyframes.isNotEmpty()) {
      return EvaluatedTextState(
        visibleText = visibleText,
        posX = animPosX,
        posY = animPosY,
        scale = animScale.coerceAtLeast(0.01f),
        rotation = animRotation,
        opacity = animAlpha.coerceIn(0f, 1f),
        rot3DX = rot3DX,
        rot3DY = rot3DY,
        rot3DZ = rot3DZ,
        depth3D = depth3D
      )
    }

    // 2. Standard Preset In/Out/Loop animations when no keyframes are defined
    val animType = clip.animationType.lowercase().trim()
    if (animType.isEmpty() || animType == "none" || animType == "static") {
      animAlpha = clip.opacity
      animScale = clip.scale
      visibleText = clip.text
    } else if (progress >= 1.0f && !animType.contains("pulse") && !animType.contains("glow") && !animType.contains("neon") && !animType.contains("shake")) {
      animAlpha = clip.opacity
      animScale = clip.scale
      visibleText = clip.text
    } else {
      when (animType) {
        "fade", "fade in", "fade_in", "fadein" -> {
          animAlpha = (0.25f + 0.75f * progress).coerceIn(0f, 1f)
        }
        "fade out", "fade_out", "fadeout" -> {
          animAlpha = 1f - progress
        }
        "slide", "slide up", "slide_up", "slideup" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animPosY = clip.posY + (1f - easeOut) * 0.18f
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        "slide down", "slide_down", "slidedown" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animPosY = clip.posY - (1f - easeOut) * 0.18f
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        "slide left", "slide_left", "slideleft" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animPosX = clip.posX - (1f - easeOut) * 0.25f
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        "slide right", "slide_right", "slideright" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animPosX = clip.posX + (1f - easeOut) * 0.25f
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        "zoom", "zoom in", "zoom_in", "zoomin" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animScale = 0.6f + 0.4f * easeOut
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        "zoom out", "zoom_out", "zoomout" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animScale = 1.4f - 0.4f * easeOut
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        "scale", "scale in", "scale_in", "scalein" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animScale = 1.4f - 0.4f * easeOut
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        "scale out", "scale_out", "scaleout" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animScale = 1.0f - 0.7f * easeOut
          animAlpha = (1f - progress).coerceIn(0f, 1f)
        }
        "rotate out", "rotate_out", "rotateout" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animRotation = clip.rotation + easeOut * 60f
          animScale = 1.0f - 0.5f * easeOut
          animAlpha = 1f - progress
        }
        "pop", "pop in", "pop_in" -> {
          animScale = if (progress < 0.65f) {
            0.85f + (progress / 0.65f) * 0.35f
          } else {
            1.20f - ((progress - 0.65f) / 0.35f) * 0.20f
          }
          animAlpha = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
        }
        "bounce", "bounce in", "bounce_in" -> {
          val spring = 1f - cos(progress * PI.toFloat() * 2.8f) * exp(-progress * 3.8f)
          animScale = 0.8f + (spring * 0.2f).coerceIn(0f, 0.4f)
          animAlpha = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
        }
        "typewriter", "classic typewriter", "fast typewriter", "digital typewriter", "cursor type", "terminal text", "hacker type", "retro writer", "machine type" -> {
          val totalChars = clip.text.length
          val charCount = maxOf(1, (totalChars * progress).toInt().coerceIn(0, totalChars))
          val cursor = if (clip.animationType.contains("hacker") || clip.animationType.contains("terminal")) "_" else "▌"
          visibleText = if (charCount < totalChars) {
            clip.text.substring(0, charCount) + if ((relTime / 200) % 2 == 0L) cursor else ""
          } else {
            clip.text
          }
        }
        "reveal", "letter reveal", "reveal in", "character reveal" -> {
          val totalChars = clip.text.length
          val charCount = maxOf(1, (totalChars * (1f - (1f - progress) * (1f - progress))).toInt().coerceIn(0, totalChars))
          visibleText = clip.text.substring(0, charCount)
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animPosY = clip.posY + (1f - easeOut) * 0.08f
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        "wipe", "wipe in", "wipe_in" -> {
          val totalChars = clip.text.length
          val charCount = maxOf(1, (totalChars * progress).toInt().coerceIn(0, totalChars))
          visibleText = clip.text.substring(0, charCount)
          animAlpha = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
        }
        "glow", "glow pulse", "glow_pulse", "neon pulse", "soft glow", "golden glow", "white glow", "aurora text", "crystal glow" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          val entranceScale = 0.85f + 0.15f * easeOut
          val pulse = sin((relTime).toFloat() / 200f * PI.toFloat()) * 0.05f
          animAlpha = (0.5f + 0.5f * progress).coerceIn(0.5f, 1f)
          animScale = entranceScale + pulse
        }
        "elastic", "elastic motion", "elastic_motion", "elastic typography" -> {
          val spring = 1f - cos(progress * PI.toFloat() * 3.8f) * exp(-progress * 3.5f)
          animScale = 0.85f + (spring * 0.15f).coerceIn(0f, 0.45f)
          animAlpha = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
        }
        "word reveal", "word_reveal", "word rush", "dynamic words", "bounce words" -> {
          val words = clip.text.split(" ")
          if (words.size > 1) {
            val wordCount = (words.size * progress).toInt().coerceIn(1, words.size)
            visibleText = words.take(wordCount).joinToString(" ")
          }
          val bounce = if (progress >= 0.8f) sin((relTime).toFloat() / 150f) * 0.03f else 0f
          animScale = (0.7f + 0.3f * progress) + bounce
          animAlpha = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
        }
        "kinetic burst", "kinetic wave", "energy text", "horizontal rush", "letter explosion", "word cascade", "fast cut text", "dynamic split", "motion stack" -> {
          val t = relTime.toFloat() / 100f
          val pulse = sin(t * 3.5f) * 0.04f
          val shiftX = cos(t * 2.0f) * 0.015f
          animScale = (0.85f + 0.15f * progress) + pulse
          animPosX = clip.posX + shiftX
          animAlpha = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
        }
        "3d pop", "3d zoom", "3d flip", "3d rotate", "3d depth", "3d perspective", "3d spin", "3d impact", "3d floating" -> {
          val t = relTime.toFloat() / 180f
          val depthScale = 0.8f + 0.2f * sin(t * 2f)
          val rotFlip = sin(t * 1.5f) * 12f
          animScale = (0.8f + 0.2f * progress) * depthScale
          animRotation = clip.rotation + rotFlip
          animAlpha = (0.4f + 0.6f * progress).coerceIn(0.4f, 1f)
          rot3DY = sin(t * 1.5f) * 20f
          rot3DX = cos(t * 1.2f) * 10f
          depth3D = max(depth3D, 12f)
        }
        "neon", "neon flicker", "neon_flicker", "cyber neon", "electric text", "rgb glow", "cyberpunk text", "laser text", "digital glow" -> {
          val flickerCycle = ((relTime / 70L) % 5).toInt()
          animAlpha = if (progress < 0.6f && (flickerCycle == 0 || flickerCycle == 2)) 0.7f else 1.0f
          val subtlePulse = sin(relTime.toFloat() / 120f) * 0.03f
          animScale = 1.0f + subtlePulse
        }
        "glitch", "cyber glitch", "glitch in" -> {
          if (progress < 0.85f) {
            val t = relTime.toFloat() / 35f
            val glitchStep = ((relTime / 75L) % 3).toInt()
            if (glitchStep != 0) {
              animPosX += sin(t * 4.2f) * 0.022f
              animPosY += cos(t * 3.1f) * 0.012f
            }
          }
          animAlpha = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
        }
        "shake", "rumble" -> {
          val t = relTime.toFloat() / 40f
          val decay = (1f - progress * 0.6f).coerceIn(0.2f, 1f)
          animPosX += sin(t * 2.6f) * (0.016f * decay)
          animPosY += cos(t * 3.2f) * (0.016f * decay)
          animRotation += sin(t * 2.1f) * (2.8f * decay)
          animAlpha = 1.0f
        }
        "cinematic", "cinematic reveal", "movie title", "epic entrance", "film credits", "dramatic zoom", "wide letter reveal", "epic gold", "trailer title", "movie opener" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animAlpha = (0.4f + 0.6f * easeOut).coerceIn(0f, 1f)
          animScale = 0.90f + 0.10f * easeOut
          animPosY = clip.posY + (1f - easeOut) * 0.04f
        }
        "rotate", "rotate in", "rotate_in" -> {
          val easeOut = 1f - (1f - progress) * (1f - progress)
          animRotation = clip.rotation + (1f - easeOut) * -60f
          animScale = 0.7f + 0.3f * easeOut
          animAlpha = (0.3f + 0.7f * progress).coerceIn(0f, 1f)
        }
        else -> {
          animAlpha = 1.0f
          animScale = 1.0f
          visibleText = clip.text
        }
      }
    }

    // 2. Specialized 3D Animations
    val effective3DAnim = if (clip.animation3D != "None") clip.animation3D else ""
    when (effective3DAnim.lowercase().trim()) {
      "3d flip", "flip_3d", "flip" -> {
        val flipProgress = (relTime.toFloat() / 1500f)
        rot3DY = (flipProgress * 360f) % 360f
        depth3D = max(depth3D, 14f)
      }
      "3d spin", "spin_3d", "spin" -> {
        rot3DY = (relTime.toFloat() / 14f) % 360f
        rot3DX = sin(relTime.toFloat() / 300f) * 12f
        depth3D = max(depth3D, 14f)
      }
      "3d tilt", "tilt_3d", "tilt" -> {
        val t = relTime.toFloat() / 250f
        rot3DX = sin(t * 1.4f) * 22f
        rot3DY = cos(t * 1.8f) * 24f
        depth3D = max(depth3D, 16f)
      }
      "3d float", "float_3d", "floating" -> {
        val t = relTime.toFloat() / 320f
        rot3DX = sin(t * 1.1f) * 12f
        rot3DY = cos(t * 1.3f) * 14f
        animPosY += sin(t * 1.8f) * 0.035f
        animScale *= (1.0f + sin(t * 2f) * 0.04f)
        depth3D = max(depth3D, 12f)
      }
      "3d wave", "wave_3d", "wave" -> {
        val t = relTime.toFloat() / 180f
        rot3DX = sin(t * 2.0f) * 16f
        rot3DZ = sin(t * 1.5f) * 8f
        depth3D = max(depth3D, 10f) + sin(t * 2.5f) * 4f
      }
      "3d extrude", "extrude_3d", "extrude" -> {
        val t = relTime.toFloat() / 220f
        val pulse = (0.5f + 0.5f * sin(t * 2f))
        depth3D = (max(depth3D, 10f) * pulse).coerceAtLeast(2f)
        rot3DX = 12f * pulse
        rot3DY = 10f * pulse
      }
    }

    // Resolve words for word-level timing
    val resolvedWords = if (clip.words.isNotEmpty()) {
      clip.words
    } else {
      val tokens = clip.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
      if (tokens.isNotEmpty()) {
        val wordDur = clip.durationMs / tokens.size
        tokens.mapIndexed { idx, token ->
          WordTiming(
            word = token,
            startMs = idx * wordDur,
            durationMs = wordDur
          )
        }
      } else emptyList()
    }

    val activeIdx = resolvedWords.indexOfFirst { w ->
      relTime >= w.startMs && relTime < (w.startMs + w.durationMs)
    }.let {
      if (it == -1 && resolvedWords.isNotEmpty() && relTime >= clip.durationMs) {
        resolvedWords.lastIndex
      } else it
    }

    return EvaluatedTextState(
      visibleText = visibleText,
      posX = animPosX,
      posY = animPosY,
      scale = clip.scale * animScale,
      rotation = animRotation,
      opacity = (clip.opacity * animAlpha).coerceIn(0f, 1f),
      rot3DX = rot3DX,
      rot3DY = rot3DY,
      rot3DZ = rot3DZ,
      depth3D = depth3D,
      activeWordIndex = activeIdx,
      words = resolvedWords
    )
  }

  fun draw(
    canvas: Canvas,
    clip: TextClip,
    currentPosMs: Long,
    width: Int,
    height: Int,
    context: Context
  ) {
    if (width <= 0 || height <= 0 || clip.opacity <= 0f) return
    val state = evaluateAnimation(clip, currentPosMs)
    drawInternal(canvas, clip, state, width, height, context)
  }

  fun drawTemplatePreview(
    canvas: Canvas,
    clip: TextClip,
    previewLoopMs: Long,
    width: Int,
    height: Int,
    context: Context,
    overridePosY: Float? = null,
    speedMultiplier: Float = 1.0f
  ) {
    if (width <= 0 || height <= 0 || clip.opacity <= 0f) return
    val scaledMs = (previewLoopMs * speedMultiplier).toLong()
    val totalLoopDur = 2400L
    val loopTime = (scaledMs % totalLoopDur).coerceAtLeast(0L)
    val effectiveClip = if (overridePosY != null) {
      clip.copy(posX = 0f, posY = overridePosY)
    } else {
      clip.copy(posX = 0f, posY = 0f)
    }
    val state = evaluateAnimation(effectiveClip, loopTime)
    val loopFadeAlpha = if (loopTime > 2050L) {
      (1f - (loopTime - 2050L).toFloat() / 350f).coerceIn(0f, 1f)
    } else 1f
    val finalState = state.copy(opacity = state.opacity * loopFadeAlpha)
    drawInternal(canvas, effectiveClip, finalState, width, height, context)
  }

  private fun drawInternal(
    canvas: Canvas,
    clip: TextClip,
    state: EvaluatedTextState,
    width: Int,
    height: Int,
    context: Context
  ) {
    var rawText = if (clip.isAllCaps) state.visibleText.uppercase() else state.visibleText
    if (clip.isHidden || state.opacity <= 0f || rawText.isEmpty()) return

    // 1. Multi-Language Unicode Script Detection & Normalization
    val containsUrdu = rawText.any {
      it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' ||
          it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
    }

    val containsHindi = rawText.any {
      it in '\u0900'..'\u097F' || it in '\uA8E0'..'\uA8FF'
    }

    val containsChinese = rawText.any {
      it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' ||
          it in '\u3000'..'\u303F' || it in '\uF900'..'\uFAFF'
    }

    // NFC Canonical composition normalization fixes broken Devanagari matras and Arabic ligatures
    if (containsHindi || containsUrdu) {
      rawText = Normalizer.normalize(rawText, Normalizer.Form.NFC)
    }

    // 2. Reference scaling base (360 width standard)
    val scaleFactor = width.toFloat() / 360f
    val baseFontSize = clip.fontSizeSp * scaleFactor * state.scale

    val typeface = FontManager.loadTypeface(
      context = context,
      fontFamily = clip.fontFamily,
      customFontPath = clip.customFontPath,
      fontWeight = if (clip.subtitleStyle.equals("Bold", true)) 900 else clip.fontWeight,
      isItalic = clip.isItalic
    )

    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      this.typeface = typeface
      this.textSize = baseFontSize
      this.letterSpacing = if (containsUrdu) 0f else clip.letterSpacing / 10f
      this.color = clip.textColor.toInt()
      this.alpha = (state.opacity * 255).toInt().coerceIn(0, 255)
      this.isUnderlineText = clip.isUnderline
    }

    // Alignment setup: in RTL Urdu, default alignment aligns appropriately
    val layoutAlignment = when (clip.alignment.lowercase()) {
      "left" -> if (containsUrdu) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
      "right" -> if (containsUrdu) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
      else -> Layout.Alignment.ALIGN_CENTER
    }

    val centerX = (width / 2f) + (state.posX * width / 2f)
    val centerY = (height / 2f) + (state.posY * height / 2f)

    canvas.save()

    // 3. GPU 3D Perspective Projection via Android Graphics Camera
    val has3DRot = state.rot3DX != 0f || state.rot3DY != 0f || state.rot3DZ != 0f
    if (has3DRot) {
      val camera = Camera()
      val matrix = Matrix()
      camera.save()
      camera.rotateX(state.rot3DX)
      camera.rotateY(state.rot3DY)
      camera.rotateZ(state.rot3DZ)
      camera.getMatrix(matrix)
      camera.restore()
      matrix.preTranslate(-centerX, -centerY)
      matrix.postTranslate(centerX, centerY)
      canvas.concat(matrix)
    }

    canvas.translate(centerX, centerY)
    canvas.rotate(state.rotation)
    canvas.scale(state.scale, state.scale)

    // Compute line spacing and metrics
    val effectiveLineSpacing = when {
      containsUrdu -> maxOf(clip.lineSpacing, 1.35f)
      containsHindi -> maxOf(clip.lineSpacing, 1.25f)
      else -> clip.lineSpacing
    }

    // Max line width calculation for StaticLayout
    val lines = rawText.split("\n")
    var maxLineWidth = 0f
    lines.forEach { line ->
      val w = paint.measureText(line)
      if (w > maxLineWidth) maxLineWidth = w
    }
    // Safe minimum width for bounds
    val layoutWidth = max(maxLineWidth.toInt() + 16, 32)

    // Create StaticLayout for flawless BiDi Unicode script rendering
    val textDir = if (containsUrdu) TextDirectionHeuristics.ANYRTL_LTR else TextDirectionHeuristics.FIRSTSTRONG_LTR
    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val builder = StaticLayout.Builder.obtain(rawText, 0, rawText.length, paint, layoutWidth)
        .setAlignment(layoutAlignment)
        .setTextDirection(textDir)
        .setLineSpacing(0f, effectiveLineSpacing)
        .setIncludePad(true)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && containsChinese) {
        builder.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
      }
      builder.build()
    } else {
      @Suppress("DEPRECATION")
      StaticLayout(rawText, paint, layoutWidth, layoutAlignment, effectiveLineSpacing, 0f, true)
    }

    val totalTextWidth = layout.width.toFloat()
    val totalTextHeight = layout.height.toFloat()

    val padX = clip.bgPadding * scaleFactor
    val padY = (clip.bgPadding * 0.7f) * scaleFactor

    val bgLeft = -totalTextWidth / 2f - padX
    val bgTop = -totalTextHeight / 2f - padY
    val bgRight = totalTextWidth / 2f + padX
    val bgBottom = totalTextHeight / 2f + padY

    // 4. Draw Background Box if configured
    if (clip.hasBackground || clip.subtitleStyle.equals("Bold", true)) {
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (clip.hasBackground) clip.backgroundColor.toInt() else 0xCC000000.toInt()
        val bgAlpha = (state.opacity * (Color.alpha(color) / 255f) * 255).toInt().coerceIn(0, 255)
        alpha = bgAlpha
      }
      val cornerRad = when {
        clip.backgroundShape.equals("Pill", true) -> (bgBottom - bgTop) / 2f
        clip.backgroundShape.equals("Rectangle", true) -> 0f
        clip.backgroundShape.equals("Square", true) -> 4f * scaleFactor
        else -> clip.cornerRadius * scaleFactor
      }
      canvas.drawRoundRect(RectF(bgLeft, bgTop, bgRight, bgBottom), cornerRad, cornerRad, bgPaint)
    }

    val textDrawX = -totalTextWidth / 2f
    val textDrawY = -totalTextHeight / 2f

    // 5. 3D Extruded Depth Layers
    val effectiveDepth = state.depth3D
    if (clip.is3D || effectiveDepth > 0f) {
      val rad = Math.toRadians((clip.bevelAngle3D + 45f).toDouble())
      val stepX = (cos(rad) * 1.25f * scaleFactor).toFloat()
      val stepY = (sin(rad) * 1.25f * scaleFactor).toFloat()
      val steps = effectiveDepth.coerceIn(1f, 30f).toInt()

      val extrudePaint = TextPaint(paint)
      val base3DColor = if (clip.color3D != 0L) clip.color3D.toInt() else 0xFF1E293B.toInt()
      extrudePaint.style = Paint.Style.FILL
      extrudePaint.shader = null
      extrudePaint.clearShadowLayer()

      for (d in steps downTo 1) {
        val fraction = d.toFloat() / steps
        val shade = (0.45f + 0.45f * (1f - fraction)).coerceIn(0.2f, 1f)
        val r = (Color.red(base3DColor) * shade).toInt().coerceIn(0, 255)
        val g = (Color.green(base3DColor) * shade).toInt().coerceIn(0, 255)
        val b = (Color.blue(base3DColor) * shade).toInt().coerceIn(0, 255)
        extrudePaint.color = Color.argb((state.opacity * 255).toInt(), r, g, b)

        canvas.save()
        canvas.translate(textDrawX + (d * stepX), textDrawY + (d * stepY))
        layout.draw(canvas)
        canvas.restore()
      }
    }

    // 6. Special Effects (Glitch, Neon, Chrome, Fire, Rainbow, Holographic, Gold, Comic)
    when (clip.effectStyle.lowercase().trim()) {
      "glitch" -> {
        val glitchOff = 3.5f * scaleFactor
        // Cyan pass
        paint.color = 0xFF00FFFF.toInt()
        paint.alpha = (state.opacity * 200).toInt()
        canvas.save()
        canvas.translate(textDrawX - glitchOff, textDrawY)
        layout.draw(canvas)
        canvas.restore()

        // Red pass
        paint.color = 0xFFFF0055.toInt()
        paint.alpha = (state.opacity * 200).toInt()
        canvas.save()
        canvas.translate(textDrawX + glitchOff, textDrawY)
        layout.draw(canvas)
        canvas.restore()

        // Main text pass
        paint.color = clip.textColor.toInt()
        paint.alpha = (state.opacity * 255).toInt()
      }

      "neon" -> {
        val neonGlowColor = if (clip.hasGlow) clip.glowColor.toInt() else 0xFF00E5FF.toInt()
        val glowLayers = listOf(20f * scaleFactor, 12f * scaleFactor, 6f * scaleFactor)
        glowLayers.forEach { r ->
          paint.clearShadowLayer()
          paint.setShadowLayer(r, 0f, 0f, neonGlowColor)
          paint.color = neonGlowColor
          canvas.save()
          canvas.translate(textDrawX, textDrawY)
          layout.draw(canvas)
          canvas.restore()
        }
        paint.clearShadowLayer()
        paint.color = 0xFFFFFFFF.toInt()
      }

      "chrome" -> {
        val chromeShader = LinearGradient(
          0f, textDrawY, 0f, textDrawY + totalTextHeight,
          intArrayOf(0xFFFFFFFF.toInt(), 0xFFCBD5E1.toInt(), 0xFF475569.toInt(), 0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt()),
          floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
          Shader.TileMode.CLAMP
        )
        paint.shader = chromeShader
      }

      "fire" -> {
        val fireShader = LinearGradient(
          0f, textDrawY, 0f, textDrawY + totalTextHeight,
          intArrayOf(0xFFFFF000.toInt(), 0xFFFF6600.toInt(), 0xFFD00000.toInt()),
          floatArrayOf(0f, 0.45f, 1f),
          Shader.TileMode.CLAMP
        )
        paint.setShadowLayer(14f * scaleFactor, 0f, 4f * scaleFactor, 0xFFFF3300.toInt())
        paint.shader = fireShader
      }

      "rainbow" -> {
        val rainbowShader = LinearGradient(
          textDrawX, 0f, textDrawX + totalTextWidth, 0f,
          intArrayOf(
            0xFFFF0000.toInt(), 0xFFFF7700.toInt(), 0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(), 0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt()
          ),
          null,
          Shader.TileMode.CLAMP
        )
        paint.shader = rainbowShader
      }

      "holographic" -> {
        val holoShader = LinearGradient(
          textDrawX, textDrawY, textDrawX + totalTextWidth, textDrawY + totalTextHeight,
          intArrayOf(0xFF00FFFF.toInt(), 0xFFF472B6.toInt(), 0xFF818CF8.toInt(), 0xFF34D399.toInt()),
          null,
          Shader.TileMode.CLAMP
        )
        paint.setShadowLayer(8f * scaleFactor, 0f, 0f, 0xAA00E5FF.toInt())
        paint.shader = holoShader
      }

      "gold" -> {
        val goldShader = LinearGradient(
          0f, textDrawY, 0f, textDrawY + totalTextHeight,
          intArrayOf(0xFFFFF7C2.toInt(), 0xFFF59E0B.toInt(), 0xFFB45309.toInt(), 0xFFFDE68A.toInt()),
          floatArrayOf(0f, 0.4f, 0.75f, 1f),
          Shader.TileMode.CLAMP
        )
        paint.setShadowLayer(6f * scaleFactor, 2f * scaleFactor, 3f * scaleFactor, 0xCC78350F.toInt())
        paint.shader = goldShader
      }

      "comic" -> {
        val comicStroke = max(clip.strokeWidth, 4f) * scaleFactor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = comicStroke
        paint.color = 0xFF000000.toInt()
        canvas.save()
        canvas.translate(textDrawX + (3f * scaleFactor), textDrawY + (3f * scaleFactor))
        layout.draw(canvas)
        canvas.restore()
      }
    }

    // 7. Glow Pass (if enabled and not neon)
    if (clip.hasGlow && !clip.effectStyle.equals("neon", true)) {
      val glowPaint = TextPaint(paint).apply {
        clearShadowLayer()
        setShadowLayer(clip.glowRadius * scaleFactor, 0f, 0f, clip.glowColor.toInt())
        color = clip.glowColor.toInt()
        style = Paint.Style.FILL
        alpha = (state.opacity * 210).toInt().coerceIn(0, 255)
      }
      canvas.save()
      canvas.translate(textDrawX, textDrawY)
      layout.draw(canvas)
      canvas.restore()
    }

    // 8. Shadow Pass
    if (clip.hasShadow || clip.subtitleStyle.equals("Classic", true)) {
      val sColor = if (clip.hasShadow) clip.shadowColor.toInt() else 0xDD000000.toInt()
      val sBlur = if (clip.hasShadow) clip.shadowBlur * scaleFactor else 4f * scaleFactor
      val sOffX = if (clip.hasShadow) clip.shadowOffsetX * scaleFactor else 2f * scaleFactor
      val sOffY = if (clip.hasShadow) clip.shadowOffsetY * scaleFactor else 2f * scaleFactor
      paint.setShadowLayer(sBlur, sOffX, sOffY, sColor)
    } else if (!clip.effectStyle.equals("neon", true) && !clip.effectStyle.equals("fire", true) && !clip.effectStyle.equals("gold", true)) {
      paint.clearShadowLayer()
    }

    // 9. Stroke Pass
    val effectiveStrokeWidth = if (clip.subtitleStyle.equals("Bold", true)) {
      max(clip.strokeWidth, 3.5f) * scaleFactor
    } else {
      clip.strokeWidth * scaleFactor
    }

    if (effectiveStrokeWidth > 0f) {
      val strokePaint = TextPaint(paint).apply {
        style = Paint.Style.STROKE
        strokeWidth = effectiveStrokeWidth
        color = clip.strokeColor.toInt()
        alpha = (state.opacity * 255).toInt().coerceIn(0, 255)
        shader = null
      }
      canvas.save()
      canvas.translate(textDrawX, textDrawY)
      layout.draw(canvas)
      canvas.restore()
    }

    // 10. Gradient Shader or Solid Fill
    if (clip.hasGradient && paint.shader == null) {
      val (x0, y0, x1, y1) = when (clip.gradientDirection.lowercase()) {
        "vertical" -> listOf(0f, textDrawY, 0f, textDrawY + totalTextHeight)
        "diagonal" -> listOf(textDrawX, textDrawY, textDrawX + totalTextWidth, textDrawY + totalTextHeight)
        else -> listOf(textDrawX, 0f, textDrawX + totalTextWidth, 0f)
      }
      paint.shader = LinearGradient(
        x0, y0, x1, y1,
        clip.gradientColorStart.toInt(),
        clip.gradientColorEnd.toInt(),
        Shader.TileMode.CLAMP
      )
    } else if (paint.shader == null) {
      paint.color = clip.textColor.toInt()
    }

    paint.style = Paint.Style.FILL
    paint.alpha = (state.opacity * 255).toInt().coerceIn(0, 255)

    // Main text draw pass
    canvas.save()
    canvas.translate(textDrawX, textDrawY)
    layout.draw(canvas)
    canvas.restore()

    paint.shader = null
    paint.clearShadowLayer()

    canvas.restore()
  }

  fun renderToBitmap(
    clip: TextClip,
    currentPosMs: Long,
    width: Int,
    height: Int,
    context: Context
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(max(width, 64), max(height, 64), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    draw(canvas, clip, currentPosMs, width, height, context)
    return bitmap
  }

  fun measureTextBounds(
    clip: TextClip,
    currentPosMs: Long,
    width: Int,
    height: Int,
    context: Context
  ): RenderedTextBounds {
    if (width <= 0 || height <= 0) {
      return RenderedTextBounds(
        widthPx = 120f,
        heightPx = 48f,
        centerXPx = width / 2f,
        centerYPx = height / 2f,
        rotation = clip.rotation,
        scale = clip.scale,
        opacity = clip.opacity
      )
    }

    val state = evaluateAnimation(clip, currentPosMs)
    var rawText = if (clip.isAllCaps) state.visibleText.uppercase() else state.visibleText
    if (rawText.isEmpty()) rawText = " "

    val containsUrdu = rawText.any {
      it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' ||
          it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
    }

    val containsHindi = rawText.any {
      it in '\u0900'..'\u097F' || it in '\uA8E0'..'\uA8FF'
    }

    val containsChinese = rawText.any {
      it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' ||
          it in '\u3000'..'\u303F' || it in '\uF900'..'\uFAFF'
    }

    if (containsHindi || containsUrdu) {
      rawText = Normalizer.normalize(rawText, Normalizer.Form.NFC)
    }

    val scaleFactor = width.toFloat() / 360f
    val baseFontSize = clip.fontSizeSp * scaleFactor * state.scale

    val typeface = FontManager.loadTypeface(
      context = context,
      fontFamily = clip.fontFamily,
      customFontPath = clip.customFontPath,
      fontWeight = if (clip.subtitleStyle.equals("Bold", true)) 900 else clip.fontWeight,
      isItalic = clip.isItalic
    )

    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      this.typeface = typeface
      this.textSize = baseFontSize
      this.letterSpacing = if (containsUrdu) 0f else clip.letterSpacing / 10f
      this.isUnderlineText = clip.isUnderline
    }

    val layoutAlignment = when (clip.alignment.lowercase()) {
      "left" -> if (containsUrdu) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
      "right" -> if (containsUrdu) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
      else -> Layout.Alignment.ALIGN_CENTER
    }

    val effectiveLineSpacing = when {
      containsUrdu -> maxOf(clip.lineSpacing, 1.35f)
      containsHindi -> maxOf(clip.lineSpacing, 1.25f)
      else -> clip.lineSpacing
    }

    val lines = rawText.split("\n")
    var maxLineWidth = 0f
    lines.forEach { line ->
      val w = paint.measureText(line)
      if (w > maxLineWidth) maxLineWidth = w
    }
    val layoutWidth = max(maxLineWidth.toInt() + 8, 32)

    val textDir = if (containsUrdu) TextDirectionHeuristics.ANYRTL_LTR else TextDirectionHeuristics.FIRSTSTRONG_LTR
    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val builder = StaticLayout.Builder.obtain(rawText, 0, rawText.length, paint, layoutWidth)
        .setAlignment(layoutAlignment)
        .setTextDirection(textDir)
        .setLineSpacing(0f, effectiveLineSpacing)
        .setIncludePad(false)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && containsChinese) {
        builder.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
      }
      builder.build()
    } else {
      @Suppress("DEPRECATION")
      StaticLayout(rawText, paint, layoutWidth, layoutAlignment, effectiveLineSpacing, 0f, false)
    }

    val totalTextWidth = maxLineWidth
    val totalTextHeight = layout.height.toFloat()

    val (boundWidth, boundHeight) = if (clip.hasBackground || clip.subtitleStyle.equals("Bold", true)) {
      val padX = clip.bgPadding * scaleFactor
      val padY = (clip.bgPadding * 0.5f) * scaleFactor
      Pair(totalTextWidth + padX, totalTextHeight + padY)
    } else {
      Pair(totalTextWidth + 6f, totalTextHeight + 6f)
    }

    val centerXPx = (width / 2f) + (state.posX * width / 2f)
    val centerYPx = (height / 2f) + (state.posY * height / 2f)

    return RenderedTextBounds(
      widthPx = boundWidth.coerceAtLeast(36f * scaleFactor),
      heightPx = boundHeight.coerceAtLeast(24f * scaleFactor),
      centerXPx = centerXPx,
      centerYPx = centerYPx,
      rotation = state.rotation,
      scale = state.scale,
      opacity = state.opacity
    )
  }
}
