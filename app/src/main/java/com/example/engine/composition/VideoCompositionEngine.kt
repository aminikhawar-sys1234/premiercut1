package com.example.engine.composition

import android.content.Context
import android.graphics.*
import com.example.domain.model.*
import com.example.engine.KeyframeInterpolator
import com.example.engine.composition.gpu.GpuCompositionRenderer
import com.example.engine.effects.ml.AdvancedBitmapDeformer
import com.example.engine.effects.ml.AdvancedHumanAnalysis
import com.example.engine.text.TextLayerRenderer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ComposedEffect(
  val clip: EffectClip,
  val effectType: EffectType,
  val intensity: Float,
  val timeInEffectMs: Long,
  val progress: Float
)

data class ComposedFrame(
  val timelinePosMs: Long,
  val activeClip: VideoClip?,
  val clipSourcePosMs: Long,
  val activeOverlays: List<ComposedOverlay>,
  val activeTexts: List<ComposedText>,
  val activeStickers: List<ComposedSticker>,
  val activeTransition: ComposedTransition?,
  val activeEffects: List<ComposedEffect> = emptyList(),
  val colorMatrix: ColorMatrix,
  val colorFilter: ColorMatrixColorFilter,
  val activeClipTransform: com.example.engine.InterpolatedClipTransform? = null
)

data class ComposedOverlay(
  val clip: VideoClip,
  val sourcePosMs: Long,
  val posX: Float,
  val posY: Float,
  val scaleX: Float,
  val scaleY: Float,
  val rotation: Float,
  val opacity: Float,
  val blendMode: String,
  val blur: Float = 0f,
  val brightness: Float = 0f,
  val contrast: Float = 1f,
  val saturation: Float = 1f,
  val effectParam: Float = 0f
) {
  val scale: Float get() = (scaleX + scaleY) / 2f

  constructor(
    clip: VideoClip,
    sourcePosMs: Long,
    posX: Float,
    posY: Float,
    scale: Float,
    rotation: Float,
    opacity: Float,
    blendMode: String
  ) : this(
    clip = clip,
    sourcePosMs = sourcePosMs,
    posX = posX,
    posY = posY,
    scaleX = scale,
    scaleY = scale,
    rotation = rotation,
    opacity = opacity,
    blendMode = blendMode
  )
}

data class ComposedText(
  val clip: TextClip,
  val posX: Float,
  val posY: Float,
  val scale: Float,
  val rotation: Float,
  val opacity: Float,
  val currentPosMs: Long = 0L
)

data class ComposedSticker(
  val clip: StickerClip,
  val posX: Float,
  val posY: Float,
  val scale: Float,
  val rotation: Float,
  val opacity: Float
)

data class ComposedTransition(
  val type: TransitionType,
  val progress: Float, // 0.0f to 1.0f
  val clipBefore: VideoClip,
  val clipAfter: VideoClip
)

class VideoCompositionEngine(private val context: Context) {

  private val humanAnalysis = AdvancedHumanAnalysis()

  val gpuRenderer: GpuCompositionRenderer by lazy {
    GpuCompositionRenderer(context)
  }

  /**
   * Renders the composed frame on the GPU into the currently active OpenGL surface/framebuffer.
   */
  fun renderGpuFrame(
    frame: ComposedFrame,
    mainTextureId: Int,
    isMainOes: Boolean,
    mainTexMatrix: FloatArray? = null,
    overlayTextures: Map<String, Int> = emptyMap(),
    viewportWidth: Int,
    viewportHeight: Int,
    adjustments: VideoAdjustments = VideoAdjustments(),
    filter: FilterSettings = FilterSettings(),
    chromaKey: ChromaKeySettings = ChromaKeySettings()
  ) {
    gpuRenderer.render(
      frame = frame,
      mainTextureId = mainTextureId,
      isMainOes = isMainOes,
      mainTexMatrix = mainTexMatrix,
      overlayTextures = overlayTextures,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight,
      timelineAdjustments = adjustments,
      timelineFilter = filter,
      chromaKey = chromaKey
    )
  }

  fun releaseGpu() {
    gpuRenderer.release()
  }

