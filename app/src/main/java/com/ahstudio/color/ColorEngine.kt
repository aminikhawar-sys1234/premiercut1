package com.ahstudio.color

import com.ahstudio.color.core.*
import com.ahstudio.color.keyframe.ColorKeyframeBridge
import com.ahstudio.color.keyframe.KeyframeTrackReader
import com.ahstudio.color.lut.*
import com.ahstudio.color.preset.*
import com.ahstudio.color.undo.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * §57 PUBLIC API:
 *   evaluate() / apply-via-setters / reset() / createPreset() / applyPreset()
 *
 * §51: state is CLIP-LOCAL (map keyed by clip id — never global).
 * §50: state is immutable data; media is never modified; reset = replace state.
 * §59: slider changes mutate state only → GPU path re-uploads uniforms; programs,
 *      curve textures and LUT textures are cached inside ColorGpuProcessor and
 *      rebuilt ONLY when the structural VariantKey changes (incremental invalidation).
 */
class ColorEngine(
    private val lutRepo: LutRepository,
    private val keyframes: KeyframeTrackReader? = null,
    private val commandSink: ColorCommandSink? = null,
    val scope: ColorEngineScope = ColorEngineScope()
) {
    private val states = ConcurrentHashMap<String, ColorState>()
    private val loadedLuts = ConcurrentHashMap<String, Lut>()
    private val listeners = CopyOnWriteArrayList<(clipId: String, state: ColorState) -> Unit>()
    private val bridge = keyframes?.let { ColorKeyframeBridge(it) }

    // ---------- state ----------
    fun state(clipId: String): ColorState = states[clipId] ?: ColorState()

    fun evaluate(clipId: String, timeMs: Long): ColorState =
        bridge?.evaluate(state(clipId), clipId, timeMs) ?: state(clipId)

    fun setState(clipId: String, s: ColorState) {
        states[clipId] = s
        notify(clipId, s)
    }

    private fun setStateQuiet(clipId: String, s: ColorState) { states[clipId] = s }

    /** Slider/UI entry point — coalesced undo + realtime event. Pass dragSession per gesture. */
    fun setFloat(clipId: String, path: String, value: Float, dragSession: Long = 0L) {
        val prop = ColorProperties.ALL[path] ?: error("Unknown color property: $path")
        val before = state(clipId)
        val next = prop.set(before, value.coerceIn(prop.min, prop.max))
        setStateQuiet(clipId, next)
        commandSink?.execute(SetPropertyCommand(path, dragSession, before, next,
            object : ColorStateStore {
                override fun get() = state(clipId)
                override fun set(s: ColorState) { setStateQuiet(clipId, s); notify(clipId, s) }
            }))
        scope.emitSlider(SliderEvent(clipId, path, prop.get(next), dragSession))
        notify(clipId, next)
    }

    /** §53: UI calls this on ACTION_UP so HostCommandSink releases one batched command. */
    fun endDrag(dragSession: Long) { (commandSink as? CoalescingSink)?.endSession(dragSession) }

    fun reset(clipId: String) { setState(clipId, ColorState()) }
    fun isIdentity(clipId: String): Boolean = state(clipId).isIdentity()

    fun copyGrade(fromClipId: String, toClipId: String) = setState(toClipId, state(fromClipId))

    // ---------- LUTs ----------
    fun setLut(clipId: String, lut: LutState) {
        setState(clipId, state(clipId).copy(lut = lut))
        if (lut.isValid() && !loadedLuts.containsKey(lut.lutId)) {
            scope.io.launch {
                lutRepo.load(lut.lutId)?.let { loadedLuts[lut.lutId] = it }
            }
        }
    }

    /** GL thread resolves LUT data synchronously from the preloaded map. */
    fun resolveLut3d(lutId: String): Lut3D? = loadedLuts[lutId] as? Lut3D

    // ---------- presets ----------
    fun createPreset(clipId: String, name: String) = UserColorPreset(name, state(clipId))
    fun applyPreset(clipId: String, preset: UserColorPreset) = setState(clipId, preset.state)

    // ---------- observation (drives preview re-render) ----------
    fun addListener(l: (clipId: String, state: ColorState) -> Unit) { listeners.add(l) }
    fun removeListener(l: (clipId: String, state: ColorState) -> Unit) { listeners.remove(l) }
    private fun notify(clipId: String, s: ColorState) = listeners.forEach { it(clipId, s) }

    fun release() {
        states.clear(); loadedLuts.clear(); listeners.clear(); scope.cancelAll()
    }
}
