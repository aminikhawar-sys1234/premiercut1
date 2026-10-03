package com.ahstudio.captions.tracking

import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.time.TimelineUs

data class TrackingSample(
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
)

interface TrackingResult {
    fun sampleAt(t: TimelineUs): TrackingSample
}

interface CaptionTrackingEngine {
    val id: String
    suspend fun track(clip: CaptionClip, videoUri: android.net.Uri): TrackingResult
}
