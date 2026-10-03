package com.example.ui.components.audio

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.AudioClip
import com.example.domain.model.AudioEffectsSettings
import com.example.domain.model.VoiceEffect
import com.example.ui.StudioViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StudioDark
import com.example.ui.theme.StudioSurface
import java.io.File
import java.util.Locale

@Composable
fun RecordAudioPanel(
  viewModel: StudioViewModel,
  activeVoiceEffect: VoiceChangerItem?,
  voiceEnhanceEnabled: Boolean,
  voiceEnhanceLevel: Int,
  onOpenVoiceChanger: () -> Unit,
  onOpenVoiceEnhance: () -> Unit,
  onClose: () -> Unit,
  onCompleteAndClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isRecording by viewModel.audioEngine.isRecording.collectAsState()
  val recordDurationMs by viewModel.audioEngine.recordingDurationMs.collectAsState()
  val currentDecibels by viewModel.audioEngine.currentDecibels.collectAsState()

  var recordedFile by remember { mutableStateOf<File?>(null) }
  var hasRecordedAudio by remember { mutableStateOf(false) }
  var showTeleprompterDialog by remember { mutableStateOf(false) }
  var teleprompterScript by remember {
    mutableStateOf("Welcome to my new video! In this episode, we are going to explore the top creative editing tools.")
  }
  var showVolumeDialog by remember { mutableStateOf(false) }
  var micVolumeGain by remember { mutableStateOf(1.0f) }
  var recordWithPlayback by remember { mutableStateOf(false) }

  // Mic permission launcher
  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      viewModel.audioEngine.startVoiceRecording {}
    } else {
      Toast.makeText(context, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show()
    }
  }

  fun toggleRecording() {
    val hasPermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return
    }

    if (isRecording) {
      val file = viewModel.audioEngine.stopVoiceRecording()
      recordedFile = file
      hasRecordedAudio = true
      Toast.makeText(context, "Recording completed!", Toast.LENGTH_SHORT).show()
    } else {
      recordedFile = null
      hasRecordedAudio = false
      viewModel.audioEngine.startVoiceRecording {}
    }
  }

  // Animation for recording button pulse
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (isRecording) 1.15f else 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDark)
  ) {
    // Header: Delete/Clear option + Record status/title in center + ✓ Confirm button + ❌ Close button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0F1523))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Left: Delete / Clear option
      TextButton(
        onClick = {
          if (isRecording) {
            viewModel.audioEngine.stopVoiceRecording()
          }
          recordedFile = null
          hasRecordedAudio = false
          Toast.makeText(context, "Recording cleared", Toast.LENGTH_SHORT).show()
        },
        enabled = hasRecordedAudio || isRecording,
        colors = ButtonDefaults.textButtonColors(
          contentColor = if (hasRecordedAudio || isRecording) Color(0xFFFF4B6E) else Color.Gray
        )
      ) {
        Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      }

      // Center: Record status / title
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = when {
            isRecording -> "Recording..."
            hasRecordedAudio -> "Recorded Voiceover"
            else -> "Record Voiceover"
          },
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold
        )
        val sec = recordDurationMs / 1000
        val millis = (recordDurationMs % 1000) / 100
        Text(
          text = String.format(Locale.US, "%02d:%02d.%d", sec / 60, sec % 60, millis),
          color = if (isRecording) Color(0xFFFF4B6E) else CyanAccent,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium
        )
      }

      // Right: ✓ Confirm Button & ❌ Close Button
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // ✓ Confirm Button
        IconButton(
          onClick = {
            val file = if (isRecording) {
              viewModel.audioEngine.stopVoiceRecording()
            } else {
              recordedFile ?: viewModel.audioEngine.stopVoiceRecording()
            }

            val finalDuration = recordDurationMs.coerceAtLeast(1500L)
            val waveform = viewModel.audioEngine.extractWaveformFromFile(file)

            // Setup effect settings
            val effectSettings = AudioEffectsSettings(
              voiceEffect = activeVoiceEffect?.voiceEffect ?: VoiceEffect.NONE,
              pitchShiftSemitones = activeVoiceEffect?.pitchShift ?: 0f,
              noiseReductionDb = if (voiceEnhanceEnabled) (voiceEnhanceLevel / 100f) * 20f else 0f,
              highGainDb = if (voiceEnhanceEnabled) (voiceEnhanceLevel / 100f) * 4f else 0f,
              normalizeVolume = voiceEnhanceEnabled
            )

            val newClip = AudioClip(
              title = "Voiceover: ${activeVoiceEffect?.name ?: "Clean Voice"}",
              uri = file.absolutePath,
              timelineStartMs = viewModel.timelineEngine.currentPositionMs.value,
              durationMs = finalDuration,
              sourceStartMs = 0L,
              sourceEndMs = finalDuration,
              volume = micVolumeGain,
              waveformData = waveform,
              audioEffects = effectSettings,
              isVoiceOver = true
            )

            viewModel.timelineEngine.addAudioClipObject(newClip)
            Toast.makeText(context, "Voiceover added to timeline!", Toast.LENGTH_SHORT).show()
            onCompleteAndClose()
          },
          enabled = hasRecordedAudio || isRecording,
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (hasRecordedAudio || isRecording) CyanAccent else Color(0xFF1E283E))
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Confirm & Add to Timeline",
            tint = if (hasRecordedAudio || isRecording) Color.Black else Color.Gray,
            modifier = Modifier.size(18.dp)
          )
        }

        // ❌ Close Button
        IconButton(
          onClick = {
            if (isRecording) {
              viewModel.audioEngine.stopVoiceRecording()
            }
            onClose()
          },
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E283E))
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Record",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    // Main Recording Area: clearly display the recording state
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 16.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // Active effect tags if any
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (activeVoiceEffect != null) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF1B2E4B),
              border = BorderStroke(1.dp, CyanAccent)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(activeVoiceEffect.icon, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text(activeVoiceEffect.name, color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }

          if (voiceEnhanceEnabled) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF1C2D24),
              border = BorderStroke(1.dp, Color(0xFF00E676))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Enhanced $voiceEnhanceLevel%", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }

        Spacer(Modifier.height(14.dp))

        // Decibel Waveform Live Visualizer
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 24.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          val barCount = 24
          val normalizedDb = ((currentDecibels + 60f) / 60f).coerceIn(0.1f, 1.0f)
          for (i in 0 until barCount) {
            val variance = (kotlin.math.sin(i * 0.6 + (recordDurationMs / 100.0)).toFloat() * 0.4f).coerceIn(-0.3f, 0.3f)
            val barHeight = if (isRecording) {
              (48.dp * (normalizedDb + variance).coerceIn(0.15f, 1.0f))
            } else {
              4.dp
            }
            Box(
              modifier = Modifier
                .width(4.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(
                  if (isRecording) {
                    if (normalizedDb > 0.8f) Color(0xFFFF4B6E) else CyanAccent
                  } else Color(0xFF26324A)
                )
            )
          }
        }

        Spacer(Modifier.height(16.dp))

        // Big Record Button
        Box(
          modifier = Modifier
            .size(72.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(if (isRecording) Color(0xFFFF4B6E) else CyanAccent)
            .clickable { toggleRecording() },
          contentAlignment = Alignment.Center
        ) {
          if (isRecording) {
            Box(
              modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
            )
          } else {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = "Tap to Record",
              tint = Color.Black,
              modifier = Modifier.size(34.dp)
            )
          }
        }

        Spacer(Modifier.height(10.dp))

        Text(
          text = if (isRecording) "Tap button to stop recording" else "Tap microphone to start recording",
          color = Color.Gray,
          fontSize = 11.sp
        )
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    // Bottom Recording Controls:
    // - Teleprompter + icon
    // - Record With + icon
    // - Voice Changer + icon
    // - Volume 🔊
    // - Voice Enhance + icon
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF101624))
        .padding(horizontal = 8.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // 1. Teleprompter
      RecordBottomActionItem(
        icon = Icons.Default.Subject,
        label = "Teleprompter",
        isActive = showTeleprompterDialog,
        onClick = { showTeleprompterDialog = true }
      )

      // 2. Record With (playback toggle)
      RecordBottomActionItem(
        icon = if (recordWithPlayback) Icons.Default.Headphones else Icons.Default.VolumeOff,
        label = if (recordWithPlayback) "Monitor: ON" else "Record With",
        isActive = recordWithPlayback,
        onClick = {
          recordWithPlayback = !recordWithPlayback
          Toast.makeText(
            context,
            if (recordWithPlayback) "Timeline audio monitor ON during recording" else "Timeline audio MUTED during recording",
            Toast.LENGTH_SHORT
          ).show()
        }
      )

      // 3. Voice Changer
      RecordBottomActionItem(
        icon = Icons.Default.RecordVoiceOver,
        label = "Voice Changer",
        isActive = activeVoiceEffect != null,
        onClick = onOpenVoiceChanger
      )

      // 4. Volume 🔊
      RecordBottomActionItem(
        icon = Icons.Default.VolumeUp,
        label = "${(micVolumeGain * 100).toInt()}% Vol",
        isActive = micVolumeGain != 1.0f,
        onClick = { showVolumeDialog = true }
      )

      // 5. Voice Enhance
      RecordBottomActionItem(
        icon = Icons.Default.AutoAwesome,
        label = "Enhance",
        isActive = voiceEnhanceEnabled,
        onClick = onOpenVoiceEnhance
      )
    }
  }

  // Teleprompter Modal Dialog
  if (showTeleprompterDialog) {
    AlertDialog(
      onDismissRequest = { showTeleprompterDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Subject, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text("Teleprompter Script", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column {
          Text(
            text = "Read your lines while recording voiceover:",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
          )
          OutlinedTextField(
            value = teleprompterScript,
            onValueChange = { teleprompterScript = it },
            modifier = Modifier
              .fillMaxWidth()
              .height(130.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color(0xFF161D2E),
              unfocusedContainerColor = Color(0xFF161D2E),
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = Color(0xFF26324A),
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showTeleprompterDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Done", fontWeight = FontWeight.Bold)
        }
      },
      containerColor = Color(0xFF151C2C)
    )
  }

  // Mic Volume Gain Dialog
  if (showVolumeDialog) {
    AlertDialog(
      onDismissRequest = { showVolumeDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.VolumeUp, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text("Recording Gain Volume", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Adjust voice input boost level: ${(micVolumeGain * 100).toInt()}%",
            color = Color.White,
            fontSize = 13.sp
          )
          Slider(
            value = micVolumeGain,
            onValueChange = { micVolumeGain = it },
            valueRange = 0.2f..2.0f,
            colors = SliderDefaults.colors(
              thumbColor = CyanAccent,
              activeTrackColor = CyanAccent,
              inactiveTrackColor = Color(0xFF26324A)
            )
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("20%", color = Color.Gray, fontSize = 10.sp)
            Text("100% (Normal)", color = Color.Gray, fontSize = 10.sp)
            Text("200% (Boost)", color = Color.Gray, fontSize = 10.sp)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showVolumeDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("OK", fontWeight = FontWeight.Bold)
        }
      },
      containerColor = Color(0xFF151C2C)
    )
  }
}

@Composable
private fun RecordBottomActionItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  isActive: Boolean,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable { onClick() }
      .padding(horizontal = 6.dp, vertical = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(if (isActive) CyanAccent else Color(0xFF1B2336)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isActive) Color.Black else Color.White,
        modifier = Modifier.size(18.dp)
      )
    }
    Spacer(Modifier.height(4.dp))
    Text(
      text = label,
      color = if (isActive) CyanAccent else Color(0xFF8E9BB5),
      fontSize = 9.sp,
      fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
    )
  }
}
