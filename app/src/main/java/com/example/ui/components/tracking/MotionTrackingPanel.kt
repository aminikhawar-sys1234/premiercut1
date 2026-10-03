package com.example.ui.components.tracking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.EffectType
import com.example.domain.model.VideoClip
import com.example.engine.ai.*
import com.example.ui.StudioViewModel
import kotlin.math.roundToInt

// Distinctive Blue + Green mixed studio aesthetic
private val BlueGreenDarkTop = Color(0xFF091E26)
private val BlueGreenDarkBottom = Color(0xFF06151B)
private val BlueGreenBorder = Color(0xFF133B44)
private val EmeraldAccent = Color(0xFF00D1B2)
private val CyanBlue = Color(0xFF00B4D8)
private val CardBg = Color(0xFF0D252E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)

@Composable
fun MotionTrackingPanel(
    viewModel: StudioViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.motionTrackingState.collectAsState()
    val activeClip = viewModel.getSelectedVideoClip()
    val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()

    Surface(
        color = BlueGreenDarkBottom,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, BlueGreenBorder),
        modifier = modifier.fillMaxWidth().wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(BlueGreenDarkTop, BlueGreenDarkBottom)
                    )
                )
                .navigationBarsPadding()
        ) {
            // 1. TOP HEADER ROW: Reset (Left) | Motion Tracking (Center) | Close X (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF071920))
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Reset
                TextButton(
                    onClick = { viewModel.resetMotionTracking() },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("motion_tracking_reset_button")
                ) {
                    Text(
                        text = "Reset",
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    )
                }

                // Center: Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrackChanges,
                        contentDescription = null,
                        tint = EmeraldAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Motion Tracking",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }

                // Right: Close X
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("motion_tracking_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Divider(color = BlueGreenBorder, thickness = 1.dp)

            // 2. MAIN CATEGORY ROW: Exactly 6 categories in ONE single horizontal row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF081C23))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackingCategory.values().forEach { cat ->
                    val isSelected = uiState.activeCategory == cat
                    CategoryPill(
                        category = cat,
                        isSelected = isSelected,
                        onClick = { viewModel.setTrackingCategory(cat) }
                    )
                }
            }

            Divider(color = BlueGreenBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

            // 3. TRACKING PROGRESS / STATUS BAR (if active)
            if (uiState.isTracking) {
                TrackingProgressBar(
                    progress = uiState.progress,
                    statusMessage = uiState.statusMessage,
                    onCancel = { viewModel.cancelMotionTracking() }
                )
            } else if (uiState.statusMessage.isNotBlank()) {
                Surface(
                    color = CardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            color = if (uiState.activeResult != null) EmeraldAccent else CyanBlue,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.activeResult != null) {
                            Text(
                                text = "${uiState.activeResult?.keyframes?.size ?: 0} keys",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // 4. CATEGORY CONTENT BODY (Scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                when (uiState.activeCategory) {
                    TrackingCategory.OBJECT -> ObjectCategoryContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        activeClip = activeClip
                    )
                    TrackingCategory.FACE -> FaceCategoryContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        activeClip = activeClip
                    )
                    TrackingCategory.BODY -> BodyCategoryContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        activeClip = activeClip
                    )
                    TrackingCategory.MOTION -> MotionCategoryContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        activeClip = activeClip
                    )
                    TrackingCategory.ATTACH -> AttachCategoryContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        activeClip = activeClip
                    )
                    TrackingCategory.CONTROLS -> ControlsCategoryContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        activeClip = activeClip,
                        currentPosMs = currentPosMs
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    category: TrackingCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) EmeraldAccent else CardBg,
        border = BorderStroke(
            1.dp,
            if (isSelected) EmeraldAccent else BlueGreenBorder
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("motion_category_${category.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = category.iconEmoji,
                fontSize = 11.5.sp
            )
            Text(
                text = category.title,
                color = if (isSelected) Color.Black else TextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun TrackingProgressBar(
    progress: Float,
    statusMessage: String,
    onCancel: () -> Unit
) {
    val percent = (progress.coerceIn(0f, 1f) * 100).roundToInt()
    val displayMessage = if (statusMessage.isNotBlank()) {
        if (statusMessage.contains("%")) statusMessage else "$statusMessage $percent%"
    } else {
        "Tracking... $percent%"
    }

    Surface(
        color = CardBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    CircularProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.size(13.dp),
                        color = EmeraldAccent,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = displayMessage,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("Cancel", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = EmeraldAccent,
                trackColor = Color(0xFF071419)
            )
        }
    }
}

// -------------------------------------------------------------------------
// 1. OBJECT CATEGORY CONTENT
// -------------------------------------------------------------------------
@Composable
private fun ObjectCategoryContent(
    viewModel: StudioViewModel,
    uiState: MotionTrackingUiState,
    activeClip: VideoClip?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Select an object using the green box on the video preview, then track forward or across the full clip.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        // Region Size Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RegionSizeChip("Small (16%)", uiState.targetRegion == NormalizedRect.SMALL_CENTER) {
                viewModel.updateTrackingRegion(NormalizedRect.SMALL_CENTER)
            }
            RegionSizeChip("Medium (30%)", uiState.targetRegion == NormalizedRect.DEFAULT_CENTER) {
                viewModel.updateTrackingRegion(NormalizedRect.DEFAULT_CENTER)
            }
            RegionSizeChip("Large (50%)", uiState.targetRegion == NormalizedRect.LARGE_CENTER) {
                viewModel.updateTrackingRegion(NormalizedRect.LARGE_CENTER)
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (activeClip != null) {
                        viewModel.startMotionTracking(TrackingCategory.OBJECT)
                    }
                },
                enabled = !uiState.isTracking && activeClip != null,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("start_object_tracking_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Track Object", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    viewModel.retrackCurrentTarget()
                },
                enabled = !uiState.isTracking && uiState.activeResult != null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanBlue),
                border = BorderStroke(1.dp, CyanBlue.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("retrack_object_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Re-track", fontSize = 12.sp)
            }
        }

        if (activeClip == null) {
            Text(
                text = "⚠️ Please add or select a video clip on the timeline first.",
                color = Color(0xFFFFB74D),
                fontSize = 11.sp
            )
        }
    }
}

