package com.example.ui.components.timeline

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.EffectClip
import com.example.domain.model.StickerClip
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.domain.model.TrackHeight
import com.example.domain.model.TrackSettings
import com.example.domain.model.TrackType
import com.example.domain.model.VideoClip
import com.example.engine.SelectedTrackElement
import com.example.ui.theme.*

/**
 * Unified Layer Representation for Z-Index ordering in the Layers Manager.
 */
sealed class LayerItem {
  abstract val id: String
  abstract val name: String
  abstract val trackType: TrackType
  abstract val isLocked: Boolean
  abstract val isHidden: Boolean

  data class TextLayer(val clip: TextClip) : LayerItem() {
    override val id: String = clip.id
    override val name: String = if (clip.text.isNotBlank()) clip.text else "Text Layer"
    override val trackType: TrackType = TrackType.TEXT
    override val isLocked: Boolean = clip.isLocked
    override val isHidden: Boolean = clip.isHidden
  }

  data class StickerLayer(val clip: StickerClip) : LayerItem() {
    override val id: String = clip.id
    override val name: String = clip.badgeType?.name ?: "Sticker Layer"
    override val trackType: TrackType = TrackType.STICKER
    override val isLocked: Boolean = clip.isLocked
    override val isHidden: Boolean = clip.isHidden
  }

  data class OverlayLayer(val clip: VideoClip) : LayerItem() {
    override val id: String = clip.id
    override val name: String = clip.name
    override val trackType: TrackType = TrackType.OVERLAY
    override val isLocked: Boolean = clip.isLocked
    override val isHidden: Boolean = clip.isHidden
  }

  data class VideoLayer(val clip: VideoClip) : LayerItem() {
    override val id: String = clip.id
    override val name: String = clip.name
    override val trackType: TrackType = TrackType.MAIN_VIDEO
    override val isLocked: Boolean = clip.isLocked
    override val isHidden: Boolean = clip.isHidden
  }

  data class EffectLayer(val clip: EffectClip) : LayerItem() {
    override val id: String = clip.id
    override val name: String = clip.customName.ifEmpty { clip.effectType.displayName }
    override val trackType: TrackType = TrackType.EFFECT
    override val isLocked: Boolean = clip.isLocked
    override val isHidden: Boolean = clip.isHidden
  }
}

/**
 * Dedicated Multi-Layer Manager & Track Inspector Drawer for AH Video Studio.
 *
 * Supports:
 * - Full visual Z-index layer order representation (Text, Sticker, Overlay, Video, FX).
 * - Individual layer selection, reordering (Bring Forward, Send Backward, Bring to Front, Send to Back).
 * - Individual visibility (Eye toggle) and Lock toggle.
 * - Single-tap duplicate and delete per layer.
 * - Track-level master Lock, Visibility, Mute, Solo, and Height controls.
 */
