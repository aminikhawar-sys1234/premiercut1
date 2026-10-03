package com.example.ui.components.filter

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.FilterType
import com.example.engine.composition.ColorFilterGenerator
import com.example.engine.media.VideoThumbnailManager
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.sin

/**
 * Studio Animated Video Filter Preview Card.
 *
 * Features:
 * - 2.5-second muted video preview that autoplays and loops continuously.
 * - Real-time color filter transform applied to the animated video frames.
 * - Visible-only playback: automatically pauses when scrolled off-screen.
 * - Purple border + checkmark badge for selected state.
 * - Clear filter title underneath the preview.
 * - Immediate filter application on tap.
 */
@Composable
fun StudioFilterPreviewCard(
  type: FilterType,
  isSelected: Boolean,
  isVisible: Boolean,
  videoUri: String,
  sourceStartMs: Long = 0L,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  // Filter color matrix array (20 floats)
  val matrixArray = remember(type) {
    ColorFilterGenerator.getFilterMatrixArray(type, 1.0f)
  }
  val composeColorFilter = remember(matrixArray, type) {
    if (type == FilterType.NONE) null
    else ColorFilter.colorMatrix(ColorMatrix(matrixArray))
  }

  // 2.5-second continuous looping video timeline (only animates when visible)
  val loopProgress = if (isVisible) {
    val infiniteTransition = rememberInfiniteTransition(label = "FilterVideoLoop_${type.name}")
    val progress by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2400, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "loopProgress"
    )
    progress
  } else {
    0f
  }

  // Frame timestamps across the 2.4s preview window (8 keyframes)
  val frameCount = 8
  val frameIndex = (loopProgress * frameCount).toInt().coerceIn(0, frameCount - 1)
  val sampleTimeMs = remember(sourceStartMs, frameIndex) {
    sourceStartMs + (frameIndex * 300L)
  }

  // Asynchronously load/cache thumbnail bitmap for current frame time
  var currentBitmap by remember(videoUri, sampleTimeMs) {
    val key = VideoThumbnailManager.makeKey(videoUri, sampleTimeMs, 140, 140)
    mutableStateOf(VideoThumbnailManager.getCachedThumbnail(key))
  }

  LaunchedEffect(videoUri, sampleTimeMs, isVisible) {
    if (isVisible && currentBitmap == null) {
      VideoThumbnailManager.requestThumbnail(
        context = context,
        uri = videoUri,
        sourceTimeMs = sampleTimeMs,
        targetWidth = 140,
        targetHeight = 140,
        isVideo = true
      ) { bmp ->
        currentBitmap = bmp
      }
    }
  }

  // Card Container
  Card(
    modifier = modifier
      .width(104.dp)
      .height(132.dp)
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag("filter_preset_${type.name.lowercase()}"),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) Color(0xFF261E38) else Color(0xFF161B26)
    ),
    border = BorderStroke(
      width = if (isSelected) 2.5.dp else 1.dp,
      color = if (isSelected) PurpleAccent else StudioBorder.copy(alpha = 0.6f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(5.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 1. Animated Video Preview Box (2.5s looping with live color matrix)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(90.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFF0F131A)),
        contentAlignment = Alignment.Center
      ) {
        val bmp = currentBitmap
        if (bmp != null && !bmp.isRecycled) {
          // Live Video Frame with Filter Effect & Subtle Ken-Burns Pan
          val panScale = 1.0f + (sin(loopProgress * Math.PI.toFloat()) * 0.08f)
          val panOffsetX = sin(loopProgress * 2 * Math.PI.toFloat()) * 4f

          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "${type.displayName} Preview",
            colorFilter = composeColorFilter,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                scaleX = panScale
                scaleY = panScale
                translationX = panOffsetX
              }
          )
        } else {
          // Procedural Cinematic Motion Scene with Filter Applied
          CinematicMotionCanvas(
            type = type,
            loopProgress = loopProgress,
            colorFilter = composeColorFilter,
            modifier = Modifier.fillMaxSize()
          )
        }

        // Top Gradient Shadow & "LIVE" badge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .align(Alignment.TopCenter)
            .background(
              Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
              )
            )
        )

        // Micro Preview Duration Pill in bottom-left
        Row(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (isVisible) PurpleAccent else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(8.dp)
          )
          Text(
            text = "2.4s",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Medium
          )
        }

        // Selected State Checkmark Badge in top-right
        if (isSelected) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(4.dp)
              .size(20.dp)
              .clip(CircleShape)
              .background(PurpleAccent),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Selected",
              tint = Color.White,
              modifier = Modifier.size(13.dp)
            )
          }

          // Subtle purple selection tint overlay
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(PurpleAccent.copy(alpha = 0.12f))
          )
        }
      }

      // 2. Filter Title Label
      Text(
        text = type.displayName,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          color = if (isSelected) PurpleAccent else TextPrimary,
          fontSize = 11.sp
        ),
        maxLines = 1,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 2.dp, vertical = 3.dp)
      )
    }
  }
}

