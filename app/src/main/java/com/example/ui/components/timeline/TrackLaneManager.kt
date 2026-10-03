package com.example.ui.components.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudioClip
import com.example.domain.model.EffectClip
import com.example.domain.model.StickerClip
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip

/**
 * Supported timeline track kinds for the Multi-Track NLE Engine.
 */
enum class LaneKind(
  val displayName: String,
  val defaultHeightDp: Dp,
  val baseColor: Color,
  val accentColor: Color
) {
  MAIN_VIDEO("Main Track", 60.dp, Color(0xFF1E293B), Color(0xFF00E5FF)),
  OVERLAY("Overlay / PIP", 36.dp, Color(0xFF1E1B4B), Color(0xFF818CF8)),
  TEXT("Text", 36.dp, Color(0xFF312E81), Color(0xFFA78BFA)),
  CAPTION("Caption", 36.dp, Color(0xFF1E3A8A), Color(0xFF60A5FA)),
  AUDIO("Audio", 36.dp, Color(0xFF064E3B), Color(0xFF34D399)),
  MUSIC("Music", 36.dp, Color(0xFF065F46), Color(0xFF10B981)),
  SFX("SFX", 36.dp, Color(0xFF047857), Color(0xFF6EE7B7)),
  EFFECT("Effect", 36.dp, Color(0xFF4C1D95), Color(0xFFC084FC)),
  FILTER("Filter", 36.dp, Color(0xFF701A75), Color(0xFFF472B6)),
  STICKER("Sticker", 36.dp, Color(0xFF78350F), Color(0xFFFBBF24)),
  ADJUSTMENT("Adjustment", 36.dp, Color(0xFF581C87), Color(0xFFA855F7)),
  ELEMENT("Element", 36.dp, Color(0xFF831843), Color(0xFFF43F5E))
}

/**
 * Normalized clip item representation inside a timeline lane.
 */
data class LaneClipItem(
  val id: String,
  val laneIndex: Int,
  val kind: LaneKind,
  val startMs: Long,
  val durationMs: Long,
  val title: String,
  val uri: String = "",
  val speed: Float = 1.0f,
  val volume: Float = 1.0f,
  val isMuted: Boolean = false,
  val isLocked: Boolean = false,
  val isHidden: Boolean = false,
  val rawClip: Any? = null
) {
  val endMs: Long
    get() = startMs + durationMs

  fun overlapsWith(otherStartMs: Long, otherDurationMs: Long): Boolean {
    val otherEndMs = otherStartMs + otherDurationMs
    return startMs < otherEndMs && otherStartMs < endMs
  }
}

/**
 * Represents a single vertical row (Lane) in the CapCut-style Multi-Track Timeline.
 */
data class TimelineLane(
  val trackId: String = "track_main_video",
  val trackType: com.example.domain.model.TrackType = com.example.domain.model.TrackType.MAIN_VIDEO,
  val laneIndex: Int,
  val kind: LaneKind,
  val heightDp: Dp,
  val label: String,
  val icon: ImageVector,
  val clips: List<LaneClipItem>,
  val isMainLane: Boolean
) {
  val hasClips: Boolean
    get() = clips.isNotEmpty()
}

/**
 * Principal NLE Multi-Track Dynamic Lane Allocation Engine.
 *
 * Enforces:
 * 1. Main Track Isolation (Lane 0 exclusively for main video/media clips, 60dp height, permanently locked).
 * 2. Permanent, fixed vertical ordering for every track type:
 *    - Main Media Track (Lane 0)
 *    - Overlay Track (Lane 1)
 *    - Text Track (Lane 2)
 *    - Effects Track (Lane 3)
 *    - Audio Track (Lane 4)
 *    - Sticker Track (Lane 5)
 *    - Additional sub-tracks (Lanes 6+)
 * 3. Stable Track IDs and zero track jumping.
 */
object TrackLaneManager {