@Composable
fun LayersDrawer(
  timeline: Timeline,
  selectedElement: SelectedTrackElement = SelectedTrackElement.None,
  onSelectElement: (SelectedTrackElement) -> Unit = {},
  onBringLayerForward: (String) -> Unit = {},
  onSendLayerBackward: (String) -> Unit = {},
  onBringLayerToFront: (String) -> Unit = {},
  onSendLayerToBack: (String) -> Unit = {},
  onToggleClipLock: (String) -> Unit = {},
  onToggleClipHide: (String) -> Unit = {},
  onDuplicateClip: (String) -> Unit = {},
  onDeleteClip: (String) -> Unit = {},
  onToggleTrackLock: (TrackType) -> Unit = {},
  onToggleTrackHide: (TrackType) -> Unit = {},
  onToggleTrackMute: (TrackType) -> Unit = {},
  onToggleTrackSolo: (TrackType) -> Unit = {},
  onCycleTrackHeight: (TrackType) -> Unit = {},
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  var activeTab by remember { mutableIntStateOf(0) }

  // Build Z-Index Layer Stack (Top-to-Bottom: Text -> Sticker -> Overlay -> FX -> Main Video)
  val allLayers = remember(timeline) {
    val list = mutableListOf<LayerItem>()
    // Text layers are rendered on top
    timeline.textClips.reversed().forEach { list.add(LayerItem.TextLayer(it)) }
    // Sticker layers
    timeline.stickerClips.reversed().forEach { list.add(LayerItem.StickerLayer(it)) }
    // Overlay (PIP) layers
    timeline.overlayClips.reversed().forEach { list.add(LayerItem.OverlayLayer(it)) }
    // Effect layers
    timeline.effectClips.reversed().forEach { list.add(LayerItem.EffectLayer(it)) }
    // Main video clips
    timeline.videoClips.reversed().forEach { list.add(LayerItem.VideoLayer(it)) }
    list
  }

  val trackTypesList = listOf(
    TrackType.TEXT,
    TrackType.STICKER,
    TrackType.OVERLAY,
    TrackType.MAIN_VIDEO,
    TrackType.AUDIO,
    TrackType.EFFECT
  )

  Surface(
    modifier = modifier
      .width(320.dp)
      .fillMaxHeight()
      .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
      .border(1.dp, StudioBorder, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)),
    color = StudioSurface,
    tonalElevation = 8.dp,
    shadowElevation = 16.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      // Drawer Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(SkyBlueContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Layers,
              contentDescription = "Layers Manager",
              tint = CyanAccent,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Multi-Layer Studio",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp
              )
            )
            Text(
              text = "${allLayers.size} Layers (${allLayers.count { !it.isHidden }} Visible)",
              style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 11.sp
              )
            )
          }
        }

        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(32.dp)
            .testTag("layers_drawer_close_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Layers",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      // Tab switcher: Z-Index Layers vs Track Controls
      TabRow(
        selectedTabIndex = activeTab,
        containerColor = StudioDarkBg,
        contentColor = CyanAccent,
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .padding(bottom = 8.dp)
      ) {
        Tab(
          selected = activeTab == 0,
          onClick = { activeTab = 0 },
          text = {
            Text(
              text = "Z-Index Layers (${allLayers.size})",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
          }
        )
        Tab(
          selected = activeTab == 1,
          onClick = { activeTab = 1 },
          text = {
            Text(
              text = "Track Controls",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
          }
        )
      }

      HorizontalDivider(color = StudioBorder, thickness = 1.dp)
      Spacer(modifier = Modifier.height(8.dp))

      if (activeTab == 0) {
        // Tab 0: Individual Layer Cards sorted by Z-Index (Top to Bottom)
        if (allLayers.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                Icons.Default.LayersClear,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(40.dp)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "No active layers on canvas",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            items(allLayers, key = { it.id }) { layer ->
              val isSelected = when (selectedElement) {
                is SelectedTrackElement.Text -> (layer is LayerItem.TextLayer) && selectedElement.clipId == layer.id
                is SelectedTrackElement.Sticker -> (layer is LayerItem.StickerLayer) && selectedElement.clipId == layer.id
                is SelectedTrackElement.Overlay -> (layer is LayerItem.OverlayLayer) && selectedElement.clipId == layer.id
                is SelectedTrackElement.Video -> (layer is LayerItem.VideoLayer) && selectedElement.clipId == layer.id
                is SelectedTrackElement.Effect -> (layer is LayerItem.EffectLayer) && selectedElement.clipId == layer.id
                else -> false
              }

              IndividualLayerRow(
                layer = layer,
                isSelected = isSelected,
                onSelect = {
                  val element = when (layer) {
                    is LayerItem.TextLayer -> SelectedTrackElement.Text(layer.id)
                    is LayerItem.StickerLayer -> SelectedTrackElement.Sticker(layer.id)
                    is LayerItem.OverlayLayer -> SelectedTrackElement.Overlay(layer.id)
                    is LayerItem.VideoLayer -> SelectedTrackElement.Video(layer.id)
                    is LayerItem.EffectLayer -> SelectedTrackElement.Effect(layer.id)
                  }
                  onSelectElement(element)
                },
                onBringForward = { onBringLayerForward(layer.id) },
                onSendBackward = { onSendLayerBackward(layer.id) },
                onBringToFront = { onBringLayerToFront(layer.id) },
                onSendToBack = { onSendLayerToBack(layer.id) },
                onToggleLock = { onToggleClipLock(layer.id) },
                onToggleHide = { onToggleClipHide(layer.id) },
                onDuplicate = { onDuplicateClip(layer.id) },
                onDelete = { onDeleteClip(layer.id) }
              )
            }
          }
        }
      } else {
        // Tab 1: Master Track Controls
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(trackTypesList) { trackType ->
            val settings = timeline.trackSettings[trackType] ?: TrackSettings(trackType)
            LayerCardItem(
              trackType = trackType,
              settings = settings,
              clipCount = when (trackType) {
                TrackType.MAIN_VIDEO -> timeline.videoClips.size
                TrackType.OVERLAY, TrackType.ADJUSTMENT, TrackType.ELEMENT -> timeline.overlayClips.size
                TrackType.TEXT, TrackType.CAPTION -> timeline.textClips.size
                TrackType.AUDIO, TrackType.MUSIC, TrackType.SFX -> timeline.audioClips.size
                TrackType.STICKER -> timeline.stickerClips.size
                TrackType.EFFECT -> timeline.effectClips.size
                TrackType.SHAPE -> timeline.shapeClips.size
              },
              onToggleLock = { onToggleTrackLock(trackType) },
              onToggleHide = { onToggleTrackHide(trackType) },
              onToggleMute = { onToggleTrackMute(trackType) },
              onToggleSolo = { onToggleTrackSolo(trackType) },
              onCycleHeight = { onCycleTrackHeight(trackType) }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Bottom Note / Quick dismiss tip
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = StudioSurfaceVariant
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Default.TouchApp,
            contentDescription = null,
            tint = CyanAccent,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Tap any layer to make it active on canvas.",
            style = MaterialTheme.typography.labelSmall.copy(
              color = TextSecondary,
              fontSize = 11.sp
            )
          )
        }
      }
    }
  }
}

