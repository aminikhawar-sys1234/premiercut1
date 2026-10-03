package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.graphics.asImageBitmap
import com.example.engine.media.VideoThumbnailManager
import com.example.ui.components.text.*
import com.example.ui.components.effects.*
import com.example.engine.effects.registry.*
import com.example.ui.components.navigation.*
import coil.compose.AsyncImage
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.LayoutInflater
import com.example.R
import com.example.engine.composition.VideoEffectRenderer
import com.example.data.presets.StockMediaCatalog
import com.example.domain.model.*
import com.example.engine.KeyframeInterpolator
import com.example.engine.SelectedTrackElement
import com.example.engine.export.ExportState
import com.example.engine.media.MediaRelinkManager
import com.example.engine.text.TextLayerRenderer
import com.example.ui.components.InteractiveTransformOverlay
import com.example.ui.AppScreen
import com.example.ui.EditorToolbarTab
import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.zIndex
import com.example.ui.StudioViewModel
import com.example.ui.components.KeyframeAnimationPanel
import com.example.ui.components.animation.AnimationsToolPanel
import com.example.ui.components.animation.AdvancedAnimationPanel
import com.example.ui.components.TransitionsPanel
import com.example.ui.components.trim.VideoTrimmingToolPanel
import com.example.ui.components.formatDuration
import com.example.ui.components.formatDurationShort
import com.example.ui.components.timeline.*
import com.example.ui.components.diagnostics.DiagnosticOverlay
import com.example.ui.components.timeline.LayersDrawer
import com.example.ui.theme.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val projectName by viewModel.activeProjectName.collectAsState()
  val aspectRatio by viewModel.activeAspectRatio.collectAsState()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()
  val isPlaying by viewModel.timelineEngine.isPlaying.collectAsState()
  val canUndo by viewModel.timelineEngine.canUndo.collectAsState()
  val canRedo by viewModel.timelineEngine.canRedo.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val activeTab by viewModel.activeToolbarTab.collectAsState()
  val isSnapping by viewModel.timelineEngine.isSnappingEnabled.collectAsState()
  val timelineZoom by viewModel.timelineEngine.timelineZoom.collectAsState()
  val selectedClipIds by viewModel.timelineEngine.selectedClipIds.collectAsState()
  val isMultiSelectMode by viewModel.timelineEngine.isMultiSelectMode.collectAsState()
  val snapIndicatorMs by viewModel.timelineEngine.snapIndicatorMs.collectAsState()
  val isMagnetic by viewModel.timelineEngine.isMagneticEnabled.collectAsState()
  val clipboardClips by viewModel.timelineEngine.clipboardClips.collectAsState()
  val selectedKeyframeIds by viewModel.timelineEngine.selectedKeyframeIds.collectAsState()
  val saveState by viewModel.saveState.collectAsState()
  val missingMediaList by viewModel.missingMediaList.collectAsState()
  val activeResolution by viewModel.activeResolution.collectAsState()
  val activeFps by viewModel.activeFps.collectAsState()
  val activeSampleRate by viewModel.activeSampleRate.collectAsState()
  val activeCanvasColor by viewModel.activeCanvasColor.collectAsState()
  val exportState by viewModel.videoExporter.exportState.collectAsState()
  val waveformStyle by viewModel.waveformStyle.collectAsState()
  val selectedTransitionCutIndex by viewModel.timelineEngine.selectedTransitionCutIndex.collectAsState()
  val timelineFps by viewModel.timelineEngine.timelineFps.collectAsState()
  val isFrameSnapping by viewModel.timelineEngine.isFrameSnapping.collectAsState()
  val isTracksSyncEnabled by viewModel.timelineEngine.isTracksSyncEnabled.collectAsState()
  val editorTools by viewModel.editorTools.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.playbackEngine.updateTimeline(viewModel.timelineEngine.timeline.value)
    viewModel.seekTo(viewModel.timelineEngine.currentPositionMs.value)
  }

  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  val coroutineScope = rememberCoroutineScope()
  var isLayersOpen by remember { mutableStateOf(false) }

  var showRenameDialog by remember { mutableStateOf(false) }
  var showSpeedDialog by remember { mutableStateOf(false) }
  var showProjectSettingsDialog by remember { mutableStateOf(false) }
  var showExportConfigDialog by remember { mutableStateOf(false) }
  var showRelinkMediaDialog by remember { mutableStateOf(false) }
  var isFullscreenPreview by remember { mutableStateOf(false) }
  var showDiagnosticOverlay by remember { mutableStateOf(false) }
  var showMoreToolsDialog by remember { mutableStateOf(false) }
  var isEditToolsOpen by remember { mutableStateOf(false) }
  var isTextToolsOpen by remember { mutableStateOf(false) }
  var activeTextToolModal by remember { mutableStateOf<TextToolCategory?>(null) }
  var isEffectsToolsOpen by remember { mutableStateOf(false) }
  var activeEffectsCategoryModal by remember { mutableStateOf<EffectCategory?>(null) }
  var showDrawingDialog by remember { mutableStateOf(false) }
  var showOpacityDialog by remember { mutableStateOf(false) }
  var pendingReplaceClipId by remember { mutableStateOf<String?>(null) }
  var draggedTransitionType by remember { mutableStateOf<TransitionType?>(null) }

  androidx.activity.compose.BackHandler(enabled = activeTextToolModal != null || isTextToolsOpen || activeEffectsCategoryModal != null || isEffectsToolsOpen || activeTab != null || isEditToolsOpen) {
    if (activeEffectsCategoryModal != null) {
      activeEffectsCategoryModal = null
    } else if (isEffectsToolsOpen) {
      isEffectsToolsOpen = false
    } else if (activeTextToolModal != null) {
      activeTextToolModal = null
    } else if (isTextToolsOpen) {
      isTextToolsOpen = false
    } else if (activeTab != null) {
      viewModel.setActiveToolbarTab(null)
    } else if (isEditToolsOpen) {
      isEditToolsOpen = false
    }
  }

  val replaceMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    val clipId = pendingReplaceClipId
    if (uri != null && clipId != null) {
      coroutineScope.launch {
        val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
          context = context,
          sourceUriString = uri.toString(),
          suggestedName = "Replaced Media"
        )
        viewModel.timelineEngine.replaceMedia(
          clipId = clipId,
          newUri = persistentPath,
          newName = "Replaced Media"
        )
        pendingReplaceClipId = null
      }
    }
  }

  // Fallback Media Picker Launcher using GET_CONTENT
  val genericMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          try {
            val fileName = try {
              var result: String? = null
              if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                  if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                  }
                }
              }
              result ?: uri.lastPathSegment ?: "Imported Media"
            } catch (e: Exception) {
              uri.lastPathSegment ?: "Imported Media"
            }

            val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
              context = context,
              sourceUriString = uri.toString(),
              suggestedName = fileName
            )

            val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(
              context,
              persistentPath,
              defaultImageDurationMs = 3000L
            )

            viewModel.checkAndAutoConfigureCanvasFromMedia(
              width = metadata.width,
              height = metadata.height,
              rotationDegrees = metadata.rotationDegrees,
              frameRate = metadata.frameRate
            )

            viewModel.timelineEngine.addVideoClip(
              uri = persistentPath,
              name = fileName,
              isVideo = metadata.isVideo,
              durationMs = metadata.durationMs,
              atPlayhead = true,
              width = metadata.width,
              height = metadata.height,
              rotationDegrees = metadata.rotationDegrees,
              frameRate = metadata.frameRate,
              mimeType = metadata.mimeType,
              hasAudio = metadata.hasAudio
            )
          } catch (e: Throwable) {
            android.util.Log.e("EditorScreen", "Error importing fallback media", e)
          }
        }
      }
    }
  }

  // Gallery / Media Picker Launcher for Timeline '+' Button (Videos & Images)
  val timelineMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          try {
            val fileName = try {
              var result: String? = null
              if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                  if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                  }
                }
              }
              result ?: uri.lastPathSegment ?: "Imported Media"
            } catch (e: Exception) {
              uri.lastPathSegment ?: "Imported Media"
            }

            val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
              context = context,
              sourceUriString = uri.toString(),
              suggestedName = fileName
            )

            val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(
              context,
              persistentPath,
              defaultImageDurationMs = 3000L
            )

            // Authoritatively configure project canvas from first imported video
            viewModel.checkAndAutoConfigureCanvasFromMedia(
              width = metadata.width,
              height = metadata.height,
              rotationDegrees = metadata.rotationDegrees,
              frameRate = metadata.frameRate
            )

            viewModel.timelineEngine.addVideoClip(
              uri = persistentPath,
              name = fileName,
              isVideo = metadata.isVideo,
              durationMs = metadata.durationMs,
              atPlayhead = true,
              width = metadata.width,
              height = metadata.height,
              rotationDegrees = metadata.rotationDegrees,
              frameRate = metadata.frameRate,
              mimeType = metadata.mimeType,
              hasAudio = metadata.hasAudio
            )
          } catch (e: Throwable) {
            android.util.Log.e("EditorScreen", "Error importing media", e)
          }
        }
      }
    }
  }

  val safeLaunchMediaPicker: () -> Unit = {
    try {
      timelineMediaPickerLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
      )
    } catch (e: Throwable) {
      try {
        genericMediaPickerLauncher.launch("video/*,image/*")
      } catch (e2: Throwable) {
        viewModel.setActiveToolbarTab(EditorToolbarTab.MEDIA)
      }
    }
  }

  // Gallery / Media Picker Launcher directly for Overlay / PIP (Videos & Images)
  val overlayMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          try {
            val fileName = try {
              var result: String? = null
              if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                  if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                  }
                }
              }
              result ?: uri.lastPathSegment ?: "Overlay Media"
            } catch (e: Exception) {
              uri.lastPathSegment ?: "Overlay Media"
            }

            val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
              context = context,
              sourceUriString = uri.toString(),
              suggestedName = fileName
            )

            val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(
              context,
              persistentPath,
              defaultImageDurationMs = 3000L
            )

            viewModel.timelineEngine.addOverlayClip(
              uri = persistentPath,
              name = fileName,
              isVideo = metadata.isVideo,
              durationMs = metadata.durationMs,
              width = metadata.width,
              height = metadata.height,
              rotationDegrees = metadata.rotationDegrees,
              frameRate = metadata.frameRate,
              mimeType = metadata.mimeType,
              hasAudio = metadata.hasAudio
            )
          } catch (e: Throwable) {
            android.util.Log.e("EditorScreen", "Error importing overlay media", e)
          }
        }
      }
    }
  }

  // Fallback Generic Media Picker Launcher for Overlay
  val genericOverlayPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          try {
            val fileName = try {
              var result: String? = null
              if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                  if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                  }
                }
              }
              result ?: uri.lastPathSegment ?: "Overlay Media"
            } catch (e: Exception) {
              uri.lastPathSegment ?: "Overlay Media"
            }

            val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
              context = context,
              sourceUriString = uri.toString(),
              suggestedName = fileName
            )

            val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(
              context,
              persistentPath,
              defaultImageDurationMs = 3000L
            )

            viewModel.timelineEngine.addOverlayClip(
              uri = persistentPath,
              name = fileName,
              isVideo = metadata.isVideo,
              durationMs = metadata.durationMs,
              width = metadata.width,
              height = metadata.height,
              rotationDegrees = metadata.rotationDegrees,
              frameRate = metadata.frameRate,
              mimeType = metadata.mimeType,
              hasAudio = metadata.hasAudio
            )
          } catch (e: Throwable) {
            android.util.Log.e("EditorScreen", "Error importing overlay fallback media", e)
          }
        }
      }
    }
  }

  val safeLaunchOverlayPicker: () -> Unit = {
    try {
      overlayMediaPickerLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
      )
    } catch (e: Throwable) {
      try {
        genericOverlayPickerLauncher.launch("video/*,image/*")
      } catch (e2: Throwable) {
        android.util.Log.e("EditorScreen", "Failed to launch overlay gallery picker", e2)
      }
    }
  }

  val handleToolClick: (EditorToolItem) -> Unit = { tool ->
    when {
      tool.actionKey.contains("SPLIT", ignoreCase = true) -> {
        com.example.engine.timeline.TimelineSplitEngine.performSplitAtPlayhead(
          timelineEngine = viewModel.timelineEngine,
          playheadMs = currentPosMs,
          selectedElement = selectedElement
        )
      }
      tool.actionKey.equals("TOOL_EXPORT_PRESETS", ignoreCase = true) -> {
        viewModel.saveCurrentProject()
        showExportConfigDialog = true
      }
      tool.actionKey.equals("TOOL_VIDEO_TEMPLATES", ignoreCase = true) ||
        tool.actionKey.equals("TOOL_TRENDING_PACKS", ignoreCase = true) -> {
        viewModel.setActiveToolbarTab(EditorToolbarTab.ASSET_STORE)
      }
      tool.actionKey.equals("TOOL_OVERLAYS", ignoreCase = true) ||
        tool.id.equals("tool_overlays", ignoreCase = true) ||
        tool.name.equals("Overlay", ignoreCase = true) ||
        tool.name.equals("Overlays", ignoreCase = true) ||
        tool.name.contains("Overlay", ignoreCase = true) -> {
        // Direct mobile gallery open for Overlays
        isEffectsToolsOpen = false
        isTextToolsOpen = false
        isEditToolsOpen = false
        viewModel.setActiveToolbarTab(null)
        safeLaunchOverlayPicker()
      }
      tool.actionKey.equals("TOOL_EFFECTS", ignoreCase = true) ||
        tool.id.equals("tool_effects", ignoreCase = true) ||
        tool.name.equals("Effects", ignoreCase = true) -> {
        // Close/hide main editor bottom nav, open dedicated Effects sub-navigation
        isEffectsToolsOpen = true
        isTextToolsOpen = false
        isEditToolsOpen = false
        activeEffectsCategoryModal = null
        viewModel.setActiveToolbarTab(null)
      }
      tool.actionKey.equals("TOOL_TEXT", ignoreCase = true) ||
        tool.id.equals("tool_text", ignoreCase = true) ||
        tool.name.contains("Text", ignoreCase = true) -> {
        // Close/hide main editor bottom nav, open dedicated Text Tools bottom nav
        isTextToolsOpen = true
        isEffectsToolsOpen = false
        isEditToolsOpen = false
        activeTextToolModal = null
        viewModel.setActiveToolbarTab(null)
      }
      tool.actionKey.equals("TOOL_EDIT", ignoreCase = true) -> {
        isEditToolsOpen = true
        isTextToolsOpen = false
        isEffectsToolsOpen = false
        viewModel.setActiveToolbarTab(null)
      }
      else -> {
        val tab = tool.mappedTab
        if (tab != null) {
          viewModel.setActiveToolbarTab(if (activeTab == tab) null else tab)
        }
      }
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .background(StudioDarkBg),
      containerColor = StudioDarkBg,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        EditorTopBar(
          activeResolution = activeResolution,
          exportState = exportState,
          canUndo = canUndo,
          canRedo = canRedo,
          isDiagnosticActive = showDiagnosticOverlay,
          onToggleDiagnostics = { showDiagnosticOverlay = !showDiagnosticOverlay },
          onUndoClick = { viewModel.timelineEngine.undo() },
          onRedoClick = { viewModel.timelineEngine.redo() },
          onBackClick = {
            viewModel.saveCurrentProject()
            viewModel.navigateTo(AppScreen.HOME)
          },
          onExportClick = {
            viewModel.saveCurrentProject()
            showExportConfigDialog = true
          }
        )
      }
  ) { padding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = padding.calculateTopPadding())
    ) {
      val configuration = LocalConfiguration.current
      val screenHeight = configuration.screenHeightDp.dp
      val screenWidth = configuration.screenWidthDp.dp
      val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
      
      // The old filmstrip thumbnail band has been removed from the editor layout.
      // Reclaim that vertical space for the actual video preview instead of leaving a gap.
      // The cap keeps the timeline and playback controls usable on compact screens.
      // In portrait: Give maximum vertical space to video preview while reserving exact space for
      // Playback bar (~44dp), 3-track timeline (~214dp), and BottomToolbar (~72dp).
      // This pushes the timeline tracks down to sit flush above the bottom navigation bar and eliminates empty space.
      val timelineHeight = if (isLandscape) 150.dp else 214.dp
      // Text & Filter Tools expand to 45% screen height per user requirement, while other tool panels maintain 37% cap
      val isTextToolActive = activeTextToolModal != null || activeTab == EditorToolbarTab.TEXT
      val isFilterToolActive = activeTab == EditorToolbarTab.FILTERS || activeTab == EditorToolbarTab.ADJUST
      val maxToolPanelHeight = if (isTextToolActive || isFilterToolActive) maxHeight * 0.45f else maxHeight * 0.37f
      val isFilterToolsOpen = activeTab == EditorToolbarTab.FILTERS || activeTab == EditorToolbarTab.ADJUST
      val isTransitionsOpen = activeTab == EditorToolbarTab.TRANSITIONS
      val isStickersOpen = activeTab == EditorToolbarTab.STICKERS
      val responsiveSpacerHeight = 0.dp

      Column(
        modifier = Modifier
          .fillMaxSize()
      ) {
      // Missing Media Warning Bar
      if (missingMediaList.isNotEmpty()) {
        Surface(
          color = AmberAccent.copy(alpha = 0.2f),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showRelinkMediaDialog = true }
            .testTag("missing_media_alert_banner")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${missingMediaList.size} missing media clip${if (missingMediaList.size == 1) "" else "s"} detected",
                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
              )
            }
            Button(
              onClick = { showRelinkMediaDialog = true },
              colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
              modifier = Modifier
                .height(26.dp)
                .testTag("relink_media_banner_button")
            ) {
              Text("Relink", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
          }
        }
      }

      // 1. VIDEO PREVIEW AREA (Fixed weight - will never shrink, keeps video screen prominent)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .background(Color.Black)
          .testTag("video_preview_container"),
        contentAlignment = Alignment.Center
      ) {
        VideoPreviewSurface(
          timeline = timeline,
          currentPosMs = currentPosMs,
          aspectRatio = aspectRatio,
          selectedElement = selectedElement,
          onSelectElement = { viewModel.timelineEngine.selectElement(it) },
          onUpdateOverlay = { viewModel.timelineEngine.updateOverlayClip(it) },
          onUpdateText = { viewModel.timelineEngine.updateTextClip(it) },
          onUpdateSticker = { viewModel.timelineEngine.updateStickerClip(it) },
          onDeleteClip = { viewModel.timelineEngine.deleteClips(setOf(it)) },
          onDuplicateClip = { viewModel.timelineEngine.duplicateClips(setOf(it)) },
          onEditText = { clip ->
            viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(clip.id))
            isTextToolsOpen = true
            activeTextToolModal = TextToolCategory.ADD_TEXT
          },
          player = viewModel.playbackEngine.player,
          onGetOverlayPlayer = { clipId -> viewModel.playbackEngine.getOverlayPlayer(clipId) },
          onToggleFullscreen = { isFullscreenPreview = true },
          onAddMedia = safeLaunchMediaPicker,
          modifier = Modifier
            .fillMaxHeight(0.98f)
            .testTag("video_preview")
        )

        // Motion Tracking Interactive Target & Trajectory Overlay
        if (activeTab == EditorToolbarTab.MOTION_TRACKING) {
          val trackingUiState by viewModel.motionTrackingState.collectAsState()
          val activeTrackClip = viewModel.getSelectedVideoClip()
          com.example.ui.components.tracking.MotionTrackingPreviewOverlay(
            uiState = trackingUiState,
            currentPosMs = currentPosMs,
            clipStartMs = activeTrackClip?.timelineStartMs ?: 0L,
            clipDurationMs = activeTrackClip?.durationMs ?: timeline.totalDurationMs,
            onUpdateRegion = { viewModel.updateTrackingRegion(it) },
            modifier = Modifier.fillMaxSize()
          )
        }

        // Real-Time ML Kit Face Detection & Mesh AR Overlay Filter
        val activeArFilter by viewModel.activeArFilter.collectAsState()
        val showMeshGrid by viewModel.showFaceMeshGrid.collectAsState()
        val arScale by viewModel.arFilterScale.collectAsState()
        val arOffsetY by viewModel.arFilterOffsetY.collectAsState()
        val arOpacity by viewModel.arFilterOpacity.collectAsState()
        val trackingUiStateForAr by viewModel.motionTrackingState.collectAsState()

        if (activeArFilter != null || showMeshGrid) {
          com.example.ui.components.ar.ArOverlayPreviewOverlay(
            activeFilter = activeArFilter,
            showMeshGrid = showMeshGrid,
            scaleFactor = arScale,
            offsetYFactor = arOffsetY,
            opacity = arOpacity,
            customColor = null,
            uiState = trackingUiStateForAr,
            modifier = Modifier.fillMaxSize()
          )
        }

        // Real-Time Hardware Diagnostic Overlay
        androidx.compose.animation.AnimatedVisibility(
          visible = showDiagnosticOverlay,
          enter = fadeIn() + slideInVertically { -it },
          exit = fadeOut() + slideOutVertically { -it },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
        ) {
          DiagnosticOverlay(
            timeline = timeline,
            isPlaying = isPlaying,
            onClose = { showDiagnosticOverlay = false }
          )
        }
      }

      // 2. PLAYBACK CONTROLS BAR (Moved below video preview, scaled-up play button)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.Black)
          .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Left: Timecode Display (e.g. 00:04 / 00:12)
        Text(
          text = "${formatDurationShort(currentPosMs)} / ${formatDurationShort(timeline.totalDurationMs)}",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          ),
          modifier = Modifier.testTag("playback_timecode_display")
        )

        // Center: Step Back (⏮), Scaled-Up Play/Pause (⏯), Step Forward (⏭)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          IconButton(
            onClick = { viewModel.timelineEngine.stepBackwardOneFrame() },
            modifier = Modifier
              .size(36.dp)
              .testTag("playback_step_backward")
          ) {
            Icon(
              imageVector = Icons.Default.SkipPrevious,
              contentDescription = "Previous Frame",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }

          // Scaled Up Play/Pause Button with Cyan Ring Accent
          IconButton(
            onClick = { viewModel.timelineEngine.togglePlayPause() },
            modifier = Modifier
              .size(52.dp)
              .clip(CircleShape)
              .background(Color.Black)
              .border(2.5.dp, CyanAccent, CircleShape)
              .testTag("playback_play_pause")
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play",
              tint = Color.White,
              modifier = Modifier.size(30.dp)
            )
          }

          IconButton(
            onClick = { viewModel.timelineEngine.stepForwardOneFrame() },
            modifier = Modifier
              .size(36.dp)
              .testTag("playback_step_forward")
          ) {
            Icon(
              imageVector = Icons.Default.SkipNext,
              contentDescription = "Next Frame",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Above media track: Add Media button (White plus, Blue color)
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0080FF),
            modifier = Modifier
              .clickable { safeLaunchMediaPicker() }
              .testTag("above_media_track_add_btn")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Media",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "Add Media",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.5.sp
                )
              )
            }
          }
        }
      }

      // Responsive spacing between video preview and timeline
      Spacer(modifier = Modifier.height(responsiveSpacerHeight))

      // 1. DEFAULT EDITOR VIEW: Multi-track Timeline + Floating Bottom Navigation Bar
      // Kept at a stable, fixed height in the base Column so the Video Preview above it NEVER jerks or zooms!
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
            // 2. TIMELINE WRAPPER (Fixed height allocated for editing)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(timelineHeight)
            ) {
              var multiTrackZoom by remember { mutableFloatStateOf(1.0f) }

              Column(modifier = Modifier.fillMaxSize()) {
                if (draggedTransitionType != null) {
                  Surface(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = PurpleAccent.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color.White)
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Transform, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = "Dragging \"${draggedTransitionType?.displayName}\" ➔ Tap any Cut diamond on timeline",
                          style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        )
                      }
                      IconButton(
                        onClick = { draggedTransitionType = null },
                        modifier = Modifier.size(20.dp)
                      ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Drag", tint = Color.White, modifier = Modifier.size(14.dp))
                      }
                    }
                  }
                }

                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                ) {
                  com.example.ui.components.timeline.MultiTrackTimeline(
                    timeline = timeline,
                    currentPosMs = currentPosMs,
                    zoom = multiTrackZoom,
                    selectedElement = selectedElement,
                    selectedClipIds = selectedClipIds,
                    isMultiSelectMode = isMultiSelectMode,
                    snapIndicatorMs = snapIndicatorMs,
                    onSeek = { targetMs ->
                      viewModel.seekTo(targetMs)
                    },
                    onPausePlayback = {
                      viewModel.timelineEngine.pause()
                      viewModel.playbackEngine.pause()
                    },
                    onSelectElement = { viewModel.timelineEngine.selectElement(it) },
                    onToggleClipSelection = { viewModel.timelineEngine.toggleClipSelection(it) },
                    onZoomChange = { multiTrackZoom = it },
                    onMoveClip = { id, delta -> viewModel.timelineEngine.moveClipByDelta(id, delta) },
                    onMoveTrack = { trackType, trackIndex, delta -> viewModel.timelineEngine.moveTrackByDelta(trackType, trackIndex, delta) },
                    onTrimClipLeft = { id, delta -> viewModel.timelineEngine.trimClipLeftByDelta(id, delta) },
                    onTrimClipRight = { id, delta -> viewModel.timelineEngine.trimClipRightByDelta(id, delta) },
                    onToggleTrackLock = { viewModel.timelineEngine.toggleTrackLock(it) },
                    onToggleTrackHide = { viewModel.timelineEngine.toggleTrackHide(it) },
                    onToggleTrackMute = { viewModel.timelineEngine.toggleTrackMute(it) },
                    onToggleTrackSolo = { viewModel.timelineEngine.toggleTrackSolo(it) },
                    onCycleTrackHeight = { viewModel.timelineEngine.cycleTrackHeight(it) },
                    onReorderVideoClips = { from, to -> viewModel.timelineEngine.reorderVideoClips(from, to) },
                    isPlaying = isPlaying,
                    onTogglePlayPause = { viewModel.timelineEngine.togglePlayPause() },
                    onSplitClip = { viewModel.timelineEngine.splitAtPlayhead() },
                    onTrimLeftToPlayhead = { viewModel.timelineEngine.trimClipLeftToPlayhead() },
                    onTrimRightToPlayhead = { viewModel.timelineEngine.trimClipRightToPlayhead() },
                    onDeleteClip = { viewModel.timelineEngine.deleteSelected() },
                    onDuplicateClip = { viewModel.timelineEngine.duplicateSelected() },
                    onCopyClip = { viewModel.timelineEngine.copySelectedClips() },
                    onPasteClip = { viewModel.timelineEngine.pasteClipsAtPlayhead() },
                    onToggleMultiSelect = { viewModel.timelineEngine.toggleMultiSelectMode() },
                    onNextPeak = { viewModel.timelineEngine.jumpToNextAudioPeak() },
                    onPrevPeak = { viewModel.timelineEngine.jumpToPrevAudioPeak() },
                    onNextSilence = { viewModel.timelineEngine.jumpToNextAudioSilence() },
                    onPrevSilence = { viewModel.timelineEngine.jumpToPrevAudioSilence() },
                    onRemoveSilence = {
                      val clipId = viewModel.timelineEngine.selectedClipIds.value.firstOrNull()
                      if (clipId != null) viewModel.timelineEngine.removeSilenceFromAudioClip(clipId)
                    },
                    onToggleWaveformStyle = { viewModel.cycleWaveformStyle() },
                    fps = timelineFps,
                    isFrameSnapping = isFrameSnapping,
                    onStepFrames = { viewModel.timelineEngine.stepFrames(it) },
                    onSeekToPrevCut = { viewModel.timelineEngine.seekToPreviousCut() },
                    onSeekToNextCut = { viewModel.timelineEngine.seekToNextCut() },
                    onToggleFrameSnapping = { viewModel.timelineEngine.toggleFrameSnapping() },
                    selectedKeyframeIds = selectedKeyframeIds,
                    onSelectKeyframe = { viewModel.timelineEngine.selectKeyframe(it) },
                    onMoveKeyframe = { id, time -> viewModel.timelineEngine.moveKeyframe(id, time) },
                    onAddAudioKeyframe = { id, time, vol -> viewModel.timelineEngine.addAudioVolumeKeyframe(id, time, vol) },
                    onUpdateAudioKeyframe = { id, kfId, time, vol -> viewModel.timelineEngine.updateAudioVolumeKeyframe(id, kfId, time, vol) },
                    onDeleteAudioKeyframe = { id, kfId -> viewModel.timelineEngine.deleteAudioVolumeKeyframe(id, kfId) },
                    onAddMedia = safeLaunchMediaPicker,
                    onAddOverlay = safeLaunchOverlayPicker,
                    onToggleMuteAllVideo = { viewModel.timelineEngine.toggleTrackMute(TrackType.MAIN_VIDEO) },
                    isTracksSyncEnabled = isTracksSyncEnabled,
                    onToggleTracksSync = { viewModel.timelineEngine.toggleTracksSync() },
                    onMoveToPlayhead = { viewModel.timelineEngine.moveSelectedClipToPlayhead() },
                    onSplitAllTracks = { viewModel.timelineEngine.splitAllTracksAtPlayhead() },
                    onScrubStart = { viewModel.onScrubStart() },
                    onScrubProgress = { viewModel.onScrubProgress(it) },
                    onScrubStop = { viewModel.onScrubStop() },
                    modifier = Modifier.fillMaxSize()
                  )
                }
              }
            } // End of timeline Box

        // Bottom Navigation Bar is displayed at the bottom of the screen in normal editor mode
        EditorBottomToolbar(
          tools = editorTools,
          activeTab = activeTab,
          isEditToolsOpen = isEditToolsOpen,
          isTextToolsOpen = isTextToolsOpen,
          activeTextToolModal = activeTextToolModal,
          isEffectsToolsOpen = isEffectsToolsOpen,
          activeEffectsCategoryModal = activeEffectsCategoryModal,
          selectedElement = selectedElement,
          onOpenEditTools = {
            isEditToolsOpen = true
            isTextToolsOpen = false
            isEffectsToolsOpen = false
          },
          onCloseEditTools = {
            isEditToolsOpen = false
            viewModel.timelineEngine.clearSelection()
          },
          onSelectTextTool = { tool ->
            activeTextToolModal = tool
          },
          onCloseTextTools = {
            isTextToolsOpen = false
            activeTextToolModal = null
          },
          onSelectEffectsCategory = { cat ->
            activeEffectsCategoryModal = cat
          },
          onCloseEffectsTools = {
            isEffectsToolsOpen = false
            activeEffectsCategoryModal = null
          },
          onToolClick = handleToolClick,
          onMoreClick = { showMoreToolsDialog = true },
          onOpenOpacityDialog = { showOpacityDialog = true },
          onAddOverlay = safeLaunchOverlayPicker,
          viewModel = viewModel
        )
      } // End of Column (Timeline + Bottom Navigation Bar)

      // HIDDEN SIDEBAR (Old layers panel - keep code but hide: 0% width, takes no space)
      Box(
        modifier = Modifier
          .size(0.dp)
          .testTag("layers_panel")
      )
    } // End of base Column (Video Preview, Controls, Timeline, Tool Bar)

    // 2. UNIFIED BOTTOM TOOLS CONTAINER (MAXIMUM 37% SCREEN HEIGHT)
    // Master constraint: Under NO condition does any bottom tool panel exceed 37% of available editor height.
    // Seamless tool switching without flicker, preview freeze, or container recreation.
    val isAnyToolPanelOpen = activeTab != null || activeTextToolModal != null || activeEffectsCategoryModal != null

    AnimatedVisibility(
      visible = isAnyToolPanelOpen,
      enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
        fadeIn(animationSpec = tween(180)),
      exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
        fadeOut(animationSpec = tween(160)),
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .heightIn(max = maxToolPanelHeight)
        .align(Alignment.BottomCenter)
    ) {
      Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Color(0xFF1E283E)),
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .heightIn(max = maxToolPanelHeight)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
          ) {} // Consume touch events so timeline underneath isn't touched accidentally
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(max = maxToolPanelHeight)
            .navigationBarsPadding()
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .heightIn(max = maxToolPanelHeight)
          ) {
            when {
              activeTextToolModal != null -> {
                when (activeTextToolModal) {
                  TextToolCategory.AUTO_CAPTIONS -> AutoCaptionsPanel(
                    viewModel = viewModel,
                    onClose = { activeTextToolModal = null }
                  )
                  TextToolCategory.ADD_TEXT -> AddTextPanel(
                    viewModel = viewModel,
                    onClose = { activeTextToolModal = null },
                    onOpenPainting = { showDrawingDialog = true }
                  )
                  TextToolCategory.TEXT_TEMPLATES -> TextTemplatesBrowserPanel(
                    viewModel = viewModel,
                    onClose = { activeTextToolModal = null },
                    onApplyTemplate = { tmpl ->
                      val newId = java.util.UUID.randomUUID().toString()
                      val newClip = tmpl.templateClip.copy(
                        id = newId,
                        timelineStartMs = currentPosMs.coerceAtLeast(0L),
                        durationMs = 3000L
                      )
                      viewModel.timelineEngine.addTextClipObject(newClip)
                      activeTextToolModal = null
                    }
                  )
                  null -> {}
                }
              }
              activeEffectsCategoryModal != null -> {
                EffectsCategoryPanel(
                  category = activeEffectsCategoryModal!!,
                  viewModel = viewModel,
                  onClose = { activeEffectsCategoryModal = null },
                  onApply = { effect ->
                    if (effect != null && effect.effectType != null) {
                      viewModel.timelineEngine.addEffectClip(effect.effectType)
                    }
                  }
                )
              }
              activeTab == EditorToolbarTab.FILTERS -> {
                com.example.ui.components.filter.FilterToolsPanel(
                  viewModel = viewModel,
                  initialTab = com.example.ui.components.filter.FilterToolsTab.FILTERS,
                  onClose = { viewModel.setActiveToolbarTab(null) }
                )
              }
              activeTab == EditorToolbarTab.ADJUST -> {
                com.example.ui.components.filter.FilterToolsPanel(
                  viewModel = viewModel,
                  initialTab = com.example.ui.components.filter.FilterToolsTab.ADJUST,
                  onClose = { viewModel.setActiveToolbarTab(null) }
                )
              }
              activeTab == EditorToolbarTab.TRANSITIONS -> {
                TransitionsPanel(
                  viewModel = viewModel,
                  onClose = { viewModel.setActiveToolbarTab(null) },
                  onStartDragTransition = { draggedTransitionType = it }
                )
              }
              activeTab == EditorToolbarTab.STICKERS -> {
                com.example.ui.components.stickers.StickersToolPanel(
                  viewModel = viewModel,
                  onClose = { viewModel.setActiveToolbarTab(null) }
                )
              }
              activeTab == EditorToolbarTab.MOTION_TRACKING -> {
                com.example.ui.components.tracking.MotionTrackingPanel(
                  viewModel = viewModel,
                  onClose = { viewModel.setActiveToolbarTab(null) }
                )
              }
              activeTab == EditorToolbarTab.AR_EFFECTS -> {
                com.example.ui.components.ar.ArEffectSelectorPanel(
                  viewModel = viewModel,
                  onClose = { viewModel.setActiveToolbarTab(null) }
                )
              }
              activeTab == EditorToolbarTab.MEDIA -> MediaImportPanel(
                viewModel = viewModel,
                onDismiss = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.OVERLAY -> OverlayToolPanel(
                viewModel = viewModel,
                onDismiss = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.EDIT -> EditToolPanel(viewModel)
              activeTab == EditorToolbarTab.TRIM -> VideoTrimmingToolPanel(
                viewModel = viewModel,
                onDismiss = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.SPEED -> com.example.ui.components.SpeedCurveToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.MASK -> com.example.ui.components.MaskAndBlendToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.AI -> com.example.ui.components.AiSuiteToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.AI_MATTING -> com.example.ui.components.AiMattingToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.ASSET_STORE -> com.example.ui.components.AssetStoreToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.EFFECTS -> EffectsToolPanel(viewModel)
              activeTab == EditorToolbarTab.TEXT -> {
                com.example.ui.components.text.AddTextPanel(
                  viewModel = viewModel,
                  onClose = { viewModel.setActiveToolbarTab(null) },
                  onOpenPainting = {
                    viewModel.setActiveToolbarTab(null)
                    showDrawingDialog = true
                  }
                )
              }
              activeTab == EditorToolbarTab.ELEMENTS -> com.example.ui.components.elements.ElementsToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.AUDIO -> com.example.ui.components.audio.AudioToolsContainerPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.VOLUME -> VolumeToolPanel(viewModel)
              activeTab == EditorToolbarTab.CHROMA -> ChromaKeyPanel(viewModel)
              activeTab == EditorToolbarTab.CANVAS -> CanvasPanel(viewModel)
              activeTab == EditorToolbarTab.KEYFRAME -> KeyframeAnimationPanel(viewModel)
              activeTab == EditorToolbarTab.CAPTIONS -> CaptionsToolPanel(viewModel)
              activeTab == EditorToolbarTab.AI_AVATAR -> AIAvatarToolPanel(viewModel)
              activeTab == EditorToolbarTab.BACKGROUND -> BackgroundToolPanel(viewModel)
              activeTab == EditorToolbarTab.ANIMATIONS -> AnimationsToolPanel(
                viewModel = viewModel,
                initialIsAdvanced = false,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              activeTab == EditorToolbarTab.ADVANCED_ANIMATION -> AnimationsToolPanel(
                viewModel = viewModel,
                initialIsAdvanced = true,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              else -> {}
            }
          }
        }
      }
    } // End of Unified AnimatedVisibility

    // 3. Sliding Multi-Layer Studio Drawer
    AnimatedVisibility(
      visible = isLayersOpen,
      enter = slideInHorizontally { -it } + fadeIn(),
      exit = slideOutHorizontally { -it } + fadeOut(),
      modifier = Modifier.align(Alignment.CenterStart)
    ) {
      com.example.ui.components.timeline.LayersDrawer(
        timeline = timeline,
        selectedElement = selectedElement,
        onSelectElement = { sel ->
          viewModel.timelineEngine.selectElement(sel)
        },
        onBringLayerForward = { clipId ->
          viewModel.timelineEngine.bringLayerForward(clipId)
        },
        onSendLayerBackward = { clipId ->
          viewModel.timelineEngine.sendLayerBackward(clipId)
        },
        onBringLayerToFront = { clipId ->
          viewModel.timelineEngine.bringLayerToFront(clipId)
        },
        onSendLayerToBack = { clipId ->
          viewModel.timelineEngine.sendLayerToBack(clipId)
        },
        onToggleClipLock = { clipId ->
          viewModel.timelineEngine.toggleClipLock(clipId)
        },
        onToggleClipHide = { clipId ->
          viewModel.timelineEngine.toggleClipHide(clipId)
        },
        onDuplicateClip = { clipId ->
          viewModel.timelineEngine.duplicateClips(setOf(clipId))
        },
        onDeleteClip = { clipId ->
          viewModel.timelineEngine.deleteClips(setOf(clipId))
        },
        onToggleTrackLock = { viewModel.timelineEngine.toggleTrackLock(it) },
        onToggleTrackHide = { viewModel.timelineEngine.toggleTrackHide(it) },
        onToggleTrackMute = { viewModel.timelineEngine.toggleTrackMute(it) },
        onToggleTrackSolo = { viewModel.timelineEngine.toggleTrackSolo(it) },
        onCycleTrackHeight = { viewModel.timelineEngine.cycleTrackHeight(it) },
        onClose = { isLayersOpen = false }
      )
    }
  }
}

  if (showDrawingDialog) {
    DrawToolPanel(
      viewModel = viewModel,
      onDismiss = { showDrawingDialog = false }
    )
  }

  // Speed Dialog
  if (showSpeedDialog) {
    val activeSpeed = (selectedElement as? SelectedTrackElement.Video)?.let { sel ->
      timeline.videoClips.find { it.id == sel.clipId }?.speed
    } ?: 1.0f
    ClipSpeedDialog(
      currentSpeed = activeSpeed,
      onDismiss = { showSpeedDialog = false },
      onConfirm = { newSpeed ->
        viewModel.timelineEngine.setClipSpeed(speed = newSpeed)
        showSpeedDialog = false
      }
    )
  }

  // Rename Dialog
  if (showRenameDialog) {
    com.example.ui.components.RenameProjectDialog(
      currentName = projectName,
      onDismiss = { showRenameDialog = false },
      onConfirm = { newName ->
        viewModel.renameProject(viewModel.activeProjectId.value, newName)
        showRenameDialog = false
      }
    )
  }

  // Relink Missing Media Dialog
  if (showRelinkMediaDialog) {
    com.example.ui.components.RelinkMediaDialog(
      missingItems = missingMediaList,
      onDismiss = { showRelinkMediaDialog = false },
      onRelink = { clipId, newUri ->
        viewModel.relinkMedia(clipId, newUri)
      }
    )
  }

  // Project Settings Dialog
  if (showProjectSettingsDialog) {
    com.example.ui.components.ProjectSettingsDialog(
      projectName = projectName,
      currentAspectRatio = aspectRatio,
      currentResolution = activeResolution,
      currentFps = activeFps,
      currentSampleRate = activeSampleRate,
      currentCanvasColor = activeCanvasColor,
      totalDurationMs = timeline.totalDurationMs,
      onDismiss = { showProjectSettingsDialog = false },
      onSaveSettings = { aspect, res, fps, sampleRate, canvasColor ->
        viewModel.updateProjectSettings(aspect, res, fps, sampleRate, canvasColor)
      }
    )
  }

  // Export Configuration Dialog (Media3 Transformer)
  if (showExportConfigDialog) {
    com.example.ui.components.export.ExportConfigurationDialog(
      projectName = projectName,
      totalDurationMs = timeline.totalDurationMs,
      aspectRatio = aspectRatio,
      initialResolution = activeResolution,
      initialFps = activeFps,
      onDismiss = { showExportConfigDialog = false },
      onConfirmExport = { config ->
        showExportConfigDialog = false
        viewModel.saveCurrentProject()
        viewModel.startExport(config)
      }
    )
  }

  // More Tools Dialog
  if (showMoreToolsDialog) {
    MoreToolsDialog(
      tools = editorTools,
      onDismiss = { showMoreToolsDialog = false },
      onSelectTool = { tool ->
        showMoreToolsDialog = false
        handleToolClick(tool)
      }
    )
  }

  // Clip Opacity Dialog
  if (showOpacityDialog) {
    val targetClipId = when (selectedElement) {
      is SelectedTrackElement.Video -> (selectedElement as SelectedTrackElement.Video).clipId
      is SelectedTrackElement.Overlay -> (selectedElement as SelectedTrackElement.Overlay).clipId
      else -> timeline.videoClips.firstOrNull()?.id
    }
    val currentOpacity = timeline.videoClips.find { it.id == targetClipId }?.opacity
      ?: timeline.overlayClips.find { it.id == targetClipId }?.opacity
      ?: 1.0f
    var opacityVal by remember(targetClipId, currentOpacity) { mutableFloatStateOf(currentOpacity) }

    AlertDialog(
      onDismissRequest = { showOpacityDialog = false },
      containerColor = Color(0xFF0F1523),
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Opacity, contentDescription = null, tint = CyanAccent)
            Text("Clip Opacity", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
          IconButton(onClick = { showOpacityDialog = false }) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
          }
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Transparency Level", color = TextSecondary, fontSize = 13.sp)
            Text("${(opacityVal * 100).toInt()}%", color = CyanAccent, fontWeight = FontWeight.Bold)
          }
          Slider(
            value = opacityVal,
            onValueChange = {
              opacityVal = it
              viewModel.timelineEngine.setClipOpacity(targetClipId, it)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
              thumbColor = CyanAccent,
              activeTrackColor = CyanAccent,
              inactiveTrackColor = StudioBorder
            ),
            modifier = Modifier.testTag("opacity_slider")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showOpacityDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
          Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      }
    )
  }

  // Immersive Fullscreen Video Preview Dialog
  if (isFullscreenPreview) {
    Dialog(
      onDismissRequest = { isFullscreenPreview = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
      ) {
        VideoPreviewSurface(
          timeline = timeline,
          currentPosMs = currentPosMs,
          aspectRatio = aspectRatio,
          selectedElement = selectedElement,
          onSelectElement = { viewModel.timelineEngine.selectElement(it) },
          onUpdateOverlay = { viewModel.timelineEngine.updateOverlayClip(it) },
          onUpdateText = { viewModel.timelineEngine.updateTextClip(it) },
          onUpdateSticker = { viewModel.timelineEngine.updateStickerClip(it) },
          onDeleteClip = { viewModel.timelineEngine.deleteClips(setOf(it)) },
          onDuplicateClip = { viewModel.timelineEngine.duplicateClips(setOf(it)) },
          onEditText = { clip ->
            isFullscreenPreview = false
            viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(clip.id))
            isTextToolsOpen = true
            activeTextToolModal = TextToolCategory.ADD_TEXT
          },
          player = viewModel.playbackEngine.player,
          onGetOverlayPlayer = { clipId -> viewModel.playbackEngine.getOverlayPlayer(clipId) },
          onToggleFullscreen = { isFullscreenPreview = false },
          onAddMedia = safeLaunchMediaPicker,
          modifier = Modifier.fillMaxSize()
        )

        // Top bar overlay
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { isFullscreenPreview = false },
            modifier = Modifier
              .size(40.dp)
              .background(Color.Black.copy(alpha = 0.6f), CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close Fullscreen", tint = Color.White)
          }

          Text(
            text = projectName,
            style = MaterialTheme.typography.titleMedium.copy(
              color = Color.White,
              fontWeight = FontWeight.Bold
            )
          )

          Text(
            text = "${formatDuration(currentPosMs)} / ${formatDurationShort(timeline.totalDurationMs)}",
            style = MaterialTheme.typography.labelMedium.copy(
              color = CyanAccent,
              fontWeight = FontWeight.Bold
            )
          )
        }

        // Bottom playback bar overlay
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(horizontal = 24.dp, vertical = 18.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { viewModel.timelineEngine.stepBackwardOneFrame() },
            modifier = Modifier.size(44.dp)
          ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "-1 Frame", tint = Color.White, modifier = Modifier.size(28.dp))
          }
          Spacer(modifier = Modifier.width(20.dp))
          IconButton(
            onClick = { viewModel.timelineEngine.togglePlayPause() },
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(CyanAccent)
          ) {
            Icon(
              if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = "Play/Pause",
              tint = Color.Black,
              modifier = Modifier.size(32.dp)
            )
          }
          Spacer(modifier = Modifier.width(20.dp))
          IconButton(
            onClick = { viewModel.timelineEngine.stepForwardOneFrame() },
            modifier = Modifier.size(44.dp)
          ) {
            Icon(Icons.Default.SkipNext, contentDescription = "+1 Frame", tint = Color.White, modifier = Modifier.size(28.dp))
          }
        }
      }
    }
  }
}
}

