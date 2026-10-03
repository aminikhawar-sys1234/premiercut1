package com.example.engine.timeline

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.example.domain.model.*
import com.example.engine.InterpolatedClipTransform
import com.example.engine.composition.*

/**
 * Authoritative NLE frame state evaluated at an exact timeline timestamp.
 * Serves as the single temporal contract for Preview, Audio, GPU, and Export pipelines.
 */
data class TimelineFrameState(
  val timestampMs: Long,
  val activeVideoClip: VideoClip?,
  val clipSourcePosMs: Long,
  val activeOverlays: List<ComposedOverlay>,
  val activeTexts: List<ComposedText>,
  val activeStickers: List<ComposedSticker>,
  val activeEffects: List<ComposedEffect>,
  val activeAudios: List<AudioClip>,
  val activeTransition: ComposedTransition?,
  val activeClipTransform: InterpolatedClipTransform?,
  val colorMatrix: ColorMatrix,
  val colorFilter: ColorMatrixColorFilter,
  val activeMasks: Map<String, MaskSettings> = emptyMap(),
  val trackVisibility: Map<TrackType, Boolean> = emptyMap(),
  val trackMute: Map<TrackType, Boolean> = emptyMap(),
  val trackSolo: Map<TrackType, Boolean> = emptyMap(),
  val zOrder: List<Int> = emptyList(),
  val activeKeyframes: Map<String, ClipKeyframe> = emptyMap()
) {
  fun toComposedFrame(): ComposedFrame = ComposedFrame(
    timelinePosMs = timestampMs,
    activeClip = activeVideoClip,
    clipSourcePosMs = clipSourcePosMs,
    activeOverlays = activeOverlays,
    activeTexts = activeTexts,
    activeStickers = activeStickers,
    activeTransition = activeTransition,
    activeEffects = activeEffects,
    colorMatrix = colorMatrix,
    colorFilter = colorFilter,
    activeClipTransform = activeClipTransform
  )
}
