package com.ahstudio.animation.keyframes

import com.ahstudio.animation.curves.CubicBezierTiming
import com.ahstudio.animation.easing.EasingType
import com.ahstudio.animation.math.Vec2
import kotlin.math.roundToLong

/** Immutable, time-sorted, duplicate-free keyframe set with a version for cache invalidation. */
class KeyframeTrackData private constructor(
    val keyframes: List<Keyframe>,
    val version: Long
) {
    val startTimeMs: Long get() = keyframes.first().timeMs
    val endTimeMs: Long get() = keyframes.last().timeMs
    fun byId(id: KeyframeId): Keyframe? = keyframes.firstOrNull { it.id == id }

    companion object {
        val EMPTY = KeyframeTrackData(emptyList(), 0L)
        /** Normalizes: sorts by time; duplicate times -> keep LAST (deterministic collision rule). */
        fun of(list: List<Keyframe>, version: Long): KeyframeTrackData {
            val sorted = list.sortedBy { it.timeMs }
            val deduped = ArrayList<Keyframe>(sorted.size)
            for (kf in sorted) {
                if (deduped.isNotEmpty() && deduped.last().timeMs == kf.timeMs) deduped[deduped.size - 1] = kf
                else deduped.add(kf)
            }
            return KeyframeTrackData(deduped, version)
        }
        /** Last index i with kfs[i].timeMs <= t. -1 = before first, n-1 = at/after last, -2 = empty. */
        fun segmentIndex(kfs: List<Keyframe>, t: Long): Int {
            val n = kfs.size
            if (n == 0) return -2
            if (t < kfs[0].timeMs) return -1
            if (t >= kfs[n - 1].timeMs) return n - 1
            var lo = 0; var hi = n - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (kfs[mid].timeMs <= t) lo = mid else hi = mid - 1
            }
            return lo
        }
    }
}

/** Pure editing operations -- every op returns NEW immutable data (copy-on-write). No UI imports. */
object KeyframeOps {
    private fun rebuild(old: KeyframeTrackData, list: List<Keyframe>): KeyframeTrackData =
        KeyframeTrackData.of(list, old.version + 1)

    fun upsert(data: KeyframeTrackData, kf: Keyframe): KeyframeTrackData {
        val list = ArrayList(data.keyframes)
        val byId = list.indexOfFirst { it.id == kf.id }
        if (byId >= 0) { list[byId] = kf; return rebuild(data, list) }
        val byTime = list.indexOfFirst { it.timeMs == kf.timeMs }
        if (byTime >= 0) list[byTime] = kf else list.add(kf)
        return rebuild(data, list)
    }

    fun delete(data: KeyframeTrackData, ids: Set<KeyframeId>): KeyframeTrackData =
        rebuild(data, data.keyframes.filterNot { it.id in ids })

    /** Collision rule: moved keyframes replace stationary keyframes landing on the same time. */
    fun move(data: KeyframeTrackData, ids: Set<KeyframeId>, deltaMs: Long): KeyframeTrackData {
        val moving = data.keyframes.filter { it.id in ids }
        if (moving.isEmpty()) return data
        val movedTimes = moving.map { it.timeMs + deltaMs }.toHashSet()
        val kept = data.keyframes.filter { it.id !in ids && it.timeMs !in movedTimes }
        val shifted = moving.map { it.copy(timeMs = it.timeMs + deltaMs) }
        return rebuild(data, kept + shifted)
    }

    fun setTime(data: KeyframeTrackData, id: KeyframeId, newTimeMs: Long): KeyframeTrackData {
        val kf = data.byId(id) ?: return data
        return upsert(data, kf.copy(timeMs = newTimeMs))
    }

    fun duplicate(data: KeyframeTrackData, ids: Set<KeyframeId>, offsetMs: Long, newId: () -> KeyframeId): KeyframeTrackData {
        val copies = data.keyframes.filter { it.id in ids }
            .map { it.copy(id = newId(), timeMs = it.timeMs + offsetMs) }
        if (copies.isEmpty()) return data
        return rebuild(data, data.keyframes + copies)
    }

    fun scaleTimes(data: KeyframeTrackData, ids: Set<KeyframeId>, pivotMs: Long, factor: Double): KeyframeTrackData {
        val f = if (factor.isFinite()) factor.coerceIn(0.001, 1000.0) else 1.0
        return rebuild(data, data.keyframes.map {
            if (it.id in ids) it.copy(timeMs = pivotMs + ((it.timeMs - pivotMs) * f).roundToLong()) else it
        })
    }

    /** Reverse within [start,end]: swap in/out tangents (negated) and spatial handles. */
    fun reverseRange(data: KeyframeTrackData, startMs: Long, endMs: Long): KeyframeTrackData =
        rebuild(data, data.keyframes.map { kf ->
            if (kf.timeMs < startMs || kf.timeMs > endMs) kf else kf.copy(
                timeMs = startMs + (endMs - kf.timeMs),
                inTangent = -kf.outTangent, outTangent = -kf.inTangent,
                spatialInHandle = kf.spatialOutHandle, spatialOutHandle = kf.spatialInHandle
            )
        })

