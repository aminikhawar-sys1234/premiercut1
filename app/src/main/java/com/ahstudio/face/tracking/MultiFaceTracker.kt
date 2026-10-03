package com.ahstudio.face.tracking

import com.ahstudio.face.core.*
import com.ahstudio.face.detection.RawFaceDetection
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class MultiFaceTracker(private val cfg: FaceTrackingConfig) {

    private class Track(
        val id: FaceTrackId,
        var det: NormalizedDet,
        var smoothed: FloatArray, // cx, cy, w, h, rx, ry, rz
        var velocity: Vec2,
        var lastSeenUs: Long,
        var misses: Int,
        var hits: Int,
        val smoother: FaceSmoother,
    ) {
        fun bounds(): FaceBounds {
            val w = smoothed[2]; val h = smoothed[3]
            return FaceBounds(smoothed[0] - w / 2, smoothed[1] - h / 2, smoothed[0] + w / 2, smoothed[1] + h / 2)
        }
        fun predicted(dt: Float): FaceBounds {
            val c = Vec2(smoothed[0], smoothed[1]) + velocity * dt
            val w = smoothed[2]; val h = smoothed[3]
            return FaceBounds(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
        }
    }

    private val tracks = ArrayList<Track>()
    private var nextId = 1L
    private var lastTsUs = -1L

    fun reset() { tracks.clear(); nextId = 1L; lastTsUs = -1L }

    fun update(
        raw: List<RawFaceDetection>, frameW: Int, frameH: Int,
        timestampUs: Long, mirrored: Boolean,
    ): List<TrackedFace> {
        val bigGap = lastTsUs <= 0 || abs(timestampUs - lastTsUs) > 500_000
        val dt = if (bigGap) 1f / 30f
                 else ((timestampUs - lastTsUs).coerceAtLeast(1) / 1_000_000f).coerceIn(1f / 240f, 0.5f)
        lastTsUs = timestampUs

        val dets = raw.map { it.normalized(frameW, frameH) }
        val diag = sqrt(2f)

        val detUsed = BooleanArray(dets.size)
        val trkUsed = BooleanArray(tracks.size)

        // 1) Hard-lock by ML Kit trackingId
        if (cfg.mlKitTracking) {
            for (ti in tracks.indices) {
                if (trkUsed[ti]) continue
                val tid = tracks[ti].det.trackingId ?: continue
                for (di in dets.indices) {
                    if (detUsed[di]) continue
                    if (dets[di].trackingId == tid && passesSanity(tracks[ti], dets[di])) {
                        detUsed[di] = true; trkUsed[ti] = true
                        assign(ti, di, dets, timestampUs, dt, resetMotion = bigGap)
                        break
                    }
                }
            }
        }

        // 2) Greedy min-cost association for the rest
        data class Cand(val t: Int, val d: Int, val c: Float)
        val cands = ArrayList<Cand>()
        for (ti in tracks.indices) {
            if (trkUsed[ti]) continue
            for (di in dets.indices) {
                if (detUsed[di]) continue
                val c = cost(tracks[ti], dets[di], diag, dt)
                if (c < Float.MAX_VALUE) cands.add(Cand(ti, di, c))
            }
        }
        cands.sortBy { it.c }
        for (cd in cands) {
            if (!trkUsed[cd.t] && !detUsed[cd.d]) {
                trkUsed[cd.t] = true; detUsed[cd.d] = true
                assign(cd.t, cd.d, dets, timestampUs, dt, resetMotion = bigGap)
            }
        }

        // 3) Emit matched + coast unmatched
        val out = ArrayList<TrackedFace>(tracks.size + 2)
        for (ti in tracks.indices) {
            val t = tracks[ti]
            if (trkUsed[ti]) {
                out += emit(t, timestampUs, recovered = t.misses > 0)
                t.misses = 0; t.hits++
            } else {
                t.misses++
                if (timestampUs - t.lastSeenUs <= cfg.smoothing.maxCoastTimeUs) {
                    out += emitPredicted(t, timestampUs)
                }
            }
        }

        // 4) Prune dead tracks
        for (ti in tracks.indices.reversed()) {
            val t = tracks[ti]
            if (!trkUsed[ti] && timestampUs - t.lastSeenUs > cfg.smoothing.maxCoastTimeUs) tracks.removeAt(ti)
        }

        // 5) New entrants
        for (di in dets.indices) if (!detUsed[di]) out += spawn(dets[di], timestampUs)

        return out.sortedByDescending { it.confidence }.take(cfg.maxFaces)
    }

    private fun passesSanity(t: Track, d: NormalizedDet): Boolean {
        val ratio = d.bounds.area / t.bounds().area.coerceAtLeast(1e-6f)
        return ratio in (1f / cfg.association.sizeGate)..cfg.association.sizeGate
    }

    private fun cost(t: Track, d: NormalizedDet, diag: Float, dt: Float): Float {
        val a = cfg.association
        val pb = t.predicted(dt)
        val i = iou(pb, d.bounds)
        if (i < a.iouGate) return Float.MAX_VALUE
        val cd = sqrt(
            (pb.centerX - d.bounds.centerX) * (pb.centerX - d.bounds.centerX) +
            (pb.centerY - d.bounds.centerY) * (pb.centerY - d.bounds.centerY)
        ) / diag
        if (cd > a.centroidGate) return Float.MAX_VALUE
        val ratio = d.bounds.area / t.bounds().area.coerceAtLeast(1e-6f)
        if (ratio <= 0f || ratio > a.sizeGate || 1f / ratio > a.sizeGate) return Float.MAX_VALUE
        var c = (1f - i) + cd
        if (dt > 0f && t.velocity.lengthSquared > 1e-10f) {
            val actual = Vec2(d.bounds.centerX, d.bounds.centerY) - Vec2(t.smoothed[0], t.smoothed[1])
            val expected = t.velocity * dt
            val align = if (actual.lengthSquared > 1e-12f)
                actual.div(actual.length).dot(expected.div(expected.length)) else 0f
            c += a.velocityWeight * (1f - (align + 1f) * 0.5f)
        }
        return c
    }

    private fun assign(ti: Int, di: Int, dets: List<NormalizedDet>, tsUs: Long, dt: Float, resetMotion: Boolean) {
        val t = tracks[ti]; val d = dets[di]
        val newC = Vec2(d.bounds.centerX, d.bounds.centerY)
        val oldC = Vec2(t.smoothed[0], t.smoothed[1])
        t.velocity = if (resetMotion) Vec2.ZERO
                     else t.velocity.lerp((newC - oldC) * (1f / dt), 0.6f)
        t.det = d
        val buf = FloatArray(7)
        t.smoother.smooth(d, dt, buf)
        t.smoothed = buf
        t.lastSeenUs = tsUs
    }

    private fun emit(t: Track, tsUs: Long, recovered: Boolean): TrackedFace {
        val b = t.bounds()
        return TrackedFace(
            trackId = t.id,
            detectorTrackId = t.det.trackingId,
            timestampUs = tsUs,
            bounds = b,
            rotation = FaceRotation(t.smoothed[4], t.smoothed[5], t.smoothed[6]),
            landmarks = t.det.landmarks,
            classification = t.det.classification,
            confidence = (0.55f + 0.4f * (t.hits.coerceAtMost(5) / 5f)).coerceAtMost(1f),
            state = if (recovered) FaceTrackState.RECOVERED else FaceTrackState.DETECTED,
            velocity = t.velocity,
            interOcular = t.det.landmarks.interOcular ?: (b.width * 0.46f),
        )
    }

    private fun emitPredicted(t: Track, tsUs: Long): TrackedFace {
        val coastS = ((tsUs - t.lastSeenUs) / 1_000_000f).coerceIn(0f, 0.2f)
        val lead = t.velocity * (coastS * cfg.smoothing.predictionStrength)
        val b = t.bounds()
        val moved = FaceBounds(b.left + lead.x, b.top + lead.y, b.right + lead.x, b.bottom + lead.y)
        return TrackedFace(
            trackId = t.id,
            detectorTrackId = t.det.trackingId,
            timestampUs = tsUs,
            bounds = moved,
            rotation = FaceRotation(t.smoothed[4], t.smoothed[5], t.smoothed[6]),
            landmarks = null,
            classification = t.det.classification,
            confidence = (0.5f * 0.7f.pow(t.misses.toFloat())).coerceAtLeast(0.15f),
            state = FaceTrackState.PREDICTED,
            velocity = t.velocity,
            interOcular = t.det.landmarks.interOcular ?: (moved.width * 0.46f),
        )
    }

    private fun spawn(d: NormalizedDet, tsUs: Long): TrackedFace {
        val t = Track(
            id = FaceTrackId(nextId++),
            det = d,
            smoothed = floatArrayOf(
                d.bounds.centerX, d.bounds.centerY, d.bounds.width, d.bounds.height,
                d.rotation.eulerX, d.rotation.eulerY, d.rotation.eulerZ),
            velocity = Vec2.ZERO,
            lastSeenUs = tsUs,
            misses = 0, hits = 1,
            smoother = FaceSmoother(cfg.smoothing),
        )
        tracks.add(t)
        return emit(t, tsUs, recovered = false)
    }
}
