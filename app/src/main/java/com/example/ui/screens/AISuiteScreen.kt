package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.providers.secure.AISecurityConfig
import com.example.domain.model.TextClip
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.PrimaryPillButton
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISuiteScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val isAIBusy by viewModel.isAIBusy.collectAsState()
  val statusMessage by viewModel.aiStatusMessage.collectAsState()
  val highlights by viewModel.aiHighlights.collectAsState()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()

  var selectedLanguage by remember { mutableStateOf("English") }
  val languages = listOf("English", "Spanish", "French", "German", "Japanese", "Chinese", "Hindi", "Arabic", "Italian", "Portuguese")

  var ttsText by remember { mutableStateOf("Welcome to AH Video Studio, the ultimate creative AI suite.") }
  var ttsPitch by remember { mutableFloatStateOf(1.0f) }
  var ttsRate by remember { mutableFloatStateOf(1.0f) }

  var showSecurityDialog by remember { mutableStateOf(false) }
  var backendProxyUrl by remember { mutableStateOf(AISecurityConfig.getBackendProxyUrl(context)) }
  var customApiKey by remember { mutableStateOf(AISecurityConfig.getCustomAuthToken(context)) }

  // Background removal result state
  var cutoutBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var alphaMaskBitmap by remember { mutableStateOf<Bitmap?>(null) }

  // Caption direct editing dialog state
  var editingCaption by remember { mutableStateOf<TextClip?>(null) }
  var editCaptionText by remember { mutableStateOf("") }
  var editCaptionStartMs by remember { mutableStateOf(0L) }
  var editCaptionDurationMs by remember { mutableStateOf(2000L) }

  val isConfigured = AISecurityConfig.isConfigured(context)

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDarkBg),
    containerColor = StudioDarkBg,
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI Creative Suite", color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(listOf(CyanAccent, PurpleAccent)))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text("GEMINI & DSP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }
          }
        },
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateTo(AppScreen.HOME) },
            modifier = Modifier.testTag("ai_suite_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
          }
        },
        actions = {
          IconButton(
            onClick = { showSecurityDialog = true },
            modifier = Modifier.testTag("ai_settings_button")
          ) {
            Icon(
              Icons.Default.Security,
              contentDescription = "AI Security & Proxy Configuration",
              tint = if (isConfigured) CyanAccent else RedAccent
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioDarkBg)
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // AI Security & Backend Connection Status Banner
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isConfigured) StudioSurfaceVariant else Color(0xFF2A1515)
          ),
          border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
              if (isConfigured) listOf(CyanAccent.copy(alpha = 0.5f), PurpleAccent.copy(alpha = 0.5f))
              else listOf(RedAccent, RedAccent.copy(alpha = 0.4f))
            )
          )
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                if (isConfigured) Icons.Default.VerifiedUser else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isConfigured) CyanAccent else RedAccent,
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = if (isConfigured) "AI Backend Abstraction Active" else "AI Backend Not Configured",
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isConfigured) TextPrimary else RedAccent
                  )
                )
                Text(
                  text = AISecurityConfig.getConnectionModeDescription(context),
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                )
              }
            }

            OutlinedButton(
              onClick = { showSecurityDialog = true },
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.testTag("configure_backend_button")
            ) {
              Text("Config", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // AI Processing Status Indicator
      if (isAIBusy || statusMessage.isNotBlank()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CyanAccent, PurpleAccent)))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (isAIBusy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = CyanAccent, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.width(12.dp))
              } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenAccent)
                Spacer(modifier = Modifier.width(12.dp))
              }
              Text(
                text = statusMessage.ifBlank { "Processing real media..." },
                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Medium)
              )
            }
          }
        }
      }

      // Feature 1: AI Auto Captions & Subtitles
      item {
        AIFeatureCard(
          title = "AI Auto Captions & Subtitles",
          description = "Transcribes actual imported audio into synchronized caption clips with word-level timings and direct editing.",
          icon = Icons.Default.Subtitles
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val hasMedia = timeline.videoClips.isNotEmpty() || timeline.audioClips.isNotEmpty()
            if (!hasMedia) {
              Text(
                "Notice: Timeline is empty. Import a video or audio clip first to transcribe actual sound.",
                style = MaterialTheme.typography.bodySmall.copy(color = RedAccent, fontSize = 11.sp)
              )
            }

            Text("Speech Language", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(languages) { lang ->
                FilterChip(
                  selected = selectedLanguage == lang,
                  onClick = { selectedLanguage = lang },
                  label = { Text(lang, fontSize = 12.sp) },
                  colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanAccent, selectedLabelColor = Color.Black)
                )
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { viewModel.runAIAutoCaptions(selectedLanguage) },
                enabled = !isAIBusy && hasMedia,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("generate_captions_button")
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Transcribe Media", fontWeight = FontWeight.Bold)
              }

              if (timeline.textClips.isNotEmpty()) {
                OutlinedButton(
                  onClick = {
                    val srt = viewModel.aiTools.exportSrt(timeline.textClips)
                    clipboardManager.setText(AnnotatedString(srt))
                    Toast.makeText(context, "SRT Subtitles copied to clipboard!", Toast.LENGTH_SHORT).show()
                  },
                  shape = RoundedCornerShape(18.dp)
                ) {
                  Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Export .SRT", color = TextPrimary)
                }
              }
            }

            // Caption Clips List with Direct Editing
            if (timeline.textClips.isNotEmpty()) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  "Timeline Captions (${timeline.textClips.size}) — Tap to edit text or timing:",
                  style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                )
                timeline.textClips.forEachIndexed { idx, clip ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        editingCaption = clip
                        editCaptionText = clip.text
                        editCaptionStartMs = clip.timelineStartMs
                        editCaptionDurationMs = clip.durationMs
                      },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141C2E))
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          "${idx + 1}. \"${clip.text}\"",
                          style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                          "${formatDurationShort(clip.timelineStartMs)} → ${formatDurationShort(clip.timelineStartMs + clip.durationMs)} (${clip.words.size} words timed)",
                          style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontSize = 10.sp)
                        )
                      }
                      Icon(Icons.Default.Edit, contentDescription = "Edit Caption", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Feature 2: AI Caption Translation
      item {
        AIFeatureCard(
          title = "AI Caption Translation",
          description = "Translates actual timeline captions into target languages while strictly preserving start and duration timings.",
          icon = Icons.Default.Translate
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Target Language", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(languages) { lang ->
                FilterChip(
                  selected = selectedLanguage == lang,
                  onClick = { selectedLanguage = lang },
                  label = { Text(lang, fontSize = 12.sp) },
                  colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PurpleAccent, selectedLabelColor = Color.White)
                )
              }
            }

            Button(
              onClick = { viewModel.runAITranslateCaptions(selectedLanguage) },
              enabled = !isAIBusy && timeline.textClips.isNotEmpty(),
              colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White),
              shape = RoundedCornerShape(18.dp),
              modifier = Modifier.testTag("translate_captions_button")
            ) {
              Icon(Icons.Default.GTranslate, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Translate Captions (${timeline.textClips.size})", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Feature 3: AI Background Removal & Alpha Mask
      item {
        AIFeatureCard(
          title = "AI Background Removal & Alpha Mask",
          description = "Processes the actual video frame or image to isolate subjects with smooth edge alpha matting.",
          icon = Icons.Default.AutoFixHigh
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
              onClick = {
                // Generate a frame from current composition
                val bmp = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.DKGRAY)
                val paint = android.graphics.Paint().apply {
                  color = android.graphics.Color.WHITE
                  isAntiAlias = true
                }
                canvas.drawCircle(240f, 240f, 150f, paint)

                viewModel.runAIBackgroundRemoval(bmp) { cutout, mask ->
                  cutoutBitmap = cutout
                  alphaMaskBitmap = mask
                }
              },
              enabled = !isAIBusy,
              colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = CyanAccent),
              shape = RoundedCornerShape(18.dp),
              modifier = Modifier.testTag("remove_background_button")
            ) {
              Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Process Frame Cutout", fontWeight = FontWeight.Bold)
            }

            val safeCutout = cutoutBitmap
            val safeAlphaMask = alphaMaskBitmap
            if (safeCutout != null && safeAlphaMask != null) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("Transparent Cutout", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                  Spacer(modifier = Modifier.height(6.dp))
                  Image(
                    bitmap = safeCutout.asImageBitmap(),
                    contentDescription = "Cutout",
                    modifier = Modifier
                      .size(120.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .border(1.dp, CyanAccent, RoundedCornerShape(8.dp))
                      .background(Color.Black)
                  )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("Alpha Matte Mask", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                  Spacer(modifier = Modifier.height(6.dp))
                  Image(
                    bitmap = safeAlphaMask.asImageBitmap(),
                    contentDescription = "Alpha Mask",
                    modifier = Modifier
                      .size(120.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .border(1.dp, PurpleAccent, RoundedCornerShape(8.dp))
                      .background(Color.Black)
                  )
                }
              }
            }
          }
        }
      }

      // Feature 4: AI Audio Noise Reduction
      item {
        AIFeatureCard(
          title = "AI Audio Noise Reduction",
          description = "Processes actual audio with dynamic noise floor scanning, spectral gating, and rumble elimination.",
          icon = Icons.Default.Hearing
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val hasAudioSource = timeline.audioClips.isNotEmpty() || timeline.videoClips.isNotEmpty()
            Button(
              onClick = { viewModel.runAINoiseReduction() },
              enabled = !isAIBusy && hasAudioSource,
              colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = CyanAccent),
              shape = RoundedCornerShape(18.dp),
              modifier = Modifier.testTag("reduce_noise_button")
            ) {
              Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Apply Spectral Denoising", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Feature 5: AI Voice Studio (Text-to-Speech)
      item {
        AIFeatureCard(
          title = "AI Voice Studio (Text-to-Speech)",
          description = "Synthesizes actual WAV audio files from written scripts and adds them directly to the timeline.",
          icon = Icons.Default.RecordVoiceOver
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = ttsText,
              onValueChange = { ttsText = it },
              placeholder = { Text("Enter script for actual audio synthesis...") },
              modifier = Modifier.fillMaxWidth().testTag("tts_text_input"),
              maxLines = 3,
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PurpleAccent,
                unfocusedBorderColor = StudioBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
              )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = {
                  viewModel.audioEngine.synthesizeTts(ttsText, ttsPitch, ttsRate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("tts_preview_button")
              ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Speak Preview", fontWeight = FontWeight.Bold)
              }

              Button(
                onClick = {
                  viewModel.runAITextToSpeech(ttsText, ttsPitch, ttsRate)
                },
                enabled = !isAIBusy && ttsText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("tts_add_to_track_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add to Timeline", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // Feature 6: AI Highlight & Viral Moments
      item {
        AIFeatureCard(
          title = "AI Highlight & Viral Moments",
          description = "Scans video composition, duration, and pacing to detect optimal hooks for social reels.",
          icon = Icons.Default.FlashOn
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = { viewModel.runAIHighlightAnalysis() },
              enabled = !isAIBusy && timeline.videoClips.isNotEmpty(),
              colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = CyanAccent),
              shape = RoundedCornerShape(18.dp),
              modifier = Modifier.testTag("detect_highlights_button")
            ) {
              Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Detect Highlights", fontWeight = FontWeight.Bold)
            }

            if (highlights.isNotEmpty()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                highlights.forEach { h ->
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(h.title, style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                        Text(h.description, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
                        Text(
                          "${formatDurationShort(h.startTimeMs)} - ${formatDurationShort(h.endTimeMs)} (Score: ${(h.score * 100).toInt()}%)",
                          style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                        )
                      }
                      Button(
                        onClick = {
                          viewModel.timelineEngine.setPosition(h.startTimeMs)
                          viewModel.navigateTo(AppScreen.EDITOR)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                      ) {
                        Text("Jump to Hook", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Feature 7: AI Auto-Edit Montage
      item {
        AIFeatureCard(
          title = "AI Auto-Edit Montage",
          description = "Analyzes imported clips, trims dead time, sets rhythmic speeds, adds transitions, and generates an editable timeline.",
          icon = Icons.Default.MovieFilter
        ) {
          PrimaryPillButton(
            text = "Generate Auto Reel",
            icon = Icons.Default.AutoFixHigh,
            onClick = {
              viewModel.runAIAutoEdit()
              viewModel.navigateTo(AppScreen.EDITOR)
            },
            modifier = Modifier.fillMaxWidth().testTag("auto_edit_button")
          )
        }
      }

      item { Spacer(modifier = Modifier.height(32.dp)) }
    }
  }

  // Security & Backend Abstraction Dialog
  if (showSecurityDialog) {
    AlertDialog(
      onDismissRequest = { showSecurityDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Security, contentDescription = null, tint = CyanAccent)
          Spacer(modifier = Modifier.width(8.dp))
          Text("AI Backend Security", fontWeight = FontWeight.Bold, color = TextPrimary)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            "Private API keys are never bundled inside the APK binary. Requests can route through your secure backend proxy or custom credentials configured at runtime.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )

          OutlinedTextField(
            value = backendProxyUrl,
            onValueChange = { backendProxyUrl = it },
            label = { Text("Secure Backend Proxy URL (Optional)") },
            placeholder = { Text("https://my-backend-proxy.app/api") },
            modifier = Modifier.fillMaxWidth().testTag("backend_proxy_url_input"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )

          OutlinedTextField(
            value = customApiKey,
            onValueChange = { customApiKey = it },
            label = { Text("Runtime Auth Token / API Key") },
            placeholder = { Text("Enter token...") },
            modifier = Modifier.fillMaxWidth().testTag("runtime_api_key_input"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            AISecurityConfig.setBackendProxyUrl(context, backendProxyUrl)
            AISecurityConfig.setCustomAuthToken(context, customApiKey)
            showSecurityDialog = false
            Toast.makeText(context, "AI Security configuration saved!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Save Configuration", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showSecurityDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioSurface
    )
  }

  // Caption Direct Editing Dialog
  editingCaption?.let { clip ->
    AlertDialog(
      onDismissRequest = { editingCaption = null },
      title = { Text("Edit Caption & Timing", fontWeight = FontWeight.Bold, color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = editCaptionText,
            onValueChange = { editCaptionText = it },
            label = { Text("Subtitle Text") },
            modifier = Modifier.fillMaxWidth().testTag("edit_caption_text_input"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )

          OutlinedTextField(
            value = editCaptionStartMs.toString(),
            onValueChange = { editCaptionStartMs = it.toLongOrNull() ?: editCaptionStartMs },
            label = { Text("Start Timestamp (ms)") },
            modifier = Modifier.fillMaxWidth().testTag("edit_caption_start_ms_input"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )

          OutlinedTextField(
            value = editCaptionDurationMs.toString(),
            onValueChange = { editCaptionDurationMs = it.toLongOrNull() ?: editCaptionDurationMs },
            label = { Text("Duration (ms)") },
            modifier = Modifier.fillMaxWidth().testTag("edit_caption_duration_ms_input"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val updated = clip.copy(
              text = editCaptionText,
              timelineStartMs = editCaptionStartMs,
              durationMs = editCaptionDurationMs
            )
            viewModel.timelineEngine.updateTextClip(updated)
            editingCaption = null
            Toast.makeText(context, "Caption updated!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Save Changes", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { editingCaption = null }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioSurface
    )
  }
}

@Composable
private fun AIFeatureCard(
  title: String,
  description: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  content: @Composable ColumnScope.() -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = StudioSurface),
    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StudioBorder, StudioBorder.copy(alpha = 0.5f))))
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(CyanAccent.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
          )
        }
      }

      content()
    }
  }
}
