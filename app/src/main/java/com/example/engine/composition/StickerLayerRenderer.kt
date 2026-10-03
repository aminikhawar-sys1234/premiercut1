package com.example.engine.composition

import android.graphics.*
import com.example.domain.model.BadgeType
import com.example.domain.model.StickerAnimationType
import com.example.domain.model.StickerClip
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class AnimatedStickerState(
  val posX: Float,
  val posY: Float,
  val scale: Float,
  val rotation: Float,
  val opacity: Float
)

object StickerLayerRenderer {

  fun evaluateAnimation(clip: StickerClip, currentPosMs: Long): AnimatedStickerState {
    val relTimeMs = (currentPosMs - clip.timelineStartMs).coerceAtLeast(0L)
    val timeSec = relTimeMs / 1000f

    val baseTransform = if (clip.keyframes.isNotEmpty()) {
      com.example.engine.KeyframeInterpolator.interpolate(clip, relTimeMs)
    } else null

    val basePosX = baseTransform?.posX ?: clip.posX
    val basePosY = baseTransform?.posY ?: clip.posY
    val baseScale = baseTransform?.scale ?: clip.scale
    val baseRotation = baseTransform?.rotation ?: clip.rotation
    val baseOpacity = baseTransform?.opacity ?: clip.opacity

    var deltaX = 0f
    var deltaY = 0f
    var scaleMul = 1.0f
    var deltaRot = 0f
    var opacityMul = 1.0f

    when (clip.animationType) {
      StickerAnimationType.NONE -> {
        // Static
      }
      StickerAnimationType.PULSE -> {
        // Smooth sine wave breathing pulse
        scaleMul = 1.0f + 0.15f * sin(relTimeMs * 0.008f)
      }
      StickerAnimationType.HEARTBEAT -> {
        // Double-beat thump
        val cycle = (relTimeMs % 1200L) / 1200f
        val beat = if (cycle < 0.2f) {
          sin(cycle / 0.2f * Math.PI.toFloat()) * 0.25f
        } else if (cycle in 0.25f..0.45f) {
          sin((cycle - 0.25f) / 0.2f * Math.PI.toFloat()) * 0.18f
        } else {
          0f
        }
        scaleMul = 1.0f + beat
      }
      StickerAnimationType.BOUNCE -> {
        // Physical bounce with ground compression
        val bouncePhase = (relTimeMs % 1000L) / 1000f
        val jump = abs(sin(bouncePhase * Math.PI.toFloat()))
        deltaY = -jump * 0.08f
        scaleMul = 1.0f + (1f - jump) * 0.08f
      }
      StickerAnimationType.SPIN -> {
        // Continuous smooth rotation
        deltaRot = (relTimeMs * 0.12f) % 360f
      }
      StickerAnimationType.SHAKE -> {
        // High frequency vibration/jitter
        deltaRot = sin(relTimeMs * 0.035f) * 7f
        deltaX = cos(relTimeMs * 0.03f) * 0.02f
      }
      StickerAnimationType.FLOAT -> {
        // Floating drift
        deltaY = sin(relTimeMs * 0.004f) * 0.05f
        deltaX = cos(relTimeMs * 0.003f) * 0.03f
      }
      StickerAnimationType.SWING -> {
        // Pendulum swing
        deltaRot = sin(relTimeMs * 0.005f) * 15f
      }
      StickerAnimationType.GLOW_PULSE -> {
        // Luminous opacity pulse
        opacityMul = 0.65f + 0.35f * (0.5f + 0.5f * sin(relTimeMs * 0.007f))
        scaleMul = 1.0f + 0.08f * sin(relTimeMs * 0.007f)
      }
      StickerAnimationType.POP_IN -> {
        // Elastic pop entrance at start of clip
        if (relTimeMs < 400L) {
          val progress = relTimeMs / 400f
          val overshoot = 1.25f
          val s = overshoot - 1f
          val p = progress - 1f
          scaleMul = max(0f, p * p * ((s + 1f) * p + s) + 1f)
        }
      }
    }

    return AnimatedStickerState(
      posX = basePosX + deltaX,
      posY = basePosY + deltaY,
      scale = (baseScale * scaleMul).coerceAtLeast(0.05f),
      rotation = (baseRotation + deltaRot) % 360f,
      opacity = (baseOpacity * opacityMul).coerceIn(0f, 1f)
    )
  }

