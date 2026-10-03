package com.ahstudio.audio.master.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat as AF
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.ahstudio.audio.master.AudioEngineError
import com.ahstudio.audio.master.AudioEngineResult
import com.ahstudio.audio.master.model.AudioClipModel
import com.ahstudio.audio.master.model.AudioSourceKind
import com.ahstudio.audio.master.model.AudioSourceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class RecordingConfig(
    val sampleRate: Int = 48_000,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val maxDurationSec: Double = 600.0,
    val outputDir: File? = null,
)

class RecordingSession internal constructor(val file: File, val sampleRate: Int, val channels: Int) {
    @Volatile var framesWritten: Long = 0; internal set
    @Volatile var peak: Float = 0f; internal set
    @Volatile var paused: Boolean = false
    val durationSec: Double get() = framesWritten.toDouble() / sampleRate
}

data class RecordingResult(val session: RecordingSession, val source: AudioSourceModel)

class WavWriter(private val file: File, private val sampleRate: Int, private val channels: Int, private val floatPcm: Boolean) {
    private val raf = RandomAccessFile(file, "rw")
    private var frames = 0L

    private fun writeLeInt(v: Int) {
        raf.write(v and 0xFF)
        raf.write((v ushr 8) and 0xFF)
        raf.write((v ushr 16) and 0xFF)
        raf.write((v ushr 24) and 0xFF)
    }

    private fun writeLeShort(v: Int) {
        raf.write(v and 0xFF)
        raf.write((v ushr 8) and 0xFF)
    }

    init {
        raf.setLength(0)
        raf.write(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        writeLeInt(0)
        raf.write(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
        raf.write(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
        writeLeInt(16)
        writeLeShort(if (floatPcm) 3 else 1)
        writeLeShort(channels)
        writeLeInt(sampleRate)
        writeLeInt(sampleRate * channels * if (floatPcm) 4 else 2)
        writeLeShort(channels * if (floatPcm) 4 else 2)
        writeLeShort(if (floatPcm) 32 else 16)
        raf.write(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
        writeLeInt(0)
    }
    fun write(interleaved: FloatArray, framesToWrite: Int) {
        if (floatPcm) {
            val buf = ByteBuffer.allocate(framesToWrite * channels * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until framesToWrite * channels) buf.putFloat(interleaved[i])
            raf.write(buf.array())
        } else {
            val buf = ByteArray(framesToWrite * channels * 2)
            for (i in 0 until framesToWrite * channels) {
                val v = (interleaved[i].coerceIn(-1f, 1f) * 32767f).toInt()
                buf[i * 2] = (v and 0xFF).toByte(); buf[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            raf.write(buf)
        }
        frames += framesToWrite
    }
    fun close() {
        val dataLen = (frames * channels * if (floatPcm) 4 else 2).toInt()
        raf.seek(4); writeLeInt(36 + dataLen)
        raf.seek(40); writeLeInt(dataLen)
        raf.close()
    }
}

class AndroidAudioRecorder(private val context: Context, private val scope: CoroutineScope) {
    @Volatile private var session: RecordingSession? = null
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isRecording(): Boolean = session != null

    fun start(config: RecordingConfig = RecordingConfig()): AudioEngineResult<RecordingSession> {
        if (!hasPermission()) return AudioEngineResult.Failure(AudioEngineError.PERMISSION_DENIED, "RECORD_AUDIO permission not granted")
        if (session != null) return AudioEngineResult.Failure(AudioEngineError.RECORD_FAILED, "Recording already active")
        val dir = config.outputDir ?: File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.wav")
        val wav = WavWriter(file, config.sampleRate, config.channels, config.bitDepth == 32)
        val s = RecordingSession(file, config.sampleRate, config.channels)
        session = s; cancelled.set(false); stopped.set(false)
        val minBuf = AudioRecord.getMinBufferSize(config.sampleRate,
            if (config.channels == 1) AF.CHANNEL_IN_MONO else AF.CHANNEL_IN_STEREO,
            if (config.bitDepth == 32) AF.ENCODING_PCM_FLOAT else AF.ENCODING_PCM_16BIT)
        job = scope.launch(Dispatchers.IO) {
            val record = AudioRecord(MediaRecorder.AudioSource.MIC, config.sampleRate,
                if (config.channels == 1) AF.CHANNEL_IN_MONO else AF.CHANNEL_IN_STEREO,
                if (config.bitDepth == 32) AF.ENCODING_PCM_FLOAT else AF.ENCODING_PCM_16BIT, minBuf * 2)
            try {
                check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
                record.startRecording()
                val chunk = 1024
                val fbuf = FloatArray(chunk * config.channels)
                val sbuf = ShortArray(chunk * config.channels)
                while (!cancelled.get() && !stopped.get() && s.durationSec < config.maxDurationSec) {
                    if (s.paused) { Thread.sleep(20); continue }
                    val n = if (config.bitDepth == 32) record.read(fbuf, 0, chunk * config.channels, AudioRecord.READ_BLOCKING)
                            else record.read(sbuf, 0, chunk * config.channels, AudioRecord.READ_BLOCKING)
                    if (n <= 0) continue
                    if (config.bitDepth != 32) for (i in 0 until n) fbuf[i] = sbuf[i] / 32768f
                    var pk = 0f
                    for (i in 0 until n) { val v = abs(fbuf[i]); if (v > pk) pk = v }
                    if (pk > s.peak) s.peak = pk
                    synchronized(s) { wav.write(fbuf, n / config.channels); s.framesWritten += n / config.channels }
                }
            } finally {
                try { record.stop() } catch (_: Exception) {}
                record.release()
                wav.close()
            }
        }
        return AudioEngineResult.Success(s)
    }

    fun pause() { session?.paused = true }
    fun resume() { session?.paused = false }
    suspend fun cancel() { cancelled.set(true); job?.join(); session = null; sessionFile()?.delete() }
    private fun sessionFile(): File? = session?.file

    suspend fun finish(): AudioEngineResult<RecordingSession> {
        val s = session ?: return AudioEngineResult.Failure(AudioEngineError.RECORD_FAILED, "No active recording")
        stopped.set(true); job?.join()
        session = null
        return AudioEngineResult.Success(s)
    }
}

object RecordingClipCreator {
    fun create(session: RecordingSession, trackId: String, timelineStartSec: Double): Pair<AudioSourceModel, AudioClipModel> {
        val source = AudioSourceModel(
            id = UUID.randomUUID().toString(), uri = session.file.absolutePath,
            kind = AudioSourceKind.RECORDED, durationSec = session.durationSec,
            nativeSampleRate = session.sampleRate, nativeChannels = session.channels,
            title = "Recording " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
        )
        val clip = AudioClipModel(
            id = UUID.randomUUID().toString(), trackId = trackId, sourceId = source.id,
            timelineStartSec = timelineStartSec, timelineDurationSec = session.durationSec,
            sourceStartSec = 0.0, sourceDurationSec = session.durationSec,
        )
        return source to clip
    }
}
