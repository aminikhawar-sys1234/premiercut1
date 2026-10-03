package com.ahstudio.audio.master.decoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

class DecodedPcm(val data: Array<FloatArray>, val sampleRate: Int, val channels: Int)

interface AudioDecoder {
    fun decode(uri: String, startUs: Long = 0L, endUs: Long = -1L): DecodedPcm
}

class MediaCodecAudioDecoder(private val context: Context) : AudioDecoder {
    override fun decode(uri: String, startUs: Long, endUs: Long): DecodedPcm {
        val ex = MediaExtractor()
        try {
            if (uri.startsWith("content:")) {
                val afd = context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")
                    ?: throw IllegalArgumentException("Cannot open $uri")
                afd.use { ex.setDataSource(it.fileDescriptor) }
            } else ex.setDataSource(uri)

            var trackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { trackIdx = i; format = f; break }
            }
            require(trackIdx >= 0) { "No audio track in $uri" }
            ex.selectTrack(trackIdx)
            if (startUs > 0) ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val mime = format!!.getString(MediaFormat.KEY_MIME)!!
            var sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0); codec.start()
                val chunks = ArrayList<FloatArray>(); var totalFrames = 0L
                val info = MediaCodec.BufferInfo()
                var inputDone = false; var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val bb = codec.getInputBuffer(inIdx)!!
                            val sz = ex.readSampleData(bb, 0)
                            if (sz < 0 || (endUs in 1..ex.sampleTime)) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                            } else { codec.queueInputBuffer(inIdx, 0, sz, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    when (val outIdx = codec.dequeueOutputBuffer(info, 10_000)) {
                        in 0..Int.MAX_VALUE -> {
                            if (info.size > 0) {
                                val ob = codec.getOutputBuffer(outIdx)!!
                                ob.order(ByteOrder.LITTLE_ENDIAN); ob.position(info.offset); ob.limit(info.offset + info.size)
                                val floatPcm = codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                                val frames = info.size / (ch * if (floatPcm) 4 else 2)
                                if (frames > 0) {
                                    val inter = FloatArray(frames * ch)
                                    if (floatPcm) { val fb = ob.asFloatBuffer(); for (i in inter.indices) inter[i] = fb.get(i) }
                                    else { val sb = ob.asShortBuffer(); for (i in inter.indices) inter[i] = sb.get(i) / 32768f }
                                    chunks.add(inter); totalFrames += frames
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
                return planarize(chunks, ch, totalFrames.toInt(), sr, ch)
            } finally { codec.stop(); codec.release() }
        } finally { ex.release() }
    }

    private fun planarize(chunks: List<FloatArray>, channels: Int, totalFrames: Int, sr: Int, ch: Int): DecodedPcm {
        val out = Array(ch) { FloatArray(totalFrames) }
        var f = 0
        for (chunk in chunks) {
            var k = 0
            while (k < chunk.size && f < totalFrames) {
                for (c in 0 until ch) { if (f < totalFrames) out[c][f] = chunk[k]; k++ }
                f++
            }
        }
        return DecodedPcm(out, sr, ch)
    }
}

class WavPcmReader {
    fun read(file: File): DecodedPcm {
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(12); raf.readFully(riff)
            require(String(riff, 0, 4) == "RIFF" && String(riff, 8, 4) == "WAVE") { "Not a WAV file" }
            var audioFormat = 0; var channels = 0; var sampleRate = 0; var bits = 0
            var dataStart = -1L; var dataLen = 0L
            while (true) {
                val hdr = ByteArray(8)
                if (raf.read(hdr) < 8) break
                val id = String(hdr, 0, 4); val size = ((hdr[4].toLong() and 0xFF) or ((hdr[5].toLong() and 0xFF) shl 8) or
                    ((hdr[6].toLong() and 0xFF) shl 16) or ((hdr[7].toLong() and 0xFF) shl 24))
                if (id == "fmt ") {
                    val fb = ByteArray(size.toInt()); raf.readFully(fb)
                    audioFormat = (fb[0].toInt() and 0xFF) or ((fb[1].toInt() and 0xFF) shl 8)
                    channels = (fb[2].toInt() and 0xFF) or ((fb[3].toInt() and 0xFF) shl 8)
                    sampleRate = (fb[4].toInt() and 0xFF) or ((fb[5].toInt() and 0xFF) shl 8) or
                        ((fb[6].toInt() and 0xFF) shl 16) or ((fb[7].toInt() and 0xFF) shl 24)
                    bits = (fb[14].toInt() and 0xFF) or ((fb[15].toInt() and 0xFF) shl 8)
                } else if (id == "data") { dataStart = raf.filePointer; dataLen = size; break }
                else raf.seek(raf.filePointer + size + (size and 1))
            }
            require(dataStart >= 0) { "No data chunk" }
            val bytesPerSample = bits / 8
            val frames = (dataLen / (channels * bytesPerSample)).toInt()
            val out = Array(channels) { FloatArray(frames) }
            raf.seek(dataStart)
            val buf = ByteArray(channels * bytesPerSample)
            for (f in 0 until frames) {
                raf.readFully(buf)
                for (c in 0 until channels) {
                    val o = c * bytesPerSample
                    out[c][f] = when {
                        audioFormat == 3 && bits == 32 -> java.lang.Float.intBitsToFloat(
                            (buf[o].toInt() and 0xFF) or ((buf[o+1].toInt() and 0xFF) shl 8) or
                            ((buf[o+2].toInt() and 0xFF) shl 16) or ((buf[o+3].toInt() and 0xFF) shl 24))
                        bits == 16 -> (((buf[o].toInt() and 0xFF) or ((buf[o+1].toInt() and 0xFF) shl 8)).toShort()) / 32768f
                        bits == 24 -> {
                            var v = (buf[o].toInt() and 0xFF) or ((buf[o+1].toInt() and 0xFF) shl 8) or ((buf[o+2].toInt() and 0xFF) shl 16)
                            if (v and 0x800000 != 0) v = v or -0x1000000
                            v / 8388608f
                        }
                        else -> throw IllegalArgumentException("Unsupported WAV: fmt=$audioFormat bits=$bits")
                    }
                }
            }
            return DecodedPcm(out, sampleRate, channels)
        }
    }
}

object AudioDecoderFactory {
    fun create(context: Context): AudioDecoder = MediaCodecAudioDecoder(context)
    fun isWav(path: String): Boolean {
        val f = File(path)
        if (!f.exists() || f.length() < 12) return false
        f.inputStream().use { s ->
            val h = ByteArray(12); if (s.read(h) < 12) return false
            return String(h, 0, 4) == "RIFF" && String(h, 8, 4) == "WAVE"
        }
    }
}
