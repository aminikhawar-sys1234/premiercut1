package com.ahstudio.audio.master.mixer

import com.ahstudio.audio.master.automation.AudioAutomationResolver
import com.ahstudio.audio.master.automation.AudioEnvelopeGenerator
import com.ahstudio.audio.master.model.AutomationParameter as AP
import com.ahstudio.audio.master.clips.AudioClipSource
import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.core.dbToLin
import com.ahstudio.audio.master.dsp.AudioDspChain
import com.ahstudio.audio.master.dsp.AudioDspFactory
import com.ahstudio.audio.master.dsp.dynamics.LimiterProcessor
import com.ahstudio.audio.master.metering.AudioLevelMeter
import com.ahstudio.audio.master.model.AudioClipModel
import com.ahstudio.audio.master.model.AudioTrackModel
import com.ahstudio.audio.master.model.MasterAudioProject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class CrossfadePair(val clipA: String, val clipB: String, val startSec: Double, val endSec: Double)

class AudioTrackBus(val trackId: String, channels: Int, frames: Int) {
    val buffer = AudioBuffer(channels, frames)
    fun clear() = buffer.clear()
}

class AudioTrackMixer(private val format: AudioFormat, private val maxFrames: Int) {
    private val clipBuf = AudioBuffer(format.channels, maxFrames)
    private val clipChainCache = HashMap<String, AudioDspChain>()

    fun renderTrackClips(
        track: AudioTrackModel, ctx: AudioRenderContext, reader: AudioClipSource,
        bus: AudioTrackBus, resolver: AudioAutomationResolver, xfades: List<CrossfadePair>,
    ) {
        bus.clear()
        val blockEnd = ctx.timelineEndSec
        for (clip in track.clips) {
            if (!clip.overlapsRange(ctx.timelineStartSec, blockEnd)) continue
            clipBuf.clear()
            val read = reader.readClip(clip, ctx, clipBuf)
            if (read.frames <= 0) continue

            val tClipStart = ctx.timelineStartSec + read.offsetFrames.toDouble() / ctx.sampleRate
            val tClipEnd = tClipStart + read.frames.toDouble() / ctx.sampleRate
            val clipCtx = AudioRenderContext(ctx.format, tClipStart, read.frames, ctx.blockIndex, ctx.realtime)

            val volAuto = resolver.automationFor(clip, AP.VOLUME)
            val g0 = clipAmplitude(clip, tClipStart, volAuto, resolver)
            val g1 = clipAmplitude(clip, tClipEnd, volAuto, resolver)
            val hasActiveFade = (clip.fade.fadeInSec > 0 && tClipStart < clip.timelineStartSec + clip.fade.fadeInSec) ||
                (clip.fade.fadeOutSec > 0 && tClipEnd > clip.timelineEndSec - clip.fade.fadeOutSec)
            val perFrameVol = hasActiveFade || (volAuto != null && volAuto.enabled && volAuto.keyframes.isNotEmpty() &&
                volAuto.keyframes.any { it.timeSec >= tClipStart && it.timeSec <= tClipEnd })
            applyGain(clipBuf, read.offsetFrames, read.frames, tClipStart, ctx.sampleRate, perFrameVol, g0, g1) { t -> clipAmplitude(clip, t, volAuto, resolver) }

            val x = xfadeFor(xfades, clip.id)
            if (x != null && x.endSec > x.startSec) {
                val isA = x.clipA == clip.id
                val u0 = (tClipStart - x.startSec) / (x.endSec - x.startSec)
                val u1 = (tClipEnd - x.startSec) / (x.endSec - x.startSec)
                applyCrossfade(clipBuf, read.offsetFrames, read.frames, u0.toFloat().coerceIn(0f, 1f), u1.toFloat().coerceIn(0f, 1f), isA)
            }

            val panAuto = resolver.automationFor(clip, AP.PAN)
            val p0 = panAt(clip, tClipStart, panAuto, resolver)
            val p1 = panAt(clip, tClipEnd, panAuto, resolver)
            applyPan(clipBuf, read.offsetFrames, read.frames, p0, p1)

            val chain = clipChainCache.getOrPut(clip.id) { AudioDspFactory.chain(clip.clipDsp) }
            if (chain !== AudioDspChain.EMPTY) chain.process(clipBuf, clipCtx)

            for (c in 0 until bus.buffer.channels) {
                val dst = bus.buffer.data[c]; val src = clipBuf.data[c]
                for (i in 0 until read.frames) dst[read.offsetFrames + i] += src[read.offsetFrames + i]
            }
        }
    }

    fun resetChains() { clipChainCache.values.forEach { it.reset() }; clipChainCache.clear() }

    private fun clipAmplitude(clip: AudioClipModel, t: Double, volAuto: com.ahstudio.audio.master.model.AudioAutomationModel?, resolver: AudioAutomationResolver): Float =
        clip.volume * dbToLin(clip.gainDb) *
            AudioEnvelopeGenerator.fadeGain(clip.fade, t - clip.timelineStartSec, clip.timelineDurationSec) *
            resolver.perFrame(volAuto, t, 1f)

