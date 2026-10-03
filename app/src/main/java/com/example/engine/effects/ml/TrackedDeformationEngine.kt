package com.example.engine.effects.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Real ML tracking/deformation layer.
 *
 * Face: ML Kit contour/landmark tracking.
 * Body: ML Kit accurate pose landmarks.
 *
 * The returned mesh is deterministic and frame-local, so preview and export can
 * use the same deformation math. No fake face/body overlay is generated here.
 */
class TrackedDeformationEngine {

  data class Point(val x: Float, val y: Float)

  data class FaceTrack(
    val bounds: android.graphics.RectF,
    val leftEye: Point?,
    val rightEye: Point?,
    val nose: Point?,
    val mouth: Point?,
    val faceOval: List<Point>,
    val trackingId: Int?
  )

  data class BodyTrack(
    val landmarks: Map<Int, Point>,
    val confidence: Float
  )

  data class TrackSnapshot(
    val width: Int,
    val height: Int,
    val faces: List<FaceTrack>,
    val body: BodyTrack?
  )

  private val faceDetector = FaceDetection.getClient(
    FaceDetectorOptions.Builder()
      .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
      .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
      .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
      .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
      .enableTracking()
      .build()
  )

  private val poseDetector = PoseDetection.getClient(
    AccuratePoseDetectorOptions.Builder()
      .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
      .build()
  )

  /**
   * Analyze a video frame. Throttle calls at the caller (normally 15-30 fps);
   * the returned snapshot is safe to reuse for intermediate render frames.
   */
  fun analyze(bitmap: Bitmap, callback: (TrackSnapshot?) -> Unit) {
    val image = InputImage.fromBitmap(bitmap, 0)
    faceDetector.process(image)
      .addOnSuccessListener { faces ->
        poseDetector.process(image)
          .addOnSuccessListener { pose ->
            callback(
              TrackSnapshot(
                bitmap.width,
                bitmap.height,
                faces.map(::toFaceTrack),
                toBodyTrack(pose.allPoseLandmarks)
              )
            )
          }
          .addOnFailureListener { callback(TrackSnapshot(bitmap.width, bitmap.height, faces.map(::toFaceTrack), null)) }
      }
      .addOnFailureListener { callback(null) }
  }

  private fun toFaceTrack(face: Face): FaceTrack {
    fun contour(type: Int): List<Point> =
      face.getContour(type)?.points?.map { Point(it.x, it.y) }.orEmpty()

    fun first(type: Int): Point? = face.getLandmark(type)?.position?.let { Point(it.x, it.y) }

    return FaceTrack(
      bounds = android.graphics.RectF(face.boundingBox),
      leftEye = first(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE),
      rightEye = first(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE),
      nose = first(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE),
      mouth = first(com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM),
      faceOval = contour(FaceContour.FACE),
      trackingId = face.trackingId
    )
  }

  private fun toBodyTrack(landmarks: List<PoseLandmark>): BodyTrack? {
    if (landmarks.isEmpty()) return null
    val map = landmarks.associate { it.landmarkType to Point(it.position3D.x, it.position3D.y) }
    val confidence = landmarks.map { it.inFrameLikelihood }.average().toFloat()
    return BodyTrack(map, confidence)
  }

  fun close() {
    faceDetector.close()
    poseDetector.close()
  }

  /**
   * Generates a destination mesh for drawBitmapMesh().
   * Face deformation is radial around tracked landmarks; body deformation is
   * landmark-guided around shoulders/hips. Amount is intentionally bounded.
   */
  fun deformMesh(
    width: Int,
    height: Int,
    cols: Int = 20,
    rows: Int = 20,
    face: FaceTrack? = null,
    body: BodyTrack? = null,
    slim: Float = 0f,
    eyeEnlarge: Float = 0f,
    faceMelt: Float = 0f,
    headScale: Float = 0f,
    shoulderWidth: Float = 0f,
    legLength: Float = 0f
  ): FloatArray {
    val c = max(4, cols)
    val r = max(4, rows)
    val verts = FloatArray((c + 1) * (r + 1) * 2)
    var k = 0
    for (y in 0..r) {
      val py = y.toFloat() / r * height
      for (x in 0..c) {
        val px = x.toFloat() / c * width
        var dx = 0f
        var dy = 0f

        face?.let { f ->
          val fw = max(1f, f.bounds.width())
          val fh = max(1f, f.bounds.height())
          val fx = f.bounds.centerX()
          val fy = f.bounds.centerY()
          val q = radial(px, py, fx, fy, fw * .62f, fh * .72f)
          dx += -q * (px - fx) * slim * .16f
          dy += q * (fy - py) * headScale * .10f
          f.leftEye?.let { e -> dx += radial(px, py, e.x, e.y, fw*.22f, fh*.16f) * (px-e.x) * eyeEnlarge*.13f }
          f.rightEye?.let { e -> dx += radial(px, py, e.x, e.y, fw*.22f, fh*.16f) * (px-e.x) * eyeEnlarge*.13f }
          f.nose?.let { n -> dx += radial(px, py, n.x, n.y, fw*.30f, fh*.34f) * (fx-px) * faceMelt*.12f }
        }

        body?.let { b ->
          val ls=b.landmarks[PoseLandmark.LEFT_SHOULDER]
          val rs=b.landmarks[PoseLandmark.RIGHT_SHOULDER]
          val lh=b.landmarks[PoseLandmark.LEFT_HIP]
          val rh=b.landmarks[PoseLandmark.RIGHT_HIP]
          if(ls!=null && rs!=null) {
            val sx=(ls.x+rs.x)/2f; val sy=(ls.y+rs.y)/2f
            val q=radial(px,py,sx,sy,max(30f,dist(ls,rs)*.9f),max(50f,height*.25f))
            dx += q*(px-sx)*shoulderWidth*.12f
          }
          if(lh!=null && rh!=null) {
            val hx=(lh.x+rh.x)/2f; val hy=(lh.y+rh.y)/2f
            val q=radial(px,py,hx,hy,max(30f,dist(lh,rh)*.9f),max(60f,height*.42f))
            dy += q*(hy-py)*legLength*.08f
          }
        }

        verts[k++] = (px + dx).coerceIn(-width*.08f, width*1.08f)
        verts[k++] = (py + dy).coerceIn(-height*.08f, height*1.08f)
      }
    }
    return verts
  }

  private fun radial(x: Float,y: Float,cx: Float,cy: Float,rx: Float,ry: Float): Float {
    val nx=(x-cx)/rx
    val ny=(y-cy)/ry
    return exp(-(nx*nx+ny*ny)*2.2f)
  }

  private fun dist(a: Point,b: Point): Float {
    val dx=a.x-b.x; val dy=a.y-b.y
    return kotlin.math.sqrt(dx*dx+dy*dy)
  }
}
