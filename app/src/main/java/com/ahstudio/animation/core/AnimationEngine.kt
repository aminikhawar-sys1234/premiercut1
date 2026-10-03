package com.ahstudio.animation.core

import com.ahstudio.animation.diagnostics.AnimationProfiler
import com.ahstudio.animation.easing.EasingType
import com.ahstudio.animation.keyframes.*
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.math.isFinite
import com.ahstudio.animation.properties.AnimatableProperty
import com.ahstudio.animation.properties.BindingKey
import com.ahstudio.animation.properties.PropertyType
import com.ahstudio.animation.procedural.Spring
import com.ahstudio.animation.procedural.Wiggle
import com.ahstudio.animation.undo.KeyframeEditSession
import com.ahstudio.animation.undo.UndoSink
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * THE animation engine. Owns NO clock: host passes authoritative timeline time into every
 * evaluate() call.
 */
class AnimationEngine(
    val timeSource: () -> Long = { 0L }
) {
    fun interface ValueSource { fun value(timeMs: Long): EvaluatedValue? }

    sealed class SourceDescriptor {
        data class WiggleDesc(val seed: Long, val freqHz: Double, val amplitude: Double,
                              val octaves: Int, val vec2: Boolean) : SourceDescriptor()
        data class SpringDesc(val from: Double, val to: Double, val startMs: Long, val durationMs: Long,
                              val stiffness: Double, val damping: Double, val mass: Double) : SourceDescriptor()
    }

    private val tracks = ConcurrentHashMap<BindingKey, AnimationTrack>()
    private val properties = ConcurrentHashMap<BindingKey, AnimatableProperty>()
    private val sources = ConcurrentHashMap<BindingKey, ValueSource>()
    val sourceDescriptors = ConcurrentHashMap<BindingKey, SourceDescriptor>()
    private val caches = ConcurrentHashMap<BindingKey, TrackEvalCache>()
    private val clips = ConcurrentHashMap<String, AnimationClip>()
    private val markers = CopyOnWriteArrayList<AnimationMarker>()
    private val idGen = AtomicLong(1L)
    private val animatableMarked = ConcurrentHashMap.newKeySet<BindingKey>()

    @Volatile var autoKeyframeEnabled = false
    @Volatile var undoSink: UndoSink? = null
    @Volatile var version: Long = 0L; private set
    val profiler = AnimationProfiler()

    fun nextId(): KeyframeId = KeyframeId(idGen.getAndIncrement())
    fun currentTimeMs(): Long = timeSource()
    fun bumpVersion() { version++ }
    internal fun applyStates(target: Map<BindingKey, AnimationTrack.State?>) {
        for ((k, s) in target) {
            if (s == null) tracks.remove(k)
            else {
                val existing = tracks[k]
                if (existing == null) tracks[k] = AnimationTrack(k, s)
                else existing.forceState(s)
            }
            caches.remove(k)
        }
        bumpVersion()
    }

    // ---------------- property registration ----------------
    fun registerProperty(p: AnimatableProperty) { properties[p.key] = p }
    fun unregisterProperty(key: BindingKey) { properties.remove(key) }
    fun registeredProperties(): Map<BindingKey, AnimatableProperty> = properties.toMap()
    fun markAnimatable(key: BindingKey) { animatableMarked.add(key) }

    // ---------------- track management ----------------
    fun trackFor(key: BindingKey): AnimationTrack? = tracks[key]
    fun allTracks(): Map<BindingKey, AnimationTrack> = tracks.toMap()
    fun tracksForTarget(targetId: String): List<AnimationTrack> =
        tracks.entries.filter { it.key.targetId == targetId }.map { it.value }
    fun hasTrack(key: BindingKey) = tracks.containsKey(key)

    fun ensureTrack(key: BindingKey, type: PropertyType, spatial: Boolean = type == PropertyType.VEC2): AnimationTrack =
        tracks.computeIfAbsent(key) {
            AnimationTrack(key, AnimationTrack.State(type = type, spatial = spatial)).also { bumpVersion() }
        }

    fun removeTrack(key: BindingKey) { if (tracks.remove(key) != null) { caches.remove(key); bumpVersion() } }
    fun clear() { tracks.clear(); caches.clear(); sources.clear(); sourceDescriptors.clear(); markers.clear(); bumpVersion() }

    // ---------------- evaluation ----------------
    fun evaluate(timeMs: Long): AnimationSnapshot = profiler.measure {
        val map = HashMap<BindingKey, EvaluatedValue>(tracks.size + sources.size)
        for ((k, tr) in tracks) {
            val v = tr.evaluate(timeMs, caches.computeIfAbsent(k) { TrackEvalCache() })
            if (v != null) map[k] = v
        }
        for ((k, src) in sources) src.value(timeMs)?.let { map[k] = it }
        profiler.propertiesEvaluated.addAndGet(map.size.toLong())
        AnimationSnapshot(timeMs, map)
    }

    fun evaluateKey(key: BindingKey, timeMs: Long): EvaluatedValue? {
        sources[key]?.let { return it.value(timeMs) }
        val tr = tracks[key] ?: return null
        return tr.evaluate(timeMs, caches.computeIfAbsent(key) { TrackEvalCache() })
    }

    fun evaluateTarget(targetId: String, timeMs: Long): Map<BindingKey, EvaluatedValue> {
        val out = HashMap<BindingKey, EvaluatedValue>()
        for ((k, _) in tracks) if (k.targetId == targetId) evaluateKey(k, timeMs)?.let { out[k] = it }
        for ((k, _) in sources) if (k.targetId == targetId) evaluateKey(k, timeMs)?.let { out[k] = it }
        return out
    }

    fun applyToRegisteredProperties(timeMs: Long): Int {
        val snap = evaluate(timeMs)
        var n = 0
        for ((k, v) in snap.values) properties[k]?.let { if (it.apply(v)) n++ }
        return n
    }

    // ---------------- auto-keyframe / user value change ----------------
    fun notifyUserValueChange(key: BindingKey, value: Double, timeMs: Long): Boolean {
        if (!isFinite(value)) return false
        val tr = tracks[key]
        if (tr != null) {
            if (!tr.hasKeyframes && !autoKeyframeEnabled) return false
            editSession(key).upsertKeyframe(key, Keyframe(nextId(), timeMs, value = value))
                .commit("Auto-key ${key.property}")
            return true
        }
        if (autoKeyframeEnabled && key in animatableMarked) {
            val t = ensureTrack(key, properties[key]?.type ?: PropertyType.FLOAT)
            t.forceState(t.get().copy(data = KeyframeOps.upsert(t.get().data, Keyframe(nextId(), timeMs, value = value))))
            bumpVersion(); return true
        }
        return false
    }

    fun notifyUserValueChange(key: BindingKey, value: Vec2, timeMs: Long): Boolean {
        if (!isFinite(value)) return false
        val tr = tracks[key]
        if (tr != null) {
            if (!tr.hasKeyframes && !autoKeyframeEnabled) return false
            editSession(key).upsertKeyframe(key, Keyframe(nextId(), timeMs, vecValue = value))
                .commit("Auto-key ${key.property}")
            return true
        }
        if (autoKeyframeEnabled && key in animatableMarked) {
            val t = ensureTrack(key, PropertyType.VEC2, spatial = true)
            t.forceState(t.get().copy(data = KeyframeOps.upsert(t.get().data, Keyframe(nextId(), timeMs, vecValue = value))))
            bumpVersion(); return true
        }
        return false
    }

    // ---------------- editing ----------------
    fun editSession(vararg keys: BindingKey) = KeyframeEditSession(this, keys.toList())
    fun editSession(keys: List<BindingKey>) = KeyframeEditSession(this, keys)

    fun addKeyframe(key: BindingKey, kf: Keyframe): Boolean {
        ensureTrack(key, if (kf.vecValue != null) PropertyType.VEC2 else PropertyType.FLOAT, kf.vecValue != null)
        return editSession(key).upsertKeyframe(key, kf).commit("Add keyframe") != null
    }
    fun deleteKeyframes(key: BindingKey, ids: Set<KeyframeId>): Boolean =
        editSession(key).deleteKeyframes(key, ids).commit("Delete keyframes") != null
    fun moveKeyframes(key: BindingKey, ids: Set<KeyframeId>, deltaMs: Long): Boolean =
        editSession(key).moveKeyframes(key, ids, deltaMs).commit("Move keyframes") != null
    fun setKeyframeEasing(key: BindingKey, ids: Set<KeyframeId>, easing: EasingType): Boolean =
        editSession(key).setEasing(key, ids, easing).commit("Change easing") != null

    // ---------------- procedural / spring / sources ----------------
    fun setWiggle(key: BindingKey, wiggle: Wiggle) {
        sources[key] = ValueSource { t -> EvaluatedValue.FloatV(wiggle.value(t), 0.0) }
        sourceDescriptors[key] = SourceDescriptor.WiggleDesc(wiggle.seed, wiggle.freqHz, wiggle.amplitude, wiggle.octaves, false)
        bumpVersion()
    }
    fun setWiggleVec2(key: BindingKey, wiggle: Wiggle) {
        sources[key] = ValueSource { t -> EvaluatedValue.Vec2V(wiggle.vec2(t), Vec2.ZERO) }
        sourceDescriptors[key] = SourceDescriptor.WiggleDesc(wiggle.seed, wiggle.freqHz, wiggle.amplitude, wiggle.octaves, true)
        bumpVersion()
    }
    fun setSpring(key: BindingKey, from: Double, to: Double, startMs: Long, durationMs: Long, spring: Spring) {
        sources[key] = ValueSource { t ->
            if (t <= startMs) EvaluatedValue.FloatV(from, 0.0)
            else {
                val tc = (t - startMs).coerceAtMost(durationMs)
                EvaluatedValue.FloatV(spring.value(from, to, tc / 1000.0), spring.velocity(from, to, tc))
            }
        }
        sourceDescriptors[key] = SourceDescriptor.SpringDesc(from, to, startMs, durationMs,
            spring.stiffness, spring.damping, spring.mass)
        bumpVersion()
    }
    fun setSource(key: BindingKey, src: ValueSource) { sources[key] = src; sourceDescriptors.remove(key); bumpVersion() }
    fun clearSource(key: BindingKey) { if (sources.remove(key) != null) { sourceDescriptors.remove(key); bumpVersion() } }
    fun activeSourceKeys(): Set<BindingKey> = sources.keys.toSet()

    // ---------------- clips / presets ----------------
    fun addClip(clip: AnimationClip) { clips[clip.id] = clip; bumpVersion() }
    fun removeClip(id: String) { if (clips.remove(id) != null) bumpVersion() }
    fun clips(): List<AnimationClip> = clips.values.toList()
    fun installBuiltinPresets() { BuiltinPresets.all().forEach { addClip(it) } }

    fun applyClip(clipId: String, targetId: String, startMs: Long, replaceExisting: Boolean = false): List<BindingKey> {
        val clip = clips[clipId] ?: return emptyList()
        val applied = ArrayList<BindingKey>()
        for (ct in clip.tracks) {
            val key = BindingKey(targetId, ct.property)
            if (replaceExisting) removeTrack(key)
            val tr = ensureTrack(key, ct.type, spatial = ct.type == PropertyType.VEC2)
            val st = tr.get()
            var data = st.data
            for (kf in ct.keyframes) {
                val fixed = if (kf.id == KeyframeId(0L)) kf.copy(id = nextId(), timeMs = kf.timeMs + startMs)
                            else kf.copy(timeMs = kf.timeMs + startMs)
                data = KeyframeOps.upsert(data, fixed)
            }
            tr.forceState(st.copy(data = data, loop = ct.loop))
            applied.add(key)
        }
        bumpVersion()
        return applied
    }

    // ---------------- markers ----------------
    fun addMarker(timeMs: Long, name: String, category: String = "", color: Int = 0): AnimationMarker {
        val m = AnimationMarker(idGen.getAndIncrement(), timeMs, name, category, color)
        markers.add(m); bumpVersion(); return m
    }
    fun removeMarker(id: Long) { if (markers.removeIf { it.id == id }) bumpVersion() }
    fun markers(): List<AnimationMarker> = markers.toList()
    fun markersAt(timeMs: Long, toleranceMs: Long = 0): List<AnimationMarker> =
        markers.filter { abs(it.timeMs - timeMs) <= toleranceMs }
    fun nearestMarker(timeMs: Long): AnimationMarker? = markers.minByOrNull { abs(it.timeMs - timeMs) }

    private fun abs(v: Long) = kotlin.math.abs(v)
}

/** Immutable per-frame result -- consumed by Composition. */
class AnimationSnapshot(
    val timeMs: Long,
    val values: Map<BindingKey, EvaluatedValue>
) {
    fun floatValue(key: BindingKey): Double? =
        (values[key] as? EvaluatedValue.FloatV)?.value
    fun vec2Value(key: BindingKey): Vec2? =
        (values[key] as? EvaluatedValue.Vec2V)?.value
    fun velocityOf(key: BindingKey): Vec2? = when (val v = values[key]) {
        is EvaluatedValue.FloatV -> Vec2(v.velocityPerSec, 0.0)
        is EvaluatedValue.Vec2V -> v.velocityPerSec
        null -> null
    }
}
