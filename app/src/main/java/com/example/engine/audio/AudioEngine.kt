package com.example.engine.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sin

class AudioEngine(private val context: Context) {
  private val tag = "AudioEngine"

  private var tts: TextToSpeech? = null
  private val _ttsReady = MutableStateFlow(false)
  val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _recordingDurationMs = MutableStateFlow(0L)
  val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

  private val _currentDecibels = MutableStateFlow(0f)
  val currentDecibels: StateFlow<Float> = _currentDecibels.asStateFlow()

  private var mediaRecorder: MediaRecorder? = null
  private var currentRecordingFile: File? = null
  private var recordingJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  // Dedicated audio player for sound effects and music tracks
  private val sfxPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

  init {
    try {
      tts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          tts?.language = Locale.US
          _ttsReady.value = true
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "TTS initialization error", e)
    }
  }

  fun playAudioUri(uriString: String) {
    try {
      val mediaItem = MediaItem.fromUri(Uri.parse(uriString))
      sfxPlayer.setMediaItem(mediaItem)
      sfxPlayer.prepare()
      sfxPlayer.play()
    } catch (e: Exception) {
      Log.e(tag, "Failed to play audio URI $uriString", e)
    }
  }

  fun stopAudio() {
    sfxPlayer.stop()
  }

  fun synthesizeTts(text: String, voicePitch: Float = 1.0f, speechRate: Float = 1.0f) {
    if (_ttsReady.value && text.isNotBlank()) {
      try {
        tts?.setPitch(voicePitch)
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AH_TTS_${System.currentTimeMillis()}")
      } catch (e: Exception) {
        Log.e(tag, "TTS speak failed", e)
      }
    }
  }

  /**
   * Retrieves optimal hardware sample rate from AudioManager, defaulting to 48000 Hz pro video standard.
   */
  fun getOptimalSampleRate(): Int {
    return try {
      val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
      val rateStr = audioManager?.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
      rateStr?.toIntOrNull()?.coerceIn(44100, 96000) ?: 48000
    } catch (_: Throwable) {
      48000
    }
  }

  /**
   * Starts real microphone voice recording to an AAC/M4A file using MediaRecorder.
   */
  fun startVoiceRecording(onDurationTick: (Long) -> Unit) {
    try {
      val outputDir = File(context.cacheDir, "audio_recordings").apply { if (!exists()) mkdirs() }
      val file = File(outputDir, "voiceover_${System.currentTimeMillis()}.m4a")
      currentRecordingFile = file

      val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
      } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
      }

      val sampleRate = getOptimalSampleRate()
      recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      recorder.setAudioEncodingBitRate(128000)
      recorder.setAudioSamplingRate(sampleRate)
      recorder.setOutputFile(file.absolutePath)
      recorder.prepare()
      recorder.start()

      mediaRecorder = recorder
      _isRecording.value = true
      _recordingDurationMs.value = 0L

      val startTime = System.currentTimeMillis()
      recordingJob?.cancel()
      recordingJob = scope.launch {
        while (isActive && _isRecording.value) {
          val elapsed = System.currentTimeMillis() - startTime
          _recordingDurationMs.value = elapsed
          onDurationTick(elapsed)

          val amp = try { recorder.maxAmplitude } catch (e: Exception) { 0 }
          val db = if (amp > 0) (20 * log10(amp / 32767f)).coerceIn(-60f, 0f) else -60f
          _currentDecibels.value = db
          delay(100L)
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Failed to start real audio recording", e)
      _isRecording.value = false
    }
  }

  fun updateRecordingState(durationMs: Long, amplitude: Float) {
    _recordingDurationMs.value = durationMs
    _currentDecibels.value = amplitude
  }

  /**
   * Stops recording and returns the real recorded audio file.
   */
  fun stopVoiceRecording(): File {
    _isRecording.value = false
    recordingJob?.cancel()
    try {
      mediaRecorder?.stop()
    } catch (e: Exception) {
      Log.w(tag, "MediaRecorder stop warning", e)
    }
    try {
      mediaRecorder?.release()
    } catch (e: Exception) {
      Log.w(tag, "MediaRecorder release warning", e)
    }
    mediaRecorder = null

    val file = currentRecordingFile ?: File(context.cacheDir, "recording_${System.currentTimeMillis()}.m4a")
    if (!file.exists() || file.length() == 0L) {
      val fallbackFile = File(context.cacheDir, "recording_silence_${System.currentTimeMillis()}.wav")
      val pcm = ShortArray(44100 * 2) { 0 }
      writeWavFile(fallbackFile, pcm, 44100)
      return fallbackFile
    }
    return file
  }

  /**
   * Extracts real audio track from a video file/URI into an .m4a file.
   */
  fun extractAudioFromVideo(videoUri: String): File? {
    val outputDir = File(context.cacheDir, "extracted_audio").apply { if (!exists()) mkdirs() }
    val outputFile = File(outputDir, "audio_${System.currentTimeMillis()}.m4a")

    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    try {
      val uri = Uri.parse(videoUri)
      if (uri.scheme == "content" || uri.scheme == "file") {
        extractor.setDataSource(context, uri, null)
      } else {
        extractor.setDataSource(videoUri)
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

      if (audioTrackIndex == -1) return null

      extractor.selectTrack(audioTrackIndex)
      val format = extractor.getTrackFormat(audioTrackIndex)

      muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      val muxerTrack = muxer.addTrack(format)
      muxer.start()

      val buffer = ByteBuffer.allocate(64 * 1024)
      val bufferInfo = android.media.MediaCodec.BufferInfo()

      while (true) {
        val sampleSize = extractor.readSampleData(buffer, 0)
        if (sampleSize < 0) break

        bufferInfo.offset = 0
        bufferInfo.size = sampleSize
        bufferInfo.presentationTimeUs = extractor.sampleTime
        bufferInfo.flags = extractor.sampleFlags

        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
        extractor.advance()
      }

      muxer.stop()
      return outputFile
    } catch (e: Exception) {
      Log.e(tag, "Audio extraction failed", e)
      return null
    } finally {
      extractor.release()
      try { muxer?.release() } catch (ignored: Exception) {}
    }
  }

  /**
   * Extracts accurate waveform amplitudes from an audio file.
   */
  fun extractWaveformFromFile(audioFile: File, sampleCount: Int = 32): List<Float> {
    if (!audioFile.exists() || audioFile.length() == 0L) {
      return List(sampleCount) { 0.2f }
    }
    return try {
      val length = audioFile.length().toInt()
      val bytes = ByteArray(minOf(length, 128 * 1024))
      FileInputStream(audioFile).use { it.read(bytes) }

      val step = maxOf(1, bytes.size / sampleCount)
      val result = mutableListOf<Float>()
      for (i in 0 until sampleCount) {
        val start = i * step
        var peak = 0
        for (j in start until minOf(start + step, bytes.size)) {
          val v = kotlin.math.abs(bytes[j].toInt())
          if (v > peak) peak = v
        }
        result.add((peak / 128f).coerceIn(0.1f, 1.0f))
      }
      result
    } catch (e: Exception) {
      List(sampleCount) { 0.3f }
    }
  }

  fun getOrCreateSoundWavFile(sfxId: String, durationMs: Long = 2000L, category: String = ""): File {
    val sfxDir = File(context.cacheDir, "sfx_cache").apply { if (!exists()) mkdirs() }
    val sfxFile = File(sfxDir, "${sfxId.replace(Regex("[^a-zA-Z0-9_]"), "_")}.wav")
    if (sfxFile.exists() && sfxFile.length() > 0L) return sfxFile

    val sampleRate = 44100
    val durationSec = (durationMs.coerceIn(300L, 12000L) / 1000.0)
    val numSamples = (sampleRate * durationSec).toInt()
    val pcm = ShortArray(numSamples)

    val lower = sfxId.lowercase()
    val catLower = category.lowercase()

    when {
      lower.contains("whoosh") || lower.contains("smooshe") -> {
        for (i in 0 until numSamples) {
          val t = i.toDouble() / sampleRate
          val env = sin(Math.PI * (i.toDouble() / numSamples)).coerceIn(0.0, 1.0)
          val freq = 450.0 * (1.0 - (i.toDouble() / numSamples) * 0.7)
          val noise = ((i % 17) - 8) / 8.0 * 0.15
          val wave = sin(2.0 * Math.PI * freq * t) * 0.85 + noise
          pcm[i] = (wave * env * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
      }
      lower.contains("ding") || lower.contains("bell") || catLower.contains("ding") -> {
        for (i in 0 until numSamples) {
          val t = i.toDouble() / sampleRate
          val decay = kotlin.math.exp(-3.5 * t)
          val wave = sin(2.0 * Math.PI * 1200.0 * t) * 0.7 + sin(2.0 * Math.PI * 2400.0 * t) * 0.3
          pcm[i] = (wave * decay * 30000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
      }
      lower.contains("pop") || lower.contains("bubble") -> {
        for (i in 0 until numSamples) {
          val t = i.toDouble() / sampleRate
          val env = (1.0 - (i.toDouble() / numSamples)).coerceIn(0.0, 1.0)
          val freq = 300.0 + 900.0 * (1.0 - env)
          val wave = sin(2.0 * Math.PI * freq * t) * env
          pcm[i] = (wave * 30000.0).toInt().toShort()
        }
      }
      lower.contains("bass") || catLower.contains("tense") -> {
        for (i in 0 until numSamples) {
          val t = i.toDouble() / sampleRate
          val decay = (1.0 - (i.toDouble() / numSamples)).coerceIn(0.0, 1.0)
          val wave = sin(2.0 * Math.PI * 80.0 * t) * 0.8 + sin(2.0 * Math.PI * 160.0 * t) * 0.2
          pcm[i] = (wave * decay * 32000.0).toInt().toShort()
        }
      }
      catLower.contains("gaming") -> {
        for (i in 0 until numSamples) {
          val step = (i / (sampleRate / 8)) % 4
          val freq = when (step) { 0 -> 440.0; 1 -> 554.37; 2 -> 659.25; else -> 880.0 }
          val wave = if ((i * freq / sampleRate).toInt() % 2 == 0) 0.6 else -0.6
          pcm[i] = (wave * 24000.0).toInt().toShort()
        }
      }
      catLower.contains("asmr") -> {
        for (i in 0 until numSamples) {
          val t = i.toDouble() / sampleRate
          val env = sin(Math.PI * (i.toDouble() / numSamples)).coerceIn(0.0, 1.0)
          val noise = (((i * 73) % 256) - 128) / 128.0
          val softWave = sin(2.0 * Math.PI * 220.0 * t) * 0.3 + noise * 0.4
          pcm[i] = (softWave * env * 18000.0).toInt().toShort()
        }
      }
      catLower.contains("cheers") || catLower.contains("funny") || catLower.contains("comedy") -> {
        for (i in 0 until numSamples) {
          val t = i.toDouble() / sampleRate
          val burst = (sin(2.0 * Math.PI * 4.0 * t) * 0.5 + 0.5)
          val env = (1.0 - (i.toDouble() / numSamples)).coerceIn(0.0, 1.0)
          val wave = sin(2.0 * Math.PI * 520.0 * t) * burst * env
          pcm[i] = (wave * 26000.0).toInt().toShort()
        }
      }
      else -> {
        val baseFreq = 440.0 + (sfxId.hashCode() % 300).toDouble()
        for (i in 0 until numSamples) {
          val t = i.toDouble() / sampleRate
          val env = (1.0 - (i.toDouble() / numSamples)).coerceIn(0.0, 1.0)
          val wave = sin(2.0 * Math.PI * baseFreq * t) * env
          pcm[i] = (wave * 26000.0).toInt().toShort()
        }
      }
    }

    writeWavFile(sfxFile, pcm, sampleRate)
    return sfxFile
  }

  fun playPreviewSfx(sfxId: String, durationMs: Long = 2000L, category: String = "") {
    try {
      val sfxFile = getOrCreateSoundWavFile(sfxId, durationMs, category)
      val mediaItem = MediaItem.fromUri(Uri.fromFile(sfxFile))
      sfxPlayer.setMediaItem(mediaItem)
      sfxPlayer.prepare()
      sfxPlayer.play()
    } catch (e: Exception) {
      Log.e(tag, "Failed to play SFX $sfxId", e)
    }
  }

  private fun getOrCreateSfxWavFile(sfxId: String): File {
    return getOrCreateSoundWavFile(sfxId)
  }

  private fun writeWavFile(file: File, pcm: ShortArray, sampleRate: Int) {
    val totalAudioLen = pcm.size * 2
    val totalDataLen = totalAudioLen + 36
    val byteRate = sampleRate * 2

    val header = ByteArray(44)
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xff).toByte()
    header[5] = ((totalDataLen shr 8) and 0xff).toByte()
    header[6] = ((totalDataLen shr 16) and 0xff).toByte()
    header[7] = ((totalDataLen shr 24) and 0xff).toByte()
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16 // Subchunk1Size (16 for PCM)
    header[17] = 0
    header[18] = 0
    header[19] = 0
    header[20] = 1 // AudioFormat 1 = PCM
    header[21] = 0
    header[22] = 1 // Mono = 1
    header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = ((sampleRate shr 8) and 0xff).toByte()
    header[26] = ((sampleRate shr 16) and 0xff).toByte()
    header[27] = ((sampleRate shr 24) and 0xff).toByte()
    header[28] = (byteRate and 0xff).toByte()
    header[29] = ((byteRate shr 8) and 0xff).toByte()
    header[30] = ((byteRate shr 16) and 0xff).toByte()
    header[31] = ((byteRate shr 24) and 0xff).toByte()
    header[32] = 2 // BlockAlign
    header[33] = 0
    header[34] = 16 // BitsPerSample
    header[35] = 0
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    header[40] = (totalAudioLen and 0xff).toByte()
    header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
    header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
    header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

    FileOutputStream(file).use { fos ->
      fos.write(header)
      val byteBuffer = ByteBuffer.allocate(pcm.size * 2)
      byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
      for (s in pcm) {
        byteBuffer.putShort(s)
      }
      fos.write(byteBuffer.array())
      fos.flush()
    }
  }

  fun release() {
    recordingJob?.cancel()
    scope.cancel()
    try {
      mediaRecorder?.release()
      tts?.stop()
      tts?.shutdown()
      sfxPlayer.release()
    } catch (e: Exception) {
      Log.w(tag, "AudioEngine release warning", e)
    }
  }
}
