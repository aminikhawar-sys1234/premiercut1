package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import android.view.ViewGroup
import com.example.engine.media.MediaRelinkManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.StickerClip
import com.example.domain.model.TextClip
import com.example.domain.model.VideoClip
import com.example.engine.KeyframeInterpolator
import com.example.engine.SelectedTrackElement
import com.example.engine.composition.StickerLayerRenderer
import com.example.engine.text.TextLayerRenderer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.RedAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Direct Manipulation Touch Gesture Detector.
 *
 * Strict gesture separation:
 * - 1-Finger Drag: ONLY updates position (pan). Scale and rotation are strictly unchanged.
 *   Uses inverse rotation/scale matrix mapping so drag delta is 1:1 with screen pixels.
 * - 2-Finger Pinch/Twist: ONLY updates scale and rotation with deadband thresholds.
 * - Single-Tap: selects element.
 * - Double-Tap: opens editor.
 *
 * All drag events are consumed so parent views never scroll or zoom accidentally.
 */
suspend fun PointerInputScope.detectElementTouchGestures(
  rotationDegrees: Float,
  scale: Float,
  parentWidthPx: Float,
  parentHeightPx: Float,
  onSelect: () -> Unit,
  onMoveDelta: (deltaNormX: Float, deltaNormY: Float) -> Unit,
  onTwoFingerTransform: ((scaleFactor: Float, rotationDeltaDeg: Float) -> Unit)? = null,
  onTap: (() -> Unit)? = null,
  onDoubleTap: (() -> Unit)? = null
) {
  val rad = Math.toRadians(rotationDegrees.toDouble())
  val cosVal = cos(rad).toFloat()
  val sinVal = sin(rad).toFloat()

  var lastTapTime = 0L

  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)

    var isDrag = false
    var totalDragDist = 0f
    val touchSlop = viewConfiguration.touchSlop
    var prevDist = 0f
    var prevAngle = 0f
    val downTime = System.currentTimeMillis()

    do {
      val event = awaitPointerEvent()
      val pressedPointers = event.changes.filter { it.pressed }
      val pointerCount = pressedPointers.size

      if (pointerCount == 1) {
        // SINGLE FINGER: MOVE ONLY (Position update only, never scale or rotate)
        val change = pressedPointers[0]
        val localDelta = change.position - change.previousPosition
        val dragDist = hypot(localDelta.x, localDelta.y)
        totalDragDist += dragDist

        if (totalDragDist > touchSlop) {
          if (!isDrag) {
            isDrag = true
            onSelect()
          }
        }

        if (isDrag && (localDelta.x != 0f || localDelta.y != 0f)) {
          change.consume()
          // Convert from rotated local space back to parent screen space
          val screenDx = (localDelta.x * cosVal - localDelta.y * sinVal)
          val screenDy = (localDelta.x * sinVal + localDelta.y * cosVal)

          val deltaNormX = (screenDx * 2f) / parentWidthPx
          val deltaNormY = (screenDy * 2f) / parentHeightPx
          onMoveDelta(deltaNormX, deltaNormY)
        }
        prevDist = 0f
        prevAngle = 0f
      } else if (pointerCount >= 2 && onTwoFingerTransform != null) {
        // TWO FINGERS: SCALE & ROTATE ONLY
        if (!isDrag) {
          isDrag = true
          onSelect()
        }
        val p1 = pressedPointers[0]
        val p2 = pressedPointers[1]
        val dx = p2.position.x - p1.position.x
        val dy = p2.position.y - p1.position.y
        val currentDist = hypot(dx, dy)
        val currentAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        if (prevDist > 10f) {
          val scaleFactor = currentDist / prevDist
          val rotDelta = currentAngle - prevAngle

          val significantZoom = abs(scaleFactor - 1f) > 0.003f
          val significantRot = abs(rotDelta) > 1.0f

          if (significantZoom || significantRot) {
            onTwoFingerTransform(scaleFactor, if (significantRot) rotDelta else 0f)
          }
        }
        prevDist = currentDist
        prevAngle = currentAngle
        pressedPointers.forEach { it.consume() }
      }
    } while (event.changes.any { it.pressed })

    // Handle Tap vs Drag
    if (!isDrag && totalDragDist <= touchSlop) {
      val now = System.currentTimeMillis()
      if (now - downTime < 400L) {
        onSelect()
        if (onDoubleTap != null && (now - lastTapTime < 350L)) {
          onDoubleTap()
          lastTapTime = 0L
        } else {
          lastTapTime = now
          onTap?.invoke()
        }
      }
    }
  }
}

/**
 * Touch-Based Interactive Transformation Canvas.
 * Supports direct touch Drag, Pinch Scale, Two-Finger Rotation, Corner Transform Handles,
 * Freehand Edge Position Coercion, Selection Switching, and Undo-Compatible Timeline State Updates.
 */
