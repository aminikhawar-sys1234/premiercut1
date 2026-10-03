package com.example.ui.components.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.domain.model.Timeline
import com.example.domain.model.TrackType
import com.example.domain.model.Transition
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.TextClip
import com.example.domain.model.AudioClip
import com.example.domain.model.EffectClip
import com.example.domain.model.StickerClip
import com.example.engine.SelectedTrackElement
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*

/**
 * Principal Production-Ready Multi-Track NLE Timeline Component.
 *
 * Implements:
 * 1. Main Track Isolation (Lane 0 at top, 60dp).
 * 2. True Dynamic Infinite Sub-Tracks (Lanes 1, 2, 3... at 36dp).
 * 3. Exact 2mm (~6dp) vertical track spacing.
 * 4. Zero Dead Space layout auto-wrapping active lane count.
 * 5. Centered CTI Playhead with real-time scrubbing and instant pause on tap.
 * 6. Dynamic Left Header Indicators that auto-collapse when sub-tracks are deleted.
 * 7. 2dp crisp white selection boundary and stretch handles.
 */
@Composable
fun MultiTrackTimeline(
  timeline: Timeline,
  currentPosMs: Long,
  zoom: Float,
  selectedElement: SelectedTrackElement,
  selectedClipIds: Set<String>,
  isMultiSelectMode: Boolean,
  snapIndicatorMs: Long?,
  onSeek: (Long) -> Unit,
  onSelectElement: (SelectedTrackElement) -> Unit,
  onToggleClipSelection: (String) -> Unit,
  onZoomChange: (Float) -> Unit,
  onMoveClip: (clipId: String, deltaMs: Long) -> Unit,
  onMoveClipStart: ((clipId: String) -> Unit)? = null,
  onMoveClipEnd: ((clipId: String) -> Unit)? = null,
  onTrimClipLeft: (clipId: String, deltaMs: Long) -> Unit,
  onTrimClipLeftStart: ((clipId: String) -> Unit)? = null,
  onTrimClipLeftEnd: ((clipId: String) -> Unit)? = null,
  onTrimClipRight: (clipId: String, deltaMs: Long) -> Unit,
  onTrimClipRightStart: ((clipId: String) -> Unit)? = null,
  onTrimClipRightEnd: ((clipId: String) -> Unit)? = null,
  onToggleTrackLock: (TrackType) -> Unit,
  onToggleTrackHide: (TrackType) -> Unit,
  onToggleTrackMute: (TrackType) -> Unit,
  onToggleTrackSolo: (TrackType) -> Unit,
  onCycleTrackHeight: (TrackType) -> Unit,
  onReorderVideoClips: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
  onOpenTrimTool: (() -> Unit)? = null,
  onOpenKeyframeTool: (() -> Unit)? = null,
  onOpenTransitionsTool: (() -> Unit)? = null,
  selectedTransitionCutIndex: Int = 0,
  onSelectTransitionCut: ((Int) -> Unit)? = null,
  draggedTransitionType: TransitionType? = null,
  onDropTransition: ((cutIndex: Int, type: TransitionType) -> Unit)? = null,
  onSplitClip: (() -> Unit)? = null,
  isPlaying: Boolean = false,
  onTogglePlayPause: (() -> Unit)? = null,
  onPausePlayback: (() -> Unit)? = null,
  onTrimLeftToPlayhead: (() -> Unit)? = null,
  onTrimRightToPlayhead: (() -> Unit)? = null,
  onDeleteClip: (() -> Unit)? = null,
  onRippleDelete: (() -> Unit)? = null,
  onNormalDelete: (() -> Unit)? = null,
  onDuplicateClip: (() -> Unit)? = null,
  onCopyClip: (() -> Unit)? = null,
  onPasteClip: (() -> Unit)? = null,
  onToggleMultiSelect: (() -> Unit)? = null,
  onNextPeak: (() -> Unit)? = null,
  onPrevPeak: (() -> Unit)? = null,
  onNextSilence: (() -> Unit)? = null,
  onPrevSilence: (() -> Unit)? = null,
  onRemoveSilence: (() -> Unit)? = null,
  waveformStyle: WaveformStyle = WaveformStyle.MIRRORED_BARS,
  onToggleWaveformStyle: (() -> Unit)? = null,
  fps: Int = 30,
  isFrameSnapping: Boolean = false,
  onStepFrames: ((Int) -> Unit)? = null,
  onSeekToPrevCut: (() -> Unit)? = null,
  onSeekToNextCut: (() -> Unit)? = null,
  onFpsChange: ((Int) -> Unit)? = null,
  onToggleFrameSnapping: (() -> Unit)? = null,
  showTrackHeaders: Boolean = true,
  selectedKeyframeIds: Set<String> = emptySet(),
  onSelectKeyframe: ((String) -> Unit)? = null,
  onMoveKeyframe: ((String, Long) -> Unit)? = null,
  onAddAudioKeyframe: ((clipId: String, relTimeMs: Long, volume: Float) -> Unit)? = null,
  onUpdateAudioKeyframe: ((clipId: String, keyframeId: String, relTimeMs: Long, volume: Float) -> Unit)? = null,
  onDeleteAudioKeyframe: ((clipId: String, keyframeId: String) -> Unit)? = null,
  onAddMedia: (() -> Unit)? = null,
  onAddAudio: (() -> Unit)? = null,
  onAddText: (() -> Unit)? = null,
  onAddOverlay: (() -> Unit)? = null,
  onAddSticker: (() -> Unit)? = null,
  onAddEffect: (() -> Unit)? = null,
  onEditCover: (() -> Unit)? = null,
  onToggleMuteAllVideo: (() -> Unit)? = null,
  isTracksSyncEnabled: Boolean = true,
  onToggleTracksSync: (() -> Unit)? = null,
  onMoveToPlayhead: (() -> Unit)? = null,
  onSplitAllTracks: (() -> Unit)? = null,
  onMoveTrack: ((TrackType, Int, Long) -> Unit)? = null,
  onScrubStart: () -> Unit = {},
  onScrubProgress: ((Long) -> Unit)? = null,
  onScrubStop: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val horizontalScrollState = rememberScrollState()
  val verticalScrollState = rememberScrollState()
  val coroutineScope = rememberCoroutineScope()
  val density = LocalDensity.current

  // 1. Dynamic Lane Allocation via TrackLaneManager
  val activeLanes = remember(timeline) { TrackLaneManager.computeLanes(timeline) }
  val mainLane = remember(activeLanes) { activeLanes.firstOrNull { it.isMainLane } ?: activeLanes.firstOrNull() }
  val secondaryLanes = remember(activeLanes) { activeLanes.filter { !it.isMainLane } }

  val totalDuration = timeline.totalDurationMs
  val msPerPixel = remember(zoom) { (20f / zoom).coerceIn(1.25f, 120f) }

  val maxTimelineMs = remember(timeline, totalDuration) {
    val durationOfClips = maxOf(
      totalDuration,
      timeline.videoClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
      timeline.overlayClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
      timeline.audioClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
      timeline.textClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
      timeline.stickerClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
      timeline.effectClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
    )
    if (durationOfClips > 0L) {
      durationOfClips + 10000L
    } else {
      10000L
    }
  }

  val trackContentWidthDp = (maxTimelineMs / msPerPixel).dp
  val computedTimelineHeightDp = remember(activeLanes) { TrackLaneManager.calculateTotalTimelineHeight(activeLanes) }

  var isMutedAll by remember { mutableStateOf(false) }

  // Touch Scrubbing & Drag gesture state
  var isTouchScrubbing by remember { mutableStateOf(false) }
  var isPinching by remember { mutableStateOf(false) }
  var scrubAccumulatorMs by remember { mutableFloatStateOf(currentPosMs.toFloat()) }
  var dragDirectionLocked by remember { mutableStateOf<Int?>(null) } // 1 = horizontal scrub, 2 = vertical scroll
  var isProgrammaticScroll by remember { mutableStateOf(false) }
  var pendingScrubTargetMs by remember { mutableStateOf<Long?>(null) }

  // Always-current values for gesture callbacks. pointerInput blocks (lane rows, ruler) are only
  // restarted on their own keys, so their closures can otherwise hold a stale msPerPixel/maxTimelineMs
  // after a pinch-zoom and convert the final scroll offset to the wrong time (playhead jump).
  val latestMsPerPixel by rememberUpdatedState(msPerPixel)
  val latestMaxTimelineMs by rememberUpdatedState(maxTimelineMs)
  val latestOnScrubProgress by rememberUpdatedState(onScrubProgress)
  val latestOnSeek by rememberUpdatedState(onSeek)
  val latestOnScrubStop by rememberUpdatedState(onScrubStop)

  // Ends a touch scrub: publishes the final needle time derived from the scroll offset, then stops scrubbing.
  fun finishTouchScrub() {
    val needleMs = (with(density) { horizontalScrollState.value.toDp() }.value * latestMsPerPixel)
      .toLong()
      .coerceIn(0L, latestMaxTimelineMs)
    // Remember where the needle was released so the playback->scroll sync does not snap the tracks
    // back to the stale pre-scrub position before the new time reaches currentPosMs.
    pendingScrubTargetMs = needleMs
    isTouchScrubbing = false
    val progress = latestOnScrubProgress
    if (progress != null) progress(needleMs) else latestOnSeek(needleMs)
    latestOnScrubStop()
  }

  LaunchedEffect(isPinching, zoom) {
    if (isPinching) {
      delay(1200)
      isPinching = false
    }
  }

  LaunchedEffect(currentPosMs) {
    if (!isTouchScrubbing) {
      scrubAccumulatorMs = currentPosMs.toFloat()
    }
  }

  // Re-run the sync when the scrollable extent changes (zoom / timeline length / first layout): scrollTo()
  // is clamped to the previous layout's maxValue, so without this the needle ends up at the wrong time after zoom.
  val scrollMaxValuePx = horizontalScrollState.maxValue

  // Keep tracks & timeline strictly synchronized with currentPosMs (moves tracks smoothly during video playback and seeking)
  LaunchedEffect(pendingScrubTargetMs) {
    if (pendingScrubTargetMs != null) {
      delay(400)
      pendingScrubTargetMs = null
    }
  }

  LaunchedEffect(currentPosMs, msPerPixel, density, isTouchScrubbing, isPlaying, scrollMaxValuePx, pendingScrubTargetMs) {
    val pending = pendingScrubTargetMs
    if (pending != null && kotlin.math.abs(currentPosMs - pending) > 80L) {
      // Waiting for the engine to publish the scrubbed position; keep the tracks where the user left them.
      return@LaunchedEffect
    }
    if (!isTouchScrubbing) {
      val safePosMs = currentPosMs.coerceAtLeast(0L)
      val targetScrollPx = with(density) { ((safePosMs / msPerPixel).dp).roundToPx() }.coerceAtLeast(0)
      if (kotlin.math.abs(horizontalScrollState.value - targetScrollPx) >= 1) {
        isProgrammaticScroll = true
        try {
          horizontalScrollState.scrollTo(targetScrollPx)
        } finally {
          isProgrammaticScroll = false
        }
      }
    }
  }

  // Handle user manual touch scrolling/scrubbing gestures ONLY (never fight playback engine while video is playing)
  LaunchedEffect(horizontalScrollState.value) {
    if (!isProgrammaticScroll && !isPlaying && isTouchScrubbing) {
      val calculatedPosMs = (with(density) { horizontalScrollState.value.toDp() }.value * msPerPixel)
        .toLong()
        .coerceIn(0L, maxTimelineMs)
      if (onScrubProgress != null) {
        onScrubProgress.invoke(calculatedPosMs)
      } else {
        onSeek(calculatedPosMs)
      }
    }
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .background(Color.Black)
  ) {
    val hasAnyTrack = activeLanes.any { it.hasClips } || timeline.videoClips.isNotEmpty()
    val timelineViewportWidthDp = maxWidth.coerceAtLeast(100.dp)
    val centerPaddingDp = timelineViewportWidthDp / 2
    val shift15mmDp = (15f * 160f / 25.4f).dp
    val ctiOffsetDp = (centerPaddingDp - shift15mmDp).coerceAtLeast(96.dp)
    val leftPaddingDp = ctiOffsetDp
    val rightPaddingDp = timelineViewportWidthDp - ctiOffsetDp

    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize()) {
        // 1. Timecode & Ruler Bar
        TimelineRulerHeader(
          hasAnyTrack = hasAnyTrack,
          isPlaying = isPlaying,
          currentPosMs = currentPosMs,
          totalDurationMs = timeline.totalDurationMs,
          maxTimelineMs = maxTimelineMs,
          msPerPixel = msPerPixel,
          zoom = zoom,
          onZoomChange = onZoomChange,
          onPinchStart = { isPinching = true },
          onPinchEnd = { isPinching = false },
          fps = fps,
          isFrameSnapping = isFrameSnapping,
          leftPaddingDp = leftPaddingDp,
          rightPaddingDp = rightPaddingDp,
          horizontalScrollState = horizontalScrollState,
          hasRightAddButton = false,
          onTogglePlayPause = onTogglePlayPause,
          onPausePlayback = onPausePlayback,
          onSeek = onSeek,
          onSeekToNextCut = onSeekToNextCut,
          onScrubStart = {
            isTouchScrubbing = true
            onScrubStart()
          },
          onScrubStop = { finishTouchScrub() },
          onAddMedia = onAddMedia,
          timeline = timeline
        )

        if (!hasAnyTrack) {
          TimelineEmptyView(
            onAddMedia = onAddMedia,
            modifier = Modifier.weight(1f).fillMaxWidth()
          )
        } else {
          // 2. Main Multi-Track Lanes Container (Permanently Locked Main Video Track + Scrollable Secondary Tracks)
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .pointerInput(totalDuration, msPerPixel, isFrameSnapping, fps, density) {
                detectDragGestures(
                  onDragStart = {
                    dragDirectionLocked = null
                    scrubAccumulatorMs = currentPosMs.toFloat()
                  },
                  onDragEnd = {
                    if (dragDirectionLocked == 1) {
                      finishTouchScrub()
                    }
                    dragDirectionLocked = null
                  },
                  onDragCancel = {
                    if (dragDirectionLocked == 1) {
                      finishTouchScrub()
                    }
                    dragDirectionLocked = null
                  },
                  onDrag = { change, dragAmount ->
                    if (dragDirectionLocked == null) {
                      val absX = kotlin.math.abs(dragAmount.x)
                      val absY = kotlin.math.abs(dragAmount.y)
                      if (absX > absY && absX > 2f) {
                        dragDirectionLocked = 1
                        isTouchScrubbing = true
                        onScrubStart()
                      } else if (absY > absX && absY > 2f) {
                        dragDirectionLocked = 2
                      }
                    }

                    // dispatchRawDelta applies synchronously; launching scrollBy would let the last
                    // delta(s) land AFTER drag-end and desync the needle from currentPosMs.
                    if (dragDirectionLocked == 1) {
                      change.consume()
                      horizontalScrollState.dispatchRawDelta(-dragAmount.x)
                    } else if (dragDirectionLocked == 2) {
                      change.consume()
                      verticalScrollState.dispatchRawDelta(-dragAmount.y)
                    }
                  }
                )
              }
              .pointerInput(zoom) {
                detectTransformGestures { _, _, zoomChange, _ ->
                  if (kotlin.math.abs(zoomChange - 1f) > 0.005f) {
                    isPinching = true
                    onZoomChange((zoom * zoomChange).coerceIn(0.25f, 8.0f))
                  }
                }
              }
          ) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)
            ) {
              Column(
                modifier = Modifier
                  .width(trackContentWidthDp + leftPaddingDp + rightPaddingDp)
                  .fillMaxHeight()
                  .pointerInput(maxTimelineMs, msPerPixel, density, ctiOffsetDp, isPlaying) {
                    detectTapGestures { offset ->
                      if (onPausePlayback != null) onPausePlayback.invoke() else if (isPlaying) onTogglePlayPause?.invoke()
                      val ctiOffsetPx = with(density) { ctiOffsetDp.toPx() }
                      val timePx = offset.x - ctiOffsetPx
                      val timeDp = timePx / density.density
                      val clickedMs = (timeDp * msPerPixel).toLong().coerceIn(0L, maxTimelineMs)
                      onSeek(clickedMs)
                    }
                  }
              ) {
                // 1. Master / Main Media Track (PERMANENTLY VERTICALLY LOCKED at top)
                if (mainLane != null) {
                  DynamicLaneRow(
                    lane = mainLane,
                    currentPosMs = currentPosMs,
                    msPerPixel = msPerPixel,
                    leftPaddingDp = leftPaddingDp,
                    maxTimelineMs = maxTimelineMs,
                    selectedElement = selectedElement,
                    selectedClipIds = selectedClipIds,
                    isMultiSelectMode = isMultiSelectMode,
                    isMutedAll = isMutedAll,
                    isPlaying = isPlaying,
                    onTogglePlayPause = onTogglePlayPause,
                    onPausePlayback = onPausePlayback,
                    onSelectElement = onSelectElement,
                    onToggleClipSelection = onToggleClipSelection,
                    onMoveClip = onMoveClip,
                    onMoveClipStart = onMoveClipStart,
                    onMoveClipEnd = onMoveClipEnd,
                    onTrimClipLeft = onTrimClipLeft,
                    onTrimClipLeftStart = onTrimClipLeftStart,
                    onTrimClipLeftEnd = onTrimClipLeftEnd,
                    onTrimClipRight = onTrimClipRight,
                    onTrimClipRightStart = onTrimClipRightStart,
                    onTrimClipRightEnd = onTrimClipRightEnd,
                    onSeek = onSeek,
                    onScrubStart = {
                      isTouchScrubbing = true
                      onScrubStart()
                    },
                    onScrubStop = { finishTouchScrub() },
                    onScrollTimeline = { delta ->
                      // Synchronous (no launch): a deferred coroutine could re-set the scrub flag after drag end.
                      horizontalScrollState.dispatchRawDelta(delta)
                    },
                    selectedKeyframeIds = selectedKeyframeIds,
                    onSelectKeyframe = onSelectKeyframe,
                    onMoveKeyframe = onMoveKeyframe,
                    onAddMedia = onAddMedia,
                    onAddText = onAddText,
                    onAddAudio = onAddAudio,
                    onAddOverlay = onAddOverlay,
                    onAddSticker = onAddSticker,
                    onAddEffect = onAddEffect,
                    onToggleTrackMute = onToggleTrackMute,
                    onToggleMuteAllVideo = onToggleMuteAllVideo,
                    onEditCover = onEditCover,
                    onMoveTrack = onMoveTrack
                  )
                }

                // 2. Secondary Tracks Viewport (Vertically Scrollable)
                if (secondaryLanes.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(TrackLaneManager.TRACK_VERTICAL_GAP))

                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .weight(1f)
                  ) {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(verticalScrollState),
                      verticalArrangement = Arrangement.spacedBy(TrackLaneManager.TRACK_VERTICAL_GAP)
                    ) {
                      for (lane in secondaryLanes) {
                        DynamicLaneRow(
                          lane = lane,
                          currentPosMs = currentPosMs,
                          msPerPixel = msPerPixel,
                          leftPaddingDp = leftPaddingDp,
                          maxTimelineMs = maxTimelineMs,
                          selectedElement = selectedElement,
                          selectedClipIds = selectedClipIds,
                          isMultiSelectMode = isMultiSelectMode,
                          isMutedAll = isMutedAll,
                          isPlaying = isPlaying,
                          onTogglePlayPause = onTogglePlayPause,
                          onPausePlayback = onPausePlayback,
                          onSelectElement = onSelectElement,
                          onToggleClipSelection = onToggleClipSelection,
                          onMoveClip = onMoveClip,
                          onMoveClipStart = onMoveClipStart,
                          onMoveClipEnd = onMoveClipEnd,
                          onTrimClipLeft = onTrimClipLeft,
                          onTrimClipLeftStart = onTrimClipLeftStart,
                          onTrimClipLeftEnd = onTrimClipLeftEnd,
                          onTrimClipRight = onTrimClipRight,
                          onTrimClipRightStart = onTrimClipRightStart,
                          onTrimClipRightEnd = onTrimClipRightEnd,
                          onSeek = onSeek,
                          onScrubStart = {
                            isTouchScrubbing = true
                            onScrubStart()
                          },
                          onScrubStop = { finishTouchScrub() },
                          onScrollTimeline = { delta ->
                            horizontalScrollState.dispatchRawDelta(delta)
                          },
                          onScrollVertical = { deltaY ->
                            verticalScrollState.dispatchRawDelta(deltaY)
                          },
                          selectedKeyframeIds = selectedKeyframeIds,
                          onSelectKeyframe = onSelectKeyframe,
                          onMoveKeyframe = onMoveKeyframe,
                          onAddMedia = onAddMedia,
                          onAddText = onAddText,
                          onAddAudio = onAddAudio,
                          onAddOverlay = onAddOverlay,
                          onAddSticker = onAddSticker,
                          onAddEffect = onAddEffect,
                          onToggleTrackMute = onToggleTrackMute,
                          onToggleMuteAllVideo = onToggleMuteAllVideo,
                          onEditCover = onEditCover,
                          onMoveTrack = onMoveTrack
                        )
                      }
                      Spacer(modifier = Modifier.height(56.dp))
                    }
                  }
                }
              }
            }
          }
        }
      }

      // ==========================================
      // FIXED CENTER CTI OVERLAY (Stationed Center Playhead)
      // ==========================================
      if (hasAnyTrack) {
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxSize()
        ) {
          // Vertical Center Playhead Line stationed at leftPaddingDp
          Box(
            modifier = Modifier
              .offset(x = leftPaddingDp)
              .fillMaxHeight()
              .width(2.5.dp)
              .background(
                Brush.verticalGradient(
                  listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF007AFF),
                    Color(0xFF0052CC)
                  )
                )
              )
              .testTag("fixed_center_playhead_line")
          )

          // CTI Top Needle Cap stationed at leftPaddingDp
          Box(
            modifier = Modifier
              .offset(x = leftPaddingDp - 5.25.dp, y = 0.dp)
              .width(13.dp)
              .height(18.dp)
              .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp, topStart = 3.dp, topEnd = 3.dp))
              .background(Color(0xFF0A0D14))
              .border(
                width = 1.25.dp,
                color = Color(0xFF0088FF),
                shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp, topStart = 3.dp, topEnd = 3.dp)
              )
              .testTag("fixed_center_playhead_cap"),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .background(Color(0xFF00E5FF))
            )
          }

          // Fixed Center Playhead
        }
      }
    }
  }
}

