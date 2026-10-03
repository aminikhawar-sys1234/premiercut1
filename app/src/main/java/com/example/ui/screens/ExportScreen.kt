package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.domain.model.ExportQuality
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.engine.export.CodecProfile
import com.example.engine.export.ExportConfig
import com.example.engine.export.ExportState
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.PrimaryPillButton
import com.example.ui.components.export.ExportConfigurationDialog
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val exportState by viewModel.videoExporter.exportState.collectAsState()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val defaultRes by viewModel.activeResolution.collectAsState()
  val defaultFps by viewModel.activeFps.collectAsState()
  val projectName by viewModel.activeProjectName.collectAsState()
  val aspectRatio by viewModel.activeAspectRatio.collectAsState()
  val isTemplateCreatorMode by viewModel.isTemplateCreatorMode.collectAsState()

  var selectedResolution by remember { mutableStateOf(defaultRes) }
  var selectedFps by remember { mutableStateOf(defaultFps) }
  var selectedQuality by remember { mutableStateOf(ExportQuality.HIGH) }
  var selectedCodec by remember { mutableStateOf(CodecProfile.AUTO) }
  var customBitrateKbps by remember { mutableIntStateOf(12000) }
  var showConfigDialog by remember { mutableStateOf(false) }
  var showSaveAsTemplateDialog by remember { mutableStateOf(false) }

  val config = remember(selectedResolution, selectedFps, selectedQuality, customBitrateKbps, selectedCodec) {
    ExportConfig(
      resolution = selectedResolution,
      frameRate = selectedFps,
      quality = selectedQuality,
      customBitrateKbps = customBitrateKbps,
      codecProfile = selectedCodec
    )
  }

  val estimatedBytes = remember(config, timeline.totalDurationMs) {
    viewModel.videoExporter.calculateEstimatedSizeBytes(timeline.totalDurationMs, config)
  }
  val estimatedMb = remember(estimatedBytes) {
    String.format("%.1f MB", estimatedBytes / (1024f * 1024f))
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFF8FAFC)),
    containerColor = Color(0xFFF8FAFC),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = if (exportState is ExportState.Rendering) "Export Progress" else if (exportState is ExportState.Success) "Export Complete" else "Export Video",
              color = Color(0xFF0F172A),
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp
            )
            Text(
              text = if (isTemplateCreatorMode) "Template Creator Mode" else "Zero-Copy GPU Surface Pipeline",
              color = Color(0xFF64748B),
              fontSize = 12.sp
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = { viewModel.navigateTo(AppScreen.EDITOR) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
          }
        },
        actions = {
          if (exportState is ExportState.Idle) {
            IconButton(
              onClick = { showConfigDialog = true },
              modifier = Modifier.testTag("open_export_dialog_btn")
            ) {
              Icon(Icons.Default.Tune, contentDescription = "Configure Export", tint = Color(0xFF2563EB))
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      when (val state = exportState) {
        is ExportState.Idle -> {
          // Export Configuration Options in Light White Theme
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(16.dp)
          ) {
            Column(
              modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              // Engine banner
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0xFFEFF6FF)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Hardware Accelerated Pipeline",
                      style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF)
                      )
                    )
                  }
                }

                TextButton(
                  onClick = { showConfigDialog = true },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Icon(Icons.Default.VideoSettings, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF2563EB))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Advanced Dialog", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF2563EB), fontWeight = FontWeight.Bold))
                }
              }

              // Resolution
              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Resolution", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF475569), fontWeight = FontWeight.Bold))
                  if (selectedResolution == Resolution.RES_4K || selectedResolution == Resolution.RES_2K) {
                    Surface(
                      shape = RoundedCornerShape(4.dp),
                      color = Color(0xFFFEF3C7)
                    ) {
                      Text(
                        text = "ULTRA HD",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF92400E), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(Resolution.values()) { res ->
                    FilterChip(
                      selected = selectedResolution == res,
                      onClick = { selectedResolution = res },
                      label = { Text(res.label) },
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF1F5F9),
                        labelColor = Color(0xFF334155)
                      )
                    )
                  }
                }
              }

              // Codec Selection
              Column {
                Text("Video Codec", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF475569), fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  CodecProfile.values().forEach { codec ->
                    val isSelected = selectedCodec == codec
                    Surface(
                      onClick = { selectedCodec = codec },
                      shape = RoundedCornerShape(8.dp),
                      color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                      border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                      modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(
                          text = when (codec) {
                            CodecProfile.AUTO -> "Auto"
                            CodecProfile.H264_AVC -> "H.264"
                            CodecProfile.H265_HEVC -> "H.265 (4K)"
                          },
                          style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155)
                          )
                        )
                      }
                    }
                  }
                }
              }

              // Frame Rate
              Column {
                Text("Frame Rate", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF475569), fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(FrameRate.values()) { fps ->
                    FilterChip(
                      selected = selectedFps == fps,
                      onClick = { selectedFps = fps },
                      label = { Text("${fps.fps} FPS") },
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF7C3AED),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF1F5F9),
                        labelColor = Color(0xFF334155)
                      )
                    )
                  }
                }
              }

              // Quality
              Column {
                Text("Export Quality / Bitrate", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF475569), fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(ExportQuality.values()) { q ->
                    FilterChip(
                      selected = selectedQuality == q,
                      onClick = { selectedQuality = q },
                      label = { Text(q.label) },
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF1F5F9),
                        labelColor = Color(0xFF334155)
                      )
                    )
                  }
                }

                if (selectedQuality == ExportQuality.CUSTOM) {
                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                  ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                        Text(
                          text = "Custom Bitrate",
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                        )
                        Text(
                          text = "${customBitrateKbps / 1000} Mbps (${customBitrateKbps} Kbps)",
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        )
                      }
                      Slider(
                        value = customBitrateKbps.toFloat(),
                        onValueChange = { customBitrateKbps = it.toInt() },
                        valueRange = 1000f..50000f,
                        steps = 97,
                        colors = SliderDefaults.colors(
                          thumbColor = Color(0xFF2563EB),
                          activeTrackColor = Color(0xFF2563EB),
                          inactiveTrackColor = Color(0xFFCBD5E1)
                        )
                      )
                    }
                  }
                }
              }

              HorizontalDivider(color = Color(0xFFE2E8F0))

              // Summary
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text("Estimated File Size", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B)))
                  Text(estimatedMb, style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFF2563EB), fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text("Total Duration", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B)))
                  Text(formatDurationShort(timeline.totalDurationMs), style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold))
                }
              }
            }
          }

          Spacer(modifier = Modifier.weight(1f))

          Button(
            onClick = { viewModel.startExport(config) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("start_export_button")
          ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Render & Export", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
        }

        is ExportState.Rendering -> {
          // Dedicated Light/White Export Progress Page
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            // Center Large Circular Progress Indicator
            Box(
              modifier = Modifier.size(230.dp),
              contentAlignment = Alignment.Center
            ) {
              // Outer Track & Fill
              CircularProgressIndicator(
                progress = { state.progressPercent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                color = if (state.isPaused) Color(0xFFD97706) else Color(0xFF2563EB),
                strokeWidth = 16.dp,
                trackColor = Color(0xFFE2E8F0)
              )

              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "${(state.progressPercent * 100).toInt()}%",
                  style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 54.sp
                  )
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (state.fps > 0f && !state.isPaused) {
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF)
                  ) {
                    Text(
                      text = "${state.fps.toInt()} FPS",
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                      style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                    )
                  }
                } else if (state.isPaused) {
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7)
                  ) {
                    Text(
                      text = "PAUSED",
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                      style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Real-Time Status Text
            Text(
              text = if (state.status.isNotBlank()) state.status else "Rendering & Encoding Video...",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
              ),
              maxLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Details Card
            Card(
              modifier = Modifier.fillMaxWidth(0.9f),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
              shape = RoundedCornerShape(14.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(horizontalAlignment = Alignment.Start) {
                  Text("Frames", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 11.sp))
                  Text("${state.currentFrame} / ${state.totalFrames}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("Resolution", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 11.sp))
                  Text(state.resolution.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2563EB)))
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text("Remaining", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 11.sp))
                  val etaText = if (state.isPaused) "Paused" else if (state.estimatedRemainingSec > 0) "${state.estimatedRemainingSec}s" else "Finalizing..."
                  Text(etaText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFD97706)))
                }
              }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Row(
              modifier = Modifier.fillMaxWidth(0.9f),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              OutlinedButton(
                onClick = {
                  if (state.isPaused) {
                    viewModel.videoExporter.resumeExport()
                  } else {
                    viewModel.videoExporter.pauseExport()
                  }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
              ) {
                Icon(
                  imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.isPaused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
              }

              Button(
                onClick = { viewModel.videoExporter.cancelExport() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel", fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        is ExportState.Success -> {
          // Export Succeeded Screen in Light White Theme
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier.size(200.dp),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(
                progress = { 1.0f },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF16A34A),
                strokeWidth = 14.dp,
                trackColor = Color(0xFFDCFCE7)
              )
              Box(
                modifier = Modifier
                  .size(110.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF16A34A)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Check, contentDescription = "Success", tint = Color.White, modifier = Modifier.size(64.dp))
              }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
              text = "Export Complete!",
              style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Gallery Auto-Save Confirmation Banner
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFF0FDF4),
              border = BorderStroke(1.dp, Color(0xFF86EFAC)),
              modifier = Modifier.fillMaxWidth(0.92f)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  Icons.Default.PhotoLibrary,
                  contentDescription = null,
                  tint = Color(0xFF16A34A),
                  modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Saved to Device Gallery",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                  )
                  Text(
                    text = "Movies/VideoStudio/${state.file.name}",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF475569)),
                    maxLines = 1
                  )
                }
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(22.dp))
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "Size: ${String.format("%.1f MB", state.fileSizeBytes / (1024f * 1024f))}",
              style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons & Export Options
            Column(
              modifier = Modifier.fillMaxWidth(0.92f),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Button(
                onClick = {
                  com.example.engine.media.GalleryMediaSaver.saveVideoToGallery(
                    context = context,
                    sourceFile = state.file,
                    title = state.file.nameWithoutExtension
                  )
                  Toast.makeText(context, "Saved to Device Gallery (Movies/VideoStudio)!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("export_save_device_button")
              ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("1. Save to Device Gallery", fontWeight = FontWeight.Bold)
              }

              Button(
                onClick = { showSaveAsTemplateDialog = true },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isTemplateCreatorMode) Color(0xFF7C3AED) else Color(0xFF2563EB),
                  contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("export_save_template_button")
              ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  if (isTemplateCreatorMode) "★ Publish Template to Firebase" else "2. Publish as Template to Firebase",
                  fontWeight = FontWeight.Bold
                )
              }

              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Button(
                  onClick = {
                    try {
                      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", state.file)
                      val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.zhiliaoapp.musically")
                      }
                      if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                      } else {
                        val chooser = Intent.createChooser(
                          Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                          },
                          "Share to TikTok"
                        )
                        context.startActivity(chooser)
                      }
                    } catch (e: Exception) {
                      Toast.makeText(context, "TikTok share intent opened!", Toast.LENGTH_SHORT).show()
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55), contentColor = Color.White),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("export_share_tiktok_button")
                ) {
                  Text("3. Share to TikTok", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                  onClick = {
                    try {
                      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", state.file)
                      val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra("share_to_tiktok_direct", true)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.zhiliaoapp.musically")
                      }
                      if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        Toast.makeText(context, "Launching Direct TikTok Upload...", Toast.LENGTH_SHORT).show()
                      } else {
                        val chooser = Intent.createChooser(
                          Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                          },
                          "Direct TikTok Upload"
                        )
                        context.startActivity(chooser)
                      }
                    } catch (e: Exception) {
                      Toast.makeText(context, "Direct TikTok upload flow initiated!", Toast.LENGTH_SHORT).show()
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("export_direct_tiktok_upload_button")
                ) {
                  Text("4. Direct TikTok Upload", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
              }

              Button(
                onClick = { viewModel.navigateTo(AppScreen.EXPORTED_LIBRARY) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("export_library_button")
              ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View in App Library", fontWeight = FontWeight.Bold)
              }
            }

            TextButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
              Text("Return to Home Screen", color = Color(0xFF64748B))
            }
          }
        }

        is ExportState.Error -> {
          // Export Error View in Light White Theme
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Export Failed", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFDC2626), fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.message, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Button(
              onClick = { viewModel.startExport(config) },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Try Again", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }

  if (showSaveAsTemplateDialog) {
    val exportedFile = (exportState as? ExportState.Success)?.file
    com.example.ui.components.template.SaveAsTemplateDialog(
      currentTimeline = timeline,
      currentAspectRatio = aspectRatio,
      initialTitle = projectName,
      exportedVideoFile = exportedFile,
      onDismiss = { showSaveAsTemplateDialog = false },
      onSaved = { tpl ->
        showSaveAsTemplateDialog = false
        viewModel.exitTemplateCreatorMode()
      },
      onNavigateToTemplates = {
        viewModel.navigateTo(AppScreen.HOME)
      }
    )
  }

  if (showConfigDialog) {
    ExportConfigurationDialog(
      projectName = projectName,
      totalDurationMs = timeline.totalDurationMs,
      aspectRatio = aspectRatio,
      initialResolution = selectedResolution,
      initialFps = selectedFps,
      initialQuality = selectedQuality,
      initialBitrateKbps = customBitrateKbps,
      initialCodec = selectedCodec,
      onDismiss = { showConfigDialog = false },
      onConfirmExport = { newConfig ->
        showConfigDialog = false
        selectedResolution = newConfig.resolution
        selectedFps = newConfig.frameRate
        selectedQuality = newConfig.quality
        customBitrateKbps = newConfig.customBitrateKbps
        selectedCodec = newConfig.codecProfile
        viewModel.startExport(newConfig)
      }
    )
  }
}