    private fun panAt(clip: AudioClipModel, t: Double, panAuto: com.ahstudio.audio.master.model.AudioAutomationModel?, resolver: AudioAutomationResolver): Float =
        resolver.perFrame(panAuto, t, clip.pan.pan)

    private fun xfadeFor(xfades: List<CrossfadePair>, clipId: String): CrossfadePair? =
        xfades.firstOrNull { it.clipA == clipId || it.clipB == clipId }

    private fun applyGain(buf: AudioBuffer, offset: Int, frames: Int, t0: Double, sampleRate: Int,
                          perFrame: Boolean, g0: Float, g1: Float, eval: (Double) -> Float) {
        for (c in 0 until buf.channels) {
            val d = buf.data[c]
            when {
                perFrame -> for (i in 0 until frames) d[offset + i] *= eval(t0 + i.toDouble() / sampleRate)
                g0 == g1 -> if (g0 != 1f) for (i in 0 until frames) d[offset + i] *= g0
                else -> { val step = (g1 - g0) / frames; var g = g0
                    for (i in 0 until frames) { d[offset + i] *= g; g += step } }
            }
        }
    }

    private fun applyCrossfade(buf: AudioBuffer, offset: Int, frames: Int, u0: Float, u1: Float, isFirst: Boolean) {
        val step = (u1 - u0) / frames
        var u = u0
        for (i in 0 until frames) {
            val g = if (isFirst) cos(u * (Math.PI / 2).toFloat()) else sin(u * (Math.PI / 2).toFloat())
            for (c in 0 until buf.channels) buf.data[c][offset + i] *= g
            u += step
        }
    }

    private fun applyPan(buf: AudioBuffer, offset: Int, frames: Int, p0: Float, p1: Float) {
        if (buf.channels < 2) return
        if (abs(p0 - p1) < 1e-6f && p0 == 0f) return
        val a0 = (p0 + 1f) * 0.25f * Math.PI.toFloat(); val a1 = (p1 + 1f) * 0.25f * Math.PI.toFloat()
        val dA = (a1 - a0) / frames
        var a = a0
        val l = buf.data[0]; val r = buf.data[1]
        for (i in 0 until frames) {
            val gl = cos(a); val gr = sin(a)
            l[offset + i] *= gl; r[offset + i] *= gr
            a += dA
        }
    }
}

class AudioTrackProcessor(private val format: AudioFormat, private val maxFrames: Int) {
    private var chain: AudioDspChain = AudioDspChain.EMPTY
    private var chainTrackId: String? = null
    private val resolver = AudioAutomationResolver()

    fun effectiveMute(track: AudioTrackModel, anySolo: Boolean): Boolean =
        track.settings.mute || (anySolo && !track.settings.solo)

    fun processBus(track: AudioTrackModel, ctx: AudioRenderContext, bus: AudioTrackBus, anySolo: Boolean) {
        if (effectiveMute(track, anySolo)) return
        if (chainTrackId != track.id) { chain = AudioDspFactory.chain(track.trackDsp); chain.prepare(format, maxFrames); chainTrackId = track.id }
        val volAuto = track.automation.firstOrNull { it.parameter == AP.VOLUME }
        val panAuto = track.automation.firstOrNull { it.parameter == AP.PAN }
        val g0 = track.settings.volume * dbToLin(track.settings.gainDb) * resolver.perFrame(volAuto, ctx.timelineStartSec, 1f)
        val g1 = track.settings.volume * dbToLin(track.settings.gainDb) * resolver.perFrame(volAuto, ctx.timelineEndSec, 1f)
        val b = bus.buffer
        if (g0 == g1) { if (g0 != 1f) for (c in 0 until b.channels) { val d = b.data[c]; for (i in 0 until ctx.frames) d[i] *= g0 } }
        else { val step = (g1 - g0) / ctx.frames; for (c in 0 until b.channels) { val d = b.data[c]; var g = g0; for (i in 0 until ctx.frames) { d[i] *= g; g += step } } }
        if (b.channels >= 2) {
            val p0 = resolver.perFrame(panAuto, ctx.timelineStartSec, track.settings.pan)
            val p1 = resolver.perFrame(panAuto, ctx.timelineEndSec, track.settings.pan)
            if (p0 != 0f || p1 != 0f) {
                val a0 = (p0 + 1f) * 0.25f * Math.PI.toFloat(); val a1 = (p1 + 1f) * 0.25f * Math.PI.toFloat()
                val dA = (a1 - a0) / ctx.frames; var a = a0
                val l = b.data[0]; val r = b.data[1]
                for (i in 0 until ctx.frames) { val gl = cos(a); val gr = sin(a); l[i] *= gl; r[i] *= gr; a += dA }
            }
        }
        if (chain !== AudioDspChain.EMPTY) chain.process(b, ctx)
    }
    fun reset() { chain.reset() }
}

