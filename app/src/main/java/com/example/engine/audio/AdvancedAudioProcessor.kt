package com.example.engine.audio

import com.example.domain.model.AudioEffectsSettings
import com.example.domain.model.VoiceEffect
import kotlin.math.*

/**
 * Enterprise Audio Processor: Real-Time Noise Reduction, Multi-Band EQ, Pitch Shift,
 * Voice Effects (Robot, Chipmunk, Deep Voice, Echo, Telephone), Volume Normalization, and Automation.
 */
object AdvancedAudioProcessor {

  /**
   * Processes a 44.1kHz / 48kHz float PCM buffer (-1.0f .. 1.0f) returning a transformed float buffer.
   */
  fun processFloatBuffer(
    pcm: FloatArray,
    sampleRate: Int = 44100,
    channels: Int = 2,
    effects: AudioEffectsSettings
  ): FloatArray {
    if (pcm.isEmpty()) return pcm
    val shortArray = ShortArray(pcm.size) { i ->
      (pcm[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
    }
    val processedShorts = processPcmBuffer(shortArray, sampleRate, channels, effects)
    val result = FloatArray(processedShorts.size) { i ->
      processedShorts[i] / 32768.0f
    }
    return result
  }

  /**
   * Processes a 44.1kHz 16-bit stereo PCM buffer in-place or returning a transformed buffer.
   */
  fun processPcmBuffer(
    pcm: ShortArray,
    sampleRate: Int = 44100,
    channels: Int = 2,
    effects: AudioEffectsSettings
  ): ShortArray {
    if (pcm.isEmpty()) return pcm
    var output = pcm

    // 1. Noise Reduction / Suppression (Spectral Gate)
    if (effects.noiseReductionDb > 0.0f) {
      output = applyNoiseGate(output, effects.noiseReductionDb)
    }

    // 2. Multi-Band Equalizer (Bass, Mid, Treble)
    if (effects.lowGainDb != 0.0f || effects.midGainDb != 0.0f || effects.highGainDb != 0.0f) {
      output = applyEqualizer(output, sampleRate, channels, effects.lowGainDb, effects.midGainDb, effects.highGainDb)
    }

    // 3. Voice Effects & Pitch Shift
    if (effects.voiceEffect != VoiceEffect.NONE || effects.pitchShiftSemitones != 0.0f) {
      output = applyVoiceEffect(output, sampleRate, channels, effects.voiceEffect, effects.pitchShiftSemitones)
    }

    // 4. Volume Normalization
    if (effects.normalizeVolume) {
      output = normalizePcm(output)
    }

    return output
  }

  private fun applyNoiseGate(pcm: ShortArray, reductionDb: Float): ShortArray {
    val result = ShortArray(pcm.size)
    // Accurate background noise floor threshold around -46 dBFS
    val noiseFloorThresholdAmp = (32767.0 * 10.0.pow(-46.0 / 20.0)).toInt() // ~164 amplitude
    val attenuation = 10.0.pow((-reductionDb.coerceIn(0f, 36f) / 20.0).toDouble()).toFloat()
    val alpha = 0.96f // smoothing factor for noise floor envelope

    var env = 0.0f
    for (i in pcm.indices) {
      val absSample = abs(pcm[i].toFloat())
      env = alpha * env + (1f - alpha) * absSample
      result[i] = if (env < noiseFloorThresholdAmp) {
        (pcm[i] * attenuation).toInt().coerceIn(-32768, 32767).toShort()
      } else {
        pcm[i]
      }
    }
    return result
  }

  private fun applyEqualizer(
    pcm: ShortArray,
    sampleRate: Int,
    channels: Int,
    lowDb: Float,
    midDb: Float,
    highDb: Float
  ): ShortArray {
    val result = ShortArray(pcm.size)
    val lowGain = 10.0.pow((lowDb / 20.0).toDouble()).toFloat()
    val midGain = 10.0.pow((midDb / 20.0).toDouble()).toFloat()
    val highGain = 10.0.pow((highDb / 20.0).toDouble()).toFloat()

    // 3-band RC filter state for left & right channels
    var lowL = 0f; var lowR = 0f
    var highL = 0f; var highR = 0f

    val safeRate = sampleRate.coerceAtLeast(8000).toFloat()
    val dt = 1.0f / safeRate
    val rcLow = 1.0f / (2.0f * Math.PI.toFloat() * 250.0f)
    val alphaLow = (dt / (rcLow + dt)).coerceIn(0.001f, 0.999f)
    val rcHigh = 1.0f / (2.0f * Math.PI.toFloat() * 4000.0f)
    val alphaHigh = (dt / (rcHigh + dt)).coerceIn(0.001f, 0.999f)

    for (i in pcm.indices step channels) {
      val sampleL = pcm[i].toFloat()
      val sampleR = if (channels > 1 && i + 1 < pcm.size) pcm[i + 1].toFloat() else sampleL

      // Low pass filter
      lowL += alphaLow * (sampleL - lowL)
      lowR += alphaLow * (sampleR - lowR)

      // High pass filter
      highL += alphaHigh * (sampleL - highL)
      highR += alphaHigh * (sampleR - highR)

      val bandLowL = lowL
      val bandHighL = sampleL - highL
      val bandMidL = sampleL - bandLowL - bandHighL

      val bandLowR = lowR
      val bandHighR = sampleR - highR
      val bandMidR = sampleR - bandLowR - bandHighR

      val outL = (bandLowL * lowGain + bandMidL * midGain + bandHighL * highGain)
      val outR = (bandLowR * lowGain + bandMidR * midGain + bandHighR * highGain)

      val clampedL = if (outL.isNaN() || outL.isInfinite()) 0 else outL.toInt().coerceIn(-32768, 32767)
      val clampedR = if (outR.isNaN() || outR.isInfinite()) 0 else outR.toInt().coerceIn(-32768, 32767)
      result[i] = clampedL.toShort()
      if (channels > 1 && i + 1 < pcm.size) {
        result[i + 1] = clampedR.toShort()
      }
    }
    return result
  }

  private fun applyVoiceEffect(
    pcm: ShortArray,
    sampleRate: Int,
    channels: Int,
    effect: VoiceEffect,
    customPitchShift: Float
  ): ShortArray {
    return when (effect) {
      VoiceEffect.DEEP_VOICE -> pitchShiftResample(pcm, channels, pitchRatio = 0.72f)
      VoiceEffect.CHIPMUNK -> pitchShiftResample(pcm, channels, pitchRatio = 1.45f)
      VoiceEffect.ROBOT -> {
        val res = ShortArray(pcm.size)
        val carrierFreq = 440.0
        for (i in pcm.indices step channels) {
          val t = (i / channels).toDouble() / sampleRate
          val carrier = sin(2.0 * PI * carrierFreq * t).toFloat()
          res[i] = (pcm[i] * carrier).toInt().coerceIn(-32768, 32767).toShort()
          if (channels > 1 && i + 1 < pcm.size) {
            res[i + 1] = (pcm[i + 1] * carrier).toInt().coerceIn(-32768, 32767).toShort()
          }
        }
        res
      }
      VoiceEffect.ECHO_REVERB -> {
        val delaySamples = (sampleRate * 0.12).toInt() * channels // 120ms delay
        val res = ShortArray(pcm.size)
        val decay = 0.42f
        for (i in pcm.indices) {
          val delayed = if (i >= delaySamples) res[i - delaySamples] else 0
          val mix = pcm[i] + (delayed * decay).toInt()
          res[i] = mix.coerceIn(-32768, 32767).toShort()
        }
        res
      }
      VoiceEffect.TELEPHONE -> {
        applyEqualizer(pcm, sampleRate, channels, lowDb = -18f, midDb = 6f, highDb = -18f)
      }
      VoiceEffect.RADIO -> {
        applyEqualizer(pcm, sampleRate, channels, lowDb = -22f, midDb = 8f, highDb = -22f)
      }
      VoiceEffect.MEGAPHONE -> {
        val filtered = applyEqualizer(pcm, sampleRate, channels, lowDb = -16f, midDb = 10f, highDb = -12f)
        for (i in filtered.indices) {
          filtered[i] = (filtered[i] * 1.5f).toInt().coerceIn(-32000, 32000).toShort()
        }
        filtered
      }
      VoiceEffect.ANONYMOUS -> pitchShiftResample(pcm, channels, pitchRatio = 0.82f)
      VoiceEffect.ALIEN -> {
        val shifted = pitchShiftResample(pcm, channels, pitchRatio = 1.25f)
        for (i in shifted.indices) {
          val tremolo = (1.0 + 0.3 * kotlin.math.sin(i * 0.05)).toFloat()
          shifted[i] = (shifted[i] * tremolo).toInt().coerceIn(-32768, 32767).toShort()
        }
        shifted
      }
      VoiceEffect.GIANT -> pitchShiftResample(pcm, channels, pitchRatio = 0.62f)
      VoiceEffect.ELF -> pitchShiftResample(pcm, channels, pitchRatio = 1.62f)
      VoiceEffect.CARTOON -> pitchShiftResample(pcm, channels, pitchRatio = 1.35f)
      VoiceEffect.AUTOTUNE, VoiceEffect.SPEECH_TO_SONG -> {
        val bright = applyEqualizer(pcm, sampleRate, channels, lowDb = -4f, midDb = 3f, highDb = 6f)
        pitchShiftResample(bright, channels, pitchRatio = 1.08f)
      }
      VoiceEffect.CHIPTUNE -> {
        val res = ShortArray(pcm.size)
        for (i in pcm.indices) {
          // 8-bit reduction bitcrush
          res[i] = ((pcm[i].toInt() shr 8) shl 8).toShort()
        }
        res
      }
      VoiceEffect.VOCODER -> {
        val res = ShortArray(pcm.size)
        val carrierFreq = 220.0
        for (i in pcm.indices step channels) {
          val t = (i / channels).toDouble() / sampleRate
          val carrier = kotlin.math.sin(2.0 * Math.PI * carrierFreq * t).toFloat()
          res[i] = (pcm[i] * carrier * 1.2f).toInt().coerceIn(-32768, 32767).toShort()
          if (channels > 1 && i + 1 < pcm.size) {
            res[i + 1] = (pcm[i + 1] * carrier * 1.2f).toInt().coerceIn(-32768, 32767).toShort()
          }
        }
        res
      }
      VoiceEffect.DISCO -> {
        val delaySamples = (sampleRate * 0.15).toInt() * channels
        val res = ShortArray(pcm.size)
        val decay = 0.5f
        for (i in pcm.indices) {
          val delayed = if (i >= delaySamples) res[i - delaySamples] else 0
          val mix = pcm[i] + (delayed * decay).toInt()
          res[i] = mix.coerceIn(-32768, 32767).toShort()
        }
        pitchShiftResample(res, channels, pitchRatio = 1.12f)
      }
      VoiceEffect.NONE -> {
        if (customPitchShift != 0.0f) {
          val ratio = 2.0.pow((customPitchShift / 12.0).toDouble()).toFloat()
          pitchShiftResample(pcm, channels, ratio)
        } else pcm
      }
    }
  }

  private fun pitchShiftResample(pcm: ShortArray, channels: Int, pitchRatio: Float): ShortArray {
    if (abs(pitchRatio - 1.0f) < 0.01f || pcm.isEmpty()) return pcm
    val totalFrames = pcm.size / channels
    val newTotalFrames = (totalFrames / pitchRatio).toInt().coerceAtLeast(1)
    val result = ShortArray(newTotalFrames * channels)

    for (f in 0 until newTotalFrames) {
      val srcFrame = (f * pitchRatio).toFloat()
      val idx0 = srcFrame.toInt().coerceIn(0, totalFrames - 1)
      val idx1 = (idx0 + 1).coerceIn(0, totalFrames - 1)
      val frac = srcFrame - idx0

      for (c in 0 until channels) {
        val s0 = pcm[idx0 * channels + c].toFloat()
        val s1 = pcm[idx1 * channels + c].toFloat()
        val interp = s0 + frac * (s1 - s0)
        result[f * channels + c] = interp.toInt().coerceIn(-32768, 32767).toShort()
      }
    }
    return result
  }

  fun normalizePcm(pcm: ShortArray): ShortArray {
    if (pcm.isEmpty()) return pcm
    var maxPeak = 0
    for (s in pcm) {
      val absVal = abs(s.toInt())
      if (absVal > maxPeak) maxPeak = absVal
    }
    if (maxPeak == 0 || maxPeak >= 31130) return pcm

    val scale = 31130.0f / maxPeak.toFloat()
    val result = ShortArray(pcm.size)
    for (i in pcm.indices) {
      result[i] = (pcm[i] * scale).toInt().coerceIn(-32768, 32767).toShort()
    }
    return result
  }
}
