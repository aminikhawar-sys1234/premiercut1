package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.ui.theme.*

data class UrduTutorialItem(
  val id: String,
  val titleUrdu: String,
  val titleEnglish: String,
  val duration: String,
  val category: String,
  val isPopular: Boolean = false
)

@Composable
fun UrduTutorialsDialog(
  onDismiss: () -> Unit = {}
) {
  val tutorials = listOf(
    UrduTutorialItem("t1", "اردو فونٹس اور نستعلیق کیلی گرافی کا استعمال", "How to use Urdu Fonts & Nastaliq Calligraphy", "4 min", "Typography", true),
    UrduTutorialItem("t2", "آٹو کیپشنز اور اردو سب ٹائٹلز بنائیں", "Auto Captions & Urdu Subtitles Tutorial", "3 min", "AI Captions", true),
    UrduTutorialItem("t3", "بیک گراؤنڈ ریمول اور ڈبل ایکسپوژر ایفیکٹ", "AI Background Removal & Double Exposure", "5 min", "AI Effects"),
    UrduTutorialItem("t4", "سلو موشن اور اسپیڈ ریمپ ریلز ایڈیٹنگ", "Smooth Slow-Mo & Speed Ramp Reels", "6 min", "Speed Ramp", true),
    UrduTutorialItem("t5", "فور کے کسٹم ایکسپورٹ اور کلر گریڈنگ", "4K Custom Export & Cinematic Color Grade", "4 min", "Color Grade")
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = StudioPrimary, fontWeight = FontWeight.Bold)
      }
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.School, contentDescription = null, tint = StudioPrimary)
        Column {
          Text("اردو ویڈیو ایڈیٹنگ ٹیوٹوریلز", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
          Text("Urdu Video Editing Masterclass", fontSize = 12.sp, color = TextSecondary)
        }
      }
    },
    text = {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 300.dp)
      ) {
        items(tutorials, key = { it.id }) { item ->
          Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(item.titleUrdu, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Surface(
                  color = StudioPrimary.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(item.duration, fontSize = 10.sp, color = StudioPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                }
              }
              Text(item.titleEnglish, fontSize = 11.sp, color = TextSecondary)
            }
          }
        }
      }
    },
    containerColor = StudioSurface,
    shape = RoundedCornerShape(16.dp)
  )
}
