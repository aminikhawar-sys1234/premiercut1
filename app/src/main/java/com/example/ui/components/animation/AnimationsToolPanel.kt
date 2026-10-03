package com.example.ui.components.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*
import com.example.engine.SelectedTrackElement
import com.example.ui.EditorToolbarTab
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import kotlin.math.*

/**
 * Normal animation tabs: In, Out, Combo, Loop
 */
enum class NormalAnimationTab(
    val label: String,
    val icon: ImageVector
) {
    IN("In", Icons.Default.Login),
    OUT("Out", Icons.Default.Logout),
    COMBO("Combo", Icons.Default.AutoAwesome),
    LOOP("Loop", Icons.Default.Sync)
}

// Unified Animatable Target Model
sealed class AnimatableLayer {
    abstract val id: String
    abstract val name: String
    abstract val durationMs: Long
    abstract val startMs: Long

    data class VideoLayer(val clip: VideoClip) : AnimatableLayer() {
        override val id: String get() = clip.id
        override val name: String get() = if (clip.isVideo) "Video: ${clip.name}" else "Photo: ${clip.name}"
        override val durationMs: Long get() = clip.durationMs
        override val startMs: Long get() = clip.timelineStartMs
    }

    data class OverlayLayer(val clip: VideoClip) : AnimatableLayer() {
        override val id: String get() = clip.id
        override val name: String get() = "Overlay: ${clip.name}"
        override val durationMs: Long get() = clip.durationMs
        override val startMs: Long get() = clip.timelineStartMs
    }

    data class TextLayer(val clip: TextClip) : AnimatableLayer() {
        override val id: String get() = clip.id
        override val name: String get() = "Text: \"${clip.text.take(14)}\""
        override val durationMs: Long get() = clip.durationMs
        override val startMs: Long get() = clip.timelineStartMs
    }

    data class StickerLayer(val clip: StickerClip) : AnimatableLayer() {
        override val id: String get() = clip.id
        override val name: String get() = if (clip.badgeType != null) "Badge: ${clip.badgeType.displayName}" else if (clip.elementCategory != null) "Shape / Element" else "Sticker Layer"
        override val durationMs: Long get() = clip.durationMs
        override val startMs: Long get() = clip.timelineStartMs
    }
}

private val CardBg = Color(0xFF0D1B2E)
private val CardBgSelected = Color(0xFF133254)
private val ControlSectionBg = Color(0xFF0A1628)

/**
 * Unified Animation Tools Panel for AH Studio.
 *
 * EXACTLY ONE THIN HEADER:
 * Left: Normal Animation | Center: Advanced Animation | Right: ✕ Close
 *
 * - Height: fills the ~45% bottom panel area without empty/marked areas.
 * - Removes extra duplicate selected-item info bar.
 * - Seamlessly toggles between Normal Animation and Advanced Animation.
 */
