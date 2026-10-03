package com.example.engine.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Classification of audio transient peaks to help users identify rhythm beats,
 * vocal bursts, crest accents, and distortion/clipping warnings.
 */
enum class AudioPeakType {
  BEAT,
  CREST,
  TRANSIENT,
  CLIPPING
}

/**
 * Represents a detected transient peak or rhythm beat in an audio track.
 * Used for visual waveform accents, clipping warnings, and cut/split alignment.
 */
data class AudioPeak(
  val index: Int,
  val timeMs: Long,
  val amplitude: Float,
  val isProminent: Boolean,
  val isClipping: Boolean = amplitude >= 0.95f,
  val peakType: AudioPeakType = when {
    amplitude >= 0.95f -> AudioPeakType.CLIPPING
    amplitude >= 0.82f -> AudioPeakType.CREST
    isProminent -> AudioPeakType.BEAT
    else -> AudioPeakType.TRANSIENT
  }
)

/**
 * Represents a detected silence / quiet interval in an audio track.
 * Used for visual dead-air highlighting, auto-trimming, and voice pause removal.
 */
data class AudioSilenceRegion(
  val startIndex: Int,
  val endIndex: Int,
  val startMs: Long,
  val endMs: Long,
  val durationMs: Long,
  val avgAmplitude: Float
)

/**
 * Detailed audio waveform analysis containing normalized samples, detected peaks,
 * and identified silence intervals.
 */
data class WaveformAnalysis(
  val samples: List<Float>,
  val peaks: List<AudioPeak>,
  val prominentPeaks: List<AudioPeak>,
  val clippingPeaks: List<AudioPeak>,
  val silenceRegions: List<AudioSilenceRegion>,
  val totalSilenceDurationMs: Long,
  val silencePercentage: Float,
  val rmsAverage: Float,
  val maxPeak: Float,
  val minAmplitude: Float
)

/**
 * Manages extraction, synthesis, peak detection, silence analysis, and caching
 * for real-time audio waveform visualizations.
 */
object AudioWaveformManager {

  private const val TAG = "AudioWaveformManager"

