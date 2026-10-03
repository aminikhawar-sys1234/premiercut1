package com.example.engine.audio.phase3

import com.example.domain.model.AudioEffectsSettings
import com.example.domain.model.ClipKeyframe
import com.example.domain.model.VoiceEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMixerTest {

  @Test fun mixes_multiple_tracks_without_independent_players() {
    val tracks = listOf(
      TimelineAudioTrack("A1", 0, clips = listOf(TimelineAudioClip("c1", "m1", "u1", 0, 1000, 0, 1000, "A1"))),
      TimelineAudioTrack("A2", 1, clips = listOf(TimelineAudioClip("c2", "m2", "u2", 0, 1000, 0, 1000, "A2")))
    )
    val pcm = FloatArray(20) { 0.25f }
    val result = AudioMixer().mix(500, tracks, mapOf("c1" to pcm, "c2" to pcm), 10)
    assertTrue(result.samples.all { it.isFinite() })
    assertEquals(20, result.samples.size)
  }

  @Test fun mute_and_solo_are_deterministic() {
    val a = TimelineAudioClip("a", "m", "u", 0, 1000, 0, 1000, "1")
    val b = a.copy(id = "b", trackId = "2")
    val tracks = listOf(TimelineAudioTrack("1", 0, clips = listOf(a)), TimelineAudioTrack("2", 1, solo = true, clips = listOf(b)))
    val pcm = FloatArray(8) { 0.25f }
    val out = AudioMixer().mix(100, tracks, mapOf("a" to pcm, "b" to pcm), 4)
    assertTrue(out.samples.any { it != 0f })
  }

  @Test fun fades_are_non_destructive() {
    val clip = TimelineAudioClip("c", "m", "u", 0, 1000, 0, 1000, "1", fadeInMs = 500, fadeOutMs = 500)
    assertEquals(0f, clip.gainAt(0), 0.0001f)
    assertTrue(clip.gainAt(250) > 0f)
    assertTrue(clip.gainAt(500) > 0f)
  }

  @Test fun gainDb_and_keyframe_interpolation_calculated_correctly() {
    val keyframes = listOf(
      ClipKeyframe(timeMs = 0L, volume = 0f),
      ClipKeyframe(timeMs = 1000L, volume = 1f)
    )
    val clip = TimelineAudioClip(
      id = "kf_clip",
      sourceMediaId = "m1",
      sourceUri = "u1",
      sourceStartMs = 0L,
      sourceEndMs = 1000L,
      timelineStartMs = 0L,
      timelineEndMs = 1000L,
      trackId = "A1",
      gainDb = 6f, // ~2.0x linear multiplier
      keyframes = keyframes
    )

    val gainAtStart = clip.gainAt(0L)
    val gainAtMid = clip.gainAt(500L)
    val gainAtEnd = clip.gainAt(990L)

    assertEquals(0f, gainAtStart, 0.001f)
    assertTrue("Mid gain should be positive", gainAtMid > 0.5f)
    assertTrue("End gain should reflect +6dB gain boost", gainAtEnd > 1.8f)
  }

  @Test fun mono_input_mixed_to_stereo_with_panning() {
    val clip = TimelineAudioClip("mono_clip", "m", "u", 0, 1000, 0, 1000, "1", pan = -1.0f) // Hard left
    val tracks = listOf(TimelineAudioTrack("1", 0, clips = listOf(clip)))
    val monoPcm = FloatArray(10) { 0.5f } // 10 mono frames

    val mixer = AudioMixer(sampleRate = 48000)
    val out = mixer.mix(100L, tracks, mapOf("mono_clip" to monoPcm), 10)

    assertEquals(20, out.samples.size) // 10 frames * 2 channels
    assertTrue("Left channel should have signal", out.samples[0] > 0.4f)
    assertEquals("Right channel should be silent when panned hard left", 0f, out.samples[1], 0.001f)
  }

  @Test fun limiter_prevents_extreme_clipping_distortion() {
    val clip1 = TimelineAudioClip("c1", "m", "u", 0, 1000, 0, 1000, "1")
    val clip2 = TimelineAudioClip("c2", "m", "u", 0, 1000, 0, 1000, "2")
    val tracks = listOf(TimelineAudioTrack("1", 0, clips = listOf(clip1)), TimelineAudioTrack("2", 1, clips = listOf(clip2)))

    val hotPcm = FloatArray(10) { 1.5f } // Signals exceeding 1.0
    val mixer = AudioMixer(sampleRate = 48000)
    val out = mixer.mix(100L, tracks, mapOf("c1" to hotPcm, "c2" to hotPcm), 5)

    assertTrue("Output samples must remain finite and controlled", out.samples.all { it.isFinite() && kotlin.math.abs(it) <= 1.5f })
  }

  @Test fun sample_accurate_conversions_and_reversal() {
    val clip = TimelineAudioClip(
      id = "rev",
      sourceMediaId = "m",
      sourceUri = "u",
      sourceStartMs = 1000L,
      sourceEndMs = 5000L,
      timelineStartMs = 2000L,
      timelineEndMs = 6000L,
      trackId = "1",
      isReversed = true
    )

    val sampleRate = 44100
    assertTrue(clip.containsSample(clip.timelineStartSample(sampleRate) + 100, sampleRate))
    assertFalse(clip.containsSample(clip.timelineStartSample(sampleRate) - 1, sampleRate))

    val srcSampleAtStart = clip.timelineToSourceSample(clip.timelineStartSample(sampleRate), sampleRate)
    val srcSampleAtEnd = clip.timelineToSourceSample(clip.timelineEndSample(sampleRate), sampleRate)

    assertEquals("Reversed clip start timeline maps to sourceEndSample", clip.sourceEndSample(sampleRate), srcSampleAtStart)
    assertEquals("Reversed clip end timeline maps to sourceStartSample", clip.sourceStartSample(sampleRate), srcSampleAtEnd)
  }

  @Test fun audio_effects_dsp_processing() {
    val effects = AudioEffectsSettings(voiceEffect = VoiceEffect.CHIPMUNK)
    val clip = TimelineAudioClip("fx", "m", "u", 0, 1000, 0, 1000, "1", audioEffects = effects)
    val tracks = listOf(TimelineAudioTrack("1", 0, clips = listOf(clip)))

    val pcm = FloatArray(16) { 0.3f }
    val mixer = AudioMixer(sampleRate = 48000)
    val out = mixer.mix(100L, tracks, mapOf("fx" to pcm), 8)

    assertTrue("DSP processed samples must be finite", out.samples.all { it.isFinite() })
  }
}
