package com.ahstudio.face.core

data class FaceTrackingConfig(
    val maxFaces: Int = 5,
    val minFaceFraction: Float = 0.04f,
    val useLandmarks: Boolean = true,
    val useContours: Boolean = false,
    val useClassification: Boolean = true,
    val mlKitTracking: Boolean = true,
    val detectionIntervalUs: Long = 33_333,
    val smoothing: SmoothingConfig = SmoothingConfig(),
    val association: AssociationConfig = AssociationConfig(),
)

/** User-facing "trackingSmoothness" 0..1 maps here via [SmoothingConfig.forSmoothness]. */
data class SmoothingConfig(
    val positionMinCutoff: Double = 1.7,
    val positionBeta: Double = 0.35,
    val rotationMinCutoff: Double = 1.2,
    val rotationBeta: Double = 0.20,
    val scaleMinCutoff: Double = 1.0,
    val scaleBeta: Double = 0.10,
    val predictionStrength: Float = 0.35f,
    val maxCoastTimeUs: Long = 400_000,
) {
    companion object {
        fun forSmoothness(s: Float): SmoothingConfig {
            val t = s.toDouble()
            return SmoothingConfig(
                positionMinCutoff = lerp(4.0, 0.7, t),
                positionBeta = lerp(0.8, 0.15, t),
                rotationMinCutoff = lerp(3.0, 0.6, t),
                scaleMinCutoff = lerp(2.5, 0.5, t),
            )
        }
        private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    }
}

data class AssociationConfig(
    val iouGate: Float = 0.08f,
    val centroidGate: Float = 0.30f,
    val sizeGate: Float = 1.8f,
    val velocityWeight: Float = 0.35f,
)
