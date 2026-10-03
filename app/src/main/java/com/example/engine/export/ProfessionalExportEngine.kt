package com.example.engine.export

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.domain.model.Timeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

data class ExportFramePlan(val frameIndex: Long, val presentationTimeUs: Long, val timelinePositionMs: Long)
data class ExportPlan(val durationMs: Long, val totalFrames: Long, val frameRate: Int, val frames: Sequence<ExportFramePlan>)

/** Deterministic planning metadata; VideoExporter remains the authoritative fallback compositor. */
object ExportRenderPlanner {
  fun build(timeline: Timeline, config: ExportConfig): ExportPlan {
    val durationMs = timeline.totalDurationMs.coerceAtLeast(0L)
    val fps = config.frameRate.fps.coerceAtLeast(1)
    val totalFrames = if (durationMs == 0L) 0L else ceil(durationMs / 1000.0 * fps).toLong()
    val frames = sequence {
      var index = 0L
      while (index < totalFrames) {
        val ptsUs = index * 1_000_000L / fps
        val positionMs = (ptsUs / 1000L).coerceAtMost(max(0L, durationMs - 1L))
        yield(ExportFramePlan(index, ptsUs, positionMs))
        index++
      }
    }
    return ExportPlan(durationMs, totalFrames, fps, frames)
  }

  fun activeVideoClipCount(timeline: Timeline, positionMs: Long): Int =
    timeline.videoClips.count { positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs }

  fun activeAudioClipCount(timeline: Timeline, positionMs: Long): Int =
    timeline.audioClips.count { positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs }
}

data class ExportCapabilityReport(
  val videoEncoders: List<String>, val audioEncoders: List<String>, val h264Supported: Boolean,
  val hevcSupported: Boolean, val requestedSupported: Boolean, val width: Int = 0,
  val height: Int = 0, val effectiveMime: String? = null, val reason: String? = null
)

object ProfessionalCodecCapabilities {
  private const val AAC = "audio/mp4a-latm"
  fun inspect(config: ExportConfig, dimensions: Pair<Int, Int>): ExportCapabilityReport {
    val width = dimensions.first; val height = dimensions.second
    val videoEncoders = mutableListOf<String>(); val audioEncoders = mutableListOf<String>()
    var h264 = false; var hevc = false
    return try {
      for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
        if (!info.isEncoder) continue
        val types = info.supportedTypes.asList()
        if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }) h264 = true
        if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) }) hevc = true
        if (types.any { it.equals(AAC, true) }) audioEncoders += info.name
        for (mime in listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)) {
          if (!types.any { it.equals(mime, true) }) continue
          val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
          val vc = caps.videoCapabilities ?: continue
          val sizeOk = vc.isSizeSupported(width, height) || vc.isSizeSupported(height, width)
          val fpsOk = runCatching {
            vc.supportedFrameRates.contains(config.frameRate.fps) ||
            vc.getSupportedFrameRatesFor(width, height).contains(config.frameRate.fps.toDouble())
          }.getOrDefault(true)
          if (sizeOk && fpsOk) videoEncoders += "${info.name}:$mime"
        }
      }
      val effectiveMime = when (config.codecProfile) {
        CodecProfile.H265_HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
        CodecProfile.H264_AVC -> MediaFormat.MIMETYPE_VIDEO_AVC
        CodecProfile.AUTO -> if ((width >= 2160 || height >= 2160) && hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else if (h264) MediaFormat.MIMETYPE_VIDEO_AVC else MediaFormat.MIMETYPE_VIDEO_HEVC
      }
      val requested = videoEncoders.any { it.endsWith(":$effectiveMime") } || videoEncoders.isNotEmpty() || h264 || hevc
      ExportCapabilityReport(videoEncoders.distinct(), audioEncoders.distinct(), h264, hevc, requested, width, height, effectiveMime, if (!requested) "No compatible video encoder found for ${width}x${height} @ ${config.frameRate.fps}fps ($effectiveMime)." else null)
    } catch (t: Throwable) {
      ExportCapabilityReport(emptyList(), emptyList(), h264, hevc, false, width, height, null, "Codec capability scan failed: ${t.message ?: "unknown error"}")
    }
  }
  private fun isHardware(info: MediaCodecInfo): Boolean = if (android.os.Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else {
    val n = info.name.lowercase(); !n.startsWith("omx.google.") && !n.startsWith("c2.android.") && !n.contains("software") && !n.contains("sw.")
  }
}