/**
 * Renders a procedural cinematic animated video scene with camera motion,
 * landscape geometry, water dynamics, and live filter matrix.
 */
@Composable
private fun CinematicMotionCanvas(
  type: FilterType,
  loopProgress: Float,
  colorFilter: ColorFilter?,
  modifier: Modifier = Modifier
) {
  val baseColors = remember(type) {
    when (type) {
      FilterType.NONE -> listOf(Color(0xFF334155), Color(0xFF0F172A))
      FilterType.FOUR_K -> listOf(Color(0xFF0284C7), Color(0xFF1E3A8A), Color(0xFF0F172A))
      FilterType.BLACKLIGHT_FIX -> listOf(Color(0xFFD97706), Color(0xFFB45309), Color(0xFF1E1B4B))
      FilterType.ENHANCE -> listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFF312E81))
      FilterType.HDR -> listOf(Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFF0284C7))
      FilterType.GLOW -> listOf(Color(0xFFFDE047), Color(0xFFF472B6), Color(0xFF4F46E5))
      FilterType.FOCUS -> listOf(Color(0xFF10B981), Color(0xFF0369A1), Color(0xFF0F172A))
      FilterType.QUALITY_RESTORATION -> listOf(Color(0xFF2DD4BF), Color(0xFF2563EB), Color(0xFF0F172A))
      FilterType.GOLDEN_AUTUMN -> listOf(Color(0xFFF97316), Color(0xFF9A3412), Color(0xFF451A03))
      FilterType.OCEANIC_VIEW -> listOf(Color(0xFF06B6D4), Color(0xFF0369A1), Color(0xFF082F49))
      FilterType.ALMOND -> listOf(Color(0xFFFDE68A), Color(0xFFD4A373), Color(0xFF78350F))
      FilterType.SUNLIGHT_ORANGE_BLUE -> listOf(Color(0xFFFB923C), Color(0xFF0284C7), Color(0xFF0F172A))
      FilterType.CINEMATIC -> listOf(Color(0xFF0D9488), Color(0xFFF97316), Color(0xFF042F2E))
      FilterType.WARM -> listOf(Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFF451A03))
      FilterType.COOL -> listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF082F49))
      FilterType.PORTRAIT -> listOf(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFF881337))
      FilterType.BLACK_AND_WHITE -> listOf(Color(0xFFE2E8F0), Color(0xFF64748B), Color(0xFF0F172A))
      FilterType.VINTAGE -> listOf(Color(0xFFB45309), Color(0xFFFDE68A), Color(0xFF451A03))
      FilterType.SATURATION -> listOf(Color(0xFFEC4899), Color(0xFF3B82F6), Color(0xFF1E1B4B))
      FilterType.FILM -> listOf(Color(0xFFA16207), Color(0xFF78350F), Color(0xFF1C1917))
      FilterType.RETRO -> listOf(Color(0xFFD946EF), Color(0xFF8B5CF6), Color(0xFF3B0764))
      FilterType.NATURE -> listOf(Color(0xFF10B981), Color(0xFF047857), Color(0xFF064E3B))
      FilterType.FOOD -> listOf(Color(0xFFEA580C), Color(0xFFFACC15), Color(0xFF7C2D12))
      FilterType.TRAVEL -> listOf(Color(0xFF0284C7), Color(0xFF10B981), Color(0xFF064E3B))
      FilterType.SOCIAL_MEDIA -> listOf(Color(0xFFFF007F), Color(0xFF8B5CF6), Color(0xFF312E81))
    }
  }

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val wavePhase = loopProgress * 2 * Math.PI.toFloat()

    // 1. Sky & Atmosphere
    drawRect(
      brush = Brush.verticalGradient(baseColors),
      size = size
    )

    // 2. Animated Sun / Celestial Glow moving slowly
    val sunX = w * (0.68f + sin(wavePhase * 0.5f) * 0.08f)
    val sunY = h * (0.32f + sin(wavePhase * 0.5f) * 0.05f)
    drawCircle(
      color = Color(0xFFFFFAEB).copy(alpha = 0.85f),
      radius = h * 0.20f,
      center = Offset(sunX, sunY)
    )

    // 3. Mountains / Horizon Silhouettes
    val mtnPath = androidx.compose.ui.graphics.Path().apply {
      moveTo(0f, h * 0.62f)
      lineTo(w * 0.32f, h * 0.38f)
      lineTo(w * 0.65f, h * 0.52f)
      lineTo(w, h * 0.40f)
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
    drawPath(path = mtnPath, color = Color(0xFF0B101B).copy(alpha = 0.65f))

    // 4. Foreground Moving Water / Dynamic Wave
    val wavePath = androidx.compose.ui.graphics.Path().apply {
      moveTo(0f, h * 0.70f)
      var cx = 0f
      while (cx <= w) {
        val cy = h * 0.73f + (sin((cx / w) * 5f + wavePhase) * (h * 0.07f))
        lineTo(cx, cy)
        cx += 4f
      }
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
    drawPath(path = wavePath, color = Color(0xFF0284C7).copy(alpha = 0.55f))

    // 5. Film / Camera Scanline sweep
    val scanX = w * ((loopProgress * 1.4f) - 0.2f)
    if (scanX in 0f..w) {
      drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(scanX, 0f),
        end = Offset(scanX + 15f, h),
        strokeWidth = 2.dp.toPx()
      )
    }
  }
}

