package com.example.engine.ai

import android.graphics.Matrix

class MotionTrackingEvaluator(private val trackingResult: TrackingResult) {

    /**
     * Interpolates normalized translation, scale, and rotation at any arbitrary playback or export timestamp.
     */
    fun evaluate(timestampUs: Long): MotionKeyframe {
        val keys = trackingResult.keyframes
        if (keys.isEmpty()) {
            return MotionKeyframe(timestampUs, 0.5f, 0.5f, 1f, 1f, 0f)
        }

        if (timestampUs <= keys.first().timestampUs) return keys.first()
        if (timestampUs >= keys.last().timestampUs) return keys.last()

        var low = 0
        var high = keys.size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                keys[mid].timestampUs < timestampUs -> low = mid + 1
                keys[mid].timestampUs > timestampUs -> high = mid - 1
                else -> return keys[mid]
            }
        }

        val k0 = keys[maxOf(0, low - 1)]
        val k1 = keys[minOf(keys.size - 1, low)]

        val span = k1.timestampUs - k0.timestampUs
        if (span <= 0L) return k0

        val factor = (timestampUs - k0.timestampUs).toFloat() / span.toFloat()

        return MotionKeyframe(
            timestampUs = timestampUs,
            centerX = k0.centerX + (k1.centerX - k0.centerX) * factor,
            centerY = k0.centerY + (k1.centerY - k0.centerY) * factor,
            scaleX = k0.scaleX + (k1.scaleX - k0.scaleX) * factor,
            scaleY = k0.scaleY + (k1.scaleY - k0.scaleY) * factor,
            rotationDeg = k0.rotationDeg + (k1.rotationDeg - k0.rotationDeg) * factor
        )
    }

    /**
     * Converts normalized keyframe coordinates to standard canvas transformation matrix.
     */
    fun computeTransformMatrix(
        timestampUs: Long,
        viewportWidth: Float,
        viewportHeight: Float,
        contentWidth: Float,
        contentHeight: Float
    ): Matrix {
        val frame = evaluate(timestampUs)
        val matrix = Matrix()

        val targetScreenX = frame.centerX * viewportWidth
        val targetScreenY = frame.centerY * viewportHeight

        matrix.postTranslate(-contentWidth / 2f, -contentHeight / 2f)
        matrix.postScale(frame.scaleX, frame.scaleY)
        matrix.postRotate(frame.rotationDeg)
        matrix.postTranslate(targetScreenX, targetScreenY)

        return matrix
    }
}
