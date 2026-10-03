package com.ahstudio.audio.master

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ahstudio.audio.master.core.*
import com.ahstudio.audio.master.dsp.eq.ParametricEqualizer
import com.ahstudio.audio.master.dsp.dynamics.CompressorProcessor
import com.ahstudio.audio.master.dsp.dynamics.LimiterProcessor
import com.ahstudio.audio.master.dsp.dynamics.NoiseGateProcessor
import com.ahstudio.audio.master.dsp.spatial.ReverbProcessor
import com.ahstudio.audio.master.dsp.transform.WsolaTimeStretcher
import com.ahstudio.audio.master.mixer.MasterAudioMixer
import com.ahstudio.audio.master.metering.LoudnessMeter
import com.ahstudio.audio.master.model.*
import com.ahstudio.audio.master.clips.AudioClipReader
import com.ahstudio.audio.master.cache.DecodedAudioCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class MasterAudioEngineTestSuite {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testAudioTimeMapper() {
        val mapper = AudioTimeMapper
        val samples = mapper.secToSample(1.5, 48000)
        assertEquals(72000L, samples)
        val sec = mapper.sampleToSec(72000L, 48000)
        assertEquals(1.5, sec, 0.0001)
        val us = mapper.secToUs(1.0)
        assertEquals(1000000L, us)
    }

    @Test
    fun testAudioBufferOperations() {
        val pool = AudioBufferPool(channels = 2, frames = 1024)
        val buf = pool.obtain()
        assertNotNull(buf)
        assertEquals(2, buf.channels)
        assertEquals(1024, buf.frames)

        // Fill with constant signal
        for (ch in 0 until buf.channels) {
            val samples = buf.data[ch]
            for (i in 0 until buf.frames) {
                samples[i] = 0.5f
            }
        }

        // Interleave check
        val interleaved = FloatArray(1024 * 2)
        buf.interleave(interleaved, 1024)
        assertEquals(0.5f, interleaved[0], 0.0001f)
        assertEquals(0.5f, interleaved[1], 0.0001f)

        val peak = buf.maxAbs(1024)
        assertEquals(0.5f, peak, 0.001f)

        buf.clear()
        assertEquals(0f, buf.maxAbs(1024), 0.0001f)
        pool.recycle(buf)
    }

    @Test
    fun testParametricEqualizer() {
        val eq = ParametricEqualizer()
        val buf = AudioBuffer(channels = 2, frames = 1024)
        for (c in 0 until buf.channels) {
            for (i in 0 until buf.frames) {
                buf.data[c][i] = 0.5f
            }
        }
        val ctx = AudioRenderContext(format = AudioFormat(48000, 2), timelineStartSec = 0.0, frames = 1024, blockIndex = 0)
        eq.prepare(ctx.format, 1024)
        eq.process(buf, ctx)
        assertTrue("Output should remain finite", buf.data[0].all { it.isFinite() })
    }

    @Test
    fun testCompressorAndLimiter() {
        val comp = CompressorProcessor().apply {
            thresholdDb = -12f
            ratio = 4f
        }
        val limiter = LimiterProcessor().apply {
            ceilingDb = -1f
        }
        val buf = AudioBuffer(channels = 2, frames = 1024)
        for (c in 0 until buf.channels) {
            for (i in 0 until buf.frames) {
                buf.data[c][i] = 0.9f
            }
        }

        val ctx = AudioRenderContext(format = AudioFormat(48000, 2), timelineStartSec = 0.0, frames = 1024, blockIndex = 0)
        comp.prepare(ctx.format, 1024)
        limiter.prepare(ctx.format, 1024)

        comp.process(buf, ctx)
        limiter.process(buf, ctx)

        assertTrue("Left channel finite and controlled", buf.data[0].all { it.isFinite() && abs(it) <= 1.0f })
        assertTrue("Right channel finite and controlled", buf.data[1].all { it.isFinite() && abs(it) <= 1.0f })
    }

    @Test
    fun testNoiseGate() {
        val gate = NoiseGateProcessor().apply {
            thresholdDb = -30f
        }
        val buf = AudioBuffer(channels = 2, frames = 1024)
        for (c in 0 until buf.channels) {
            for (i in 0 until buf.frames) {
                buf.data[c][i] = 0.001f // Below -30dB
            }
        }

        val ctx = AudioRenderContext(format = AudioFormat(48000, 2), timelineStartSec = 0.0, frames = 1024, blockIndex = 0)
        gate.prepare(ctx.format, 1024)
        gate.process(buf, ctx)
        assertTrue("Noise gate attenuated low signal", buf.data[0][500] < 0.001f)
    }

    @Test
    fun testReverbProcessor() {
        val reverb = ReverbProcessor().apply {
            wet = 0.3f
            roomSize = 0.5f
        }
        val buf = AudioBuffer(channels = 2, frames = 1024)
        for (c in 0 until buf.channels) {
            for (i in 0 until buf.frames) {
                buf.data[c][i] = 0.4f
            }
        }

        val ctx = AudioRenderContext(format = AudioFormat(48000, 2), timelineStartSec = 0.0, frames = 1024, blockIndex = 0)
        reverb.prepare(ctx.format, 1024)
        reverb.process(buf, ctx)
        assertTrue("Reverb output valid", buf.data[0].all { it.isFinite() } && buf.data[1].all { it.isFinite() })
    }

    @Test
    fun testWsolaTimeStretcher() {
        val input = Array(1) { FloatArray(2048) { kotlin.math.sin(2.0 * Math.PI * 440.0 * it / 48000.0).toFloat() } }
        val output = WsolaTimeStretcher.stretch(input, 1, 1.25f, 48000)
        assertTrue("Time stretch produces samples", output[0].isNotEmpty())
        assertTrue("Time stretch samples finite", output[0].all { it.isFinite() })
    }

    @Test
    fun testLoudnessMeter() {
        val meter = LoudnessMeter()
        meter.configure(48000, 2)
        val buf = AudioBuffer(channels = 2, frames = 1024)
        for (i in 0 until buf.frames) {
            val sample = (0.5 * kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 48000.0)).toFloat()
            buf.data[0][i] = sample
            buf.data[1][i] = sample
        }
        meter.process(buf, 1024)
        val lufs = meter.integratedLufs()
        assertTrue("LUFS value calculated ($lufs)", lufs.isFinite())
    }

    @Test
    fun testMasterAudioMixer() {
        val mixer = MasterAudioMixer(maxFrames = 1024)
        val track = AudioTrackModel(id = "t1", index = 0)
        val clip = AudioClipModel(
            id = "c1",
            trackId = "t1",
            sourceId = "s1",
            timelineStartSec = 0.0,
            timelineDurationSec = 2.0,
            sourceStartSec = 0.0,
            sourceDurationSec = 2.0
        )
        val trackWithClip = track.copy(clips = listOf(clip))
        val proj = MasterAudioProject(id = "p1", tracks = listOf(trackWithClip))

        val fmt = AudioFormat(48000, 2)
        mixer.setProject(proj, fmt)
        val ctx = AudioRenderContext(fmt, 0.0, 1024, 0)
        val cache = DecodedAudioCache(context, fmt)
        val resultBuf = mixer.renderBlock(ctx, AudioClipReader(cache))
        assertNotNull(resultBuf)
        assertEquals(1024, resultBuf.frames)
    }
}