  /**
   * Export/preview-capture entry point for VFX_BODY_* effects.
   *
   * The same dense ML analysis + segmentation mask + deformation field is used
   * for every frame. AdvancedHumanAnalysis owns temporal state, so landmark
   * smoothing survives across sequential frames instead of resetting per frame.
   */
  suspend fun renderFrameWithHumanEffects(
    canvas: Canvas,
    frame: ComposedFrame,
    mainBitmap: Bitmap?,
    overlayBitmaps: Map<String, Bitmap>,
    canvasWidth: Int,
    canvasHeight: Int,
    chromaKey: ChromaKeySettings = ChromaKeySettings()
  ) {
    val bodyEffects = frame.activeEffects
      .map { it.clip }
      .filter { VfxCatalogRenderer.requiresMlDeformation(it.effectType) }

    if (mainBitmap == null || bodyEffects.isEmpty()) {
      renderFrame(canvas, frame, mainBitmap, overlayBitmaps, canvasWidth, canvasHeight, chromaKey)
      return
    }

    val analysis = humanAnalysis.analyzeSuspending(mainBitmap, frame.timelinePosMs)
    if (analysis == null || analysis.analysisConfidence < 0.05f) {
      renderFrame(canvas, frame, mainBitmap, overlayBitmaps, canvasWidth, canvasHeight, chromaKey)
      return
    }

    val deformed = AdvancedBitmapDeformer.apply(
      source = mainBitmap,
      frame = analysis,
      effects = bodyEffects,
      quality = com.example.engine.effects.ml.HumanDeformationQuality.MEDIUM
    )
    try {
      renderFrame(canvas, frame, deformed, overlayBitmaps, canvasWidth, canvasHeight, chromaKey)
    } finally {
      if (deformed !== mainBitmap && !deformed.isRecycled) deformed.recycle()
    }
  }

  private val timelineEvaluator = com.example.engine.timeline.TimelineEvaluator()

  /**
   * Calculates the exact state of all timeline elements at any timestamp.
   * Delegates to the authoritative TimelineEvaluator to guarantee preview/export parity.
   */
  fun evaluateFrame(timeline: Timeline, posMs: Long): ComposedFrame {
    return timelineEvaluator.evaluate(timeline, posMs).toComposedFrame()
  }

