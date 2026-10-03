package com.ahstudio.captions.tracking

import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.time.TimelineUs
import android.net.Uri

class DefaultTrackingAdapter : CaptionTrackingEngine {
    override val id = "none"
    private val emptySample = TrackingSample()
    private val result = object : TrackingResult {
        override fun sampleAt(t: TimelineUs): TrackingSample = emptySample
    }
    override suspend fun track(clip: CaptionClip, videoUri: Uri): TrackingResult = result
}
