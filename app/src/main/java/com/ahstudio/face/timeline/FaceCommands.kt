package com.ahstudio.face.timeline

import com.ahstudio.face.FaceEngineHost

class AddFaceEffectCommand(private val repo: FaceEffectRepository, private val data: FaceEffectClipData) : FaceCommand {
    override fun run() { repo.addInternal(data) }
    override fun undo() { repo.removeInternal(data.effectId) }
}

class RemoveFaceEffectCommand(private val repo: FaceEffectRepository, private val data: FaceEffectClipData) : FaceCommand {
    override fun run() { repo.removeInternal(data.effectId) }
    override fun undo() { repo.addInternal(data) }
}

class UpdateFaceEffectCommand(
    private val repo: FaceEffectRepository,
    private val effectId: String,
    private val old: FaceEffectClipData,
    private val new: FaceEffectClipData,
) : FaceCommand {
    override fun run() { repo.replaceInternal(effectId, new) }
    override fun undo() { repo.replaceInternal(effectId, old) }
}

class FaceEffectRepository(
    private val host: FaceEngineHost,
    private val commands: CommandBus,
    private val sink: FaceProjectSink,
) {
    private val byClip = HashMap<String, MutableMap<String, FaceEffectClipData>>()

    fun add(data: FaceEffectClipData) = commands.execute(AddFaceEffectCommand(this, data))
    fun update(effectId: String, transform: (FaceEffectClipData) -> FaceEffectClipData) {
        val old = find(effectId) ?: return
        commands.execute(UpdateFaceEffectCommand(this, effectId, old, transform(old)))
    }
    fun remove(effectId: String) {
        val old = find(effectId) ?: return
        commands.execute(RemoveFaceEffectCommand(this, old))
    }
    fun effectsFor(clipId: String): List<FaceEffectClipData> = byClip[clipId]?.values?.toList() ?: emptyList()

    /** Project load path — restore from serializer. */
    fun restore(clipId: String, jsonList: List<String>) {
        val m = byClip.getOrPut(clipId) { mutableMapOf() }
        m.clear()
        jsonList.forEach { s -> runCatching { FaceEffectSerializer.fromJson(s) }.getOrNull()?.let { m[it.effectId] = it } }
        refresh(clipId)
    }

    internal fun addInternal(d: FaceEffectClipData) {
        byClip.getOrPut(d.clipId) { mutableMapOf() }[d.effectId] = d; refresh(d.clipId)
    }
    internal fun removeInternal(effectId: String) {
        val clip = findClipOf(effectId) ?: return
        byClip[clip]?.remove(effectId); refresh(clip)
    }
    internal fun replaceInternal(effectId: String, d: FaceEffectClipData) {
        byClip.getOrPut(d.clipId) { mutableMapOf() }[effectId] = d; refresh(d.clipId)
    }

    private fun refresh(clipId: String) {
        host.onEffectsChanged(clipId, byClip[clipId]?.values?.map { it.toSpec() } ?: emptyList())
        sink.persistFaceEffects(clipId, byClip[clipId]?.values?.map { FaceEffectSerializer.toJson(it) } ?: emptyList())
    }
    private fun find(effectId: String) = byClip.values.firstNotNullOfOrNull { it[effectId] }
    private fun findClipOf(effectId: String) = byClip.entries.firstOrNull { it.value.containsKey(effectId) }?.key
}