  /**
   * Renders the composed frame onto an Android Canvas for export or preview capture.
   */
  fun renderFrame(
    canvas: Canvas,
    frame: ComposedFrame,
    mainBitmap: Bitmap?,
    overlayBitmaps: Map<String, Bitmap>,
    canvasWidth: Int,
    canvasHeight: Int,
    chromaKey: ChromaKeySettings = ChromaKeySettings()
  ) {
    // 1. Draw canvas background
    canvas.drawColor(Color.BLACK)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.colorFilter = frame.colorFilter

    // 2. Draw Main Clip Bitmap
    if (mainBitmap != null && !mainBitmap.isRecycled) {
      val clip = frame.activeClip
      val totalRot = if (clip != null) {
        val rel = frame.timelinePosMs - clip.timelineStartMs
        val kf = frame.activeClipTransform ?: KeyframeInterpolator.interpolate(clip, rel)
        kotlin.math.abs((clip.rotationDegrees + kf.rotation).toInt() % 360)
      } else 0
      val isTransposed = (totalRot == 90 || totalRot == 270)
      val effBitmapW = if (isTransposed) mainBitmap.height else mainBitmap.width
      val effBitmapH = if (isTransposed) mainBitmap.width else mainBitmap.height
      val scaleX = canvasWidth.toFloat() / effBitmapW
      val scaleY = canvasHeight.toFloat() / effBitmapH
      val baseScale = min(scaleX, scaleY)

      val matrix = Matrix()
      // Center bitmap
      matrix.postTranslate(-mainBitmap.width / 2f, -mainBitmap.height / 2f)

      if (clip != null) {
        val rel = frame.timelinePosMs - clip.timelineStartMs
        val kf = frame.activeClipTransform ?: KeyframeInterpolator.interpolate(clip, rel)
        matrix.postScale(
          if (clip.flipHorizontal) -1f else 1f,
          if (clip.flipVertical) -1f else 1f
        )
        matrix.postRotate((clip.rotationDegrees + kf.rotation) % 360)
        matrix.postScale(baseScale * clip.cropScale * kf.scaleX, baseScale * clip.cropScale * kf.scaleY)
        matrix.postTranslate(
          (canvasWidth / 2f) + (clip.cropOffsetX + kf.posX) * (canvasWidth / 2f),
          (canvasHeight / 2f) + (clip.cropOffsetY + kf.posY) * (canvasHeight / 2f)
        )
        paint.alpha = (kf.opacity * 255).toInt().coerceIn(0, 255)
      } else {
        matrix.postScale(baseScale, baseScale)
        matrix.postTranslate(canvasWidth / 2f, canvasHeight / 2f)
      }

      // Apply accumulated camera motion from active effects (Shake, Zoom, Skater Zoom, Vertigo Dolly, Spin, Mirror)
      if (frame.activeEffects.isNotEmpty()) {
        val motion = VideoEffectRenderer.calculateMotionTransform(frame.activeEffects.map { it.clip }, frame.timelinePosMs)
        matrix.postScale(motion.scaleX, motion.scaleY, canvasWidth / 2f, canvasHeight / 2f)
        matrix.postRotate(motion.rotation, canvasWidth / 2f, canvasHeight / 2f)
        matrix.postTranslate(motion.translationX * canvasWidth, motion.translationY * canvasHeight)
        paint.alpha = ((paint.alpha / 255f) * motion.alpha * 255f).toInt().coerceIn(0, 255)
      }

      // Handle transition blending if active
      if (frame.activeTransition != null) {
        val tr = frame.activeTransition
        when (tr.type) {
          TransitionType.FADE, TransitionType.DISSOLVE -> {
            paint.alpha = ((1f - tr.progress) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.WIPE -> {
            canvas.save()
            val clipRight = canvasWidth * (1f - tr.progress)
            canvas.clipRect(0f, 0f, clipRight, canvasHeight.toFloat())
            canvas.drawBitmap(mainBitmap, matrix, paint)
            canvas.restore()
          }
          TransitionType.SLIDE_LEFT -> {
            matrix.postTranslate(-canvasWidth * tr.progress, 0f)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.SLIDE_RIGHT -> {
            matrix.postTranslate(canvasWidth * tr.progress, 0f)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.PUSH_UP -> {
            matrix.postTranslate(0f, -canvasHeight * tr.progress)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.ZOOM_IN -> {
            val zoom = 1f + tr.progress * 0.6f
            matrix.postScale(zoom, zoom, canvasWidth / 2f, canvasHeight / 2f)
            paint.alpha = ((1f - tr.progress * 0.5f) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.ZOOM_OUT -> {
            val zoom = (1f - tr.progress * 0.4f).coerceAtLeast(0.1f)
            matrix.postScale(zoom, zoom, canvasWidth / 2f, canvasHeight / 2f)
            paint.alpha = ((1f - tr.progress * 0.5f) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.SPIN -> {
            matrix.postRotate(tr.progress * 360f, canvasWidth / 2f, canvasHeight / 2f)
            val zoom = (1f - tr.progress * 0.5f).coerceAtLeast(0.1f)
            matrix.postScale(zoom, zoom, canvasWidth / 2f, canvasHeight / 2f)
            paint.alpha = ((1f - tr.progress) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.BLUR -> {
            val zoom = 1f + tr.progress * 0.3f
            matrix.postScale(zoom, zoom, canvasWidth / 2f, canvasHeight / 2f)
            paint.alpha = ((1f - tr.progress) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          TransitionType.FLASH -> {
            paint.alpha = 255
            canvas.drawBitmap(mainBitmap, matrix, paint)
            // Draw white flash rectangle overlay
            val flashAlpha = (if (tr.progress < 0.5f) tr.progress * 2f else (1f - tr.progress) * 2f).coerceIn(0f, 1f)
            val flashPaint = Paint().apply {
              color = Color.WHITE
              alpha = (flashAlpha * 240).toInt().coerceIn(0, 255)
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), flashPaint)
          }
          TransitionType.GLITCH -> {
            val jitterX = if (tr.progress in 0.2f..0.8f) ((Math.random() - 0.5) * 30).toFloat() else 0f
            val jitterY = if (tr.progress in 0.2f..0.8f) ((Math.random() - 0.5) * 20).toFloat() else 0f
            matrix.postTranslate(jitterX, jitterY)
            paint.alpha = ((1f - tr.progress) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
          else -> {
            paint.alpha = 255
            canvas.drawBitmap(mainBitmap, matrix, paint)
          }
        }
      } else {
        paint.alpha = 255
        canvas.drawBitmap(mainBitmap, matrix, paint)
      }
    }

    // 3. Draw Overlays (PIP)
    for (composedOverlay in frame.activeOverlays) {
      val rawOverlayBitmap = overlayBitmaps[composedOverlay.clip.id]
      if (rawOverlayBitmap != null && !rawOverlayBitmap.isRecycled) {
        val overlayBitmap = if (chromaKey.enabled) {
          ChromaKeyProcessor.applyChromaKey(rawOverlayBitmap, chromaKey)
        } else rawOverlayBitmap

        val overlayMatrix = Matrix()
        overlayMatrix.postTranslate(-overlayBitmap.width / 2f, -overlayBitmap.height / 2f)
        overlayMatrix.postRotate(composedOverlay.rotation)
        val overlayScaleX = (canvasWidth.toFloat() / overlayBitmap.width) * composedOverlay.scaleX * 0.5f
        val overlayScaleY = (canvasWidth.toFloat() / overlayBitmap.width) * composedOverlay.scaleY * 0.5f
        overlayMatrix.postScale(overlayScaleX, overlayScaleY)

        val targetX = (canvasWidth / 2f) + (composedOverlay.posX * canvasWidth / 2f)
        val targetY = (canvasHeight / 2f) + (composedOverlay.posY * canvasHeight / 2f)
        overlayMatrix.postTranslate(targetX, targetY)

        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        overlayPaint.alpha = (composedOverlay.opacity * 255).toInt().coerceIn(0, 255)

        // Blend mode support
        when (composedOverlay.blendMode.lowercase()) {
          "screen" -> overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
          "multiply" -> overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
          "overlay" -> overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
          "lighten" -> overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
          else -> overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        canvas.drawBitmap(overlayBitmap, overlayMatrix, overlayPaint)
      }
    }

    // 4. Draw Text Overlays
    for (composedText in frame.activeTexts) {
      drawTextClip(canvas, composedText, canvasWidth, canvasHeight)
    }

    // 5. Draw Stickers
    for (sticker in frame.activeStickers) {
      drawStickerClip(canvas, sticker, canvasWidth, canvasHeight)
    }

    // 6. Apply Active Visual Effects (Flash, Glow, Glitch, Light Leak, Lens Flare, RGB Split)
    for (effect in frame.activeEffects) {
      drawVisualEffect(canvas, effect, canvasWidth, canvasHeight)
    }
  }

  private fun drawVisualEffect(canvas: Canvas, effect: ComposedEffect, width: Int, height: Int) {
    if (effect.intensity <= 0.01f) return
    VideoEffectRenderer.renderSingleEffect(
      canvas = canvas,
      effect = effect.clip,
      intensity = effect.intensity,
      relTime = effect.timeInEffectMs,
      width = width,
      height = height
    )
  }

  private fun drawTextClip(canvas: Canvas, composedText: ComposedText, width: Int, height: Int) {
    TextLayerRenderer.draw(
      canvas = canvas,
      clip = composedText.clip,
      currentPosMs = composedText.currentPosMs,
      width = width,
      height = height,
      context = context
    )
  }

  private fun drawStickerClip(canvas: Canvas, sticker: ComposedSticker, width: Int, height: Int) {
    StickerLayerRenderer.draw(
      canvas = canvas,
      clip = sticker.clip.copy(
        posX = sticker.posX,
        posY = sticker.posY,
        scale = sticker.scale,
        rotation = sticker.rotation,
        opacity = sticker.opacity
      ),
      currentPosMs = sticker.clip.timelineStartMs,
      width = width,
      height = height
    )
  }
}
