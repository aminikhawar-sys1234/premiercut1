package com.ahstudio.audio.master.dsp

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.model.AudioDspChainSpec

interface AudioDspProcessor {
    val name: String
    fun prepare(format: AudioFormat, maxBlockFrames: Int)
    fun process(buffer: AudioBuffer, ctx: AudioRenderContext)
    fun reset()
    fun release() {}
    fun setParam(key: String, value: Float) {}
}

class AudioDspChain(spec: AudioDspChainSpec) : AudioDspProcessor {
    override val name = "chain"
    val processors: List<AudioDspProcessor> = spec.nodes.filter { it.enabled }.map { AudioDspFactory.create(it) }
    private var prepared = false

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) {
        for (p in processors) p.prepare(format, maxBlockFrames)
        prepared = true
    }
    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        if (!prepared) prepare(ctx.format, buffer.frames)
        for (p in processors) p.process(buffer, ctx)
    }
    override fun reset() { for (p in processors) p.reset() }
    override fun release() { for (p in processors) p.release() }
    fun processorAt(index: Int): AudioDspProcessor? = processors.getOrNull(index)
    companion object { val EMPTY = AudioDspChain(AudioDspChainSpec(emptyList())) }
}
