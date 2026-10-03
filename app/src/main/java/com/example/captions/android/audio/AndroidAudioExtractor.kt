package com.ahstudio.captions.android.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.ahstudio.captions.audio.AudioChunk
import com.ahstudio.captions.audio.AudioExtractor
import com.ahstudio.captions.audio.AudioInfo
import com.ahstudio.captions.audio.LinearResampler
import com.ahstudio.captions.core.errors.CaptionEngineException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.nio.ByteOrder

class AndroidAudioExtractor(private val context: Context) : AudioExtractor {

    override fun probe(uri: Uri): AudioInfo {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            var hasAudio = false
            var durationUs = -1L
            var rateHint: Int? = null
            for (t in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(t)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    if (fmt.containsKey(MediaFormat.KEY_DURATION)) durationUs = fmt.getLong(MediaFormat.KEY_DURATION)
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rateHint = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    break
                }
            }
            if (durationUs <= 0) {
                durationUs = runCatching {
                    MediaMetadataRetriever().use { r ->
                        r.setDataSource(context, uri)
                        (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
                    }
                }.getOrDefault(-1L)
            }
            return AudioInfo(hasAudioTrack = hasAudio, durationUs = durationUs, sampleRateHint = rateHint)
        } catch (e: Exception) {
            throw CaptionEngineException.AudioExtraction("cannot open media: ${e.message}", e)
        } finally {
            runCatching { ex.release() }
        }
    }

    override fun extract(uri: Uri, targetSampleRate: Int): Flow<AudioChunk> = flow {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (t in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(t)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { trackIndex = t; format = fmt; break }
            }
            if (format == null) throw CaptionEngineException.AudioExtraction("media contains no audio track")

            var outRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmFloat = runCatching {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
            }.getOrDefault(false)

            ex.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()

            val resampler = LinearResampler(outRate, targetSampleRate)
            val info = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false

            val acc = ArrayList<Float>(targetSampleRate)
            var chunkStartUs = -1L

            fun downmixToFloat(buffer: java.nio.ByteBuffer, size: Int) {
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                val frames = size / (if (pcmFloat) 4 else 2) / outChannels
                if (pcmFloat) {
                    val fb = buffer.asFloatBuffer()
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until outChannels) sum += fb.get(f * outChannels + c)
                        acc += sum / outChannels
                    }
                } else {
                    val sb = buffer.asShortBuffer()
                    for (f in 0 until frames) {
                        var sum = 0
                        for (c in 0 until outChannels) sum += sb.get(f * outChannels + c)
                        acc += sum / outChannels / 32768f
                    }
                }
            }

            while (!outputEos) {
                currentCoroutineContext().ensureActive()

                if (!inputEos) {
                    val ib = dec.dequeueInputBuffer(10_000)
                    if (ib >= 0) {
                        val buf = dec.getInputBuffer(ib)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            dec.queueInputBuffer(ib, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }

                when (val ob = dec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = dec.outputFormat
                        outRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmFloat = runCatching {
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                        }.getOrDefault(pcmFloat)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (ob >= 0) {
                        if (info.size > 0) {
                            val buf = dec.getOutputBuffer(ob)!!
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            if (chunkStartUs < 0) chunkStartUs = info.presentationTimeUs.coerceAtLeast(0)
                            downmixToFloat(buf, info.size)
                        }
                        dec.releaseOutputBuffer(ob, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true

                        while (acc.size >= targetSampleRate) {
                            val oneSecond = FloatArray(targetSampleRate) { acc[it] }
                            acc.subList(0, targetSampleRate).clear()
                            emit(AudioChunk(resampler.process(oneSecond), chunkStartUs, targetSampleRate))
                            chunkStartUs += targetSampleRate * 1_000_000L / targetSampleRate
                        }
                    }
                }
            }
            if (acc.isNotEmpty()) emit(AudioChunk(resampler.process(acc.toFloatArray()), chunkStartUs.coerceAtLeast(0), targetSampleRate))
        } catch (e: CaptionEngineException) {
            throw e
        } catch (e: Exception) {
            throw CaptionEngineException.AudioExtraction("audio decode failed: ${e.message}", e)
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            runCatching { ex.release() }
        }
    }.flowOn(Dispatchers.IO)

    private fun MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> Long): Long =
        try { block(this) } finally { runCatching { release() } }
}
