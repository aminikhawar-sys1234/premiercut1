package com.example.ui.components.timeline

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.media.VideoThumbnailManager
import com.example.ui.theme.CyanAccent
import kotlin.math.ceil

/**
 * Renders a continuous, high-performance video thumbnail filmstrip for a timeline clip.
 * Automatically samples source frames from [sourceStartMs] to [sourceEndMs],
 * recalculates density upon zoom (msPerPixel changes), and displays frames asynchronously.
 */
@Composable
fun VideoFilmstripView(
  clipId: String,
  uri: String,
  timelineStartMs: Long,
  durationMs: Long,
  sourceStartMs: Long = 0L,
  sourceEndMs: Long = durationMs,
  speed: Float = 1.0f,
  isReversed: Boolean = false,
  isVideo: Boolean = true,
  clipWidthDp: Dp,
  clipHeightDp: Dp,
  currentPlayheadMs: Long? = null,
  rotation: Int = 0,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val density = androidx.compose.ui.platform.LocalDensity.current.density

  // Compute adaptive thumbnail tile width based on clip height (~50-60dp wide per frame)
  val tileWidthDp = remember(clipHeightDp) {
    (clipHeightDp * 0.82f).coerceIn(42.dp, 68.dp)
  }

  // Calculate optimal pixel dimensions for smooth, memory-efficient timeline filmstrip rendering
  val targetHeightPx = remember(clipHeightDp, density) {
    (clipHeightDp.value * density).toInt().coerceIn(72, 160)
  }
  val targetWidthPx = remember(tileWidthDp, density) {
    (tileWidthDp.value * density).toInt().coerceIn(72, 160)
  }

  val tileCount = remember(clipWidthDp, tileWidthDp) {
    maxOf(1, ceil(clipWidthDp.value / tileWidthDp.value).toInt()).coerceAtMost(25)
  }

  // Calculate the exact source timestamp in milliseconds for each thumbnail tile
  val frameTimestampsMs = remember(
    clipId, uri, durationMs, sourceStartMs, sourceEndMs, speed, isReversed, tileCount
  ) {
    List(tileCount) { index ->
      val progress = if (tileCount == 1) 0.5f else (index.toFloat() / (tileCount - 1).coerceAtLeast(1))
      val offsetMs = (progress * durationMs).toLong()
      val effectiveSourceStart = sourceStartMs.coerceAtLeast(0L)
      val effectiveSourceEnd = if (sourceEndMs > sourceStartMs) sourceEndMs else (effectiveSourceStart + durationMs)

      if (isReversed) {
        (effectiveSourceEnd - (offsetMs * speed).toLong()).coerceIn(effectiveSourceStart, effectiveSourceEnd)
      } else {
        (effectiveSourceStart + (offsetMs * speed).toLong()).coerceIn(effectiveSourceStart, effectiveSourceEnd)
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .clip(RoundedCornerShape(6.dp))
      .background(Color(0xFF0F131A))
  ) {
    // 1. Continuous Row of Video Frame Thumbnails
    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.Start
    ) {
      frameTimestampsMs.forEachIndexed { index, sourceTimeMs ->
        ThumbnailTile(
          context = context,
          uri = uri,
          sourceTimeMs = sourceTimeMs,
          isVideo = isVideo,
          targetWidthPx = targetWidthPx,
          targetHeightPx = targetHeightPx,
          rotation = rotation,
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("thumbnail_tile_${clipId}_$index")
        )
      }
    }

    // 2. Filmstrip Frame Separator Grid Overlay (subtle frame boundaries)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val count = tileCount
      if (count > 1) {
        val step = w / count
        for (i in 1 until count) {
          val x = i * step
          // Frame separator line
          drawLine(
            color = Color.Black.copy(alpha = 0.4f),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 0.8.dp.toPx()
          )
        }
      }
    }
  }
}

@Composable
private fun ThumbnailTile(
  context: android.content.Context,
  uri: String,
  sourceTimeMs: Long,
  isVideo: Boolean,
  targetWidthPx: Int,
  targetHeightPx: Int,
  rotation: Int,
  modifier: Modifier = Modifier
) {
  val quantizedTimeMs = remember(sourceTimeMs) {
    (sourceTimeMs.coerceAtLeast(0L) / 100L) * 100L
  }

  var bitmap by remember(uri, quantizedTimeMs, isVideo, targetWidthPx, targetHeightPx, rotation) {
    val key = VideoThumbnailManager.makeKey(uri, quantizedTimeMs, targetWidthPx, targetHeightPx, rotation)
    mutableStateOf(VideoThumbnailManager.getCachedThumbnail(key))
  }

  LaunchedEffect(uri, quantizedTimeMs, isVideo, targetWidthPx, targetHeightPx, rotation) {
    if (bitmap == null) {
      val result = VideoThumbnailManager.getThumbnail(
        context = context,
        uri = uri,
        sourceTimeMs = quantizedTimeMs,
        targetWidth = targetWidthPx,
        targetHeight = targetHeightPx,
        isVideo = isVideo,
        rotation = rotation
      )
      if (result != null && !result.isRecycled) {
        bitmap = result
      }
    }
  }

  Box(
    modifier = modifier
      .background(Color(0xFF141923))
  ) {
    val currentBmp = bitmap
    if (currentBmp != null && !currentBmp.isRecycled) {
      Image(
        bitmap = currentBmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      // Shimmer / Placeholder while decoding
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.linearGradient(
              listOf(
                Color(0xFF161C28),
                Color(0xFF222B3D),
                Color(0xFF161C28)
              )
            )
          )
      )
    }
  }
}
