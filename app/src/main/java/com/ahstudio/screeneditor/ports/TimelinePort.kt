package com.ahstudio.screeneditor.ports

import kotlinx.coroutines.flow.StateFlow

interface TimelinePort {
    val positionUs: StateFlow<Long>
    val durationUs: StateFlow<Long>
    fun clipsAt(us: Long): List<ClipView>
    fun trackViews(): List<TrackView>
    fun clipById(clipId: String): ClipView?
    fun moveClip(clipId: String, newStartUs: Long, targetTrackId: String? = null): Boolean
    fun trimClip(clipId: String, edge: TrimEdge, newBoundaryUs: Long): Boolean
    fun snapTimeUs(us: Long): Long
}
