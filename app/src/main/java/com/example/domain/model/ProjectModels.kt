package com.example.domain.model

import java.util.UUID

/**
 * Global settings for a project configuration.
 */
data class ProjectSettings(
  val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
  val resolution: Resolution = Resolution.RES_1080P,
  val fps: FrameRate = FrameRate.FPS_30,
  val sampleRateHz: Int = 48000,
  val canvasBackgroundColor: Long = 0xFF000000,
  val totalDurationMs: Long = 0L
)

/**
 * Tracks a media file reference used across timeline clips.
 */
data class MediaReference(
  val id: String = UUID.randomUUID().toString(),
  val clipId: String,
  val uri: String,
  val originalPath: String = "",
  val filename: String = "",
  val fileSizeBytes: Long = 0L,
  val mediaType: String = "VIDEO", // "VIDEO", "AUDIO", "IMAGE"
  val mimeType: String = "video/mp4",
  val isMissing: Boolean = false
)

/**
 * Detailed technical metadata extracted from an imported media file.
 */
data class SourceMetadata(
  val clipId: String = "",
  val width: Int = 1920,
  val height: Int = 1080,
  val naturalRotation: Int = 0,
  val durationMs: Long = 0L,
  val frameRate: Float = 30f,
  val audioChannels: Int = 2,
  val audioSampleRate: Int = 48000,
  val videoCodec: String = "h264",
  val audioCodec: String = "aac",
  val fileSizeBytes: Long = 0L
)

/**
 * Information about a missing media file requiring user relinking.
 */
data class MissingMediaItem(
  val clipId: String,
  val clipName: String,
  val currentUri: String,
  val originalFilename: String,
  val mediaType: String, // "VIDEO", "AUDIO", "IMAGE"
  val trackType: String  // "Main Video", "Overlay", "Audio", "Chroma Background"
)

/**
 * Complete, self-contained project package for robust serialization and storage.
 */
data class ProjectPackage(
  val version: Int = 2,
  val projectId: String,
  val projectName: String,
  val createdAt: Long = System.currentTimeMillis(),
  val lastEditedAt: Long = System.currentTimeMillis(),
  val settings: ProjectSettings = ProjectSettings(),
  val mediaReferences: List<MediaReference> = emptyList(),
  val sourceMetadata: List<SourceMetadata> = emptyList(),
  val timeline: Timeline = Timeline(),
  val isDraft: Boolean = true
)
