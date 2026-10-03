package com.example.engine.cloud

import android.content.Context
import com.example.data.local.TimelineSerializer
import com.example.domain.model.ProjectPackage
import com.example.domain.model.Timeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class CloudSyncState {
  IDLE, SYNCING, SUCCESS, ERROR
}

object CloudProjectSyncEngine {
  private val _syncState = MutableStateFlow(CloudSyncState.IDLE)
  val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

  private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
  val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

  /**
   * Bundles project timeline and media reference metadata into a single exportable .aecproj zip file.
   */
  suspend fun exportProjectBundle(
    context: Context,
    projectId: String,
    projectName: String,
    timeline: Timeline
  ): File? = withContext(Dispatchers.IO) {
    _syncState.value = CloudSyncState.SYNCING
    try {
      val exportDir = File(context.filesDir, "cloud_exports").apply { if (!exists()) mkdirs() }
      val zipFile = File(exportDir, "${projectName.replace(" ", "_")}_$projectId.aecproj")
      val jsonString = TimelineSerializer.serializeTimeline(timeline)

      ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        val entry = ZipEntry("project_manifest.json")
        zos.putNextEntry(entry)
        zos.write(jsonString.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
      }

      _syncState.value = CloudSyncState.SUCCESS
      _lastSyncTimestamp.value = System.currentTimeMillis()
      zipFile
    } catch (e: Exception) {
      _syncState.value = CloudSyncState.ERROR
      null
    }
  }

  /**
   * Performs cloud project sync simulation for offline/online state synchronization.
   */
  suspend fun syncProjectToCloud(context: Context, projectId: String): Boolean = withContext(Dispatchers.IO) {
    _syncState.value = CloudSyncState.SYNCING
    kotlinx.coroutines.delay(800)
    _syncState.value = CloudSyncState.SUCCESS
    _lastSyncTimestamp.value = System.currentTimeMillis()
    true
  }
}
