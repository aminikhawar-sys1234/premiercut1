package com.ahstudio.audio.master.cache

import android.content.Context
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.decoder.AudioDecoderFactory
import com.ahstudio.audio.master.decoder.WavPcmReader
import com.ahstudio.audio.master.dsp.transform.AudioClipResampler
import com.ahstudio.audio.master.dsp.transform.PitchShifter
import com.ahstudio.audio.master.dsp.transform.ReverseAudio
import com.ahstudio.audio.master.model.AudioClipModel
import com.ahstudio.audio.master.model.AudioSourceModel
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs

class DecodedAudioCache(
    private val context: Context,
    private val format: AudioFormat,
    private val maxBytes: Long = 96L * 1024 * 1024,
) {
    class Entry(val data: Array<FloatArray>, val sampleRate: Int, val channels: Int, val frames: Int) {
        val sizeBytes: Long get() = frames.toLong() * channels * 4
        val durationSec: Double get() = frames.toDouble() / sampleRate
    }

    private val lock = Any()
    private val map = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var bytes = 0L
    private val diskDir: File by lazy {
        File(context.cacheDir, "audio_master_pcm").apply { mkdirs() }
    }

    var sourceProvider: ((String) -> AudioSourceModel?)? = null

    fun sourceDurationSec(sourceId: String): Double? = synchronized(lock) { map[sourceId]?.durationSec }

    fun getForClip(clip: AudioClipModel): Entry? {
        val base = get(clip.sourceId) ?: return null
        val t = clip.transform
        return when {
            t.reverse && abs(t.pitchSemitones) > 0.01f ->
                derived(base, "rev_pitch_${t.pitchSemitones}") { PitchShifter.shift(ReverseAudio.reverse(it.data), it.channels, t.pitchSemitones, it.sampleRate).let { d -> Entry(d, it.sampleRate, it.channels, d[0].size) } }
            t.reverse -> derived(base, "rev") { ReverseAudio.reverse(it.data).let { d -> Entry(d, it.sampleRate, it.channels, d[0].size) } }
            abs(t.pitchSemitones) > 0.01f ->
                derived(base, "pitch_${t.pitchSemitones}") { PitchShifter.shift(it.data, it.channels, t.pitchSemitones, it.sampleRate).let { d -> Entry(d, it.sampleRate, it.channels, d[0].size) } }
            else -> base
        }
    }

    fun effectiveSourceStartSec(clip: AudioClipModel): Double {
        val base = get(clip.sourceId) ?: return clip.sourceStartSec
        return if (clip.transform.reverse)
            base.durationSec - clip.sourceStartSec - clip.sourceDurationSec
        else clip.sourceStartSec
    }

    fun get(sourceId: String): Entry? {
        synchronized(lock) { map[sourceId]?.let { return it } }
        val src = sourceProvider?.invoke(sourceId) ?: return null
        val entry = if (src.derivedFrom != null && src.derivation.startsWith("disk:")) {
            PcmDiskStore.read(diskFile(sourceId)) ?: decodeAndConvert(src) ?: return null
        } else decodeAndConvert(src) ?: return null
        putInternal(sourceId, entry)
        return entry
    }

    fun preload(sources: Collection<AudioSourceModel>) {
        for (s in sources) get(s.id)
    }

    fun putDerived(key: String, entry: Entry) { putInternal(key, entry) }

    private fun derived(base: Entry, suffix: String, compute: (Entry) -> Entry): Entry {
        val key = base.data.hashCode().toString() + "#" + suffix
        synchronized(lock) { map[key]?.let { return it } }
        val disk = diskFile("${base.sampleRate}_${suffix}_${base.frames}")
        val fromDisk = PcmDiskStore.read(disk)
        val entry = if (fromDisk != null) fromDisk else compute(base).also { PcmDiskStore.write(disk, it) }
        putInternal(key, entry)
        return entry
    }

    private fun putInternal(key: String, entry: Entry) {
        synchronized(lock) {
            map[key] = entry; bytes += entry.sizeBytes
            val it = map.entries.iterator()
            while (bytes > maxBytes && it.hasNext()) { val e = it.next(); bytes -= e.value.sizeBytes; it.remove() }
        }
    }

    private fun decodeAndConvert(src: AudioSourceModel): Entry? {
        val decoder = AudioDecoderFactory.create(context)
        val raw: com.ahstudio.audio.master.decoder.DecodedPcm = try {
            if (AudioDecoderFactory.isWav(src.uri)) WavPcmReader().read(File(src.uri)) else decoder.decode(src.uri)
        } catch (e: Exception) { return null }
        var data = raw.data
        var sr = raw.sampleRate
        var ch = raw.channels
        if (sr != format.sampleRate) { data = AudioClipResampler.resampleRate(data, ch, sr, format.sampleRate); sr = format.sampleRate }
        data = convertChannels(data, ch, format.channels)
        ch = format.channels
        return Entry(data, sr, ch, data[0].size)
    }

    private fun convertChannels(data: Array<FloatArray>, from: Int, to: Int): Array<FloatArray> {
        if (from == to) return data
        val n = data[0].size
        return when {
            from == 1 && to >= 2 -> Array(to) { data[0].copyOf() }
            from >= 2 && to == 1 -> {
                val m = FloatArray(n)
                for (i in 0 until n) { var s = 0f; for (c in data) s += c[i]; m[i] = s / from }
                arrayOf(m)
            }
            else -> Array(to) { c -> data[c % from].copyOf() }
        }
    }

    private fun diskFile(key: String): File = File(diskDir, key.hashCode().toUInt().toString() + ".f32")

    fun release() { synchronized(lock) { map.clear(); bytes = 0 } }
}

internal object PcmDiskStore {
    fun write(file: File, e: DecodedAudioCache.Entry) {
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { o ->
                o.writeInt(0x41485043); o.writeInt(e.channels); o.writeInt(e.sampleRate); o.writeInt(e.frames)
                for (c in e.data) for (v in c) o.writeFloat(v)
            }
        } catch (_: Exception) {}
    }
    fun read(file: File): DecodedAudioCache.Entry? = try {
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { i ->
            if (i.readInt() != 0x41485043) return null
            val ch = i.readInt(); val sr = i.readInt(); val frames = i.readInt()
            val data = Array(ch) { FloatArray(frames) }
            for (c in 0 until ch) for (f in 0 until frames) data[c][f] = i.readFloat()
            DecodedAudioCache.Entry(data, sr, ch, frames)
        }
    } catch (_: Exception) { null }
}
