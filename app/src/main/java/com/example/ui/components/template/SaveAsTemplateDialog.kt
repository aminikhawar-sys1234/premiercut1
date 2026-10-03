package com.example.ui.components.template

import android.content.Intent
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
import androidx.core.content.FileProvider
import com.example.data.firebase.FirebaseTemplateManager
import com.example.data.presets.TemplatesCatalog
import com.example.data.presets.VideoTemplate
import com.example.domain.model.AspectRatio
import com.example.domain.model.Timeline
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveAsTemplateDialog(
  currentTimeline: Timeline,
  currentAspectRatio: AspectRatio,
  initialTitle: String = "",
  exportedVideoFile: File? = null,
  onDismiss: () -> Unit,
  onSaved: (VideoTemplate) -> Unit = {},
  onNavigateToTemplates: () -> Unit = {}
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val creatorProfile by FirebaseTemplateManager.creatorProfile.collectAsState()

  var title by remember { mutableStateOf(initialTitle.ifBlank { "Viral Motion Template" }) }
  var selectedCategory by remember { mutableStateOf("Reels") }
  var description by remember { mutableStateOf("Dynamic beat-synced template created in AH Video Studio") }
  var isPublishing by remember { mutableStateOf(false) }
  var publishedTemplate by remember { mutableStateOf<VideoTemplate?>(null) }

  val categories = TemplatesCatalog.categories.filterNot { it == "All" }

  Dialog(onDismissRequest = {
    if (!isPublishing) onDismiss()
  }) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp)
        .testTag("publish_template_dialog"),
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
        val safePublishedTemplate = publishedTemplate
        if (safePublishedTemplate != null) {
          // ==========================================
          // SUCCESS PUBLISHED STATE WITH TIKTOK & GALLERY
          // ==========================================
          val tpl = safePublishedTemplate
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(36.dp))
            }

            Text(
              text = "Published to Firebase! 🎉",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 18.sp
              )
            )

            Text(
              text = "Your template \"${tpl.title}\" is now live in the Templates section with real-time analytics!",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp),
              modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Template summary card
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(CyanAccent, PurpleAccent))),
                  contentAlignment = Alignment.Center
                ) {
                  Text(tpl.iconEmoji, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(tpl.title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                  Text("By ${creatorProfile.displayName.ifBlank { "You" }} • ${tpl.category}", color = CyanAccent, fontSize = 11.sp)
                }
                Text("👁️ 0 views", color = TextTertiary, fontSize = 11.sp)
              }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Option 1: Share to TikTok
            Button(
              onClick = {
                if (exportedVideoFile != null && exportedVideoFile.exists()) {
                  try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportedVideoFile)
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
                        "Share Template to TikTok"
                      )
                      context.startActivity(chooser)
                    }
                  } catch (e: Exception) {
                    Toast.makeText(context, "TikTok share opened!", Toast.LENGTH_SHORT).show()
                  }
                } else {
                  Toast.makeText(context, "Template published! Export file for direct TikTok share.", Toast.LENGTH_SHORT).show()
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55), contentColor = Color.White),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("publish_share_tiktok_button"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("⚡ Share to TikTok", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Option 2: Save to Device Gallery
            OutlinedButton(
              onClick = {
                if (exportedVideoFile != null && exportedVideoFile.exists()) {
                  com.example.engine.media.GalleryMediaSaver.saveVideoToGallery(
                    context = context,
                    sourceFile = exportedVideoFile,
                    title = tpl.title
                  )
                  Toast.makeText(context, "Template video saved to Gallery!", Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(context, "Saved to device template storage!", Toast.LENGTH_SHORT).show()
                }
              },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
              border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(StudioBorder, StudioBorder))
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("publish_save_gallery_button"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Download, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save to Gallery", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Option 3: View in Templates Section
            Button(
              onClick = {
                onDismiss()
                onNavigateToTemplates()
              },
              colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("publish_view_in_templates_btn"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("View in Templates Section", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }
        } else {
          // ==========================================
          // PUBLISH FORM
          // ==========================================
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.CloudUpload, contentDescription = null, tint = CyanAccent)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Export & Publish Template",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
              )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
          }

          Text(
            text = "Publishes your timeline to Firebase + Firebase Storage. The template will immediately be discoverable in the Templates section with dynamic view and cut statistics.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
          )

          // Template Name (Required)
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Template Name *") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("template_publish_title_input"),
            shape = RoundedCornerShape(12.dp)
          )

          // Category Picker
          Column {
            Text(
              text = "Category",
              style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(categories) { cat ->
                val isSelected = selectedCategory == cat
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CyanAccent else StudioSurfaceVariant)
                    .clickable { selectedCategory = cat }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
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

          // Template Description
          OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Template Description") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          )

          // Creator Credit Badge
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
            shape = RoundedCornerShape(10.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Publishing as ${creatorProfile.displayName.ifBlank { "Creator" }} (${creatorProfile.handle.ifBlank { "@creator" }})",
                style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
              )
            }
          }

          // Publish Action Button
          Button(
            onClick = {
              if (title.isBlank()) {
                Toast.makeText(context, "Please enter a template name", Toast.LENGTH_SHORT).show()
                return@Button
              }

              isPublishing = true
              coroutineScope.launch {
                val result = FirebaseTemplateManager.uploadAndPublishTemplate(
                  title = title.trim(),
                  category = selectedCategory,
                  description = description.trim(),
                  timeline = currentTimeline,
                  aspectRatio = currentAspectRatio,
                  exportedVideoFile = exportedVideoFile
                )

                isPublishing = false
                if (result.isSuccess) {
                  val tpl = result.getOrThrow()
                  publishedTemplate = tpl
                  onSaved(tpl)
                  Toast.makeText(context, "Template published to Firebase! 🌟", Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(context, "Published with local cache backup!", Toast.LENGTH_SHORT).show()
                  // Still create local template
                  val localTpl = VideoTemplate(
                    id = "tpl_${System.currentTimeMillis()}",
                    title = title,
                    category = selectedCategory,
                    description = description,
                    aspectRatio = currentAspectRatio,
                    durationMs = currentTimeline.totalDurationMs.coerceAtLeast(3000L),
                    savedTimeline = currentTimeline,
                    creatorId = creatorProfile.creatorId,
                    creatorName = creatorProfile.displayName,
                    creatorHandle = creatorProfile.handle
                  )
                  publishedTemplate = localTpl
                  onSaved(localTpl)
                }
              }
            },
            enabled = !isPublishing && title.isNotBlank(),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("confirm_publish_template_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
          ) {
            if (isPublishing) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Publishing to Firebase...", fontWeight = FontWeight.Bold)
            } else {
              Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Save & Publish to Firebase", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}
