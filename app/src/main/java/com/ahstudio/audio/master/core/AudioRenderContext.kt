package com.ahstudio.audio.master.core

class AudioRenderContext(
    val format: AudioFormat,
    val timelineStartSec: Double,
    val frames: Int,
    val blockIndex: Long = 0L,
    val realtime: Boolean = true,
) {
    val sampleRate get() = format.sampleRate
    val timelineEndSec get() = timelineStartSec + frames.toDouble() / sampleRate
    fun timelineSecAt(frame: Int) = timelineStartSec + frame.toDouble() / sampleRate
}