/**
 * Individual Interactive Layer Card with quick action buttons.
 */
@Composable
private fun IndividualLayerRow(
  layer: LayerItem,
  isSelected: Boolean,
  onSelect: () -> Unit,
  onBringForward: () -> Unit,
  onSendBackward: () -> Unit,
  onBringToFront: () -> Unit,
  onSendToBack: () -> Unit,
  onToggleLock: () -> Unit,
  onToggleHide: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit
) {
  val (trackLabel, icon, badgeColor) = when (layer) {
    is LayerItem.TextLayer -> Triple("TEXT", Icons.Default.TextFields, TextTrackColor)
    is LayerItem.StickerLayer -> Triple("STICKER", Icons.Default.EmojiEmotions, StickerTrackColor)
    is LayerItem.OverlayLayer -> Triple("PIP", Icons.Default.Layers, OverlayTrackColor)
    is LayerItem.VideoLayer -> Triple("VIDEO", Icons.Default.Movie, VideoTrackColor)
    is LayerItem.EffectLayer -> Triple("FX", Icons.Default.AutoFixHigh, EffectTrackColor)
  }

  val backgroundColor by animateColorAsState(
    if (isSelected) SkyBlueContainer.copy(alpha = 0.5f) else Color.White,
    label = "layerBg"
  )

  val scale by animateFloatAsState(
    targetValue = if (isSelected) 1.025f else 1.0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "layerScale"
  )

  val borderWidth by animateDpAsState(
    targetValue = if (isSelected) 2.dp else 0.5.dp,
    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    label = "layerBorderWidth"
  )

  val borderColor by animateColorAsState(
    targetValue = if (isSelected) CyanAccent else StudioBorder,
    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    label = "layerBorderColor"
  )

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(10.dp))
      .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
      .clickable(onClick = onSelect)
      .testTag("layer_item_${layer.id}"),
    color = backgroundColor,
    tonalElevation = if (isSelected) 6.dp else 1.dp,
    shadowElevation = if (isSelected) 4.dp else 0.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          // Track Type Badge
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(badgeColor)
              .padding(horizontal = 5.dp, vertical = 2.dp)
          ) {
            Text(
              text = trackLabel,
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            )
          }

          Spacer(modifier = Modifier.width(6.dp))

          // Layer Title
          Text(
            text = layer.name,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (layer.isHidden) TextTertiary else TextPrimary,
              fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        // Lock / Hide Status Icons
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Lock Button
          IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(24.dp).testTag("layer_lock_${layer.id}")
          ) {
            Icon(
              imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
              contentDescription = "Lock",
              tint = if (layer.isLocked) RedAccent else TextSecondary,
              modifier = Modifier.size(14.dp)
            )
          }

          // Hide / Eye Button
          IconButton(
            onClick = onToggleHide,
            modifier = Modifier.size(24.dp).testTag("layer_hide_${layer.id}")
          ) {
            Icon(
              imageVector = if (layer.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = "Visibility",
              tint = if (layer.isHidden) AmberAccent else TextSecondary,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }

      // Quick Actions Row (Reorder Up/Down, Duplicate, Delete)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          // Bring Forward
          IconButton(
            onClick = onBringForward,
            modifier = Modifier.size(22.dp).testTag("layer_up_${layer.id}")
          ) {
            Icon(
              imageVector = Icons.Default.ArrowUpward,
              contentDescription = "Bring Forward",
              tint = TextSecondary,
              modifier = Modifier.size(13.dp)
            )
          }

          // Send Backward
          IconButton(
            onClick = onSendBackward,
            modifier = Modifier.size(22.dp).testTag("layer_down_${layer.id}")
          ) {
            Icon(
              imageVector = Icons.Default.ArrowDownward,
              contentDescription = "Send Backward",
              tint = TextSecondary,
              modifier = Modifier.size(13.dp)
            )
          }

          // Bring to Front
          IconButton(
            onClick = onBringToFront,
            modifier = Modifier.size(22.dp).testTag("layer_top_${layer.id}")
          ) {
            Icon(
              imageVector = Icons.Default.VerticalAlignTop,
              contentDescription = "Bring to Top",
              tint = TextSecondary,
              modifier = Modifier.size(13.dp)
            )
          }

          // Send to Back
          IconButton(
            onClick = onSendToBack,
            modifier = Modifier.size(22.dp).testTag("layer_bottom_${layer.id}")
          ) {
            Icon(
              imageVector = Icons.Default.VerticalAlignBottom,
              contentDescription = "Send to Bottom",
              tint = TextSecondary,
              modifier = Modifier.size(13.dp)
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          // Duplicate
          IconButton(
            onClick = onDuplicate,
            modifier = Modifier.size(22.dp).testTag("layer_duplicate_${layer.id}")
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Duplicate Layer",
              tint = TextSecondary,
              modifier = Modifier.size(13.dp)
            )
          }

          // Delete
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(22.dp).testTag("layer_delete_${layer.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Delete Layer",
              tint = RedAccent,
              modifier = Modifier.size(13.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LayerCardItem(
  trackType: TrackType,
  settings: TrackSettings,
  clipCount: Int,
  onToggleLock: () -> Unit,
  onToggleHide: () -> Unit,
  onToggleMute: () -> Unit,
  onToggleSolo: () -> Unit,
  onCycleHeight: () -> Unit
) {
  val (label, icon, color) = when (trackType) {
    TrackType.MAIN_VIDEO -> Triple("V1 Main", Icons.Default.Movie, VideoTrackColor)
    TrackType.OVERLAY -> Triple("V2 Overlay", Icons.Default.Layers, OverlayTrackColor)
    TrackType.TEXT -> Triple("T1 Subtitle", Icons.Default.TextFields, TextTrackColor)
    TrackType.CAPTION -> Triple("C1 Caption", Icons.Default.TextFields, TextTrackColor)
    TrackType.AUDIO -> Triple("A1 Master", Icons.Default.Audiotrack, AudioTrackColor)
    TrackType.MUSIC -> Triple("M1 Music", Icons.Default.MusicNote, AudioTrackColor)
    TrackType.SFX -> Triple("FX Sound", Icons.Default.GraphicEq, AudioTrackColor)
    TrackType.STICKER -> Triple("S1 Sticker", Icons.Default.EmojiEmotions, StickerTrackColor)
    TrackType.EFFECT -> Triple("FX Filter", Icons.Default.AutoFixHigh, EffectTrackColor)
    TrackType.ADJUSTMENT -> Triple("ADJ Layer", Icons.Default.Tune, EffectTrackColor)
    TrackType.ELEMENT -> Triple("EL Element", Icons.Default.Widgets, StickerTrackColor)
    TrackType.SHAPE -> Triple("SH Shape", Icons.Default.Widgets, StickerTrackColor)
  }

  val hasAudio = trackType.isAudioTrack || trackType == TrackType.MAIN_VIDEO || trackType == TrackType.OVERLAY
  val hasVisual = !trackType.isAudioTrack

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .border(0.5.dp, StudioBorder, RoundedCornerShape(10.dp)),
    color = Color.White,
    tonalElevation = 2.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Track Info
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
          Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              fontSize = 12.sp
            )
          )
          Text(
            text = "$clipCount clips",
            style = MaterialTheme.typography.labelSmall.copy(
              color = TextSecondary,
              fontSize = 10.sp
            )
          )
        }
      }

      // Action Control Buttons
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        // Lock Button
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (settings.isLocked) RedAccent.copy(alpha = 0.2f) else Color.White)
            .border(0.5.dp, StudioBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onToggleLock)
            .testTag("drawer_track_lock_${trackType.name.lowercase()}"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (settings.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = "Lock Track",
            tint = if (settings.isLocked) RedAccent else TextSecondary,
            modifier = Modifier.size(15.dp)
          )
        }

        // Hide Button (Eye)
        if (hasVisual) {
          Box(
            modifier = Modifier
              .size(30.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(if (settings.isHidden) AmberAccent.copy(alpha = 0.2f) else Color.White)
              .border(0.5.dp, StudioBorder, RoundedCornerShape(6.dp))
              .clickable(onClick = onToggleHide)
              .testTag("drawer_track_hide_${trackType.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (settings.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = "Hide Track",
              tint = if (settings.isHidden) AmberAccent else TextSecondary,
              modifier = Modifier.size(15.dp)
            )
          }
        }

        // Mute Button
        if (hasAudio) {
          Box(
            modifier = Modifier
              .size(30.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(if (settings.isMuted) RedAccent.copy(alpha = 0.2f) else Color.White)
              .border(0.5.dp, StudioBorder, RoundedCornerShape(6.dp))
              .clickable(onClick = onToggleMute)
              .testTag("drawer_track_mute_${trackType.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (settings.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
              contentDescription = "Mute Track",
              tint = if (settings.isMuted) RedAccent else TextSecondary,
              modifier = Modifier.size(15.dp)
            )
          }

          // Solo Button
          Box(
            modifier = Modifier
              .size(30.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(if (settings.isSolo) SkyBlueContainer else Color.White)
              .border(0.5.dp, if (settings.isSolo) CyanAccent else StudioBorder, RoundedCornerShape(6.dp))
              .clickable(onClick = onToggleSolo)
              .testTag("drawer_track_solo_${trackType.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "S",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (settings.isSolo) CyanAccent else TextTertiary
              )
            )
          }
        }

        // Height Cycle Button
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(0.5.dp, StudioBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onCycleHeight)
            .testTag("drawer_track_height_${trackType.name.lowercase()}"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = when (settings.height) {
              TrackHeight.COMPACT -> Icons.Default.UnfoldMore
              TrackHeight.NORMAL -> Icons.Default.Height
              TrackHeight.EXPANDED -> Icons.Default.UnfoldLess
            },
            contentDescription = "Track Height",
            tint = TextSecondary,
            modifier = Modifier.size(15.dp)
          )
        }
      }
    }
  }
}
