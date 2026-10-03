package com.example.engine.effects.ml

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshPoint
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Shared per-frame human analysis. One pass feeds every deformation effect.
 * ML Kit supplies the real 468-point face topology and the subject confidence mask.
 */
class AdvancedHumanAnalysis : AutoCloseable {
  data class Vec3(val x: Float, val y: Float, val z: Float)
  data class MeshTriangle(val a: Int, val b: Int, val c: Int)
  enum class FaceRegion { FACE_BOUNDARY, FOREHEAD, EYEBROW, EYE, NOSE, CHEEK, MOUTH, JAW, CHIN, OTHER }

  data class FaceMeshState(
    val vertices: List<Vec3>,
    val triangles: List<MeshTriangle>,
    val regionIds: IntArray,
    val confidence: Float,
    val trackingId: Long,
    val bounds: RectF,
    val timestampMs: Long
  )

  data class BodyState(
    val landmarks: Map<Int, Vec3>,
    val confidence: Float,
    val trackingId: Long = 0L
  )

  data class SubjectMask(
    val width: Int,
    val height: Int,
    val confidence: FloatArray,
    val timestampMs: Long
  ) {
    fun sample(u: Float, v: Float): Float {
      if (width <= 0 || height <= 0 || confidence.isEmpty()) return 0f
      val x = (u.coerceIn(0f, 1f) * (width - 1)).toInt()
      val y = (v.coerceIn(0f, 1f) * (height - 1)).toInt()
      return confidence[y * width + x].coerceIn(0f, 1f)
    }
  }

  data class Frame(
    val width: Int,
    val height: Int,
    val timestampMs: Long,
    val faces: List<FaceMeshState>,
    val body: BodyState?,
    val mask: SubjectMask?,
    val analysisConfidence: Float
  )

