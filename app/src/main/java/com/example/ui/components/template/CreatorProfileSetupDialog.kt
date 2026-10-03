package com.example.ui.components.template

import android.widget.Toast
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
import androidx.compose.ui.window.Dialog
import com.example.data.firebase.CreatorProfile
import com.example.data.firebase.FirebaseTemplateManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorProfileSetupDialog(
  onDismiss: () -> Unit,
  onProfileCompleted: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val existingProfile by FirebaseTemplateManager.creatorProfile.collectAsState()

  var displayName by remember { mutableStateOf(existingProfile.displayName.ifBlank { "Viral Creator" }) }
  var handle by remember { mutableStateOf(existingProfile.handle.ifBlank { "@creator" }.removePrefix("@")) }
  var bio by remember {
    mutableStateOf(
      existingProfile.bio.ifBlank { "Creating fast-paced beat sync and aesthetic video templates." }
    )
  }
  var selectedCategory by remember { mutableStateOf(existingProfile.category) }
  var tikTokHandle by remember { mutableStateOf(existingProfile.tikTokHandle ?: "@tiktok_creator") }
  var isSaving by remember { mutableStateOf(false) }

  val categories = listOf(
    "Reels & TikTok",
    "Cinematic & Travel",
    "Beat Sync & Motion",
    "YouTube Shorts",
    "Business & Ads",
    "Lifestyle & Vlogs"
  )

  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp)
        .testTag("creator_profile_setup_dialog"),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = StudioSurface),
      border = CardDefaults.outlinedCardBorder().copy(
        brush = Brush.linearGradient(listOf(CyanAccent, PurpleAccent))
      )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(CyanAccent, PurpleAccent))),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Stars, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Template Creator Profile",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
              )
              Text(
                text = "Complete profile to design & publish templates",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
              )
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
          }
        }

        HorizontalDivider(color = StudioBorder)

        // Creator Avatar Preview Badge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioSurfaceVariant)
            .padding(14.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFE2C55), Color(0xFF25F4EE)))),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = displayName.take(1).uppercase().ifBlank { "C" },
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                color = Color.White
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = displayName.ifBlank { "Your Name" },
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              fontSize = 15.sp
            )
            Text(
              text = "@${handle.removePrefix("@").ifBlank { "handle" }} • Verified Creator",
              color = CyanAccent,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        // Creator Name Field
        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text("Creator Display Name *") },
          leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = CyanAccent) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("creator_name_input"),
          shape = RoundedCornerShape(12.dp)
        )

        // Creator Handle Field
        OutlinedTextField(
          value = handle,
          onValueChange = { handle = it.replace(" ", "").lowercase() },
          label = { Text("Creator Handle (e.g. alex_edits) *") },
          prefix = { Text("@", color = CyanAccent, fontWeight = FontWeight.Bold) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("creator_handle_input"),
          shape = RoundedCornerShape(12.dp)
        )

        // Category Selection
        Column {
          Text(
            text = "Primary Niche / Category",
            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
          )
          Spacer(modifier = Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
              val isSelected = selectedCategory == cat
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSelected) CyanAccent else StudioSurfaceVariant)
                  .border(1.dp, if (isSelected) CyanAccent else StudioBorder, RoundedCornerShape(10.dp))
                  .clickable { selectedCategory = cat }
                  .padding(horizontal = 12.dp, vertical = 7.dp)
              ) {
                Text(
                  text = cat,
                  color = if (isSelected) Color.Black else TextPrimary,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 12.sp
                )
              }
            }
          }
        }

        // Bio / Style
        OutlinedTextField(
          value = bio,
          onValueChange = { bio = it },
          label = { Text("Bio / Editing Style") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp)
        )

        // TikTok Profile Handle
        OutlinedTextField(
          value = tikTokHandle,
          onValueChange = { tikTokHandle = it },
          label = { Text("TikTok Handle (for Direct Sharing & Credits)") },
          leadingIcon = {
            Text("⚡", fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Confirm Action Button
        Button(
          onClick = {
            if (displayName.isBlank() || handle.isBlank()) {
              Toast.makeText(context, "Please enter your Creator Name and Handle", Toast.LENGTH_SHORT).show()
              return@Button
            }

            isSaving = true
            coroutineScope.launch {
              val updated = existingProfile.copy(
                displayName = displayName.trim(),
                handle = "@${handle.removePrefix("@").trim()}",
                bio = bio.trim(),
                category = selectedCategory,
                tikTokHandle = tikTokHandle.trim(),
                isProfileComplete = true
              )
              FirebaseTemplateManager.saveCreatorProfile(updated)
              isSaving = false
              Toast.makeText(context, "Creator Profile Activated! 🚀", Toast.LENGTH_SHORT).show()
              onProfileCompleted()
              onDismiss()
            }
          },
          enabled = !isSaving && displayName.isNotBlank() && handle.isNotBlank(),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("save_creator_profile_btn"),
          colors = ButtonDefaults.buttonColors(
            containerColor = CyanAccent,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(14.dp)
        ) {
          if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
          } else {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Profile & Launch Template Creator", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
        }
      }
    }
  }
}
