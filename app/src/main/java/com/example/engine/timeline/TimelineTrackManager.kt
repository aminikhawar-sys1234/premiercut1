package com.example.engine.timeline

import com.example.domain.model.*
import com.example.ui.components.timeline.ActiveTracksSnapshot
import com.example.ui.components.timeline.LaneClipItem
import com.example.ui.components.timeline.LaneKind
import com.example.ui.components.timeline.TimelineLane
import com.example.ui.components.timeline.TrackLaneManager
import java.util.UUID

/**
 * Principal NLE Multi-Track Manager.
 *
 * Responsibilities:
 * 1. Capture and enforce Authoritative CTI Playhead insertion time (never fallback to 0s or auto-end).
 * 2. Dynamic Track Generation & Unique Z-Index Allocation for multiple simultaneous layers.
 * 3. Track Auto-Assignment: Finds active compatible track or creates a new track dynamically.
 * 4. Locked track protection (skips locked tracks; never places content on locked tracks).
 * 5. Track-level state management (Lock, Visibility, Mute, Solo, Height, Collapse).
 */
object TimelineTrackManager {

    /**
     * Captures the authoritative CTI playhead position to be used as insertion start time.
     * Guaranteed to use the exact playhead timestamp without resetting to 0s or snapping to end.
     */
    fun getAuthoritativeInsertionTime(currentCtiMs: Long): Long {
        return currentCtiMs.coerceAtLeast(0L)
    }

    /**
     * Allocates a guaranteed unique Z-Index / TrackIndex for a newly created overlay.
     */
    fun allocateOverlayTrackIndex(timeline: Timeline): Int {
        val allTracks = timeline.tracks.ifEmpty { timeline.getEffectiveTracks() }
        val maxOrder = (allTracks.map { it.order } + allTracks.map { it.zOrder } + timeline.overlayClips.map { it.trackIndex }).maxOrNull() ?: 0
        return maxOrder + 1
    }

    /**
     * Allocates a guaranteed unique TrackIndex for a newly created text clip.
     */
    fun allocateTextTrackIndex(timeline: Timeline): Int {
        val allTracks = timeline.tracks.ifEmpty { timeline.getEffectiveTracks() }
        val maxOrder = (allTracks.map { it.order } + allTracks.map { it.zOrder } + timeline.textClips.map { it.trackIndex }).maxOrNull() ?: 0
        return maxOrder + 1
    }

    /**
     * Allocates a guaranteed unique TrackIndex for a newly created audio clip.
     */
    fun allocateAudioTrackIndex(timeline: Timeline): Int {
        val allTracks = timeline.tracks.ifEmpty { timeline.getEffectiveTracks() }
        val maxOrder = (allTracks.map { it.order } + allTracks.map { it.zOrder } + timeline.audioClips.map { it.trackIndex }).maxOrNull() ?: 0
        return maxOrder + 1
    }

    /**
     * Allocates a guaranteed unique TrackIndex for a newly created sticker / shape / emoji / element clip.
     */
    fun allocateStickerTrackIndex(timeline: Timeline): Int {
        val allTracks = timeline.tracks.ifEmpty { timeline.getEffectiveTracks() }
        val maxOrder = (allTracks.map { it.order } + allTracks.map { it.zOrder } + timeline.stickerClips.map { it.trackIndex }).maxOrNull() ?: 0
        return maxOrder + 1
    }

    /**
     * Allocates a guaranteed unique TrackIndex for a newly created effect / filter clip.
     */
    fun allocateEffectTrackIndex(timeline: Timeline): Int {
        val allTracks = timeline.tracks.ifEmpty { timeline.getEffectiveTracks() }
        val maxOrder = (allTracks.map { it.order } + allTracks.map { it.zOrder } + timeline.effectClips.map { it.trackIndex }).maxOrNull() ?: 0
        return maxOrder + 1
    }

