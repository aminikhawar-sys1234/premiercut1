package com.example.ui.components.audio

import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudioViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StudioDark
import com.example.ui.theme.StudioSurface
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun TextToAudioFullPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  onCompleteAndClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

  var inputText by remember {
    mutableStateOf("Welcome to this video! Today we explore amazing stories and creative effects.")
  }

  // Active voice selection tab: "Voice Over" vs "AI Voices"
  var mainCategoryTab by remember { mutableStateOf("Voice Over") }
  // Sub-category under Voice Over: "All", "Urdu", "English", "Characters", "Other Languages"
  var subCategory by remember { mutableStateOf("All") }

  var selectedVoice by remember { mutableStateOf(TTSVoicesCatalog.VOICES.first()) }
  var pitchVal by remember { mutableStateOf(selectedVoice.pitch) }
  var speedVal by remember { mutableStateOf(selectedVoice.speed) }
  var isGenerating by remember { mutableStateOf(false) }

  // TTS Engine instance
  var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
  var ttsReady by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    var tts: TextToSpeech? = null
    tts = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        ttsEngine = tts
        ttsReady = true
      }
    }
    onDispose {
      tts?.stop()
      tts?.shutdown()
    }
  }

  val filteredVoices = remember(mainCategoryTab, subCategory) {
    if (mainCategoryTab == "AI Voices") {
      TTSVoicesCatalog.VOICES.filter { it.isAi }
    } else {
      val base = TTSVoicesCatalog.VOICES.filter { !it.isAi }
      when (subCategory) {
        "Urdu" -> base.filter { it.id.startsWith("ur_") }
        "English" -> base.filter { it.id.startsWith("en_us_") || it.id.startsWith("en_uk_") || it.id.startsWith("en_au_") }
        "Characters" -> base.filter { it.id.contains("char") }
        "Other Languages" -> base.filter { it.id.startsWith("ar_") || it.id.startsWith("hi_") || it.id.startsWith("es_") }
        else -> base
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDark)
  ) {
    // Header: Title + At the top-right: ❌ Close button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0F1523))
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E283E)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.RecordVoiceOver,
            contentDescription = null,
            tint = CyanAccent,
            modifier = Modifier.size(16.dp)
          )
        }
        Text(
          text = "Text to Audio / Voice",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold
        )
      }

      // Top-Right ❌ Close Button
      IconButton(
        onClick = {
          ttsEngine?.stop()
          onClose()
        },
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E283E))
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Text to Audio",
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Clear heading: Enter Text
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Enter Text",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )

          // Paste & Clear buttons
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
              onClick = {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                  val pasted = clip.getItemAt(0).text?.toString() ?: ""
                  if (pasted.isNotBlank()) {
                    inputText = if (inputText.isBlank()) pasted else "$inputText\n$pasted"
                    Toast.makeText(context, "Pasted text from clipboard", Toast.LENGTH_SHORT).show()
                  }
                } else {
                  Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
              },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = CyanAccent, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("Paste", color = CyanAccent, fontSize = 11.sp)
            }

            TextButton(
              onClick = { inputText = "" },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("Clear", color = Color.Gray, fontSize = 11.sp)
            }
          }
        }
      }

      // Professional Text Input Box (supports multiline, editing, long text)
      item {
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = { Text("Type or paste script narration here...", color = Color.Gray, fontSize = 13.sp) },
          minLines = 3,
          maxLines = 6,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF151C2C),
            unfocusedContainerColor = Color(0xFF151C2C),
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = Color(0xFF1E283E),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, end = 4.dp),
          horizontalArrangement = Arrangement.End
        ) {
          val words = if (inputText.isBlank()) 0 else inputText.trim().split(Regex("\\s+")).size
          Text(
            text = "$words words • ${inputText.length} chars",
            color = Color(0xFF7E8EA8),
            fontSize = 10.sp
          )
        }
      }

      // Voice Selection Category Tabs: "Voice Over" & "AI Voices"
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF151C2C))
            .padding(3.dp)
        ) {
          listOf("Voice Over", "AI Voices").forEach { tab ->
            val isSelected = mainCategoryTab == tab
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) CyanAccent else Color.Transparent)
                .clickable { mainCategoryTab = tab }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = tab,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
              )
            }
          }
        }
      }

      // Sub-category row for Voice Over
      if (mainCategoryTab == "Voice Over") {
        item {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            items(listOf("All", "Urdu", "English", "Characters", "Other Languages")) { cat ->
              val isSelected = subCategory == cat
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) Color(0xFF1B2E4B) else Color(0xFF151C2C),
                border = BorderStroke(1.dp, if (isSelected) CyanAccent else Color(0xFF1E283E)),
                modifier = Modifier.clickable { subCategory = cat }
              ) {
                Text(
                  text = cat,
                  color = if (isSelected) CyanAccent else Color(0xFFC4CBD8),
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
              }
            }
          }
        }
      }

      // Voice list options
      items(filteredVoices, key = { it.id }) { voice ->
        val isSelected = selectedVoice.id == voice.id
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isSelected) Color(0xFF1A263C) else Color(0xFF151C2C),
          border = BorderStroke(1.dp, if (isSelected) CyanAccent else Color(0xFF1E283E)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              selectedVoice = voice
              pitchVal = voice.pitch
              speedVal = voice.speed
            }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Icon / Flag / Avatar
            Text(text = voice.iconEmoji, fontSize = 22.sp)

            // Name & Subtitle
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = voice.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = voice.subtitle,
                color = if (isSelected) CyanAccent else Color(0xFF7E8EA8),
                fontSize = 10.sp
              )
            }

            // Preview speech button
            IconButton(
              onClick = {
                val previewSample = if (inputText.isNotBlank()) {
                  inputText.take(120)
                } else {
                  "Hello, this is a sample preview of this voice."
                }
                ttsEngine?.let { engine ->
                  engine.language = voice.locale
                  engine.setPitch(pitchVal)
                  engine.setSpeechRate(speedVal)
                  engine.speak(previewSample, TextToSpeech.QUEUE_FLUSH, null, "preview_${voice.id}")
                } ?: Toast.makeText(context, "Initializing speech engine...", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E283E))
            ) {
              Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = CyanAccent, modifier = Modifier.size(16.dp))
            }
          }
        }
      }

      // Pitch & Speed Controls
      item {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF151C2C),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Voice Pitch", color = Color.White, fontSize = 11.sp)
              Text(String.format(Locale.US, "%.2fx", pitchVal), color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
              value = pitchVal,
              onValueChange = { pitchVal = it },
              valueRange = 0.5f..1.8f,
              colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Speech Speed", color = Color.White, fontSize = 11.sp)
              Text(String.format(Locale.US, "%.2fx", speedVal), color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
              value = speedVal,
              onValueChange = { speedVal = it },
              valueRange = 0.5f..2.0f,
              colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
            )
          }
        }
      }

      // Generate Audio Button
      item {
        Button(
          onClick = {
            if (inputText.isBlank()) {
              Toast.makeText(context, "Please enter some text first", Toast.LENGTH_SHORT).show()
              return@Button
            }

            isGenerating = true
            coroutineScope.launch {
              try {
                val wordCount = inputText.trim().split(Regex("\\s+")).size.coerceAtLeast(1)
                val estimatedDurationMs = ((wordCount / (2.5f * speedVal)) * 1000L).toLong().coerceIn(2000L, 60000L)

                val outputFile = File(context.filesDir, "tts_voice_${System.currentTimeMillis()}.wav")
                ttsEngine?.let { engine ->
                  engine.language = selectedVoice.locale
                  engine.setPitch(pitchVal)
                  engine.setSpeechRate(speedVal)
                  engine.synthesizeToFile(inputText, null, outputFile, "tts_${System.currentTimeMillis()}")
                }

                // Generate waveform
                val waveform = com.example.engine.audio.SoundEffectsCatalog.generateWaveform(selectedVoice.name, 36)

                viewModel.timelineEngine.addAudioClip(
                  title = "Voice: ${selectedVoice.name}",
                  durationMs = estimatedDurationMs,
                  uri = outputFile.absolutePath,
                  waveformData = waveform
                )

                Toast.makeText(context, "Generated audio added to timeline track!", Toast.LENGTH_SHORT).show()
                onCompleteAndClose()
              } catch (e: Exception) {
                Toast.makeText(context, "Generation error: ${e.message}", Toast.LENGTH_SHORT).show()
              } finally {
                isGenerating = false
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = CyanAccent,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(23.dp)
        ) {
          if (isGenerating) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Generating Speech Audio...", fontWeight = FontWeight.Bold)
          } else {
            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Generate Audio & Add to Timeline", fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
