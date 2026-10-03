package com.example.data.repository

import com.example.data.local.*
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ProjectRepository(private val database: AppDatabase) {
  private val projectDao: ProjectDao = database.projectDao()
  private val exportedVideoDao = database.exportedVideoDao()
  private val crashRecoveryDao = database.crashRecoveryDao()

  val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()
  val drafts: Flow<List<ProjectEntity>> = projectDao.getDrafts()
  val exportedVideos: Flow<List<ExportedVideoEntity>> = exportedVideoDao.getAllExportedVideos()
  val activeRecoverySession: Flow<CrashRecoveryEntity?> = crashRecoveryDao.getActiveSessionFlow()

  suspend fun getProjectById(id: String): ProjectEntity? {
    return projectDao.getProjectById(id)
  }

  suspend fun getActiveRecoverySession(): CrashRecoveryEntity? {
    return crashRecoveryDao.getActiveSession()
  }

  suspend fun saveCrashRecoverySession(
    projectId: String,
    projectName: String,
    timeline: Timeline,
    settings: ProjectSettings = ProjectSettings(),
    settingsJson: String = "{}"
  ) {
    val projectPackage = TimelineSerializer.buildProjectPackage(
      projectId = projectId,
      projectName = projectName,
      settings = settings,
      timeline = timeline,
      isDraft = true
    )
    val packageJson = TimelineSerializer.toPackageJson(projectPackage)
    val entity = CrashRecoveryEntity(
      id = "active_session",
      projectId = projectId,
      projectName = projectName,
      timestamp = System.currentTimeMillis(),
      timelineJson = packageJson,
      settingsJson = if (settingsJson.isBlank() || settingsJson == "{}") TimelineSerializer.settingsToJson(settings) else settingsJson,
      isDirty = true
    )
    crashRecoveryDao.saveSession(entity)
  }

  suspend fun clearCrashRecoverySession() {
    crashRecoveryDao.clearSession()
  }

  suspend fun saveProject(
    id: String,
    name: String,
    durationMs: Long,
    thumbnailPath: String,
    aspectRatio: String,
    resolution: String,
    fps: Int,
    timeline: Timeline,
    isDraft: Boolean = true,
    sampleRate: Int = 48000,
    canvasColor: Long = 0xFF000000,
    hasMissingMedia: Boolean = false,
    extraMetadataJson: String = "{}"
  ): ProjectEntity {
    val projectSettings = ProjectSettings(
      aspectRatio = AspectRatio.values().find { it.label == aspectRatio } ?: AspectRatio.RATIO_9_16,
      resolution = Resolution.values().find { it.label == resolution } ?: Resolution.RES_1080P,
      fps = FrameRate.values().find { it.fps == fps } ?: FrameRate.FPS_30,
      sampleRateHz = sampleRate,
      canvasBackgroundColor = canvasColor,
      totalDurationMs = durationMs
    )
    val projectPackage = TimelineSerializer.buildProjectPackage(
      projectId = id,
      projectName = name,
      settings = projectSettings,
      timeline = timeline,
      isDraft = isDraft
    )
    val packageJson = TimelineSerializer.toPackageJson(projectPackage)

    val entity = ProjectEntity(
      id = id,
      name = name,
      durationMs = durationMs,
      lastEditedTime = System.currentTimeMillis(),
      thumbnailPath = thumbnailPath,
      aspectRatio = aspectRatio,
      resolution = resolution,
      fps = fps,
      timelineJson = packageJson,
      isDraft = isDraft,
      sampleRate = sampleRate,
      canvasColor = canvasColor,
      hasMissingMedia = hasMissingMedia,
      extraMetadataJson = extraMetadataJson
    )
    projectDao.insertProject(entity)
    // Clear the active dirty recovery session because user project is saved
    crashRecoveryDao.clearSession()
    return entity
  }

  suspend fun updateMissingMediaStatus(id: String, hasMissing: Boolean) {
    projectDao.updateMissingMediaStatus(id, hasMissing)
  }

  suspend fun duplicateProject(id: String): ProjectEntity? {
    val original = projectDao.getProjectById(id) ?: return null
    val duplicated = original.copy(
      id = UUID.randomUUID().toString(),
      name = "${original.name} (Copy)",
      lastEditedTime = System.currentTimeMillis()
    )
    projectDao.insertProject(duplicated)
    return duplicated
  }

  suspend fun renameProject(id: String, newName: String) {
    projectDao.renameProject(id, newName)
  }

  suspend fun deleteProject(id: String) {
    projectDao.deleteProjectById(id)
    val active = crashRecoveryDao.getActiveSession()
    if (active?.projectId == id) {
      crashRecoveryDao.clearSession()
    }
  }

  suspend fun recordExport(
    projectId: String,
    title: String,
    filePath: String,
    durationMs: Long,
    resolution: String,
    fps: Int,
    fileSizeBytes: Long
  ) {
    val entity = ExportedVideoEntity(
      id = UUID.randomUUID().toString(),
      projectId = projectId,
      title = title,
      filePath = filePath,
      durationMs = durationMs,
      resolution = resolution,
      fps = fps,
      timestamp = System.currentTimeMillis(),
      fileSizeBytes = fileSizeBytes
    )
    exportedVideoDao.insertExportedVideo(entity)
  }

  suspend fun deleteExportedVideo(id: String) {
    exportedVideoDao.deleteExportedVideo(id)
  }

  suspend fun createSampleProjectIfEmpty() {
    // Projects start clean and empty; no sample projects pre-seeded
  }
}
