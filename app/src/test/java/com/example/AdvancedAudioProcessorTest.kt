package com.example

import com.example.domain.model.AudioEffectsSettings
import com.example.domain.model.VoiceEffect
import com.example.engine.audio.AdvancedAudioProcessor
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AdvancedAudioProcessorTest {

  @Test
  fun testNoiseGateRetainsLoudVoiceAndAttenuatesNoise() {
    // 1 second buffer at 44.1kHz
    val sampleRate = 44100
    val pcm = ShortArray(sampleRate)

    // First half: faint noise floor (amplitude 80)
    for (i in 0 until sampleRate / 2) {
      pcm[i] = (80.0 * sin(i * 0.1)).toInt().toShort()
    }
    // Second half: loud speech tone (amplitude 15000)
    for (i in sampleRate / 2 until sampleRate) {
      pcm[i] = (15000.0 * sin(2.0 * Math.PI * 440.0 * (i - sampleRate / 2) / sampleRate)).toInt().toShort()
    }

    val effects = AudioEffectsSettings(noiseReductionDb = 18f)
    val processed = AdvancedAudioProcessor.processPcmBuffer(pcm, sampleRate, 1, effects)

    assertEquals(pcm.size, processed.size)

    // Verify noise section is significantly attenuated
    val maxNoise = (0 until sampleRate / 2).maxOf { abs(processed[it].toInt()) }
    assertTrue("Noise floor should be attenuated, was $maxNoise", maxNoise < 80)

    // Verify loud speech is preserved with high amplitude
    val maxSpeech = (sampleRate / 2 until sampleRate).maxOf { abs(processed[it].toInt()) }
    assertTrue("Speech amplitude should be preserved, was $maxSpeech", maxSpeech > 10000)
  }

  @Test
  fun testEqualizerBandsModulation() {
    val sampleRate = 48000
    val pcm = ShortArray(sampleRate) { 5000 }

    // Boost treble +6dB, cut bass -6dB
    val fx = AudioEffectsSettings(lowGainDb = -6f, highGainDb = 6f)
    val processed = AdvancedAudioProcessor.processPcmBuffer(pcm, sampleRate, 1, fx)

    assertEquals(pcm.size, processed.size)
    assertNotNull(processed)
  }

  @Test
  fun testVoiceChangerEffectsProcessing() {
    val sampleRate = 48000
    val pcm = ShortArray(sampleRate) { i -> (10000.0 * sin(i * 0.05)).toInt().toShort() }

    val voiceEffects = listOf(
      VoiceEffect.ROBOT,
      VoiceEffect.DEEP_VOICE,
      VoiceEffect.CHIPMUNK,
      VoiceEffect.ECHO_REVERB,
      VoiceEffect.TELEPHONE,
      VoiceEffect.RADIO,
      VoiceEffect.MEGAPHONE,
      VoiceEffect.VOCODER,
      VoiceEffect.CHIPTUNE
    )

    for (effect in voiceEffects) {
      val fx = AudioEffectsSettings(voiceEffect = effect)
      val processed = AdvancedAudioProcessor.processPcmBuffer(pcm, sampleRate, 1, fx)
      assertTrue("Effect $effect should produce samples", processed.isNotEmpty())
    }
  }

  @Test
  fun testVolumeNormalization() {
    val pcm = ShortArray(1000) { 1000 } // Very quiet audio
    val fx = AudioEffectsSettings(normalizeVolume = true)
    val processed = AdvancedAudioProcessor.processPcmBuffer(pcm, 44100, 1, fx)

    val maxVal = processed.maxOf { abs(it.toInt()) }
    assertTrue("Normalized volume should reach near full scale, was $maxVal", maxVal > 25000)
  }
}
