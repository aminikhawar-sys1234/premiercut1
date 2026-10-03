package com.example.engine.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.domain.model.AudioClip
import com.example.domain.model.Timeline
import com.example.domain.model.TrackType
import com.example.domain.model.VideoClip
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class DecodedPcm(
  val samples: ShortArray,
  val sampleRate: Int,
  val channels: Int
)

data class TimelineAudioFormat(
  val sampleRate: Int = 48000,
  val channelCount: Int = 2,
  val bitrate: Int = 192000
)

data class AudioTrackDescriptor(
  val uri: String,
  val title: String,
  val timelineStartMs: Long,
  val durationMs: Long,
  val sourceStartMs: Long,
  val sourceEndMs: Long,
  val speed: Float,
  val volume: Float,
  val gainDb: Float,
  val fadeInMs: Long,
  val fadeOutMs: Long,
  val isMuted: Boolean,
  val isReversed: Boolean = false,
  val keyframes: List<com.example.domain.model.ClipKeyframe> = emptyList(),
  val audioEffects: com.example.domain.model.AudioEffectsSettings = com.example.domain.model.AudioEffectsSettings()
)

class AudioExportProcessor(
    private val muxer: MediaMuxer? = null,
    private val muxerLock: Any = Any(),
    private val isMuxerStarted: () -> Boolean = { true },
    private val onTrackReady: (Int) -> Unit = {}
) {
    companion object {
        private const val TAG = "AudioExportProcessor"
        private const val TIMEOUT_US = 10_000L
    }

    private var context: Context? = null

    constructor(context: Context) : this(null, Any(), { true }, {}) {
        this.context = context
    }

    private var audioTrackIndex = -1
    private var isAudioFormatAdded = false

    var sampleRate: Int = 48000
    var channelCount: Int = 2 // Stereo
    var bitrate: Int = 192000

    private val pcmCache = mutableMapOf<String, DecodedPcm>()

    fun clearCache() {
        pcmCache.clear()
    }

    /**
     * Extracts, decodes/encodes and writes audio samples synchronized to video baseline PTS.
     */
    fun processAudioTrack(
        sourceUriPath: String,
        clipStartOffsetUs: Long,
        clipDurationUs: Long
    ): Boolean {
        val targetMuxer = muxer ?: return false
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(sourceUriPath)
            val sourceTrackIndex = selectAudioTrack(extractor)
            if (sourceTrackIndex == -1) {
                Log.w(TAG, "No audio track found in source.")
                return false
            }

            extractor.selectTrack(sourceTrackIndex)
            val inputFormat = extractor.getTrackFormat(sourceTrackIndex)

            // Dynamic Sample Rate aur Channel count (No hardcoded 44100Hz)
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            this.sampleRate = sampleRate
            this.channelCount = channelCount

            // Pass-through check ya AAC re-encoder setup
            synchronized(muxerLock) {
                audioTrackIndex = targetMuxer.addTrack(inputFormat)
                isAudioFormatAdded = true
                onTrackReady(audioTrackIndex)
            }

            // Wait until VideoExporter starts the muxer safely
            while (!isMuxerStarted()) {
                Thread.sleep(10)
            }

            // Seek extractor to clip start
            extractor.seekTo(clipStartOffsetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val maxBufferSize = if (inputFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else 64 * 1024

            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var firstSampleTimeUs = -1L
            val clipEndUs = clipStartOffsetUs + clipDurationUs

            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break // EOS
                }

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > clipEndUs) {
                    break // Clip duration exceeded
                }

                if (sampleTimeUs >= clipStartOffsetUs) {
                    if (firstSampleTimeUs == -1L) {
                        firstSampleTimeUs = sampleTimeUs
                    }

                    // ZERO-POINT PTS NORMALIZATION:
                    val normalizedPtsUs = sampleTimeUs - firstSampleTimeUs

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = normalizedPtsUs
                    bufferInfo.flags = extractor.sampleFlags

                    synchronized(muxerLock) {
                        if (isMuxerStarted()) {
                            targetMuxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                        }
                    }
                }

                extractor.advance()
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Audio processing failed: ${e.message}", e)
            return false
        } finally {
            try {
                extractor.release()
                decoder?.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }

    /**
     * Detects the native audio format (sample rate, channel count, bitrate) from input media clips.
     */
    fun detectTimelineAudioFormat(timeline: Timeline): TimelineAudioFormat {
        val ctx = context
        val candidateUris = (timeline.videoClips.filter { it.isVideo && it.hasAudio && it.uri.isNotBlank() }.map { it.uri } +
            timeline.overlayClips.filter { it.isVideo && it.hasAudio && it.uri.isNotBlank() }.map { it.uri } +
            timeline.audioClips.filter { it.uri.isNotBlank() }.map { it.uri })

        for (uriString in candidateUris) {
            if (ctx != null && !MediaRelinkManager.isRealPlayableMedia(ctx, uriString)) continue
            val extractor = MediaExtractor()
            try {
                val uri = Uri.parse(uriString)
                if (ctx != null && (uri.scheme == "content" || uri.scheme == "file")) {
                    extractor.setDataSource(ctx, uri, null)
                } else {
                    val f = File(uriString)
                    if (f.exists()) extractor.setDataSource(f.absolutePath) else extractor.setDataSource(uriString)
                }

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        val sr = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48000
                        val ch = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
                        val br = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            format.getInteger(MediaFormat.KEY_BIT_RATE).coerceIn(64000, 320000)
                        } else {
                            if (ch == 1) 128000 else 192000
                        }
                        this.sampleRate = sr.coerceIn(22050, 96000)
                        this.channelCount = ch.coerceIn(1, 2)
                        this.bitrate = br
                        Log.i(TAG, "Detected timeline audio format from $uriString: ${this.sampleRate} Hz, ${this.channelCount} ch, ${this.bitrate} bps")
                        return TimelineAudioFormat(this.sampleRate, this.channelCount, this.bitrate)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract format info for $uriString", e)
            } finally {
                try { extractor.release() } catch (ignored: Exception) {}
            }
        }
        this.sampleRate = 48000
        this.channelCount = 2
        this.bitrate = 192000
        return TimelineAudioFormat(48000, 2, 192000)
    }

    fun hasActiveAudio(timeline: Timeline): Boolean {
        val anySolo = timeline.trackSettings.values.any { it.isSolo }
        val videoAudible = (timeline.trackSettings[TrackType.MAIN_VIDEO]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.MAIN_VIDEO]?.isSolo == true)
        val overlayAudible = (timeline.trackSettings[TrackType.OVERLAY]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.OVERLAY]?.isSolo == true)
        val audioAudible = (timeline.trackSettings[TrackType.AUDIO]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.AUDIO]?.isSolo == true)

        val hasAudibleVideoClips = videoAudible && timeline.videoClips.any { it.isVideo && it.hasAudio && it.volume > 0f }
        val hasAudibleOverlays = overlayAudible && timeline.overlayClips.any { it.isVideo && it.hasAudio && it.volume > 0f }
        val hasAudibleAudioClips = audioAudible && timeline.audioClips.any { it.volume > 0f }

        return hasAudibleVideoClips || hasAudibleOverlays || hasAudibleAudioClips
    }

    suspend fun mixTimelineAudio(
        timeline: Timeline,
        totalDurationMs: Long,
        targetSampleRate: Int = 48000,
        targetChannelCount: Int = 2,
        isCancelled: () -> Boolean = { false }
    ): ShortArray = withContext(Dispatchers.IO) {
        this@AudioExportProcessor.sampleRate = targetSampleRate
        this@AudioExportProcessor.channelCount = targetChannelCount

        val totalFrames = ((totalDurationMs / 1000.0) * targetSampleRate).toInt()
        if (totalFrames <= 0) return@withContext ShortArray(0)

        val mixedPcm = FloatArray(totalFrames * targetChannelCount)

        val tracks = collectAudioTracks(timeline)
        for (track in tracks) {
            if (isCancelled()) return@withContext ShortArray(0)
            val decoded = decodeOrSynthesizeTrack(track)
            mixTrackIntoBuffer(decoded, track, mixedPcm, totalFrames, targetSampleRate, targetChannelCount)
        }

        val finalPcm = ShortArray(mixedPcm.size)
        for (i in mixedPcm.indices) {
            finalPcm[i] = softClipSample(mixedPcm[i])
        }
        finalPcm
    }

    suspend fun renderMixedAudioToMuxer(
        timeline: Timeline,
        totalDurationMs: Long,
        audioEncoder: MediaCodec,
        muxer: MediaMuxer,
        muxerLock: Any,
        getAudioTrackIndex: () -> Int,
        setAudioTrackIndex: (Int) -> Unit,
        isMuxerStarted: () -> Boolean,
        onMuxerReadyCheck: () -> Unit,
        targetSampleRate: Int = 48000,
        targetChannelCount: Int = 2
    ): Boolean = withContext(Dispatchers.IO) {
        this@AudioExportProcessor.sampleRate = targetSampleRate
        this@AudioExportProcessor.channelCount = targetChannelCount

        val totalFrames = ((totalDurationMs / 1000.0) * targetSampleRate).toInt()
        if (totalFrames <= 0) return@withContext false

        val mixedPcm = FloatArray(totalFrames * targetChannelCount)

        val tracks = collectAudioTracks(timeline)
        for (track in tracks) {
            val decoded = decodeOrSynthesizeTrack(track)
            mixTrackIntoBuffer(decoded, track, mixedPcm, totalFrames, targetSampleRate, targetChannelCount)
        }

        val finalPcm = ShortArray(mixedPcm.size)
        for (i in mixedPcm.indices) {
            finalPcm[i] = softClipSample(mixedPcm[i])
        }

        encodeAndMuxPcm(finalPcm, targetSampleRate, targetChannelCount, audioEncoder, muxer, muxerLock, getAudioTrackIndex, setAudioTrackIndex, isMuxerStarted, onMuxerReadyCheck)
        true
    }

    private fun collectAudioTracks(timeline: Timeline): List<AudioTrackDescriptor> {
        val list = mutableListOf<AudioTrackDescriptor>()
        val anySolo = timeline.trackSettings.values.any { it.isSolo }
        val videoAudible = (timeline.trackSettings[TrackType.MAIN_VIDEO]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.MAIN_VIDEO]?.isSolo == true)
        val overlayAudible = (timeline.trackSettings[TrackType.OVERLAY]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.OVERLAY]?.isSolo == true)
        val audioAudible = (timeline.trackSettings[TrackType.AUDIO]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.AUDIO]?.isSolo == true)

        if (videoAudible) {
            timeline.videoClips.filter { it.isVideo && it.hasAudio && it.volume > 0f }.forEach {
                list.add(AudioTrackDescriptor(
                    uri = it.uri,
                    title = it.name,
                    timelineStartMs = it.timelineStartMs,
                    durationMs = it.durationMs,
                    sourceStartMs = it.sourceStartMs,
                    sourceEndMs = it.sourceEndMs,
                    speed = it.speed,
                    volume = it.volume,
                    gainDb = 0f,
                    fadeInMs = 0L,
                    fadeOutMs = 0L,
                    isMuted = it.isMuted,
                    isReversed = it.isReversed,
                    keyframes = it.keyframes,
                    audioEffects = it.audioEffects
                ))
            }
        }
        if (overlayAudible) {
            timeline.overlayClips.filter { it.isVideo && it.hasAudio && it.volume > 0f }.forEach {
                list.add(AudioTrackDescriptor(
                    uri = it.uri,
                    title = it.name,
                    timelineStartMs = it.timelineStartMs,
                    durationMs = it.durationMs,
                    sourceStartMs = it.sourceStartMs,
                    sourceEndMs = it.sourceEndMs,
                    speed = it.speed,
                    volume = it.volume,
                    gainDb = 0f,
                    fadeInMs = 0L,
                    fadeOutMs = 0L,
                    isMuted = it.isMuted,
                    isReversed = it.isReversed,
                    keyframes = it.keyframes,
                    audioEffects = it.audioEffects
                ))
            }
        }
        if (audioAudible) {
            timeline.audioClips.filter { it.volume > 0f }.forEach {
                list.add(AudioTrackDescriptor(
                    uri = it.uri,
                    title = it.title,
                    timelineStartMs = it.timelineStartMs,
                    durationMs = it.durationMs,
                    sourceStartMs = it.sourceStartMs,
                    sourceEndMs = it.sourceEndMs,
                    speed = it.speed,
                    volume = it.volume,
                    gainDb = it.gainDb,
                    fadeInMs = it.fadeInMs,
                    fadeOutMs = it.fadeOutMs,
                    isMuted = it.isMuted,
                    isReversed = it.isReversed,
                    keyframes = it.keyframes,
                    audioEffects = it.audioEffects
                ))
            }
        }
        return list
    }

    private fun decodeOrSynthesizeTrack(track: AudioTrackDescriptor): DecodedPcm {
        val fx = track.audioEffects
        val cacheKey = "${track.uri}_${track.speed}_${track.isReversed}_${fx.voiceEffect}_${fx.pitchShiftSemitones}_${fx.noiseReductionDb}_${fx.lowGainDb}_${fx.midGainDb}_${fx.highGainDb}_${fx.normalizeVolume}"
        pcmCache[cacheKey]?.let { return it }

        var decoded = if (track.uri.startsWith("built_in_sfx_") || track.uri.isBlank() || context == null) {
            synthesizePcmForTrack(track)
        } else {
            decodePcmFromMedia(track.uri) ?: synthesizePcmForTrack(track)
        }

        // Apply audio reversal if enabled
        if (track.isReversed && decoded.samples.isNotEmpty()) {
            val channels = decoded.channels.coerceAtLeast(1)
            val frames = decoded.samples.size / channels
            val revSamples = ShortArray(decoded.samples.size)
            for (f in 0 until frames) {
                val srcF = frames - 1 - f
                for (ch in 0 until channels) {
                    revSamples[f * channels + ch] = decoded.samples[srcF * channels + ch]
                }
            }
            decoded = decoded.copy(samples = revSamples)
        }

        // Apply real DSP Audio Effects (Noise Gate, Equalizer, Voice Effects, Pitch Shift, Volume Normalization)
        if (fx.voiceEffect != com.example.domain.model.VoiceEffect.NONE ||
            fx.pitchShiftSemitones != 0f ||
            fx.noiseReductionDb > 0f ||
            fx.lowGainDb != 0f ||
            fx.midGainDb != 0f ||
            fx.highGainDb != 0f ||
            fx.normalizeVolume
        ) {
            val processed = com.example.engine.audio.AdvancedAudioProcessor.processPcmBuffer(
                pcm = decoded.samples,
                sampleRate = decoded.sampleRate,
                channels = decoded.channels,
                effects = fx
            )
            decoded = decoded.copy(samples = processed)
        }

        pcmCache[cacheKey] = decoded
        return decoded
    }

    private fun decodePcmFromMedia(uriString: String): DecodedPcm? {
        val ctx = context ?: return null
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content" || uri.scheme == "file") {
                extractor.setDataSource(ctx, uri, null)
            } else {
                val f = File(uriString)
                if (f.exists()) extractor.setDataSource(f.absolutePath) else extractor.setDataSource(uriString)
            }

            val audioTrack = selectAudioTrack(extractor)
            if (audioTrack == -1) return null

            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmBytes = mutableListOf<Byte>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false

            while (!isEos) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inputBuf = decoder.getInputBuffer(inIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outIndex >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.get(chunk)
                        pcmBytes.addAll(chunk.toList())
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    outIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            val byteArr = pcmBytes.toByteArray()
            val shortBuf = ByteBuffer.wrap(byteArr).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val shorts = ShortArray(shortBuf.remaining())
            shortBuf.get(shorts)

            return DecodedPcm(shorts, srcSampleRate, srcChannels)
        } catch (e: Exception) {
            Log.e(TAG, "Failed decoding PCM from $uriString: ${e.message}")
            return null
        } finally {
            try { decoder?.stop() } catch (ignored: Exception) {}
            try { decoder?.release() } catch (ignored: Exception) {}
            try { extractor.release() } catch (ignored: Exception) {}
        }
    }

    private fun mixTrackIntoBuffer(
        decoded: DecodedPcm,
        track: AudioTrackDescriptor,
        outputMix: FloatArray,
        totalFrames: Int,
        targetSampleRate: Int,
        targetChannels: Int
    ) {
        val startFrame = ((track.timelineStartMs / 1000.0) * targetSampleRate).toInt()
        val durationFrames = ((track.durationMs / 1000.0) * targetSampleRate).toInt()
        val endFrame = (startFrame + durationFrames).coerceAtMost(totalFrames)

        val srcSamples = decoded.samples
        val srcRate = decoded.sampleRate
        val srcChannels = decoded.channels

        val speed = track.speed.coerceIn(0.25f, 4.0f)
        val rateRatio = (srcRate.toDouble() / targetSampleRate.toDouble()) * speed

        val linearGain = track.volume * 10f.pow(track.gainDb / 20f)
        val fadeInFrames = ((track.fadeInMs / 1000.0) * targetSampleRate).toInt()
        val fadeOutFrames = ((track.fadeOutMs / 1000.0) * targetSampleRate).toInt()

        for (frame in startFrame until endFrame) {
            val relativeFrame = frame - startFrame
            val srcIndex = (relativeFrame * rateRatio).toInt()

            var fadeVol = 1.0f
            if (fadeInFrames > 0 && relativeFrame < fadeInFrames) {
                fadeVol *= (relativeFrame.toFloat() / fadeInFrames)
            }
            if (fadeOutFrames > 0 && (durationFrames - relativeFrame) < fadeOutFrames) {
                fadeVol *= ((durationFrames - relativeFrame).toFloat() / fadeOutFrames).coerceIn(0f, 1f)
            }

            val gain = linearGain * fadeVol

            for (ch in 0 until targetChannels) {
                val sampleVal: Short = if (srcChannels == 1) {
                    if (srcIndex < srcSamples.size) srcSamples[srcIndex] else 0
                } else {
                    val sIdx = srcIndex * 2 + (ch % 2)
                    if (sIdx < srcSamples.size) srcSamples[sIdx] else 0
                }
                val outIdx = frame * targetChannels + ch
                if (outIdx < outputMix.size) {
                    outputMix[outIdx] += (sampleVal * gain)
                }
            }
        }
    }

    private fun encodeAndMuxPcm(
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        muxerLock: Any,
        getAudioTrackIndex: () -> Int,
        setAudioTrackIndex: (Int) -> Unit,
        isMuxerStarted: () -> Boolean,
        onMuxerReadyCheck: () -> Unit
    ) {
        val byteBuf = ByteBuffer.allocateDirect(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuf = byteBuf.asShortBuffer()
        shortBuf.put(pcm)
        byteBuf.position(0)

        val bufferInfo = MediaCodec.BufferInfo()
        var inputOffset = 0
        val totalBytes = pcm.size * 2
        var isEos = false

        var presentationTimeUs = 0L
        val bytesPerFrame = channels * 2
        val usPerFrame = 1_000_000.0 / sampleRate

        while (!isEos) {
            val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                val inBuf = encoder.getInputBuffer(inIndex)
                if (inBuf != null) {
                    inBuf.clear()
                    val bytesToCopy = inBuf.remaining().coerceAtMost(totalBytes - inputOffset)
                    if (bytesToCopy > 0) {
                        byteBuf.position(inputOffset)
                        byteBuf.limit(inputOffset + bytesToCopy)
                        inBuf.put(byteBuf)
                        inputOffset += bytesToCopy

                        val framesSent = bytesToCopy / bytesPerFrame
                        encoder.queueInputBuffer(inIndex, 0, bytesToCopy, presentationTimeUs, 0)
                        presentationTimeUs += (framesSent * usPerFrame).toLong()
                    } else {
                        encoder.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    }
                }
            }

            var outIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            while (outIndex >= 0) {
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized(muxerLock) {
                        val trackIdx = muxer.addTrack(encoder.outputFormat)
                        setAudioTrackIndex(trackIdx)
                        onMuxerReadyCheck()
                    }
                } else if (outIndex >= 0) {
                    val encoded = encoder.getOutputBuffer(outIndex)
                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (encoded != null && bufferInfo.size > 0) {
                        while (!isMuxerStarted()) {
                            Thread.sleep(5)
                        }
                        synchronized(muxerLock) {
                            val trackIdx = getAudioTrackIndex()
                            if (trackIdx >= 0 && isMuxerStarted()) {
                                try {
                                    muxer.writeSampleData(trackIdx, encoded, bufferInfo)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Safe catch during audio muxer writeSampleData: ${e.message}")
                                }
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (isEos) break
                }
                outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }
    }

    private fun synthesizePcmForTrack(track: AudioTrackDescriptor): DecodedPcm {
        val durationSec = (track.durationMs / 1000.0).coerceIn(0.3, 120.0)
        val numFrames = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numFrames * 2)

        val lower = (track.uri + " " + track.title).lowercase()
        when {
            lower.contains("whoosh") -> {
                for (i in 0 until numFrames) {
                    val t = i.toDouble() / sampleRate
                    val sweep = (1.0 - (i.toDouble() / numFrames)).coerceIn(0.0, 1.0)
                    val freq = 120.0 + sweep * 400.0
                    val env = sin(Math.PI * (i.toDouble() / numFrames))
                    val sample = (sin(2.0 * Math.PI * freq * t) * env * 28000.0).toInt().toShort()
                    pcm[i * 2] = sample
                    pcm[i * 2 + 1] = sample
                }
            }
            lower.contains("pop") -> {
                for (i in 0 until numFrames) {
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 24.0)
                    val sample = (sin(2.0 * Math.PI * 650.0 * t) * env * 30000.0).toInt().toShort()
                    pcm[i * 2] = sample
                    pcm[i * 2 + 1] = sample
                }
            }
            lower.contains("ding") || lower.contains("bell") -> {
                for (i in 0 until numFrames) {
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 3.5)
                    val wave = sin(2.0 * Math.PI * 1200.0 * t) * 0.7 + sin(2.0 * Math.PI * 2400.0 * t) * 0.3
                    val sample = (wave * env * 26000.0).toInt().toShort()
                    pcm[i * 2] = sample
                    pcm[i * 2 + 1] = sample
                }
            }
            lower.contains("bass") -> {
                for (i in 0 until numFrames) {
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 2.0)
                    val sample = (sin(2.0 * Math.PI * 85.0 * t) * env * 32000.0).toInt().toShort()
                    pcm[i * 2] = sample
                    pcm[i * 2 + 1] = sample
                }
            }
            else -> {
                val chordFreqs = listOf(220.0, 261.63, 329.63, 392.0) // Am7
                for (i in 0 until numFrames) {
                    val t = i.toDouble() / sampleRate
                    val beat = if ((t % 0.5) < 0.08) 0.6 else 0.0
                    val chord = chordFreqs.indices.sumOf { idx ->
                        sin(2.0 * Math.PI * chordFreqs[idx] * t) * (0.2 / (idx + 1))
                    }
                    val wave = (chord + beat * sin(2.0 * Math.PI * 90.0 * t)).coerceIn(-1.0, 1.0)
                    val sample = (wave * 20000.0).toInt().toShort()
                    pcm[i * 2] = sample
                    pcm[i * 2 + 1] = sample
                }
            }
        }

        return DecodedPcm(pcm, sampleRate, 2)
    }

    private fun softClipSample(sample: Float): Short {
        val norm = sample / 32768f
        val clipped = when {
            norm > 1.0f -> 1.0f
            norm < -1.0f -> -1.0f
            norm > 0.75f -> 0.75f + (norm - 0.75f) * 0.5f
            norm < -0.75f -> -0.75f + (norm + 0.75f) * 0.5f
            else -> norm
        }
        return (clipped * 32767f).toInt().toShort()
    }
}