    fun mirror(data: KeyframeTrackData, pivotMs: Long): KeyframeTrackData =
        rebuild(data, data.keyframes.map { kf ->
            kf.copy(
                timeMs = 2 * pivotMs - kf.timeMs,
                inTangent = -kf.outTangent, outTangent = -kf.inTangent,
                spatialInHandle = kf.spatialOutHandle, spatialOutHandle = kf.spatialInHandle
            )
        })

    fun quantize(data: KeyframeTrackData, ids: Set<KeyframeId>, gridMs: Long): KeyframeTrackData {
        if (gridMs <= 0) return data
        return rebuild(data, data.keyframes.map {
            if (it.id in ids) it.copy(timeMs = (it.timeMs.toDouble() / gridMs).roundToLong() * gridMs) else it
        })
    }

    fun distributeEvenly(data: KeyframeTrackData, ids: Set<KeyframeId>): KeyframeTrackData {
        val sel = data.keyframes.filter { it.id in ids }.sortedBy { it.timeMs }
        if (sel.size < 2) return data
        val t0 = sel.first().timeMs; val t1 = sel.last().timeMs
        val step = (t1 - t0).toDouble() / (sel.size - 1)
        val map = sel.mapIndexed { i, kf -> kf.id to (t0 + (step * i).roundToLong()) }.toMap()
        return rebuild(data, data.keyframes.map { map[it.id]?.let { t -> it.copy(timeMs = t) } ?: it })
    }

    fun alignSelectionTo(data: KeyframeTrackData, ids: Set<KeyframeId>, targetMs: Long): KeyframeTrackData {
        val sel = data.keyframes.filter { it.id in ids }
        if (sel.isEmpty()) return data
        val shift = targetMs - sel.minOf { it.timeMs }
        if (shift == 0L) return data
        return rebuild(data, data.keyframes.map { if (it.id in ids) it.copy(timeMs = it.timeMs + shift) else it })
    }

    fun setInterpolation(data: KeyframeTrackData, ids: Set<KeyframeId>, type: InterpolationType): KeyframeTrackData =
        rebuild(data, data.keyframes.map { if (it.id in ids) it.copy(interpolation = type) else it })

    fun setEasing(data: KeyframeTrackData, ids: Set<KeyframeId>, easing: EasingType): KeyframeTrackData =
        rebuild(data, data.keyframes.map { if (it.id in ids) it.copy(easing = easing) else it })

    fun setTangents(data: KeyframeTrackData, id: KeyframeId, tin: Double, tout: Double, mode: TangentMode): KeyframeTrackData {
        val kf = data.byId(id) ?: return data
        return upsert(data, kf.copy(inTangent = tin, outTangent = tout, tangentMode = mode))
    }

    fun setSpatialHandles(data: KeyframeTrackData, id: KeyframeId, inH: Vec2?, outH: Vec2?): KeyframeTrackData {
        val kf = data.byId(id) ?: return data
        return upsert(data, kf.copy(spatialInHandle = inH, spatialOutHandle = outH))
    }

    fun setCustomBezier(data: KeyframeTrackData, id: KeyframeId, bez: CubicBezierTiming?): KeyframeTrackData {
        val kf = data.byId(id) ?: return data
        return upsert(data, kf.copy(bezier = bez, interpolation = InterpolationType.CUSTOM_CURVE))
    }
}

/** Structured copy/paste -- relative timing preserved, raw object refs NOT copied. */
data class CopiedKeyframe(
    val relTimeMs: Long, val value: Double, val vecValue: Vec2?,
    val interpolation: InterpolationType, val easing: EasingType,
    val bez: CubicBezierTiming?, val inT: Double, val outT: Double,
    val tangentMode: TangentMode, val hIn: Vec2?, val hOut: Vec2?,
    val metadata: Map<String, String>
)

object KeyframeClipboard {
    @Volatile private var contents: List<CopiedKeyframe> = emptyList()
    @Volatile var copiedType: String = "FLOAT"; private set

    fun copy(kfs: List<Keyframe>, type: String) {
        if (kfs.isEmpty()) return
        val t0 = kfs.minOf { it.timeMs }
        contents = kfs.sortedBy { it.timeMs }.map {
            CopiedKeyframe(it.timeMs - t0, it.value, it.vecValue, it.interpolation, it.easing,
                it.bezier, it.inTangent, it.outTangent, it.tangentMode,
                it.spatialInHandle, it.spatialOutHandle, it.metadata)
        }
        copiedType = type
    }
    fun isEmpty() = contents.isEmpty()
    /** Paste at [atTimeMs]; ids are fresh. Caller verifies type compatibility. */
    fun paste(atTimeMs: Long, newId: () -> KeyframeId): List<Keyframe> =
        contents.map {
            Keyframe(
                id = newId(), timeMs = atTimeMs + it.relTimeMs, value = it.value, vecValue = it.vecValue,
                interpolation = it.interpolation, easing = it.easing, bezier = it.bez,
                inTangent = it.inT, outTangent = it.outT, tangentMode = it.tangentMode,
                spatialInHandle = it.hIn, spatialOutHandle = it.hOut, metadata = it.metadata
            )
        }
}
