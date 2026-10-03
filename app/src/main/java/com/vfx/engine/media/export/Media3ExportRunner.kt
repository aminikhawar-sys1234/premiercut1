package com.vfx.engine.media.export

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.vfx.engine.core.graph.RenderGraph
import com.vfx.engine.media.media3.Media3RenderGraphEffect
import java.io.File

/**
 * Production-grade end-to-end pipeline execution helper for hardware-accelerated video export
 * using AndroidX Media3 Transformer and custom RenderGraph effects.
 */
@OptIn(UnstableApi::class)
class Media3ExportRunner(
  private val context: Context
) {

  interface ExportProgressListener {
    fun onProgress(progressPercent: Int)
    fun onSuccess(outputFile: File)
    fun onError(exception: Exception)
  }

  fun startExport(
    inputMediaUri: String,
    outputFile: File,
    renderGraphProvider: () -> RenderGraph,
    listener: ExportProgressListener? = null
  ): Transformer {
    val mediaItem = MediaItem.fromUri(inputMediaUri)

    // Build custom GlEffect with RenderGraph
    val renderGraphEffect: Effect = Media3RenderGraphEffect(renderGraphProvider)

    val audioProcessors: List<AudioProcessor> = emptyList()
    val videoEffects: List<Effect> = listOf(renderGraphEffect)

    // Testing androidx.media3.transformer.Effects
    val effectsContainer = androidx.media3.transformer.Effects(audioProcessors, videoEffects)

    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
      .setEffects(effectsContainer)
      .build()

    val sequence = EditedMediaItemSequence(editedMediaItem)
    val composition = Composition.Builder(sequence).build()

    val mainHandler = Handler(Looper.getMainLooper())

    val transformer = Transformer.Builder(context)
      .addListener(object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
          mainHandler.post {
            listener?.onSuccess(outputFile)
          }
        }

        override fun onError(
          composition: Composition,
          exportResult: ExportResult,
          exportException: ExportException
        ) {
          mainHandler.post {
            listener?.onError(exportException)
          }
        }
      })
      .build()

    transformer.start(composition, outputFile.absolutePath)

    if (listener != null) {
      val progressHolder = ProgressHolder()
      val progressRunnable = object : Runnable {
        override fun run() {
          val progressState = transformer.getProgress(progressHolder)
          if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
            listener.onProgress(progressHolder.progress)
            if (progressState == Transformer.PROGRESS_STATE_AVAILABLE || progressState == Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
              mainHandler.postDelayed(this, 100)
            }
          }
        }
      }
      mainHandler.post(progressRunnable)
    }

    return transformer
  }
}
