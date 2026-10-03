package com.ahstudio.audio.master.dsp.eq

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.dsp.AudioDspProcessor

data class EqualizerBand(
    val type: BiquadType = BiquadType.PEAKING,
    val frequencyHz: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 0.7071f,
    val enabled: Boolean = true,
)

class ParametricEqualizer(bands: List<EqualizerBand> = defaultBands()) : AudioDspProcessor {
    override val name = "parametric_eq"
    private val sections = bands.filter { it.enabled }
        .map { BiquadFilter(it.type).apply { configure(it.type, it.frequencyHz, it.gainDb, it.q) } }

    override fun prepare(format: AudioFormat, maxBlockFrames: Int) { sections.forEach { it.prepare(format, maxBlockFrames) } }
    override fun process(buffer: AudioBuffer, ctx: AudioRenderContext) { for (s in sections) s.process(buffer, ctx) }
    override fun reset() { sections.forEach { it.reset() } }

    companion object {
        fun defaultBands() = listOf(
            EqualizerBand(BiquadType.HIGH_PASS, 30f),
            EqualizerBand(BiquadType.PEAKING, 100f, 0f, 0.9f),
            EqualizerBand(BiquadType.PEAKING, 350f, 0f, 0.9f),
            EqualizerBand(BiquadType.PEAKING, 1000f, 0f, 0.9f),
            EqualizerBand(BiquadType.PEAKING, 4000f, 0f, 0.9f),
            EqualizerBand(BiquadType.HIGH_SHELF, 10000f, 0f),
        )
        fun preset(name: String): List<EqualizerBand> = when (name) {
            "vocal" -> listOf(EqualizerBand(BiquadType.HIGH_PASS, 90f), EqualizerBand(BiquadType.PEAKING, 250f, -3f),
                EqualizerBand(BiquadType.PEAKING, 3000f, 3f), EqualizerBand(BiquadType.HIGH_SHELF, 9000f, 1.5f))
            "bass_boost" -> listOf(EqualizerBand(BiquadType.LOW_SHELF, 120f, 6f), EqualizerBand(BiquadType.PEAKING, 300f, 2f))
            "treble_boost" -> listOf(EqualizerBand(BiquadType.HIGH_SHELF, 5000f, 5f))
            else -> defaultBands()
        }
    }
}