  val MAIN_LANE_HEIGHT: Dp = 60.dp
  val SUB_LANE_HEIGHT: Dp = 36.dp
  val TRACK_VERTICAL_GAP: Dp = 6.dp // Exact 2mm (~6dp) visual separation gap

  /**
   * Computes the complete list of active timeline lanes from the current Timeline model
   * with stable, permanent vertical positions for every track.
   *
   * @param timeline The immutable Timeline snapshot.
   * @return A list of [TimelineLane]s in fixed vertical order starting with Main Video.
   */
  fun computeLanes(timeline: Timeline): List<TimelineLane> {
    val resultLanes = mutableListOf<TimelineLane>()

    // 1. Lane 0: Main Video Track (Strictly isolated at the top, vertically locked)
    val mainVideoClips = timeline.videoClips.map { clip ->
      LaneClipItem(
        id = clip.id,
        laneIndex = 0,
        kind = LaneKind.MAIN_VIDEO,
        startMs = clip.timelineStartMs,
        durationMs = clip.durationMs,
        title = clip.name.ifBlank { "Main Video" },
        uri = clip.uri,
        speed = clip.speed,
        volume = clip.volume,
        isMuted = clip.isMuted,
        isLocked = clip.isLocked,
        isHidden = clip.isHidden,
        rawClip = clip
      )
    }.sortedBy { it.startMs }

    resultLanes.add(
      TimelineLane(
        trackId = "track_main_video",
        trackType = com.example.domain.model.TrackType.MAIN_VIDEO,
        laneIndex = 0,
        kind = LaneKind.MAIN_VIDEO,
        heightDp = MAIN_LANE_HEIGHT,
        label = "Main Track",
        icon = Icons.Default.Movie,
        clips = mainVideoClips,
        isMainLane = true
      )
    )

    // 2. Fixed Vertical Position Tracks (Requirement 1 & 4)
    var currentSubLaneIndex = 1

    // A. Overlay Tracks (PIP, Elements, Layers)
    val overlayTrackIndices = timeline.overlayClips.map { it.trackIndex.coerceAtLeast(1) }.distinct().sorted()
    for (idx in overlayTrackIndices) {
      val clipsForTrack = timeline.overlayClips.filter { it.trackIndex.coerceAtLeast(1) == idx }.map { clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = currentSubLaneIndex,
          kind = LaneKind.OVERLAY,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.name.ifBlank { "Overlay $idx" },
          uri = clip.uri,
          speed = clip.speed,
          volume = clip.volume,
          isMuted = clip.isMuted,
          isLocked = clip.isLocked,
          isHidden = clip.isHidden,
          rawClip = clip
        )
      }.sortedBy { it.startMs }

      if (clipsForTrack.isNotEmpty()) {
        resultLanes.add(
          TimelineLane(
            trackId = "track_overlay_$idx",
            trackType = com.example.domain.model.TrackType.OVERLAY,
            laneIndex = currentSubLaneIndex++,
            kind = LaneKind.OVERLAY,
            heightDp = SUB_LANE_HEIGHT,
            label = if (overlayTrackIndices.size > 1) "Overlay $idx" else "Overlay",
            icon = Icons.Default.Layers,
            clips = clipsForTrack,
            isMainLane = false
          )
        )
      }
    }

    // B. Text Tracks (Titles, Captions, 3D Text)
    val textTrackIndices = timeline.textClips.map { it.trackIndex.coerceAtLeast(1) }.distinct().sorted()
    for (idx in textTrackIndices) {
      val clipsForTrack = timeline.textClips.filter { it.trackIndex.coerceAtLeast(1) == idx }.map { clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = currentSubLaneIndex,
          kind = LaneKind.TEXT,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.text.ifBlank { "Text $idx" },
          rawClip = clip
        )
      }.sortedBy { it.startMs }

