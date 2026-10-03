package com.example.ui.components.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ClipSpeedDialog(
  currentSpeed: Float,
  onDismiss: () -> Unit,
  onConfirm: (Float) -> Unit
) {
  var selectedSpeed by remember { mutableFloatStateOf(currentSpeed) }
  val presetSpeeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 4.0f)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Adjust Clip Speed",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
          text = "Speed: ${String.format("%.2f", selectedSpeed)}x",
          style = MaterialTheme.typography.bodyLarge.copy(color = CyanAccent, fontWeight = FontWeight.ExtraBold),
          modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Slider(
          value = selectedSpeed,
          onValueChange = { selectedSpeed = it },
          valueRange = 0.2f..4.0f,
          colors = SliderDefaults.colors(
            thumbColor = CyanAccent,
            activeTrackColor = CyanAccent,
            inactiveTrackColor = StudioBorder
          ),
          modifier = Modifier.testTag("clip_speed_slider")
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          presetSpeeds.forEach { speed ->
            OutlinedButton(
              onClick = { selectedSpeed = speed },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedSpeed == speed) CyanAccent.copy(alpha = 0.2f) else Color.Transparent
              ),
              modifier = Modifier.height(30.dp)
            ) {
              Text(
                text = "${speed}x",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = if (selectedSpeed == speed) CyanAccent else TextSecondary)
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(selectedSpeed) },
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
        modifier = Modifier.testTag("clip_speed_confirm")
      ) {
        Text("Apply", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = TextSecondary)
      }
    },
    containerColor = StudioSurface,
    shape = RoundedCornerShape(16.dp)
  )
}
