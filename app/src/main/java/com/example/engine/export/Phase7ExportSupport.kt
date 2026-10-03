package com.example.engine.export

import android.os.StatFs
import com.example.domain.model.Timeline
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import kotlin.math.max

/** Phase 7 policy layer around the existing VideoExporter/ChunkedExportEngine. */
data class Phase7RenderPolicy(
  val useChunking: Boolean,
  val chunkDurationMs: Long,
  val preferHardware: Boolean,
  val allowSoftwareFallback: Boolean,
  val estimatedPeakMemoryMb: Long
)

object Phase7RenderPolicyResolver {
  private const val FOUR_K_PIXELS = 3840L * 2160L

  fun resolve(config: ExportConfig, timeline: Timeline, availableMemoryMb: Long): Phase7RenderPolicy {
    val pixels = when (config.resolution) {
      com.example.domain.model.Resolution.RES_4K,
      com.example.domain.model.Resolution.RES_VERTICAL_4K -> FOUR_K_PIXELS
      else -> 1920L * 1080L
    }
    val longProject = timeline.totalDurationMs > 30_000L
    val highMemoryRisk = pixels >= FOUR_K_PIXELS || availableMemoryMb in 1..1536
    return Phase7RenderPolicy(
      useChunking = longProject || highMemoryRisk,
      chunkDurationMs = if (pixels >= FOUR_K_PIXELS) 5_000L else 10_000L,
      preferHardware = true,
      allowSoftwareFallback = true,
      estimatedPeakMemoryMb = max(128L, (pixels * 4L / (1024L * 1024L)) * 2L)
    )
  }
}

/** Atomic checkpoint for retry/resume at the completed-output boundary. */
class Phase7ExportCheckpointStore(private val directory: File) {
  private val file = File(directory, "phase7-export-checkpoint.properties")

  fun save(fingerprint: String, outputPath: String, durationMs: Long, completed: Boolean) {
    directory.mkdirs()
    val tmp = File(directory, file.name + ".tmp")
    Properties().apply {
      setProperty("version", "1")
      setProperty("fingerprint", fingerprint)
      setProperty("outputPath", outputPath)
      setProperty("durationMs", durationMs.toString())
      setProperty("completed", completed.toString())
    }.store(tmp.outputStream(), "Ah Studio Phase 7 export checkpoint")
    if (!tmp.renameTo(file)) {
      file.delete()
      if (!tmp.renameTo(file)) throw IllegalStateException("Unable to commit export checkpoint")
    }
  }

  fun findCompleted(fingerprint: String): File? {
    if (!file.isFile) return null
    return runCatching {
      val p = Properties().apply { file.inputStream().use { load(it) } }
      if (p.getProperty("fingerprint") != fingerprint || p.getProperty("completed") != "true") return null
      val output = File(p.getProperty("outputPath") ?: return null)
      output.takeIf { it.isFile && it.length() > 0L }
    }.getOrNull()
  }

  fun clear() { file.delete() }
}

object Phase7ExportFingerprint {
  fun create(projectName: String, timeline: Timeline, config: ExportConfig): String {
    val canonical = buildString {
      append(projectName).append('|')
      append(config.resolution.name).append('|')
      append(config.frameRate.name).append('|')
      append(config.quality.name).append('|')
      append(config.customBitrateKbps).append('|')
      append(config.codecProfile.name).append('|')
      append(timeline)
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }
}

object Phase7StorageGuard {
  fun hasSpace(file: File, requiredBytes: Long): Boolean {
    val target = file.parentFile ?: return false
    target.mkdirs()
    return runCatching { StatFs(target.absolutePath).availableBytes >= requiredBytes }.getOrDefault(false)
  }
}

data class Phase7RetryDecision(val shouldRetry: Boolean, val delayMs: Long, val reason: String)

object Phase7RetryPolicy {
  fun next(attempt: Int, maxAttempts: Int = 3): Phase7RetryDecision {
    if (attempt >= maxAttempts) return Phase7RetryDecision(false, 0L, "retry limit reached")
    val delay = (500L * (1L shl attempt.coerceIn(0, 4))).coerceAtMost(4_000L)
    return Phase7RetryDecision(true, delay, "transient export failure")
  }
}
