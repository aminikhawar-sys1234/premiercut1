package com.example.ui.components.elements

import android.graphics.*
import com.example.domain.model.StickerClip
import kotlin.math.*

object ElementRenderer {

  fun draw(
    canvas: Canvas,
    clip: StickerClip,
    scale: Float,
    opacity: Float,
    width: Int,
    height: Int,
    currentPosMs: Long
  ) {
    val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)
    if (alphaInt <= 2) return

    val primaryColor = (clip.customColor ?: 0xFF00E5FF).toInt()
    val secondaryColor = (clip.secondaryColor ?: 0xFF7000FF).toInt()
    val vectorType = clip.elementId?.let { id ->
      ElementsCatalog.findById(id)?.vectorType
    } ?: "circle_solid"

    // Base dimension for element drawing centered at (0, 0)
    val baseSize = (min(width, height) * 0.42f) * scale
    val halfW = baseSize
    val halfH = baseSize

    when (clip.elementCategory) {
      "shapes" -> drawShape(canvas, vectorType, halfW, halfH, primaryColor, secondaryColor, alphaInt, scale)
      "graphics" -> drawGraphic(canvas, vectorType, halfW, halfH, primaryColor, secondaryColor, alphaInt, scale, currentPosMs)
      "frames" -> drawFrame(canvas, vectorType, halfW, halfH, primaryColor, secondaryColor, alphaInt, scale)
      "tables" -> drawTable(canvas, vectorType, halfW * 1.35f, halfH * 0.85f, primaryColor, secondaryColor, alphaInt, scale)
      "charts" -> drawChart(canvas, vectorType, halfW * 1.25f, halfH * 0.95f, primaryColor, secondaryColor, alphaInt, scale)
      "3d" -> draw3DObject(canvas, vectorType, halfW, halfH, primaryColor, secondaryColor, alphaInt, scale)
      "icons" -> drawIconGlyph(canvas, vectorType, clip.emojiOrAsset, halfW, halfH, primaryColor, secondaryColor, alphaInt, scale)
      "animals", "birds", "characters", "vehicles" -> {
        drawCharacterOrVehicle(canvas, vectorType, clip.emojiOrAsset, halfW, halfH, primaryColor, secondaryColor, alphaInt, scale)
      }
      else -> drawShape(canvas, vectorType, halfW, halfH, primaryColor, secondaryColor, alphaInt, scale)
    }
  }

  // =========================================================================
  // 1. SHAPES RENDERING
  // =========================================================================
  private fun drawShape(
    canvas: Canvas,
    type: String,
    w: Float,
    h: Float,
    primaryColor: Int,
    secondaryColor: Int,
    alpha: Int,
    scale: Float
  ) {
    val radius = min(w, h)
    when (type) {
      "circle_solid" -> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(0f, 0f, radius, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawCircle(0f, 0f, radius, paint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = max(2f, 2.5f * scale)
          color = Color.WHITE
          this.alpha = (alpha * 0.6f).toInt()
        }
        canvas.drawCircle(0f, 0f, radius, borderPaint)
      }
      "circle_outline" -> {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = max(4f, 6f * scale)
          shader = LinearGradient(-radius, -radius, radius, radius, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawCircle(0f, 0f, radius - strokePaint.strokeWidth / 2f, strokePaint)
      }
      "circle_gradient" -> {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(0f, 0f, radius, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawCircle(0f, 0f, radius, glowPaint)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 3f * scale
          color = Color.WHITE
          this.alpha = (alpha * 0.8f).toInt()
        }
        canvas.drawCircle(0f, 0f, radius * 0.82f, ringPaint)
      }
      "square_solid" -> {
        val rect = RectF(-w, -h, w, h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w, -h, w, h, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawRoundRect(rect, 14f * scale, 14f * scale, paint)
      }
      "square_outline" -> {
        val rect = RectF(-w, -h, w, h)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = max(3f, 5f * scale)
          shader = LinearGradient(-w, -h, w, h, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawRoundRect(rect, 12f * scale, 12f * scale, strokePaint)
      }
      "rect_banner" -> {
        val rect = RectF(-w * 1.4f, -h * 0.55f, w * 1.4f, h * 0.55f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w * 1.4f, 0f, w * 1.4f, 0f, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        val cr = rect.height() / 2f
        canvas.drawRoundRect(rect, cr, cr, paint)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 2f * scale
          color = Color.WHITE
          this.alpha = (alpha * 0.7f).toInt()
        }
        canvas.drawRoundRect(rect, cr, cr, border)
      }
      "rect_card" -> {
        val rect = RectF(-w * 1.25f, -h * 0.8f, w * 1.25f, h * 0.8f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w * 1.25f, -h * 0.8f, w * 1.25f, h * 0.8f, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawRoundRect(rect, 16f * scale, 16f * scale, paint)
      }
      "line_solid" -> {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = max(3f, 5f * scale)
          strokeCap = Paint.Cap.ROUND
          shader = LinearGradient(-w * 1.3f, 0f, w * 1.3f, 0f, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawLine(-w * 1.3f, 0f, w * 1.3f, 0f, linePaint)
      }
      "line_dashed" -> {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = max(3f, 4.5f * scale)
          strokeCap = Paint.Cap.ROUND
          color = primaryColor
          pathEffect = DashPathEffect(floatArrayOf(16f * scale, 10f * scale), 0f)
          this.alpha = alpha
        }
        canvas.drawLine(-w * 1.3f, 0f, w * 1.3f, 0f, linePaint)
      }
      "arrow_right" -> {
        val path = Path().apply {
          val stemW = w * 0.7f
          val headW = w * 0.6f
          val stemHalfH = h * 0.18f
          val headHalfH = h * 0.55f
          moveTo(-stemW, -stemHalfH)
          lineTo(0f, -stemHalfH)
          lineTo(0f, -headHalfH)
          lineTo(headW, 0f)
          lineTo(0f, headHalfH)
          lineTo(0f, stemHalfH)
          lineTo(-stemW, stemHalfH)
          close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w, 0f, w, 0f, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, paint)
      }
      "arrow_curve" -> {
        val path = Path().apply {
          moveTo(-w * 0.8f, h * 0.5f)
          quadTo(0f, h * 0.5f, w * 0.2f, -h * 0.2f)
          lineTo(w * 0.05f, -h * 0.45f)
          lineTo(w * 0.7f, -h * 0.35f)
          lineTo(w * 0.55f, h * 0.2f)
          lineTo(w * 0.38f, 0.05f)
          quadTo(0f, h * 0.8f, -w * 0.8f, h * 0.75f)
          close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w, -h, w, h, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, paint)
      }
      "triangle" -> {
        val path = Path().apply {
          moveTo(0f, -h)
          lineTo(w, h * 0.8f)
          lineTo(-w, h * 0.8f)
          close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(0f, -h, 0f, h, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, paint)
      }
      "star_5" -> {
        val path = createStarPath(5, radius, radius * 0.45f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(0f, 0f, radius, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, paint)
      }
      "hexagon" -> {
        val path = createPolygonPath(6, radius)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-radius, -radius, radius, radius, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, paint)
      }
      "heart" -> {
        val path = createHeartPath(radius)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(0f, -radius * 0.2f, radius * 1.2f, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, paint)
      }
      "speech_bubble" -> {
        val rect = RectF(-w * 1.1f, -h * 0.75f, w * 1.1f, h * 0.45f)
        val path = Path().apply {
          addRoundRect(rect, 14f * scale, 14f * scale, Path.Direction.CW)
          moveTo(-w * 0.3f, h * 0.45f)
          lineTo(-w * 0.5f, h * 0.9f)
          lineTo(0f, h * 0.45f)
          close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w, -h, w, h, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, paint)
      }
      else -> {
        canvas.drawCircle(0f, 0f, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = primaryColor
          this.alpha = alpha
        })
      }
    }
  }

  // =========================================================================
  // 2. GRAPHICS & DECORATIONS
  // =========================================================================
  private fun drawGraphic(
    canvas: Canvas,
    type: String,
    w: Float,
    h: Float,
    primaryColor: Int,
    secondaryColor: Int,
    alpha: Int,
    scale: Float,
    timeMs: Long
  ) {
    when (type) {
      "sunburst" -> {
        val numRays = 12
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = primaryColor
          this.alpha = (alpha * 0.85f).toInt()
          style = Paint.Style.FILL
        }
        val rotOffset = (timeMs / 40.0) % 360.0
        canvas.save()
        canvas.rotate(rotOffset.toFloat())
        for (i in 0 until numRays) {
          canvas.save()
          canvas.rotate(i * (360f / numRays))
          val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(-w * 0.15f, -w)
            lineTo(w * 0.15f, -w)
            close()
          }
          canvas.drawPath(path, rayPaint)
          canvas.restore()
        }
        canvas.drawCircle(0f, 0f, w * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = secondaryColor
          this.alpha = alpha
        })
        canvas.restore()
      }
      "sparkles" -> {
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = primaryColor
          this.alpha = alpha
        }
        drawSparkleStar(canvas, 0f, 0f, w * 0.85f, starPaint)
        drawSparkleStar(canvas, w * 0.55f, -h * 0.5f, w * 0.4f, starPaint)
        drawSparkleStar(canvas, -w * 0.5f, h * 0.45f, w * 0.35f, starPaint)
      }
      "ribbon" -> {
        val rect = RectF(-w * 1.3f, -h * 0.4f, w * 1.3f, h * 0.4f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w * 1.3f, 0f, w * 1.3f, 0f, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawRoundRect(rect, 8f * scale, 8f * scale, paint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = h * 0.45f
          typeface = Typeface.DEFAULT_BOLD
          textAlign = Paint.Align.CENTER
          this.alpha = alpha
        }
        canvas.drawText("SPECIAL AWARD", 0f, textPaint.textSize * 0.35f, textPaint)
      }
      "sale_tag" -> {
        val path = Path().apply {
          moveTo(-w * 0.9f, -h * 0.5f)
          lineTo(w * 0.4f, -h * 0.5f)
          lineTo(w * 0.9f, 0f)
          lineTo(w * 0.4f, h * 0.5f)
          lineTo(-w * 0.9f, h * 0.5f)
          close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w, -h, w, h, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, paint)
        canvas.drawCircle(w * 0.55f, 0f, 6f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          this.alpha = alpha
        })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = h * 0.45f
          typeface = Typeface.DEFAULT_BOLD
          textAlign = Paint.Align.CENTER
          this.alpha = alpha
        }
        canvas.drawText("SALE", -w * 0.2f, textPaint.textSize * 0.35f, textPaint)
      }
      "verified_stamp" -> {
        val path = createStarPath(16, w, w * 0.88f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = primaryColor
          this.alpha = alpha
        }
        canvas.drawPath(path, bgPaint)
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          style = Paint.Style.STROKE
          strokeWidth = 6f * scale
          strokeCap = Paint.Cap.ROUND
          strokeJoin = Paint.Join.ROUND
          this.alpha = alpha
        }
        val checkPath = Path().apply {
          moveTo(-w * 0.35f, 0f)
          lineTo(-w * 0.05f, h * 0.3f)
          lineTo(w * 0.4f, -h * 0.25f)
        }
        canvas.drawPath(checkPath, checkPaint)
      }
      "trophy" -> {
        val cupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w, -h, w, h, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        val cupPath = Path().apply {
          moveTo(-w * 0.6f, -h * 0.7f)
          lineTo(w * 0.6f, -h * 0.7f)
          lineTo(w * 0.45f, 0f)
          quadTo(0f, h * 0.3f, -w * 0.45f, 0f)
          close()
        }
        canvas.drawPath(cupPath, cupPaint)
        // Base stem
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = secondaryColor
          this.alpha = alpha
        }
        canvas.drawRect(-w * 0.15f, h * 0.15f, w * 0.15f, h * 0.55f, stemPaint)
        canvas.drawRoundRect(RectF(-w * 0.5f, h * 0.55f, w * 0.5f, h * 0.75f), 4f * scale, 4f * scale, cupPaint)
      }
      "fire_flame" -> {
        val flamePath = Path().apply {
          moveTo(0f, -h * 0.85f)
          cubicTo(w * 0.4f, -h * 0.4f, w * 0.8f, 0f, w * 0.5f, h * 0.6f)
          quadTo(0f, h * 0.85f, -w * 0.5f, h * 0.6f)
          cubicTo(-w * 0.8f, 0f, -w * 0.4f, -h * 0.4f, 0f, -h * 0.85f)
          close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(0f, h * 0.3f, w, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(flamePath, paint)
        // Inner core
        val innerPath = Path().apply {
          moveTo(0f, -h * 0.3f)
          cubicTo(w * 0.2f, -h * 0.1f, w * 0.35f, h * 0.2f, w * 0.25f, h * 0.5f)
          quadTo(0f, h * 0.7f, -w * 0.25f, h * 0.5f)
          cubicTo(-w * 0.35f, h * 0.2f, -w * 0.2f, -h * 0.1f, 0f, -h * 0.3f)
          close()
        }
        canvas.drawPath(innerPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.YELLOW
          this.alpha = alpha
        })
      }
      "target" -> {
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor; this.alpha = alpha }
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; this.alpha = alpha }
        canvas.drawCircle(0f, 0f, w, p1)
        canvas.drawCircle(0f, 0f, w * 0.72f, p2)
        canvas.drawCircle(0f, 0f, w * 0.46f, p1)
        canvas.drawCircle(0f, 0f, w * 0.2f, p2)
      }
      else -> {
        drawSparkleStar(canvas, 0f, 0f, w, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor; this.alpha = alpha })
      }
    }
  }

  // =========================================================================
  // 3. FRAMES RENDERING
  // =========================================================================
  private fun drawFrame(
    canvas: Canvas,
    type: String,
    w: Float,
    h: Float,
    primaryColor: Int,
    secondaryColor: Int,
    alpha: Int,
    scale: Float
  ) {
    when (type) {
      "frame_polaroid" -> {
        val cardRect = RectF(-w * 1.1f, -h * 1.25f, w * 1.1f, h * 1.25f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFFF5F5F5.toInt()
          this.alpha = alpha
        }
        canvas.drawRoundRect(cardRect, 8f * scale, 8f * scale, cardPaint)
        // Cutout window for photo
        val winRect = RectF(-w * 0.95f, -h * 1.1f, w * 0.95f, h * 0.7f)
        val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF1E2433.toInt()
          this.alpha = alpha
        }
        canvas.drawRect(winRect, winPaint)
      }
      "frame_phone" -> {
        val bodyRect = RectF(-w * 0.9f, -h * 1.4f, w * 0.9f, h * 1.4f)
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF121620.toInt()
          this.alpha = alpha
        }
        val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 3f * scale
          color = primaryColor
          this.alpha = alpha
        }
        canvas.drawRoundRect(bodyRect, 22f * scale, 22f * scale, bodyPaint)
        canvas.drawRoundRect(bodyRect, 22f * scale, 22f * scale, bezelPaint)
        // Speaker / dynamic island notch at top
        canvas.drawRoundRect(RectF(-w * 0.3f, -h * 1.32f, w * 0.3f, -h * 1.22f), 6f * scale, 6f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.BLACK
          this.alpha = alpha
        })
      }
      "frame_film" -> {
        val stripRect = RectF(-w * 1.35f, -h * 0.85f, w * 1.35f, h * 0.85f)
        canvas.drawRect(stripRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF0D0F14.toInt()
          this.alpha = alpha
        })
        // Sprocket holes along top & bottom
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          this.alpha = (alpha * 0.9f).toInt()
        }
        val numHoles = 7
        val step = (w * 2.5f) / (numHoles + 1)
        for (i in 1..numHoles) {
          val x = -w * 1.25f + (i * step)
          canvas.drawRoundRect(RectF(x - 5f * scale, -h * 0.78f, x + 5f * scale, -h * 0.62f), 2f * scale, 2f * scale, holePaint)
          canvas.drawRoundRect(RectF(x - 5f * scale, h * 0.62f, x + 5f * scale, h * 0.78f), 2f * scale, 2f * scale, holePaint)
        }
      }
      "frame_circle" -> {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 8f * scale
          shader = SweepGradient(0f, 0f, intArrayOf(primaryColor, secondaryColor, primaryColor), null)
          this.alpha = alpha
        }
        canvas.drawCircle(0f, 0f, w, ringPaint)
      }
      "frame_neon" -> {
        val rect = RectF(-w * 1.15f, -h * 0.9f, w * 1.15f, h * 0.9f)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 5f * scale
          color = primaryColor
          this.alpha = alpha
        }
        canvas.drawRoundRect(rect, 14f * scale, 14f * scale, strokePaint)
      }
      else -> {
        val rect = RectF(-w, -h, w, h)
        canvas.drawRoundRect(rect, 10f * scale, 10f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 3f * scale
          color = primaryColor
          this.alpha = alpha
        })
      }
    }
  }

  // =========================================================================
  // 4. TABLES RENDERING
  // =========================================================================
  private fun drawTable(
    canvas: Canvas,
    type: String,
    w: Float,
    h: Float,
    primaryColor: Int,
    secondaryColor: Int,
    alpha: Int,
    scale: Float
  ) {
    val tableRect = RectF(-w, -h, w, h)
    // Table outer backdrop
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF0F1523.toInt()
      this.alpha = alpha
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f * scale
      color = 0xFF26324D.toInt()
      this.alpha = alpha
    }
    canvas.drawRoundRect(tableRect, 10f * scale, 10f * scale, bgPaint)
    canvas.drawRoundRect(tableRect, 10f * scale, 10f * scale, borderPaint)

    when (type) {
      "table_2x2" -> {
        // Header bar
        val headerRect = RectF(-w, -h, w, -h * 0.35f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(-w, 0f, w, 0f, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawRoundRect(headerRect, 8f * scale, 8f * scale, headerPaint)
        // Dividers
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF26324D.toInt()
          strokeWidth = 1.5f * scale
          this.alpha = alpha
        }
        canvas.drawLine(0f, -h, 0f, h, divPaint)
        canvas.drawLine(-w, h * 0.3f, w, h * 0.3f, divPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = h * 0.26f
          typeface = Typeface.DEFAULT_BOLD
          textAlign = Paint.Align.CENTER
          this.alpha = alpha
        }
        canvas.drawText("A", -w * 0.5f, -h * 0.52f, textPaint)
        canvas.drawText("B", w * 0.5f, -h * 0.52f, textPaint)

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = h * 0.22f
          textAlign = Paint.Align.CENTER
          this.alpha = (alpha * 0.85f).toInt()
        }
        canvas.drawText("94%", -w * 0.5f, 0f, cellPaint)
        canvas.drawText("76%", w * 0.5f, 0f, cellPaint)
        canvas.drawText("120", -w * 0.5f, h * 0.65f, cellPaint)
        canvas.drawText("85", w * 0.5f, h * 0.65f, cellPaint)
      }
      "table_3x3" -> {
        // 3 cols, 3 rows
        val colW = (w * 2f) / 3f
        val rowH = (h * 2f) / 3f
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF2D3C5C.toInt()
          strokeWidth = 1.5f * scale
          this.alpha = alpha
        }
        // Header
        canvas.drawRect(RectF(-w, -h, w, -h + rowH), Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF1B2844.toInt()
          this.alpha = alpha
        })
        canvas.drawLine(-w + colW, -h, -w + colW, h, divPaint)
        canvas.drawLine(-w + colW * 2f, -h, -w + colW * 2f, h, divPaint)
        canvas.drawLine(-w, -h + rowH, w, -h + rowH, divPaint)
        canvas.drawLine(-w, -h + rowH * 2f, w, -h + rowH * 2f, divPaint)

        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF00E5FF.toInt()
          textSize = rowH * 0.4f
          typeface = Typeface.DEFAULT_BOLD
          textAlign = Paint.Align.CENTER
          this.alpha = alpha
        }
        canvas.drawText("Item", -w + colW * 0.5f, -h + rowH * 0.65f, headerText)
        canvas.drawText("Qty", -w + colW * 1.5f, -h + rowH * 0.65f, headerText)
        canvas.drawText("Total", -w + colW * 2.5f, -h + rowH * 0.65f, headerText)
      }
      else -> {
        // Generic table layout
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF26324D.toInt()
          strokeWidth = 1.5f * scale
          this.alpha = alpha
        }
        canvas.drawLine(0f, -h, 0f, h, divPaint)
        canvas.drawLine(-w, 0f, w, 0f, divPaint)
      }
    }
  }

  // =========================================================================
  // 5. CHARTS RENDERING
  // =========================================================================
  private fun drawChart(
    canvas: Canvas,
    type: String,
    w: Float,
    h: Float,
    primaryColor: Int,
    secondaryColor: Int,
    alpha: Int,
    scale: Float
  ) {
    when (type) {
      "chart_bar" -> {
        val bars = floatArrayOf(0.4f, 0.7f, 0.55f, 0.95f)
        val numBars = bars.size
        val barW = (w * 1.8f) / (numBars * 1.7f)
        val startX = -w * 0.85f

        // Baseline
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF334155.toInt()
          strokeWidth = 2f * scale
          this.alpha = alpha
        }
        canvas.drawLine(-w * 0.95f, h * 0.75f, w * 0.95f, h * 0.75f, axisPaint)

        val colors = intArrayOf(primaryColor, secondaryColor, primaryColor, secondaryColor)
        for (i in 0 until numBars) {
          val barH = (h * 1.35f) * bars[i]
          val bx = startX + (i * barW * 1.7f)
          val rect = RectF(bx, h * 0.75f - barH, bx + barW, h * 0.75f)
          val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bx, rect.top, bx, rect.bottom, colors[i], 0xFF0F172A.toInt(), Shader.TileMode.CLAMP)
            this.alpha = alpha
          }
          canvas.drawRoundRect(rect, 4f * scale, 4f * scale, barPaint)
        }
      }
      "chart_line" -> {
        val pts = arrayOf(
          Pair(-w * 0.85f, h * 0.55f),
          Pair(-w * 0.35f, -h * 0.1f),
          Pair(w * 0.15f, h * 0.2f),
          Pair(w * 0.85f, -h * 0.65f)
        )
        val path = Path().apply {
          moveTo(pts[0].first, pts[0].second)
          quadTo(-w * 0.6f, h * 0.1f, pts[1].first, pts[1].second)
          quadTo(-w * 0.05f, -h * 0.2f, pts[2].first, pts[2].second)
          quadTo(w * 0.5f, 0f, pts[3].first, pts[3].second)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 4.5f * scale
          strokeCap = Paint.Cap.ROUND
          shader = LinearGradient(-w, 0f, w, 0f, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawPath(path, linePaint)

        // Draw node circles
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          this.alpha = alpha
        }
        for (pt in pts) {
          canvas.drawCircle(pt.first, pt.second, 5f * scale, nodePaint)
        }
      }
      "chart_pie" -> {
        val radius = min(w, h)
        val oval = RectF(-radius, -radius, radius, radius)
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor; this.alpha = alpha }
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = secondaryColor; this.alpha = alpha }
        val p3 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD700.toInt(); this.alpha = alpha }

        canvas.drawArc(oval, 0f, 130f, true, p1)
        canvas.drawArc(oval, 130f, 120f, true, p2)
        canvas.drawArc(oval, 250f, 110f, true, p3)
      }
      "chart_donut" -> {
        val radius = min(w, h)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = radius * 0.38f
          strokeCap = Paint.Cap.ROUND
          this.alpha = alpha
        }
        val oval = RectF(-radius * 0.8f, -radius * 0.8f, radius * 0.8f, radius * 0.8f)
        strokePaint.color = 0xFF1E293B.toInt()
        canvas.drawArc(oval, 0f, 360f, false, strokePaint)
        strokePaint.color = primaryColor
        canvas.drawArc(oval, -90f, 260f, false, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = radius * 0.38f
          typeface = Typeface.DEFAULT_BOLD
          textAlign = Paint.Align.CENTER
          this.alpha = alpha
        }
        canvas.drawText("72%", 0f, textPaint.textSize * 0.35f, textPaint)
      }
      else -> {
        canvas.drawCircle(0f, 0f, min(w, h), Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = primaryColor
          this.alpha = alpha
        })
      }
    }
  }

  // =========================================================================
  // 6. 3D OBJECTS RENDERING
  // =========================================================================
  private fun draw3DObject(
    canvas: Canvas,
    type: String,
    w: Float,
    h: Float,
    primaryColor: Int,
    secondaryColor: Int,
    alpha: Int,
    scale: Float
  ) {
    val radius = min(w, h)
    when (type) {
      "3d_cube" -> {
        // Isometric 3D Cube with 3 facets: top, left, right
        val s = radius * 0.85f
        val topPath = Path().apply {
          moveTo(0f, -s)
          lineTo(s * 0.866f, -s * 0.5f)
          lineTo(0f, 0f)
          lineTo(-s * 0.866f, -s * 0.5f)
          close()
        }
        val leftPath = Path().apply {
          moveTo(-s * 0.866f, -s * 0.5f)
          lineTo(0f, 0f)
          lineTo(0f, s)
          lineTo(-s * 0.866f, s * 0.5f)
          close()
        }
        val rightPath = Path().apply {
          moveTo(0f, 0f)
          lineTo(s * 0.866f, -s * 0.5f)
          lineTo(s * 0.866f, s * 0.5f)
          lineTo(0f, s)
          close()
        }
        // Top facet (brightest)
        canvas.drawPath(topPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = primaryColor
          this.alpha = alpha
        })
        // Left facet (medium)
        canvas.drawPath(leftPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = secondaryColor
          this.alpha = (alpha * 0.85f).toInt()
        })
        // Right facet (shadow)
        canvas.drawPath(rightPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF0A192F.toInt()
          this.alpha = (alpha * 0.9f).toInt()
        })
      }
      "3d_sphere" -> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(-radius * 0.35f, -radius * 0.35f, radius * 1.3f, Color.WHITE, primaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        }
        canvas.drawCircle(0f, 0f, radius, paint)
      }
      "3d_pyramid" -> {
        val s = radius * 0.9f
        val leftFace = Path().apply {
          moveTo(0f, -s)
          lineTo(-s * 0.85f, s * 0.6f)
          lineTo(0f, s * 0.9f)
          close()
        }
        val rightFace = Path().apply {
          moveTo(0f, -s)
          lineTo(0f, s * 0.9f)
          lineTo(s * 0.85f, s * 0.6f)
          close()
        }
        canvas.drawPath(leftFace, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = primaryColor
          this.alpha = alpha
        })
        canvas.drawPath(rightFace, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = secondaryColor
          this.alpha = (alpha * 0.75f).toInt()
        })
      }
      "3d_coin" -> {
        canvas.drawCircle(0f, 0f, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = RadialGradient(0f, 0f, radius, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
          this.alpha = alpha
        })
        canvas.drawCircle(0f, 0f, radius * 0.82f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 3f * scale
          color = Color.WHITE
          this.alpha = (alpha * 0.8f).toInt()
        })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          textSize = radius * 0.6f
          typeface = Typeface.DEFAULT_BOLD
          textAlign = Paint.Align.CENTER
          this.alpha = alpha
        }
        canvas.drawText("$", 0f, textPaint.textSize * 0.35f, textPaint)
      }
      else -> {
        canvas.drawCircle(0f, 0f, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = primaryColor
          this.alpha = alpha
        })
      }
    }
  }

  // =========================================================================
  // 7. ICONS, CHARACTERS, VEHICLES & ANIMALS
  // =========================================================================
  private fun drawIconGlyph(
    canvas: Canvas,
    type: String,
    glyphText: String,
    w: Float,
    h: Float,
    primaryColor: Int,
    secondaryColor: Int,
    alpha: Int,
    scale: Float
  ) {
    val radius = min(w, h)
    // Disc background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(0f, 0f, radius, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
      this.alpha = alpha
    }
    canvas.drawRoundRect(RectF(-w, -h, w, h), 16f * scale, 16f * scale, bgPaint)

    // Symbol text / emoji
    val text = glyphText.split(" ").firstOrNull() ?: "★"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = radius * 0.95f
      textAlign = Paint.Align.CENTER
      this.alpha = alpha
    }
    val yPos = -((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(text, 0f, yPos, textPaint)
  }

  private fun drawCharacterOrVehicle(
    canvas: Canvas,
    type: String,
    label: String,
    w: Float,
    h: Float,
    primaryColor: Int,
    secondaryColor: Int,
    alpha: Int,
    scale: Float
  ) {
    val radius = min(w, h)
    // Backdrop pill/badge
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(-w, -h, w, h, primaryColor, secondaryColor, Shader.TileMode.CLAMP)
      this.alpha = (alpha * 0.45f).toInt()
    }
    canvas.drawCircle(0f, 0f, radius * 0.95f, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.5f * scale
      color = primaryColor
      this.alpha = (alpha * 0.8f).toInt()
    }
    canvas.drawCircle(0f, 0f, radius * 0.95f, borderPaint)

    val symbol = label.split(" ").firstOrNull() ?: "⭐"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = radius * 1.05f
      textAlign = Paint.Align.CENTER
      this.alpha = alpha
    }
    val yPos = -((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(symbol, 0f, yPos, textPaint)
  }

  // =========================================================================
  // HELPER GEOMETRY UTILITIES
  // =========================================================================
  private fun createStarPath(numPoints: Int, outerRadius: Float, innerRadius: Float): Path {
    val path = Path()
    val angleStep = Math.PI / numPoints
    for (i in 0 until (numPoints * 2)) {
      val r = if (i % 2 == 0) outerRadius else innerRadius
      val angle = i * angleStep - (Math.PI / 2)
      val x = (r * cos(angle)).toFloat()
      val y = (r * sin(angle)).toFloat()
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
  }

  private fun createPolygonPath(sides: Int, radius: Float): Path {
    val path = Path()
    val angleStep = (2 * Math.PI) / sides
    for (i in 0 until sides) {
      val angle = i * angleStep - (Math.PI / 2)
      val x = (radius * cos(angle)).toFloat()
      val y = (radius * sin(angle)).toFloat()
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
  }

  private fun createHeartPath(size: Float): Path {
    val path = Path()
    val s = size * 0.85f
    path.moveTo(0f, s * 0.7f)
    path.cubicTo(-s * 1.2f, s * 0.1f, -s * 1.1f, -s * 0.9f, 0f, -s * 0.35f)
    path.cubicTo(s * 1.1f, -s * 0.9f, s * 1.2f, s * 0.1f, 0f, s * 0.7f)
    path.close()
    return path
  }

  private fun drawSparkleStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
    val path = Path().apply {
      moveTo(cx, cy - radius)
      quadTo(cx, cy, cx + radius, cy)
      quadTo(cx, cy, cx, cy + radius)
      quadTo(cx, cy, cx - radius, cy)
      quadTo(cx, cy, cx, cy - radius)
      close()
    }
    canvas.drawPath(path, paint)
  }
}
