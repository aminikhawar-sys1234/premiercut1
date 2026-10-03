package com.ahstudio.audio.master.core

import com.ahstudio.audio.master.model.AudioClipModel
import kotlin.math.roundToLong

object AudioTimeMapper {
    fun timelineToSourceSec(clip: AudioClipModel, t: Double): Double =
        clip.sourceStartSec + (t - clip.timelineStartSec) * clip.transform.speed

    fun sourceToTimelineSec(clip: AudioClipModel, s: Double): Double =
        clip.timelineStartSec + (s - clip.sourceStartSec) / clip.transform.speed

    fun secToSample(sec: Double, sampleRate: Int): Long = (sec * sampleRate).roundToLong()
    fun sampleToSec(sample: Long, sampleRate: Int): Double = sample.toDouble() / sampleRate
    fun secToUs(sec: Double): Long = (sec * 1_000_000.0).roundToLong()
    fun usToSec(us: Long): Double = us / 1_000_000.0
}