@Composable
fun InteractiveTransformOverlay(
  activeTexts: List<TextClip>,
  activeOverlays: List<VideoClip>,
  activeStickers: List<StickerClip>,
  selectedElement: SelectedTrackElement,
  currentPosMs: Long,
  onSelectElement: (SelectedTrackElement) -> Unit,
  onUpdateText: (TextClip) -> Unit,
  onUpdateOverlay: (VideoClip) -> Unit,
  onUpdateSticker: (StickerClip) -> Unit,
  onDeleteClip: (String) -> Unit,
  onDuplicateClip: (String) -> Unit,
  onEditText: ((TextClip) -> Unit)? = null,
  getOverlayPlayer: ((String) -> androidx.media3.exoplayer.ExoPlayer?)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  val currentOnSelectElement by rememberUpdatedState(onSelectElement)
  val currentOnUpdateText by rememberUpdatedState(onUpdateText)
  val currentOnUpdateOverlay by rememberUpdatedState(onUpdateOverlay)
  val currentOnUpdateSticker by rememberUpdatedState(onUpdateSticker)
  val currentOnEditText by rememberUpdatedState(onEditText)
  val currentOnDeleteClip by rememberUpdatedState(onDeleteClip)
  val currentOnDuplicateClip by rememberUpdatedState(onDuplicateClip)

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .then(
        if (selectedElement != SelectedTrackElement.None) {
          Modifier.pointerInput(selectedElement) {
            // Tap on empty canvas background to deselect active transform frame
            detectTapGestures {
              currentOnSelectElement(SelectedTrackElement.None)
            }
          }
        } else {
          Modifier
        }
      )
  ) {
    val parentWidthPx = constraints.maxWidth.toFloat()
    val parentHeightPx = constraints.maxHeight.toFloat()
    val density = LocalDensity.current

    if (parentWidthPx <= 0f || parentHeightPx <= 0f) return@BoxWithConstraints

    // 1. Render Active Overlays (PIP / Picture-in-Picture)
    activeOverlays.filter { !it.isHidden }.forEach { overlay ->
      val isSelected = selectedElement is SelectedTrackElement.Overlay &&
        (selectedElement as SelectedTrackElement.Overlay).clipId == overlay.id

      val currentOverlay by rememberUpdatedState(overlay)
      val relTime = currentPosMs - overlay.timelineStartMs
      val kf = KeyframeInterpolator.interpolate(overlay, relTime)

      // Base unscaled size
      val baseWidthDp = 160.dp
      val baseHeightDp = 100.dp

      val baseWidthPx = with(density) { baseWidthDp.toPx() }
      val baseHeightPx = with(density) { baseHeightDp.toPx() }

      // Normalized coordinates (-1f..1f) to Screen Center Offset
      val centerXPx = (parentWidthPx / 2f) + (kf.posX * parentWidthPx / 2f)
      val centerYPx = (parentHeightPx / 2f) + (kf.posY * parentHeightPx / 2f)

      val centerXDp = with(density) { centerXPx.toDp() }
      val centerYDp = with(density) { centerYPx.toDp() }

      Box(
        modifier = Modifier
          .offset(
            x = centerXDp - (baseWidthDp / 2f),
            y = centerYDp - (baseHeightDp / 2f)
          )
          .size(baseWidthDp, baseHeightDp)
          .scale(kf.scale)
          .rotate(kf.rotation)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF1E293B))
          .border(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) (if (overlay.isLocked) RedAccent else AmberAccent) else Color.White.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
          )
          .pointerInput(overlay.id, kf.rotation, kf.scale, overlay.isLocked) {
            detectElementTouchGestures(
              rotationDegrees = kf.rotation,
              scale = kf.scale,
              parentWidthPx = parentWidthPx,
              parentHeightPx = parentHeightPx,
              onSelect = {
                currentOnSelectElement(SelectedTrackElement.Overlay(currentOverlay.id))
              },
              onMoveDelta = { deltaNormX, deltaNormY ->
                if (currentOverlay.isLocked) return@detectElementTouchGestures
                val clip = currentOverlay
                val rel = currentPosMs - clip.timelineStartMs
                val activeKf = clip.keyframes.find { abs(it.timeMs - rel) <= 150L }
                if (activeKf != null) {
                  val updatedKeyframes = clip.keyframes.map { kfItem ->
                    if (kfItem.id == activeKf.id) {
                      kfItem.copy(
                        posX = (kfItem.posX + deltaNormX).coerceIn(-1.8f, 1.8f),
                        posY = (kfItem.posY + deltaNormY).coerceIn(-1.8f, 1.8f)
                      )
                    } else kfItem
                  }
                  currentOnUpdateOverlay(clip.copy(keyframes = updatedKeyframes))
                } else {
                  val newX = (clip.cropOffsetX + deltaNormX).coerceIn(-1.8f, 1.8f)
                  val newY = (clip.cropOffsetY + deltaNormY).coerceIn(-1.8f, 1.8f)
                  currentOnUpdateOverlay(clip.copy(cropOffsetX = newX, cropOffsetY = newY))
                }
              },
              onTwoFingerTransform = if (overlay.isLocked) null else { scaleFactor, rotDelta ->
                val clip = currentOverlay
                val rel = currentPosMs - clip.timelineStartMs
                val activeKf = clip.keyframes.find { abs(it.timeMs - rel) <= 150L }
                if (activeKf != null) {
                  val updatedKeyframes = clip.keyframes.map { kfItem ->
                    if (kfItem.id == activeKf.id) {
                      val newScaleX = (kfItem.scaleX * scaleFactor).coerceIn(0.15f, 8.0f)
                      val newScaleY = (kfItem.scaleY * scaleFactor).coerceIn(0.15f, 8.0f)
                      val newRot = (kfItem.rotation + rotDelta) % 360f
                      kfItem.copy(scaleX = newScaleX, scaleY = newScaleY, rotation = newRot)
                    } else kfItem
                  }
                  currentOnUpdateOverlay(clip.copy(keyframes = updatedKeyframes))
                } else {
                  val newScale = (clip.cropScale * scaleFactor).coerceIn(0.15f, 8.0f)
                  val newRot = ((clip.rotationDegrees + rotDelta) % 360f).toInt()
                  currentOnUpdateOverlay(clip.copy(cropScale = newScale, rotationDegrees = newRot))
                }
              },
              onTap = {
                currentOnSelectElement(SelectedTrackElement.Overlay(currentOverlay.id))
              }
            )
          }
      ) {
        val overlayColorFilter = remember(overlay.filter) {
          val f = overlay.filter
          if (f == null || f.type == com.example.domain.model.FilterType.NONE) {
            null
          } else {
            com.example.engine.composition.ColorFilterGenerator.getFilterMatrix(f.type, f.intensity)?.let { m ->
              androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m.array))
            }
          }
        }

        val isRealVideoOverlay = overlay.isVideo && MediaRelinkManager.isRealPlayableMedia(context, overlay.uri)
        val overlayPlayer = if (isRealVideoOverlay) getOverlayPlayer?.invoke(overlay.id) else null

        if (isRealVideoOverlay && overlayPlayer != null) {
          AndroidView(
            factory = { ctx ->
              android.view.TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                try {
                  overlayPlayer.setVideoTextureView(this)
                } catch (e: Exception) {
                  android.util.Log.w("InteractiveTransformOverlay", "Failed to attach TextureView to overlayPlayer", e)
                }
                tag = overlayPlayer
              }
            },
            update = { tv ->
              if (tv.tag != overlayPlayer) {
                try {
                  overlayPlayer.setVideoTextureView(tv)
                  tv.tag = overlayPlayer
                } catch (e: Exception) {
                  android.util.Log.w("InteractiveTransformOverlay", "Failed to rebind TextureView to overlayPlayer", e)
                }
              }
            },
            onReset = { /* Keep texture view intact */ },
            onRelease = { tv ->
              try {
                overlayPlayer.clearVideoTextureView(tv)
              } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxSize()
          )
        } else {
          AsyncImage(
            model = overlay.uri,
            contentDescription = overlay.name,
            contentScale = ContentScale.Crop,
            colorFilter = overlayColorFilter,
            modifier = Modifier.fillMaxSize()
          )
        }

        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (overlay.isLocked) RedAccent else AmberAccent)
                .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
              Text(
                text = if (overlay.isLocked) "LOCKED" else "PIP",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.Black
                )
              )
            }
            Icon(
              if (overlay.isLocked) Icons.Default.Lock else if (overlay.isVideo) Icons.Default.Movie else Icons.Default.Image,
              contentDescription = null,
              tint = if (overlay.isLocked) RedAccent else AmberAccent,
              modifier = Modifier.size(14.dp)
            )
          }
          Text(
            text = overlay.name,
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // If Overlay is Selected, Render Interactive Transform Handles around it
      if (isSelected && !overlay.isLocked) {
        TransformHandlesBox(
          centerXPx = centerXPx,
          centerYPx = centerYPx,
          baseWidthPx = baseWidthPx,
          baseHeightPx = baseHeightPx,
          scale = kf.scale,
          rotation = kf.rotation,
          onDelete = { onDeleteClip(overlay.id) },
          onDuplicate = { onDuplicateClip(overlay.id) },
          onReset = {
            onUpdateOverlay(overlay.copy(cropScale = 1.0f, rotationDegrees = 0))
          },
          onTransformHandleDrag = { deltaScale, deltaRotation ->
            val clip = currentOverlay
            val newScale = (clip.cropScale * deltaScale).coerceIn(0.15f, 8.0f)
            val newRot = ((clip.rotationDegrees + deltaRotation) % 360f).toInt()
            currentOnUpdateOverlay(clip.copy(cropScale = newScale, rotationDegrees = newRot))
          }
        )
      }
    }

    // 2. Render Active Stickers
    activeStickers.filter { !it.isHidden }.forEach { sticker ->
      val isSelected = selectedElement is SelectedTrackElement.Sticker &&
        (selectedElement as SelectedTrackElement.Sticker).clipId == sticker.id

      val currentSticker by rememberUpdatedState(sticker)
      val isBadge = sticker.badgeType != null
      val isElement = sticker.elementId != null
      val baseWidthDp = when {
        isBadge -> 150.dp
        isElement -> when (sticker.elementCategory) {
          "tables" -> 140.dp
          "charts" -> 130.dp
          "frames" -> 120.dp
          "graphics" -> 100.dp
          else -> 90.dp
        }
        else -> 80.dp
      }
      val baseHeightDp = when {
        isBadge -> 64.dp
        isElement -> when (sticker.elementCategory) {
          "tables" -> 90.dp
          "charts" -> 95.dp
          "frames" -> 120.dp
          "graphics" -> 100.dp
          else -> 90.dp
        }
        else -> 80.dp
      }
      val baseWidthPx = with(density) { baseWidthDp.toPx() }
      val baseHeightPx = with(density) { baseHeightDp.toPx() }

      val animState = StickerLayerRenderer.evaluateAnimation(sticker, currentPosMs)

      val centerXPx = (parentWidthPx / 2f) + (animState.posX * parentWidthPx / 2f)
      val centerYPx = (parentHeightPx / 2f) + (animState.posY * parentHeightPx / 2f)

      val centerXDp = with(density) { centerXPx.toDp() }
      val centerYDp = with(density) { centerYPx.toDp() }

      Box(
        modifier = Modifier
          .offset(
            x = centerXDp - (baseWidthDp / 2f),
            y = centerYDp - (baseHeightDp / 2f)
          )
          .size(width = baseWidthDp, height = baseHeightDp)
          .scale(animState.scale)
          .rotate(animState.rotation)
          .border(
            width = if (isSelected) 2.dp else 0.dp,
            color = if (isSelected) (if (sticker.isLocked) RedAccent else AmberAccent) else Color.Transparent,
            shape = RoundedCornerShape(12.dp)
          )
          .pointerInput(sticker.id, animState.rotation, animState.scale, sticker.isLocked) {
            detectElementTouchGestures(
              rotationDegrees = animState.rotation,
              scale = animState.scale,
              parentWidthPx = parentWidthPx,
              parentHeightPx = parentHeightPx,
              onSelect = {
                currentOnSelectElement(SelectedTrackElement.Sticker(currentSticker.id))
              },
              onMoveDelta = { deltaNormX, deltaNormY ->
                if (currentSticker.isLocked) return@detectElementTouchGestures
                val clip = currentSticker
                val rel = currentPosMs - clip.timelineStartMs
                val activeKf = clip.keyframes.find { abs(it.timeMs - rel) <= 150L }
                if (activeKf != null) {
                  val updatedKeyframes = clip.keyframes.map { kfItem ->
                    if (kfItem.id == activeKf.id) {
                      kfItem.copy(
                        posX = (kfItem.posX + deltaNormX).coerceIn(-1.8f, 1.8f),
                        posY = (kfItem.posY + deltaNormY).coerceIn(-1.8f, 1.8f)
                      )
                    } else kfItem
                  }
                  currentOnUpdateSticker(clip.copy(keyframes = updatedKeyframes))
                } else {
                  val newX = (clip.posX + deltaNormX).coerceIn(-1.8f, 1.8f)
                  val newY = (clip.posY + deltaNormY).coerceIn(-1.8f, 1.8f)
                  currentOnUpdateSticker(clip.copy(posX = newX, posY = newY))
                }
              },
              onTwoFingerTransform = if (sticker.isLocked) null else { scaleFactor, rotDelta ->
                val clip = currentSticker
                val rel = currentPosMs - clip.timelineStartMs
                val activeKf = clip.keyframes.find { abs(it.timeMs - rel) <= 150L }
                if (activeKf != null) {
                  val updatedKeyframes = clip.keyframes.map { kfItem ->
                    if (kfItem.id == activeKf.id) {
                      val newScaleX = (kfItem.scaleX * scaleFactor).coerceIn(0.15f, 8.0f)
                      val newScaleY = (kfItem.scaleY * scaleFactor).coerceIn(0.15f, 8.0f)
                      val newRot = (kfItem.rotation + rotDelta) % 360f
                      kfItem.copy(scaleX = newScaleX, scaleY = newScaleY, rotation = newRot)
                    } else kfItem
                  }
                  currentOnUpdateSticker(clip.copy(keyframes = updatedKeyframes))
                } else {
                  val newScale = (clip.scale * scaleFactor).coerceIn(0.15f, 8.0f)
                  val newRot = (clip.rotation + rotDelta) % 360f
                  currentOnUpdateSticker(clip.copy(scale = newScale, rotation = newRot))
                }
              },
              onTap = {
                currentOnSelectElement(SelectedTrackElement.Sticker(currentSticker.id))
              }
            )
          },
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas
            StickerLayerRenderer.draw(
              canvas = nativeCanvas,
              clip = sticker.copy(posX = 0f, posY = 0f, scale = 1f, rotation = 0f),
              currentPosMs = currentPosMs,
              width = size.width.toInt(),
              height = size.height.toInt()
            )
          }
        }
      }

      if (isSelected && !sticker.isLocked) {
        TransformHandlesBox(
          centerXPx = centerXPx,
          centerYPx = centerYPx,
          baseWidthPx = baseWidthPx,
          baseHeightPx = baseHeightPx,
          scale = animState.scale,
          rotation = animState.rotation,
          onDelete = { onDeleteClip(sticker.id) },
          onDuplicate = { onDuplicateClip(sticker.id) },
          onReset = {
            onUpdateSticker(sticker.copy(scale = 1.0f, rotation = 0f))
          },
          onTransformHandleDrag = { deltaScale, deltaRotation ->
            val clip = currentSticker
            val rel = currentPosMs - clip.timelineStartMs
            val activeKf = clip.keyframes.find { abs(it.timeMs - rel) <= 150L }
            if (activeKf != null) {
              val updatedKeyframes = clip.keyframes.map { kfItem ->
                if (kfItem.id == activeKf.id) {
                  val newScaleX = (kfItem.scaleX * deltaScale).coerceIn(0.15f, 8.0f)
                  val newScaleY = (kfItem.scaleY * deltaScale).coerceIn(0.15f, 8.0f)
                  val newRot = (kfItem.rotation + deltaRotation) % 360f
                  kfItem.copy(scaleX = newScaleX, scaleY = newScaleY, rotation = newRot)
                } else kfItem
              }
              currentOnUpdateSticker(clip.copy(keyframes = updatedKeyframes))
            } else {
              val newScale = (clip.scale * deltaScale).coerceIn(0.15f, 8.0f)
              val newRot = (clip.rotation + deltaRotation) % 360f
              currentOnUpdateSticker(clip.copy(scale = newScale, rotation = newRot))
            }
          }
        )
      }
    }

    // 3. Render Active Text Layers (Single unified high-performance Canvas pass for 50+ layers)
    val visibleTexts = activeTexts.filter { !it.isHidden }
    if (visibleTexts.isNotEmpty()) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
          visibleTexts.forEach { textClip ->
            TextLayerRenderer.draw(
              canvas = canvas.nativeCanvas,
              clip = textClip,
              currentPosMs = currentPosMs,
              width = parentWidthPx.toInt(),
              height = parentHeightPx.toInt(),
              context = context
            )
          }
        }
      }
    }

    // Touch Target Bounding Boxes and Transform Handles for Text Layers
    visibleTexts.forEach { textClip ->
      val isSelected = selectedElement is SelectedTrackElement.Text &&
        (selectedElement as SelectedTrackElement.Text).clipId == textClip.id

      val currentTextClip by rememberUpdatedState(textClip)

      // Calculate authoritative rendered bounds using TextLayerRenderer
      val textBounds = remember(
        textClip.text,
        textClip.fontFamily,
        textClip.customFontPath,
        textClip.fontSizeSp,
        textClip.fontWeight,
        textClip.isItalic,
        textClip.isUnderline,
        textClip.isAllCaps,
        textClip.letterSpacing,
        textClip.lineSpacing,
        textClip.alignment,
        textClip.scale,
        textClip.rotation,
        textClip.posX,
        textClip.posY,
        textClip.hasBackground,
        textClip.bgPadding,
        textClip.strokeWidth,
        textClip.animationType,
        textClip.animDurationMs,
        textClip.timelineStartMs,
        currentPosMs,
        parentWidthPx,
        parentHeightPx
      ) {
        TextLayerRenderer.measureTextBounds(
          clip = textClip,
          currentPosMs = currentPosMs,
          width = parentWidthPx.toInt(),
          height = parentHeightPx.toInt(),
          context = context
        )
      }

      val currentWidthPx = textBounds.widthPx
      val currentHeightPx = textBounds.heightPx

      val currentWidthDp = with(density) { currentWidthPx.toDp() }
      val currentHeightDp = with(density) { currentHeightPx.toDp() }

      val centerXPx = textBounds.centerXPx
      val centerYPx = textBounds.centerYPx

      val centerXDp = with(density) { centerXPx.toDp() }
      val centerYDp = with(density) { centerYPx.toDp() }

      // Touch Target Bounding Box positioned at exact text coordinates
      Box(
        modifier = Modifier
          .offset(
            x = centerXDp - (currentWidthDp / 2f),
            y = centerYDp - (currentHeightDp / 2f)
          )
          .size(currentWidthDp, currentHeightDp)
          .rotate(textBounds.rotation)
          .pointerInput(textClip.id, textBounds.rotation, textClip.scale, textClip.isLocked) {
            detectElementTouchGestures(
              rotationDegrees = textBounds.rotation,
              scale = textClip.scale,
              parentWidthPx = parentWidthPx,
              parentHeightPx = parentHeightPx,
              onSelect = {
                currentOnSelectElement(SelectedTrackElement.Text(currentTextClip.id))
              },
              onMoveDelta = { deltaNormX, deltaNormY ->
                if (currentTextClip.isLocked) return@detectElementTouchGestures
                val clip = currentTextClip
                val newX = (clip.posX + deltaNormX).coerceIn(-1.8f, 1.8f)
                val newY = (clip.posY + deltaNormY).coerceIn(-1.8f, 1.8f)
                currentOnUpdateText(clip.copy(posX = newX, posY = newY))
              },
              onTwoFingerTransform = if (textClip.isLocked) null else { scaleFactor, rotDelta ->
                val clip = currentTextClip
                val newScale = (clip.scale * scaleFactor).coerceIn(0.15f, 10.0f)
                val newRot = if (rotDelta != 0f) (clip.rotation + rotDelta) % 360f else clip.rotation
                currentOnUpdateText(clip.copy(scale = newScale, rotation = newRot))
              },
              onTap = {
                currentOnSelectElement(SelectedTrackElement.Text(currentTextClip.id))
              },
              onDoubleTap = {
                currentOnSelectElement(SelectedTrackElement.Text(currentTextClip.id))
                if (!currentTextClip.isLocked) {
                  currentOnEditText?.invoke(currentTextClip)
                }
              }
            )
          }
      )

      // When text is selected: display Four-Corner Controls around selected text
      if (isSelected && !textClip.isLocked) {
        TextFourCornerControlsBox(
          parentWidthPx = parentWidthPx,
          parentHeightPx = parentHeightPx,
          centerXPx = centerXPx,
          centerYPx = centerYPx,
          widthPx = currentWidthPx,
          heightPx = currentHeightPx,
          rotation = textBounds.rotation,
          onEdit = {
            currentOnSelectElement(SelectedTrackElement.Text(currentTextClip.id))
            currentOnEditText?.invoke(currentTextClip)
          },
          onDelete = {
            currentOnDeleteClip(textClip.id)
          },
          onDuplicate = {
            currentOnDuplicateClip(textClip.id)
          },
          onResizeDrag = { deltaScale ->
            val clip = currentTextClip
            val newScale = (clip.scale * deltaScale).coerceIn(0.15f, 10.0f)
            currentOnUpdateText(clip.copy(scale = newScale))
          },
          onTransformHandleDrag = { deltaScale, deltaRotation ->
            val clip = currentTextClip
            val newScale = (clip.scale * deltaScale).coerceIn(0.15f, 10.0f)
            val newRot = (clip.rotation + deltaRotation) % 360f
            currentOnUpdateText(clip.copy(scale = newScale, rotation = newRot))
          }
        )
      }
    }
  }
}