    /**
     * Allocates a guaranteed unique TrackIndex for any TrackType.
     */
    fun allocateTrackIndex(timeline: Timeline, trackType: TrackType): Int {
        val allTracks = timeline.tracks.ifEmpty { timeline.getEffectiveTracks() }
        val maxOrder = (allTracks.map { it.order } + allTracks.map { it.zOrder }).maxOrNull() ?: 0
        return maxOrder + 1
    }

    /**
     * Appends a brand new track to the timeline track list (tracks + newTrack)
     * with a unique UUID and proper incremented order. Never overwrites existing tracks.
     */
    fun addTrack(
        timeline: Timeline,
        trackType: TrackType,
        displayName: String? = null
    ): Pair<Timeline, NleTrack> {
        val currentTracks = timeline.tracks
        
        // Max order dhoond kar +1 karein taake list ke bilkul aakhir mein append ho
        val nextOrder = (currentTracks.maxOfOrNull { it.order } ?: -1) + 1
        
        // Stable UUID har track ke liye laazmi hai taake Compose keys duplicate na hon
        val newTrackUuid = UUID.randomUUID().toString()
        
        val newNleTrack = NleTrack(
            trackId = newTrackUuid,
            trackType = trackType,
            displayName = displayName ?: "${trackType.name.replace("_", " ")} ${nextOrder + 1}",
            order = nextOrder,
            zOrder = nextOrder, // zOrder ko order ke sath sync rakhein taake overwrite na ho
            isLocked = false,
            isVisible = true,
            isMuted = false,
            isSolo = false
        )
        
        // List ke aakhir mein append karein (no indexing or replacement)
        val updatedTracks = currentTracks + newNleTrack
        return Pair(timeline.copy(tracks = updatedTracks), newNleTrack)
    }

    /**
     * Track Auto-Assignment:
     * Finds active compatible track for [trackType] at [requestedCtiMs].
     * If an existing compatible track is unlocked and available, returns its index.
     * If all compatible tracks are locked or occupied at that timestamp, dynamically creates
     * a new track and returns updated Timeline + newly allocated trackIndex.
     */
    fun findOrCreateTrackForClip(
        timeline: Timeline,
        trackType: TrackType,
        requestedCtiMs: Long,
        durationMs: Long = 3000L
    ): Pair<Timeline, Int> {
        val effectiveTracks = timeline.getEffectiveTracks()
        val compatibleTracks = effectiveTracks.filter { it.trackType == trackType }

        // Find an unlocked compatible track without collision at requestedCtiMs
        for (track in compatibleTracks) {
            if (track.isLocked) continue // Skip locked tracks

            val hasCollision = when (trackType) {
                TrackType.MAIN_VIDEO -> false // Main track uses insert/append/ripple
                TrackType.OVERLAY, TrackType.ELEMENT, TrackType.ADJUSTMENT -> {
                    timeline.overlayClips.any { it.trackIndex == track.zOrder &&
                        it.overlapsWith(requestedCtiMs, durationMs) }
                }
                TrackType.AUDIO, TrackType.MUSIC, TrackType.SFX -> {
                    timeline.audioClips.any { it.trackIndex == track.order &&
                        it.overlapsWith(requestedCtiMs, durationMs) }
                }
                TrackType.TEXT, TrackType.CAPTION -> {
                    timeline.textClips.any { it.trackIndex == track.order &&
                        it.overlapsWith(requestedCtiMs, durationMs) }
                }
                TrackType.EFFECT -> false
                TrackType.STICKER -> false
                TrackType.SHAPE -> false
            }

            if (!hasCollision) {
                return Pair(timeline, track.zOrder)
            }
        }

        // No free unlocked track found -> dynamically create a new track appended with unique UUID & incremented order
        val currentTracks = timeline.tracks
        val nextOrder = (currentTracks.maxOfOrNull { it.order } ?: -1) + 1
        val newTrackUuid = UUID.randomUUID().toString()
        val newNleTrack = NleTrack(
            trackId = newTrackUuid,
            trackType = trackType,
            displayName = "${trackType.name.replace("_", " ")} ${nextOrder + 1}",
            order = nextOrder,
            zOrder = nextOrder,
            isLocked = false,
            isVisible = true,
            isMuted = false,
            isSolo = false
        )
        val updatedTracks = currentTracks + newNleTrack
        return Pair(timeline.copy(tracks = updatedTracks), nextOrder)
    }

