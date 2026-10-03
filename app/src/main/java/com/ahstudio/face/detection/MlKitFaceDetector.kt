package com.ahstudio.face.detection

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.ahstudio.face.core.FaceLandmarkType
import com.ahstudio.face.core.FaceTrackingConfig
import com.ahstudio.face.core.Vec2
import kotlinx.coroutines.tasks.await

/** The ONLY file allowed to import ML Kit types. */
class MlKitFaceDetector(private val cfg: FaceTrackingConfig) : FaceDetector {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(if (cfg.useLandmarks) FaceDetectorOptions.LANDMARK_MODE_ALL else FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(if (cfg.useContours) FaceDetectorOptions.CONTOUR_MODE_ALL else FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(if (cfg.useClassification) FaceDetectorOptions.CLASSIFICATION_MODE_ALL else FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(cfg.minFaceFraction)
        .apply {
            if (cfg.mlKitTracking) enableTracking()
        }
        .build()

    private val client by lazy { FaceDetection.getClient(options) }

    override suspend fun detect(frame: DetectionFrame, maxFaces: Int): List<RawFaceDetection> {
        val image = when (frame) {
            is DetectionFrame.BitmapFrame -> InputImage.fromBitmap(frame.bitmap, 0)
            is DetectionFrame.YuvImageFrame -> InputImage.fromMediaImage(frame.image, frame.rotationDegrees)
        }
        return try {
            client.process(image).await().take(maxFaces).map { f ->
                val r = f.boundingBox
                RawFaceDetection(
                    boundsPx = BoundsPx(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()),
                    eulerX = f.headEulerAngleX,
                    eulerY = f.headEulerAngleY,
                    eulerZ = f.headEulerAngleZ,
                    landmarksPx = f.allLandmarks.associate {
                        mlType(it.landmarkType) to Vec2(it.position.x.toFloat(), it.position.y.toFloat())
                    },
                    smiling = f.smilingProbability?.takeUnless { p -> p.isNaN() },
                    leftEyeOpen = f.leftEyeOpenProbability?.takeUnless { p -> p.isNaN() },
                    rightEyeOpen = f.rightEyeOpenProbability?.takeUnless { p -> p.isNaN() },
                    trackingId = if (cfg.mlKitTracking) f.trackingId else null,
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    override fun close() {
        runCatching { client.close() }
    }

    private fun mlType(t: Int): FaceLandmarkType = when (t) {
        FaceLandmark.LEFT_EYE -> FaceLandmarkType.LEFT_EYE
        FaceLandmark.RIGHT_EYE -> FaceLandmarkType.RIGHT_EYE
        FaceLandmark.NOSE_BASE -> FaceLandmarkType.NOSE_BASE
        FaceLandmark.MOUTH_LEFT -> FaceLandmarkType.MOUTH_LEFT
        FaceLandmark.MOUTH_BOTTOM -> FaceLandmarkType.MOUTH_BOTTOM
        FaceLandmark.MOUTH_RIGHT -> FaceLandmarkType.MOUTH_RIGHT
        FaceLandmark.LEFT_CHEEK -> FaceLandmarkType.LEFT_CHEEK
        FaceLandmark.RIGHT_CHEEK -> FaceLandmarkType.RIGHT_CHEEK
        else -> FaceLandmarkType.NOSE_BASE
    }
}