/**
 * Renders a single horizontal dynamic lane row.
 * Both the attached Track Header and Track Content (clips) share the exact same horizontal coordinate mapping.
 */
@Composable
private fun DynamicLaneRow(
  lane: TimelineLane,
  currentPosMs: Long,
  msPerPixel: Float,
  leftPaddingDp: Dp,
  maxTimelineMs: Long = 1000L,
  selectedElement: SelectedTrackElement,
  selectedClipIds: Set<String>,
  isMultiSelectMode: Boolean,
  isMutedAll: Boolean,
  isPlaying: Boolean,
  onTogglePlayPause: (() -> Unit)?,
  onPausePlayback: (() -> Unit)? = null,
  onSelectElement: (SelectedTrackElement) -> Unit,
  onToggleClipSelection: (String) -> Unit,
  onMoveClip: (clipId: String, deltaMs: Long) -> Unit,
  onMoveClipStart: ((clipId: String) -> Unit)?,
  onMoveClipEnd: ((clipId: String) -> Unit)?,
  onTrimClipLeft: (clipId: String, deltaMs: Long) -> Unit,
  onTrimClipLeftStart: ((clipId: String) -> Unit)?,
  onTrimClipLeftEnd: ((clipId: String) -> Unit)?,
  onTrimClipRight: (clipId: String, deltaMs: Long) -> Unit,
  onTrimClipRightStart: ((clipId: String) -> Unit)?,
  onTrimClipRightEnd: ((clipId: String) -> Unit)?,
  onSeek: (Long) -> Unit,
  onScrubStart: () -> Unit,
  onScrubStop: () -> Unit,
  selectedKeyframeIds: Set<String>,
  onSelectKeyframe: ((String) -> Unit)?,
  onMoveKeyframe: ((String, Long) -> Unit)?,
  onAddMedia: (() -> Unit)?,
  onAddText: (() -> Unit)?,
  onAddAudio: (() -> Unit)?,
  onAddOverlay: (() -> Unit)?,
  onAddSticker: (() -> Unit)?,
  onAddEffect: (() -> Unit)?,
  onToggleTrackMute: ((TrackType) -> Unit)? = null,
  onToggleMuteAllVideo: (() -> Unit)? = null,
  onEditCover: (() -> Unit)? = null,
  onMoveTrack: ((TrackType, Int, Long) -> Unit)? = null,
  onScrollTimeline: ((Float) -> Unit)? = null,
  onScrollVertical: ((Float) -> Unit)? = null
) {
  val laneHeight = lane.heightDp
  val headerWidth = 84.dp
  val headerMarginEnd = 8.dp
  val trackStartMs = lane.clips.minOfOrNull { it.startMs } ?: 0L
  val maxHeaderStartX = (leftPaddingDp - headerWidth - headerMarginEnd).coerceAtLeast(0.dp)
  val rawHeaderX = leftPaddingDp + (trackStartMs / msPerPixel).dp - headerWidth - headerMarginEnd
  val headerStartX = rawHeaderX.coerceAtMost(maxHeaderStartX).coerceAtLeast(0.dp)
  val density = LocalDensity.current
  var trackDragAccumulator by remember(lane.trackId) { mutableFloatStateOf(0f) }

  val laneTapModifier = Modifier.pointerInput(lane.trackId, msPerPixel, density, leftPaddingDp, maxTimelineMs, isPlaying) {
    detectTapGestures(
      onTap = { offset ->
        if (onPausePlayback != null) onPausePlayback.invoke() else if (isPlaying) onTogglePlayPause?.invoke()
        val leftPaddingPx = with(density) { leftPaddingDp.toPx() }
        val timePx = offset.x - leftPaddingPx
        val timeDp = timePx / density.density
        val clickedMs = (timeDp * msPerPixel).toLong().coerceIn(0L, maxTimelineMs)
        onSeek(clickedMs)
      }
    )
  }

  val laneScrollModifier = Modifier.pointerInput(lane.trackId) {
    var dragDirection: Int? = null // 1 = horizontal, 2 = vertical
    detectDragGestures(
      onDragStart = {
        if (onPausePlayback != null) onPausePlayback.invoke() else if (isPlaying) onTogglePlayPause?.invoke()
        dragDirection = null
      },
      onDragEnd = {
        if (dragDirection == 1) onScrubStop()
        dragDirection = null
      },
      onDragCancel = {
        if (dragDirection == 1) onScrubStop()
        dragDirection = null
      },
      onDrag = { change, dragAmount ->
        if (dragDirection == null) {
          val absX = kotlin.math.abs(dragAmount.x)
          val absY = kotlin.math.abs(dragAmount.y)
          if (absX > absY && absX > 2f) {
            dragDirection = 1
            onScrubStart()
          } else if (absY > absX && absY > 2f) {
            dragDirection = 2
          }
        }

        if (dragDirection == 1) {
          change.consume()
          if (onScrollTimeline != null) {
            onScrollTimeline.invoke(-dragAmount.x)
          } else {
            val deltaMs = (-dragAmount.x / density.density * msPerPixel).toLong()
            if (deltaMs != 0L) {
              onSeek((currentPosMs + deltaMs).coerceIn(0L, maxTimelineMs))
            }
          }
        } else if (dragDirection == 2) {
          change.consume()
          if (onScrollVertical != null) {
            onScrollVertical.invoke(-dragAmount.y)
          }
        }
      }
    )
  }

  val trackDragModifier = Modifier.pointerInput(lane.trackId, msPerPixel, density) {
    detectHorizontalDragGestures(
      onDragStart = { trackDragAccumulator = 0f },
      onDragEnd = { trackDragAccumulator = 0f },
      onDragCancel = { trackDragAccumulator = 0f },
      onHorizontalDrag = { change, dragAmount ->
        change.consume()
        val dragAmountDp = dragAmount / density.density
        trackDragAccumulator += dragAmountDp
        val deltaMs = (trackDragAccumulator * msPerPixel).toLong()
        if (kotlin.math.abs(deltaMs) >= 15L) {
          onMoveTrack?.invoke(lane.trackType, lane.laneIndex, deltaMs)
          trackDragAccumulator = 0f
        }
      }
    )
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(laneHeight)
      .then(laneScrollModifier)
      .then(laneTapModifier)
      .testTag("timeline_lane_${lane.laneIndex}_${lane.kind.name.lowercase()}")
  ) {
    // 1. Attached Track Header (Synchronized horizontally with this track)
    if (lane.isMainLane) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF161A23),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
        modifier = Modifier
          .offset(x = headerStartX)
          .width(headerWidth)
          .height(laneHeight)
          .testTag("track_header_main")
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          // Pinned Media Track Lock Badge
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF00E5FF).copy(alpha = 0.15f),
            border = BorderStroke(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
            modifier = Modifier.padding(end = 2.dp)
          ) {
            Text(
              text = "🔒",
              fontSize = 9.sp,
              modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
            )
          }
          // Mute all video clip toggle
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(6.dp))
              .clickable { onToggleMuteAllVideo?.invoke() }
              .testTag("mute_clip_btn"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = if (isMutedAll) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
              contentDescription = "Mute clip",
              tint = if (isMutedAll) RedAccent else Color.White.copy(alpha = 0.85f),
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = if (isMutedAll) "Muted" else "Mute\nclip",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
              )
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          // Cover thumbnail button
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF222630),
            border = BorderStroke(1.dp, Color(0xFF333A4A)),
            modifier = Modifier
              .width(36.dp)
              .fillMaxHeight()
              .clickable { onEditCover?.invoke() }
              .testTag("cover_thumbnail_btn")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = "Cover",
                  tint = Color.White.copy(alpha = 0.85f),
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = "Cover",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                )
              }
            }
          }
        }
      }
    } else {
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF181C26),
        border = BorderStroke(1.dp, lane.kind.accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
          .offset(x = headerStartX)
          .width(headerWidth)
          .height(laneHeight)
          .then(trackDragModifier)
          .testTag("track_header_${lane.laneIndex}_${lane.kind.name.lowercase()}")
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = lane.icon,
              contentDescription = lane.label,
              tint = lane.kind.accentColor,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = lane.label,
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = lane.kind.accentColor
              ),
              maxLines = 1,
              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
          }

          if (lane.kind == LaneKind.AUDIO || lane.kind == LaneKind.MUSIC || lane.kind == LaneKind.SFX) {
            val isLaneMuted = lane.clips.isNotEmpty() && lane.clips.all { it.isMuted }
            IconButton(
              onClick = { onToggleTrackMute?.invoke(TrackType.AUDIO) },
              modifier = Modifier.size(20.dp).testTag("track_mute_${lane.laneIndex}_btn")
            ) {
              Icon(
                imageVector = if (isLaneMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Mute track",
                tint = if (isLaneMuted) RedAccent else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
              )
            }
          }
        }
      }
    }

    // 2. Track Content (Clips)
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .offset(x = leftPaddingDp)
    ) {
      // Lane Clips Rendering
      for (clip in lane.clips) {
        val isSelected = when (lane.kind) {
          LaneKind.MAIN_VIDEO -> (selectedElement as? SelectedTrackElement.Video)?.clipId == clip.id
          LaneKind.OVERLAY -> (selectedElement as? SelectedTrackElement.Overlay)?.clipId == clip.id
          LaneKind.TEXT, LaneKind.CAPTION -> (selectedElement as? SelectedTrackElement.Text)?.clipId == clip.id
          LaneKind.AUDIO, LaneKind.MUSIC, LaneKind.SFX -> (selectedElement as? SelectedTrackElement.Audio)?.clipId == clip.id
          LaneKind.EFFECT, LaneKind.ADJUSTMENT -> (selectedElement as? SelectedTrackElement.Effect)?.clipId == clip.id
          LaneKind.FILTER -> false
          LaneKind.STICKER, LaneKind.ELEMENT -> (selectedElement as? SelectedTrackElement.Sticker)?.clipId == clip.id
        }
        val isMulti = clip.id in selectedClipIds

        TimelineClipView(
          clipId = clip.id,
          title = clip.title,
          timelineStartMs = clip.startMs,
          durationMs = clip.durationMs,
          sourceStartMs = (clip.rawClip as? VideoClip)?.sourceStartMs ?: (clip.rawClip as? AudioClip)?.sourceStartMs ?: 0L,
          sourceEndMs = (clip.rawClip as? VideoClip)?.sourceEndMs ?: (clip.rawClip as? AudioClip)?.sourceEndMs ?: clip.durationMs,
          currentPlayheadMs = currentPosMs,
          hasAudio = (clip.rawClip as? VideoClip)?.hasAudio == true || lane.kind == LaneKind.AUDIO,
          isMuted = clip.isMuted || (lane.isMainLane && isMutedAll),
          trackColor = lane.kind.accentColor,
          heightDp = laneHeight,
          msPerPixel = msPerPixel,
          isSelected = isSelected,
          isMultiSelected = isMulti,
          isLocked = clip.isLocked,
          speed = clip.speed,
          isReversed = (clip.rawClip as? VideoClip)?.isReversed ?: false,
          isFreeze = false,
          keyframes = (clip.rawClip as? VideoClip)?.keyframes ?: (clip.rawClip as? TextClip)?.keyframes ?: (clip.rawClip as? AudioClip)?.keyframes ?: emptyList(),
          selectedKeyframeIds = selectedKeyframeIds,
          onSelectKeyframe = onSelectKeyframe,
          onMoveKeyframe = onMoveKeyframe,
          onSelect = {
            if (onPausePlayback != null) onPausePlayback.invoke() else if (isPlaying) onTogglePlayPause?.invoke()
            if (isMultiSelectMode) {
              onToggleClipSelection(clip.id)
            } else {
              val element = when (lane.kind) {
                LaneKind.MAIN_VIDEO -> SelectedTrackElement.Video(clip.id)
                LaneKind.OVERLAY -> SelectedTrackElement.Overlay(clip.id)
                LaneKind.TEXT, LaneKind.CAPTION -> SelectedTrackElement.Text(clip.id)
                LaneKind.AUDIO, LaneKind.MUSIC, LaneKind.SFX -> SelectedTrackElement.Audio(clip.id)
                LaneKind.EFFECT, LaneKind.ADJUSTMENT -> SelectedTrackElement.Effect(clip.id)
                LaneKind.FILTER -> SelectedTrackElement.None
                LaneKind.STICKER, LaneKind.ELEMENT -> SelectedTrackElement.Sticker(clip.id)
              }
              onSelectElement(element)
            }
          },
          onPausePlayback = onPausePlayback ?: { if (isPlaying) onTogglePlayPause?.invoke() ?: Unit },
          onSeekToPosition = { posMs -> onSeek(posMs) },
          onLongClick = { onToggleClipSelection(clip.id) },
          onMoveClip = { delta -> onMoveClip(clip.id, delta) },
          onScrollTimeline = onScrollTimeline,
          onScrollVertical = onScrollVertical,
          onScrub = { delta -> onSeek((currentPosMs + delta).coerceAtLeast(0L)) },
          onScrubStart = onScrubStart,
          onScrubStop = onScrubStop,
          onMoveClipStart = { onMoveClipStart?.invoke(clip.id) },
          onMoveClipEnd = { onMoveClipEnd?.invoke(clip.id) },
          onTrimLeft = { delta -> onTrimClipLeft(clip.id, delta) },
          onTrimLeftStart = { onTrimClipLeftStart?.invoke(clip.id) },
          onTrimLeftEnd = { onTrimClipLeftEnd?.invoke(clip.id) },
          onTrimRight = { delta -> onTrimClipRight(clip.id, delta) },
          onTrimRightStart = { onTrimClipRightStart?.invoke(clip.id) },
          onTrimRightEnd = { onTrimClipRightEnd?.invoke(clip.id) },
          isVideoClip = lane.isMainLane || (lane.kind == LaneKind.OVERLAY && (clip.rawClip as? VideoClip)?.isVideo == true),
          uri = clip.uri,
          isVideo = (clip.rawClip as? VideoClip)?.isVideo ?: false,
          naturalRotation = (clip.rawClip as? VideoClip)?.let {
            if (it.naturalRotation != 0) it.naturalRotation else it.rotationDegrees
          } ?: 0
        )
      }

      // 2. Add Element Quick Pill for active secondary lane
      if (!lane.isMainLane && lane.clips.isNotEmpty()) {
        val maxEndMs = lane.clips.maxOfOrNull { it.endMs } ?: 0L
        val addOffset = (maxEndMs / msPerPixel).dp + 8.dp

        val onAddAction = when (lane.kind) {
          LaneKind.TEXT -> onAddText
          LaneKind.AUDIO -> onAddAudio
          LaneKind.OVERLAY -> onAddOverlay
          LaneKind.STICKER -> onAddSticker
          LaneKind.EFFECT -> onAddEffect
          else -> null
        }

        if (onAddAction != null) {
          Box(
            modifier = Modifier
              .offset(x = addOffset)
              .align(Alignment.CenterStart)
          ) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFF1E222D),
              border = BorderStroke(1.dp, Color(0xFF2D3344)),
              modifier = Modifier
                .width(115.dp)
                .height(30.dp)
                .clickable { onAddAction.invoke() }
                .testTag("add_lane_${lane.laneIndex}_pill_btn")
            ) {
              Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Add ${lane.kind.displayName.lowercase()}",
                  style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 11.sp),
                  maxLines = 1
                )
              }
            }
          }
        }
      }

      // 3. Add Element Quick Pill for empty explicit secondary lane
      if (!lane.isMainLane && lane.clips.isEmpty()) {
        val onAddAction = when (lane.kind) {
          LaneKind.TEXT -> onAddText
          LaneKind.AUDIO -> onAddAudio
          LaneKind.OVERLAY -> onAddOverlay
          LaneKind.STICKER -> onAddSticker
          LaneKind.EFFECT -> onAddEffect
          else -> null
        }

        if (onAddAction != null) {
          Box(
            modifier = Modifier
              .offset(x = 8.dp)
              .align(Alignment.CenterStart)
          ) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFF1E222D).copy(alpha = 0.7f),
              border = BorderStroke(1.dp, Color(0xFF2D3344)),
              modifier = Modifier
                .width(115.dp)
                .height(30.dp)
                .clickable { onAddAction.invoke() }
                .testTag("add_lane_${lane.laneIndex}_empty_pill_btn")
            ) {
              Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Add ${lane.kind.displayName.lowercase()}",
                  style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 11.sp),
                  maxLines = 1
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TimelineRulerHeader(
  hasAnyTrack: Boolean,
  isPlaying: Boolean,
  currentPosMs: Long,
  totalDurationMs: Long,
  maxTimelineMs: Long,
  msPerPixel: Float,
  zoom: Float,
  onZoomChange: (Float) -> Unit,
  onPinchStart: () -> Unit,
  onPinchEnd: () -> Unit,
  fps: Int,
  isFrameSnapping: Boolean,
  leftPaddingDp: Dp,
  rightPaddingDp: Dp,
  horizontalScrollState: androidx.compose.foundation.ScrollState,
  hasRightAddButton: Boolean,
  onTogglePlayPause: (() -> Unit)?,
  onPausePlayback: (() -> Unit)? = null,
  onSeek: (Long) -> Unit,
  onSeekToNextCut: (() -> Unit)?,
  onScrubStart: () -> Unit = {},
  onScrubStop: () -> Unit = {},
  onAddMedia: (() -> Unit)? = null,
  timeline: Timeline? = null,
  modifier: Modifier = Modifier
) {
  val hasVideoClips = remember(timeline) {
    timeline?.videoClips?.isNotEmpty() == true || timeline?.overlayClips?.any { it.isVideo } == true
  }
  val trackContentWidthDp = (maxTimelineMs / msPerPixel).dp
  val totalRulerWidthDp = trackContentWidthDp + leftPaddingDp + rightPaddingDp

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(if (hasVideoClips) 54.dp else 34.dp)
      .background(Color.Black)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(zoom) {
          detectTransformGestures { _, _, zoomChange, _ ->
            if (kotlin.math.abs(zoomChange - 1f) > 0.005f) {
              onPinchStart()
              onZoomChange((zoom * zoomChange).coerceIn(0.25f, 8.0f))
            }
          }
        }
        .horizontalScroll(horizontalScrollState)
    ) {
      Row(
        modifier = Modifier
          .width(totalRulerWidthDp)
          .fillMaxHeight()
      ) {
        Spacer(modifier = Modifier.width(leftPaddingDp))
        AccurateTimecodeRuler(
          totalDurationMs = maxTimelineMs,
          currentPosMs = currentPosMs,
          msPerPixel = msPerPixel,
          fps = fps,
          isFrameSnapping = isFrameSnapping,
          onSeek = onSeek,
          onDoubleTapSnap = onSeekToNextCut,
          onScrubStart = onScrubStart,
          onScrubStop = onScrubStop,
          onPausePlayback = onPausePlayback,
          timeline = timeline,
          onScrubDelta = { deltaPx -> horizontalScrollState.dispatchRawDelta(deltaPx) }
        )
        Spacer(modifier = Modifier.width(rightPaddingDp))
      }
    }

    // Floating Timecode display
    Surface(
      shape = RoundedCornerShape(4.dp),
      color = Color(0xFF141720).copy(alpha = 0.88f),
      border = BorderStroke(0.5.dp, Color(0xFF282F3F)),
      modifier = Modifier
        .padding(start = 8.dp, top = 4.dp)
        .testTag("timeline_timecode_text")
    ) {
      Text(
        text = if (hasAnyTrack) formatDurationShort(currentPosMs) else "00:00",
        style = MaterialTheme.typography.bodySmall.copy(
          color = Color.White.copy(alpha = 0.9f),
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        ),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1
      )
    }
  }
}

@Composable
private fun TimelineEmptyView(
  onAddMedia: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .background(Color(0xFF090B10)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(24.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = Color(0xFF0066FF).copy(alpha = 0.15f),
        border = BorderStroke(1.5.dp, Color(0xFF007AFF)),
        modifier = Modifier
          .size(56.dp)
          .clickable { onAddMedia?.invoke() }
          .testTag("empty_timeline_add_media_btn")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Media",
            tint = Color(0xFF0088FF),
            modifier = Modifier.size(30.dp)
          )
        }
      }
      Text(
        text = "Tap + to add your first video or photo",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = Color.White.copy(alpha = 0.85f),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium
        )
      )
    }
  }
}