/**
 * Four-Corner Controls for Selected Text (Matching CapCut & Pro Mobile Editor UX):
 * - Top-left corner — Edit/Pen 🖊️: Reopens the Text Editing Panel.
 * - Top-right corner — Delete ✕: Removes the selected text immediately.
 * - Bottom-left corner — Resize: Drag to scale smaller/larger while maintaining position.
 * - Bottom-right corner — Copy ❐: Tapping creates one duplicate with preserved properties. Drag scales & rotates.
 * - Crisp rectangular bounding border tightly matching rendered text.
 * - Handles are intelligently constrained to visible preview viewport when text reaches edges.
 */
@Composable
private fun TextFourCornerControlsBox(
  parentWidthPx: Float,
  parentHeightPx: Float,
  centerXPx: Float,
  centerYPx: Float,
  widthPx: Float,
  heightPx: Float,
  rotation: Float,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDuplicate: () -> Unit,
  onResizeDrag: (deltaScale: Float) -> Unit,
  onTransformHandleDrag: (deltaScale: Float, deltaRotation: Float) -> Unit
) {
  val density = LocalDensity.current

  val currentWidthDp = with(density) { widthPx.toDp() }
  val currentHeightDp = with(density) { heightPx.toDp() }

  val centerXDp = with(density) { centerXPx.toDp() }
  val centerYDp = with(density) { centerYPx.toDp() }

  val handleSizeDp = 32.dp
  val halfHandleDp = handleSizeDp / 2f
  val halfHandlePx = with(density) { halfHandleDp.toPx() }
  val marginPx = with(density) { 6.dp.toPx() }

  val minX = halfHandlePx + marginPx
  val maxX = (parentWidthPx - halfHandlePx - marginPx).coerceAtLeast(minX)
  val minY = halfHandlePx + marginPx
  val maxY = (parentHeightPx - halfHandlePx - marginPx).coerceAtLeast(minY)

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    // 1. White Crisp Bounding Border Box (exact rendered text bounds)
    Box(
      modifier = Modifier
        .offset(
          x = centerXDp - (currentWidthDp / 2f),
          y = centerYDp - (currentHeightDp / 2f)
        )
        .size(currentWidthDp, currentHeightDp)
        .rotate(rotation)
        .border(
          width = 1.5.dp,
          color = Color.White,
          shape = RoundedCornerShape(2.dp)
        )
    )

    // Corner Positions relative to center with rotation applied
    val rad = Math.toRadians(rotation.toDouble())
    val halfW = widthPx / 2f
    val halfH = heightPx / 2f

    // Top-Left Corner (Edit / Pen 🖊️)
    val rawTlX = centerXPx + ((-halfW) * cos(rad) - (-halfH) * sin(rad)).toFloat()
    val rawTlY = centerYPx + ((-halfW) * sin(rad) + (-halfH) * cos(rad)).toFloat()

    // Top-Right Corner (Delete ✕)
    val rawTrX = centerXPx + (halfW * cos(rad) - (-halfH) * sin(rad)).toFloat()
    val rawTrY = centerYPx + (halfW * sin(rad) + (-halfH) * cos(rad)).toFloat()

    // Bottom-Left Corner (Resize)
    val rawBlX = centerXPx + ((-halfW) * cos(rad) - (halfH) * sin(rad)).toFloat()
    val rawBlY = centerYPx + ((-halfW) * sin(rad) + (halfH) * cos(rad)).toFloat()

    // Bottom-Right Corner (Copy / Duplicate ❐ & Rotate/Scale)
    val rawBrX = centerXPx + (halfW * cos(rad) - (halfH) * sin(rad)).toFloat()
    val rawBrY = centerYPx + (halfW * sin(rad) + (halfH) * cos(rad)).toFloat()

    // Clamp handle rendering coordinates to visible viewport so buttons are never clipped
    val tlX = rawTlX.coerceIn(minX, maxX)
    val tlY = rawTlY.coerceIn(minY, maxY)

    val trX = rawTrX.coerceIn(minX, maxX)
    val trY = rawTrY.coerceIn(minY, maxY)

    val blX = rawBlX.coerceIn(minX, maxX)
    val blY = rawBlY.coerceIn(minY, maxY)

    val brX = rawBrX.coerceIn(minX, maxX)
    val brY = rawBrY.coerceIn(minY, maxY)

    // Convert corners to DP
    val tlXDp = with(density) { tlX.toDp() }
    val tlYDp = with(density) { tlY.toDp() }

    val trXDp = with(density) { trX.toDp() }
    val trYDp = with(density) { trY.toDp() }

    val blXDp = with(density) { blX.toDp() }
    val blYDp = with(density) { blY.toDp() }

    val brXDp = with(density) { brX.toDp() }
    val brYDp = with(density) { brY.toDp() }

    // --- CORNER 1: TOP-LEFT (Edit / Pen 🖊️) ---
    Surface(
      onClick = onEdit,
      shape = CircleShape,
      color = Color(0xFF1E222D),
      shadowElevation = 4.dp,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
      modifier = Modifier
        .offset(x = tlXDp - halfHandleDp, y = tlYDp - halfHandleDp)
        .size(handleSizeDp)
        .testTag("text_handle_edit_button")
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = "Edit Text",
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // --- CORNER 2: TOP-RIGHT (Delete ✕) ---
    Surface(
      onClick = onDelete,
      shape = CircleShape,
      color = Color(0xFF1E222D),
      shadowElevation = 4.dp,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
      modifier = Modifier
        .offset(x = trXDp - halfHandleDp, y = trYDp - halfHandleDp)
        .size(handleSizeDp)
        .testTag("text_handle_delete_button")
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Delete Text",
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // --- CORNER 3: BOTTOM-LEFT (Resize) ---
    var blTouchXPx by remember { mutableFloatStateOf(0f) }
    var blTouchYPx by remember { mutableFloatStateOf(0f) }
    var blTouchDist by remember { mutableFloatStateOf(0f) }

    Surface(
      shape = CircleShape,
      color = Color(0xFF1E222D),
      shadowElevation = 4.dp,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
      modifier = Modifier
        .offset(x = blXDp - halfHandleDp, y = blYDp - halfHandleDp)
        .size(handleSizeDp)
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { offset ->
              blTouchXPx = rawBlX + offset.x
              blTouchYPx = rawBlY + offset.y
              val dx = blTouchXPx - centerXPx
              val dy = blTouchYPx - centerYPx
              blTouchDist = hypot(dx, dy).coerceAtLeast(10f)
            },
            onDrag = { change, dragAmount ->
              change.consume()
              blTouchXPx += dragAmount.x
              blTouchYPx += dragAmount.y
              val dx = blTouchXPx - centerXPx
              val dy = blTouchYPx - centerYPx
              val currentDist = hypot(dx, dy).coerceAtLeast(10f)

              if (blTouchDist > 0f) {
                val deltaScale = currentDist / blTouchDist
                onResizeDrag(deltaScale)
              }
              blTouchDist = currentDist
            }
          )
        }
        .testTag("text_handle_resize_button")
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
          imageVector = Icons.Default.OpenInFull,
          contentDescription = "Resize Text",
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // --- CORNER 4: BOTTOM-RIGHT (Copy ❐ & Rotate/Scale) ---
    var brTouchXPx by remember { mutableFloatStateOf(0f) }
    var brTouchYPx by remember { mutableFloatStateOf(0f) }
    var brTouchDist by remember { mutableFloatStateOf(0f) }
    var brTouchAngle by remember { mutableFloatStateOf(0f) }

    Surface(
      onClick = onDuplicate,
      shape = CircleShape,
      color = Color(0xFF1E222D),
      shadowElevation = 4.dp,
      border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
      modifier = Modifier
        .offset(x = brXDp - halfHandleDp, y = brYDp - halfHandleDp)
        .size(handleSizeDp)
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { offset ->
              brTouchXPx = rawBrX + offset.x
              brTouchYPx = rawBrY + offset.y
              val dx = brTouchXPx - centerXPx
              val dy = brTouchYPx - centerYPx
              brTouchDist = hypot(dx, dy).coerceAtLeast(10f)
              brTouchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            },
            onDrag = { change, dragAmount ->
              change.consume()
              brTouchXPx += dragAmount.x
              brTouchYPx += dragAmount.y
              val dx = brTouchXPx - centerXPx
              val dy = brTouchYPx - centerYPx

              val currentDist = hypot(dx, dy).coerceAtLeast(10f)
              val currentAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

              if (brTouchDist > 0f) {
                val deltaScale = currentDist / brTouchDist
                val deltaRotation = currentAngle - brTouchAngle
                onTransformHandleDrag(deltaScale, deltaRotation)
              }

              brTouchDist = currentDist
              brTouchAngle = currentAngle
            }
          )
        }
        .testTag("text_handle_copy_button")
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
          imageVector = Icons.Default.ContentCopy,
          contentDescription = "Copy Text",
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}

