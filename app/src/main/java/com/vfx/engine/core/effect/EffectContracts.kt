package com.vfx.engine.core.effect

import com.vfx.engine.core.blend.BlendMode
import com.vfx.engine.core.curve.Curve
import com.vfx.engine.core.device.RenderRequirements
import com.vfx.engine.core.mask.Mask
import com.vfx.engine.core.mask.MaskCodec
import com.vfx.engine.core.params.Easings
import com.vfx.engine.core.params.KeyframeTrack
import com.vfx.engine.core.params.KeyframeTracks
import com.vfx.engine.core.params.ParamDescriptor
import com.vfx.engine.core.params.ParamValue
import com.vfx.engine.core.serialization.Json

/** Broad grouping for editor UI + discovery. */
enum class EffectCategory {
    COLOR, LUT, BLUR, SHARPEN, LIGHT, VIGNETTE,
    DISTORTION, STYLIZE, NOISE, CHROMATIC, TEMPORAL, COMPOSITING,
    RETOUCH, TRANSFORM
}

/**
 * Color space an effect's math expects.
 * The pipeline automatically inserts srgb<->linear conversion passes between
 * effects that declare different spaces — effects never convert manually.
 */
enum class WorkingSpace { GAMMA, LINEAR }

/**
 * Fully-resolved parameter values at one media timestamp.
 * Immutable value bag — safe to pass across threads; GPU runtimes read from it.
 */
class EffectSnapshot(
    val effectId: String,
    val timeUs: Long,
    private val values: Map<String, Any>,
    val intensity: Float,
    val mask: Mask? = null,
    val blendMode: BlendMode = BlendMode.NORMAL
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> get(id: String): T = values[id] as T
    fun float(id: String): Float = get(id)
    fun int(id: String): Int = get(id)
    fun bool(id: String): Boolean = get(id)
    fun string(id: String): String = get(id)
    fun floats(id: String): FloatArray = get(id)
    fun curve(id: String): Curve = get(id)
    fun has(id: String): Boolean = values.containsKey(id)
}

/**
 * Core-level runtime contract: LIFECYCLE only (no GPU types — keeps core
 * dependency-free). GPU rendering contract is [GpuEffectRuntime] in engine-gpu,
 * which extends this interface. Runtimes are owned by the GL thread.
 */
interface EffectRuntime {
    /** Color space the effect's math expects; pipeline converts around it. */
    val workingSpace: WorkingSpace

    /** Device requirements; pipeline checks and degrades gracefully if unmet. */
    val requirements: RenderRequirements get() = RenderRequirements()

    /** Called once per frame before passes are built (temporal effects use it). */
    fun onFrameStart(timeUs: Long) {}

    /** Reset internal state (seek, scrub, stack reset). */
    fun reset() {}

    /** Release all held resources. Called on engine release / context loss. */
    fun release() {}
}

/**
 * Immutable metadata + runtime factory.
 * Registration is the ONLY extension point needed to add effects —
 * no core renderer modification, ever.
 */
class EffectDefinition(
    val id: String,
    val name: String,
    val category: EffectCategory,
    val params: List<ParamDescriptor<*>> = emptyList(),
    val description: String = "",
    val requirements: RenderRequirements = RenderRequirements(),
    val aliases: List<String> = emptyList(),
    val runtimeFactory: (EffectInstance) -> EffectRuntime = { object : EffectRuntime { override val workingSpace = WorkingSpace.GAMMA } }
) {
    val displayName: String get() = name

    constructor(
        id: String,
        displayName: String,
        category: EffectCategory,
        parameters: List<com.vfx.engine.core.params.ParameterDescriptor> = emptyList(),
        requirements: RenderRequirements = RenderRequirements()
    ) : this(
        id = id,
        name = displayName,
        category = category,
        params = emptyList(),
        description = "",
        requirements = requirements,
        aliases = emptyList(),
        runtimeFactory = { object : EffectRuntime { override val workingSpace = WorkingSpace.GAMMA } }
    )

    init {
        require(id.isNotBlank()) { "Effect id required" }
        val dupes = params.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "Effect '$id' has duplicate param ids: $dupes" }
        aliases.forEach { require(it.isNotBlank()) { "Blank alias on '$id'" } }
    }

    fun createRuntime(instance: EffectInstance): EffectRuntime = runtimeFactory(instance)

    val paramIds: Set<String> get() = params.map { it.id }.toSet()
}

/**
 * Base effect interface for effects with a definition.
 */
interface Effect {
    val definition: EffectDefinition
}

/**
 * A live effect: parameter values, enable state, intensity, keyframes,
 * optional mask + blend mode. Host-model object — NOT thread-bound.
 * Snapshot evaluation ([snapshotAt]) is pure and safe from any thread.
 */
class EffectInstance(val definition: EffectDefinition) {

    private val values: LinkedHashMap<String, ParamValue<*>> = LinkedHashMap()

    var enabled: Boolean = true

    /** Global effect strength 0..1 — mixed against the effect result. */
    var intensity: Float = 1f
        set(v) { field = v.coerceIn(0f, 1f) }

