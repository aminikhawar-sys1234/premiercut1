package com.example.ui.components.audio

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StudioDark

@Composable
fun VoiceEnhancePanel(
  initialEnabled: Boolean,
  initialLevel: Int,
  onConfirm: (Boolean, Int) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var isEnabled by remember { mutableStateOf(initialEnabled) }
  var enhanceLevel by remember { mutableStateOf(initialLevel.toFloat()) }
  var selectedPreset by remember { mutableStateOf("Studio Clarity") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDark)
  ) {
    // Professional Header: Title + ✓ Confirm Button + ❌ Close Button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0F1523))
        .padding(horizontal = 14.dp, vertical = 10.dp),
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
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = CyanAccent,
            modifier = Modifier.size(16.dp)
          )
        }
        Text(
          text = "Voice Enhance",
          color = Color.White,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // ✓ Confirm Button
        IconButton(
          onClick = {
            Toast.makeText(
              context,
              if (isEnabled) "Voice Enhancement applied (${enhanceLevel.toInt()}%)" else "Voice Enhancement disabled",
              Toast.LENGTH_SHORT
            ).show()
            onConfirm(isEnabled, enhanceLevel.toInt())
          },
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(CyanAccent)
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Confirm",
            tint = Color.Black,
            modifier = Modifier.size(18.dp)
          )
        }

        // ❌ Close Button
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E283E))
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    // Main Enhance Controls
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Toggle card: ON / OFF
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF151C2C),
        border = BorderStroke(1.dp, if (isEnabled) CyanAccent else Color(0xFF1E283E)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text(
              text = "Voice Enhancement",
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = if (isEnabled) "Active - Noise reduction & vocal clarity" else "Disabled - Raw recorded audio",
              color = if (isEnabled) CyanAccent else Color.Gray,
              fontSize = 11.sp
            )
          }

          Switch(
            checked = isEnabled,
            onCheckedChange = { isEnabled = it },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.Black,
              checkedTrackColor = CyanAccent,
              uncheckedThumbColor = Color.Gray,
              uncheckedTrackColor = Color(0xFF1E283E)
            )
          )
        }
      }

      // Slider & level 0 - 100
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF151C2C),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Enhancement Level",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium
            )
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isEnabled) CyanAccent.copy(alpha = 0.2f) else Color(0xFF1E283E)
            ) {
              Text(
                text = "${enhanceLevel.toInt()}%",
                color = if (isEnabled) CyanAccent else Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }

          Slider(
            value = enhanceLevel,
            onValueChange = {
              enhanceLevel = it
              if (!isEnabled && it > 0) isEnabled = true
            },
            valueRange = 0f..100f,
            enabled = isEnabled,
            colors = SliderDefaults.colors(
              thumbColor = CyanAccent,
              activeTrackColor = CyanAccent,
              inactiveTrackColor = Color(0xFF1E283E),
              disabledThumbColor = Color.Gray,
              disabledActiveTrackColor = Color(0xFF26324A),
              disabledInactiveTrackColor = Color(0xFF1A2234)
            )
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("0% (Natural)", color = Color.Gray, fontSize = 10.sp)
            Text("50% (Recommended)", color = Color.Gray, fontSize = 10.sp)
            Text("100% (Studio Pro)", color = Color.Gray, fontSize = 10.sp)
          }
        }
      }

      // Audio presets
      Text(
        text = "Vocal Tone Presets",
        color = Color(0xFF8E9BB5),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
      )

      val presets = listOf(
        Pair("Studio Clarity", 75f),
        Pair("Podcast Warmth", 60f),
        Pair("Aggressive De-Noise", 90f),
        Pair("Subtle Clean", 40f)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        presets.forEach { (name, lvl) ->
          val isSelected = selectedPreset == name && isEnabled
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) CyanAccent else Color(0xFF1A2234),
            modifier = Modifier
              .weight(1f)
              .clickable {
                selectedPreset = name
                enhanceLevel = lvl
                isEnabled = true
              }
          ) {
            Column(
              modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = name,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
              )
              Text(
                text = "${lvl.toInt()}%",
                color = if (isSelected) Color.Black.copy(alpha = 0.7f) else Color.Gray,
                fontSize = 9.sp
              )
            }
          }
        }
      }
    }
  }
}
