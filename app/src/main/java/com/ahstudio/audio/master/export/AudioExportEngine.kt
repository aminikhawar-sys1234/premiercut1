package com.ahstudio.audio.master.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.ahstudio.audio.master.cache.DecodedAudioCache
import com.ahstudio.audio.master.clips.AudioClipReader
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.metering.LoudnessMeter
import com.ahstudio.audio.master.mixer.MasterAudioMixer
import com.ahstudio.audio.master.model.MasterAudioProject
import com.ahstudio.audio.master.recording.WavWriter
import com.ahstudio.audio.master.timeline.AudioTimelineValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

enum class AudioExportFormat { WAV_16, WAV_FLOAT, AAC_M4A, PCM_SINK }

data class AudioExportConfig(
    val format: AudioExportFormat = AudioExportFormat.AAC_M4A,
    val outFile: File,
    val sampleRate: Int = 48_000,
    val channels: Int = 2,
    val bitrate: Int = 192_000,
    val startSec: Double = 0.0,
    val endSec: Double = -1.0,
    val blockSize: Int = 4096,
    val normalizeTargetLufs: Double? = null,
) {
    // Backwards compatibility alias
    val outputFile: File get() = outFile
}

data class AudioExportResult(
    val file: File?,
    val renderedSec: Double,
    val peakLinear: Float,
    val loudnessLufs: Double,
    val clippedSamples: Long
)

data class AudioExportProgress(val fraction: Float, val atSec: Double)

/** Streaming destination for rendered interleaved float PCM. */
interface PcmSink {
    fun prepare()
    fun write(interleaved: FloatArray, frames: Int)
    fun finish()
    fun release()
}

class WavFileSink(file: File, sampleRate: Int, channels: Int, floatPcm: Boolean) : PcmSink {
    private val writer = WavWriter(file, sampleRate, channels, floatPcm)
    override fun prepare() {}
    override fun write(interleaved: FloatArray, frames: Int) = writer.write(interleaved, frames)
    override fun finish() = writer.close()
    override fun release() { runCatching { writer.close() } }
}

/** AAC-LC → MP4 via MediaCodec + MediaMuxer. Real encoding, correct PTS from sample counter. */
class AacM4aWriter(file: File, private val sampleRate: Int, private val channels: Int, bitrateBps: Int = 192_000) : PcmSink {
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var sampleIndex = 0L

    init {
        val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        f.setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
        codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    override fun prepare() {}
    override fun release() { runCatching { codec.stop() }; runCatching { codec.release() }; runCatching { muxer.release() } }

    override fun write(interleaved: FloatArray, frames: Int) {
        var offset = 0; var remaining = frames * channels
        while (remaining > 0) {
            val inIdx = codec.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val ib = codec.getInputBuffer(inIdx)!!
                ib.clear()
                val cap = ib.remaining() / 2
                val n = minOf(remaining, cap)
                for (i in 0 until n) {
                    val v = (interleaved[offset + i].coerceIn(-1f, 1f) * 32767f).toInt()
                    ib.putShort(v.toShort())
                }
                codec.queueInputBuffer(inIdx, 0, n * 2, sampleIndex * 1_000_000L / sampleRate, 0)
                sampleIndex += n / channels
                offset += n; remaining -= n
            }
            drain(eos = false)
        }
        drain(eos = false)
    }

    override fun finish() {
        var tries = 0
        while (tries < 50) {
            val inIdx = codec.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                codec.queueInputBuffer(inIdx, 0, 0, sampleIndex * 1_000_000L / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            }
            tries++
        }
        drain(eos = true)
        runCatching { codec.stop() }; codec.release()
        if (muxerStarted) { muxer.stop() }
        muxer.release()
    }

    private fun drain(eos: Boolean) {
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, if (eos) 10_000 else 0)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start(); muxerStarted = true
            } else if (outIdx >= 0) {
                val ob = codec.getOutputBuffer(outIdx)!!
                if (bufferInfo.size > 0 && muxerStarted && trackIndex >= 0) {
                    ob.position(bufferInfo.offset); ob.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, ob, bufferInfo)
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            } else break
        }
    }
}

object AudioMuxerAdapter {
    /** [WIRE] Host video exporter: muxer.addTrack(createAudioMediaFormat(...)) → track index. */
    fun createAudioMediaFormat(sampleRate: Int, channels: Int, bitrate: Int = 192_000): MediaFormat {
        val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        f.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
        return f
    }
}

/** [WIRE] Sink onto the host video-export MediaMuxer so audio muxes with video in ONE file. */
class MediaMuxerAudioSinkAdapter(
    private val muxer: MediaMuxer,
    val trackIndex: Int,
    private val sampleRate: Int,
) : PcmSink {
    private val bufferInfo = MediaCodec.BufferInfo()
    private var sampleIndex = 0L
    override fun prepare() {}
    override fun write(interleaved: FloatArray, frames: Int) {
        val ch = if (frames > 0) interleaved.size / frames else 1
        val bb = ByteBuffer.allocateDirect(frames * ch * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in interleaved) bb.putShort(((v.coerceIn(-1f, 1f) * 32767f).toInt()).toShort())
        bb.flip()
        bufferInfo.set(0, bb.remaining(), sampleIndex * 1_000_000L / sampleRate, 0)
        muxer.writeSampleData(trackIndex, bb, bufferInfo)
        sampleIndex += frames
    }
    override fun finish() {}
    override fun release() {}
}

