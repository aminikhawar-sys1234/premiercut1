package com.ahstudio.face.core

import com.ahstudio.face.detection.FaceDetector
import com.ahstudio.face.detection.FrameProvider
import com.ahstudio.face.performance.TrackingCache
import com.ahstudio.face.tracking.MultiFaceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

class FaceTrackingEngine(
    private val detector: FaceDetector,
    private val frames: FrameProvider,
    private val cfg: FaceTrackingConfig,
) {
    private val detectDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "face-detect").apply { isDaemon = true } }
            .asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + detectDispatcher)
    private val tracker = MultiFaceTracker(cfg)
    val cache = TrackingCache()

    /** Full pipeline: cache -> detect -> track -> smooth -> cache. Deterministic per timestamp. */
    suspend fun resultAt(clipId: String, sourceTimeUs: Long, transformHash: Int, mirrored: Boolean): FaceTrackingResult {
        cache.get(clipId, transformHash, sourceTimeUs)?.let { return it }
        val dims = frames.dimensionsOf(clipId) ?: return FaceTrackingResult.EMPTY
        val frame = frames.frame(clipId, sourceTimeUs) ?: return FaceTrackingResult.EMPTY
        val raw = try { detector.detect(frame, cfg.maxFaces) } catch (t: Throwable) { emptyList() }
        val faces = tracker.update(raw, dims.first, dims.second, sourceTimeUs, mirrored)
        val result = FaceTrackingResult(sourceTimeUs, dims.first, dims.second, mirrored, faces, DetectionSource.DETECTOR)
        cache.put(clipId, transformHash, result)
        return result
    }

    /** Non-suspending path for the render thread: cache hit, else fire-and-forget detection. */
    fun resultAtRenderTime(clipId: String, sourceTimeUs: Long, transformHash: Int, mirrored: Boolean): FaceTrackingResult {
        cache.get(clipId, transformHash, sourceTimeUs)?.let { return it }
        scope.launch { resultAt(clipId, sourceTimeUs, transformHash, mirrored) }
        return FaceTrackingResult.EMPTY
    }

    /** Blocking path for scrubbing / frame-stepping. */
    fun resultAtBlocking(clipId: String, sourceTimeUs: Long, transformHash: Int,
                         mirrored: Boolean, timeoutMs: Long = 80): FaceTrackingResult = runBlocking {
        withTimeoutOrNull(timeoutMs) { resultAt(clipId, sourceTimeUs, transformHash, mirrored) }
            ?: cache.get(clipId, transformHash, sourceTimeUs, toleranceUs = Long.MAX_VALUE / 2)
            ?: FaceTrackingResult.EMPTY
    }

    /** Pre-warm a range. */
    fun prewarm(clipId: String, fromUs: Long, toUs: Long, stepUs: Long, transformHash: Int, mirrored: Boolean) {
        scope.launch {
            var t = fromUs
            while (t <= toUs) { resultAt(clipId, t, transformHash, mirrored); t += stepUs }
        }
    }

    fun onConfigChanged() { tracker.reset(); cache.bumpGeneration() }
    fun onSeek() { tracker.reset() }
    fun shutdown() { scope.cancel(); detectDispatcher.close(); frames.close() }
}