/**
 * Studio Animated Preview Card for Third-Party Installed Plugin Filters.
 */
@Composable
fun StudioPluginFilterPreviewCard(
  item: com.example.domain.plugin.PluginItemManifest,
  plugin: com.example.domain.plugin.InstalledPlugin,
  isSelected: Boolean,
  isVisible: Boolean,
  videoUri: String,
  sourceStartMs: Long = 0L,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  // Compute 20-float color matrix for the plugin filter
  val matrixArray = remember(item) {
    com.example.engine.composition.ColorFilterGenerator.getPluginFilterMatrixArray(item, 1.0f)
  }
  val composeColorFilter = remember(matrixArray) {
    ColorFilter.colorMatrix(ColorMatrix(matrixArray))
  }

  // 2.5-second continuous looping video timeline
  val loopProgress = if (isVisible) {
    val infiniteTransition = rememberInfiniteTransition(label = "PluginFilterLoop_${item.id}")
    val progress by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2400, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "loopProgress"
    )
    progress
  } else {
    0f
  }

  val frameCount = 8
  val frameIndex = (loopProgress * frameCount).toInt().coerceIn(0, frameCount - 1)
  val sampleTimeMs = remember(sourceStartMs, frameIndex) {
    sourceStartMs + (frameIndex * 300L)
  }

  var currentBitmap by remember(videoUri, sampleTimeMs) {
    val key = VideoThumbnailManager.makeKey(videoUri, sampleTimeMs, 140, 140)
    mutableStateOf(VideoThumbnailManager.getCachedThumbnail(key))
  }

  LaunchedEffect(videoUri, sampleTimeMs, isVisible) {
    if (isVisible && currentBitmap == null) {
      VideoThumbnailManager.requestThumbnail(
        context = context,
        uri = videoUri,
        sourceTimeMs = sampleTimeMs,
        targetWidth = 140,
        targetHeight = 140,
        isVideo = true
      ) { bmp ->
        currentBitmap = bmp
      }
    }
  }

  Card(
    modifier = modifier
      .width(108.dp)
      .height(138.dp)
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag("plugin_filter_${item.id}"),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) Color(0xFF261E38) else Color(0xFF161B26)
    ),
    border = BorderStroke(
      width = if (isSelected) 2.5.dp else 1.dp,
      color = if (isSelected) PurpleAccent else StudioBorder.copy(alpha = 0.6f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(5.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 1. Animated Video Preview Box with Live Plugin Filter Applied
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(90.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFF0F131A)),
        contentAlignment = Alignment.Center
      ) {
        val bmp = currentBitmap
        if (bmp != null && !bmp.isRecycled) {
          val panScale = 1.0f + (sin(loopProgress * Math.PI.toFloat()) * 0.08f)
          val panOffsetX = sin(loopProgress * 2 * Math.PI.toFloat()) * 4f

          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "${item.name} Preview",
            colorFilter = composeColorFilter,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                scaleX = panScale
                scaleY = panScale
                translationX = panOffsetX
              }
          )
        } else {
          CinematicMotionCanvas(
            type = FilterType.CINEMATIC,
            loopProgress = loopProgress,
            colorFilter = composeColorFilter,
            modifier = Modifier.fillMaxSize()
          )
        }

        // Top Gradient & Emoji badge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .align(Alignment.TopCenter)
            .background(
              Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
              )
            )
        )

        // Plugin Emoji / Pack icon in top-left
        Text(
          text = item.emoji,
          fontSize = 12.sp,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(4.dp)
        )

        // Micro Duration Pill in bottom-left
        Row(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (isVisible) PurpleAccent else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(8.dp)
          )
          Text(
            text = "2.4s",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Medium
          )
        }

        // Selected State Checkmark Badge in top-right
        if (isSelected) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(4.dp)
              .size(20.dp)
              .clip(CircleShape)
              .background(PurpleAccent),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Selected",
              tint = Color.White,
              modifier = Modifier.size(13.dp)
            )
          }

          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(PurpleAccent.copy(alpha = 0.12f))
          )
        }
      }

      // 2. Filter Title Label & Pack Subtitle
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PurpleAccent else TextPrimary,
            fontSize = 10.5.sp
          ),
          maxLines = 1,
          textAlign = TextAlign.Center,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = plugin.manifest.name,
          style = MaterialTheme.typography.labelSmall.copy(
            color = TextSecondary.copy(alpha = 0.8f),
            fontSize = 8.5.sp
          ),
          maxLines = 1,
          textAlign = TextAlign.Center,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