@Composable
private fun EditorTopBar(
  activeResolution: Resolution,
  exportState: ExportState,
  canUndo: Boolean = false,
  canRedo: Boolean = false,
  isDiagnosticActive: Boolean = false,
  onToggleDiagnostics: () -> Unit = {},
  onUndoClick: () -> Unit = {},
  onRedoClick: () -> Unit = {},
  onBackClick: () -> Unit,
  onExportClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .height(40.dp)
      .background(Color.Black)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left: Close (X) + Undo + Redo + Diagnostics Speed HUD
    IconButton(
      onClick = onBackClick,
      modifier = Modifier
        .size(34.dp)
        .testTag("close_btn")
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Close",
        tint = Color.White,
        modifier = Modifier.size(20.dp)
      )
    }

    Spacer(modifier = Modifier.width(2.dp))

    IconButton(
      onClick = onUndoClick,
      enabled = canUndo,
      modifier = Modifier
        .size(34.dp)
        .testTag("top_undo_btn")
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.Undo,
        contentDescription = "Undo",
        tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = Modifier.size(18.dp)
      )
    }

    IconButton(
      onClick = onRedoClick,
      enabled = canRedo,
      modifier = Modifier
        .size(34.dp)
        .testTag("top_redo_btn")
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.Redo,
        contentDescription = "Redo",
        tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = Modifier.size(18.dp)
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    // Right: compact Export control, matching the Close/Undo/Redo footprint.
    // Keep the action discoverable while avoiding a wide pill that consumes editor chrome.
    val isRendering = exportState is ExportState.Rendering
    Surface(
      onClick = { if (!isRendering) onExportClick() },
      shape = CircleShape,
      color = if (!isRendering) Color(0xFF006FE6) else Color(0xFF263244),
      enabled = !isRendering,
      modifier = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .testTag("export_btn")
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        if (isRendering) {
          val progress = (exportState as ExportState.Rendering).progressPercent
          CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color.White
          )
        } else {
          Icon(
            imageVector = Icons.Default.FileUpload,
            contentDescription = "Export",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun VideoPreviewSurface(
  timeline: Timeline,
  currentPosMs: Long,
  aspectRatio: AspectRatio,
  selectedElement: SelectedTrackElement = SelectedTrackElement.None,
  onSelectElement: (SelectedTrackElement) -> Unit = {},
  onUpdateOverlay: (VideoClip) -> Unit = {},
  onUpdateText: (TextClip) -> Unit = {},
  onUpdateSticker: (StickerClip) -> Unit = {},
  onDeleteClip: (String) -> Unit = {},
  onDuplicateClip: (String) -> Unit = {},
  onEditText: ((TextClip) -> Unit)? = null,
  player: ExoPlayer? = null,
  onGetOverlayPlayer: ((String) -> ExoPlayer?)? = null,
  onToggleFullscreen: (() -> Unit)? = null,
  onAddMedia: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  // Find current active video clip
  val activeClip = remember(timeline.videoClips, currentPosMs) {
    timeline.videoClips.find {
      currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs
    } ?: timeline.videoClips.lastOrNull()
  }

  val activeOverlays = remember(timeline.overlayClips, currentPosMs) {
    timeline.overlayClips.filter {
      currentPosMs >= it.timelineStartMs && currentPosMs <= it.timelineStartMs + it.durationMs
    }
  }

  val activeTexts = remember(timeline.textClips, currentPosMs, selectedElement) {
    val selectedId = (selectedElement as? SelectedTrackElement.Text)?.clipId
    timeline.textClips.filter {
      (it.id == selectedId) ||
      (currentPosMs >= it.timelineStartMs && currentPosMs <= it.timelineStartMs + it.durationMs)
    }.sortedWith(compareBy({ it.trackIndex }, { it.timelineStartMs }))
  }

  val activeStickers = remember(timeline.stickerClips, currentPosMs) {
    timeline.stickerClips.filter {
      currentPosMs >= it.timelineStartMs && currentPosMs <= it.timelineStartMs + it.durationMs
    }
  }

  val activeEffects = remember(timeline.effectClips, currentPosMs, selectedElement, activeClip) {
    timeline.effectClips.filter { clip ->
      val isSelected = (selectedElement as? SelectedTrackElement.Effect)?.clipId == clip.id
      !clip.isHidden && (
        isSelected ||
        if (clip.targetClipId != null) {
          activeClip != null && activeClip.id == clip.targetClipId &&
            currentPosMs >= activeClip.timelineStartMs &&
            currentPosMs < activeClip.timelineStartMs + activeClip.durationMs
        } else {
          currentPosMs >= clip.timelineStartMs && currentPosMs < clip.timelineStartMs + clip.durationMs
        }
      )
    }.sortedBy { it.timelineStartMs }
  }

  // Calculate accumulated motion transform from active effects (shake, zoom, skater zoom, vertigo dolly, spin, pan, mirror)
  val effectMotion = remember(activeEffects, currentPosMs) {
    if (activeEffects.isEmpty()) {
      VideoEffectRenderer.EffectMotionTransform()
    } else {
      VideoEffectRenderer.calculateMotionTransform(activeEffects, currentPosMs)
    }
  }

  // Keyframe calculations
  val clipTransform = remember(activeClip, currentPosMs) {
    if (activeClip != null) {
      val rel = currentPosMs - activeClip.timelineStartMs
      KeyframeInterpolator.interpolate(activeClip, rel)
    } else null
  }

  // Color Matrix for video adjustments, filter presets, and active color effects matching export pipeline
  val androidCombinedMatrix = remember(timeline.adjustments, timeline.filter, activeClip?.filter, activeEffects, currentPosMs) {
    val baseMatrix = com.example.engine.composition.ColorFilterGenerator.createCombinedMatrix(
      timeline.adjustments,
      timeline.filter,
      activeClip?.filter
    )
    val resultMatrix = android.graphics.ColorMatrix(baseMatrix)
    if (activeEffects.isNotEmpty()) {
      val effectMat = VideoEffectRenderer.calculateEffectColorMatrix(activeEffects, currentPosMs)
      if (effectMat != null) {
        resultMatrix.postConcat(effectMat)
      }
    }
    resultMatrix
  }

  val isIdentityFilter = remember(androidCombinedMatrix) {
    com.example.engine.composition.ColorFilterGenerator.isIdentityMatrix(androidCombinedMatrix)
  }

  val combinedColorFilter = remember(androidCombinedMatrix, isIdentityFilter) {
    if (isIdentityFilter) null else ColorFilter.colorMatrix(ColorMatrix(androidCombinedMatrix.array))
  }

  // Pinch-to-zoom & pan inspection state
  var previewZoomScale by remember { mutableFloatStateOf(1.0f) }
  var previewPanOffset by remember { mutableStateOf(Offset.Zero) }
  var showSafeAreas by remember { mutableStateOf(false) }
  var showGrid by remember { mutableStateOf(false) }
  var showCenterGuides by remember { mutableStateOf(false) }

  Card(
    modifier = modifier
      .aspectRatio(aspectRatio.ratio, matchHeightConstraintsFirst = true)
      .clip(RoundedCornerShape(12.dp))
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
    colors = CardDefaults.cardColors(containerColor = Color(timeline.canvasBackgroundColor))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(12.dp))
        .pointerInput(Unit) {
          detectTapGestures(
            onDoubleTap = {
              if (previewZoomScale > 1.05f) {
                previewZoomScale = 1.0f
                previewPanOffset = Offset.Zero
              } else {
                previewZoomScale = 2.0f
              }
            }
          )
        }
        .pointerInput(selectedElement) {
          detectTransformGestures { _, pan, zoom, _ ->
            // Only allow preview frame zooming/panning when no overlay element is selected
            if (selectedElement == SelectedTrackElement.None && (zoom != 1.0f || previewZoomScale > 1.05f)) {
              val oldScale = previewZoomScale
              val newScale = (oldScale * zoom).coerceIn(1.0f, 5.0f)
              previewZoomScale = newScale
              if (newScale > 1.0f) {
                val maxX = (newScale - 1f) * 400f
                val maxY = (newScale - 1f) * 400f
                val newPanX = (previewPanOffset.x + pan.x).coerceIn(-maxX, maxX)
                val newPanY = (previewPanOffset.y + pan.y).coerceIn(-maxY, maxY)
                previewPanOffset = Offset(newPanX, newPanY)
              } else {
                previewPanOffset = Offset.Zero
              }
            }
          }
        }
    ) {
      // Zoomable and Pannable Frame Content Container
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = previewZoomScale
            scaleY = previewZoomScale
            translationX = previewPanOffset.x
            translationY = previewPanOffset.y
          }
      ) {
        // Background Video / Image Layer
        if (activeClip != null) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                val baseScaleX = clipTransform?.scaleX ?: 1f
                val baseScaleY = clipTransform?.scaleY ?: 1f
                val baseRot = clipTransform?.rotation ?: 0f
                val baseTransX = (clipTransform?.posX ?: 0f) * size.width
                val baseTransY = (clipTransform?.posY ?: 0f) * size.height
                val baseAlpha = clipTransform?.opacity ?: 1f

                scaleX = baseScaleX * effectMotion.scaleX
                scaleY = baseScaleY * effectMotion.scaleY
                rotationZ = baseRot + effectMotion.rotation
                translationX = baseTransX + effectMotion.translationX * size.width
                translationY = baseTransY + effectMotion.translationY * size.height
                alpha = (baseAlpha * effectMotion.alpha).coerceIn(0f, 1f)
              },
            contentAlignment = Alignment.Center
          ) {
            val isRealPlayable = remember(activeClip.uri) {
              MediaRelinkManager.isRealPlayableMedia(context, activeClip.uri)
            }
            if (activeClip.isVideo && isRealPlayable && player != null) {
              val clipRot = if (activeClip.naturalRotation != 0) activeClip.naturalRotation else activeClip.rotationDegrees
              val (clipW, clipH) = AspectRatio.resolveEffectiveDimensions(activeClip.width, activeClip.height, clipRot)
              AndroidView(
                factory = { ctx ->
                  android.view.TextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                      ViewGroup.LayoutParams.MATCH_PARENT,
                      ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                      override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        try {
                          player.setVideoTextureView(this@apply)
                        } catch (e: Exception) {
                          android.util.Log.w("VideoPreviewSurface", "Failed to attach TextureView on available", e)
                        }
                      }
                      override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        applyTextureViewAspectFit(this@apply, clipW, clipH)
                      }
                      override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                        try {
                          player.clearVideoTextureView(this@apply)
                        } catch (_: Exception) {}
                        return true
                      }
                      override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                    }
                    if (isAvailable) {
                      try {
                        player.setVideoTextureView(this)
                      } catch (e: Exception) {
                        android.util.Log.w("VideoPreviewSurface", "Failed to attach TextureView to ExoPlayer", e)
                      }
                    }
                    tag = player
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                      applyTextureViewAspectFit(this, clipW, clipH)
                    }
                    applyTextureViewAspectFit(this, clipW, clipH)
                  }
                },
                update = { tv ->
                  if (tv.tag != player) {
                    try {
                      player.setVideoTextureView(tv)
                      tv.tag = player
                    } catch (e: Exception) {
                      android.util.Log.w("VideoPreviewSurface", "Failed to rebind TextureView", e)
                    }
                  }
                  applyTextureViewAspectFit(tv, clipW, clipH)
                },
                onReset = { /* Preserve texture view across recompositions */ },
                onRelease = { tv ->
                  try {
                    player.clearVideoTextureView(tv)
                  } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
              )
            } else if (!activeClip.isVideo && activeClip.uri.isNotBlank() && !activeClip.uri.startsWith("stock://") && !activeClip.uri.startsWith("sample://")) {
              AsyncImage(
                model = activeClip.uri,
                contentDescription = activeClip.name,
                contentScale = ContentScale.Fit,
                colorFilter = combinedColorFilter,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              SyntheticClipPreview(
                clip = activeClip,
                currentPosMs = currentPosMs,
                colorFilter = combinedColorFilter,
                modifier = Modifier.fillMaxSize()
              )
            }
          }
        } else if (timeline.videoClips.isEmpty() && timeline.overlayClips.isEmpty() && timeline.textClips.isEmpty() && timeline.stickerClips.isEmpty() && timeline.audioClips.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clickable { onAddMedia?.invoke() }
              .testTag("empty_timeline_canvas"),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF1E293B))
                  .border(1.dp, CyanAccent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.VideoLibrary,
                  contentDescription = "Add Media",
                  tint = CyanAccent,
                  modifier = Modifier.size(26.dp)
                )
              }
              Text(
                text = "Tap to add video or photo",
                style = MaterialTheme.typography.bodyMedium.copy(
                  color = TextPrimary,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.sp
                )
              )
              Text(
                text = "Clean blank timeline ready for your media",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = TextTertiary,
                  fontSize = 11.sp
                )
              )
            }
          }
        }

        // Active Visual Effects Overlay (procedural shaders, flares, sparks, scanlines, glitch, wings, grids)
        if (activeEffects.isNotEmpty()) {
          androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
          ) {
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w > 0 && h > 0) {
              drawIntoCanvas { composeCanvas ->
                try {
                  VideoEffectRenderer.renderEffectsOnCanvas(
                    canvas = composeCanvas.nativeCanvas,
                    activeEffects = activeEffects,
                    currentPosMs = currentPosMs,
                    width = w,
                    height = h
                  )
                } catch (e: Throwable) {
                  android.util.Log.w("VideoPreviewSurface", "Error rendering active effects on canvas", e)
                }
              }
            }
          }
        }

        // Touch-Based Interactive Transformation Layer (Text, PIP Overlays, Stickers, Shapes)
        InteractiveTransformOverlay(
          activeTexts = activeTexts,
          activeOverlays = activeOverlays,
          activeStickers = activeStickers,
          selectedElement = selectedElement,
          currentPosMs = currentPosMs,
          onSelectElement = onSelectElement,
          onUpdateText = onUpdateText,
          onUpdateOverlay = onUpdateOverlay,
          onUpdateSticker = onUpdateSticker,
          onDeleteClip = onDeleteClip,
          onDuplicateClip = onDuplicateClip,
          onEditText = onEditText,
          getOverlayPlayer = onGetOverlayPlayer,
          modifier = Modifier.fillMaxSize()
        )

        // Safe Areas, Rule-of-Thirds Grid, and Center Crosshair Guidelines (Editor Only)
        if (showSafeAreas || showGrid || showCenterGuides) {
          androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Rule of Thirds Grid (3x3)
            if (showGrid) {
              val lineAlpha = 0.35f
              drawLine(Color.White.copy(alpha = lineAlpha), Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
              drawLine(Color.White.copy(alpha = lineAlpha), Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
              drawLine(Color.White.copy(alpha = lineAlpha), Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
              drawLine(Color.White.copy(alpha = lineAlpha), Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
            }

            // 2. Center Crosshair Guides
            if (showCenterGuides) {
              drawLine(CyanAccent.copy(alpha = 0.6f), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1.5.dp.toPx())
              drawLine(CyanAccent.copy(alpha = 0.6f), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.5.dp.toPx())
              drawCircle(CyanAccent, radius = 3.dp.toPx(), center = Offset(w / 2f, h / 2f))
            }

            // 3. Title Safe Area (90% boundary: 5% inset) and Action Safe Area (80% boundary: 10% inset)
            if (showSafeAreas) {
              // Action Safe (80%) - Amber/Gold
              drawRect(
                color = GoldAccent.copy(alpha = 0.5f),
                topLeft = Offset(w * 0.10f, h * 0.10f),
                size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.80f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
              )
              // Title Safe (90%) - Cyan
              drawRect(
                color = CyanAccent.copy(alpha = 0.5f),
                topLeft = Offset(w * 0.05f, h * 0.05f),
                size = androidx.compose.ui.geometry.Size(w * 0.90f, h * 0.90f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
              )
            }
          }
        }
      }

      // Floating Zoom Scale Reset Badge (Top-Left overlay when zoomed in)
      if (previewZoomScale > 1.05f) {
        Surface(
          onClick = {
            previewZoomScale = 1.0f
            previewPanOffset = Offset.Zero
          },
          shape = RoundedCornerShape(16.dp),
          color = CyanAccent.copy(alpha = 0.95f),
          contentColor = Color.Black,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .testTag("reset_zoom_badge")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              Icons.Default.FitScreen,
              contentDescription = "Reset Zoom",
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "%.1fx (Reset)".format(previewZoomScale),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            )
          }
        }
      }

      // Fullscreen button overlay in video preview corner
      if (onToggleFullscreen != null) {
        IconButton(
          onClick = onToggleFullscreen,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .testTag("preview_fullscreen_overlay_btn")
        ) {
          Icon(
            Icons.Default.Fullscreen,
            contentDescription = "Fullscreen",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun TimelineControlsBar(
  isPlaying: Boolean,
  currentPosMs: Long,
  totalDurationMs: Long,
  zoom: Float,
  canUndo: Boolean,
  canRedo: Boolean,
  isSnapping: Boolean,
  onTogglePlay: () -> Unit,
  onStop: () -> Unit,
  onStepBack: () -> Unit,
  onStepForward: () -> Unit,
  onUndoClick: () -> Unit,
  onRedoClick: () -> Unit,
  onToggleSnapping: () -> Unit,
  onSplit: () -> Unit,
  onDelete: () -> Unit,
  onAddKeyframe: () -> Unit,
  onAddMedia: () -> Unit,
  onZoomChange: (Float) -> Unit,
  onToggleFullscreen: (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .drawBehind {
        drawLine(
          color = StudioBorder,
          start = Offset(0f, size.height),
          end = Offset(size.width, size.height),
          strokeWidth = 1.dp.toPx()
        )
      }
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    // Left: Fullscreen icon & Timecode (e.g., 00:07 / 00:29)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      if (onToggleFullscreen != null) {
        IconButton(
          onClick = onToggleFullscreen,
          modifier = Modifier
            .size(32.dp)
            .testTag("timeline_fullscreen_button")
        ) {
          Icon(
            Icons.Default.Fullscreen,
            contentDescription = "Fullscreen",
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Text(
        text = "${formatDurationShort(currentPosMs)} / ${formatDurationShort(totalDurationMs)}",
        style = MaterialTheme.typography.labelMedium.copy(
          color = TextPrimary,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp
        ),
        modifier = Modifier.testTag("timeline_timecode_display")
      )
    }

    // Center: Frame Step Back, Prominent Sky Blue Play/Pause, Frame Step Forward
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      IconButton(onClick = onStepBack, modifier = Modifier.size(30.dp)) {
        Icon(Icons.Default.SkipPrevious, contentDescription = "-1 Frame", tint = TextSecondary, modifier = Modifier.size(18.dp))
      }

      IconButton(
        onClick = onTogglePlay,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(CyanAccent)
          .testTag("timeline_play_pause")
      ) {
        Icon(
          if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = "Play/Pause",
          tint = Color.White,
          modifier = Modifier.size(22.dp)
        )
      }

      IconButton(onClick = onStepForward, modifier = Modifier.size(30.dp)) {
        Icon(Icons.Default.SkipNext, contentDescription = "+1 Frame", tint = TextSecondary, modifier = Modifier.size(18.dp))
      }
    }

    // Right: Snapping, Undo, Redo, Quick Split, Delete
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      IconButton(
        onClick = onToggleSnapping,
        modifier = Modifier.size(30.dp).testTag("editor_snapping_button")
      ) {
        Icon(
          Icons.Default.Adjust,
          contentDescription = "Snapping",
          tint = if (isSnapping) CyanAccent else TextTertiary,
          modifier = Modifier.size(17.dp)
        )
      }

      IconButton(
        onClick = onUndoClick,
        enabled = canUndo,
        modifier = Modifier.size(30.dp).testTag("editor_undo_button")
      ) {
        Icon(
          Icons.AutoMirrored.Filled.Undo,
          contentDescription = "Undo",
          tint = if (canUndo) TextPrimary else TextTertiary.copy(alpha = 0.35f),
          modifier = Modifier.size(17.dp)
        )
      }

      IconButton(
        onClick = onRedoClick,
        enabled = canRedo,
        modifier = Modifier.size(30.dp).testTag("editor_redo_button")
      ) {
        Icon(
          Icons.AutoMirrored.Filled.Redo,
          contentDescription = "Redo",
          tint = if (canRedo) TextPrimary else TextTertiary.copy(alpha = 0.35f),
          modifier = Modifier.size(17.dp)
        )
      }

      IconButton(
        onClick = onSplit,
        modifier = Modifier.size(30.dp).testTag("timeline_quick_split")
      ) {
        Icon(
          Icons.Default.CallSplit,
          contentDescription = "Split",
          tint = CyanAccent,
          modifier = Modifier.size(17.dp)
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(30.dp).testTag("timeline_quick_delete")
      ) {
        Icon(
          Icons.Default.Delete,
          contentDescription = "Delete",
          tint = RedAccent,
          modifier = Modifier.size(17.dp)
        )
      }
    }
  }
}

// TIMELINE WITH TIMESTAMPS: 40-50dp
@Composable
private fun TimelineTimestampsRow(
  currentPosMs: Long,
  totalDurationMs: Long,
  isMultiTrackView: Boolean = false,
  onToggleMultiTrackView: (() -> Unit)? = null
) {
  val total = totalDurationMs.coerceAtLeast(1000L)
  val safePos = currentPosMs.coerceIn(0L, total)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(45.dp)
      .background(Color.Black)
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag("timeline_container"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "${formatDurationShort(safePos)} / ${formatDurationShort(total)}",
      color = Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.testTag("time_display")
    )

    Text(
      text = "•",
      color = Color.White.copy(alpha = 0.5f),
      modifier = Modifier.padding(horizontal = 6.dp)
    )

    // Timestamps around current time
    val step = (total / 5).coerceAtLeast(2000L)
    val times = listOf(
      (safePos - step).coerceAtLeast(0L),
      safePos,
      (safePos + step).coerceAtMost(total)
    )

    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      times.forEachIndexed { index, timeMs ->
        Text(
          text = formatDurationShort(timeMs),
          color = if (index == 1) CyanAccent else Color.White.copy(alpha = 0.6f),
          fontSize = 10.sp,
          fontWeight = if (index == 1) FontWeight.Bold else FontWeight.Normal
        )
      }
    }

    // Toggle Multi-Track vs Compact View Pill
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = if (isMultiTrackView) StudioSurfaceVariant else StudioDarkBg,
      border = BorderStroke(1.dp, if (isMultiTrackView) CyanAccent else StudioBorder),
      modifier = Modifier
        .clickable { onToggleMultiTrackView?.invoke() }
        .testTag("timeline_mode_toggle")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = if (isMultiTrackView) Icons.Default.ViewAgenda else Icons.Default.Tune,
          contentDescription = null,
          tint = if (isMultiTrackView) CyanAccent else AudioTrackColor,
          modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (isMultiTrackView) "Multi-Track" else "Envelopes",
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        )
      }
    }
  }
}