// -------------------------------------------------------------------------
// 2. FACE CATEGORY CONTENT
// -------------------------------------------------------------------------
@Composable
private fun FaceCategoryContent(
    viewModel: StudioViewModel,
    uiState: MotionTrackingUiState,
    activeClip: VideoClip?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.startMotionTracking(TrackingCategory.FACE) },
            enabled = !uiState.isTracking && activeClip != null,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .testTag("start_face_tracking_button")
        ) {
            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Detect & Track Face", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        // Quick Face Actions (Blur Face, Mosaic Face, Face Sticker)
        Text(
            text = "Quick Face Applications",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickToolButton(
                icon = Icons.Default.Face,
                label = "AR Filters",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setActiveToolbarTab(com.example.ui.EditorToolbarTab.AR_EFFECTS) }
            )
            QuickToolButton(
                icon = Icons.Default.BlurOn,
                label = "Face Blur",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.attachFaceEffect(EffectType.BLUR) }
            )
            QuickToolButton(
                icon = Icons.Default.GridOn,
                label = "Face Mosaic",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.attachFaceEffect(EffectType.MOSAIC) }
            )
            QuickToolButton(
                icon = Icons.Default.EmojiEmotions,
                label = "Face Sticker",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.attachTrackingToLayer(AttachmentTarget.STICKER) }
            )
        }
    }
}

// -------------------------------------------------------------------------
// 3. BODY CATEGORY CONTENT
// -------------------------------------------------------------------------
@Composable
private fun BodyCategoryContent(
    viewModel: StudioViewModel,
    uiState: MotionTrackingUiState,
    activeClip: VideoClip?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Real 33-point body and pose tracking with limb classification (Full Body, Upper Body, Arms, Legs).",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Button(
            onClick = { viewModel.startMotionTracking(TrackingCategory.BODY) },
            enabled = !uiState.isTracking && activeClip != null,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .testTag("start_body_tracking_button")
        ) {
            Icon(Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Track Body & Pose", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickToolButton(
                icon = Icons.Default.Person,
                label = "Full Body",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.startMotionTracking(TrackingCategory.BODY) }
            )
            QuickToolButton(
                icon = Icons.Default.SportsHandball,
                label = "Upper Body",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.startMotionTracking(TrackingCategory.BODY) }
            )
            QuickToolButton(
                icon = Icons.Default.BlurCircular,
                label = "Body Blur",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.attachFaceEffect(EffectType.BLUR) }
            )
        }
    }
}

// -------------------------------------------------------------------------
// 4. MOTION CATEGORY CONTENT
// -------------------------------------------------------------------------
@Composable
private fun MotionCategoryContent(
    viewModel: StudioViewModel,
    uiState: MotionTrackingUiState,
    activeClip: VideoClip?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Feature point and multi-point motion tracking to compute translational movement, angular rotation, and zoom scale.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Button(
            onClick = { viewModel.startMotionTracking(TrackingCategory.MOTION) },
            enabled = !uiState.isTracking && activeClip != null,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .testTag("start_point_motion_tracking_button")
        ) {
            Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Track Feature Motion", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickToolButton(
                icon = Icons.Default.Adjust,
                label = "Single Point",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.startMotionTracking(TrackingCategory.MOTION) }
            )
            QuickToolButton(
                icon = Icons.Default.Grain,
                label = "Multi-Point",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.startMotionTracking(TrackingCategory.MOTION) }
            )
            QuickToolButton(
                icon = Icons.Default.Timeline,
                label = "Motion Path",
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleMotionPathVisibility() }
            )
        }
    }
}

