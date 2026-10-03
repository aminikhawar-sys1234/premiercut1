package com.example.engine.effects.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.example.domain.model.EffectClip
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * CPU-compatible dense deformation fallback. It uses real ML geometry and the
 * subject confidence mask as an alpha constraint. The same deformation fields
 * are suitable for the GPU implementation, so preview/export can share math.
 */
object AdvancedBitmapDeformer {
  fun apply(source: Bitmap, frame: AdvancedHumanAnalysis.Frame, effects: List<EffectClip>, quality: HumanDeformationQuality = HumanDeformationQuality.MEDIUM): Bitmap {
    if (source.isRecycled || effects.isEmpty() || frame.analysisConfidence < 0.05f) return source
    val active = effects.filter { it.intensity > 0.005f }
    if (active.isEmpty()) return source

    val grid = quality.grid.coerceIn(8, 48)
    val vertices = FloatArray((grid + 1) * (grid + 1) * 2)
    var k = 0
    for (gy in 0..grid) {
      val y = gy.toFloat() / grid * source.height
      for (gx in 0..grid) {
        val x = gx.toFloat() / grid * source.width
        val p = deformPoint(x, y, source.width, source.height, frame, active)
        vertices[k] = p.first.coerceIn(-source.width * .15f, source.width * 1.15f)
        vertices[k + 1] = p.second.coerceIn(-source.height * .15f, source.height * 1.15f)
        k += 2
      }
    }

    val warped = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(warped).drawBitmapMesh(
      source, grid, grid, vertices, 0, null, 0,
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    )

    if (frame.mask == null || !hasBodyEffect(active)) return warped

    val foreground = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val fgCanvas = Canvas(foreground)
    fgCanvas.drawBitmap(warped, 0f, 0f, null)
    val maskBitmap = maskToBitmap(frame.mask, source.width, source.height)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    fgCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)
    maskPaint.xfermode = null
    maskBitmap.recycle()

    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(out).drawBitmap(foreground, 0f, 0f, null)
    foreground.recycle()
    warped.recycle()
    return out
  }

  private fun deformPoint(x: Float, y: Float, width: Int, height: Int, frame: AdvancedHumanAnalysis.Frame, effects: List<EffectClip>): Pair<Float, Float> {
    var px = x
    var py = y
    val names = effects.joinToString(" ") { it.effectType.displayName.lowercase() }
    val intensity = effects.maxOfOrNull { it.intensity.coerceIn(0f, 1f) } ?: 0f
    val face = frame.faces.minByOrNull { distance(it.bounds.centerX(), it.bounds.centerY(), x, y) }

    if (face != null && face.bounds.contains(x, y)) {
      val b = face.bounds
      val cx = b.centerX()
      val cy = b.centerY()
      val nx = (x - cx) / max(1f, b.width())
      val ny = (y - cy) / max(1f, b.height())

      if (names.contains("jaw sharpen")) {
        val w = gaussian(nx, ny, .48f, .55f)
        px -= nx * b.width() * .12f * intensity * w
        py -= max(0f, ny) * b.height() * .035f * intensity * w
      }
      if (names.contains("eye enlarge") || names.contains("big eyes")) {
        val eye = if (x < cx) .33f else .67f
        val ex = b.left + b.width() * eye
        val ey = b.top + b.height() * .38f
        val w = gaussian((x - ex) / b.width(), (y - ey) / b.height(), .18f, .14f)
        px += (x - ex) * .20f * intensity * w
        py += (y - ey) * .20f * intensity * w
      }
      if (names.contains("big head") || names.contains("bobble")) {
        val w = gaussian(nx, ny, .70f, .78f)
        px = cx + (px - cx) * (1f + .16f * intensity * w)
        py = cy + (py - cy) * (1f + .16f * intensity * w)
      }
      if (names.contains("tiny body")) {
        val w = gaussian(nx, ny, .72f, .82f)
        px = cx + (px - cx) * (1f - .14f * intensity * w)
        py = cy + (py - cy) * (1f - .10f * intensity * w)
      }
      if (names.contains("squeeze face")) {
        val w = gaussian(nx, ny, .78f, .82f)
        px = cx + (px - cx) * (1f - .12f * intensity * w)
      }
      if (names.contains("face melt") || names.contains("rubber face") || names.contains("face warp") || names.contains("fisheye face")) {
        val w = gaussian(nx, ny, .75f, .85f)
        val radial = (nx * nx + ny * ny).coerceIn(0f, 1.5f)
        val warp = if (names.contains("fisheye face")) .08f else .045f
        px += sinLike(ny * 8f + nx * 4f) * b.width() * warp * intensity * w
        py += sinLike(nx * 7f - ny * 3f) * b.height() * .035f * intensity * w
        if (names.contains("fisheye face")) {
          px += (x - cx) * radial * .10f * intensity * w
          py += (y - cy) * radial * .10f * intensity * w
        }
      }
      if (names.contains("old age face")) {
        val w = gaussian(nx, ny, .78f, .86f)
        px += sinLike(ny * 23f) * b.width() * .012f * intensity * w
        py += sinLike(nx * 19f) * b.height() * .010f * intensity * w
      }
      if (names.contains("baby face filter")) {
        val w = gaussian(nx, ny, .80f, .88f)
        px = cx + (px - cx) * (1f - .08f * intensity * w)
        py = cy + (py - cy) * (1f - .05f * intensity * w)
      }
      if (names.contains("funny face") || names.contains("zombie face")) {
        val w = gaussian(nx, ny, .78f, .88f)
        px += sinLike(ny * 10f + tLike(x, y)) * b.width() * .055f * intensity * w
        py += sinLike(nx * 9f - tLike(y, x)) * b.height() * .045f * intensity * w
      }
      if (names.contains("cartoon face overlay")) {
        val w = gaussian(nx, ny, .75f, .82f)
        px = cx + (px - cx) * (1f + .05f * intensity * w)
        py = cy + (py - cy) * (1f + .05f * intensity * w)
      }
    }

    if (frame.body != null && hasBodyEffect(effects)) {
      val body = frame.body
      val ls = body.landmarks[11]
      val rs = body.landmarks[12]
      val lh = body.landmarks[23]
      val rh = body.landmarks[24]
      val shoulderCx = if (ls != null && rs != null) (ls.x + rs.x) * .5f else width * .5f
      val shoulderCy = if (ls != null && rs != null) (ls.y + rs.y) * .5f else height * .35f
      val hipCx = if (lh != null && rh != null) (lh.x + rh.x) * .5f else width * .5f
      val hipCy = if (lh != null && rh != null) (lh.y + rh.y) * .5f else height * .62f

      if (names.contains("slim body") || names.contains("pro slim contour")) {
        val q = gaussian((x - hipCx) / width, (y - hipCy) / height, .22f, .40f)
        px += (hipCx - x) * .22f * intensity * q
      }
      if (names.contains("muscle boost") || names.contains("wide shoulder")) {
        val q = gaussian((x - shoulderCx) / width, (y - shoulderCy) / height, .28f, .25f)
        px += (x - shoulderCx) * .13f * intensity * q
      }
      if (names.contains("long legs")) {
        val q = smoothBand(y / height, .60f, .98f, .12f)
        py += (y - hipCy) * .12f * intensity * q
      }
      if (names.contains("stretch body")) {
        val q = gaussian((x - shoulderCx) / width, (y - shoulderCy) / height, .32f, .55f)
        py += (y - shoulderCy) * .10f * intensity * q
      }
      if (names.contains("tiny body")) {
        val q = gaussian((x - hipCx) / width, (y - hipCy) / height, .35f, .65f)
        px = hipCx + (px - hipCx) * (1f - .10f * intensity * q)
        py = hipCy + (py - hipCy) * (1f - .12f * intensity * q)
      }
    }
    return px to py
  }

  private fun hasBodyEffect(effects: List<EffectClip>): Boolean =
    effects.any { val n = it.effectType.displayName.lowercase(); n.contains("body") || n.contains("muscle") || n.contains("shoulder") || n.contains("legs") || n.contains("slim") }

  private fun gaussian(x: Float, y: Float, sx: Float, sy: Float): Float =
    exp(-((x / max(.001f, sx)) * (x / max(.001f, sx)) + (y / max(.001f, sy)) * (y / max(.001f, sy))) * 2.2f)

  private fun smoothBand(v: Float, a: Float, b: Float, edge: Float): Float {
    val left = ((v - a) / max(.001f, edge)).coerceIn(0f, 1f)
    val right = ((b - v) / max(.001f, edge)).coerceIn(0f, 1f)
    return min(left, right).coerceIn(0f, 1f)
  }

  private fun sinLike(v: Float): Float = kotlin.math.sin(v.toDouble()).toFloat()

  private fun tLike(a: Float, b: Float): Float = (a * 0.017f + b * 0.013f)

  private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return sqrt(dx * dx + dy * dy)
  }

  private fun maskToBitmap(mask: AdvancedHumanAnalysis.SubjectMask, width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height)
    val sx = mask.width.toFloat() / width
    val sy = mask.height.toFloat() / height
    for (y in 0 until height) {
      val my = (y * sy).toInt().coerceIn(0, mask.height - 1)
      for (x in 0 until width) {
        val mx = (x * sx).toInt().coerceIn(0, mask.width - 1)
        val a = (mask.confidence[my * mask.width + mx].coerceIn(0f, 1f) * 255f).toInt()
        pixels[y * width + x] = (a shl 24) or 0x00FFFFFF
      }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
  }
}