// FILM STRIP THUMBNAILS: 80-100dp
@Composable
private fun FilmstripThumbnailsRow(
  timeline: Timeline,
  currentPosMs: Long,
  onSeek: (Long) -> Unit,
  onScrollLeft: () -> Unit,
  onAddMedia: () -> Unit
) {
  val totalDuration = timeline.totalDurationMs.coerceAtLeast(2000L)
  val frameIntervalMs = 500L
  val frameCount = ((totalDuration / frameIntervalMs) + 1).toInt().coerceIn(8, 60)

  val scrollState = rememberScrollState()

  LaunchedEffect(currentPosMs) {
    val progress = (currentPosMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    val targetScroll = (scrollState.maxValue * progress).toInt()
    if (!scrollState.isScrollInProgress) {
      scrollState.scrollTo(targetScroll)
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(90.dp)
      .background(Color.Black)
      .testTag("filmstrip_scroll"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left scroll button
    IconButton(
      onClick = onScrollLeft,
      modifier = Modifier
        .width(48.dp)
        .fillMaxHeight()
        .testTag("scroll_left_btn")
    ) {
      Icon(
        imageVector = Icons.Default.ChevronLeft,
        contentDescription = "Scroll Left",
        tint = Color.White,
        modifier = Modifier.size(28.dp)
      )
    }

    // Scrollable Thumbnails container
    Row(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .horizontalScroll(scrollState)
        .padding(vertical = 4.dp)
        .testTag("filmstrip_container"),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      for (i in 0 until frameCount) {
        val frameTimeMs = (i * frameIntervalMs).coerceAtMost(totalDuration)
        val isCurrentFrame = kotlin.math.abs(currentPosMs - frameTimeMs) < frameIntervalMs

        val activeClip = timeline.videoClips.find { c ->
          frameTimeMs >= c.timelineStartMs && frameTimeMs < (c.timelineStartMs + c.durationMs)
        } ?: timeline.overlayClips.find { c ->
          frameTimeMs >= c.timelineStartMs && frameTimeMs < (c.timelineStartMs + c.durationMs)
        }

        FilmstripThumbnailCell(
          frameIndex = i,
          frameTimeMs = frameTimeMs,
          isCurrentFrame = isCurrentFrame,
          activeClip = activeClip,
          onSeek = onSeek
        )
      }
    }

    // Right add button
    IconButton(
      onClick = onAddMedia,
      modifier = Modifier
        .width(48.dp)
        .fillMaxHeight()
        .testTag("add_btn")
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "Add Media",
        tint = CyanAccent,
        modifier = Modifier.size(28.dp)
      )
    }
  }
}

@Composable
private fun FilmstripThumbnailCell(
  frameIndex: Int,
  frameTimeMs: Long,
  isCurrentFrame: Boolean,
  activeClip: VideoClip?,
  onSeek: (Long) -> Unit
) {
  val context = LocalContext.current
  val sourceTimeMs = remember(activeClip, frameTimeMs) {
    if (activeClip != null) {
      activeClip.timelineToSourceMs(frameTimeMs)
    } else frameTimeMs
  }

  var thumbnailBitmap by remember(activeClip?.uri, sourceTimeMs) {
    val key = VideoThumbnailManager.makeKey(activeClip?.uri ?: "", sourceTimeMs, 140, 140)
    mutableStateOf(VideoThumbnailManager.getCachedThumbnail(key))
  }

  LaunchedEffect(activeClip?.uri, sourceTimeMs) {
    if (activeClip?.uri?.isNotEmpty() == true && thumbnailBitmap == null) {
      VideoThumbnailManager.requestThumbnail(
        context = context,
        uri = activeClip.uri,
        sourceTimeMs = sourceTimeMs,
        targetWidth = 140,
        targetHeight = 140,
        isVideo = activeClip.isVideo
      ) { bmp ->
        thumbnailBitmap = bmp
      }
    }
  }

  Box(
    modifier = Modifier
      .width(72.dp)
      .height(82.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(Color(0xFF1E293B))
      .border(
        width = if (isCurrentFrame) 2.dp else 1.dp,
        color = if (isCurrentFrame) CyanAccent else Color(0xFF334155),
        shape = RoundedCornerShape(6.dp)
      )
      .clickable { onSeek(frameTimeMs) }
  ) {
    val currentBmp = thumbnailBitmap
    if (currentBmp != null && !currentBmp.isRecycled) {
      Image(
        bitmap = currentBmp.asImageBitmap(),
        contentDescription = "Frame ${frameIndex + 1}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else if (activeClip?.uri?.isNotEmpty() == true) {
      AsyncImage(
        model = activeClip.uri,
        contentDescription = "Frame ${frameIndex + 1}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.linearGradient(
              listOf(
                Color(0xFF0F172A),
                Color(0xFF1E293B),
                Color(0xFF0F172A)
              )
            )
          )
          .padding(4.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Box(modifier = Modifier.size(3.dp).background(Color.White.copy(alpha = 0.3f)))
          Box(modifier = Modifier.size(3.dp).background(Color.White.copy(alpha = 0.3f)))
        }
        Icon(
          imageVector = Icons.Default.Movie,
          contentDescription = null,
          tint = if (isCurrentFrame) CyanAccent else Color.White.copy(alpha = 0.4f),
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = formatDurationShort(frameTimeMs),
          color = if (isCurrentFrame) CyanAccent else Color.White.copy(alpha = 0.6f),
          fontSize = 9.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }

    if (isCurrentFrame) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(3.dp)
          .background(CyanAccent)
      )
    }
  }
}

// BOTTOM TOOLBAR: Firestore-driven Tools + Edit Tools Mode + More Dialog + Dedicated Text Tools
@Composable
private fun EditorBottomToolbar(
  tools: List<EditorToolItem>,
  activeTab: EditorToolbarTab?,
  isEditToolsOpen: Boolean,
  isTextToolsOpen: Boolean,
  activeTextToolModal: TextToolCategory?,
  isEffectsToolsOpen: Boolean,
  activeEffectsCategoryModal: EffectCategory?,
  selectedElement: SelectedTrackElement?,
  onOpenEditTools: () -> Unit,
  onCloseEditTools: () -> Unit,
  onSelectTextTool: (TextToolCategory) -> Unit,
  onCloseTextTools: () -> Unit,
  onSelectEffectsCategory: (EffectCategory) -> Unit,
  onCloseEffectsTools: () -> Unit,
  onToolClick: (EditorToolItem) -> Unit,
  onMoreClick: () -> Unit,
  onOpenOpacityDialog: () -> Unit,
  onAddOverlay: () -> Unit,
  viewModel: StudioViewModel
) {
  if (isEffectsToolsOpen) {
    // Dedicated Effects Sub-Navigation:
    // Replaces the previous main editor bottom navigation completely when "Effects" is selected!
    // Contains exactly 4 horizontally arranged options:
    // 1. Video Effects
    // 2. Body Effects
    // 3. Photo Effects
    // 4. AI Effects
    EffectsToolsBottomBar(
      activeCategory = activeEffectsCategoryModal,
      onSelectCategory = onSelectEffectsCategory,
      onBackToMainMenu = onCloseEffectsTools
    )
  } else if (isTextToolsOpen) {
    // Dedicated Text Tools Bottom Navigation:
    // Replaces the previous main editor bottom navigation completely when "Text ✏️" is selected!
    // Contains exactly:
    // 1. Auto Captions
    // 2. Add Text
    // 3. Text Templates
    TextToolsBottomBar(
      activeTool = activeTextToolModal,
      onSelectTool = onSelectTextTool,
      onBackToMainMenu = onCloseTextTools
    )
  } else if (isEditToolsOpen) {
    // Edit Tools Mode: Show all 36 tools in ONE horizontal row with Back arrow at start
    val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()

    val editToolItems = remember(selectedElement, currentPosMs) {
      listOf(
        FuturisticNavItemData(
          id = "split",
          label = "Split",
          icon = Icons.Default.ContentCut,
          theme = NavItemThemes.Edit,
          isSelected = false,
          testTag = "tool_split_btn",
          onClick = {
            viewModel.timelineEngine.splitSelectedClipAtPlayhead()
          }
        ),
        FuturisticNavItemData(
          id = "volume",
          label = "Volume",
          icon = Icons.Default.VolumeUp,
          theme = NavItemThemes.Audio,
          isSelected = activeTab == EditorToolbarTab.VOLUME,
          testTag = "tool_volume_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.VOLUME) }
        ),
        FuturisticNavItemData(
          id = "delete",
          label = "Delete",
          icon = Icons.Default.Delete,
          theme = NavItemThemes.DefaultSlate,
          isSelected = false,
          testTag = "tool_delete_btn",
          onClick = { viewModel.timelineEngine.deleteSelected() }
        ),
        FuturisticNavItemData(
          id = "animations",
          label = "Animations",
          icon = Icons.Default.AutoAwesome,
          theme = NavItemThemes.Animations,
          isSelected = activeTab == EditorToolbarTab.ANIMATIONS,
          testTag = "tool_animations_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.ANIMATIONS) }
        ),
        FuturisticNavItemData(
          id = "effects",
          label = "Effects",
          icon = Icons.Default.AutoFixHigh,
          theme = NavItemThemes.Effects,
          isSelected = activeTab == EditorToolbarTab.EFFECTS,
          testTag = "tool_effects_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.EFFECTS) }
        ),
        FuturisticNavItemData(
          id = "speed",
          label = "Speed",
          icon = Icons.Default.Speed,
          theme = NavItemThemes.Speed,
          isSelected = activeTab == EditorToolbarTab.SPEED,
          testTag = "tool_speed_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.SPEED) }
        ),
        FuturisticNavItemData(
          id = "beats",
          label = "Beats",
          icon = Icons.Default.GraphicEq,
          theme = NavItemThemes.Audio,
          isSelected = false,
          testTag = "tool_beats_btn",
          onClick = { viewModel.timelineEngine.jumpToNextAudioPeak() }
        ),
        FuturisticNavItemData(
          id = "tracking",
          label = "Tracking",
          icon = Icons.Default.TrackChanges,
          theme = NavItemThemes.AI,
          isSelected = activeTab == EditorToolbarTab.MOTION_TRACKING,
          testTag = "tool_tracking_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.MOTION_TRACKING) }
        ),
        FuturisticNavItemData(
          id = "ar_filters",
          label = "AR Filters",
          icon = Icons.Default.Face,
          theme = NavItemThemes.AI,
          isSelected = activeTab == EditorToolbarTab.AR_EFFECTS,
          testTag = "tool_ar_filters_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AR_EFFECTS) }
        ),
        FuturisticNavItemData(
          id = "aivideo",
          label = "AI Video",
          icon = Icons.Default.Psychology,
          theme = NavItemThemes.AI,
          isSelected = activeTab == EditorToolbarTab.AI,
          testTag = "tool_aivideo_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AI) }
        ),
        FuturisticNavItemData(
          id = "crop",
          label = "Crop",
          icon = Icons.Default.Crop,
          theme = NavItemThemes.Edit,
          isSelected = activeTab == EditorToolbarTab.TRIM,
          testTag = "tool_crop_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.TRIM) }
        ),
        FuturisticNavItemData(
          id = "duplicate",
          label = "Duplicate",
          icon = Icons.Default.ContentCopy,
          theme = NavItemThemes.Edit,
          isSelected = false,
          testTag = "tool_duplicate_btn",
          onClick = { viewModel.timelineEngine.duplicateSelected() }
        ),
        FuturisticNavItemData(
          id = "replace",
          label = "Replace",
          icon = Icons.Default.FindReplace,
          theme = NavItemThemes.Edit,
          isSelected = activeTab == EditorToolbarTab.MEDIA,
          testTag = "tool_replace_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.MEDIA) }
        ),
        FuturisticNavItemData(
          id = "overlay",
          label = "Overlay",
          icon = Icons.Default.Layers,
          theme = NavItemThemes.Overlay,
          isSelected = false,
          testTag = "tool_overlay_btn",
          onClick = onAddOverlay
        ),
        FuturisticNavItemData(
          id = "adjust",
          label = "Adjust",
          icon = Icons.Default.Tune,
          theme = NavItemThemes.Filters,
          isSelected = activeTab == EditorToolbarTab.ADJUST,
          testTag = "tool_adjust_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.ADJUST) }
        ),
        FuturisticNavItemData(
          id = "filters",
          label = "Filters",
          icon = Icons.Default.Filter,
          theme = NavItemThemes.Filters,
          isSelected = activeTab == EditorToolbarTab.FILTERS,
          testTag = "tool_filters_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.FILTERS) }
        ),
        FuturisticNavItemData(
          id = "retouch",
          label = "Retouch",
          icon = Icons.Default.Face,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_retouch_btn",
          onClick = { viewModel.timelineEngine.toggleRetouch() }
        ),
        FuturisticNavItemData(
          id = "videoquality",
          label = "Video Quality",
          icon = Icons.Default.HighQuality,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_videoquality_btn",
          onClick = { viewModel.timelineEngine.toggleQualityEnhancement() }
        ),
        FuturisticNavItemData(
          id = "removebg",
          label = "Remove BG",
          icon = Icons.Default.PersonRemove,
          theme = NavItemThemes.AI,
          isSelected = activeTab == EditorToolbarTab.AI_MATTING,
          testTag = "tool_removebg_btn",
          onClick = { viewModel.timelineEngine.toggleClipBackgroundRemoval() }
        ),
        FuturisticNavItemData(
          id = "airemover",
          label = "AI Remover",
          icon = Icons.Default.AutoFixNormal,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_airemover_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AI) }
        ),
        FuturisticNavItemData(
          id = "aiexpand",
          label = "AI Expand",
          icon = Icons.Default.AspectRatio,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_aiexpand_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AI) }
        ),
        FuturisticNavItemData(
          id = "airemix",
          label = "AI Remix",
          icon = Icons.Default.Shuffle,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_airemix_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AI) }
        ),
        FuturisticNavItemData(
          id = "eyecontact",
          label = "Eye Contact",
          icon = Icons.Default.Visibility,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_eyecontact_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AI) }
        ),
        FuturisticNavItemData(
          id = "relight",
          label = "Relight",
          icon = Icons.Default.WbSunny,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_relight_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AI) }
        ),
        FuturisticNavItemData(
          id = "opacity",
          label = "Opacity",
          icon = Icons.Default.Opacity,
          theme = NavItemThemes.Edit,
          isSelected = false,
          testTag = "tool_opacity_btn",
          onClick = onOpenOpacityDialog
        ),
        FuturisticNavItemData(
          id = "motionblur",
          label = "Motion Blur",
          icon = Icons.Default.BlurOn,
          theme = NavItemThemes.Effects,
          isSelected = false,
          testTag = "tool_motionblur_btn",
          onClick = { viewModel.timelineEngine.toggleMotionBlur() }
        ),
        FuturisticNavItemData(
          id = "lipsync",
          label = "Lip Sync",
          icon = Icons.Default.RecordVoiceOver,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_lipsync_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AI) }
        ),
        FuturisticNavItemData(
          id = "transform",
          label = "Transform",
          icon = Icons.Default.Transform,
          theme = NavItemThemes.Edit,
          isSelected = activeTab == EditorToolbarTab.EDIT,
          testTag = "tool_transform_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.EDIT) }
        ),
        FuturisticNavItemData(
          id = "autoframe",
          label = "Auto Frame",
          icon = Icons.Default.CropFree,
          theme = NavItemThemes.AI,
          isSelected = false,
          testTag = "tool_autoframe_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.EDIT) }
        ),
        FuturisticNavItemData(
          id = "stabilize",
          label = "Stabilize",
          icon = Icons.Default.Security,
          theme = NavItemThemes.Edit,
          isSelected = false,
          testTag = "tool_stabilize_btn",
          onClick = { viewModel.timelineEngine.toggleStabilization() }
        ),
        FuturisticNavItemData(
          id = "extractaudio",
          label = "Extract Audio",
          icon = Icons.Default.MusicNote,
          theme = NavItemThemes.Audio,
          isSelected = false,
          testTag = "tool_extractaudio_btn",
          onClick = { viewModel.timelineEngine.extractAudioFromSelectedClip() }
        ),
        FuturisticNavItemData(
          id = "isolate",
          label = "Isolate",
          icon = Icons.Default.FilterCenterFocus,
          theme = NavItemThemes.Audio,
          isSelected = false,
          testTag = "tool_isolate_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.AUDIO) }
        ),
        FuturisticNavItemData(
          id = "enhancevoice",
          label = "Enhance Voice",
          icon = Icons.Default.Mic,
          theme = NavItemThemes.Audio,
          isSelected = false,
          testTag = "tool_enhancevoice_btn",
          onClick = { viewModel.timelineEngine.enhanceSelectedAudio() }
        ),
        FuturisticNavItemData(
          id = "videotranslator",
          label = "Translate",
          icon = Icons.Default.Translate,
          theme = NavItemThemes.Captions,
          isSelected = activeTab == EditorToolbarTab.CAPTIONS,
          testTag = "tool_videotranslator_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.CAPTIONS) }
        ),
        FuturisticNavItemData(
          id = "freeze",
          label = "Freeze",
          icon = Icons.Default.AcUnit,
          theme = NavItemThemes.Edit,
          isSelected = false,
          testTag = "tool_freeze_btn",
          onClick = { viewModel.timelineEngine.freezeFrameAtPlayhead() }
        ),
        FuturisticNavItemData(
          id = "reverse",
          label = "Reverse",
          icon = Icons.Default.FastRewind,
          theme = NavItemThemes.Edit,
          isSelected = false,
          testTag = "tool_reverse_btn",
          onClick = { viewModel.timelineEngine.toggleReverseSelectedClip() }
        ),
        FuturisticNavItemData(
          id = "mask",
          label = "Mask",
          icon = Icons.Default.Brush,
          theme = NavItemThemes.Edit,
          isSelected = activeTab == EditorToolbarTab.MASK,
          testTag = "tool_mask_btn",
          onClick = { viewModel.setActiveToolbarTab(EditorToolbarTab.MASK) }
        )
      )
    }

    FuturisticBottomNavBarContainer(
      onBackClick = onCloseEditTools,
      items = editToolItems,
      showDividers = true
    )
  } else {
    // Standard Bottom Toolbar Mode: "Edit Tools" is the FIRST item on the left
    val activeTools = remember(tools) {
      val seenMappedTabs = mutableSetOf<EditorToolbarTab>()
      val seenActionKeys = mutableSetOf<String>()
      val seenNames = mutableSetOf<String>()

      tools.filter { tool ->
        tool.isActive &&
          tool.id != "tool_asset_packs" &&
          tool.id != "tool_ai_tools" &&
          tool.id != "tool_emojis" &&
          tool.actionKey != "TOOL_ASSET_PACKS" &&
          tool.actionKey != "TOOL_AI_TOOLS" &&
          tool.actionKey != "TOOL_EMOJIS" &&
          !tool.name.contains("Asset Pack", ignoreCase = true) &&
          !tool.name.contains("Assets Pack", ignoreCase = true) &&
          !tool.name.equals("AI Tools", ignoreCase = true) &&
          !tool.name.equals("Emojis", ignoreCase = true)
      }.filter { tool ->
        val tab = tool.mappedTab
        val action = tool.actionKey.uppercase().trim()
        val nameNorm = tool.name.lowercase().trim()

        if (seenNames.contains(nameNorm)) {
          false
        } else if (tab != null) {
          if (seenMappedTabs.contains(tab)) {
            false
          } else {
            seenMappedTabs.add(tab)
            seenNames.add(nameNorm)
            true
          }
        } else if (action.isNotBlank()) {
          if (seenActionKeys.contains(action)) {
            false
          } else {
            seenActionKeys.add(action)
            seenNames.add(nameNorm)
            true
          }
        } else {
          seenNames.add(nameNorm)
          true
        }
      }.sortedBy { it.order }
    }

    val editToolsNavItem = FuturisticNavItemData(
      id = "edit_tools_primary",
      label = "Edit Tools",
      icon = Icons.Default.ContentCut,
      theme = NavItemThemes.Edit,
      isSelected = false,
      testTag = "tool_edit_tools_btn",
      onClick = onOpenEditTools
    )

    val navItems = remember(activeTools, activeTab) {
      listOf(editToolsNavItem) + activeTools.map { tool ->
        FuturisticNavItemData(
          id = tool.id,
          label = tool.name,
          icon = tool.icon,
          theme = tool.theme,
          isSelected = tool.mappedTab != null && activeTab == tool.mappedTab,
          testTag = "tool_${tool.id.lowercase()}_btn",
          onClick = { onToolClick(tool) }
        )
      } + FuturisticNavItemData(
        id = "more_tools",
        label = "More",
        icon = Icons.Default.Tune,
        theme = NavItemThemes.DefaultSlate,
        isSelected = false,
        testTag = "more_btn",
        onClick = onMoreClick
      )
    }

    FuturisticBottomNavBarContainer(
      items = navItems,
      showDividers = true
    )
  }
}