  private val faceMeshDetector by lazy {
    runCatching { FaceMeshDetection.getClient() }.getOrNull()
  }
  private val poseDetector by lazy {
    runCatching {
      PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
          .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
          .build()
      )
    }.getOrNull()
  }
  private val segmenter: SubjectSegmenter? by lazy {
    runCatching {
      SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
          .enableForegroundConfidenceMask()
          .enableMultipleSubjects(
            SubjectSegmenterOptions.SubjectResultOptions.Builder()
              .enableConfidenceMask()
              .build()
          )
          .build()
      )
    }.getOrNull()
  }

  private val faceTracks = mutableListOf<Track>()
  private val faceSmoothers = mutableMapOf<Long, TemporalLandmarkSmoother>()
  private val bodySmoothers = mutableMapOf<Int, TemporalLandmarkSmoother>()
  private var nextTrackId = 1L
  private data class Track(var id: Long, var x: Float, var y: Float, var lastSeenMs: Long)

  fun analyze(bitmap: Bitmap, timestampMs: Long, callback: (Frame?) -> Unit) {
    if (bitmap.isRecycled || bitmap.width < 64 || bitmap.height < 64) {
      callback(null); return
    }
    val image = InputImage.fromBitmap(bitmap, 0)
    val lock = Any()
    var faces: List<FaceMeshState>? = null
    var body: BodyState? = null
    var mask: SubjectMask? = null
    var done = 0
    var failed = false

    fun finishOne() {
      synchronized(lock) {
        done++
        if (done < 3) return
        if (failed) { callback(null); return }
        val f = faces ?: emptyList()
        val conf = listOf(
          if (f.isEmpty()) 0f else f.map { it.confidence }.average().toFloat(),
          body?.confidence ?: 0f,
          mask?.let { if (it.confidence.isEmpty()) 0f else it.confidence.average().toFloat() } ?: 0f
        ).maxOrNull() ?: 0f
        callback(Frame(bitmap.width, bitmap.height, timestampMs, f, body, mask, conf.coerceIn(0f, 1f)))
      }
    }

    val fDetector = faceMeshDetector
    if (fDetector != null) {
      try {
        fDetector.process(image)
          .addOnSuccessListener { result -> faces = mapFaces(result, timestampMs); finishOne() }
          .addOnFailureListener { finishOne() }
      } catch (t: Throwable) {
        android.util.Log.w("AdvancedHumanAnalysis", "Face mesh processing skipped", t)
        finishOne()
      }
    } else {
      finishOne()
    }

    val pDetector = poseDetector
    if (pDetector != null) {
      try {
        pDetector.process(image)
          .addOnSuccessListener { result -> body = mapBody(result, timestampMs); finishOne() }
          .addOnFailureListener { finishOne() }
      } catch (t: Throwable) {
        android.util.Log.w("AdvancedHumanAnalysis", "Pose detection processing skipped", t)
        finishOne()
      }
    } else {
      finishOne()
    }

    val sDetector = segmenter
    if (sDetector != null) {
      try {
        sDetector.process(image)
          .addOnSuccessListener { result ->
            val data = result.foregroundConfidenceMask
            mask = if (data != null) {
              SubjectMask(bitmap.width, bitmap.height, FloatArray(bitmap.width * bitmap.height) { data.get() }, timestampMs)
            } else null
            finishOne()
          }
          .addOnFailureListener { finishOne() }
      } catch (t: Throwable) {
        android.util.Log.w("AdvancedHumanAnalysis", "Subject segmentation processing skipped", t)
        finishOne()
      }
    } else {
      finishOne()
    }
  }

  private fun mapFaces(meshes: List<FaceMesh>, timestampMs: Long): List<FaceMeshState> {
    val raw = meshes.map { mesh ->
      val points = mesh.allPoints
      val vertices = MutableList(468) { Vec3(0f, 0f, 0f) }
      points.forEach { point ->
        val index = point.index
        if (index in 0 until 468) {
          vertices[index] = Vec3(point.position.x, point.position.y, point.position.z)
        }
      }
      @Suppress("UNCHECKED_CAST")
      val mlTriangles = mesh.allTriangles as List<com.google.mlkit.vision.common.Triangle<FaceMeshPoint>>
      val triangles = mlTriangles.mapNotNull { t ->
        val p: List<FaceMeshPoint> = t.getAllPoints()
        if (p.size != 3) null else MeshTriangle(p[0].index, p[1].index, p[2].index)
      }
      val bounds = RectF(mesh.boundingBox)
      val regionIds = classifyRegions(mesh)
      val confidence = if (vertices.isEmpty()) 0f else 1f
      Triple(bounds, vertices, Triple(triangles, regionIds, confidence))
    }

    return raw.map { item ->
      val centerX = item.first.centerX()
      val centerY = item.first.centerY()
      val id = assignTrack(centerX, centerY, timestampMs)
      val smoother = faceSmoothers.getOrPut(id) { TemporalLandmarkSmoother() }
      val state = smoother.update(id, centerX, centerY, item.third.third, timestampMs)
      val shiftX = state.x - centerX
      val shiftY = state.y - centerY
      val smoothedVertices = item.second.map { v ->
        Vec3(v.x + shiftX, v.y + shiftY, v.z)
      }
      FaceMeshState(smoothedVertices, item.third.first, item.third.second, item.third.third, id, item.first, timestampMs)
    }
  }

  private fun classifyRegions(mesh: FaceMesh): IntArray {
    val points = mesh.allPoints.associateBy { it.index }
    val bounds = RectF(mesh.boundingBox)
    val w = max(1f, bounds.width())
    val h = max(1f, bounds.height())
    val eye = mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.LEFT_EYE).map { it.index }.toSet()
    val eyeR = mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.RIGHT_EYE).map { it.index }.toSet()
    val brow = (mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.LEFT_EYEBROW_TOP) +
      mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.LEFT_EYEBROW_BOTTOM) +
      mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.RIGHT_EYEBROW_TOP) +
      mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.RIGHT_EYEBROW_BOTTOM)).map { it.index }.toSet()
    val mouth = (mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.UPPER_LIP_TOP) +
      mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.UPPER_LIP_BOTTOM) +
      mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.LOWER_LIP_TOP) +
      mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.LOWER_LIP_BOTTOM)).map { it.index }.toSet()
    val nose = mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.NOSE_BRIDGE).map { it.index }.toSet()
    val oval = mesh.getPoints(com.google.mlkit.vision.facemesh.FaceMesh.FACE_OVAL).map { it.index }.toSet()
    return IntArray(468) { index ->
      val p = points[index]?.position
      when {
        index in oval -> FaceRegion.FACE_BOUNDARY.ordinal
        index in eye || index in eyeR -> FaceRegion.EYE.ordinal
        index in brow -> FaceRegion.EYEBROW.ordinal
        index in mouth -> FaceRegion.MOUTH.ordinal
        index in nose -> FaceRegion.NOSE.ordinal
        p == null -> FaceRegion.OTHER.ordinal
        else -> {
          val nx = ((p.x - bounds.left) / w).coerceIn(0f, 1f)
          val ny = ((p.y - bounds.top) / h).coerceIn(0f, 1f)
          when {
            ny < .20f -> FaceRegion.FOREHEAD.ordinal
            ny > .82f -> if (nx in .28f.. .72f) FaceRegion.CHIN.ordinal else FaceRegion.JAW.ordinal
            nx < .25f || nx > .75f -> FaceRegion.CHEEK.ordinal
            ny > .68f -> FaceRegion.JAW.ordinal
            else -> FaceRegion.OTHER.ordinal
          }
        }
      }
    }
  }

  private fun mapBody(pose: Pose, timestampMs: Long): BodyState? {
    val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
    val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
    val confidence = pose.allPoseLandmarks.map { it.inFrameLikelihood }.average().toFloat()
    if (ls == null && rs == null) return null
    val map = pose.allPoseLandmarks.associate { landmark ->
      val rawX = landmark.position3D.x
      val rawY = landmark.position3D.y
      val smoother = bodySmoothers.getOrPut(landmark.landmarkType) { TemporalLandmarkSmoother() }
      val state = smoother.update(landmark.landmarkType.toLong(), rawX, rawY, landmark.inFrameLikelihood, timestampMs)
      landmark.landmarkType to Vec3(state.x, state.y, landmark.position3D.z)
    }
    return BodyState(map, confidence.coerceIn(0f, 1f))
  }

  suspend fun analyzeSuspending(bitmap: Bitmap, timestampMs: Long): Frame? =
    suspendCancellableCoroutine { continuation ->
      analyze(bitmap, timestampMs) { frame ->
        if (continuation.isActive) continuation.resume(frame)
      }
      continuation.invokeOnCancellation { }
    }

  private fun assignTrack(x: Float, y: Float, now: Long): Long {
    val maxDistance = 0.35f
    val existing = faceTracks.minByOrNull { distance(it.x, it.y, x, y) }
    if (existing != null && distance(existing.x, existing.y, x, y) <= maxDistance) {
      existing.x = x; existing.y = y; existing.lastSeenMs = now
      faceTracks.removeAll { now - it.lastSeenMs > 500L }
      return existing.id
    }
    val track = Track(nextTrackId++, x, y, now)
    faceTracks.add(track)
    faceTracks.removeAll { now - it.lastSeenMs > 500L }
    return track.id
  }

  private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return sqrt(dx * dx + dy * dy)
  }

  override fun close() {
    runCatching { faceMeshDetector?.close() }
    runCatching { poseDetector?.close() }
    runCatching { segmenter?.close() }
  }
}
