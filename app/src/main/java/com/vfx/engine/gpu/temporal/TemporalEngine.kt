package com.vfx.engine.gpu.temporal

import com.vfx.engine.gpu.FramebufferObject
import com.vfx.engine.gpu.GpuTexture
import com.vfx.engine.gpu.TextureSpec

/**
 * Frame-history rings for temporal effects. Slots are PERSISTENT (never pooled)
 * and survive across frames until reset (seek) / release / context loss.
 * Ring keys must be unique per effect INSTANCE (e.g. a per-runtime UUID).
 */
class TemporalEngine {

    class Ring(val slots: Array<FramebufferObject>) {
        var writeIndex = 0; internal set
        var primed = false; internal set

        /** Slot to render INTO this frame. */
        fun writeSlot(): FramebufferObject = slots[writeIndex]

        /** Slot to READ this frame (previous output), null on first frame. */
        fun readSlot(): FramebufferObject? =
            if (!primed) null else slots[(writeIndex + 1) % slots.size]

        fun advance() { writeIndex = (writeIndex + 1) % slots.size; primed = true }
    }

    private val rings = LinkedHashMap<String, Ring>()

    fun ring(key: String, slotCount: Int, width: Int, height: Int): Ring {
        val existing = rings[key]
        if (existing != null && existing.slots.size == slotCount &&
            existing.slots[0].texture.width == width &&
            existing.slots[0].texture.height == height) return existing
        existing?.let { r -> r.slots.forEach { it.release() } }
        val slots = Array(slotCount) {
            val tex = GpuTexture(TextureSpec(), width, height, persistent = true).apply { allocate() }
            FramebufferObject(tex).also { fbo -> fbo.pooled = false; fbo.checkedOut = true }
        }
        return Ring(slots).also { rings[key] = it }
    }

    class HistorySlot(val read: FramebufferObject, val write: FramebufferObject, private val onSwap: () -> Unit) {
        fun swap() = onSwap()
    }

    fun acquireHistory(key: String, width: Int, height: Int): HistorySlot {
        val r = ring(key, 2, width, height)
        val readFbo = r.readSlot() ?: r.writeSlot()
        val writeFbo = if (r.primed) r.writeSlot() else r.slots[1]
        return HistorySlot(readFbo, writeFbo) { r.advance() }
    }

    /** Reset one ring (by key) or all (seek / global flush). */
    fun reset(key: String? = null) {
        if (key == null) rings.clear()
        else rings.remove(key)
    }

    fun onContextLost() = rings.clear()
    fun release() { rings.values.forEach { r -> r.slots.forEach { it.release() } }; rings.clear() }
}
