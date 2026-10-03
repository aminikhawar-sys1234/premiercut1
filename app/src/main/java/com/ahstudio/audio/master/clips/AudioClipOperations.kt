package com.ahstudio.audio.master.clips

import com.ahstudio.audio.master.model.AudioClipModel
import java.util.UUID
import kotlin.math.min

object AudioClipOperations {
    fun newId(): String = UUID.randomUUID().toString()

    fun split(clip: AudioClipModel, atTimelineSec: Double): Pair<AudioClipModel, AudioClipModel>? {
        if (!clip.containsTime(atTimelineSec)) return null
        val leftDur = atTimelineSec - clip.timelineStartSec
        val leftSrc = leftDur * clip.transform.speed
        val left = clip.copy(timelineDurationSec = leftDur, sourceDurationSec = leftSrc)
        val right = clip.copy(
            id = newId(),
            timelineStartSec = atTimelineSec,
            timelineDurationSec = clip.timelineDurationSec - leftDur,
            sourceStartSec = clip.sourceStartSec + leftSrc,
            sourceDurationSec = clip.sourceDurationSec - leftSrc,
        )
        return left to right
    }

    fun trim(clip: AudioClipModel, newStartSec: Double, newEndSec: Double): AudioClipModel {
        require(newEndSec > newStartSec) { "trim end <= start" }
        require(newStartSec >= clip.timelineStartSec - 1e-9 && newEndSec <= clip.timelineEndSec + 1e-9) { "trim out of clip bounds" }
        val dStart = newStartSec - clip.timelineStartSec
        val newDur = newEndSec - newStartSec
        return clip.copy(
            timelineStartSec = newStartSec,
            timelineDurationSec = newDur,
            sourceStartSec = clip.sourceStartSec + dStart * clip.transform.speed,
            sourceDurationSec = newDur * clip.transform.speed,
        )
    }

    fun duplicate(clip: AudioClipModel, newTrackId: String = clip.trackId, offsetSec: Double = 0.0): AudioClipModel =
        clip.copy(id = newId(), trackId = newTrackId, timelineStartSec = clip.timelineStartSec + offsetSec)

    fun withSpeed(clip: AudioClipModel, speed: Float): AudioClipModel {
        require(speed in 0.1f..8f)
        val newTimelineDur = clip.sourceDurationSec / speed
        return clip.copy(transform = clip.transform.copy(speed = speed), timelineDurationSec = newTimelineDur)
    }

    fun replaceSource(clip: AudioClipModel, newSourceId: String, newSourceDurationSec: Double): AudioClipModel {
        val maxSrc = min(clip.sourceDurationSec, newSourceDurationSec)
        return clip.copy(
            sourceId = newSourceId,
            sourceDurationSec = maxSrc,
            timelineDurationSec = maxSrc / clip.transform.speed,
        )
    }
}

object AudioClipValidator {
    fun validate(clip: AudioClipModel, sourceDurationSec: Double?): List<String> {
        val issues = mutableListOf<String>()
        if (clip.timelineStartSec < 0) issues.add("negative timelineStart")
        if (clip.timelineDurationSec <= 0) issues.add("non-positive duration")
        val sd = sourceDurationSec ?: return issues
        if (sd > 0.0 && !clip.isValidAgainstSource(sd)) issues.add("source coverage exceeds source length")
        return issues
    }
}