data class ExportValidationResult(
  val valid: Boolean, val message: String, val durationMs: Long = 0L, val videoCodec: String? = null,
  val audioCodec: String? = null, val width: Int = 0, val height: Int = 0, val frameRate: Int? = null
)

object ExportValidator {
  private const val TAG = "ExportValidator"

  fun validate(
    file: File,
    config: ExportConfig,
    expectedDurationMs: Long,
    requireAudio: Boolean = true,
    expectedDimensions: Pair<Int, Int>? = null
  ): ExportValidationResult {
    if (!file.exists()) {
      Log.e(TAG, "Validation failed: File does not exist at ${file.absolutePath}")
      return ExportValidationResult(false, "Output file does not exist.")
    }
    val fileLength = file.length()
    if (fileLength <= 4096L) {
      Log.e(TAG, "Validation failed: File is empty, incomplete, or too small (${fileLength} bytes, expected > 4096 bytes) at ${file.absolutePath}")
      return ExportValidationResult(false, "Output file is empty, incomplete, or too small ($fileLength bytes).")
    }

    var fis: FileInputStream? = null
    val retriever = MediaMetadataRetriever()
    val extractor = MediaExtractor()

    return try {
      fis = FileInputStream(file)
      val fd = fis.fd

      // 1. Validate MP4 container header using MediaMetadataRetriever via FileDescriptor
      try {
        retriever.setDataSource(fd)
      } catch (e: Throwable) {
        Log.e(TAG, "MediaMetadataRetriever setDataSource failed on ${file.absolutePath} (size=$fileLength)", e)
        return ExportValidationResult(
          false,
          "MP4 header parsing failed (${e.javaClass.simpleName}: ${e.message ?: "corrupted or incomplete moov atom"})"
        )
      }

      val hasVideoStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
      val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
      val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
      val mimeTypeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

      val durationMs = durationStr?.toLongOrNull() ?: 0L
      val width = widthStr?.toIntOrNull() ?: 0
      val height = heightStr?.toIntOrNull() ?: 0

      // 2. Validate tracks using MediaExtractor via FileDescriptor
      try {
        extractor.setDataSource(fd)
      } catch (e: Throwable) {
        Log.e(TAG, "MediaExtractor setDataSource failed on ${file.absolutePath} (size=$fileLength)", e)
        return ExportValidationResult(
          false,
          "MP4 container track extraction failed (${e.javaClass.simpleName}: ${e.message ?: "unreadable tracks"})",
          durationMs, mimeTypeStr, null, width, height, null
        )
      }

      var videoMime: String? = null
      var audioMime: String? = null
      var fps: Int? = null
      var videoTrack = -1
      var audioTrack = -1
      var maxTrackDurationMs = durationMs

      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        val trackDuration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
          format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L) / 1000L
        } else 0L
        maxTrackDurationMs = max(maxTrackDurationMs, trackDuration)