@Composable
fun AnimationsToolPanel(
    viewModel: StudioViewModel,
    initialIsAdvanced: Boolean = false,
    onClose: () -> Unit = { viewModel.setActiveToolbarTab(null) },
    modifier: Modifier = Modifier
) {
    val timeline by viewModel.timelineEngine.timeline.collectAsState()
    val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
    val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()

    var isAdvanced by remember { mutableStateOf(initialIsAdvanced) }

    LaunchedEffect(initialIsAdvanced) {
        isAdvanced = initialIsAdvanced
    }

    // Resolve active layer dynamically across ALL supported object types (No sticker-only bias!)
    val activeLayer = remember(selectedElement, timeline, currentPosMs) {
        when (selectedElement) {
            is SelectedTrackElement.Video -> {
                timeline.videoClips.find { it.id == (selectedElement as SelectedTrackElement.Video).clipId }
                    ?.let { AnimatableLayer.VideoLayer(it) }
            }
            is SelectedTrackElement.Overlay -> {
                timeline.overlayClips.find { it.id == (selectedElement as SelectedTrackElement.Overlay).clipId }
                    ?.let { AnimatableLayer.OverlayLayer(it) }
            }
            is SelectedTrackElement.Text -> {
                timeline.textClips.find { it.id == (selectedElement as SelectedTrackElement.Text).clipId }
                    ?.let { AnimatableLayer.TextLayer(it) }
            }
            is SelectedTrackElement.Sticker -> {
                timeline.stickerClips.find { it.id == (selectedElement as SelectedTrackElement.Sticker).clipId }
                    ?.let { AnimatableLayer.StickerLayer(it) }
            }
            else -> {
                // Fallback priority: Active main video at playhead -> Any video clip -> Text -> Overlay -> Sticker
                val activeVideo = timeline.videoClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
                    ?: timeline.videoClips.firstOrNull()
                if (activeVideo != null) {
                    AnimatableLayer.VideoLayer(activeVideo)
                } else {
                    val topText = timeline.textClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
                    if (topText != null) {
                        AnimatableLayer.TextLayer(topText)
                    } else {
                        val topOverlay = timeline.overlayClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
                        if (topOverlay != null) {
                            AnimatableLayer.OverlayLayer(topOverlay)
                        } else {
                            val topSticker = timeline.stickerClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
                            topSticker?.let { AnimatableLayer.StickerLayer(it) }
                        }
                    }
                }
            }
        }
    }

    val currentAnimation = remember(activeLayer, timeline) {
        if (activeLayer == null) ClipAnimationSettings()
        else viewModel.timelineEngine.getClipAnimationSettings(activeLayer.id)
    }

    var activeTab by remember { mutableStateOf(NormalAnimationTab.IN) }
    var inDurationMs by remember { mutableLongStateOf(500L) }
    var outDurationMs by remember { mutableLongStateOf(500L) }
    var animSpeed by remember { mutableFloatStateOf(1.0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060B16))
            .navigationBarsPadding()
            .testTag("unified_animations_panel")
    ) {
        // -----------------------------------------------------------------------------------------
        // 1. ONE SINGLE THIN HEADER (~36dp compact height)
        // Left: Normal Animation | Center: Advanced Animation | Right: ✕ Close
        // -----------------------------------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF091220))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left & Center: Normal Animation & Advanced Animation Tabs
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left: Normal Animation Button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (!isAdvanced) SkyBlue else Color(0xFF132035),
                    border = BorderStroke(1.dp, if (!isAdvanced) SkyBlue else Color(0xFF223550)),
                    modifier = Modifier
                        .clickable { isAdvanced = false }
                        .testTag("header_normal_animation_tab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Animation,
                            contentDescription = null,
                            tint = if (!isAdvanced) Color.Black else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Normal Animation",
                            fontSize = 11.sp,
                            fontWeight = if (!isAdvanced) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isAdvanced) Color.Black else Color.White
                        )
                    }
                }

                // Center: Advanced Animation Button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isAdvanced) AccentCyan else Color(0xFF132035),
                    border = BorderStroke(1.dp, if (isAdvanced) AccentCyan else Color(0xFF223550)),
                    modifier = Modifier
                        .clickable { isAdvanced = true }
                        .testTag("header_advanced_animation_tab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isAdvanced) Color.Black else Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Advanced Animation",
                            fontSize = 11.sp,
                            fontWeight = if (isAdvanced) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAdvanced) Color.Black else Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // Right: ✕ Close Button
            IconButton(
                onClick = { onClose() },
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16253D))
                    .testTag("close_animations_panel_cross")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Animation Panel",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFF16253D))
        )

        // -----------------------------------------------------------------------------------------
        // 2. BODY CONTENT (Directly below single header without redundant info bars)
        // -----------------------------------------------------------------------------------------
        if (isAdvanced) {
            // ADVANCED ANIMATION SUITE
            AdvancedAnimationSuiteContent(
                viewModel = viewModel,
                activeLayer = activeLayer,
                currentPosMs = currentPosMs,
                modifier = Modifier.weight(1f)
            )
        } else {
            // NORMAL ANIMATION CONTROLS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // In | Out | Combo | Loop category bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0B172A))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NormalAnimationTab.values().forEach { tab ->
                        val isActive = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) SkyBlue else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 5.dp)
                                .testTag("normal_tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (isActive) Color.Black else TextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActive) Color.Black else TextSecondary
                                )
                            }
                        }
                    }
                }

                if (activeLayer != null) {
                    val targetId = activeLayer.id

                    // Duration / Speed quick bar
                    Surface(
                        color = ControlSectionBg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (activeTab) {
                                NormalAnimationTab.IN -> {
                                    Text(
                                        text = "Duration: ${String.format("%.1fs", inDurationMs / 1000f)}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Slider(
                                        value = (inDurationMs / 1000f).coerceIn(0.1f, 3.0f),
                                        onValueChange = { dur ->
                                            inDurationMs = (dur * 1000f).toLong()
                                            viewModel.timelineEngine.updateClipAnimation(targetId) {
                                                it.copy(inDurationMs = inDurationMs)
                                            }
                                        },
                                        valueRange = 0.1f..3.0f,
                                        colors = SliderDefaults.colors(thumbColor = SkyBlue, activeTrackColor = SkyBlue),
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(14.dp)
                                    )
                                }
                                NormalAnimationTab.OUT -> {
                                    Text(
                                        text = "Duration: ${String.format("%.1fs", outDurationMs / 1000f)}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Slider(
                                        value = (outDurationMs / 1000f).coerceIn(0.1f, 3.0f),
                                        onValueChange = { dur ->
                                            outDurationMs = (dur * 1000f).toLong()
                                            viewModel.timelineEngine.updateClipAnimation(targetId) {
                                                it.copy(outDurationMs = outDurationMs)
                                            }
                                        },
                                        valueRange = 0.1f..3.0f,
                                        colors = SliderDefaults.colors(thumbColor = SkyBlue, activeTrackColor = SkyBlue),
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(14.dp)
                                    )
                                }
                                NormalAnimationTab.COMBO, NormalAnimationTab.LOOP -> {
                                    Text(
                                        text = "Speed: ${String.format("%.1fx", animSpeed)}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Slider(
                                        value = animSpeed.coerceIn(0.5f, 3.0f),
                                        onValueChange = { spd ->
                                            animSpeed = spd
                                            viewModel.timelineEngine.updateClipAnimation(targetId) {
                                                it.copy(speed = animSpeed)
                                            }
                                        },
                                        valueRange = 0.5f..3.0f,
                                        colors = SliderDefaults.colors(thumbColor = SkyBlue, activeTrackColor = SkyBlue),
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(14.dp)
                                    )
                                }
                            }

                            // Reset button
                            TextButton(
                                onClick = {
                                    when (activeTab) {
                                        NormalAnimationTab.IN -> viewModel.timelineEngine.setClipInAnimation(targetId, InAnimationType.NONE)
                                        NormalAnimationTab.OUT -> viewModel.timelineEngine.setClipOutAnimation(targetId, OutAnimationType.NONE)
                                        NormalAnimationTab.COMBO, NormalAnimationTab.LOOP -> viewModel.timelineEngine.setClipComboAnimation(targetId, ComboAnimationType.NONE)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Reset", fontSize = 10.sp, color = RedAccent)
                            }
                        }
                    }

                    // 3. ANIMATION THUMBNAILS GRID (Applying animation to targetId)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        when (activeTab) {
                            NormalAnimationTab.IN -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(InAnimationType.values()) { type ->
                                        AnimationThumbnailCard(
                                            label = type.displayName,
                                            isSelected = currentAnimation.inType == type,
                                            onClick = {
                                                 viewModel.timelineEngine.updateClipAnimation(targetId) {
                                                     it.copy(inType = type, inDurationMs = inDurationMs)
                                                 }
                                            }
                                        ) {
                                            AnimatedInThumbnail(type = type)
                                        }
                                    }
                                }
                            }
                            NormalAnimationTab.OUT -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(OutAnimationType.values()) { type ->
                                        AnimationThumbnailCard(
                                            label = type.displayName,
                                            isSelected = currentAnimation.outType == type,
                                            onClick = {
                                                 viewModel.timelineEngine.updateClipAnimation(targetId) {
                                                     it.copy(outType = type, outDurationMs = outDurationMs)
                                                 }
                                            }
                                        ) {
                                            AnimatedOutThumbnail(type = type)
                                        }
                                    }
                                }
                            }
                            NormalAnimationTab.COMBO, NormalAnimationTab.LOOP -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(ComboAnimationType.values()) { type ->
                                        AnimationThumbnailCard(
                                            label = type.displayName,
                                            isSelected = currentAnimation.comboType == type,
                                            onClick = {
                                                 viewModel.timelineEngine.updateClipAnimation(targetId) {
                                                     it.copy(comboType = type, speed = animSpeed)
                                                 }
                                            }
                                        ) {
                                            AnimatedComboThumbnail(type = type)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieFilter,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Select an item to animate",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimationThumbnailCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) CardBgSelected else CardBg,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) SkyBlue else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("anim_card_${label.replace(" ", "_").lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) SkyBlue.copy(alpha = 0.25f) else Color(0xFF0F1B2C)),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) SkyBlue else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnimatedInThumbnail(type: InAnimationType) {
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    var scale = 1.0f
    var alpha = 1.0f
    var rot = 0f
    var transX = 0f
    var transY = 0f

    when (type) {
        InAnimationType.NONE -> alpha = 0.5f
        InAnimationType.FADE_IN -> alpha = progress
        InAnimationType.ZOOM_IN -> {
            scale = 0.2f + 0.8f * progress
            alpha = progress
        }
        InAnimationType.ZOOM_OUT -> {
            scale = 1.6f - 0.6f * progress
            alpha = progress
        }
        InAnimationType.SLIDE_UP -> {
            transY = (1f - progress) * 12f
            alpha = progress
        }
        InAnimationType.SLIDE_DOWN -> {
            transY = -(1f - progress) * 12f
            alpha = progress
        }
        InAnimationType.SLIDE_LEFT -> {
            transX = (1f - progress) * 12f
            alpha = progress
        }
        InAnimationType.SLIDE_RIGHT -> {
            transX = -(1f - progress) * 12f
            alpha = progress
        }
        InAnimationType.SPIN_IN -> {
            rot = (1f - progress) * 360f
            scale = progress
        }
        InAnimationType.BOUNCE_IN -> {
            scale = (1f - cos(progress * PI.toFloat() * 2f) * exp(-progress * 2.5f)).coerceIn(0f, 1.2f)
        }
        InAnimationType.POP_IN -> {
            scale = if (progress < 0.7f) progress / 0.7f * 1.2f else 1.2f - (progress - 0.7f) / 0.3f * 0.2f
        }
        InAnimationType.FLIP_X, InAnimationType.FLIP_Y -> {
            scale = abs(cos((1f - progress) * PI.toFloat() * 0.5f))
        }
        InAnimationType.SWING_IN -> {
            rot = sin((1f - progress) * 5f) * 20f
        }
        InAnimationType.ELASTIC_IN -> {
            val p = progress - 1f
            scale = (p * p * (2.7f * p + 1.7f) + 1f).coerceIn(0f, 1.3f)
        }
        InAnimationType.GLITCH_IN -> {
            transX = sin(progress * 20f) * 3f
            alpha = if ((progress * 10).toInt() % 2 == 0) 0.5f else 1f
        }
        InAnimationType.WIPE_IN, InAnimationType.BLUR_IN -> {
            alpha = progress
        }
    }

    if (type == InAnimationType.NONE) {
        Icon(Icons.Default.Block, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
    } else {
        Box(
            modifier = Modifier
                .size(15.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rot
                    translationX = transX
                    translationY = transY
                    this.alpha = alpha
                }
                .clip(RoundedCornerShape(3.dp))
                .background(SkyBlue)
        )
    }
}

@Composable
private fun AnimatedOutThumbnail(type: OutAnimationType) {
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    var scale = 1.0f
    var alpha = 1.0f
    var rot = 0f
    var transX = 0f
    var transY = 0f

    when (type) {
        OutAnimationType.NONE -> alpha = 0.5f
        OutAnimationType.FADE_OUT -> alpha = 1f - progress
        OutAnimationType.ZOOM_OUT -> {
            scale = 1f - 0.7f * progress
            alpha = 1f - progress
        }
        OutAnimationType.ZOOM_IN_OUT -> {
            scale = 1f + 0.8f * progress
            alpha = 1f - progress
        }
        OutAnimationType.SLIDE_UP_OUT -> {
            transY = -progress * 12f
            alpha = 1f - progress
        }
        OutAnimationType.SLIDE_DOWN_OUT -> {
            transY = progress * 12f
            alpha = 1f - progress
        }
        OutAnimationType.SLIDE_LEFT_OUT -> {
            transX = -progress * 12f
            alpha = 1f - progress
        }
        OutAnimationType.SLIDE_RIGHT_OUT -> {
            transX = progress * 12f
            alpha = 1f - progress
        }
        OutAnimationType.SPIN_OUT -> {
            rot = progress * 360f
            scale = 1f - progress
            alpha = 1f - progress
        }
        OutAnimationType.BOUNCE_OUT -> {
            scale = (1f - progress) * (1f + sin(progress * 6f) * 0.2f)
            alpha = 1f - progress
        }
        OutAnimationType.POP_OUT -> {
            scale = 1f - progress * 0.8f
            alpha = 1f - progress
        }
        OutAnimationType.FLIP_X_OUT -> {
            scale = abs(cos(progress * PI.toFloat() * 0.5f))
            alpha = 1f - progress
        }
        OutAnimationType.SWING_OUT -> {
            rot = sin(progress * 5f) * 20f
            alpha = 1f - progress
        }
        OutAnimationType.GLITCH_OUT -> {
            transX = sin(progress * 20f) * 3f
            alpha = (1f - progress)
        }
        OutAnimationType.WIPE_OUT, OutAnimationType.BLUR_OUT -> {
            alpha = 1f - progress
        }
    }

    if (type == OutAnimationType.NONE) {
        Icon(Icons.Default.Block, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
    } else {
        Box(
            modifier = Modifier
                .size(15.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rot
                    translationX = transX
                    translationY = transY
                    this.alpha = alpha
                }
                .clip(RoundedCornerShape(3.dp))
                .background(SkyBlue)
        )
    }
}

@Composable
private fun AnimatedComboThumbnail(type: ComboAnimationType) {
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    var scale = 1.0f
    var alpha = 1.0f
    var rot = 0f
    var transX = 0f
    var transY = 0f

    val cycle = progress * 2f * PI.toFloat()

    when (type) {
        ComboAnimationType.NONE -> alpha = 0.5f
        ComboAnimationType.PULSE -> scale = 1.0f + 0.2f * sin(cycle)
        ComboAnimationType.HEARTBEAT -> {
            val ph = progress
            val beat = if (ph < 0.2f) sin(ph / 0.2f * PI.toFloat()) * 0.3f else if (ph in 0.25f..0.45f) sin((ph - 0.25f) / 0.2f * PI.toFloat()) * 0.2f else 0f
            scale = 1.0f + beat
        }
        ComboAnimationType.PENDULUM -> rot = sin(cycle) * 18f
        ComboAnimationType.FLOAT -> {
            transY = sin(cycle) * 5f
            transX = cos(cycle) * 3f
        }
        ComboAnimationType.SHAKE -> {
            transX = sin(progress * 25f) * 3f
            transY = cos(progress * 25f) * 3f
            rot = sin(progress * 20f) * 6f
        }
        ComboAnimationType.JITTER -> {
            transX = ((progress * 13) % 1f - 0.5f) * 6f
            transY = ((progress * 17) % 1f - 0.5f) * 6f
        }
        ComboAnimationType.FLASH_PULSE -> alpha = 0.4f + 0.6f * (0.5f + 0.5f * sin(cycle))
        ComboAnimationType.WAVE -> {
            rot = sin(cycle) * 12f
            scale = 1.0f + sin(cycle) * 0.1f
        }
        ComboAnimationType.SPIN_360 -> rot = progress * 360f
        ComboAnimationType.BREATHE -> scale = 1.0f + sin(cycle) * 0.15f
        ComboAnimationType.ZOOM_PULSE -> scale = 0.9f + abs(sin(cycle)) * 0.25f
    }

    if (type == ComboAnimationType.NONE) {
        Icon(Icons.Default.Block, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
    } else {
        Box(
            modifier = Modifier
                .size(15.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rot
                    translationX = transX
                    translationY = transY
                    this.alpha = alpha
                }
                .clip(RoundedCornerShape(3.dp))
                .background(SkyBlue)
        )
    }
}
