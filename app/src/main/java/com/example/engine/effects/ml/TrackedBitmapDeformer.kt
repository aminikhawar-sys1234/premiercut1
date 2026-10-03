package com.example.engine.effects.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.domain.model.EffectClip

/**
 * Pixel deformation stage. Unlike an overlay, this actually remaps source pixels
 * through an ML-guided mesh. The same bitmap stage can be used by preview/export.
 */
object TrackedBitmapDeformer {

  fun apply(
    source: Bitmap,
    effects: List<EffectClip>,
    snapshot: TrackedDeformationEngine.TrackSnapshot,
    engine: TrackedDeformationEngine
  ): Bitmap {
    if (effects.isEmpty()) return source
    val names = effects.map { it.effectType.displayName.lowercase() }
    val intensity = effects.maxOfOrNull { it.intensity.coerceIn(0f,1f) } ?: 0f
    if (intensity <= .01f) return source

    val slim = effects.filter { it.effectType.category == "Body Effects" && it.effectType.displayName.lowercase().contains("slim") }
      .maxOfOrNull { it.intensity } ?: 0f
    val muscle = effects.filter { it.effectType.category == "Body Effects" && it.effectType.displayName.lowercase().contains("muscle") }
      .maxOfOrNull { it.intensity } ?: 0f
    val eye = effects.filter { it.effectType.displayName.lowercase().contains("eye") }
      .maxOfOrNull { it.intensity } ?: 0f
    val head = effects.filter { it.effectType.displayName.lowercase().contains("head") }
      .maxOfOrNull { it.intensity } ?: 0f
    val melt = effects.filter { it.effectType.displayName.lowercase().contains("melt") || it.effectType.displayName.lowercase().contains("rubber") }
      .maxOfOrNull { it.intensity } ?: 0f
    val shoulder = effects.filter { it.effectType.displayName.lowercase().contains("shoulder") }
      .maxOfOrNull { it.intensity } ?: 0f
    val legs = effects.filter { it.effectType.displayName.lowercase().contains("legs") }
      .maxOfOrNull { it.intensity } ?: 0f

    val face = snapshot.faces.firstOrNull()
    val body = snapshot.body
    if (face == null && body == null) return source

    val mesh = engine.deformMesh(
      source.width, source.height,
      face = face, body = body,
      slim = slim + intensity * if (names.any { it.contains("body") }) .18f else 0f,
      eyeEnlarge = eye,
      faceMelt = melt,
      headScale = head,
      shoulderWidth = shoulder + muscle * .35f,
      legLength = legs
    )

    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmapMesh(source, 20, 20, mesh, 0, null, 0, paint)
    return out
  }
}