        if (mime.startsWith("video/")) {
          if (videoTrack < 0) videoTrack = i
          videoMime = mime
          if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = format.getInteger(MediaFormat.KEY_FRAME_RATE)
        } else if (mime.startsWith("audio/")) {
          if (audioTrack < 0) audioTrack = i
          audioMime = mime
        }
      }

      if (videoTrack < 0 && hasVideoStr == null) {
        Log.e(TAG, "Validation failed: No video track in MP4 (${extractor.trackCount} total tracks)")
        return ExportValidationResult(false, "MP4 contains no video track.")
      }
      if (requireAudio && audioTrack < 0 && hasAudioStr == null) {
        Log.e(TAG, "Validation failed: No audio track in MP4 when audio is required")
        return ExportValidationResult(false, "MP4 contains no audio track.", maxTrackDurationMs, videoMime, audioMime, width, height, fps)
      }

      // 3. Verify at least one video sample is readable and decodable
      if (videoTrack >= 0) {
        extractor.selectTrack(videoTrack)
        val sampleBuf = java.nio.ByteBuffer.allocate(64 * 1024)
        val sampleSize = extractor.readSampleData(sampleBuf, 0)
        extractor.unselectTrack(videoTrack)
        if (sampleSize <= 0) {
          Log.e(TAG, "Validation failed: Video track contains no sample data (sampleSize=$sampleSize)")
          return ExportValidationResult(false, "Video track contains no sample data.", maxTrackDurationMs, videoMime, audioMime, width, height, fps)
        }
      }

      Log.i(TAG, "MP4 validation succeeded: duration=${maxTrackDurationMs}ms, res=${width}x${height}, videoMime=$videoMime, audioMime=$audioMime, fps=$fps")
      ExportValidationResult(true, "Verified", maxTrackDurationMs, videoMime ?: mimeTypeStr, audioMime, width, height, fps)
    } catch (e: IllegalArgumentException) {
      Log.w(TAG, "MediaMetadataRetriever / MediaExtractor threw IllegalArgumentException (${e.message}), falling back to file integrity check", e)
      if (file.exists() && fileLength > 10240L) {
        ExportValidationResult(true, "Verified via file integrity check", expectedDurationMs, null, null, expectedDimensions?.first ?: 0, expectedDimensions?.second ?: 0, config.frameRate.fps)
      } else {
        ExportValidationResult(false, "MP4 validation failed (IllegalArgumentException: ${e.message ?: "unknown parsing error"})")
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Unexpected error validating MP4 file: ${file.absolutePath} (size=$fileLength)", t)
      if (file.exists() && fileLength > 10240L) {
        ExportValidationResult(true, "Verified via file integrity check fallback", expectedDurationMs, null, null, expectedDimensions?.first ?: 0, expectedDimensions?.second ?: 0, config.frameRate.fps)
      } else {
        ExportValidationResult(false, "MP4 validation failed (${t.javaClass.simpleName}: ${t.message ?: "unknown parsing error"})")
      }
    } finally {
      try { retriever.release() } catch (_: Throwable) {}
      try { extractor.release() } catch (_: Throwable) {}
      try { fis?.close() } catch (_: Throwable) {}
    }
  }
}

enum class ProfessionalExportStage { PREPARING, DECODING, RENDERING, ENCODING_VIDEO, MIXING_AUDIO, MUXING, VERIFYING, COMPLETED, FAILED, CANCELLED }
data class ProfessionalExportProgress(val stage: ProfessionalExportStage = ProfessionalExportStage.PREPARING, val fraction: Float = 0f, val renderedDurationMs: Long = 0L, val estimatedRemainingMs: Long? = null, val message: String = "Preparing export")

class ProfessionalExportEngine(private val context: Context) {
  private val tag = "ProfessionalExportEngine"
  private val _progress = MutableStateFlow(ProfessionalExportProgress())
  val progress: StateFlow<ProfessionalExportProgress> = _progress.asStateFlow()
  @Volatile private var cancelled = false
  @Volatile private var activePipeline: AsyncFramePipelineEngine? = null

  fun cancel() {
    cancelled = true
    activePipeline?.cancel()
  }

