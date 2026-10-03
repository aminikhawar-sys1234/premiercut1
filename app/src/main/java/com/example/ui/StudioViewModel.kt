package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AIToolsService
import com.example.ai.VideoHighlightSegment
import com.example.data.local.AppDatabase
import com.example.data.local.ExportedVideoEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.TimelineSerializer
import com.example.data.presets.TemplatesCatalog
import com.example.data.presets.VideoTemplate
import com.example.data.repository.EditorToolsRepository
import com.example.data.repository.ProjectRepository
import com.example.domain.StudioPreferencesManager
import com.example.domain.UserSettings
import com.example.domain.model.*
import com.example.engine.SelectedTrackElement
import com.example.engine.TimelineEngine
import com.example.engine.history.TimelineActionType
import com.example.engine.audio.AudioEngine
import com.example.engine.export.ExportConfig
import com.example.engine.export.ExportState
import com.example.engine.export.VideoExporter
import com.example.engine.export.ProfessionalExportEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.local.CrashRecoveryEntity
import com.example.engine.media.MediaPersistenceManager
import com.example.engine.media.MediaRelinkManager
import com.ahstudio.captions.android.CaptionsGraph
import com.ahstudio.captions.engine.AutoCaptionOptions
import com.ahstudio.captions.engine.CaptionGenerationState
import com.ahstudio.captions.subtitle.SubtitleCue
import com.ahstudio.captions.subtitle.SubtitleExporter
import com.ahstudio.captions.subtitle.SubtitleFormat
import com.ahstudio.captions.subtitle.SubtitleImporter
import com.example.engine.ai.*
import com.example.engine.integration.KeyframeAnimationEngine

enum class ProjectSaveStatus {
  SAVED,
  SAVING,
  UNSAVED
}

data class ProjectSaveState(
  val status: ProjectSaveStatus = ProjectSaveStatus.SAVED,
  val lastSavedTimeMs: Long = System.currentTimeMillis()
)

enum class AppScreen {
  HOME,
  PROJECTS,
  EDITOR,
  EXPORT,
  TEMPLATES,
  CREATOR,
  NOTIFICATIONS,
  ACCOUNT,
  AI_SUITE,
  EXPORTED_LIBRARY,
  SETTINGS
}

enum class EditorToolbarTab {
  MEDIA,
  OVERLAY,
  EDIT,
  TRIM,
  AUDIO,
  VOLUME,
  TEXT,
  ELEMENTS,
  STICKERS,
  EFFECTS,
  FILTERS,
  TRANSITIONS,
  ADJUST,
  SPEED,
  CHROMA,
  AI,
  CANVAS,
  KEYFRAME,
  CAPTIONS,
  BACKGROUND,
  AI_AVATAR,
  ANIMATIONS,
  MASK,
  AI_MATTING,
  ASSET_STORE,
  MOTION_TRACKING,
  ADVANCED_ANIMATION,
  AR_EFFECTS
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

