package com.ahstudio.audio.master

import com.ahstudio.audio.master.automation.AudioEnvelopeGenerator
import com.ahstudio.audio.master.automation.AudioKeyframeEngine
import com.ahstudio.audio.master.clips.*
import com.ahstudio.audio.master.commands.*
import com.ahstudio.audio.master.core.*
import com.ahstudio.audio.master.dsp.dynamics.*
import com.ahstudio.audio.master.dsp.eq.*
import com.ahstudio.audio.master.dsp.noise.*
import com.ahstudio.audio.master.dsp.spatial.*
import com.ahstudio.audio.master.dsp.transform.*
import com.ahstudio.audio.master.dsp.utility.*
import com.ahstudio.audio.master.export.AudioExportConfig
import com.ahstudio.audio.master.export.AudioExportFormat
import com.ahstudio.audio.master.export.AudioExportValidator
import com.ahstudio.audio.master.metering.AudioLevelMeter
import com.ahstudio.audio.master.metering.LoudnessMeter
import com.ahstudio.audio.master.mixer.MasterAudioMixer
import com.ahstudio.audio.master.model.*
import com.ahstudio.audio.master.recording.WavWriter
import com.ahstudio.audio.master.timeline.AudioOverlapResolver
import com.ahstudio.audio.master.timeline.AudioTimelineController
import com.ahstudio.audio.master.timeline.OverlapStrategy
import com.ahstudio.audio.master.waveform.WaveformGenerator
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class AudioMasterEngineTest {
    private val sr = 48000
    private val fmt = AudioFormat(sr, 2)

    private fun sine(freq: Double, amp: Float, frames: Int, ch: Int = 2): Array<FloatArray> =
        Array(ch) { FloatArray(frames) { i -> (amp * sin(2 * PI * freq * i / sr)).toFloat() } }
    private fun buf(pcm: Array<FloatArray>): AudioBuffer {
        val b = AudioBuffer(pcm.size, pcm[0].size)
        for (c in pcm.indices) System.arraycopy(pcm[c], 0, b.data[c], 0, pcm[0].size)
        return b
    }
    private fun ctx(frames: Int, t0: Double = 0.0) = AudioRenderContext(fmt, t0, frames)
    private fun rmsOf(pcm: Array<FloatArray>, from: Int, to: Int): Double {
        var s = 0.0; var n = 0
        for (c in pcm) for (i in from until to) { s += c[i].toDouble() * c[i]; n++ }
        return kotlin.math.sqrt(s / n)
    }
    private fun track(id: String, clips: List<AudioClipModel>, settings: AudioTrackSettings = AudioTrackSettings()) =
        AudioTrackModel(id, 0, settings, clips)
    private fun clip(trackId: String, start: Double, dur: Double = 1.0) =
        AudioClipModel(AudioClipOperations.newId(), trackId, "src1", start, dur, start, dur)
    private fun project(vararg tracks: AudioTrackModel) = MasterAudioProject(tracks = tracks.toList())

    // ---------- EQ / Filters ----------
    @Test fun lowPass_attenuatesHighFrequencies() {
        val pcm = sine(10_000.0, 1f, 9600)
        val f = LowPassFilter(1000f); f.prepare(fmt, 9600)
        val b = buf(pcm); f.process(b, ctx(9600))
        assertTrue(rmsOf(b.data, 2400, 9600) < rmsOf(pcm, 2400, 9600) * 0.15)
    }
    @Test fun lowPass_passesLowFrequencies() {
        val pcm = sine(100.0, 1f, 9600)
        val f = LowPassFilter(1000f); f.prepare(fmt, 9600)
        val b = buf(pcm); f.process(b, ctx(9600))
        val ratio = rmsOf(b.data, 2400, 9600) / rmsOf(pcm, 2400, 9600)
        assertTrue("ratio=$ratio", ratio in 0.8..1.1)
    }
    @Test fun notch_rejectsCenterFrequency() {
        val pcm = sine(1000.0, 1f, 9600)
        val f = NotchFilter(1000f, 8f); f.prepare(fmt, 9600)
        val b = buf(pcm); f.process(b, ctx(9600))
        assertTrue(rmsOf(b.data, 2400, 9600) < rmsOf(pcm, 2400, 9600) * 0.2)
    }
    @Test fun parametricEq_boostRaisesBandLevel() {
        val pcm = sine(1000.0, 0.5f, 9600)
        val eq = ParametricEqualizer(listOf(EqualizerBand(BiquadType.PEAKING, 1000f, 12f, 1f)))
        eq.prepare(fmt, 9600)
        val b = buf(pcm); eq.process(b, ctx(9600))
        assertTrue(rmsOf(b.data, 2400, 9600) > rmsOf(pcm, 2400, 9600) * 2.0)
    }

    // ---------- Dynamics ----------
    @Test fun compressor_reducesLoudSignal() {
        val pcm = sine(440.0, 0.9f, 48000)
        val c = CompressorProcessor().apply { thresholdDb = -30f; ratio = 10f; attackMs = 5f; releaseMs = 100f }
        c.prepare(fmt, 48000)
        val b = buf(pcm); c.process(b, ctx(48000))
        assertTrue(rmsOf(b.data, 24000, 48000) < rmsOf(pcm, 24000, 48000) * 0.5)
    }
    @Test fun limiter_neverExceedsCeiling() {
        val pcm = sine(1000.0, 1.4f, 9600)
        val l = LimiterProcessor(); l.prepare(fmt, 9600)
        val b = buf(pcm); l.process(b, ctx(9600))
        val ceil = dbToLin(-0.3f)
        for (i in 200 until 9600) assertTrue("i=$i v=${b.data[0][i]}", abs(b.data[0][i]) <= ceil + 1e-2f)
        assertTrue(b.data[0].slice(9468..9516).maxOf { abs(it) } > ceil - 0.05f)
    }
    @Test fun noiseGate_closesOnSilence() {
        val pcm = Array(2) { FloatArray(9600) }
        for (i in 0 until 4800) { pcm[0][i] = 0.5f; pcm[1][i] = 0.5f }
        val g = NoiseGateProcessor().apply { thresholdDb = -30f; releaseMs = 10f; holdMs = 0f }
        g.prepare(fmt, 9600)
        val b = buf(pcm); g.process(b, ctx(9600))
        assertTrue(abs(b.data[0][9400]) < 0.01f)
    }

    // ---------- Spatial ----------
    @Test fun reverb_producesTail() {
        val pcm = Array(2) { FloatArray(24000) }
        for (i in 0 until 200) { pcm[0][i] = (0.8 * sin(2 * PI * 1000 * i / sr)).toFloat(); pcm[1][i] = pcm[0][i] }
        val r = ReverbProcessor().apply { wet = 0.5f; dry = 1f; roomSize = 0.8f }
        r.prepare(fmt, 24000)
        val b = buf(pcm); r.process(b, ctx(24000))
        var tail = 0.0
        for (i in 12000 until 24000) tail += b.data[0][i].toDouble() * b.data[0][i]
        assertTrue("tail=$tail", tail > 1e-4)
    }
    @Test fun delay_producesDelayedCopy() {
        val pcm = Array(2) { FloatArray(4800) }; pcm[0][0] = 1f; pcm[1][0] = 1f
        val d = DelayProcessor().apply { delayMs = 25f; wet = 0.5f; dry = 1f; feedback = 0f }
        d.prepare(fmt, 4800)
        val b = buf(pcm); d.process(b, ctx(4800))
        val idx = (25f * sr / 1000f).roundToInt()
        assertEquals(1f, b.data[0][0], 1e-4f)
        assertEquals(0.5f, b.data[0][idx], 1e-3f)
    }
    @Test fun stereoWidth_monoCollapsesChannels() {
        val pcm = Array(2) { ch -> FloatArray(480) { i -> if (ch == 0) 0.5f else -0.5f } }
        val w = StereoWidthProcessor().apply { width = 0f }
        val b = buf(pcm); w.process(b, ctx(480))
        assertEquals(0f, b.data[0][10], 1e-6f); assertEquals(0f, b.data[1][10], 1e-6f)
    }

    // ---------- Transform ----------
    @Test fun wsola_stretchChangesLength() {
        val out = WsolaTimeStretcher.stretch(sine(440.0, 0.5f, 48000), 2, 1.5f, sr)
        assertTrue(abs(out[0].size - 72000) < 600)
    }
    @Test fun pitchShift_preservesLength() {
        val out = PitchShifter.shift(sine(440.0, 0.5f, 24000), 2, 5f, sr)
        assertEquals(24000, out[0].size)
    }
    @Test fun pitchShift_raisesFundamental() {
        fun zc(p: Array<FloatArray>): Int { var n = 0
            for (i in 1 until p[0].size) if (p[0][i - 1] < 0 && p[0][i] >= 0) n++
            return n }
        val shifted = PitchShifter.shift(sine(220.0, 0.5f, 24000), 1, 7f, sr)
        assertTrue(zc(shifted) > zc(sine(220.0, 0.5f, 24000)))
    }
    @Test fun reverse_reversesSamples() {
        val pcm = arrayOf(floatArrayOf(1f, 2f, 3f, 4f), floatArrayOf(1f, 2f, 3f, 4f))
        val r = ReverseAudio.reverse(pcm)
        assertEquals(4f, r[0][0], 1e-6f); assertEquals(1f, r[0][3], 1e-6f)
    }

    // ---------- Noise / Utility ----------
    @Test fun fft_roundtripImpulse() {
        val n = 1024; val fft = Fft(n)
        val re = FloatArray(n) { if (it == 0) 1f else 0f }; val im = FloatArray(n)
        fft.transform(re, im); fft.inverse(re, im)
        assertEquals(1f, re[0], 1e-3f); assertEquals(0f, re[10], 1e-3f)
    }
    @Test fun spectralNoiseReducer_attenuatesProfiledTone() {
        val noise = sine(1000.0, 0.3f, 48000)
        val profile = NoiseProfileAnalyzer.analyze(noise, 2, sr, frameSize = 2048)
        val reducer = SpectralNoiseReducer(profile, NoiseReductionConfig(strength = 0.8f, frameSize = 2048))
        reducer.prepare(fmt, 48000)
        val b = buf(sine(1000.0, 0.3f, 48000)); reducer.process(b, ctx(48000))
        assertTrue(rmsOf(b.data, 24000, 48000) < rmsOf(sine(1000.0, 0.3f, 48000), 24000, 48000) * 0.5)
    }
    @Test fun dcFilter_removesDcOffset() {
        val pcm = Array(2) { FloatArray(48000) { 0.5f } }
        val f = DcOffsetFilter(); f.prepare(fmt, 48000)
        val b = buf(pcm); f.process(b, ctx(48000))
        assertTrue(abs(b.data[0][47900]) < 0.01f)
    }
    @Test fun softClip_limitsAboveThreshold() {
        val pcm = sine(1000.0, 1.5f, 4800)
        val s = SoftClipProcessor(); val b = buf(pcm); s.process(b, ctx(4800))
        for (i in 0 until 4800) assertTrue(abs(b.data[0][i]) <= 1.0f)
    }
    @Test fun normalizer_peaksAtTarget() {
        val pcm = sine(1000.0, 0.25f, 4800)
        AudioNormalizer.peakNormalize(pcm, -1f)
        assertEquals(dbToLin(-1f), kotlin.math.abs(pcm[0].maxOrNull() ?: 0f), 0.01f)
    }

    // ---------- Automation ----------
    @Test fun keyframes_linearHoldSine() {
        val kf = listOf(AudioKeyframeModel(0.0, 0f), AudioKeyframeModel(1.0, 1f, KeyframeCurve.HOLD), AudioKeyframeModel(2.0, 2f))
        assertEquals(0.5f, AudioKeyframeEngine.valueAt(kf, 0.5, 0f), 1e-4f)
        assertEquals(1f, AudioKeyframeEngine.valueAt(kf, 1.5, 0f), 1e-4f)
        assertEquals(2f, AudioKeyframeEngine.valueAt(kf, 3.0, 0f), 1e-4f)
    }
    @Test fun fadeGain_edges() {
        val fade = AudioFadeSettings(fadeInSec = 1.0, fadeOutSec = 1.0)
        assertEquals(0f, AudioEnvelopeGenerator.fadeGain(fade, 0.0, 2.0), 1e-4f)
        assertEquals(1f, AudioEnvelopeGenerator.fadeGain(fade, 1.0, 2.0), 1e-4f)
        assertTrue(AudioEnvelopeGenerator.fadeGain(fade, 1.9, 2.0) < 0.5f)
    }

    // ---------- Clips / Timeline ----------
    @Test fun clip_splitPreservesSourceMapping() {
        val c = AudioClipModel("c", "t", "s", 0.0, 10.0, 0.0, 10.0, transform = AudioClipTransform(speed = 2f))
        val (l, r) = AudioClipOperations.split(c, 4.0)!!
        assertEquals(4.0, l.timelineDurationSec, 1e-9)
        assertEquals(8.0, l.sourceDurationSec, 1e-9)
        assertEquals(8.0, r.sourceStartSec, 1e-9)
        assertEquals(6.0, r.timelineDurationSec, 1e-9)
    }
    @Test fun clip_trimUpdatesMapping() {
        val c = AudioClipModel("c", "t", "s", 0.0, 10.0, 0.0, 10.0)
        val t = AudioClipOperations.trim(c, 2.0, 5.0)
        assertEquals(2.0, t.sourceStartSec, 1e-9)
        assertEquals(3.0, t.sourceDurationSec, 1e-9)
    }
    @Test fun clip_speedChangesDuration() {
        val c = AudioClipModel("c", "t", "s", 0.0, 10.0, 0.0, 10.0)
        assertEquals(5.0, AudioClipOperations.withSpeed(c, 2f).timelineDurationSec, 1e-9)
    }
    @Test fun overlapResolver_rejectAndTrim() {
        val t = track("t", listOf(clip("t", 0.0, 2.0), clip("t", 1.0, 2.0)))
        assertNull(AudioOverlapResolver.resolve(project(t), "t", OverlapStrategy.REJECT))
        val trimmed = AudioOverlapResolver.resolve(project(t), "t", OverlapStrategy.TRIM_INCOMING)!!
        assertEquals(2.0, trimmed.trackById("t")!!.clips[1].timelineStartSec, 1e-9)
    }
    @Test fun timeline_undoRedoRoundtrip() {
        val ctl = AudioTimelineController(AudioUndoRedoAdapter(50))
        val src = AudioSourceModel("src1", "/unused")
        ctl.setProject(MasterAudioProject(sources = mapOf("src1" to src)))
        val t = track("t1", emptyList())
        ctl.submit(AddTrackCommand(t))
        ctl.submit(AudioEditCommandFactory.addClip("t1", "src1", 0.0, 0.0, 1.0))
        assertEquals(1, ctl.current.trackById("t1")!!.clips.size)
        assertTrue(ctl.undo()); assertEquals(0, ctl.current.trackById("t1")!!.clips.size)
        assertTrue(ctl.redo()); assertEquals(1, ctl.current.trackById("t1")!!.clips.size)
    }
    @Test fun factoryCommands_apply() {
        val c = AudioClipModel("c", "t", "s", 0.0, 1.0, 0.0, 1.0)
        val p = project(track("t", listOf(c)))
        val p2 = AudioEditCommandFactory.setClipVolume("c", 0.5f).apply(p)
        assertEquals(0.5f, p2.trackById("t")!!.clips[0].volume, 1e-4f)
        val p3 = AudioEditCommandFactory.addClipKeyframe("c", AutomationParameter.VOLUME, 0.5, 0.25f).apply(p2)
        val auto = p3.trackById("t")!!.clips[0].automation.first()
        assertTrue(auto.enabled && auto.keyframes.size == 1)
    }
    @Test fun exportValidator_flagsBadRange() {
        val cfg = AudioExportConfig(format = AudioExportFormat.WAV_16, outFile = File("/tmp/x.wav"), startSec = 5.0, endSec = 1.0)
        assertTrue(AudioExportValidator.validate(MasterAudioProject(), cfg).isNotEmpty())
    }

    // ---------- Mixer ----------
    private inner class FakeClipSource(private val level: Float) : AudioClipSource {
        override fun readClip(clip: AudioClipModel, ctx: AudioRenderContext, out: AudioBuffer): ClipRead {
            val t0 = maxOf(ctx.timelineStartSec, clip.timelineStartSec)
            val t1 = minOf(ctx.timelineEndSec, clip.timelineEndSec)
            if (t1 <= t0) return ClipRead(0, 0)
            val off = ((t0 - ctx.timelineStartSec) * ctx.sampleRate).roundToInt().coerceAtLeast(0)
            val n = ((t1 - t0) * ctx.sampleRate).roundToInt().coerceAtMost(out.frames - off).coerceAtLeast(0)
            for (c in 0 until out.channels) java.util.Arrays.fill(out.data[c], off, off + n, level)
            return ClipRead(off, n)
        }
    }
    @Test fun mixer_sumsTwoTracks() {
        val m = MasterAudioMixer(1024)
        m.setProject(project(track("t1", listOf(clip("t1", 0.0, 1.0))), track("t2", listOf(clip("t2", 0.0, 1.0)))), fmt)
        val out = m.renderBlock(ctx(1024), FakeClipSource(0.25f))
        assertEquals(0.5f, out.data[0][300], 1e-3f)
    }
    @Test fun mixer_muteSilencesTrack() {
        val m = MasterAudioMixer(1024)
        m.setProject(project(
            track("t1", listOf(clip("t1", 0.0, 1.0)), AudioTrackSettings(mute = true)),
            track("t2", listOf(clip("t2", 0.0, 1.0)))), fmt)
        val out = m.renderBlock(ctx(1024), FakeClipSource(0.25f))
        assertEquals(0.25f, out.data[0][300], 1e-3f)
    }
    @Test fun mixer_soloExcludesOthers() {
        val m = MasterAudioMixer(1024)
        m.setProject(project(
            track("t1", listOf(clip("t1", 0.0, 1.0)), AudioTrackSettings(solo = true)),
            track("t2", listOf(clip("t2", 0.0, 1.0)))), fmt)
        val out = m.renderBlock(ctx(1024), FakeClipSource(0.25f))
        assertEquals(0.25f, out.data[0][300], 1e-3f)
    }
    @Test fun mixer_masterBus_limiterProtectsHeadroom() {
        val m = MasterAudioMixer(1024)
        m.setProject(project(track("t1", listOf(clip("t1", 0.0, 1.0)))), fmt)
        val out = m.renderBlock(ctx(1024), FakeClipSource(1.0f))
        val ceil = dbToLin(-0.3f)
        for (i in 200 until 1024) assertTrue(abs(out.data[0][i]) <= ceil + 1e-2f)
    }
    @Test fun mixer_clipVolumeAndFadeApplied() {
        val faded = clip("t1", 0.0, 1.0).copy(volume = 0.5f, fade = AudioFadeSettings(fadeInSec = 0.01))
        val m = MasterAudioMixer(1024)
        m.setProject(project(track("t1", listOf(faded))), fmt)
        val out = m.renderBlock(ctx(1024), FakeClipSource(1.0f))
        assertTrue("start=${out.data[0][2]}", out.data[0][2] < 0.05f)
        assertEquals(0.5f, out.data[0][900], 0.05f)
    }

    // ---------- Metering ----------
    @Test fun levelMeter_peakAndRms() {
        val meter = AudioLevelMeter()
        val pcm = sine(1000.0, 0.5f, 4800)
        meter.update(buf(pcm), ctx(4800))
        val s = meter.snapshot()
        assertEquals(0.5f, dbToLin(s.peakDbPerChannel[0]), 0.01f)
        assertEquals(0.3536f, dbToLin(s.rmsDbPerChannel[0]), 0.02f)
    }
    @Test fun loudnessMeter_toneInSaneRange() {
        val lm = LoudnessMeter(); lm.configure(sr, 2)
        val b = buf(sine(1000.0, 0.1f, 96000))
        lm.process(b, 96000)
        val lufs = lm.integratedLufs()
        assertTrue("lufs=$lufs", lufs in -30.0..-16.0)
    }

    // ---------- Waveform ----------
    @Test fun waveformGenerator_minMaxFromRealPcm() {
        val data = WaveformGenerator.generate("s", sine(1000.0, 0.8f, 48000), sr, 100)
        assertEquals(100, data.bucketCount)
        assertTrue(data.max[0] > 0.75f); assertTrue(data.min[0] < -0.75f)
    }

    // ---------- WAV roundtrip ----------
    @Test fun wavWriterReader_roundtrip() {
        val f = File(System.getProperty("java.io.tmpdir"), "ah_test.wav")
        val w = WavWriter(f, sr, 1, false)
        val data = floatArrayOf(0f, 0.5f, -0.5f, 0.25f, -0.25f, 1f, -1f)
        w.write(data, 7); w.close()
        val pcm = com.ahstudio.audio.master.decoder.WavPcmReader().read(f)
        assertEquals(1, pcm.channels)
        assertEquals(sr, pcm.sampleRate)
        assertEquals(7, pcm.data[0].size)
        for (i in data.indices) assertEquals(data[i], pcm.data[0][i], 1f / 32768f)
        f.delete()
    }
}
