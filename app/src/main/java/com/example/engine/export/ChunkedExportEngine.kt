package com.example.engine.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.example.domain.model.Resolution
import com.example.domain.model.Timeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Enterprise Segmented/Chunked Video Export Manager.
 * Splices complex 4K or long timeline projects into 5-10s sub-renders to prevent
 * GPU memory pressure, MediaCodec buffer stalls, and OOM failures.
 * Concatenates MP4 video/audio samples into the output file seamlessly.
 */
class ChunkedExportEngine(private val context: Context) {
  companion object {
    private const val TAG = "ChunkedExportEngine"
    private const val DEFAULT_CHUNK_DURATION_MS = 10_000L // 10s chunks
  }

  suspend fun exportInChunks(
    timeline: Timeline,
    config: ExportConfig,
    destinationFile: File,
    videoExporter: VideoExporter,
    onProgress: (progressPercent: Float, status: String) -> Unit
  ): Boolean = withContext(Dispatchers.IO) {
    val totalDurationMs = timeline.totalDurationMs
    if (totalDurationMs <= 0L) {
      Log.e(TAG, "Invalid timeline duration: $totalDurationMs")
      return@withContext false
    }

    val chunkCount = maxOf(1, ((totalDurationMs + DEFAULT_CHUNK_DURATION_MS - 1) / DEFAULT_CHUNK_DURATION_MS).toInt())
    val tempDir = File(context.cacheDir, "chunk_export_${System.currentTimeMillis()}")
    if (!tempDir.exists()) tempDir.mkdirs()

    val chunkFiles = mutableListOf<File>()

    try {
      Log.d(TAG, "Starting chunked export for project duration $totalDurationMs ms across $chunkCount chunks.")

      for (i in 0 until chunkCount) {
        val startMs = i * DEFAULT_CHUNK_DURATION_MS
        val endMs = minOf(totalDurationMs, (i + 1) * DEFAULT_CHUNK_DURATION_MS)
        val chunkFile = File(tempDir, "chunk_$i.mp4")

        val chunkTimeline = timeline.copy(
          videoClips = timeline.videoClips.filter { clip ->
            val clipEndMs = clip.timelineStartMs + clip.durationMs
            clip.timelineStartMs < endMs && clipEndMs > startMs
          },
          audioClips = timeline.audioClips.filter { clip ->
            val clipEndMs = clip.timelineStartMs + clip.durationMs
            clip.timelineStartMs < endMs && clipEndMs > startMs
          }
        )

        val statusMsg = "Rendering chunk ${i + 1}/$chunkCount (${startMs / 1000}s - ${endMs / 1000}s)..."
        val baseProgress = (i.toFloat() / chunkCount.toFloat()) * 0.9f
        onProgress(baseProgress, statusMsg)

        // Render individual segment
        val success = renderSegment(videoExporter, chunkTimeline, config, chunkFile, startMs, endMs)
        if (!success || !chunkFile.exists() || chunkFile.length() == 0L) {
          Log.e(TAG, "Failed to render chunk $i")
          tempDir.deleteRecursively()
          return@withContext false
        }

        chunkFiles.add(chunkFile)

        // Force memory reclamation & GC between chunks
        System.gc()
      }

      onProgress(0.92f, "Stitching MP4 chunks into final video file...")
      val stitchSuccess = concatenateMp4Chunks(chunkFiles, destinationFile)

      tempDir.deleteRecursively()
      if (stitchSuccess && destinationFile.exists() && destinationFile.length() > 0L) {
        onProgress(1.0f, "Export finished successfully!")
        Log.d(TAG, "Chunked export completed successfully: ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")
        return@withContext true
      } else {
        Log.e(TAG, "Chunk stitching failed.")
        return@withContext false
      }
    } catch (e: Exception) {
      Log.e(TAG, "Fatal error in chunked export engine", e)
      tempDir.deleteRecursively()
      return@withContext false
    }
  }