  fun draw(
    canvas: Canvas,
    clip: StickerClip,
    currentPosMs: Long,
    width: Int,
    height: Int
  ) {
    val state = evaluateAnimation(clip, currentPosMs)
    if (state.opacity <= 0.01f) return

    val centerX = (width / 2f) + (state.posX * width / 2f)
    val centerY = (height / 2f) + (state.posY * height / 2f)

    canvas.save()
    canvas.translate(centerX, centerY)
    canvas.rotate(state.rotation)

    if (clip.elementId != null) {
      com.example.ui.components.elements.ElementRenderer.draw(
        canvas = canvas,
        clip = clip,
        scale = state.scale,
        opacity = state.opacity,
        width = width,
        height = height,
        currentPosMs = currentPosMs
      )
    } else if (clip.badgeType != null) {
      drawBadge(canvas, clip.badgeType, state.scale, state.opacity, width, height)
    } else {
      drawEmojiOrAsset(canvas, clip.emojiOrAsset, state.scale, state.opacity, width)
    }

    canvas.restore()
  }

  private fun drawBadge(
    canvas: Canvas,
    badge: BadgeType,
    scale: Float,
    opacity: Float,
    canvasWidth: Int,
    canvasHeight: Int
  ) {
    val badgeBaseWidth = (canvasWidth * 0.34f) * scale
    val badgeBaseHeight = badgeBaseWidth * 0.42f
    val cornerRadius = badgeBaseHeight * 0.32f

    val rect = RectF(
      -badgeBaseWidth / 2f,
      -badgeBaseHeight / 2f,
      badgeBaseWidth / 2f,
      badgeBaseHeight / 2f
    )

    val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)

    // 1. Drop shadow glow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      alpha = (alphaInt * 0.45f).toInt()
      maskFilter = BlurMaskFilter(12f * scale, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, shadowPaint)

    // 2. Main gradient pill background
    val gradient = LinearGradient(
      rect.left, rect.top, rect.right, rect.bottom,
      badge.primaryColor.toInt(),
      badge.secondaryColor.toInt(),
      Shader.TileMode.CLAMP
    )

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = gradient
      alpha = alphaInt
    }
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

    // 3. Highlight gloss line on top half
    val glossRect = RectF(
      rect.left + 2f, rect.top + 2f,
      rect.right - 2f, rect.centerY()
    )
    val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        glossRect.left, glossRect.top, glossRect.left, glossRect.bottom,
        Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
      )
      alpha = (alphaInt * 0.32f).toInt()
    }
    canvas.drawRoundRect(glossRect, cornerRadius * 0.8f, cornerRadius * 0.8f, glossPaint)

    // 4. Clean border outline
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = max(2f, 2.5f * scale)
      color = Color.WHITE
      alpha = (alphaInt * 0.75f).toInt()
    }
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

    // 5. Draw Icon and Badge Text
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      alpha = alphaInt
      textSize = badgeBaseHeight * 0.40f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
      setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = badgeBaseHeight * 0.44f
      textAlign = Paint.Align.CENTER
      alpha = alphaInt
    }

    val hasSubtitle = badge.subtitle.isNotBlank() && badgeBaseHeight > 36f

    if (hasSubtitle) {
      val textY = rect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2f - (badgeBaseHeight * 0.12f)
      val fullTitle = "${badge.icon}  ${badge.displayName}"
      canvas.drawText(fullTitle, 0f, textY, titlePaint)

      val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = (alphaInt * 0.88f).toInt()
        textSize = badgeBaseHeight * 0.22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
      }
      val subY = textY + (badgeBaseHeight * 0.30f)
      canvas.drawText(badge.subtitle, 0f, subY, subPaint)
    } else {
      val textY = rect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2f
      val fullTitle = "${badge.icon} ${badge.displayName}"
      canvas.drawText(fullTitle, 0f, textY, titlePaint)
    }
  }

  private fun drawEmojiOrAsset(
    canvas: Canvas,
    emojiOrAsset: String,
    scale: Float,
    opacity: Float,
    canvasWidth: Int
  ) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = 58f * scale * (canvasWidth / 400f)
      alpha = (opacity * 255).toInt().coerceIn(0, 255)
      textAlign = Paint.Align.CENTER
    }

    val yPos = -((paint.descent() + paint.ascent()) / 2f)
    canvas.drawText(emojiOrAsset, 0f, yPos, paint)
  }

  fun createStickerBitmap(
    clip: StickerClip,
    targetWidth: Int,
    targetHeight: Int,
    currentPosMs: Long = 0L
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    draw(canvas, clip, currentPosMs, targetWidth, targetHeight)
    return bitmap
  }
}
