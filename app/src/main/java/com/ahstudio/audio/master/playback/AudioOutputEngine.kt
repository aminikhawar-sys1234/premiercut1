package com.ahstudio.audio.master.playback

import android.media.AudioAttributes
import android.media.AudioFormat as AndroidPcmFormat
import android.media.AudioTrack
import com.ahstudio.audio.master.clips.AudioClipSource
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.diagnostics.AudioGlitchDetector
import com.ahstudio.audio.master.diagnostics.AudioPerformanceMonitor
import com.ahstudio.audio.master.mixer.MasterAudioMixer
import kotlin.math.abs

enum class AudioPlaybackState { IDLE, PLAYING, PAUSED, RELEASED }

class AndroidAudioTrackOutput(private val format: AudioFormat) {
    private var track: AudioTrack? = null

    fun start(blockFrames: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            format.sampleRate,
            if (format.channels == 1) AndroidPcmFormat.CHANNEL_OUT_MONO else AndroidPcmFormat.CHANNEL_OUT_STEREO,
            AndroidPcmFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(4096)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setAudioFormat(AndroidPcmFormat.Builder().setEncoding(AndroidPcmFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(format.sampleRate)
                .setChannelMask(if (format.channels == 1) AndroidPcmFormat.CHANNEL_OUT_MONO else AndroidPcmFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf * 2, blockFrames * format.channels * 4 * 4))
            .build()
        t.play()
        track = t
    }

    fun writeInterleaved(src: FloatArray, frames: Int): Int {
        val t = track ?: return 0
        return t.write(src, 0, frames * format.channels, AudioTrack.WRITE_BLOCKING) / format.channels
    }
    fun pause() { track?.pause() }
    fun resume() { track?.play() }
    fun flush() { track?.flush() }
    fun release() { track?.let { it.stop(); it.release() }; track = null }
}

class AudioPlaybackSynchronizer {
    var driftSec: Double = 0.0; private set
    fun update(audioSec: Double, hostSec: Double) {
        val d = audioSec - hostSec
        driftSec = driftSec * 0.9 + d * 0.1
    }
}

class AudioSeekController {
    private var lastTarget = Double.NaN
    fun shouldSeek(target: Double): Boolean {
        if (abs(target - lastTarget) < 0.0005) return false
        lastTarget = target; return true
    }
    fun reset() { lastTarget = Double.NaN }
}

class AudioOutputEngine(
    private val format: AudioFormat,
    private val mixer: MasterAudioMixer,
    private val performance: AudioPerformanceMonitor,
    private val glitches: AudioGlitchDetector,
) {
    interface HostClock { fun positionSec(): Double; fun isPlaying(): Boolean }
    @Volatile var hostClock: HostClock? = null
    @Volatile var state: AudioPlaybackState = AudioPlaybackState.IDLE; private set
    @Volatile var onPosition: ((Double) -> Unit)? = null

    private val blockSize = 512
    private val seekCtl = AudioSeekController()
    private val sync = AudioPlaybackSynchronizer()
    private var output: AndroidAudioTrackOutput? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var nextBlockSec = 0.0
    @Volatile private var blockIndex = 0L
    private val interleaved = FloatArray(blockSize * format.channels)

    fun play(startSec: Double, reader: AudioClipSource) {
        if (state == AudioPlaybackState.PLAYING) return
        if (output == null) { output = AndroidAudioTrackOutput(format).also { it.start(blockSize) } }
        synchronized(this) { resetAt(startSec, reader) }
        state = AudioPlaybackState.PLAYING
        output?.resume()
        if (thread == null || !running) {
            running = true
            thread = Thread({ renderLoop(reader) }, "AhAudioRender").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        }
    }

    fun pause() {
        if (state != AudioPlaybackState.PLAYING) return
        state = AudioPlaybackState.PAUSED
        output?.pause()
    }

    fun seekTo(sec: Double, reader: AudioClipSource) {
        if (!seekCtl.shouldSeek(sec)) return
        synchronized(this) {
            resetAt(sec, reader)
            if (state == AudioPlaybackState.PLAYING) output?.resume()
        }
    }

    fun stopAndRelease() {
        running = false; state = AudioPlaybackState.RELEASED
        thread?.join(500); thread = null
        output?.release(); output = null
    }

    fun currentPositionSec(): Double = nextBlockSec

    private fun resetAt(sec: Double, reader: AudioClipSource) {
        nextBlockSec = sec; blockIndex = 0
        mixer.reset()
        output?.flush()
    }

    private fun renderLoop(reader: AudioClipSource) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        while (running) {
            if (state != AudioPlaybackState.PLAYING) { Thread.sleep(15); continue }
            val hc = hostClock
            if (hc != null && hc.isPlaying()) {
                val host = hc.positionSec()
                sync.update(nextBlockSec, host)
                if (abs(nextBlockSec - host) > 0.08) synchronized(this) { resetAt(host, reader) }
            }
            val ctx = AudioRenderContext(format, nextBlockSec, blockSize, blockIndex, realtime = true)
            val buf = mixer.renderBlock(ctx, reader)
            performance.onBlockRendered(mixer.lastRenderMs, blockSize)
            buf.interleave(interleaved, blockSize)
            val written = output?.writeInterleaved(interleaved, blockSize) ?: 0
            if (written < blockSize) glitches.onUnderrun()
            nextBlockSec += blockSize.toDouble() / format.sampleRate
            blockIndex++
            onPosition?.invoke(nextBlockSec)
        }
    }
}