  private suspend fun renderSegment(
    exporter: VideoExporter,
    timeline: Timeline,
    config: ExportConfig,
    chunkFile: File,
    segmentStartMs: Long,
    segmentEndMs: Long
  ): Boolean {
    return try {
      exporter.exportTimelineSegment(timeline, config, chunkFile, segmentStartMs, segmentEndMs)
    } catch (e: Exception) {
      Log.w(TAG, "Hardware segment render failed, retrying with software/adaptive fallback", e)
      val fallbackConfig = config.copy(
        resolution = Resolution.RES_1080P,
        customBitrateKbps = minOf(config.customBitrateKbps, 8000)
      )
      exporter.exportTimelineSegment(timeline, fallbackConfig, chunkFile, segmentStartMs, segmentEndMs)
    }
  }

  /**
   * Stitches multiple MP4 video segment files into a single continuous MP4 file
   * using MediaExtractor and MediaMuxer sample pass-through.
   */
  fun concatenateMp4Chunks(chunkFiles: List<File>, outputFile: File): Boolean {
    if (chunkFiles.isEmpty()) return false
    if (chunkFiles.size == 1) {
      chunkFiles[0].copyTo(outputFile, overwrite = true)
      return true
    }

    var muxer: MediaMuxer? = null
    var outVideoTrackIndex = -1
    var outAudioTrackIndex = -1

    try {
      muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

      // Register tracks from first chunk
      val sampleExtractor = MediaExtractor()
      sampleExtractor.setDataSource(chunkFiles[0].absolutePath)

      for (i in 0 until sampleExtractor.trackCount) {
        val format = sampleExtractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("video/") && outVideoTrackIndex < 0) {
          outVideoTrackIndex = muxer.addTrack(format)
        } else if (mime.startsWith("audio/") && outAudioTrackIndex < 0) {
          outAudioTrackIndex = muxer.addTrack(format)
        }
      }
      sampleExtractor.release()

      muxer.start()

      var globalVideoPtsOffsetUs = 0L
      var globalAudioPtsOffsetUs = 0L
      val buffer = ByteBuffer.allocate(2 * 1024 * 1024)

      for (chunk in chunkFiles) {
        val extractor = MediaExtractor()
        extractor.setDataSource(chunk.absolutePath)

        var inVideoTrack = -1
        var inAudioTrack = -1

        for (i in 0 until extractor.trackCount) {
          val format = extractor.getTrackFormat(i)
          val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
          if (mime.startsWith("video/")) inVideoTrack = i
          else if (mime.startsWith("audio/")) inAudioTrack = i
        }

        var maxChunkVideoPts = 0L
        var maxChunkAudioPts = 0L

        // Process Video Samples
        if (inVideoTrack >= 0 && outVideoTrackIndex >= 0) {
          extractor.selectTrack(inVideoTrack)
          while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val ptsUs = extractor.sampleTime + globalVideoPtsOffsetUs
            val flags = extractor.sampleFlags

            val info = MediaCodec.BufferInfo().apply {
              set(0, sampleSize, ptsUs, flags)
            }
            muxer.writeSampleData(outVideoTrackIndex, buffer, info)
            maxChunkVideoPts = maxOf(maxChunkVideoPts, extractor.sampleTime)
            extractor.advance()
          }
          extractor.unselectTrack(inVideoTrack)
        }

        // Process Audio Samples
        if (inAudioTrack >= 0 && outAudioTrackIndex >= 0) {
          extractor.selectTrack(inAudioTrack)
          while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val ptsUs = extractor.sampleTime + globalAudioPtsOffsetUs
            val flags = extractor.sampleFlags

            val info = MediaCodec.BufferInfo().apply {
              set(0, sampleSize, ptsUs, flags)
            }
            muxer.writeSampleData(outAudioTrackIndex, buffer, info)
            maxChunkAudioPts = maxOf(maxChunkAudioPts, extractor.sampleTime)
            extractor.advance()
          }
          extractor.unselectTrack(inAudioTrack)
        }

        globalVideoPtsOffsetUs += maxChunkVideoPts + 33_333L // ~30fps step
        globalAudioPtsOffsetUs += maxChunkAudioPts + 23_220L // ~44.1kHz step
        extractor.release()
      }

      muxer.stop()
      muxer.release()
      return true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to concatenate MP4 chunks", e)
      try { muxer?.release() } catch (ignored: Exception) {}
      return false
    }
  }
}
