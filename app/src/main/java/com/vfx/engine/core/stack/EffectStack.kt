package com.vfx.engine.core.stack

import com.vfx.engine.core.EffectEngineException
import com.vfx.engine.core.effect.EffectInstance
import com.vfx.engine.core.effect.EffectSnapshot
import com.vfx.engine.core.registry.EffectRegistry
import com.vfx.engine.core.serialization.Json
import com.vfx.engine.core.transform.Transform2D

/**
 * Ordered, deterministic effect chain: A -> B -> C (index 0 executes first).
 *
 * Determinism guarantees:
 *  - identical (stack state, timestamp) always resolves to identical snapshots
 *  - ordering is the list order; [move] is the only reordering operation
 *  - disabled / zero-intensity effects are skipped (no-ops by construction:
 *    every runtime mixes its result with the input via intensity)
 *
 * Also carries the stack-level layer [transform], applied at present time.
 */
class EffectStack(val registry: EffectRegistry = EffectRegistry()) {

    private val effectList = ArrayList<EffectInstance>()
    val transform = Transform2D()

    constructor(initialEffects: List<EffectInstance>, registry: EffectRegistry = EffectRegistry()) : this(registry) {
        initialEffects.forEach { addInstance(it) }
    }

    val size: Int get() = effectList.size
    fun isEmpty(): Boolean = effectList.isEmpty()
    fun effectAt(index: Int): EffectInstance = effectList[index]
    fun effects(): List<EffectInstance> = effectList.toList()
    val effects: List<EffectInstance> get() = effectList.toList()
    fun indexOf(instance: EffectInstance): Int = effectList.indexOf(instance)

    // ---------- mutation ----------
    /** Creates an instance from the registry and inserts at [index] (default: end). */
    fun addEffect(id: String, index: Int = effectList.size): EffectInstance {
        val inst = registry.createEffect(id)
        effectList.add(index.coerceIn(0, effectList.size), inst)
        return inst
    }

    /** Inserts a host-created instance (e.g. pre-configured). */
    fun addInstance(instance: EffectInstance, index: Int = effectList.size) {
        effectList.add(index.coerceIn(0, effectList.size), instance)
    }

    fun addEffect(effect: EffectInstance): EffectStack {
        addInstance(effect)
        return this
    }

    fun removeEffect(instance: EffectInstance): Boolean = effectList.remove(instance)
    fun removeAt(index: Int): EffectInstance = effectList.removeAt(index)
    fun removeEffect(instanceId: String): EffectStack {
        effectList.removeAll { it.id == instanceId || it.instanceId == instanceId }
        return this
    }

    fun move(from: Int, to: Int) {
        require(from in effectList.indices) { "move: bad source $from" }
        require(to in effectList.indices) { "move: bad target $to" }
        val e = effectList.removeAt(from)
        effectList.add(to, e)
    }

    fun clear() = effectList.clear()

    /**
     * Duplicate an effect within the stack (inserted right after the original).
     * Copy includes params, keyframes, mask, blend, intensity.
     */
    fun duplicate(instance: EffectInstance): EffectInstance {
        val index = effectList.indexOf(instance)
        require(index >= 0) { "Instance not in stack" }
        val json = Json.parse(Json.write(instance.serialize()))
        val copy = registry.createEffect(instance.definition.id)
        copy.deserializeState(json.asObj()!!)
        effectList.add(index + 1, copy)
        return copy
    }

    // ---------- deterministic per-frame resolution ----------
    class ResolvedEffect(val instance: EffectInstance, val snapshot: EffectSnapshot)

    /**
     * Resolve every enabled, non-zero-intensity effect at [timeUs] (media µs).
     * Skipped effects: enabled == false, or intensity <= 1e-4 (provably a no-op).
     */
    fun resolveAt(timeUs: Long): List<ResolvedEffect> =
        effectList
            .filter { it.enabled && it.intensity > EFFECT_EPSILON }
            .map { ResolvedEffect(it, it.snapshotAt(timeUs)) }

    /** Max intensity in the stack — used by the pipeline to skip graph builds entirely. */
    fun anyActive(): Boolean =
        effectList.any { it.enabled && it.intensity > EFFECT_EPSILON }