      if (clipsForTrack.isNotEmpty()) {
        resultLanes.add(
          TimelineLane(
            trackId = "track_text_$idx",
            trackType = com.example.domain.model.TrackType.TEXT,
            laneIndex = currentSubLaneIndex++,
            kind = LaneKind.TEXT,
            heightDp = SUB_LANE_HEIGHT,
            label = if (textTrackIndices.size > 1) "Text $idx" else "Text",
            icon = Icons.Default.TextFields,
            clips = clipsForTrack,
            isMainLane = false
          )
        )
      }
    }

    // C. Sticker, Emoji, Shapes, Elements & Badges Tracks
    val stickerTrackIndices = timeline.stickerClips.map { it.trackIndex.coerceAtLeast(1) }.distinct().sorted()
    for (idx in stickerTrackIndices) {
      val clipsForTrack = timeline.stickerClips.filter { it.trackIndex.coerceAtLeast(1) == idx }.map { clip ->
        val itemTitle = when {
          clip.badgeType != null -> clip.badgeType.displayName
          clip.category.contains("Shape", ignoreCase = true) -> "Shape"
          clip.category.contains("Emoji", ignoreCase = true) -> clip.emojiOrAsset
          clip.elementCategory != null -> clip.elementCategory
          else -> clip.emojiOrAsset.ifBlank { "Sticker $idx" }
        }
        LaneClipItem(
          id = clip.id,
          laneIndex = currentSubLaneIndex,
          kind = LaneKind.STICKER,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = itemTitle,
          rawClip = clip
        )
      }.sortedBy { it.startMs }

      if (clipsForTrack.isNotEmpty()) {
        val firstClip = clipsForTrack.first()
        val firstSticker = firstClip.rawClip as? StickerClip
        val trackLabel = when {
          firstSticker?.category?.contains("Shape", ignoreCase = true) == true -> "Shape $idx"
          firstSticker?.category?.contains("Emoji", ignoreCase = true) == true -> "Emoji $idx"
          firstSticker?.badgeType != null -> "Badge $idx"
          firstSticker?.elementCategory != null -> "${firstSticker.elementCategory.replaceFirstChar { it.uppercase() }} $idx"
          stickerTrackIndices.size > 1 -> "Sticker $idx"
          else -> "Sticker"
        }
        resultLanes.add(
          TimelineLane(
            trackId = "track_sticker_$idx",
            trackType = com.example.domain.model.TrackType.STICKER,
            laneIndex = currentSubLaneIndex++,
            kind = LaneKind.STICKER,
            heightDp = SUB_LANE_HEIGHT,
            label = trackLabel,
            icon = Icons.Default.Face,
            clips = clipsForTrack,
            isMainLane = false
          )
        )
      }
    }

    // D. Effect & Filter Tracks
    val effectTrackIndices = timeline.effectClips.map { it.trackIndex.coerceAtLeast(1) }.distinct().sorted()
    for (idx in effectTrackIndices) {
      val clipsForTrack = timeline.effectClips.filter { it.trackIndex.coerceAtLeast(1) == idx }.map { clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = currentSubLaneIndex,
          kind = LaneKind.EFFECT,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.customName.ifBlank { clip.effectType.displayName },
          rawClip = clip
        )
      }.sortedBy { it.startMs }

      if (clipsForTrack.isNotEmpty()) {
        resultLanes.add(
          TimelineLane(
            trackId = "track_effect_$idx",
            trackType = com.example.domain.model.TrackType.EFFECT,
            laneIndex = currentSubLaneIndex++,
            kind = LaneKind.EFFECT,
            heightDp = SUB_LANE_HEIGHT,
            label = if (effectTrackIndices.size > 1) "Effect $idx" else "Effect",
            icon = Icons.Default.AutoAwesome,
            clips = clipsForTrack,
            isMainLane = false
          )
        )
      }
    }

    // E. Shapes & Callouts Tracks
    val shapeTrackIndices = timeline.shapeClips.map { it.trackIndex.coerceAtLeast(1) }.distinct().sorted()
    for (idx in shapeTrackIndices) {
      val clipsForTrack = timeline.shapeClips.filter { it.trackIndex.coerceAtLeast(1) == idx }.map { clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = currentSubLaneIndex,
          kind = LaneKind.ELEMENT,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = "Shape ${clip.shapeType.name.lowercase().replaceFirstChar { it.uppercase() }}",
          rawClip = clip
        )
      }.sortedBy { it.startMs }

      if (clipsForTrack.isNotEmpty()) {
        resultLanes.add(
          TimelineLane(
            trackId = "track_shape_$idx",
            trackType = com.example.domain.model.TrackType.SHAPE,
            laneIndex = currentSubLaneIndex++,
            kind = LaneKind.ELEMENT,
            heightDp = SUB_LANE_HEIGHT,
            label = if (shapeTrackIndices.size > 1) "Shape $idx" else "Shape",
            icon = Icons.Default.ColorLens,
            clips = clipsForTrack,
            isMainLane = false
          )
        )
      }
    }

    // F. Audio Tracks (Music, SFX, Voiceover)
    val audioTrackIndices = timeline.audioClips.map { it.trackIndex.coerceAtLeast(1) }.distinct().sorted()
    for (idx in audioTrackIndices) {
      val clipsForTrack = timeline.audioClips.filter { it.trackIndex.coerceAtLeast(1) == idx }.map { clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = currentSubLaneIndex,
          kind = LaneKind.AUDIO,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.title.ifBlank { if (clip.isVoiceOver) "Voiceover" else "Audio $idx" },
          uri = clip.uri,
          speed = clip.speed,
          volume = clip.volume,
          isMuted = clip.isMuted,
          isLocked = clip.isLocked,
          isHidden = clip.isHidden,
          rawClip = clip
        )
      }.sortedBy { it.startMs }

      if (clipsForTrack.isNotEmpty()) {
        val isVoiceOverTrack = clipsForTrack.any { (it.rawClip as? AudioClip)?.isVoiceOver == true }
        val label = when {
          isVoiceOverTrack && audioTrackIndices.size > 1 -> "Voiceover $idx"
          isVoiceOverTrack -> "Voiceover"
          audioTrackIndices.size > 1 -> "Audio $idx"
          else -> "Audio"
        }
        resultLanes.add(
          TimelineLane(
            trackId = "track_audio_$idx",
            trackType = com.example.domain.model.TrackType.AUDIO,
            laneIndex = currentSubLaneIndex++,
            kind = LaneKind.AUDIO,
            heightDp = SUB_LANE_HEIGHT,
            label = label,
            icon = Icons.Default.MusicNote,
            clips = clipsForTrack,
            isMainLane = false
          )
        )
      }
    }

    // Explicit tracks in timeline.tracks
    val explicitSubTracks = timeline.tracks.filter { it.trackType != com.example.domain.model.TrackType.MAIN_VIDEO }
    for (explicitTrack in explicitSubTracks) {
      val alreadyCovered = resultLanes.any { it.trackId == explicitTrack.trackId || (it.trackType == explicitTrack.trackType && it.label == explicitTrack.displayName) }
      if (!alreadyCovered) {
        val kind = when (explicitTrack.trackType) {
          com.example.domain.model.TrackType.MAIN_VIDEO -> LaneKind.MAIN_VIDEO
          com.example.domain.model.TrackType.OVERLAY, com.example.domain.model.TrackType.ELEMENT, com.example.domain.model.TrackType.ADJUSTMENT -> LaneKind.OVERLAY
          com.example.domain.model.TrackType.TEXT, com.example.domain.model.TrackType.CAPTION -> LaneKind.TEXT
          com.example.domain.model.TrackType.AUDIO, com.example.domain.model.TrackType.MUSIC, com.example.domain.model.TrackType.SFX -> LaneKind.AUDIO
          com.example.domain.model.TrackType.EFFECT -> LaneKind.EFFECT
          com.example.domain.model.TrackType.STICKER -> LaneKind.STICKER
          com.example.domain.model.TrackType.SHAPE -> LaneKind.ELEMENT
        }
        val icon = when (kind) {
          LaneKind.OVERLAY -> Icons.Default.Layers
          LaneKind.TEXT -> Icons.Default.TextFields
          LaneKind.AUDIO -> Icons.Default.MusicNote
          LaneKind.EFFECT -> Icons.Default.AutoAwesome
          LaneKind.STICKER -> Icons.Default.Face
          else -> Icons.Default.Movie
        }
        resultLanes.add(
          TimelineLane(
            trackId = explicitTrack.trackId,
            trackType = explicitTrack.trackType,
            laneIndex = currentSubLaneIndex++,
            kind = kind,
            heightDp = SUB_LANE_HEIGHT,
            label = explicitTrack.displayName.ifBlank { "${explicitTrack.trackType.name} $currentSubLaneIndex" },
            icon = icon,
            clips = emptyList(),
            isMainLane = false
          )
        )
      }
    }

    return resultLanes
  }

  /**
   * Partitions clips into non-overlapping sub-lanes.
   * Preserves separate user tracks (distinct laneIndex) without squashing independent tracks together.
   */
  private fun partitionClipsIntoLanes(
    clips: List<LaneClipItem>,
    kind: LaneKind,
    icon: ImageVector,
    baseLabel: String,
    startingLaneIndex: Int
  ): List<TimelineLane> {
    if (clips.isEmpty()) return emptyList()

    val distinctTracks = clips.map { it.laneIndex }.distinct().sorted()
    val lanes = mutableListOf<TimelineLane>()
    var currentLaneIdx = startingLaneIndex

    for (trackIdx in distinctTracks) {
      val trackClips = clips.filter { it.laneIndex == trackIdx }
        .sortedWith(compareBy({ it.startMs }, { -it.durationMs }))

      val label = if (distinctTracks.size > 1) "$baseLabel $trackIdx" else baseLabel
      val updatedClips = trackClips.map { it.copy(laneIndex = currentLaneIdx) }
      lanes.add(
        TimelineLane(
          laneIndex = currentLaneIdx,
          kind = kind,
          heightDp = SUB_LANE_HEIGHT,
          label = label,
          icon = icon,
          clips = updatedClips,
          isMainLane = false
        )
      )
      currentLaneIdx++
    }

    return lanes
  }

  /**
   * Re-indexes lanes so that all active lanes are strictly continuous (0, 1, 2, ... N).
   * Retains all tracks without dropping explicit tracks.
   */
  private fun recompactLanes(lanes: List<TimelineLane>): List<TimelineLane> {
    val compacted = mutableListOf<TimelineLane>()
    var nextIndex = 0

    for (lane in lanes) {
      val reindexedClips = lane.clips.map { it.copy(laneIndex = nextIndex) }
      compacted.add(
        lane.copy(
          laneIndex = nextIndex,
          clips = reindexedClips
        )
      )
      nextIndex++
    }

    return compacted
  }

  /**
   * Calculates the exact wrapped height for the timeline container with zero dead space.
   */
  fun calculateTotalTimelineHeight(lanes: List<TimelineLane>): Dp {
    if (lanes.isEmpty()) return MAIN_LANE_HEIGHT
    val totalLanesHeight = lanes.sumOf { if (it.isMainLane) MAIN_LANE_HEIGHT.value.toDouble() else SUB_LANE_HEIGHT.value.toDouble() }
    val totalGapsHeight = (lanes.size - 1).coerceAtLeast(0) * TRACK_VERTICAL_GAP.value.toDouble()
    return (totalLanesHeight + totalGapsHeight).dp
  }

  /**
   * Allocates a brand new sub-lane index directly beneath all existing tracks.
   */
  fun allocateNewSubLane(timeline: Timeline): Int {
    val activeLanes = computeLanes(timeline)
    return (activeLanes.maxOfOrNull { it.laneIndex } ?: 0) + 1
  }

  /**
   * Checks if placing a clip at [startMs] with [durationMs] collides with any clip in [laneClips].
   */
  fun checkCollision(
    laneClips: List<LaneClipItem>,
    startMs: Long,
    durationMs: Long,
    excludeClipId: String? = null
  ): Boolean {
    val targetEndMs = startMs + durationMs
    return laneClips.any { item ->
      if (item.id == excludeClipId) false
      else item.overlapsWith(startMs, durationMs)
    }
  }

  /**
   * Authoritative multi-track timeline evaluation.
   * Evaluates ALL active tracks (Track 0 Main, Track 1 PIP, Track 2, Track 3 ... up to Track N)
   * simultaneously at [timelinePosMs] without arbitrary hardcoded track limits.
   */
  fun evaluateTimelineAt(timeline: Timeline, timelinePosMs: Long): ActiveTracksSnapshot {
    val pos = timelinePosMs.coerceAtLeast(0L)

    // Track 0: Main Video Clip
    val mainClip = timeline.videoClips.firstOrNull { clip ->
      !clip.isHidden && pos >= clip.timelineStartMs && pos < (clip.timelineStartMs + clip.durationMs)
    }

    // Track 1..N: ALL Active Overlays / PIP clips across all sub-tracks (Dynamic unlimited)
    val activeOverlays = timeline.overlayClips
      .filter { clip -> !clip.isHidden && pos >= clip.timelineStartMs && pos < (clip.timelineStartMs + clip.durationMs) }
      .sortedBy { it.trackIndex } // Deterministic layer Z-stacking order

    // ALL Active Audio Clips across all audio tracks (Lane 1, 2, 3... N)
    val activeAudios = timeline.audioClips
      .filter { clip -> !clip.isHidden && !clip.isMuted && pos >= clip.timelineStartMs && pos < (clip.timelineStartMs + clip.durationMs) }
      .sortedBy { it.trackIndex }

    // ALL Active Text Clips across all text lanes
    val activeTexts = timeline.textClips
      .filter { clip -> pos >= clip.timelineStartMs && pos < (clip.timelineStartMs + clip.durationMs) }
      .sortedBy { it.trackIndex }

    // ALL Active Effect Clips
    val activeEffects = timeline.effectClips
      .filter { clip -> !clip.isHidden && pos >= clip.timelineStartMs && pos < (clip.timelineStartMs + clip.durationMs) }

    // ALL Active Sticker Clips
    val activeStickers = timeline.stickerClips
      .filter { clip -> !clip.isHidden && pos >= clip.timelineStartMs && pos < (clip.timelineStartMs + clip.durationMs) }

    val totalLayers = (if (mainClip != null) 1 else 0) +
      activeOverlays.size +
      activeAudios.size +
      activeTexts.size +
      activeEffects.size +
      activeStickers.size

    return ActiveTracksSnapshot(
      timestampMs = pos,
      mainVideoClip = mainClip,
      activeOverlays = activeOverlays,
      activeAudios = activeAudios,
      activeTexts = activeTexts,
      activeEffects = activeEffects,
      activeStickers = activeStickers,
      totalActiveLayersCount = totalLayers
    )
  }

  /**
   * Returns all clip items in the specified lane index.
   */
  fun getClipsInLane(timeline: Timeline, laneIndex: Int): List<LaneClipItem> {
    val lanes = computeLanes(timeline)
    return lanes.firstOrNull { it.laneIndex == laneIndex }?.clips ?: emptyList()
  }

  /**
   * Returns total count of all dynamically generated track lanes.
   * Never hardcapped by static limits.
   */
  fun getTotalLaneCount(timeline: Timeline): Int {
    return computeLanes(timeline).size
  }
}

/**
 * Result of evaluating all active clips across all tracks at a given timeline timestamp.
 */
data class ActiveTracksSnapshot(
  val timestampMs: Long,
  val mainVideoClip: VideoClip?,
  val activeOverlays: List<VideoClip>,
  val activeAudios: List<AudioClip>,
  val activeTexts: List<TextClip>,
  val activeEffects: List<EffectClip>,
  val activeStickers: List<StickerClip>,
  val totalActiveLayersCount: Int
)