    /** Optional mask restricting where the effect applies. */
    var mask: Mask? = null

    /** Blend mode used when compositing the masked effect over the input. */
    var blendMode: BlendMode = BlendMode.NORMAL

    companion object {
        private fun <T> initParamValue(d: ParamDescriptor<T>): ParamValue<T> = ParamValue(d, d.default)
    }

    init {
        definition.params.forEach { values[it.id] = initParamValue(it) }
    }

    val id: String get() = definition.id
    val name: String get() = definition.name
    val effectId: String get() = definition.id
    val isEnabled: Boolean get() = enabled
    val instanceId: String = java.util.UUID.randomUUID().toString()
    val parameters: com.vfx.engine.core.params.ParamMap get() = com.vfx.engine.core.params.ParamMap()

    fun paramIds(): Set<String> = values.keys
    fun hasParam(pid: String): Boolean = values.containsKey(pid)

    // ---------- parameter access ----------
    @Suppress("UNCHECKED_CAST")
    fun <T> setParam(pid: String, value: T) {
        val pv = values[pid]
            ?: throw com.vfx.engine.core.EffectEngineException.InvalidParameter(definition.id, pid, value)
        (pv as ParamValue<T>).set(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getParam(pid: String): T {
        val pv = values[pid]
            ?: throw com.vfx.engine.core.EffectEngineException.InvalidParameter(definition.id, pid, null)
        return (pv as ParamValue<T>).static
    }

    // ---------- keyframes ----------
    @Suppress("UNCHECKED_CAST")
    fun <T> setKeyframeTrack(pid: String, track: KeyframeTrack<T>) {
        val pv = values[pid]
            ?: throw com.vfx.engine.core.EffectEngineException.InvalidParameter(definition.id, pid, null)
        (pv as ParamValue<T>).setKeyframed(track)
    }

    fun clearKeyframes(pid: String) { values[pid]?.track = null }
    fun paramHasTrack(pid: String): Boolean = values[pid]?.track?.isEmpty == false

    /** Remove all keyframes on every parameter. */
    fun clearAllKeyframes() = values.values.forEach { it.track = null }

    // ---------- evaluation ----------
    /** Resolve all parameters at a media timestamp. Pure + deterministic. */
    fun snapshotAt(timeUs: Long): EffectSnapshot {
        val m = HashMap<String, Any>(values.size)
        values.forEach { (k, pv) -> m[k] = pv.valueAt(timeUs) as Any }
        return EffectSnapshot(definition.id, timeUs, m, intensity, mask, blendMode)
    }

    fun reset() {
        values.values.forEach { it.reset() }
        intensity = 1f
        enabled = true
        mask = null
        blendMode = BlendMode.NORMAL
    }

    // ---------- portable serialization ----------
    fun serialize(): Json.Obj {
        val params = LinkedHashMap<String, Json>()
        values.forEach { (pid, pv) ->
            val fields = LinkedHashMap<String, Json>()
            fields["type"] = Json.Str(pv.descriptor.jsonType)
            fields["value"] = valueToJson(pv.descriptor.jsonType, pv.valueAt(0L))
            pv.track?.let { tr ->
                fields["keyframes"] = Json.Arr(tr.keyframes().map { kf ->
                    Json.obj(
                        "t" to Json.Num(kf.timeUs.toDouble()),
                        "v" to valueToJson(pv.descriptor.jsonType, kf.value),
                        "e" to Json.Str(Easings.nameOf(kf.easing))
                    )
                })
            }
            params[pid] = Json.Obj(fields)
        }
        return Json.obj(
            "effectId" to Json.Str(definition.id),
            "enabled" to Json.Bool(enabled),
            "intensity" to Json.Num(intensity.toDouble()),
            "blendMode" to Json.Str(blendMode.name),
            "params" to Json.Obj(params),
            "mask" to (mask?.let { MaskCodec.toJson(it) } ?: Json.Null)
        )
    }

    /** Restores state from [deserialize]-shaped JSON. Unknown param ids are skipped (forward-compatible). */
    internal fun deserializeState(o: Json.Obj) {
        enabled = o["enabled"]?.asBool() ?: true
        intensity = (o["intensity"]?.asFloat() ?: 1f).coerceIn(0f, 1f)
        o["blendMode"]?.asStr()?.let { name ->
            blendMode = BlendMode.entries.firstOrNull { it.name == name } ?: BlendMode.NORMAL
        }
        mask = o["mask"]?.takeIf { it !is Json.Null }?.asObj()?.let { MaskCodec.fromJson(it) }
        val params = o["params"]?.asObj() ?: return
        params.fields.forEach { (pid, pj) ->
            val pjO = pj.asObj() ?: return@forEach
            val pv = values[pid] ?: return@forEach   // unknown/renamed param: skip
            decodeInto(pv, pjO)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeInto(pv: ParamValue<*>, o: Json.Obj) {
        val jsonType = pv.descriptor.jsonType
        o["value"]?.takeIf { it !is Json.Null }?.let { v ->
            jsonToValue(jsonType, v)?.let { decoded ->
                try { (pv as ParamValue<Any>).set(decoded) } catch (_: Exception) { /* out-of-range: keep default */ }
            }
        }
        (o["keyframes"]?.asArr())?.let { arr ->
            decodeTrack(jsonType, arr.items)?.let { track ->
                (pv as ParamValue<Any>).setKeyframed(track)
            }
        }
    }

    // ---- JSON value codecs per param type ----
    private fun valueToJson(jsonType: String, v: Any?): Json = when (jsonType) {
        "float" -> Json.Num((v as? Float ?: 0f).toDouble())
        "int" -> Json.Num((v as? Int ?: 0).toDouble())
        "bool" -> Json.Bool(v as? Boolean ?: false)
        "enum", "string" -> Json.Str(v as? String ?: "")
        "texture" -> Json.Num((v as? Int ?: -1).toDouble())
        "color", "vec2", "vec3", "vec4" ->
            Json.Arr((v as? FloatArray ?: FloatArray(0)).map { Json.Num(it.toDouble()) })
        "curve" -> Json.Arr((v as? Curve ?: Curve.LINEAR).points.map {
            Json.Arr(listOf(Json.Num(it.first.toDouble()), Json.Num(it.second.toDouble())))
        })
        "gradient" -> Json.Arr((v as? List<Pair<Float, FloatArray>> ?: emptyList()).map { stop ->
            Json.obj(
                "p" to Json.Num(stop.first.toDouble()),
                "c" to Json.Arr(stop.second.map { Json.Num(it.toDouble()) })
            )
        })
        "mat4" -> Json.Arr((v as? com.vfx.engine.core.math.Mat4
            ?: com.vfx.engine.core.math.Mat4()).m.map { Json.Num(it.toDouble()) })
        else -> Json.Null
    }

    private fun jsonToValue(jsonType: String, j: Json): Any? = when (jsonType) {
        "float" -> j.asFloat()
        "int" -> j.asLong()?.toInt()
        "bool" -> j.asBool()
        "enum", "string" -> j.asStr()
        "texture" -> j.asLong()?.toInt()
        "color", "vec2", "vec3", "vec4" -> j.asArr()?.items
            ?.map { it.asFloat() ?: 0f }?.toFloatArray()
        "curve" -> j.asArr()?.items
            ?.mapNotNull { p ->
                p.asArr()?.items?.takeIf { it.size == 2 }?.let {
                    (it[0].asFloat() ?: return@mapNotNull null) to (it[1].asFloat() ?: return@mapNotNull null)
                }
            }?.takeIf { it.size >= 2 }?.let { Curve(it) }
        "gradient" -> j.asArr()?.items?.mapNotNull { stop ->
            stop.asObj()?.let {
                val p = it["p"]?.asFloat() ?: return@mapNotNull null
                val c = it["c"]?.asArr()?.items?.map { n -> n.asFloat() ?: 0f }?.toFloatArray()
                    ?: return@mapNotNull null
                p to c
            }
        }
        "mat4" -> j.asArr()?.items?.map { it.asFloat() ?: 0f }?.toFloatArray()
            ?.takeIf { it.size == 16 }?.let { com.vfx.engine.core.math.Mat4(it) }
        else -> null
    }

    private fun decodeTrack(jsonType: String, items: List<Json>): KeyframeTrack<Any>? {
        class KF(val t: Long, val v: Any, val e: com.vfx.engine.core.params.Easing)
        fun parse(read: (Json) -> Any?): List<KF> = items.mapNotNull { o ->
            val oo = o.asObj() ?: return@mapNotNull null
            val t = oo["t"]?.asLong() ?: return@mapNotNull null
            val v = oo["v"]?.let(read) ?: return@mapNotNull null
            KF(t, v, Easings.named(oo["e"]?.asStr() ?: "linear"))
        }
        fun build(track: KeyframeTrack<*>, read: (Json) -> Any?): KeyframeTrack<Any> {
            @Suppress("UNCHECKED_CAST")
            val t = track as KeyframeTrack<Any>
            parse(read).forEach { kf -> t.set(kf.t, kf.v, kf.e) }
            return t
        }
        fun farr(j: Json): FloatArray? =
            j.asArr()?.items?.map { it.asFloat() ?: 0f }?.toFloatArray()
        return when (jsonType) {
            "float" -> build(KeyframeTracks.floats()) { it.asFloat() }
            "int" -> build(KeyframeTracks.ints()) { it.asLong()?.toInt() }
            "bool" -> build(KeyframeTracks.booleans()) { it.asBool() }
            "enum", "string" -> build(KeyframeTracks.enums()) { it.asStr() }
            "color" -> build(KeyframeTracks.colors()) { farr(it) }
            "vec2" -> build(KeyframeTracks.vecs(2)) { farr(it) }
            "vec3" -> build(KeyframeTracks.vecs(3)) { farr(it) }
            "vec4" -> build(KeyframeTracks.vecs(4)) { farr(it) }
            else -> null
        }
    }
}