object AudioExportValidator {
    fun validate(project: MasterAudioProject, config: AudioExportConfig): List<String> {
        val issues = mutableListOf<String>()
        if (config.endSec in 0.0..config.startSec) issues.add("endSec (${config.endSec}) must be after startSec (${config.startSec})")
        if (config.outFile.parentFile?.exists() != true && config.outFile.parentFile != null) issues.add("output directory does not exist")
        val (fatal, _) = AudioTimelineValidator.validate(project)
        issues.addAll(fatal)
        return issues
    }
}

/** Offline render pipeline: dedicated mixer (preview state untouched), block-by-block PCM out. */
class AudioPcmRenderer(
    private val cache: DecodedAudioCache,
    private val format: AudioFormat,
    private val blockSize: Int = 4096,
) {
    fun render(
        project: MasterAudioProject,
        startSec: Double, endSec: Double,
        onBlock: (interleaved: FloatArray, frames: Int, blockStartSec: Double) -> Boolean, // false = cancel
    ): AudioExportResult {
        val mixer = MasterAudioMixer(blockSize)
        mixer.setProject(project, format)
        val reader = AudioClipReader(cache)
        val interleaved = FloatArray(blockSize * format.channels)
        val loudness = LoudnessMeter().apply { configure(format.sampleRate, format.channels) }
        var peak = 0f; var rendered = 0.0; var t = startSec
        while (t < endSec - 1e-9) {
            val frames = minOf(blockSize, ((endSec - t) * format.sampleRate).toInt().coerceAtLeast(1))
            val ctx = AudioRenderContext(format, t, frames, realtime = false)
            val buf = mixer.renderBlock(ctx, reader)
            buf.interleave(interleaved, frames)
            for (i in 0 until frames * format.channels) { val v = abs(interleaved[i]); if (v > peak) peak = v }
            loudness.process(buf, frames)
            if (!onBlock(interleaved, frames, t))
                return AudioExportResult(null, rendered, peak, loudness.integratedLufs(), mixer.clippingGuard.clippedSamples)
            t += frames / format.sampleRate.toDouble()
            rendered += frames / format.sampleRate.toDouble()
        }
        return AudioExportResult(null, rendered, peak, loudness.integratedLufs(), mixer.clippingGuard.clippedSamples)
    }
}

class AudioExportEngine(private val cache: DecodedAudioCache, private val format: AudioFormat) {
    suspend fun export(
        project: MasterAudioProject,
        config: AudioExportConfig,
        progress: ((AudioExportProgress) -> Unit)? = null,
    ): AudioExportResult = withContext(Dispatchers.Default) {
        require(config.sampleRate == format.sampleRate) { "export sampleRate must match engine (${format.sampleRate})" }
        require(config.channels == format.channels) { "export channels must match engine (${format.channels})" }
        val issues = AudioExportValidator.validate(project, config)
        check(issues.isEmpty()) { issues.joinToString("; ") }
        val end = if (config.endSec < 0) project.totalDurationSec() else config.endSec
        val job = coroutineContext[Job]

        var effective = project
        if (config.normalizeTargetLufs != null) {
            val measure = AudioPcmRenderer(cache, format, config.blockSize).render(project, config.startSec, end) { _, _, _ -> true }
            val delta = config.normalizeTargetLufs - measure.loudnessLufs
            effective = project.copy(mix = project.mix.copy(masterGainDb = project.mix.masterGainDb + delta.toFloat()))
        }

        val sink: PcmSink = when (config.format) {
            AudioExportFormat.WAV_16 -> WavFileSink(config.outFile, config.sampleRate, config.channels, floatPcm = false)
            AudioExportFormat.WAV_FLOAT -> WavFileSink(config.outFile, config.sampleRate, config.channels, floatPcm = true)
            AudioExportFormat.AAC_M4A -> AacM4aWriter(config.outFile, config.sampleRate, config.channels)
            AudioExportFormat.PCM_SINK -> throw IllegalArgumentException("PCM_SINK requires renderToPcmSink API")
        }
        sink.prepare()
        val totalSec = (end - config.startSec).coerceAtLeast(1e-6)
        val result = AudioPcmRenderer(cache, format, config.blockSize).render(effective, config.startSec, end) { inter, frames, t ->
            sink.write(inter, frames)
            progress?.invoke(AudioExportProgress(((t - config.startSec) / totalSec).toFloat().coerceIn(0f, 1f), t))
            job?.isActive != false
        }
        sink.finish()
        result.copy(file = config.outFile)
    }
}