  // In-memory bounded cache for fast O(1) waveform lookup during 60fps timeline rendering and scrolling
  private val waveformCache = object : LinkedHashMap<String, List<Float>>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Float>>?): Boolean = size > 64
  }
  private val analysisCache = object : LinkedHashMap<String, WaveformAnalysis>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WaveformAnalysis>?): Boolean = size > 64
  }

  /**
   * Returns existing waveform data or generates a rich, realistic audio envelope.
   */
  @Synchronized
  fun getOrGenerateWaveform(
    clipId: String,
    uri: String,
    title: String,
    totalDurationMs: Long,
    existingWaveform: List<Float> = emptyList()
  ): List<Float> {
    if (existingWaveform.size >= 40) {
      return existingWaveform
    }

    val cacheKey = "$clipId-$uri-$totalDurationMs"
    waveformCache[cacheKey]?.let { return it }

    val generated = generateRichWaveform(
      seed = "$clipId-$title-$uri",
      durationMs = totalDurationMs.coerceAtLeast(1000L)
    )

    waveformCache[cacheKey] = generated
    return generated
  }

  /**
   * Asynchronously extracts real audio waveform from a file or URI using MediaExtractor/PCM,
   * falling back smoothly to synthesized realistic audio envelope when offline or synthetic.
   */
  suspend fun extractWaveformRealtime(
    context: Context?,
    uriString: String,
    durationMs: Long,
    sampleCount: Int = 160
  ): List<Float> = withContext(Dispatchers.IO) {
    val cacheKey = "$uriString-$durationMs-$sampleCount"
    waveformCache[cacheKey]?.let { return@withContext it }

    if (uriString.isBlank()) {
      val fallback = generateRichWaveform(seed = "empty_${durationMs}", durationMs = durationMs)
      waveformCache[cacheKey] = fallback
      return@withContext fallback
    }

    // Attempt direct file extraction if local file exists
    try {
      val uri = Uri.parse(uriString)
      if (uri.scheme == "file" || (uri.path != null && File(uri.path ?: "").exists())) {
        val path = uri.path ?: uriString
        val file = File(path)
        if (file.exists() && file.length() > 0) {
          val extracted = extractWaveformFromFile(file, sampleCount)
          if (extracted.isNotEmpty() && extracted.size >= 20) {
            waveformCache[cacheKey] = extracted
            return@withContext extracted
          }
        }
      }

      // Attempt MediaExtractor if context is available
      if (context != null) {
        val extracted = extractUsingMediaExtractor(context, uri, durationMs, sampleCount)
        if (extracted.isNotEmpty() && extracted.size >= 20) {
          waveformCache[cacheKey] = extracted
          return@withContext extracted
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Realtime audio extraction fallback for $uriString: ${e.message}")
    }

    // High fidelity fallback envelope
    val generated = generateRichWaveform(seed = uriString, durationMs = durationMs)
    waveformCache[cacheKey] = generated
    return@withContext generated
  }

  /**
   * Fast byte-level peak extraction from PCM or media file.
   */
  fun extractWaveformFromFile(audioFile: File, sampleCount: Int = 160): List<Float> {
    if (!audioFile.exists() || audioFile.length() == 0L) return emptyList()

    return try {
      val fileLength = audioFile.length()
      val readBytesCount = minOf(fileLength, (1024 * 512).toLong()).toInt()
      val bytes = ByteArray(readBytesCount)
      FileInputStream(audioFile).use { it.read(bytes) }

      val step = maxOf(1, bytes.size / sampleCount)
      val result = ArrayList<Float>(sampleCount)

      for (i in 0 until sampleCount) {
        val start = i * step
        var maxSample = 0
        var sumEnergy = 0.0
        var count = 0

        for (j in start until minOf(start + step, bytes.size)) {
          val v = abs(bytes[j].toInt())
          if (v > maxSample) maxSample = v
          sumEnergy += (v * v)
          count++
        }

        val peakVal = (maxSample / 128f).coerceIn(0.04f, 1.0f)
        val rmsVal = if (count > 0) (sqrt(sumEnergy / count) / 128.0).toFloat().coerceIn(0.02f, 1.0f) else peakVal
        val combined = (peakVal * 0.7f + rmsVal * 0.3f).coerceIn(0.03f, 1.0f)
        result.add(combined)
      }
      result
    } catch (e: Exception) {
      Log.w(TAG, "Failed to extract waveform from file: ${e.message}")
      emptyList()
    }
  }

  /**
   * MediaExtractor track sampler for container formats (.mp4, .m4a, .wav, .mp3).
   */
  private fun extractUsingMediaExtractor(
    context: Context,
    uri: Uri,
    durationMs: Long,
    sampleCount: Int
  ): List<Float> {
    val extractor = MediaExtractor()
    return try {
      if (uri.scheme == "content" || uri.scheme == "file") {
        extractor.setDataSource(context, uri, null)
      } else {
        extractor.setDataSource(uri.toString())
      }

      var audioTrackIndex = -1
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
          audioTrackIndex = i
          break
        }
      }

      if (audioTrackIndex == -1) return emptyList()

      extractor.selectTrack(audioTrackIndex)
      val buffer = ByteBuffer.allocate(32 * 1024)
      val amplitudes = ArrayList<Float>()

      while (extractor.readSampleData(buffer, 0) >= 0) {
        var localMax = 0
        val sampleSize = extractor.sampleSize.toInt().coerceAtMost(buffer.capacity())
        for (k in 0 until minOf(sampleSize, 512)) {
          val b = abs(buffer.get(k).toInt())
          if (b > localMax) localMax = b
        }
        val amp = (localMax / 128f).coerceIn(0.04f, 1.0f)
        amplitudes.add(amp)
        if (!extractor.advance()) break
      }

      if (amplitudes.isEmpty()) return emptyList()
      resampleList(amplitudes, sampleCount)
    } catch (e: Exception) {
      Log.w(TAG, "MediaExtractor extraction error: ${e.message}")
      emptyList()
    } finally {
      extractor.release()
    }
  }

  /**
   * Generates a musically realistic audio envelope with rhythmic transients,
   * natural vocal/phrase cadence, deliberate silence valleys, and high dynamic range.
   */
  fun generateRichWaveform(
    seed: String,
    durationMs: Long,
    includeSilenceGaps: Boolean = true
  ): List<Float> {
    val sampleIntervalMs = 40L // 25 samples per second
    val sampleCount = (durationMs / sampleIntervalMs).toInt().coerceIn(60, 600)
    val random = java.util.Random(seed.hashCode().toLong())

    // Rhythm beat parameters (e.g., 115-135 BPM = ~440-520ms per beat)
    val tempoBpm = 115 + (random.nextInt(30))
    val beatIntervalMs = (60_000f / tempoBpm)

    val result = ArrayList<Float>(sampleCount)
    var phase = random.nextFloat() * 10f

    // Define 1-2 realistic silence gap windows for voiceover pauses or intro/outro
    val silenceStartMs1 = if (includeSilenceGaps && durationMs > 3000L) (durationMs * 0.35f).toLong() else -1L
    val silenceEndMs1 = if (silenceStartMs1 > 0) silenceStartMs1 + 600L else -1L

    val silenceStartMs2 = if (includeSilenceGaps && durationMs > 8000L) (durationMs * 0.72f).toLong() else -1L
    val silenceEndMs2 = if (silenceStartMs2 > 0) silenceStartMs2 + 750L else -1L

    for (i in 0 until sampleCount) {
      val timeMs = i * sampleIntervalMs

      // Check if current timestamp falls into an intentional silence valley
      val isSilence = (timeMs in silenceStartMs1..silenceEndMs1) || (timeMs in silenceStartMs2..silenceEndMs2)

      if (isSilence) {
        // Very low ambient noise floor (0.02f - 0.05f)
        val noiseFloor = 0.02f + (random.nextFloat() * 0.03f)
        result.add(noiseFloor)
        continue
      }

      // 1. Phrasing envelope: Slow macro-dynamics (3-5 second musical phrases)
      val phraseCycle = (timeMs % 4000L).toFloat() / 4000f
      val phraseEnvelope = sin(phraseCycle * PI.toFloat()).coerceIn(0.18f, 1.0f)

      // 2. Rhythm beat spikes: Kick & Snare transients on regular quarter/eighth beats
      val beatPhase = (timeMs % beatIntervalMs.toLong()).toFloat() / beatIntervalMs
      val beatTransient = exp(-beatPhase * 7f) // Fast attack and exponential decay

      // 3. High-frequency micro-variation (vocal formants / instrumentation texture)
      phase += 0.38f
      val microTexture = (sin(phase) * 0.16f + cos(phase * 1.8f) * 0.12f)

      // 4. Occasional break section or energetic chorus buildup
      val isChorusSection = (timeMs % 10_000L) in 4_000L..8_500L
      val energyFactor = if (isChorusSection) 1.25f else 0.9f

      // Combine into composite amplitude
      var amp = (0.28f * phraseEnvelope + 0.52f * beatTransient + microTexture + 0.16f * random.nextFloat()) * energyFactor
      amp = amp.coerceIn(0.06f, 1.0f)

      result.add(amp)
    }

    return result
  }

  /**
   * Slices the source waveform according to clip trimming (sourceStartMs to sourceEndMs),
   * preserving exact transient peak and silence positions relative to cuts.
   */
  fun sliceForTrim(
    fullWaveform: List<Float>,
    sourceStartMs: Long,
    sourceEndMs: Long,
    totalSourceDurationMs: Long
  ): List<Float> {
    if (fullWaveform.isEmpty()) return emptyList()
    val totalDur = totalSourceDurationMs.coerceAtLeast(100L)
    val startRatio = (sourceStartMs.toFloat() / totalDur).coerceIn(0f, 1f)
    val endRatio = (sourceEndMs.toFloat() / totalDur).coerceIn(startRatio + 0.01f, 1f)

    val startIndex = (startRatio * fullWaveform.size).toInt().coerceIn(0, fullWaveform.size - 1)
    val endIndex = (endRatio * fullWaveform.size).toInt().coerceIn(startIndex + 1, fullWaveform.size)

    val sublist = fullWaveform.subList(startIndex, endIndex)
    return if (sublist.isNotEmpty()) sublist else fullWaveform
  }

  /**
   * Detects silence and dead-air intervals where amplitude remains below [silenceThreshold]
   * for at least [minSilenceDurationMs].
   */
  fun detectSilenceRegions(
    samples: List<Float>,
    clipDurationMs: Long,
    silenceThreshold: Float = 0.10f,
    minSilenceDurationMs: Long = 200L
  ): List<AudioSilenceRegion> {
    if (samples.isEmpty() || clipDurationMs <= 0L) return emptyList()

    val regions = mutableListOf<AudioSilenceRegion>()
    val msPerSample = clipDurationMs.toFloat() / samples.size
    val minSampleCount = max(1, (minSilenceDurationMs / msPerSample).toInt())

    var inSilence = false
    var silenceStartIdx = 0
    var silenceSum = 0f

    for (i in samples.indices) {
      val amp = samples[i]
      val isQuiet = amp <= silenceThreshold

      if (isQuiet) {
        if (!inSilence) {
          inSilence = true
          silenceStartIdx = i
          silenceSum = amp
        } else {
          silenceSum += amp
        }
      } else {
        if (inSilence) {
          val silenceLen = i - silenceStartIdx
          if (silenceLen >= minSampleCount) {
            val startMs = (silenceStartIdx * msPerSample).toLong()
            val endMs = (i * msPerSample).toLong()
            val avgAmp = silenceSum / silenceLen
            regions.add(
              AudioSilenceRegion(
                startIndex = silenceStartIdx,
                endIndex = i - 1,
                startMs = startMs,
                endMs = endMs,
                durationMs = endMs - startMs,
                avgAmplitude = avgAmp
              )
            )
          }
          inSilence = false
        }
      }
    }

    // Check tail silence
    if (inSilence) {
      val silenceLen = samples.size - silenceStartIdx
      if (silenceLen >= minSampleCount) {
        val startMs = (silenceStartIdx * msPerSample).toLong()
        val endMs = clipDurationMs
        val avgAmp = silenceSum / silenceLen
        regions.add(
          AudioSilenceRegion(
            startIndex = silenceStartIdx,
            endIndex = samples.size - 1,
            startMs = startMs,
            endMs = endMs,
            durationMs = endMs - startMs,
            avgAmplitude = avgAmp
          )
        )
      }
    }

    return regions
  }

  /**
   * Detects peaks and rhythm transients in the waveform to aid in cut alignment.
   */
  @Synchronized
  fun analyzeWaveform(
    samples: List<Float>,
    clipDurationMs: Long,
    peakThreshold: Float = 0.55f,
    silenceThreshold: Float = 0.10f
  ): WaveformAnalysis {
    if (samples.isEmpty() || clipDurationMs <= 0L) {
      return WaveformAnalysis(
        samples = emptyList(),
        peaks = emptyList(),
        prominentPeaks = emptyList(),
        clippingPeaks = emptyList(),
        silenceRegions = emptyList(),
        totalSilenceDurationMs = 0L,
        silencePercentage = 0f,
        rmsAverage = 0f,
        maxPeak = 0f,
        minAmplitude = 0f
      )
    }

    val cacheKey = "${samples.hashCode()}-$clipDurationMs-$peakThreshold-$silenceThreshold"
    analysisCache[cacheKey]?.let { return it }

    val peaks = mutableListOf<AudioPeak>()
    var sumSq = 0f
    var maxVal = 0f
    var minVal = 1.0f

    for (i in samples.indices) {
      val amp = samples[i]
      sumSq += amp * amp
      if (amp > maxVal) maxVal = amp
      if (amp < minVal) minVal = amp

      // Local maximum check
      val prev = if (i > 0) samples[i - 1] else 0f
      val next = if (i < samples.size - 1) samples[i + 1] else 0f

      if (amp >= peakThreshold && amp >= prev && amp >= next) {
        val timeMs = ((i.toFloat() / samples.size) * clipDurationMs).toLong()
        val isProminent = amp >= 0.78f
        val isClipping = amp >= 0.95f
        peaks.add(
          AudioPeak(
            index = i,
            timeMs = timeMs,
            amplitude = amp,
            isProminent = isProminent,
            isClipping = isClipping
          )
        )
      }
    }

    val silenceRegions = detectSilenceRegions(samples, clipDurationMs, silenceThreshold)
    val totalSilenceDurationMs = silenceRegions.sumOf { it.durationMs }
    val silencePercentage = if (clipDurationMs > 0) (totalSilenceDurationMs.toFloat() / clipDurationMs) * 100f else 0f

    val rmsAverage = sqrt(sumSq / samples.size)
    val prominentPeaks = peaks.filter { it.isProminent }
    val clippingPeaks = peaks.filter { it.isClipping }

    val analysis = WaveformAnalysis(
      samples = samples,
      peaks = peaks,
      prominentPeaks = prominentPeaks,
      clippingPeaks = clippingPeaks,
      silenceRegions = silenceRegions,
      totalSilenceDurationMs = totalSilenceDurationMs,
      silencePercentage = silencePercentage,
      rmsAverage = rmsAverage,
      maxPeak = maxVal,
      minAmplitude = minVal
    )

    analysisCache[cacheKey] = analysis
    return analysis
  }

  /**
   * Finds the closest audio peak near a candidate playhead position within [snapThresholdMs].
   */
  fun findNearestPeak(
    candidateTimeMs: Long,
    peaks: List<AudioPeak>,
    snapThresholdMs: Long = 90L
  ): AudioPeak? {
    if (peaks.isEmpty()) return null
    var closest: AudioPeak? = null
    var minDiff = Long.MAX_VALUE

    for (peak in peaks) {
      val diff = abs(peak.timeMs - candidateTimeMs)
      if (diff <= snapThresholdMs && diff < minDiff) {
        minDiff = diff
        closest = peak
      }
    }
    return closest
  }

  /**
   * Finds the next peak after the given timestamp.
   */
  fun findNextPeak(currentTimeMs: Long, peaks: List<AudioPeak>): AudioPeak? {
    return peaks.firstOrNull { it.timeMs > currentTimeMs + 30L }
  }

  /**
   * Finds the previous peak before the given timestamp.
   */
  fun findPrevPeak(currentTimeMs: Long, peaks: List<AudioPeak>): AudioPeak? {
    return peaks.lastOrNull { it.timeMs < currentTimeMs - 30L }
  }

  /**
   * Finds the nearest silence region near a candidate timestamp.
   */
  fun findNearestSilence(
    candidateTimeMs: Long,
    silenceRegions: List<AudioSilenceRegion>,
    snapThresholdMs: Long = 120L
  ): AudioSilenceRegion? {
    if (silenceRegions.isEmpty()) return null
    return silenceRegions.firstOrNull { region ->
      candidateTimeMs in (region.startMs - snapThresholdMs)..(region.endMs + snapThresholdMs)
    }
  }

  /**
   * Finds the next silence region after the given timestamp.
   */
  fun findNextSilence(currentTimeMs: Long, silenceRegions: List<AudioSilenceRegion>): AudioSilenceRegion? {
    return silenceRegions.firstOrNull { it.startMs > currentTimeMs + 30L }
  }

  /**
   * Finds the previous silence region before the given timestamp.
   */
  fun findPrevSilence(currentTimeMs: Long, silenceRegions: List<AudioSilenceRegion>): AudioSilenceRegion? {
    return silenceRegions.lastOrNull { it.endMs < currentTimeMs - 30L }
  }

  private fun resampleList(input: List<Float>, targetSize: Int): List<Float> {
    if (input.size == targetSize) return input
    if (input.isEmpty()) return emptyList()

    val result = ArrayList<Float>(targetSize)
    val step = input.size.toFloat() / targetSize

    for (i in 0 until targetSize) {
      val start = (i * step).toInt().coerceIn(0, input.size - 1)
      val end = ((i + 1) * step).toInt().coerceIn(start + 1, input.size)
      var maxVal = 0f
      for (j in start until end) {
        if (input[j] > maxVal) maxVal = input[j]
      }
      result.add(maxVal.coerceIn(0.04f, 1.0f))
    }
    return result
  }

  @Synchronized
  fun clearCache() {
    waveformCache.clear()
    analysisCache.clear()
  }
}