    // ---------- serialization ----------
    fun serialize(): Json.Obj = Json.obj(
        "version" to Json.Num(SCHEMA_VERSION.toDouble()),
        "transform" to serializeTransform(),
        "effects" to Json.Arr(effectList.map { it.serialize() })
    )

    /**
     * Restores stack contents. Unknown effect ids:
     *  - lenient = true  -> skipped (project still loads; logged by host)
     *  - lenient = false -> throws Serialization error (strict rebuild)
     */
    fun deserialize(json: Json, lenient: Boolean = false) {
        val o = json.asObj() ?: throw EffectEngineException.Serialization("Stack JSON must be an object")
        val version = o["version"]?.asLong() ?: SCHEMA_VERSION
        if (version > SCHEMA_VERSION && !lenient)
            throw EffectEngineException.Serialization("Stack schema $version newer than supported $SCHEMA_VERSION")

        (o["transform"]?.asObj())?.let { t ->
            fun arr(k: String): FloatArray =
                t[k]?.asArr()?.items?.map { it.asFloat() ?: 0f }?.toFloatArray() ?: FloatArray(0)
            arr("pos").takeIf { it.size == 2 }?.let { transform.posX = it[0]; transform.posY = it[1] }
            arr("scale").takeIf { it.size == 2 }?.let { transform.scaleX = it[0]; transform.scaleY = it[1] }
            transform.rotationDeg = t["rotation"]?.asFloat() ?: 0f
            arr("crop").takeIf { it.size == 4 }?.let {
                transform.cropX = it[0]; transform.cropY = it[1]; transform.cropW = it[2]; transform.cropH = it[3]
            }
            arr("anchor").takeIf { it.size == 2 }?.let { transform.anchorX = it[0]; transform.anchorY = it[1] }
            transform.flipH = t["flipH"]?.asBool() ?: false
            transform.flipV = t["flipV"]?.asBool() ?: false
        }

        clear()
        val items = o["effects"]?.asArr()?.items ?: emptyList()
        for ((i, ej) in items.withIndex()) {
            val ejo = ej.asObj() ?: continue
            val eid = ejo["effectId"]?.asStr()
                ?: throw EffectEngineException.Serialization("Effect[$i] missing 'effectId'")
            val def = registry.getEffectOrNull(eid)
            if (def == null) {
                if (lenient) continue
                throw EffectEngineException.Serialization("Unknown effect id '$eid' at index $i")
            }
            val inst = addInstanceOrdered(def)
            inst.deserializeState(ejo)
        }
    }

    private fun addInstanceOrdered(def: com.vfx.engine.core.effect.EffectDefinition): EffectInstance {
        val inst = EffectInstance(def)
        effectList.add(inst)
        return inst
    }

    private fun serializeTransform(): Json.Obj = Json.obj(
        "pos" to Json.Arr(listOf(
            Json.Num(transform.posX.toDouble()), Json.Num(transform.posY.toDouble()))),
        "scale" to Json.Arr(listOf(
            Json.Num(transform.scaleX.toDouble()), Json.Num(transform.scaleY.toDouble()))),
        "rotation" to Json.Num(transform.rotationDeg.toDouble()),
        "crop" to Json.Arr(listOf(
            transform.cropX, transform.cropY, transform.cropW, transform.cropH
        ).map { Json.Num(it.toDouble()) }),
        "anchor" to Json.Arr(listOf(
            Json.Num(transform.anchorX.toDouble()), Json.Num(transform.anchorY.toDouble()))),
        "flipH" to Json.Bool(transform.flipH),
        "flipV" to Json.Bool(transform.flipV)
    )

    companion object {
        const val SCHEMA_VERSION = 1L
        const val EFFECT_EPSILON = 1e-4f
    }
}

object StackEvaluator {
    fun evaluateStackAtTime(
        stack: EffectStack,
        pts: com.vfx.engine.core.Microseconds
    ): List<Pair<EffectInstance, com.vfx.engine.core.params.ParamMap>> {
        return stack.effects.filter { it.enabled }.map { it to it.parameters }
    }
}