  // Room is also deferred: corrupted/legacy DB state must never prevent HOME from launching.
  private val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppDatabase.getDatabase(application) }
  val repository: ProjectRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ProjectRepository(database) }
  val timelineEngine = TimelineEngine()
  private val _isCreatingProject = MutableStateFlow(false)
  val isCreatingProject: StateFlow<Boolean> = _isCreatingProject.asStateFlow()
  // Keep heavyweight media/ML/GPU services lazy so the launcher can always reach HOME.
  // They are created on first real editor/playback/export use instead of during ViewModel construction.
  val audioEngine: AudioEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AudioEngine(application) }
  val aiTools: AIToolsService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AIToolsService(application) }
  val compositionEngine: com.example.engine.composition.VideoCompositionEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { com.example.engine.composition.VideoCompositionEngine(application) }
  val videoExporter: VideoExporter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { VideoExporter(application) }
  val professionalExportEngine: ProfessionalExportEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ProfessionalExportEngine(application) }
  val memoryManager: com.example.engine.memory.EngineMemoryManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { com.example.engine.memory.EngineMemoryManager.getInstance(application) }
  val reliabilityManager: com.example.engine.reliability.EngineReliabilityManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { com.example.engine.reliability.EngineReliabilityManager(application) }
  val proxyMediaEngine: com.example.engine.playback.ProxyMediaEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { com.example.engine.playback.ProxyMediaEngine(application) }

  private var isSyncingFromPlayback = false
  private var wasPlayingBeforeScrub = false

  val playbackEngine: com.example.engine.playback.VideoPlaybackEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    com.example.engine.playback.VideoPlaybackEngine(
    context = application,
    onTimelinePositionChanged = { posMs ->
      if (timelineEngine.isPlaying.value) {
        isSyncingFromPlayback = true
        timelineEngine.updatePlayheadFromPlayback(posMs)
        isSyncingFromPlayback = false
      }
    },
    onPlaybackEnded = {
      timelineEngine.pause()
    },
    proxyEngine = proxyMediaEngine
    )
  }

  val engineController: com.example.engine.controller.CustomVideoEngineController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { playbackEngine.engineController }
  val engineState: StateFlow<com.example.engine.controller.VideoEngineState> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { engineController.engineState }

  val allProjects: StateFlow<List<ProjectEntity>> = repository.allProjects
    .catch { e ->
      android.util.Log.e("StudioViewModel", "Error collecting allProjects", e)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val drafts: StateFlow<List<ProjectEntity>> = repository.drafts
    .catch { e ->
      android.util.Log.e("StudioViewModel", "Error collecting drafts", e)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val exportedVideos: StateFlow<List<ExportedVideoEntity>> = repository.exportedVideos
    .catch { e ->
      android.util.Log.e("StudioViewModel", "Error collecting exportedVideos", e)
      emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val editorToolsRepository: EditorToolsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    EditorToolsRepository()
  }

  val editorTools: StateFlow<List<EditorToolItem>> = editorToolsRepository.getEditorToolsStream()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = editorToolsRepository.getDefaultEditorTools()
    )

  fun getSelectedVideoClip(): VideoClip? {
    val sel = timelineEngine.selectedElement.value
    val selectedId = (sel as? SelectedTrackElement.Video)?.clipId ?: timelineEngine.selectedClipIds.value.firstOrNull()
    val clips = timelineEngine.timeline.value.videoClips
    val pos = timelineEngine.currentPositionMs.value
    return clips.find { it.id == selectedId }
      ?: clips.find { pos >= it.timelineStartMs && pos < it.timelineStartMs + it.durationMs }
      ?: clips.firstOrNull()
  }

  // Global Undo / Redo Memento State
  val canUndo: StateFlow<Boolean> = timelineEngine.canUndo
  val canRedo: StateFlow<Boolean> = timelineEngine.canRedo
  val undoActionTitle: StateFlow<String?> = timelineEngine.undoActionTitle
  val redoActionTitle: StateFlow<String?> = timelineEngine.redoActionTitle

  fun undo() {
    timelineEngine.undo()
  }

  fun redo() {
    timelineEngine.redo()
  }

  val settings: StateFlow<UserSettings> = StudioPreferencesManager.settings

  // Navigation State - Home page as the initial opening screen
  private val _currentScreen = MutableStateFlow(AppScreen.HOME)
  val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

  // Active Project State
  private val _activeProjectId = MutableStateFlow("")
  val activeProjectId: StateFlow<String> = _activeProjectId.asStateFlow()

  private val _activeProjectName = MutableStateFlow("New Project")
  val activeProjectName: StateFlow<String> = _activeProjectName.asStateFlow()

  private val _activeAspectRatio = MutableStateFlow(AspectRatio.RATIO_9_16)
  val activeAspectRatio: StateFlow<AspectRatio> = _activeAspectRatio.asStateFlow()

  private val _isCanvasConfiguredByMedia = MutableStateFlow(false)
  val isCanvasConfiguredByMedia: StateFlow<Boolean> = _isCanvasConfiguredByMedia.asStateFlow()

  private val _activeResolution = MutableStateFlow(Resolution.RES_1080P)
  val activeResolution: StateFlow<Resolution> = _activeResolution.asStateFlow()

  private val _activeFps = MutableStateFlow(FrameRate.FPS_30)
  val activeFps: StateFlow<FrameRate> = _activeFps.asStateFlow()

  private val _activeSampleRate = MutableStateFlow(48000)
  val activeSampleRate: StateFlow<Int> = _activeSampleRate.asStateFlow()

  private val _activeCanvasColor = MutableStateFlow(0xFF000000)
  val activeCanvasColor: StateFlow<Long> = _activeCanvasColor.asStateFlow()

  // Save State & Missing Media Tracking
  private val _saveState = MutableStateFlow(ProjectSaveState())
  val saveState: StateFlow<ProjectSaveState> = _saveState.asStateFlow()

  private val _missingMediaList = MutableStateFlow<List<MissingMediaItem>>(emptyList())
  val missingMediaList: StateFlow<List<MissingMediaItem>> = _missingMediaList.asStateFlow()

  val activeRecoverySession: StateFlow<CrashRecoveryEntity?> = repository.activeRecoverySession
    .catch { e ->
      android.util.Log.e("StudioViewModel", "Error collecting activeRecoverySession", e)
      emit(null)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  // Active Bottom Sheet/Tool in Editor
  private val _activeToolbarTab = MutableStateFlow<EditorToolbarTab?>(null)
  val activeToolbarTab: StateFlow<EditorToolbarTab?> = _activeToolbarTab.asStateFlow()

  // AI Operation States
  private val _isAIBusy = MutableStateFlow(false)
  val isAIBusy: StateFlow<Boolean> = _isAIBusy.asStateFlow()

  private val _aiStatusMessage = MutableStateFlow("")
  val aiStatusMessage: StateFlow<String> = _aiStatusMessage.asStateFlow()

  private val _aiHighlights = MutableStateFlow<List<VideoHighlightSegment>>(emptyList())
  val aiHighlights: StateFlow<List<VideoHighlightSegment>> = _aiHighlights.asStateFlow()

  // Playback & auto-save jobs
  private var autoSaveJob: Job? = null

  init {
    // 1. Initialize with a clean, blank timeline
    timelineEngine.loadTimeline(Timeline())

    // Sync Timeline changes with Playback Engine, mark unsaved, and persist recovery snapshot
    viewModelScope.launch {
      timelineEngine.timeline.collectLatest { timeline ->
        if (_currentScreen.value == AppScreen.EDITOR || _currentScreen.value == AppScreen.EXPORT) {
          try {
            playbackEngine.updateTimeline(timeline)
          } catch (t: Throwable) {
            android.util.Log.w("StudioViewModel", "Playback update deferred", t)
          }
        }
        if (_activeAspectRatio.value != timeline.aspectRatio) {
          _activeAspectRatio.value = timeline.aspectRatio
        }
        if (_activeCanvasColor.value != timeline.canvasBackgroundColor) {
          _activeCanvasColor.value = timeline.canvasBackgroundColor
        }
        if (!_isCanvasConfiguredByMedia.value && timeline.videoClips.isNotEmpty()) {
          val firstClip = timeline.videoClips.first()
          val rot = if (firstClip.naturalRotation != 0) firstClip.naturalRotation else firstClip.rotationDegrees
          checkAndAutoConfigureCanvasFromMedia(
            width = firstClip.width,
            height = firstClip.height,
            rotationDegrees = rot,
            frameRate = firstClip.frameRate
          )
        }
        if (_activeProjectId.value.isNotBlank() && _currentScreen.value == AppScreen.EDITOR) {
          _saveState.value = _saveState.value.copy(status = ProjectSaveStatus.UNSAVED)
          // Debounce crash recovery snapshot so every keystroke or trim is immediately protected
          delay(1200L)
          val currentSettings = ProjectSettings(
            aspectRatio = _activeAspectRatio.value,
            resolution = _activeResolution.value,
            fps = _activeFps.value,
            sampleRateHz = _activeSampleRate.value,
            canvasBackgroundColor = _activeCanvasColor.value,
            totalDurationMs = timeline.totalDurationMs
          )
          repository.saveCrashRecoverySession(
            projectId = _activeProjectId.value,
            projectName = _activeProjectName.value,
            timeline = timeline,
            settings = currentSettings
          )
        }
      }
    }

    // Keep activeFps in lockstep with authoritative timelineFps
    viewModelScope.launch {
      timelineEngine.timelineFps.collectLatest { fpsInt ->
        val matchedFps = FrameRate.values().find { it.fps == fpsInt } ?: FrameRate.FPS_30
        if (_activeFps.value != matchedFps) {
          _activeFps.value = matchedFps
        }
      }
    }

    // Monitor playback state from TimelineEngine
    viewModelScope.launch {
      timelineEngine.isPlaying.collectLatest { isPlaying ->
        if (_currentScreen.value == AppScreen.EDITOR || _currentScreen.value == AppScreen.EXPORT) {
          try {
            if (isPlaying) {
              val currentCti = timelineEngine.currentPositionMs.value
              playbackEngine.play(currentCti)
            } else {
              playbackEngine.pause()
            }
          } catch (t: Throwable) {
            android.util.Log.w("StudioViewModel", "Playback state change error", t)
          }
        }
      }
    }

    // Sync seeking from timeline UI into playback engine
    viewModelScope.launch {
      timelineEngine.currentPositionMs.collectLatest { posMs ->
        if (_currentScreen.value == AppScreen.EDITOR || _currentScreen.value == AppScreen.EXPORT) {
          try {
            if (!isSyncingFromPlayback && !timelineEngine.isPlaying.value && !timelineEngine.isScrubbing.value && !playbackEngine.isScrubbing) {
              playbackEngine.seekTo(posMs)
            }
          } catch (t: Throwable) {
            android.util.Log.w("StudioViewModel", "Playback seek error", t)
          }
        }
      }
    }

    // Periodic auto-save
    startAutoSave()
  }

  fun navigateTo(screen: AppScreen) {
    if (screen != AppScreen.EDITOR && screen != AppScreen.EXPORT) {
      timelineEngine.pause()
      if (_currentScreen.value == AppScreen.EDITOR || _currentScreen.value == AppScreen.EXPORT) {
        try {
          playbackEngine.pause()
        } catch (_: Throwable) {}
      }
    }
    _currentScreen.value = screen
    if (screen == AppScreen.EDITOR) {
      // Initialize the media pipeline immediately when the editor is opened.
      try {
        playbackEngine.updateTimeline(timelineEngine.timeline.value)
        playbackEngine.seekTo(timelineEngine.currentPositionMs.value)
      } catch (t: Throwable) {
        android.util.Log.w("StudioViewModel", "Editor entry playback initialization error", t)
      }
    }
  }

  fun seekTo(posMs: Long) {
    val safePos = posMs.coerceAtLeast(0L)
    if (timelineEngine.isPlaying.value) {
      timelineEngine.pause()
      playbackEngine.pause()
    }
    isSyncingFromPlayback = true
    try {
      timelineEngine.setPosition(safePos, snap = false)
    } finally {
      isSyncingFromPlayback = false
    }
    playbackEngine.seekTo(safePos)
  }

  fun seekToUs(posUs: Long) {
    seekTo(posUs / 1000L)
  }

  fun onScrubStart() {
    wasPlayingBeforeScrub = timelineEngine.isPlaying.value
    timelineEngine.startScrubbing()
    playbackEngine.startScrubbing()
  }

  fun onScrubProgress(posMs: Long) {
    // Any direct timeline touch/drag is an explicit seek gesture: pause first,
    // then publish the new master position. Never let the old play state resume it.
    if (!timelineEngine.isScrubbing.value) {
      timelineEngine.beginScrubbing()
    }
    isSyncingFromPlayback = true
    try {
      timelineEngine.setPosition(posMs, snap = false)
    } finally {
      isSyncingFromPlayback = false
    }
    playbackEngine.scrubTo(posMs)
  }

  fun onScrubStop(posMs: Long? = null) {
    val finalPos = (posMs ?: timelineEngine.currentPositionMs.value).coerceAtLeast(0L)
    val shouldResume = wasPlayingBeforeScrub
    wasPlayingBeforeScrub = false
    isSyncingFromPlayback = true
    try {
      timelineEngine.setPosition(finalPos)
      timelineEngine.stopScrubbing()
    } finally {
      isSyncingFromPlayback = false
    }
    playbackEngine.stopScrubbing(finalPos)
    if (shouldResume) {
      timelineEngine.play()
    }
  }

  fun setActiveToolbarTab(tab: EditorToolbarTab?) {
    _activeToolbarTab.value = tab
  }

  // Motion Tracking Engine & UI State
  val motionTrackerEngine = MotionTrackerEngine()
  private val _motionTrackingState = MutableStateFlow(MotionTrackingUiState())
  val motionTrackingState: StateFlow<MotionTrackingUiState> = _motionTrackingState.asStateFlow()
  private var trackingJob: Job? = null

  fun resetMotionTracking() {
    trackingJob?.cancel()
    trackingJob = null
    _motionTrackingState.value = MotionTrackingUiState(
      activeCategory = _motionTrackingState.value.activeCategory,
      targetRegion = NormalizedRect.DEFAULT_CENTER,
      settings = MotionTrackingSettings()
    )
  }

  fun setTrackingCategory(category: TrackingCategory) {
    _motionTrackingState.update { it.copy(activeCategory = category, errorMessage = null) }
  }

  fun updateTrackingRegion(region: NormalizedRect) {
    _motionTrackingState.update { it.copy(targetRegion = region.clamped()) }
  }

  fun cancelMotionTracking() {
    trackingJob?.cancel()
    trackingJob = null
    _motionTrackingState.update {
      it.copy(
        isTracking = false,
        statusMessage = "Tracking cancelled by user",
        progress = 0f
      )
    }
  }

  fun startMotionTracking(category: TrackingCategory = _motionTrackingState.value.activeCategory, isForward: Boolean = true) {
    val activeClip = getSelectedVideoClip()
    if (activeClip == null) {
      _motionTrackingState.update {
        it.copy(
          errorMessage = "No video clip selected on timeline. Please add or select a clip first.",
          statusMessage = "Select a clip to track"
        )
      }
      return
    }

    trackingJob?.cancel()
    trackingJob = viewModelScope.launch(Dispatchers.Default) {
      _motionTrackingState.update {
        it.copy(
          isTracking = true,
          progress = 0.05f,
          statusMessage = "Extracting video frames for ${category.title} tracking...",
          errorMessage = null
        )
      }

      try {
        val currentPlayheadMs = timelineEngine.currentPositionMs.value
        val clipStartTimelineMs = activeClip.timelineStartMs
        val clipDurationMs = activeClip.durationMs.coerceAtLeast(500L)
        val relativePlayheadMs = (currentPlayheadMs - clipStartTimelineMs).coerceIn(0L, clipDurationMs)

        val startUs: Long
        val durationUs: Long
        if (isForward) {
          startUs = relativePlayheadMs * 1000L
          durationUs = (clipDurationMs - relativePlayheadMs).coerceAtLeast(100L) * 1000L
        } else {
          startUs = 0L
          durationUs = clipDurationMs * 1000L
        }

        val settings = _motionTrackingState.value.settings.copy(
          trackForward = isForward,
          trackBackward = !isForward
        )

        val result = motionTrackerEngine.analyzeMotion(
          context = getApplication(),
          videoUri = activeClip.uri,
          targetClipId = activeClip.id,
          initialBox = _motionTrackingState.value.targetRegion,
          startUs = startUs,
          durationUs = durationUs,
          category = category,
          settings = settings,
          onProgress = { p, msg, _ ->
            _motionTrackingState.update {
              it.copy(progress = p, statusMessage = msg)
            }
          }
        )

        _motionTrackingState.update {
          it.copy(
            isTracking = false,
            activeResult = result,
            progress = 1.0f,
            statusMessage = if (result.keyframes.isNotEmpty()) {
              "Tracked ${result.keyframes.size} frames successfully"
            } else {
              when (category) {
                TrackingCategory.FACE -> "No face detected in video"
                TrackingCategory.BODY -> "No body pose detected in video"
                else -> "No distinct tracking target found"
              }
            }
          )
        }
      } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) {
          _motionTrackingState.update {
            it.copy(isTracking = false, statusMessage = "Tracking stopped")
          }
        } else {
          android.util.Log.e("StudioViewModel", "Tracking failure", e)
          _motionTrackingState.update {
            it.copy(
              isTracking = false,
              errorMessage = "Tracking failed: ${e.localizedMessage ?: "Unknown error"}",
              statusMessage = "Tracking failed"
            )
          }
        }
      }
    }
  }

  fun retrackCurrentTarget() {
    val activeClip = getSelectedVideoClip() ?: return
    val currentCat = _motionTrackingState.value.activeCategory
    MotionTrackingCache.invalidateClip(activeClip.id)
    startMotionTracking(currentCat)
  }

  fun attachFaceEffect(effectType: EffectType) {
    val activeClip = getSelectedVideoClip() ?: return
    val result = _motionTrackingState.value.activeResult
    val newEffect = timelineEngine.addEffectClip(effectType)
    if (result != null && result.keyframes.isNotEmpty()) {
      val bakedKeyframes = KeyframeAnimationEngine.convertTrackingResultToClipKeyframes(
        trackingResult = result,
        clipStartTimelineMs = result.startTimestampUs / 1000L
      )
      timelineEngine.updateEffectClip(newEffect.copy(keyframes = bakedKeyframes))
    }
    _motionTrackingState.update {
      it.copy(statusMessage = "Attached ${effectType.displayName} to tracked region")
    }
  }

  fun attachTrackingToLayer(
    target: AttachmentTarget,
    followPos: Boolean = true,
    followScale: Boolean = true,
    followRot: Boolean = true
  ) {
    val activeClip = getSelectedVideoClip() ?: return
    val result = _motionTrackingState.value.activeResult
    if (result == null || result.keyframes.isEmpty()) {
      _motionTrackingState.update {
        it.copy(errorMessage = "Please track an object, face, or motion point first before attaching.")
      }
      return
    }

    val bakedKeyframes = KeyframeAnimationEngine.convertTrackingResultToClipKeyframes(
      trackingResult = result,
      clipStartTimelineMs = result.startTimestampUs / 1000L,
      followPosition = followPos,
      followScale = followScale,
      followRotation = followRot,
      offsetX = _motionTrackingState.value.settings.offsetX,
      offsetY = _motionTrackingState.value.settings.offsetY
    )

    val clipStartMs = activeClip.timelineStartMs + (result.startTimestampUs / 1000L)
    val clipDurationMs = ((result.endTimestampUs - result.startTimestampUs) / 1000L).coerceAtLeast(1000L)

    when (target) {
      AttachmentTarget.TEXT -> {
        val selectedText = timelineEngine.selectedElement.value as? SelectedTrackElement.Text
        if (selectedText != null) {
          val existing = timelineEngine.timeline.value.textClips.find { it.id == selectedText.clipId }
          if (existing != null) {
            val updated = existing.copy(keyframes = bakedKeyframes)
            timelineEngine.updateTextClip(updated)
            _motionTrackingState.update { it.copy(statusMessage = "Updated selected text keyframes with tracking") }
            return
          }
        }
        val newTextClip = TextClip(
          id = UUID.randomUUID().toString(),
          text = "Tracked Text",
          timelineStartMs = clipStartMs,
          durationMs = clipDurationMs,
          keyframes = bakedKeyframes,
          posX = bakedKeyframes.firstOrNull()?.posX ?: 0f,
          posY = bakedKeyframes.firstOrNull()?.posY ?: 0f
        )
        timelineEngine.addTextClipObject(newTextClip)
        _motionTrackingState.update { it.copy(statusMessage = "Attached new text layer to tracking path") }
      }
      AttachmentTarget.STICKER -> {
        val newSticker = timelineEngine.addStickerClip(emojiOrAsset = "✨")
        timelineEngine.updateStickerClip(
          newSticker.copy(
            keyframes = bakedKeyframes,
            posX = bakedKeyframes.firstOrNull()?.posX ?: 0f,
            posY = bakedKeyframes.firstOrNull()?.posY ?: 0f
          )
        )
        _motionTrackingState.update { it.copy(statusMessage = "Attached sticker to tracking path") }
      }
      AttachmentTarget.OVERLAY -> {
        val selectedVideo = timelineEngine.selectedElement.value as? SelectedTrackElement.Video
        if (selectedVideo != null) {
          val existingOverlay = timelineEngine.timeline.value.overlayClips.find { it.id == selectedVideo.clipId }
          if (existingOverlay != null) {
            val updated = existingOverlay.copy(keyframes = bakedKeyframes)
            timelineEngine.updateOverlayClip(updated)
            _motionTrackingState.update { it.copy(statusMessage = "Attached overlay layer to tracking path") }
            return
          }
        }
        _motionTrackingState.update { it.copy(statusMessage = "Select an overlay clip on timeline to attach motion") }
      }
      AttachmentTarget.EFFECT_BLUR -> {
        val newBlur = timelineEngine.addEffectClip(EffectType.BLUR)
        timelineEngine.updateEffectClip(newBlur.copy(keyframes = bakedKeyframes))
        _motionTrackingState.update { it.copy(statusMessage = "Attached blur effect to tracked target") }
      }
      AttachmentTarget.EFFECT_MOSAIC -> {
        val newMosaic = timelineEngine.addEffectClip(EffectType.MOSAIC)
        timelineEngine.updateEffectClip(newMosaic.copy(keyframes = bakedKeyframes))
        _motionTrackingState.update { it.copy(statusMessage = "Attached mosaic pixelate to tracked target") }
      }
      AttachmentTarget.MASK -> {
        timelineEngine.updateClipKeyframes(activeClip.id, bakedKeyframes)
        _motionTrackingState.update { it.copy(statusMessage = "Attached motion keyframes to clip mask") }
      }
    }
  }

  fun toggleMotionPathVisibility() {
    _motionTrackingState.update {
      it.copy(isRegionSelectorActive = !it.isRegionSelectorActive)
    }
  }

  fun updateSmoothingFactor(factor: Float) {
    val clamped = factor.coerceIn(0f, 0.95f)
    val curResult = _motionTrackingState.value.activeResult
    val newResult = if (curResult != null && curResult.keyframes.size > 2) {
      curResult.copy(keyframes = motionTrackerEngine.smoothKeyframes(curResult.keyframes, clamped))
    } else {
      curResult
    }
    _motionTrackingState.update {
      it.copy(
        settings = it.settings.copy(smoothingFactor = clamped),
        activeResult = newResult
      )
    }
  }

  fun updateSearchWindowFactor(factor: Float) {
    val clamped = factor.coerceIn(1.0f, 4.0f)
    _motionTrackingState.update {
      it.copy(settings = it.settings.copy(searchWindowFactor = clamped))
    }
  }

  fun manualCorrectionAtCurrentTime(currentPosMs: Long) {
    val curResult = _motionTrackingState.value.activeResult ?: return
    val currentUs = currentPosMs * 1000L
    val curRegion = _motionTrackingState.value.targetRegion

    val correctedKf = MotionKeyframe(
      timestampUs = currentUs,
      centerX = curRegion.centerX,
      centerY = curRegion.centerY,
      scaleX = (curRegion.width / 0.3f).coerceIn(0.1f, 5.0f),
      scaleY = (curRegion.height / 0.3f).coerceIn(0.1f, 5.0f),
      confidence = 1.0f
    )

    val updatedKeys = (curResult.keyframes.filterNot { kotlin.math.abs(it.timestampUs - currentUs) < 20_000L } + correctedKf)
      .sortedBy { it.timestampUs }

    val updatedResult = curResult.copy(keyframes = updatedKeys)
    _motionTrackingState.update {
      it.copy(
        activeResult = updatedResult,
        statusMessage = "Keyframe corrected at ${currentPosMs}ms"
      )
    }
  }

  fun deleteActiveTrackingData() {
    val activeClip = getSelectedVideoClip()
    if (activeClip != null) {
      MotionTrackingCache.invalidateClip(activeClip.id)
    }
    _motionTrackingState.update {
      it.copy(
        activeResult = null,
        statusMessage = "Tracking data cleared",
        progress = 0f
      )
    }
  }

  fun createNewProject(
    name: String,
    aspectRatio: AspectRatio,
    resolution: Resolution,
    fps: FrameRate,
    initialMediaClips: List<VideoClip> = emptyList()
  ) {
    val projectId = UUID.randomUUID().toString()
    _activeProjectId.value = projectId
    _activeProjectName.value = name.ifBlank { "Project ${System.currentTimeMillis() % 10000}" }
    _activeAspectRatio.value = aspectRatio
    _activeResolution.value = resolution
    _activeFps.value = fps
    _activeSampleRate.value = 48000
    _activeCanvasColor.value = 0xFF000000
    _isCanvasConfiguredByMedia.value = initialMediaClips.isNotEmpty()

    // Timeline is completely empty: no video clips, text, audio clips, overlays, stickers, or any other media
    val initialTimeline = Timeline(
      videoClips = initialMediaClips,
      overlayClips = emptyList(),
      audioClips = emptyList(),
      textClips = emptyList(),
      stickerClips = emptyList(),
      effectClips = emptyList(),
      transitions = emptyList(),
      adjustments = VideoAdjustments(),
      filter = FilterSettings(),
      aspectRatio = aspectRatio,
      canvasBackgroundColor = 0xFF000000
    )

    timelineEngine.setTimelineFps(fps.fps)
    timelineEngine.loadTimeline(initialTimeline)
    saveCurrentProject()
    _isCreatingProject.value = false
    navigateTo(AppScreen.EDITOR)
    checkMissingMedia()
  }

  /**
   * Authoritatively configures the project canvas (aspect ratio, resolution, fps)
   * based on the intrinsic dimensions and metadata of the first imported video.
   * Subsequent media imports do NOT alter the established canvas aspect ratio.
   */
  fun checkAndAutoConfigureCanvasFromMedia(
    width: Int,
    height: Int,
    rotationDegrees: Int = 0,
    frameRate: Float = 30f,
    force: Boolean = false
  ): Boolean {
    val clips = timelineEngine.timeline.value.videoClips
    // Authoritative rule: If canvas is already configured by media and has clips, and not forced, keep established canvas locked
    if (!force && _isCanvasConfiguredByMedia.value && clips.isNotEmpty()) {
      return false
    }

    val (effWidth, effHeight) = AspectRatio.resolveEffectiveDimensions(width, height, rotationDegrees)
    val detectedAspect = AspectRatio.fromDimensions(effWidth, effHeight)
    val detectedFps = when {
      frameRate >= 50f -> FrameRate.FPS_60
      frameRate in 23.5f..26.5f -> FrameRate.FPS_24
      frameRate in 24.5f..26.0f -> FrameRate.FPS_25
      else -> FrameRate.FPS_30
    }
    val detectedResolution = when (detectedAspect) {
      AspectRatio.RATIO_9_16 -> if (effWidth >= 1440 || effHeight >= 2560) Resolution.RES_VERTICAL_2K else Resolution.RES_1080P
      AspectRatio.RATIO_1_1 -> if (effWidth >= 2000 || effHeight >= 2000) Resolution.RES_SQUARE_2K else Resolution.RES_1080P
      AspectRatio.RATIO_16_9, AspectRatio.RATIO_21_9 -> if (effWidth >= 3840 || effHeight >= 2160) Resolution.RES_4K else if (effWidth >= 2560) Resolution.RES_2K else Resolution.RES_1080P
      else -> Resolution.RES_1080P
    }

    _activeAspectRatio.value = detectedAspect
    _activeResolution.value = detectedResolution
    _activeFps.value = detectedFps
    _isCanvasConfiguredByMedia.value = true
    timelineEngine.setAspectRatio(detectedAspect)
    timelineEngine.setTimelineFps(detectedFps.fps)
    saveCurrentProject()
    android.util.Log.d("StudioViewModel", "Canvas auto-configured from first imported video: ${detectedAspect.label} (${effWidth}x${effHeight}) at ${detectedFps.fps}fps")
    return true
  }

  fun createProjectFromPickedVideo(
    uri: String,
    projectName: String? = null
  ) {
    createProjectWithAutoAspect(
      uris = listOf(uri),
      name = projectName ?: "Video Project",
      isVideo = true
    )
  }

  fun createProjectWithAutoAspect(
    uris: List<String>,
    name: String = "Video Project",
    isVideo: Boolean = true
  ) {
    viewModelScope.launch {
      val appContext = getApplication<Application>().applicationContext
      val clips = withContext(Dispatchers.IO) {
        val persistentUris = MediaPersistenceManager.persistMediaList(appContext, uris)
        var runningStart = 0L
        persistentUris.mapIndexed { index, uri ->
          val meta = com.example.engine.media.MediaMetadataHelper.extractMetadata(appContext, uri)
          val duration = meta.durationMs
          val clip = VideoClip(
            uri = uri,
            name = if (meta.isVideo) "Video ${index + 1}" else "Photo ${index + 1}",
            timelineStartMs = runningStart,
            durationMs = duration,
            sourceStartMs = 0L,
            sourceEndMs = duration,
            isVideo = meta.isVideo,
            width = meta.width,
            height = meta.height,
            naturalRotation = meta.rotationDegrees,
            frameRate = meta.frameRate,
            mimeType = meta.mimeType,
            hasAudio = meta.hasAudio
          )
          runningStart += duration
          clip
        }
      }

      val firstClip = clips.firstOrNull()
      val (effW, effH) = if (firstClip != null) {
        val rot = if (firstClip.naturalRotation != 0) firstClip.naturalRotation else firstClip.rotationDegrees
        AspectRatio.resolveEffectiveDimensions(firstClip.width, firstClip.height, rot)
      } else Pair(1920, 1080)

      val detectedAspect = if (firstClip != null) {
        AspectRatio.fromDimensions(effW, effH)
      } else AspectRatio.RATIO_16_9

      val detectedFps = if (firstClip != null) {
        when {
          firstClip.frameRate >= 50f -> FrameRate.FPS_60
          firstClip.frameRate in 23.5f..26.5f -> FrameRate.FPS_24
          firstClip.frameRate in 24.5f..26.0f -> FrameRate.FPS_25
          else -> FrameRate.FPS_30
        }
      } else FrameRate.FPS_30

      val detectedResolution = if (firstClip != null) {
        when (detectedAspect) {
          AspectRatio.RATIO_9_16 -> if (effW >= 1440 || effH >= 2560) Resolution.RES_VERTICAL_2K else Resolution.RES_1080P
          AspectRatio.RATIO_1_1 -> if (effW >= 2000 || effH >= 2000) Resolution.RES_SQUARE_2K else Resolution.RES_1080P
          AspectRatio.RATIO_16_9, AspectRatio.RATIO_21_9 -> if (effW >= 3840 || effH >= 2160) Resolution.RES_4K else if (effW >= 2560) Resolution.RES_2K else Resolution.RES_1080P
          else -> Resolution.RES_1080P
        }
      } else Resolution.RES_1080P

      createNewProject(
        name = name,
        aspectRatio = detectedAspect,
        resolution = detectedResolution,
        fps = detectedFps,
        initialMediaClips = clips
      )
    }
  }

  fun createProjectWithMedia(
    name: String,
    uris: List<String>,
    isVideo: Boolean = true,
    aspectRatio: AspectRatio? = null
  ) {
    if (aspectRatio == null) {
      createProjectWithAutoAspect(uris = uris, name = name, isVideo = isVideo)
    } else {
      _isCreatingProject.value = true
      viewModelScope.launch {
        val appContext = getApplication<Application>().applicationContext
        val clips = withContext(Dispatchers.IO) {
          val persistentUris = MediaPersistenceManager.persistMediaList(appContext, uris)
          var runningStart = 0L
          persistentUris.mapIndexed { index, uri ->
            val meta = com.example.engine.media.MediaMetadataHelper.extractMetadata(appContext, uri)
            val duration = meta.durationMs
            val clip = VideoClip(
              uri = uri,
              name = if (meta.isVideo) "Video ${index + 1}" else "Photo ${index + 1}",
              timelineStartMs = runningStart,
              durationMs = duration,
              sourceStartMs = 0L,
              sourceEndMs = duration,
              isVideo = meta.isVideo,
              width = meta.width,
              height = meta.height,
              naturalRotation = meta.rotationDegrees,
              frameRate = meta.frameRate,
              mimeType = meta.mimeType,
              hasAudio = meta.hasAudio
            )
            runningStart += duration
            clip
          }
        }
        createNewProject(
          name = name,
          aspectRatio = aspectRatio,
          resolution = Resolution.RES_1080P,
          fps = FrameRate.FPS_30,
          initialMediaClips = clips
        )
      }
    }
  }

  fun loadProject(project: ProjectEntity) {
    _activeProjectId.value = project.id
    _activeProjectName.value = project.name
    _activeAspectRatio.value = AspectRatio.values().find { it.label == project.aspectRatio } ?: AspectRatio.RATIO_9_16
    _activeResolution.value = Resolution.values().find { it.label == project.resolution } ?: Resolution.RES_1080P
    _activeFps.value = FrameRate.values().find { it.fps == project.fps } ?: FrameRate.FPS_30
    _activeSampleRate.value = project.sampleRate
    _activeCanvasColor.value = project.canvasColor

    val pkg = TimelineSerializer.fromPackageJson(project.timelineJson)
    val loadedTimeline = pkg?.timeline ?: TimelineSerializer.fromJson(project.timelineJson)
    if (pkg != null && pkg.settings.sampleRateHz > 0) {
      _activeSampleRate.value = pkg.settings.sampleRateHz
      _activeCanvasColor.value = pkg.settings.canvasBackgroundColor
      _activeAspectRatio.value = pkg.settings.aspectRatio
      _activeResolution.value = pkg.settings.resolution
      _activeFps.value = pkg.settings.fps
    }
    timelineEngine.setTimelineFps(_activeFps.value.fps)
    timelineEngine.setAspectRatio(_activeAspectRatio.value)
    timelineEngine.setCanvasBackgroundColor(_activeCanvasColor.value)
    timelineEngine.loadTimeline(loadedTimeline.copy(
      aspectRatio = _activeAspectRatio.value,
      canvasBackgroundColor = _activeCanvasColor.value
    ))
    _isCanvasConfiguredByMedia.value = loadedTimeline.videoClips.isNotEmpty()
    _saveState.value = ProjectSaveState(ProjectSaveStatus.SAVED, project.lastEditedTime)
    navigateTo(AppScreen.EDITOR)
    checkMissingMedia()
  }

  // Template Creator Mode
  private val _isTemplateCreatorMode = MutableStateFlow(false)
  val isTemplateCreatorMode: StateFlow<Boolean> = _isTemplateCreatorMode.asStateFlow()

  fun enterTemplateCreatorMode() {
    _isTemplateCreatorMode.value = true
    createNewProject(
      name = "Template Project ${System.currentTimeMillis() % 1000}",
      aspectRatio = AspectRatio.RATIO_9_16,
      resolution = Resolution.RES_1080P,
      fps = FrameRate.FPS_30
    )
  }

  fun exitTemplateCreatorMode() {
    _isTemplateCreatorMode.value = false
  }

  fun applyTemplate(
    template: VideoTemplate,
    mediaReplacements: Map<String, String> = emptyMap(),
    textReplacements: Map<String, String> = emptyMap()
  ) {
    _isTemplateCreatorMode.value = false
    com.example.data.firebase.FirebaseTemplateManager.recordTemplateUse(template.id, template.creatorId)
    val projectId = UUID.randomUUID().toString()
    _activeProjectId.value = projectId
    _activeProjectName.value = "${template.title} Project"
    _activeAspectRatio.value = template.aspectRatio
    _activeResolution.value = template.resolution
    _activeFps.value = template.fps
    _activeSampleRate.value = 48000
    _activeCanvasColor.value = 0xFF000000

    val generatedTimeline = template.createTimeline(mediaReplacements, textReplacements).copy(
      aspectRatio = template.aspectRatio,
      canvasBackgroundColor = 0xFF000000
    )
    timelineEngine.setTimelineFps(template.fps.fps)
    timelineEngine.setAspectRatio(template.aspectRatio)
    timelineEngine.setCanvasBackgroundColor(0xFF000000)
    timelineEngine.loadTimeline(generatedTimeline)
    _isCanvasConfiguredByMedia.value = true
    saveCurrentProject()
    navigateTo(AppScreen.EDITOR)
    checkMissingMedia()
  }

  fun saveCurrentProject(isManual: Boolean = false) {
    val id = _activeProjectId.value
    if (id.isBlank()) return
    viewModelScope.launch {
      _saveState.value = _saveState.value.copy(status = ProjectSaveStatus.SAVING)
      val currentTimeline = timelineEngine.timeline.value.copy(
        aspectRatio = _activeAspectRatio.value,
        canvasBackgroundColor = _activeCanvasColor.value
      )
      val missingList = MediaRelinkManager.detectMissingMedia(getApplication(), currentTimeline)
      _missingMediaList.value = missingList

      repository.saveProject(
        id = id,
        name = _activeProjectName.value,
        durationMs = currentTimeline.totalDurationMs,
        thumbnailPath = "",
        aspectRatio = _activeAspectRatio.value.label,
        resolution = _activeResolution.value.label,
        fps = _activeFps.value.fps,
        timeline = currentTimeline,
        isDraft = false,
        sampleRate = _activeSampleRate.value,
        canvasColor = _activeCanvasColor.value,
        hasMissingMedia = missingList.isNotEmpty()
      )
      _saveState.value = ProjectSaveState(ProjectSaveStatus.SAVED, System.currentTimeMillis())
    }
  }

  fun manualSaveProject() {
    saveCurrentProject(isManual = true)
  }

  fun restoreCrashRecoverySession() {
    viewModelScope.launch {
      val session: CrashRecoveryEntity = repository.getActiveRecoverySession() ?: return@launch
      _activeProjectId.value = session.projectId.ifBlank { UUID.randomUUID().toString() }
      _activeProjectName.value = session.projectName.ifBlank { "Recovered Project" }
      val pkg = TimelineSerializer.fromPackageJson(session.timelineJson)
      if (pkg != null && (pkg.timeline.videoClips.isNotEmpty() || pkg.timeline.audioClips.isNotEmpty() || pkg.timeline.textClips.isNotEmpty())) {
        _activeAspectRatio.value = pkg.settings.aspectRatio
        _activeResolution.value = pkg.settings.resolution
        _activeFps.value = pkg.settings.fps
        _activeSampleRate.value = pkg.settings.sampleRateHz
        _activeCanvasColor.value = pkg.settings.canvasBackgroundColor
        timelineEngine.setTimelineFps(pkg.settings.fps.fps)
        timelineEngine.setAspectRatio(pkg.settings.aspectRatio)
        timelineEngine.setCanvasBackgroundColor(pkg.settings.canvasBackgroundColor)
        timelineEngine.loadTimeline(pkg.timeline.copy(
          aspectRatio = pkg.settings.aspectRatio,
          canvasBackgroundColor = pkg.settings.canvasBackgroundColor
        ))
      } else {
        val recoveredTimeline = TimelineSerializer.fromJson(session.timelineJson)
        timelineEngine.setTimelineFps(_activeFps.value.fps)
        timelineEngine.setAspectRatio(_activeAspectRatio.value)
        timelineEngine.setCanvasBackgroundColor(_activeCanvasColor.value)
        timelineEngine.loadTimeline(recoveredTimeline)
      }
      _saveState.value = ProjectSaveState(ProjectSaveStatus.UNSAVED, session.timestamp)
      navigateTo(AppScreen.EDITOR)
      checkMissingMedia()
    }
  }

  fun discardCrashRecoverySession() {
    viewModelScope.launch {
      repository.clearCrashRecoverySession()
    }
  }

  fun restorePreviousProject() {
    viewModelScope.launch {
      val mostRecent = allProjects.value.firstOrNull()
      if (mostRecent != null) {
        loadProject(mostRecent)
      }
    }
  }

  fun reorderVideoClips(fromIndex: Int, toIndex: Int) {
    val clips = timelineEngine.timeline.value.videoClips
    if (fromIndex in clips.indices && toIndex in clips.indices && fromIndex != toIndex) {
      val movedClip = clips[fromIndex]
      val success = timelineEngine.reorderVideoClips(fromIndex, toIndex)
      if (success) {
        val updatedClips = timelineEngine.timeline.value.videoClips
        val newClip = updatedClips.find { it.id == movedClip.id }
        if (newClip != null) {
          timelineEngine.selectElement(SelectedTrackElement.Video(newClip.id))
          timelineEngine.setPosition(newClip.timelineStartMs)
          playbackEngine.seekTo(newClip.timelineStartMs)
        }
      }
    }
  }

  fun checkMissingMedia() {
    viewModelScope.launch(Dispatchers.IO) {
      val missing = MediaRelinkManager.detectMissingMedia(getApplication(), timelineEngine.timeline.value)
      _missingMediaList.value = missing
      if (_activeProjectId.value.isNotBlank()) {
        repository.updateMissingMediaStatus(_activeProjectId.value, missing.isNotEmpty())
      }
    }
  }

  fun relinkMedia(clipId: String, newUri: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val currentTimeline = timelineEngine.timeline.value
      val updated = MediaRelinkManager.relinkClip(getApplication(), currentTimeline, clipId, newUri)
      withContext(Dispatchers.Main) {
        timelineEngine.replaceTimelineKeepingState(updated)
        saveCurrentProject(isManual = true)
        checkMissingMedia()
      }
    }
  }

  fun renameProject(id: String, newName: String) {
    viewModelScope.launch {
      repository.renameProject(id, newName)
      if (_activeProjectId.value == id) {
        _activeProjectName.value = newName
      }
    }
  }

  fun duplicateProject(id: String) {
    viewModelScope.launch {
      repository.duplicateProject(id)
    }
  }

  // --- Precision Video Trimming (Media3) ---

  val trimPlaybackPosition = playbackEngine.trimPlaybackPositionMs

  fun previewClipTrim(clip: VideoClip, startMs: Long, endMs: Long, loop: Boolean = true) {
    playbackEngine.previewTrimRange(clip, startMs, endMs, loop)
  }

  fun seekTrimPreview(offsetFromStartMs: Long) {
    playbackEngine.seekTrimPreview(offsetFromStartMs)
  }

  fun seekTrimPreviewToSourceMs(sourceTimeMs: Long) {
    playbackEngine.seekTrimPreviewToSourceMs(sourceTimeMs)
  }

  fun stepTrimFrame(forward: Boolean) {
    playbackEngine.stepTrimFrame(forward)
  }

  fun toggleTrimPlayPause() {
    playbackEngine.toggleTrimPlayPause()
  }

  fun exitTrimPreview() {
    playbackEngine.exitTrimPreview()
  }

  fun beginMoveClip(clipId: String) {
    timelineEngine.beginContinuousAction(TimelineActionType.MOVE_CLIP, "Move Clip", clipId)
  }

  fun endMoveClip() {
    timelineEngine.endContinuousAction()
  }

  fun beginTrimClipLeft(clipId: String) {
    timelineEngine.beginContinuousAction(TimelineActionType.TRIM_LEFT, "Trim Start", clipId)
  }

  fun endTrimClipLeft() {
    timelineEngine.endContinuousAction()
  }

  fun beginTrimClipRight(clipId: String) {
    timelineEngine.beginContinuousAction(TimelineActionType.TRIM_RIGHT, "Trim End", clipId)
  }

  fun endTrimClipRight() {
    timelineEngine.endContinuousAction()
  }

  fun moveClipByDelta(clipId: String, deltaMs: Long, snap: Boolean = true) {
    timelineEngine.moveClipByDelta(clipId, deltaMs, snap)
  }

  fun trimClipLeftByDelta(clipId: String, deltaMs: Long, snap: Boolean = true) {
    timelineEngine.trimClipLeftByDelta(clipId, deltaMs, snap)
    val clip = timelineEngine.timeline.value.videoClips.find { it.id == clipId }
      ?: timelineEngine.timeline.value.overlayClips.find { it.id == clipId }
    if (clip != null) {
      playbackEngine.seekTo(clip.timelineStartMs)
    }
  }

  fun trimClipRightByDelta(clipId: String, deltaMs: Long, snap: Boolean = true) {
    timelineEngine.trimClipRightByDelta(clipId, deltaMs, snap)
    val clip = timelineEngine.timeline.value.videoClips.find { it.id == clipId }
      ?: timelineEngine.timeline.value.overlayClips.find { it.id == clipId }
    if (clip != null) {
      playbackEngine.seekTo((clip.timelineStartMs + clip.durationMs - 1L).coerceAtLeast(clip.timelineStartMs))
    }
  }

  fun applyClipTrim(clipId: String, newSourceStartMs: Long, newSourceEndMs: Long) {
    val success = timelineEngine.trimClipSourceRange(clipId, newSourceStartMs, newSourceEndMs, rippleContiguous = true)
    playbackEngine.exitTrimPreview()
    if (success) {
      val clip = timelineEngine.timeline.value.videoClips.find { it.id == clipId }
      if (clip != null) {
        timelineEngine.setPosition(clip.timelineStartMs)
        playbackEngine.seekTo(clip.timelineStartMs)
      }
    }
  }

  fun resetClipTrim(clipId: String) {
    val success = timelineEngine.resetClipTrim(clipId)
    playbackEngine.exitTrimPreview()
    if (success) {
      val clip = timelineEngine.timeline.value.videoClips.find { it.id == clipId }
      if (clip != null) {
        timelineEngine.setPosition(clip.timelineStartMs)
        playbackEngine.seekTo(clip.timelineStartMs)
      }
    }
  }

  fun setClipInPointAtPlayhead(clipId: String) {
    timelineEngine.setClipInPointAtPlayhead(clipId)
    playbackEngine.exitTrimPreview()
  }

  fun setClipOutPointAtPlayhead(clipId: String) {
    timelineEngine.setClipOutPointAtPlayhead(clipId)
    playbackEngine.exitTrimPreview()
  }

  // --- Real-time Audio Waveform & Peak/Silence Actions ---

  private val _waveformStyle = MutableStateFlow(com.example.ui.components.timeline.WaveformStyle.MIRRORED_BARS)
  val waveformStyle: StateFlow<com.example.ui.components.timeline.WaveformStyle> = _waveformStyle.asStateFlow()

  fun cycleWaveformStyle() {
    val current = _waveformStyle.value
    val next = when (current) {
      com.example.ui.components.timeline.WaveformStyle.MIRRORED_BARS -> com.example.ui.components.timeline.WaveformStyle.SOLID_ENVELOPE
      com.example.ui.components.timeline.WaveformStyle.SOLID_ENVELOPE -> com.example.ui.components.timeline.WaveformStyle.BASELINE_UPWARD
      com.example.ui.components.timeline.WaveformStyle.BASELINE_UPWARD -> com.example.ui.components.timeline.WaveformStyle.MIRRORED_BARS
    }
    _waveformStyle.value = next
  }

  fun setWaveformStyle(style: com.example.ui.components.timeline.WaveformStyle) {
    _waveformStyle.value = style
  }

  fun jumpToNextAudioPeak() {
    val jumped = timelineEngine.jumpToNextAudioPeak()
    if (jumped) {
      playbackEngine.seekTo(timelineEngine.currentPositionMs.value)
    }
  }

  fun jumpToPrevAudioPeak() {
    val jumped = timelineEngine.jumpToPrevAudioPeak()
    if (jumped) {
      playbackEngine.seekTo(timelineEngine.currentPositionMs.value)
    }
  }

  fun jumpToNextAudioSilence() {
    val jumped = timelineEngine.jumpToNextAudioSilence()
    if (jumped) {
      playbackEngine.seekTo(timelineEngine.currentPositionMs.value)
    }
  }

  fun jumpToPrevAudioSilence() {
    val jumped = timelineEngine.jumpToPrevAudioSilence()
    if (jumped) {
      playbackEngine.seekTo(timelineEngine.currentPositionMs.value)
    }
  }

  fun removeSilenceInSelectedAudioClip() {
    val selectedId = (timelineEngine.selectedElement.value as? SelectedTrackElement.Audio)?.clipId
    if (selectedId != null) {
      val removed = timelineEngine.removeSilenceFromAudioClip(selectedId)
      if (removed) {
        playbackEngine.seekTo(timelineEngine.currentPositionMs.value)
      }
    }
  }

  fun deleteProject(id: String) {
    viewModelScope.launch {
      repository.deleteProject(id)
    }
  }

  fun deleteExportedVideo(id: String) {
    viewModelScope.launch {
      repository.deleteExportedVideo(id)
    }
  }

  fun updateProjectSettings(
    aspectRatio: AspectRatio,
    resolution: Resolution,
    fps: FrameRate,
    sampleRate: Int = _activeSampleRate.value,
    canvasColor: Long = _activeCanvasColor.value
  ) {
    _activeAspectRatio.value = aspectRatio
    _activeResolution.value = resolution
    _activeFps.value = fps
    _activeSampleRate.value = sampleRate
    _activeCanvasColor.value = canvasColor
    _isCanvasConfiguredByMedia.value = true
    // Authoritatively synchronize timeline settings
    timelineEngine.setAspectRatio(aspectRatio)
    timelineEngine.setTimelineFps(fps.fps)
    timelineEngine.setCanvasBackgroundColor(canvasColor)
    saveCurrentProject()
  }

  private fun startAutoSave() {
    autoSaveJob?.cancel()
    autoSaveJob = viewModelScope.launch {
      while (isActive) {
        delay(15000L) // 15s auto-save
        if (_activeProjectId.value.isNotBlank() && _currentScreen.value == AppScreen.EDITOR) {
          saveCurrentProject()
        }
      }
    }
  }

  // --- AI Operations & Auto Captions Engine ---

  fun runAIAutoCaptions(language: String = "English") {
    viewModelScope.launch {
      _isAIBusy.value = true
      _aiStatusMessage.value = "Captions Engine: Analyzing audio & transcribing..."
      try {
        val timeline = timelineEngine.timeline.value
        val mediaUriStr = timeline.audioClips.firstOrNull { it.uri.isNotBlank() }?.uri
          ?: timeline.videoClips.firstOrNull { it.uri.isNotBlank() }?.uri

        var generatedWithEngine = false
        if (mediaUriStr != null) {
          val mediaFile = File(mediaUriStr)
          val mediaUri = if (mediaUriStr.startsWith("content://") || mediaUriStr.startsWith("file://")) {
            android.net.Uri.parse(mediaUriStr)
          } else if (mediaFile.exists()) {
            android.net.Uri.fromFile(mediaFile)
          } else null

          if (mediaUri != null) {
            try {
              val langTag = when (language.lowercase()) {
                "spanish" -> "es"
                "french" -> "fr"
                "german" -> "de"
                "chinese" -> "zh"
                "japanese" -> "ja"
                "arabic" -> "ar"
                "urdu" -> "ur"
                "hindi" -> "hi"
                else -> "en"
              }
              val captionsGraph = CaptionsGraph.get(getApplication())
              val project = captionsGraph.autoEngine.generate(
                mediaUri = mediaUri,
                options = AutoCaptionOptions(languageTag = langTag)
              ) { state ->
                when (state) {
                  is CaptionGenerationState.ExtractingAudio -> _aiStatusMessage.value = "Captions Engine: Extracting audio..."
                  is CaptionGenerationState.Transcribing -> _aiStatusMessage.value = "Captions Engine: Transcribing speech (${(state.progress * 100).toInt()}%)..."
                  is CaptionGenerationState.Diarizing -> _aiStatusMessage.value = "Captions Engine: Diarizing speakers..."
                  is CaptionGenerationState.Segmenting -> _aiStatusMessage.value = "Captions Engine: Segmenting ${state.clipCount} clips..."
                  else -> {}
                }
              }
              val primaryTrack = project.tracks.firstOrNull()
              if (primaryTrack != null && primaryTrack.clips.isNotEmpty()) {
                val newClips = primaryTrack.clips.map { clip ->
                  val startMs = (clip.timing.start.micros / 1000L).coerceAtLeast(0L)
                  val durMs = ((clip.timing.end.micros - clip.timing.start.micros) / 1000L).coerceAtLeast(500L)
                  val wordTimings = clip.words.map { w ->
                    val wStartMs = ((w.start.micros - clip.timing.start.micros) / 1000L).coerceAtLeast(0L)
                    val wDurMs = ((w.end.micros - w.start.micros) / 1000L).coerceAtLeast(100L)
                    WordTiming(w.text, wStartMs, wDurMs)
                  }
                  TextClip(
                    id = UUID.randomUUID().toString(),
                    text = clip.displayText,
                    timelineStartMs = startMs,
                    durationMs = durMs,
                    words = wordTimings
                  )
                }
                val currentList = timelineEngine.timeline.value.textClips.toMutableList()
                currentList.addAll(newClips)
                timelineEngine.replaceTimelineKeepingState(timelineEngine.timeline.value.copy(textClips = currentList))
                _aiStatusMessage.value = "Generated ${newClips.size} auto captions via Captions Engine!"
                generatedWithEngine = true
              }
            } catch (e: Exception) {
              // Fallback to aiTools if engine extraction encounters format discrepancy
            }
          }
        }

        if (!generatedWithEngine) {
          val result = aiTools.generateAutoCaptions(timelineEngine.timeline.value, language)
          val captions = result.getOrThrow()
          if (captions.isEmpty()) {
            _aiStatusMessage.value = "No spoken words detected in imported audio."
          } else {
            val currentList = timelineEngine.timeline.value.textClips.toMutableList()
            currentList.addAll(captions)
            timelineEngine.replaceTimelineKeepingState(timelineEngine.timeline.value.copy(textClips = currentList))
            _aiStatusMessage.value = "Generated ${captions.size} auto captions successfully!"
          }
        }
      } catch (e: Exception) {
        _aiStatusMessage.value = e.message ?: "AI Captions unavailable. Configure backend/API credentials."
      } finally {
        _isAIBusy.value = false
      }
    }
  }

  fun importSubtitlesFromText(subtitleText: String) {
    val cues = SubtitleImporter.autoDetectAndParse(subtitleText)
    if (cues.isEmpty()) return
    val importedClips = cues.map { cue ->
      val startMs = (cue.startUs / 1000L).coerceAtLeast(0L)
      val durationMs = ((cue.endUs - cue.startUs) / 1000L).coerceAtLeast(500L)
      val text = cue.lines.joinToString(" ")
      val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
      val wordTimings = if (words.isNotEmpty()) {
        val perWord = durationMs / words.size
        words.mapIndexed { idx, w ->
          WordTiming(w, idx * perWord, perWord)
        }
      } else emptyList()
      TextClip(
        id = UUID.randomUUID().toString(),
        text = text,
        timelineStartMs = startMs,
        durationMs = durationMs,
        words = wordTimings
      )
    }
    val current = timelineEngine.timeline.value.textClips.toMutableList()
    current.addAll(importedClips)
    timelineEngine.replaceTimelineKeepingState(timelineEngine.timeline.value.copy(textClips = current))
    _aiStatusMessage.value = "Imported ${importedClips.size} subtitle cues!"
  }

  fun exportSubtitles(format: SubtitleFormat = SubtitleFormat.SRT): String {
    val textClips = timelineEngine.timeline.value.textClips.sortedBy { it.timelineStartMs }
    val cues = textClips.mapIndexed { idx, clip ->
      val startUs = clip.timelineStartMs * 1000L
      val endUs = (clip.timelineStartMs + clip.durationMs) * 1000L
      SubtitleCue(idx + 1, startUs, endUs, listOf(clip.text))
    }
    return SubtitleExporter.export(cues, null, format)
  }

  fun exportSubtitlesToFile(format: SubtitleFormat = SubtitleFormat.SRT): File {
    val ext = when (format) {
      SubtitleFormat.SRT -> "srt"
      SubtitleFormat.VTT -> "vtt"
      SubtitleFormat.ASS -> "ass"
      SubtitleFormat.TXT -> "txt"
    }
    val content = exportSubtitles(format)
    val file = File(getApplication<Application>().cacheDir, "exported_subtitles_${System.currentTimeMillis()}.$ext")
    file.writeText(content)
    return file
  }

  fun runAITranslateCaptions(targetLanguage: String) {
    viewModelScope.launch {
      _isAIBusy.value = true
      _aiStatusMessage.value = "AI translating captions to $targetLanguage (preserving timings)..."
      try {
        val result = aiTools.translateCaptions(timelineEngine.timeline.value.textClips, targetLanguage)
        val translated = result.getOrThrow()
        timelineEngine.replaceTimelineKeepingState(timelineEngine.timeline.value.copy(textClips = translated))
        _aiStatusMessage.value = "Captions translated to $targetLanguage!"
      } catch (e: Exception) {
        _aiStatusMessage.value = e.message ?: "Translation unavailable."
      } finally {
        _isAIBusy.value = false
      }
    }
  }

  fun runAIBackgroundRemoval(inputBitmap: Bitmap, onResult: (Bitmap, Bitmap) -> Unit) {
    viewModelScope.launch {
      _isAIBusy.value = true
      _aiStatusMessage.value = "AI computing color clustering and edge alpha matting..."
      try {
        val cutoutRes = aiTools.removeBackground(inputBitmap)
        val maskRes = aiTools.generateAlphaMask(inputBitmap)
        val cutout = cutoutRes.getOrThrow()
        val mask = maskRes.getOrThrow()
        onResult(cutout, mask)
        _aiStatusMessage.value = "Background removal complete!"
      } catch (e: Exception) {
        _aiStatusMessage.value = e.message ?: "Background removal failed."
      } finally {
        _isAIBusy.value = false
      }
    }
  }

  fun runAINoiseReduction() {
    viewModelScope.launch {
      _isAIBusy.value = true
      _aiStatusMessage.value = "AI Noise Reduction: Sampling noise floor & applying spectral suppression..."
      try {
        val timeline = timelineEngine.timeline.value
        var audioFile: File? = null
        val firstAudio = timeline.audioClips.firstOrNull()
        if (firstAudio != null && firstAudio.uri.isNotBlank()) {
          val candidate = File(firstAudio.uri)
          if (candidate.exists() && candidate.length() > 0L) audioFile = candidate
        }
        if (audioFile == null && timeline.videoClips.isNotEmpty()) {
          audioFile = audioEngine.extractAudioFromVideo(timeline.videoClips.first().uri)
        }

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
          throw IllegalStateException("No audio source found on timeline to denoise. Please import a clip with audio.")
        }

        val denoisedResult = aiTools.reduceAudioNoise(audioFile)
        val denoisedFile = denoisedResult.getOrThrow()

        val newAudioClip = AudioClip(
          id = UUID.randomUUID().toString(),
          title = "Denoised Audio",
          uri = denoisedFile.absolutePath,
          timelineStartMs = 0L,
          durationMs = timeline.totalDurationMs.coerceAtLeast(3000L),
          volume = 1.0f
        )
        val updatedAudioClips = timeline.audioClips.toMutableList().apply { add(newAudioClip) }
        timelineEngine.replaceTimelineKeepingState(timeline.copy(audioClips = updatedAudioClips))
        _aiStatusMessage.value = "Noise reduction applied to timeline!"
      } catch (e: Exception) {
        _aiStatusMessage.value = e.message ?: "Noise reduction failed."
      } finally {
        _isAIBusy.value = false
      }
    }
  }

  fun runAITextToSpeech(text: String, pitch: Float = 1.0f, speed: Float = 1.0f) {
    viewModelScope.launch {
      _isAIBusy.value = true
      _aiStatusMessage.value = "Synthesizing actual voice audio..."
      try {
        val result = aiTools.synthesizeSpeech(text, pitch, speed)
        val file = result.getOrThrow()
        val durationMs = ((text.split(" ").size / (2.5f * speed)) * 1000L).toLong().coerceIn(1500L, 30000L)
        val newAudioClip = AudioClip(
          id = UUID.randomUUID().toString(),
          title = "AI Voice: ${text.take(20)}...",
          uri = file.absolutePath,
          timelineStartMs = timelineEngine.currentPositionMs.value,
          durationMs = durationMs,
          volume = 1.0f
        )
        val currentAudio = timelineEngine.timeline.value.audioClips.toMutableList().apply { add(newAudioClip) }
        timelineEngine.replaceTimelineKeepingState(timelineEngine.timeline.value.copy(audioClips = currentAudio))
        _aiStatusMessage.value = "AI Voice audio added to timeline audio track!"
      } catch (e: Exception) {
        _aiStatusMessage.value = e.message ?: "Voice synthesis failed."
      } finally {
        _isAIBusy.value = false
      }
    }
  }

  fun runAIHighlightAnalysis() {
    viewModelScope.launch {
      _isAIBusy.value = true
      _aiStatusMessage.value = "AI scanning visual motion and highlight moments..."
      try {
        val result = aiTools.analyzeHighlights(timelineEngine.timeline.value)
        val highlights = result.getOrThrow()
        _aiHighlights.value = highlights
        _aiStatusMessage.value = "Found ${highlights.size} optimal scene moments!"
      } catch (e: Exception) {
        _aiStatusMessage.value = e.message ?: "Highlight analysis unavailable."
      } finally {
        _isAIBusy.value = false
      }
    }
  }

  fun runAIAutoEdit() {
    viewModelScope.launch {
      _isAIBusy.value = true
      _aiStatusMessage.value = "AI Auto-Edit: Analyzing clips, beat synchronization & pacing..."
      try {
        val clips = timelineEngine.timeline.value.videoClips
        val result = aiTools.autoEditMontage(clips)
        val editedTimeline = result.getOrThrow()
        timelineEngine.replaceTimelineKeepingState(editedTimeline)
        _aiStatusMessage.value = "Montage generated with transitions & timing!"
      } catch (e: Exception) {
        _aiStatusMessage.value = e.message ?: "Auto-edit failed."
      } finally {
        _isAIBusy.value = false
      }
    }
  }

  // --- Export Operation ---

  fun startExport(config: ExportConfig) {
    timelineEngine.pause()
    navigateTo(AppScreen.EXPORT)
    viewModelScope.launch(Dispatchers.Default) {
      runCatching {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND)
      }
      val timelineSnapshot = timelineEngine.timeline.value
      val outputFile = File(
        getApplication<Application>().cacheDir,
        "ah_studio_" + System.currentTimeMillis() + ".mp4"
      )

      videoExporter.beginExternalExport(config)
      val progressJob = launch {
        professionalExportEngine.progress.collect { progress ->
          videoExporter.updateExternalExportProgress(progress.fraction, progress.message)
        }
      }

      try {
        val result = professionalExportEngine.export(
          projectName = _activeProjectName.value,
          timeline = timelineSnapshot,
          config = config,
          outputFile = outputFile,
          requireAudio = timelineSnapshot.audioClips.isNotEmpty() ||
            timelineSnapshot.videoClips.any { it.hasAudio }
        )

        val verifiedFile = result.getOrElse { error ->
          videoExporter.failExternalExport(error.message ?: "Export failed.")
          return@launch
        }

        val saveResult = com.example.engine.media.GalleryMediaSaver.saveVideoToGallery(
          context = getApplication(),
          sourceFile = verifiedFile,
          title = _activeProjectName.value
        )

        if (!saveResult.isSavedToPublicGallery) {
          videoExporter.failExternalExport("Export completed, but the video could not be saved to the device Gallery.")
          return@launch
        }

        val finalFile = saveResult.file
        repository.recordExport(
          projectId = _activeProjectId.value,
          title = _activeProjectName.value + ".mp4",
          filePath = finalFile.absolutePath,
          durationMs = timelineSnapshot.totalDurationMs,
          resolution = config.resolution.label,
          fps = config.frameRate.fps,
          fileSizeBytes = finalFile.length()
        )
        videoExporter.completeExternalExport(finalFile, timelineSnapshot.totalDurationMs)
        if (verifiedFile.absolutePath != finalFile.absolutePath) {
          verifiedFile.delete()
        }
      } catch (t: Throwable) {
        videoExporter.failExternalExport(t.message ?: "Export failed.")
      } finally {
        progressJob.cancel()
      }
    }
  }

  // ==========================================
  // AR Filter & Face Mesh State
  // ==========================================
  private val _activeArFilter = MutableStateFlow<com.example.ui.components.ar.ArFilterItem?>(null)
  val activeArFilter: StateFlow<com.example.ui.components.ar.ArFilterItem?> = _activeArFilter.asStateFlow()

  private val _showFaceMeshGrid = MutableStateFlow(false)
  val showFaceMeshGrid: StateFlow<Boolean> = _showFaceMeshGrid.asStateFlow()

  private val _arFilterScale = MutableStateFlow(1.0f)
  val arFilterScale: StateFlow<Float> = _arFilterScale.asStateFlow()

  private val _arFilterOffsetY = MutableStateFlow(0.0f)
  val arFilterOffsetY: StateFlow<Float> = _arFilterOffsetY.asStateFlow()

  private val _arFilterOpacity = MutableStateFlow(1.0f)
  val arFilterOpacity: StateFlow<Float> = _arFilterOpacity.asStateFlow()

  fun selectArFilter(filter: com.example.ui.components.ar.ArFilterItem?) {
    _activeArFilter.value = filter
    if (filter != null) {
      _arFilterScale.value = filter.defaultScale
      _arFilterOffsetY.value = filter.defaultOffsetY
      if (filter.isMeshOverlay) {
        _showFaceMeshGrid.value = true
      }
    }
  }

  fun setShowFaceMeshGrid(enabled: Boolean) {
    _showFaceMeshGrid.value = enabled
  }

  fun setArFilterScale(scale: Float) {
    _arFilterScale.value = scale
  }

  fun setArFilterOffsetY(offsetY: Float) {
    _arFilterOffsetY.value = offsetY
  }

  fun setArFilterOpacity(opacity: Float) {
    _arFilterOpacity.value = opacity
  }

  fun applyArFilterToTimeline() {
    val filter = _activeArFilter.value ?: return
    val clip = getSelectedVideoClip() ?: return
    val effectType = when (filter.id) {
      "ar_privacy_mosaic" -> com.example.domain.model.EffectType.MOSAIC
      "ar_neon_crown", "ar_angel_halo" -> com.example.domain.model.EffectType.HALO_GLOW
      else -> com.example.domain.model.EffectType.BLUR
    }
    attachFaceEffect(effectType)
  }

  // ==========================================
  // Multi-Layer Operations (Z-Index, Lock, Hide, Duplicate, Delete)
  // ==========================================
  fun bringLayerForward(clipId: String): Boolean = timelineEngine.bringLayerForward(clipId)
  fun sendLayerBackward(clipId: String): Boolean = timelineEngine.sendLayerBackward(clipId)
  fun bringLayerToFront(clipId: String): Boolean = timelineEngine.bringLayerToFront(clipId)
  fun sendLayerToBack(clipId: String): Boolean = timelineEngine.sendLayerToBack(clipId)
  fun toggleClipLock(clipId: String) = timelineEngine.toggleClipLock(clipId)
  fun toggleClipHide(clipId: String) = timelineEngine.toggleClipHide(clipId)
  fun duplicateClip(clipId: String): Boolean = timelineEngine.duplicateClips(setOf(clipId))
  fun deleteClip(clipId: String): Boolean = timelineEngine.deleteClips(setOf(clipId))

  override fun onCleared() {
    super.onCleared()
    autoSaveJob?.cancel()
    playbackEngine.release()
    audioEngine.release()
    videoExporter.release()
    proxyMediaEngine.release()
    compositionEngine.releaseGpu()
  }
}
