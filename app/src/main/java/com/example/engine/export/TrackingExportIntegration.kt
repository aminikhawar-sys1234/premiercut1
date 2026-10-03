package com.example.engine.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.engine.ai.MotionTrackingEvaluator

/**
 * Utility applied inside VideoExporter and VideoCompositionEngine
 * to render attached text or overlays according to baked motion tracking.
 */
object TrackingExportRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun drawTrackedOverlayOnCanvas(
        canvas: Canvas,
        overlayBitmap: Bitmap,
        evaluator: MotionTrackingEvaluator,
        frameTimestampUs: Long,
        outputWidth: Int,
        outputHeight: Int
    ) {
        val transformMatrix: Matrix = evaluator.computeTransformMatrix(
            timestampUs = frameTimestampUs,
            viewportWidth = outputWidth.toFloat(),
            viewportHeight = outputHeight.toFloat(),
            contentWidth = overlayBitmap.width.toFloat(),
            contentHeight = overlayBitmap.height.toFloat()
        )

        canvas.drawBitmap(overlayBitmap, transformMatrix, paint)
    }
}