    /**
     * Checks if a track of a given type and index is locked.
     */
    fun isTrackLocked(timeline: Timeline, trackType: TrackType, trackIndex: Int = 0): Boolean {
        val directSettingLocked = timeline.trackSettings[trackType]?.isLocked == true
        if (directSettingLocked) return true

        val matchingTrack = timeline.tracks.firstOrNull { it.trackType == trackType && it.zOrder == trackIndex }
        return matchingTrack?.isLocked == true
    }

    /**
     * Sets locked state of a track by trackId.
     */
    fun setTrackLocked(timeline: Timeline, trackId: String, locked: Boolean): Timeline {
        val tracks = timeline.getEffectiveTracks().map { track ->
            if (track.trackId == trackId) track.copy(isLocked = locked) else track
        }
        val targetTrack = tracks.firstOrNull { it.trackId == trackId }
        val updatedSettings = if (targetTrack != null) {
            val curr = timeline.trackSettings[targetTrack.trackType] ?: TrackSettings(targetTrack.trackType)
            timeline.trackSettings + (targetTrack.trackType to curr.copy(isLocked = locked))
        } else timeline.trackSettings
        return timeline.copy(tracks = tracks, trackSettings = updatedSettings)
    }

    /**
     * Sets visibility state of a track by trackId.
     */
    fun setTrackVisible(timeline: Timeline, trackId: String, visible: Boolean): Timeline {
        val tracks = timeline.getEffectiveTracks().map { track ->
            if (track.trackId == trackId) track.copy(isVisible = visible) else track
        }
        val targetTrack = tracks.firstOrNull { it.trackId == trackId }
        val updatedSettings = if (targetTrack != null) {
            val curr = timeline.trackSettings[targetTrack.trackType] ?: TrackSettings(targetTrack.trackType)
            timeline.trackSettings + (targetTrack.trackType to curr.copy(isHidden = !visible))
        } else timeline.trackSettings
        return timeline.copy(tracks = tracks, trackSettings = updatedSettings)
    }

    /**
     * Sets muted state of an audio track by trackId.
     */
    fun setTrackMuted(timeline: Timeline, trackId: String, muted: Boolean): Timeline {
        val tracks = timeline.getEffectiveTracks().map { track ->
            if (track.trackId == trackId) track.copy(isMuted = muted) else track
        }
        val targetTrack = tracks.firstOrNull { it.trackId == trackId }
        val updatedSettings = if (targetTrack != null && targetTrack.trackType.isAudioTrack) {
            val curr = timeline.trackSettings[targetTrack.trackType] ?: TrackSettings(targetTrack.trackType)
            timeline.trackSettings + (targetTrack.trackType to curr.copy(isMuted = muted))
        } else timeline.trackSettings
        return timeline.copy(tracks = tracks, trackSettings = updatedSettings)
    }

    /**
     * Sets solo state of an audio track by trackId.
     */
    fun setTrackSolo(timeline: Timeline, trackId: String, solo: Boolean): Timeline {
        val tracks = timeline.getEffectiveTracks().map { track ->
            if (track.trackId == trackId) track.copy(isSolo = solo) else track
        }
        val targetTrack = tracks.firstOrNull { it.trackId == trackId }
        val updatedSettings = if (targetTrack != null && targetTrack.trackType.isAudioTrack) {
            val curr = timeline.trackSettings[targetTrack.trackType] ?: TrackSettings(targetTrack.trackType)
            timeline.trackSettings + (targetTrack.trackType to curr.copy(isSolo = solo))
        } else timeline.trackSettings
        return timeline.copy(tracks = tracks, trackSettings = updatedSettings)
    }