@Composable
private fun MoreToolsDialog(
  tools: List<EditorToolItem>,
  onDismiss: () -> Unit,
  onSelectTool: (EditorToolItem) -> Unit
) {
  var selectedCategory by remember { mutableStateOf("All") }
  val activeTools = remember(tools) {
    val seenMappedTabs = mutableSetOf<EditorToolbarTab>()
    val seenActionKeys = mutableSetOf<String>()
    val seenNames = mutableSetOf<String>()

    tools.filter { tool ->
      tool.isActive &&
        tool.id != "tool_asset_packs" &&
        tool.id != "tool_ai_tools" &&
        tool.id != "tool_emojis" &&
        tool.actionKey != "TOOL_ASSET_PACKS" &&
        tool.actionKey != "TOOL_AI_TOOLS" &&
        tool.actionKey != "TOOL_EMOJIS" &&
        !tool.name.contains("Asset Pack", ignoreCase = true) &&
        !tool.name.contains("Assets Pack", ignoreCase = true) &&
        !tool.name.equals("AI Tools", ignoreCase = true) &&
        !tool.name.equals("Emojis", ignoreCase = true)
    }.filter { tool ->
      val tab = tool.mappedTab
      val action = tool.actionKey.uppercase().trim()
      val nameNorm = tool.name.lowercase().trim()

      if (seenNames.contains(nameNorm)) {
        false
      } else if (tab != null) {
        if (seenMappedTabs.contains(tab)) {
          false
        } else {
          seenMappedTabs.add(tab)
          seenNames.add(nameNorm)
          true
        }
      } else if (action.isNotBlank()) {
        if (seenActionKeys.contains(action)) {
          false
        } else {
          seenActionKeys.add(action)
          seenNames.add(nameNorm)
          true
        }
      } else {
        seenNames.add(nameNorm)
        true
      }
    }.sortedBy { it.order }
  }
  val categories = remember(activeTools) {
    val cats = activeTools.map { it.category.replaceFirstChar { c -> c.uppercase() } }.distinct()
    listOf("All") + cats
  }
  val filteredTools = remember(activeTools, selectedCategory) {
    if (selectedCategory == "All") activeTools
    else activeTools.filter { it.category.equals(selectedCategory, ignoreCase = true) }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF0F1523),
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Editor Tools",
          color = Color.White,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFF1E283E)
        ) {
          Text(
            text = "${activeTools.size} Tools",
            color = CyanAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Category Filter Chips
        if (categories.size > 2) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            categories.forEach { cat ->
              val isSelected = selectedCategory == cat
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) CyanAccent.copy(alpha = 0.2f) else Color(0xFF1B2233),
                border = BorderStroke(1.dp, if (isSelected) CyanAccent else Color(0xFF2E3852)),
                modifier = Modifier.clickable { selectedCategory = cat }
              ) {
                Text(
                  text = cat,
                  color = if (isSelected) CyanAccent else Color(0xFF9EABB8),
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }
          }
        }

        // Tools Grid (Displaying all Firestore tools)
        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredTools, key = { it.id }) { tool ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF1B2233),
              border = BorderStroke(1.dp, Color(0xFF2E3852)),
              modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clickable { onSelectTool(tool) }
                .testTag("editor_tool_${tool.id}")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(tool.theme.bgCircle.copy(alpha = 0.85f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.name,
                    tint = tool.theme.iconTint,
                    modifier = Modifier.size(18.dp)
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = tool.name,
                  color = Color.White,
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = CyanAccent, fontWeight = FontWeight.Bold)
      }
    }
  )
}