/**
 * Renders high-contrast bounding box and 4 corner touch handles (Delete, Scale/Rotate, Duplicate, Reset)
 * around the active element.
 */
@Composable
private fun TransformHandlesBox(
  centerXPx: Float,
  centerYPx: Float,
  baseWidthPx: Float,
  baseHeightPx: Float,
  scale: Float,
  rotation: Float,
  onDelete: () -> Unit,
  onDuplicate: () -> Unit,
  onReset: () -> Unit,
  onTransformHandleDrag: (deltaScale: Float, deltaRotation: Float) -> Unit
) {
  val density = LocalDensity.current

  val currentWidthPx = baseWidthPx * scale
  val currentHeightPx = baseHeightPx * scale

  val currentWidthDp = with(density) { currentWidthPx.toDp() }
  val currentHeightDp = with(density) { currentHeightPx.toDp() }

  val centerXDp = with(density) { centerXPx.toDp() }
  val centerYDp = with(density) { centerYPx.toDp() }

  val handleSizeDp = 28.dp
  val halfHandleDp = handleSizeDp / 2f

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    // 1. Dashed/Solid Accent Bounding Border Box
    Box(
      modifier = Modifier
        .offset(
          x = centerXDp - (currentWidthDp / 2f),
          y = centerYDp - (currentHeightDp / 2f)
        )
        .size(currentWidthDp, currentHeightDp)
        .rotate(rotation)
        .border(
          width = 2.dp,
          color = CyanAccent,
          shape = RoundedCornerShape(8.dp)
        )
    )

    // Corner Positions relative to center with rotation applied
    val rad = Math.toRadians(rotation.toDouble())
    val halfW = currentWidthPx / 2f
    val halfH = currentHeightPx / 2f

    // Corner Offsets from Center
    // Top-Right Corner (Delete)
    val trX = centerXPx + (halfW * cos(rad) - (-halfH) * sin(rad)).toFloat()
    val trY = centerYPx + (halfW * sin(rad) + (-halfH) * cos(rad)).toFloat()

    // Bottom-Right Corner (Scale & Rotate Handle)
    val brX = centerXPx + (halfW * cos(rad) - (halfH) * sin(rad)).toFloat()
    val brY = centerYPx + (halfW * sin(rad) + (halfH) * cos(rad)).toFloat()

    // Top-Left Corner (Duplicate / Copy)
    val tlX = centerXPx + ((-halfW) * cos(rad) - (-halfH) * sin(rad)).toFloat()
    val tlY = centerYPx + ((-halfW) * sin(rad) + (-halfH) * cos(rad)).toFloat()

    // Bottom-Left Corner (Reset Scale/Rotation)
    val blX = centerXPx + ((-halfW) * cos(rad) - (halfH) * sin(rad)).toFloat()
    val blY = centerYPx + ((-halfW) * sin(rad) + (halfH) * cos(rad)).toFloat()

    // Convert corners to DP
    val trXDp = with(density) { trX.toDp() }
    val trYDp = with(density) { trY.toDp() }

    val brXDp = with(density) { brX.toDp() }
    val brYDp = with(density) { brY.toDp() }

    val tlXDp = with(density) { tlX.toDp() }
    val tlYDp = with(density) { tlY.toDp() }

    val blXDp = with(density) { blX.toDp() }
    val blYDp = with(density) { blY.toDp() }

    // 2. Corner Handle Buttons
    // Top-Right: Delete
    Surface(
      onClick = onDelete,
      shape = CircleShape,
      color = Color(0xFFFF5252),
      shadowElevation = 4.dp,
      modifier = Modifier
        .offset(x = trXDp - halfHandleDp, y = trYDp - halfHandleDp)
        .size(handleSizeDp)
        .testTag("handle_delete_button")
    ) {
      Icon(
        Icons.Default.Close,
        contentDescription = "Delete Clip",
        tint = Color.White,
        modifier = Modifier
          .padding(4.dp)
          .fillMaxSize()
      )
    }

    // Top-Left: Duplicate
    Surface(
      onClick = onDuplicate,
      shape = CircleShape,
      color = PurpleAccent,
      shadowElevation = 4.dp,
      modifier = Modifier
        .offset(x = tlXDp - halfHandleDp, y = tlYDp - halfHandleDp)
        .size(handleSizeDp)
        .testTag("handle_duplicate_button")
    ) {
      Icon(
        Icons.Default.ContentCopy,
        contentDescription = "Duplicate Clip",
        tint = Color.White,
        modifier = Modifier
          .padding(5.dp)
          .fillMaxSize()
      )
    }

    // Bottom-Left: Reset Scale & Rotation
    Surface(
      onClick = onReset,
      shape = CircleShape,
      color = Color.DarkGray,
      shadowElevation = 4.dp,
      modifier = Modifier
        .offset(x = blXDp - halfHandleDp, y = blYDp - halfHandleDp)
        .size(handleSizeDp)
        .testTag("handle_reset_button")
    ) {
      Icon(
        Icons.Default.RestartAlt,
        contentDescription = "Reset Transform",
        tint = Color.White,
        modifier = Modifier
          .padding(4.dp)
          .fillMaxSize()
      )
    }

    // Bottom-Right: Single-Finger Drag Scale & Rotation Handle
    var touchPointXPx by remember { mutableFloatStateOf(0f) }
    var touchPointYPx by remember { mutableFloatStateOf(0f) }
    var lastTouchDist by remember { mutableFloatStateOf(0f) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }

    Surface(
      shape = CircleShape,
      color = CyanAccent,
      shadowElevation = 6.dp,
      modifier = Modifier
        .offset(x = brXDp - halfHandleDp, y = brYDp - halfHandleDp)
        .size(handleSizeDp + 4.dp) // slightly larger for touch ease
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { offset ->
              touchPointXPx = brX + offset.x
              touchPointYPx = brY + offset.y
              val dx = touchPointXPx - centerXPx
              val dy = touchPointYPx - centerYPx
              lastTouchDist = hypot(dx, dy).coerceAtLeast(10f)
              lastTouchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            },
            onDrag = { change, dragAmount ->
              change.consume()
              touchPointXPx += dragAmount.x
              touchPointYPx += dragAmount.y
              val dx = touchPointXPx - centerXPx
              val dy = touchPointYPx - centerYPx

              val currentDist = hypot(dx, dy).coerceAtLeast(10f)
              val currentAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

              if (lastTouchDist > 0f) {
                val deltaScale = currentDist / lastTouchDist
                val deltaRotation = currentAngle - lastTouchAngle
                onTransformHandleDrag(deltaScale, deltaRotation)
              }

              lastTouchDist = currentDist
              lastTouchAngle = currentAngle
            }
          )
        }
        .testTag("handle_transform_button")
    ) {
      Icon(
        Icons.Default.CropRotate,
        contentDescription = "Drag to Resize and Rotate",
        tint = Color.Black,
        modifier = Modifier
          .padding(4.dp)
          .fillMaxSize()
      )
    }
  }
}