    /**
     * Sets track display height.
     */
    fun setTrackHeight(timeline: Timeline, trackId: String, height: TrackHeight): Timeline {
        val tracks = timeline.getEffectiveTracks().map { track ->
            if (track.trackId == trackId) track.copy(height = height) else track
        }
        return timeline.copy(tracks = tracks)
    }

    /**
     * Re-assigns strict unique sequential Z-Indices to all overlay clips.
     */
    fun normalizeOverlayZIndices(overlays: List<VideoClip>): List<VideoClip> {
        return overlays.mapIndexed { index, clip ->
            clip.copy(trackIndex = index + 1)
        }
    }

    /**
     * Generates all dynamic timeline lanes via TrackLaneManager.
     */
    fun generateDynamicLanes(timeline: Timeline): List<TimelineLane> {
        return TrackLaneManager.computeLanes(timeline)
    }

    /**
     * Evaluates all active tracks simultaneously at [currentCtiMs] without hardcoded limits.
     */
    fun evaluateActiveTracks(timeline: Timeline, currentCtiMs: Long): ActiveTracksSnapshot {
        return TrackLaneManager.evaluateTimelineAt(timeline, currentCtiMs)
    }

    /**
     * Moves an existing clip between tracks (e.g. from Lane 1 to Lane 2, or Main Video to Overlay).
     */
    fun moveClipToTrack(timeline: Timeline, clipId: String, targetTrackIndex: Int): Timeline {
        val targetIdx = targetTrackIndex.coerceAtLeast(0)

        // Case 1: Video clip moving to overlay
        val videoIndex = timeline.videoClips.indexOfFirst { it.id == clipId }
        if (videoIndex != -1 && targetIdx > 0) {
            val movingClip = timeline.videoClips[videoIndex]
            val updatedVideos = timeline.videoClips.filterIndexed { index, _ -> index != videoIndex }
            val updatedOverlays = timeline.overlayClips + movingClip.copy(trackIndex = targetIdx)
            return timeline.copy(videoClips = updatedVideos, overlayClips = updatedOverlays)
        }

        // Case 2: Overlay clip moving to main video or another overlay tier
        val overlayIndex = timeline.overlayClips.indexOfFirst { it.id == clipId }
        if (overlayIndex != -1) {
            val movingClip = timeline.overlayClips[overlayIndex]
            if (targetIdx == 0) {
                // Move overlay down to main video
                val updatedOverlays = timeline.overlayClips.filterIndexed { index, _ -> index != overlayIndex }
                val updatedVideos = (timeline.videoClips + movingClip.copy(trackIndex = 0)).sortedBy { it.timelineStartMs }
                return timeline.copy(videoClips = updatedVideos, overlayClips = updatedOverlays)
            } else {
                // Move between overlay tiers
                val updatedOverlays = timeline.overlayClips.map { clip ->
                    if (clip.id == clipId) clip.copy(trackIndex = targetIdx) else clip
                }
                return timeline.copy(overlayClips = updatedOverlays)
            }
        }

        // Case 3: Audio clip moving between audio lanes
        val audioIndex = timeline.audioClips.indexOfFirst { it.id == clipId }
        if (audioIndex != -1) {
            val updatedAudios = timeline.audioClips.map { clip ->
                if (clip.id == clipId) clip.copy(trackIndex = targetIdx) else clip
            }
            return timeline.copy(audioClips = updatedAudios)
        }

        // Case 4: Text clip moving between text lanes
        val textIndex = timeline.textClips.indexOfFirst { it.id == clipId }
        if (textIndex != -1) {
            val updatedTexts = timeline.textClips.map { clip ->
                if (clip.id == clipId) clip.copy(trackIndex = targetIdx) else clip
            }
            return timeline.copy(textClips = updatedTexts)
        }

        return timeline
    }

    /**
     * Preserves stable user-created track indices without squashing intermediate empty tracks.
     */
    fun compactTracks(timeline: Timeline): Timeline {
        return timeline
    }
}