private fun applyTextureViewAspectFit(
  tv: android.view.TextureView,
  videoWidth: Int,
  videoHeight: Int
) {
  val viewW = tv.width.toFloat()
  val viewH = tv.height.toFloat()
  if (viewW <= 0f || viewH <= 0f || videoWidth <= 0 || videoHeight <= 0) return

  val viewRatio = viewW / viewH
  val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()

  var scaleX = 1f
  var scaleY = 1f

  if (videoRatio > viewRatio) {
    // Video is wider than canvas -> letterbox top & bottom
    scaleY = viewRatio / videoRatio
  } else {
    // Video is taller than canvas -> pillarbox left & right
    scaleX = videoRatio / viewRatio
  }

  val matrix = Matrix()
  matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
  tv.setTransform(matrix)
}

private fun formatDurationShort(timeMs: Long): String {
  val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun SyntheticClipPreview(
  clip: VideoClip,
  currentPosMs: Long,
  colorFilter: ColorFilter? = null,
  modifier: Modifier = Modifier
) {
  val stockItem = remember(clip.uri) {
    val stockId = clip.uri.removePrefix("stock://")
    StockMediaCatalog.stockItems.find { it.id == stockId }
  }

  val (startColor, endColor, iconEmoji) = remember(clip.id, clip.name, stockItem) {
    if (stockItem != null) {
      Triple(Color(stockItem.gradientStart), Color(stockItem.gradientEnd), stockItem.iconEmoji)
    } else if (clip.name.contains("Mountain", ignoreCase = true) || clip.name.contains("Stream", ignoreCase = true) || clip.uri.contains("nature", ignoreCase = true)) {
      Triple(Color(0xFF0077B6), Color(0xFF00B4D8), "🏔️")
    } else if (clip.name.contains("Skyline", ignoreCase = true) || clip.name.contains("Sunset", ignoreCase = true) || clip.name.contains("Golden", ignoreCase = true) || clip.uri.contains("urban", ignoreCase = true)) {
      Triple(Color(0xFFE85D04), Color(0xFF7209B7), "🌇")
    } else {
      Triple(Color(0xFF1E293B), Color(0xFF0F172A), "🎬")
    }
  }

  val relativeClipPosMs = (currentPosMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
  val progress = if (clip.durationMs > 0) relativeClipPosMs.toFloat() / clip.durationMs else 0f

  val filterAppliedModifier = if (colorFilter != null) {
    modifier.drawWithContent {
      drawIntoCanvas { canvas ->
        val paint = androidx.compose.ui.graphics.Paint().apply {
          this.colorFilter = colorFilter
        }
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
        drawContent()
        canvas.restore()
      }
    }
  } else {
    modifier
  }

  Box(
    modifier = filterAppliedModifier
      .background(Brush.linearGradient(listOf(startColor, endColor)))
      .drawBehind {
        val scanY = size.height * ((progress * 3f) % 1f)
        drawLine(
          color = Color.White.copy(alpha = 0.08f),
          start = Offset(0f, scanY),
          end = Offset(size.width, scanY),
          strokeWidth = 3f
        )
      },
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(16.dp)
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.35f))
          .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Text(text = iconEmoji, fontSize = 24.sp)
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = clip.name,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(
          text = "${formatDurationShort(relativeClipPosMs)} / ${formatDurationShort(clip.durationMs)}",
          color = CyanAccent,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
      }
    }
  }
}