class MasterAudioMixer(private val maxFrames: Int) {
    private var format = AudioFormat.DEFAULT
    private var project: MasterAudioProject = MasterAudioProject()
    private lateinit var masterBuf: AudioBuffer
    private val trackBuses = HashMap<String, AudioTrackBus>()
    private val trackMixers = HashMap<String, AudioTrackMixer>()
    private val trackProcessors = HashMap<String, AudioTrackProcessor>()
    private var masterChain: AudioDspChain = AudioDspChain.EMPTY
    private var limiter = LimiterProcessor()
    private var xfadesByTrack = HashMap<String, List<CrossfadePair>>()
    val meter = AudioLevelMeter()
    val clippingGuard = AudioClippingGuard()

    @Volatile var lastRenderMs: Double = 0.0; private set

    fun setProject(p: MasterAudioProject, fmt: AudioFormat) {
        project = p; format = fmt
        masterBuf = AudioBuffer(fmt.channels, maxFrames)
        for (t in p.tracks) {
            trackBuses.getOrPut(t.id) { AudioTrackBus(t.id, fmt.channels, maxFrames) }
            trackMixers.getOrPut(t.id) { AudioTrackMixer(fmt, maxFrames) }
            trackProcessors.getOrPut(t.id) { AudioTrackProcessor(fmt, maxFrames) }
        }
        masterChain = AudioDspFactory.chain(p.masterDsp)
        masterChain.prepare(fmt, maxFrames)
        limiter = LimiterProcessor().apply { ceilingDb = p.mix.headroomDb }
        limiter.prepare(fmt, maxFrames)
        xfadesByTrack = computeCrossfades(p)
    }

    fun reset() {
        masterChain.reset(); limiter.reset()
        trackMixers.values.forEach { it.resetChains() }
        trackProcessors.values.forEach { it.reset() }
        meter.reset()
    }

    fun renderBlock(ctx: AudioRenderContext, reader: AudioClipSource): AudioBuffer {
        val t0 = System.nanoTime()
        masterBuf.clear()
        val anySolo = project.tracks.any { it.settings.solo }
        val resolver = AudioAutomationResolver()
        for (track in project.tracks) {
            val proc = trackProcessors.getValue(track.id)
            if (proc.effectiveMute(track, anySolo)) continue
            val bus = trackBuses.getValue(track.id)
            trackMixers.getValue(track.id).renderTrackClips(track, ctx, reader, bus, resolver, xfadesByTrack[track.id] ?: emptyList())
            proc.processBus(track, ctx, bus, anySolo)

            val tb = bus.buffer
            var silent = true
            for (c in 0 until tb.channels) { val d = tb.data[c]; for (i in 0 until ctx.frames) if (d[i] != 0f) { silent = false; break }; if (!silent) break }
            if (silent) continue
            for (c in 0 until masterBuf.channels) {
                val src = tb.data[c % tb.channels]; val dst = masterBuf.data[c]
                for (i in 0 until ctx.frames) dst[i] += src[i]
            }
        }

        val mg = project.mix.masterVolume * dbToLin(project.mix.masterGainDb)
        if (mg != 1f) for (c in 0 until masterBuf.channels) { val d = masterBuf.data[c]; for (i in 0 until ctx.frames) d[i] *= mg }

        if (masterChain !== AudioDspChain.EMPTY) masterChain.process(masterBuf, ctx)

        limiter.process(masterBuf, ctx)
        clippingGuard.inspect(masterBuf, ctx)
        meter.update(masterBuf, ctx)
        lastRenderMs = (System.nanoTime() - t0) / 1_000_000.0
        return masterBuf
    }

    private fun computeCrossfades(p: MasterAudioProject): HashMap<String, List<CrossfadePair>> {
        val map = HashMap<String, List<CrossfadePair>>()
        for (track in p.tracks) {
            val sorted = track.clips.sortedBy { it.timelineStartSec }
            val pairs = mutableListOf<CrossfadePair>()
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]; val b = sorted[i + 1]
                if (b.timelineStartSec < a.timelineEndSec - 1e-9) {
                    val s = b.timelineStartSec; val e = min(a.timelineEndSec, b.timelineEndSec)
                    if (e > s) pairs.add(CrossfadePair(a.id, b.id, s, e))
                }
            }
            if (pairs.isNotEmpty()) map[track.id] = pairs
        }
        return map
    }
}

class AudioClippingGuard {
    @Volatile var clippedSamples: Long = 0; private set
    fun inspect(buf: AudioBuffer, ctx: AudioRenderContext) {
        for (c in 0 until buf.channels) {
            val d = buf.data[c]
            for (i in 0 until ctx.frames) { val v = d[i]; if (v > 0.999f || v < -0.999f) clippedSamples++ }
        }
    }
    fun reset() { clippedSamples = 0 }
}
