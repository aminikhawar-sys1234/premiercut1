package com.ahstudio.audio.master.core

enum class PcmEncoding { FLOAT, INT16 }

data class AudioFormat(val sampleRate: Int, val channels: Int, val encoding: PcmEncoding = PcmEncoding.FLOAT) {
    init {
        require(sampleRate in 8000..192000) { "sampleRate out of bounds: $sampleRate" }
        require(channels in 1..8) { "channels out of bounds: $channels" }
    }
    val bytesPerFrame: Int get() = channels * if (encoding == PcmEncoding.FLOAT) 4 else 2
    companion object { val DEFAULT = AudioFormat(48_000, 2, PcmEncoding.FLOAT) }
}

data class AudioTimestamp(val timelineUs: Long, val sourceUs: Long)

fun dbToLin(db: Float): Float = Math.pow(10.0, db / 20.0).toFloat()
fun linToDb(lin: Float): Float = (20.0 * Math.log10(lin.toDouble().coerceAtLeast(1e-9))).toFloat()