// -------------------------------------------------------------------------
// 5. ATTACH CATEGORY CONTENT
// -------------------------------------------------------------------------
@Composable
private fun AttachCategoryContent(
    viewModel: StudioViewModel,
    uiState: MotionTrackingUiState,
    activeClip: VideoClip?
) {
    var selectedTarget by remember { mutableStateOf(AttachmentTarget.TEXT) }
    var followPos by remember { mutableStateOf(uiState.settings.followPosition) }
    var followScale by remember { mutableStateOf(uiState.settings.followScale) }
    var followRot by remember { mutableStateOf(uiState.settings.followRotation) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Attach tracked motion path to editor layers. The attached element will follow in preview and export.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        // Target Selector Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AttachmentTarget.values().forEach { target ->
                val isSel = selectedTarget == target
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSel) EmeraldAccent else CardBg,
                    border = BorderStroke(1.dp, if (isSel) EmeraldAccent else BlueGreenBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { selectedTarget = target }
                ) {
                    Text(
                        text = target.title,
                        color = if (isSel) Color.Black else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Transform Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = followPos,
                    onCheckedChange = { followPos = it },
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldAccent)
                )
                Text("Position", color = TextPrimary, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = followScale,
                    onCheckedChange = { followScale = it },
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldAccent)
                )
                Text("Scale", color = TextPrimary, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = followRot,
                    onCheckedChange = { followRot = it },
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldAccent)
                )
                Text("Rotation", color = TextPrimary, fontSize = 11.sp)
            }
        }

        // Attach Button
        Button(
            onClick = {
                viewModel.attachTrackingToLayer(
                    target = selectedTarget,
                    followPos = followPos,
                    followScale = followScale,
                    followRot = followRot
                )
            },
            enabled = uiState.activeResult != null && !uiState.activeResult.isEmpty,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .testTag("apply_attachment_button")
        ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Attach to ${selectedTarget.title}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        if (uiState.activeResult == null || uiState.activeResult.isEmpty) {
            Text(
                text = "Track an object, face, or motion point first before attaching layers.",
                color = Color(0xFFFFB74D),
                fontSize = 10.5.sp
            )
        }
    }
}

// -------------------------------------------------------------------------
// 6. CONTROLS CATEGORY CONTENT
// -------------------------------------------------------------------------
@Composable
private fun ControlsCategoryContent(
    viewModel: StudioViewModel,
    uiState: MotionTrackingUiState,
    activeClip: VideoClip?,
    currentPosMs: Long
) {
    var smoothing by remember { mutableStateOf(uiState.settings.smoothingFactor) }
    var searchWindow by remember { mutableStateOf(uiState.settings.searchWindowFactor) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Smoothing Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Path Smoothing", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                Text("${(smoothing * 100).toInt()}%", color = EmeraldAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = smoothing,
                onValueChange = {
                    smoothing = it
                    viewModel.updateSmoothingFactor(it)
                },
                valueRange = 0f..0.9f,
                colors = SliderDefaults.colors(thumbColor = EmeraldAccent, activeTrackColor = EmeraldAccent)
            )
        }

        // Search Window Factor
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Search Window Factor", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                Text("${String.format("%.1f", searchWindow)}x", color = CyanBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = searchWindow,
                onValueChange = {
                    searchWindow = it
                    viewModel.updateSearchWindowFactor(it)
                },
                valueRange = 1.0f..4.0f,
                steps = 6,
                colors = SliderDefaults.colors(thumbColor = CyanBlue, activeTrackColor = CyanBlue)
            )
        }

        // Manual Correction / Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.manualCorrectionAtCurrentTime(currentPosMs)
                },
                enabled = uiState.activeResult != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF133B44), contentColor = TextPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) {
                Icon(Icons.Default.EditLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Set Keyframe", fontSize = 11.sp)
            }

            Button(
                onClick = { viewModel.deleteActiveTrackingData() },
                enabled = uiState.activeResult != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B1A1A), contentColor = Color(0xFFFF5252)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete Data", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RegionSizeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) EmeraldAccent else CardBg,
        border = BorderStroke(1.dp, if (isSelected) EmeraldAccent else BlueGreenBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun QuickToolButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BlueGreenBorder),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldAccent,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