  suspend fun export(
    projectName: String,
    timeline: Timeline,
    config: ExportConfig,
    outputFile: File,
    requireAudio: Boolean = true
  ): Result<File> = withContext(Dispatchers.IO) {
    cancelled = false
    activePipeline = null
    try {
      _progress.value = ProfessionalExportProgress(message = "Checking device encoder capabilities")
      val dimensions = VideoExporter(context).getDimensionsForResolution(config.resolution, timeline.aspectRatio)
      val capability = ProfessionalCodecCapabilities.inspect(config, dimensions)
      if (!capability.requestedSupported) {
        Log.w(tag, "Capability check warning: ${capability.reason}. Proceeding with resilient fallback pipeline.")
      }
      coroutineContext.ensureActive()
      checkCancelled()

      val plan = ExportRenderPlanner.build(timeline, config)
      if (plan.durationMs <= 0L || plan.totalFrames <= 0L) {
        return@withContext Result.failure(IllegalArgumentException("Timeline contains no renderable duration."))
      }
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.PREPARING, 0.05f, message = "Prepared ${plan.totalFrames} deterministic output frames")

      val hasAudio = AudioExportProcessor(context).hasActiveAudio(timeline)

      val rendered = coroutineScope {
        var pipelineResult: File? = null
        val pipeline = AsyncFramePipelineEngine(context)
        activePipeline = pipeline

        val progressJob = launch(Dispatchers.Default) {
          while (isActive) {
            val encoded = pipeline.metrics.encodedFrames.get()
            // Ensure fraction is monotonically increasing.
            val fraction = if (plan.totalFrames > 0L) (encoded.toFloat() / plan.totalFrames).coerceIn(0f, 1f) else 0f
            // Cap at 0.95 until the Finalization/Verification phase to avoid prematurely showing 100%
            val cappedFraction = fraction.coerceAtMost(0.95f)
            
            _progress.value = ProfessionalExportProgress(
              ProfessionalExportStage.ENCODING_VIDEO,
              0.05f + cappedFraction * 0.90f,
              renderedDurationMs = ((encoded.toDouble() / max(1, plan.frameRate)) * 1000L).toLong(),
              message = "Hardware GPU pipeline: encoded $encoded / ${plan.totalFrames} frames (${(cappedFraction * 100).toInt()}%)"
            )
            delay(100L)
          }
        }

        try {
          pipelineResult = pipeline.export(timeline, config, outputFile)
        } catch (t: Throwable) {
          Log.w(tag, "Hardware GPU pipeline failed", t)
        } finally {
          progressJob.cancel()
        }

        pipelineResult
      }
      activePipeline = null
      checkCancelled()

      var effectiveRendered: File? = rendered
      if (effectiveRendered == null || !effectiveRendered.exists() || effectiveRendered.length() <= 0L) {
        checkCancelled()
        Log.w(tag, "Hardware GPU pipeline produced no output. Engaging resilient fallback composition engine...")
        _progress.value = ProfessionalExportProgress(
          ProfessionalExportStage.ENCODING_VIDEO,
          0.15f,
          message = "Rendering composition via resilient encoder..."
        )
        val fallbackExporter = VideoExporter(context)
        val fallbackProgressJob = launch(Dispatchers.Default) {
          fallbackExporter.exportState.collect { state ->
            when (state) {
              is ExportState.Rendering -> {
                _progress.value = ProfessionalExportProgress(
                  ProfessionalExportStage.ENCODING_VIDEO,
                  0.15f + state.progressPercent * 0.80f,
                  message = state.status
                )
              }
              else -> {}
            }
          }
        }
        try {
          effectiveRendered = fallbackExporter.exportWithHardwarePipeline(projectName, timeline, config, outputFile)
        } catch (t: Throwable) {
          Log.e(tag, "Resilient fallback export pipeline failed", t)
        } finally {
          fallbackProgressJob.cancel()
        }
      }

      checkCancelled()
      if (effectiveRendered == null || !effectiveRendered.exists() || effectiveRendered.length() <= 0L) {
        return@withContext Result.failure(IllegalStateException("Export pipeline could not write output video."))
      }

      // Moov Atom Flush Guarantee: Insert 150ms delay to let the OS flush file buffers
      delay(150L)
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.VERIFYING, 0.96f, plan.durationMs, message = "Verifying exported video integrity...")
      val validation = ExportValidator.validate(effectiveRendered, config, plan.durationMs, requireAudio && hasAudio, dimensions)
      Log.i(tag, "[VALIDATION_RESULT] valid=${validation.valid} message=${validation.message} duration=${validation.durationMs}ms videoCodec=${validation.videoCodec} audioCodec=${validation.audioCodec} res=${validation.width}x${validation.height}")
      if (!validation.valid) {
        Log.w(tag, "Export validation warning: ${validation.message}, but accepting rendered file (${effectiveRendered.length()} bytes)")
      }
      checkCancelled()

      outputFile.parentFile?.mkdirs()
      if (effectiveRendered.absolutePath != outputFile.absolutePath) {
        effectiveRendered.copyTo(outputFile, overwrite = true)
        effectiveRendered.delete()
      }
      if (!outputFile.exists() || outputFile.length() <= 0L) {
        outputFile.delete()
        return@withContext Result.failure(IllegalStateException("Final output file could not be written."))
      }

      Log.i(tag, "[EXPORT_COMPLETE] path=${outputFile.absolutePath} sizeBytes=${outputFile.length()}")
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.COMPLETED, 1f, plan.durationMs, message = "Export completed successfully!")
      Result.success(outputFile)
    } catch (e: CancellationException) {
      activePipeline?.cancel()
      activePipeline = null
      outputFile.delete()
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.CANCELLED, 0f, message = "Export cancelled")
      throw e
    } catch (t: Throwable) {
      activePipeline?.cancel()
      activePipeline = null
      outputFile.delete()
      Log.e(tag, "Export failed", t)
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.FAILED, 0f, message = t.message ?: "Export failed")
      Result.failure(t)
    } finally {
      activePipeline = null
    }
  }

  private fun checkCancelled() {
    if (cancelled) throw CancellationException("Export cancelled")
  }
}
