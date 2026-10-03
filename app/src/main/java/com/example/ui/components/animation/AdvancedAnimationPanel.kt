package com.example.ui.components.animation

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import kotlin.math.*

/**
 * Main sections of the dedicated Advanced Animation panel:
 * Keyframe | Motion | Transform | Easing | Speed | Blur
 */
enum class AdvancedAnimationTab(
    val label: String,
    val icon: ImageVector,
    val description: String
) {
    KEYFRAME("Keyframe", Icons.Default.Diamond, "Manual Keyframe Timeline & Properties"),
    MOTION("Motion", Icons.Default.Route, "Custom Motion Path & Curves"),
    TRANSFORM("Transform", Icons.Default.Transform, "Advanced Transform, Skew & Perspective"),
    EASING("Easing", Icons.Default.ShowChart, "Professional Easing & Bezier Curves"),
    SPEED("Speed", Icons.Default.Speed, "Dynamic Speed Curves"),
    BLUR("Blur", Icons.Default.BlurOn, "Subframe Motion Blur & Shutter")
}

// Styling Constants
internal val AdvCardBg = Color(0xFF0B1728)
internal val AdvCardBgSelected = Color(0xFF102844)
internal val AdvControlSectionBg = Color(0xFF081322)
internal val AdvPanelBorder = Color(0xFF14243B)
internal val AccentCyan = Color(0xFF00C2FF)
internal val AccentEmerald = Color(0xFF00D1B2)

/**
 * Dedicated Advanced Animation Suite Content without redundant headers or info bars.
 * Cleanly renders the controls row: Keyframe | Motion | Transform | Easing | Speed | Blur
 * directly followed by the active advanced suite.
 */
@Composable
fun AdvancedAnimationSuiteContent(
    viewModel: StudioViewModel,
    activeLayer: AnimatableLayer?,
    currentPosMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(AdvancedAnimationTab.KEYFRAME) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }
    var showPresetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(notificationMessage) {
        if (notificationMessage != null) {
            kotlinx.coroutines.delay(2000)
            notificationMessage = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF040A12))
    ) {
        // Notification Banner
        AnimatedVisibility(visible = notificationMessage != null) {
            notificationMessage?.let { msg ->
                Surface(
                    color = AccentCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(13.dp))
                        Text(text = msg, fontSize = 11.sp, color = SkyBlueLight, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // 1. MAIN ADVANCED CONTROLS ROW: Keyframe | Motion | Transform | Easing | Speed | Blur + Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdvancedAnimationTab.values().forEach { tab ->
                val isSelected = activeTab == tab
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) AccentCyan else AdvCardBg,
                    border = BorderStroke(1.dp, if (isSelected) AccentCyan else AdvPanelBorder),
                    modifier = Modifier
                        .clickable { activeTab = tab }
                        .testTag("adv_tab_${tab.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.Black else TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else TextPrimary
                        )
                    }
                }
            }

            // Presets Bookmark Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0E223B),
                border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clickable { showPresetDialog = true }
                    .testTag("open_presets_dialog_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(text = "Presets", fontSize = 10.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Target Check & Tab Content
        if (activeLayer != null) {
            val targetId = activeLayer.id
            val targetDuration = activeLayer.durationMs
            val targetStart = activeLayer.startMs

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeTab) {
                    AdvancedAnimationTab.KEYFRAME -> {
                        AdvKeyframeTabContent(
                            targetId = targetId,
                            targetDurationMs = targetDuration,
                            targetStartMs = targetStart,
                            currentPosMs = currentPosMs,
                            viewModel = viewModel,
                            onNotify = { notificationMessage = it }
                        )
                    }
                    AdvancedAnimationTab.MOTION -> {
                        AdvMotionPathTabContent(
                            targetId = targetId,
                            targetDurationMs = targetDuration,
                            targetStartMs = targetStart,
                            currentPosMs = currentPosMs,
                            viewModel = viewModel,
                            onNotify = { notificationMessage = it }
                        )
                    }
                    AdvancedAnimationTab.TRANSFORM -> {
                        AdvTransformTabContent(
                            targetId = targetId,
                            targetDurationMs = targetDuration,
                            targetStartMs = targetStart,
                            currentPosMs = currentPosMs,
                            viewModel = viewModel,
                            onNotify = { notificationMessage = it }
                        )
                    }
                    AdvancedAnimationTab.EASING -> {
                        AdvEasingTabContent(
                            targetId = targetId,
                            viewModel = viewModel,
                            onNotify = { notificationMessage = it }
                        )
                    }
                    AdvancedAnimationTab.SPEED -> {
                        AdvSpeedCurveTabContent(
                            targetId = targetId,
                            viewModel = viewModel,
                            onNotify = { notificationMessage = it }
                        )
                    }
                    AdvancedAnimationTab.BLUR -> {
                        AdvMotionBlurTabContent(
                            targetId = targetId,
                            viewModel = viewModel,
                            onNotify = { notificationMessage = it }
                        )
                    }
                }
            }

            // Global Animation Management Bar at bottom of advanced panel
            AdvAnimationManagementBottomBar(
                targetId = targetId,
                targetDurationMs = targetDuration,
                viewModel = viewModel,
                onNotify = { notificationMessage = it }
            )
        } else {
            // EMPTY / NO SELECTION STATE
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
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Select an item to animate",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Tap any video, overlay, text or sticker clip on the timeline to use Advanced Animation tools",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Presets Dialog
    if (showPresetDialog && activeLayer != null) {
        AdvPresetManagementDialog(
            context = context,
            onDismiss = { showPresetDialog = false },
            onNotify = { notificationMessage = it }
        )
    }
}

@Composable
fun AdvancedAnimationPanel(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    AnimationsToolPanel(
        viewModel = viewModel,
        initialIsAdvanced = true,
        onClose = { viewModel.setActiveToolbarTab(null) },
        modifier = modifier
    )
}

// -------------------------------------------------------------------------------------------------
// KEYFRAME TAB CONTENT
// -------------------------------------------------------------------------------------------------
@Composable
private fun AdvKeyframeTabContent(
    targetId: String,
    targetDurationMs: Long,
    targetStartMs: Long,
    currentPosMs: Long,
    viewModel: StudioViewModel,
    onNotify: (String) -> Unit
) {
    val relTimeMs = (currentPosMs - targetStartMs).coerceIn(0L, targetDurationMs)
    val keyframesPair = viewModel.timelineEngine.getSelectedClipKeyframes()
    val keyframes = keyframesPair?.second ?: emptyList()
    val activeKf = keyframes.find { abs(it.timeMs - relTimeMs) <= 120L } ?: ClipKeyframe(timeMs = relTimeMs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Keyframe Action Bar: + Add | - Delete | ◀ Prev | Next ▶ | Copy | Paste
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    viewModel.timelineEngine.addKeyframeToSelectedClip()
                    onNotify("Keyframe added at ${String.format("%.2fs", relTimeMs / 1000f)}")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeKf.id in keyframes.map { it.id }) AccentCyan else AccentCyan.copy(alpha = 0.2f),
                    contentColor = if (activeKf.id in keyframes.map { it.id }) Color.Black else AccentCyan
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.weight(1.1f).height(30.dp)
            ) {
                Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = if (activeKf.id in keyframes.map { it.id }) "Update ◇" else "+ Add ◇", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            if (activeKf.id in keyframes.map { it.id }) {
                Button(
                    onClick = {
                        viewModel.timelineEngine.deleteKeyframeFromSelectedClip()
                        onNotify("Keyframe deleted")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent.copy(alpha = 0.2f), contentColor = RedAccent),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(0.9f).height(30.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(text = "Delete", fontSize = 10.sp)
                }
            }

            OutlinedButton(
                onClick = { viewModel.timelineEngine.jumpToPreviousKeyframe() },
                enabled = keyframes.any { it.timeMs < relTimeMs - 50L },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.4f)),
                modifier = Modifier.weight(0.7f).height(30.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = AccentCyan, modifier = Modifier.size(14.dp))
            }

            OutlinedButton(
                onClick = { viewModel.timelineEngine.jumpToNextKeyframe() },
                enabled = keyframes.any { it.timeMs > relTimeMs + 50L },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.4f)),
                modifier = Modifier.weight(0.7f).height(30.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = AccentCyan, modifier = Modifier.size(14.dp))
            }

            IconButton(
                onClick = {
                    viewModel.timelineEngine.copySelectedKeyframes()
                    onNotify("Keyframe copied")
                },
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(AdvCardBg)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(13.dp))
            }

            IconButton(
                onClick = {
                    viewModel.timelineEngine.pasteKeyframes(relTimeMs)
                    onNotify("Keyframe pasted")
                },
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(AdvCardBg)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = AccentCyan, modifier = Modifier.size(13.dp))
            }
        }

        // Keyframe Timeline Strip
        Surface(
            color = AdvControlSectionBg,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(0.5.dp, AdvPanelBorder),
            modifier = Modifier.fillMaxWidth().height(32.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                val dur = targetDurationMs.coerceAtLeast(100L).toFloat()
                val w = size.width
                val h = size.height
                val playheadX = (relTimeMs / dur) * w

                drawLine(color = Color(0xFF1E334E), start = Offset(0f, h / 2f), end = Offset(w, h / 2f), strokeWidth = 2f)

                keyframes.forEach { kf ->
                    val kfX = (kf.timeMs / dur) * w
                    val isKfActive = abs(kf.timeMs - relTimeMs) <= 120L
                    drawCircle(color = if (isKfActive) AccentCyan else AccentEmerald, radius = if (isKfActive) 5f else 3.5f, center = Offset(kfX, h / 2f))
                }

                drawLine(color = Color.White, start = Offset(playheadX, 0f), end = Offset(playheadX, h), strokeWidth = 2f)
            }
        }

        // Property Sliders Grid
        Surface(
            color = AdvCardBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, AdvPanelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Keyframe Properties (Time: ${String.format("%.2fs", relTimeMs / 1000f)})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                // Position X & Y
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Position X", fontSize = 9.sp, color = TextSecondary)
                            Text(String.format("%.2f", activeKf.posX), fontSize = 9.sp, color = AccentCyan)
                        }
                        Slider(
                            value = activeKf.posX.coerceIn(-1.5f, 1.5f),
                            onValueChange = { x ->
                                viewModel.timelineEngine.addKeyframeToSelectedClip(
                                    activeKf.copy(timeMs = relTimeMs, posX = x)
                                )
                            },
                            valueRange = -1.5f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                            modifier = Modifier.height(12.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Position Y", fontSize = 9.sp, color = TextSecondary)
                            Text(String.format("%.2f", activeKf.posY), fontSize = 9.sp, color = AccentCyan)
                        }
                        Slider(
                            value = activeKf.posY.coerceIn(-1.5f, 1.5f),
                            onValueChange = { y ->
                                viewModel.timelineEngine.addKeyframeToSelectedClip(
                                    activeKf.copy(timeMs = relTimeMs, posY = y)
                                )
                            },
                            valueRange = -1.5f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                            modifier = Modifier.height(12.dp)
                        )
                    }
                }

                // Scale X & Y
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Scale X", fontSize = 9.sp, color = TextSecondary)
                            Text(String.format("%.2fx", activeKf.scaleX), fontSize = 9.sp, color = AccentCyan)
                        }
                        Slider(
                            value = activeKf.scaleX.coerceIn(0.1f, 3.0f),
                            onValueChange = { s ->
                                viewModel.timelineEngine.addKeyframeToSelectedClip(
                                    activeKf.copy(timeMs = relTimeMs, scaleX = s)
                                )
                            },
                            valueRange = 0.1f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                            modifier = Modifier.height(12.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Scale Y", fontSize = 9.sp, color = TextSecondary)
                            Text(String.format("%.2fx", activeKf.scaleY), fontSize = 9.sp, color = AccentCyan)
                        }
                        Slider(
                            value = activeKf.scaleY.coerceIn(0.1f, 3.0f),
                            onValueChange = { s ->
                                viewModel.timelineEngine.addKeyframeToSelectedClip(
                                    activeKf.copy(timeMs = relTimeMs, scaleY = s)
                                )
                            },
                            valueRange = 0.1f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                            modifier = Modifier.height(12.dp)
                        )
                    }
                }

                // Rotation & Opacity
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Rotation", fontSize = 9.sp, color = TextSecondary)
                            Text("${activeKf.rotation.toInt()}°", fontSize = 9.sp, color = AccentCyan)
                        }
                        Slider(
                            value = activeKf.rotation.coerceIn(-180f, 180f),
                            onValueChange = { r ->
                                viewModel.timelineEngine.addKeyframeToSelectedClip(
                                    activeKf.copy(timeMs = relTimeMs, rotation = r)
                                )
                            },
                            valueRange = -180f..180f,
                            colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                            modifier = Modifier.height(12.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Opacity", fontSize = 9.sp, color = TextSecondary)
                            Text("${(activeKf.opacity * 100).toInt()}%", fontSize = 9.sp, color = AccentCyan)
                        }
                        Slider(
                            value = activeKf.opacity.coerceIn(0f, 1f),
                            onValueChange = { op ->
                                viewModel.timelineEngine.addKeyframeToSelectedClip(
                                    activeKf.copy(timeMs = relTimeMs, opacity = op)
                                )
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                            modifier = Modifier.height(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// MOTION PATH TAB CONTENT
// -------------------------------------------------------------------------------------------------
@Composable
private fun AdvMotionPathTabContent(
    targetId: String,
    targetDurationMs: Long,
    targetStartMs: Long,
    currentPosMs: Long,
    viewModel: StudioViewModel,
    onNotify: (String) -> Unit
) {
    val relTimeMs = (currentPosMs - targetStartMs).coerceIn(0L, targetDurationMs)
    val keyframes = viewModel.timelineEngine.getSelectedClipKeyframes()?.second ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AdvCardBgSelected,
                border = BorderStroke(1.dp, AccentCyan),
                modifier = Modifier.weight(1f).clickable {
                    val kf1 = ClipKeyframe(timeMs = 0L, posX = -0.6f, posY = 0f)
                    val kf2 = ClipKeyframe(timeMs = targetDurationMs, posX = 0.6f, posY = 0f)
                    viewModel.timelineEngine.updateClipKeyframes(targetId, listOf(kf1, kf2))
                    onNotify("Applied Straight Motion Path")
                }
            ) {
                Text("Straight", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCyan, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 5.dp))
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AdvCardBg,
                border = BorderStroke(0.5.dp, AdvPanelBorder),
                modifier = Modifier.weight(1f).clickable {
                    val kf1 = ClipKeyframe(timeMs = 0L, posX = -0.6f, posY = 0.4f)
                    val kf2 = ClipKeyframe(timeMs = targetDurationMs / 2, posX = 0f, posY = -0.5f)
                    val kf3 = ClipKeyframe(timeMs = targetDurationMs, posX = 0.6f, posY = 0.4f)
                    viewModel.timelineEngine.updateClipKeyframes(targetId, listOf(kf1, kf2, kf3))
                    onNotify("Applied Curved Arc Motion Path")
                }
            ) {
                Text("Curved Arc", fontSize = 10.sp, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 5.dp))
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AdvCardBg,
                border = BorderStroke(0.5.dp, AdvPanelBorder),
                modifier = Modifier.weight(1f).clickable {
                    val reversed = keyframes.map { kf -> kf.copy(timeMs = targetDurationMs - kf.timeMs) }.sortedBy { it.timeMs }
                    viewModel.timelineEngine.updateClipKeyframes(targetId, reversed)
                    onNotify("Reversed Motion Path")
                }
            ) {
                Text("Reverse", fontSize = 10.sp, color = AccentEmerald, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 5.dp))
            }
        }

        // Draggable Motion Path Canvas
        Surface(
            color = AdvControlSectionBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AdvPanelBorder),
            modifier = Modifier.fillMaxWidth().height(150.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(targetId) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val normalizedX = ((change.position.x / size.width) - 0.5f) * 2f
                                val normalizedY = ((change.position.y / size.height) - 0.5f) * 2f
                                val currentKf = keyframes.find { abs(it.timeMs - relTimeMs) <= 150L }
                                    ?: ClipKeyframe(timeMs = relTimeMs)
                                viewModel.timelineEngine.addKeyframeToSelectedClip(
                                    currentKf.copy(posX = normalizedX, posY = normalizedY)
                                )
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val centerX = w / 2f
                    val centerY = h / 2f

                    drawLine(color = Color(0xFF1E334E), start = Offset(0f, centerY), end = Offset(w, centerY), strokeWidth = 1f)
                    drawLine(color = Color(0xFF1E334E), start = Offset(centerX, 0f), end = Offset(centerX, h), strokeWidth = 1f)

                    if (keyframes.isNotEmpty()) {
                        val path = Path()
                        val sortedKfs = keyframes.sortedBy { it.timeMs }
                        sortedKfs.forEachIndexed { i, kf ->
                            val ptX = centerX + (kf.posX * (w / 2f))
                            val ptY = centerY + (kf.posY * (h / 2f))
                            if (i == 0) path.moveTo(ptX, ptY) else path.lineTo(ptX, ptY)
                        }

                        drawPath(
                            path = path,
                            color = AccentCyan,
                            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f), cap = StrokeCap.Round)
                        )

                        sortedKfs.forEach { kf ->
                            val ptX = centerX + (kf.posX * (w / 2f))
                            val ptY = centerY + (kf.posY * (h / 2f))
                            drawCircle(color = AccentEmerald, radius = 4.5f, center = Offset(ptX, ptY))
                        }
                    }

                    val curKf = keyframes.find { abs(it.timeMs - relTimeMs) <= 150L } ?: ClipKeyframe(timeMs = relTimeMs)
                    val curX = centerX + (curKf.posX * (w / 2f))
                    val curY = centerY + (curKf.posY * (h / 2f))
                    drawCircle(color = Color.White, radius = 7f, center = Offset(curX, curY))
                    drawCircle(color = AccentCyan, radius = 3.5f, center = Offset(curX, curY))
                }

                Text(
                    text = "Drag canvas to adjust position node at playhead",
                    color = TextTertiary,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TRANSFORM TAB CONTENT
// -------------------------------------------------------------------------------------------------
@Composable
private fun AdvTransformTabContent(
    targetId: String,
    targetDurationMs: Long,
    targetStartMs: Long,
    currentPosMs: Long,
    viewModel: StudioViewModel,
    onNotify: (String) -> Unit
) {
    val relTimeMs = (currentPosMs - targetStartMs).coerceIn(0L, targetDurationMs)
    val keyframes = viewModel.timelineEngine.getSelectedClipKeyframes()?.second ?: emptyList()
    val activeKf = keyframes.find { abs(it.timeMs - relTimeMs) <= 120L } ?: ClipKeyframe(timeMs = relTimeMs)
    var isUniformScale by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    viewModel.timelineEngine.flipSelectedClip(horizontal = true)
                    onNotify("Flipped Horizontal")
                },
                colors = ButtonDefaults.buttonColors(containerColor = AdvCardBg, contentColor = TextPrimary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(28.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Flip H", fontSize = 10.sp)
            }

            Button(
                onClick = {
                    viewModel.timelineEngine.flipSelectedClip(horizontal = false)
                    onNotify("Flipped Vertical")
                },
                colors = ButtonDefaults.buttonColors(containerColor = AdvCardBg, contentColor = TextPrimary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(28.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Flip V", fontSize = 10.sp)
            }

            Button(
                onClick = { isUniformScale = !isUniformScale },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isUniformScale) AccentEmerald.copy(alpha = 0.2f) else AdvCardBg,
                    contentColor = if (isUniformScale) AccentEmerald else TextSecondary
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1.2f).height(28.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(if (isUniformScale) Icons.Default.Link else Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(if (isUniformScale) "Uniform" else "Free", fontSize = 10.sp)
            }
        }

        Surface(
            color = AdvCardBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, AdvPanelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Scale Transform", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                if (isUniformScale) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Scale", fontSize = 9.sp, color = TextSecondary)
                        Text("${String.format("%.2f", activeKf.scaleX)}x", fontSize = 9.sp, color = AccentCyan)
                    }
                    Slider(
                        value = activeKf.scaleX.coerceIn(0.1f, 3.5f),
                        onValueChange = { s ->
                            viewModel.timelineEngine.addKeyframeToSelectedClip(
                                activeKf.copy(timeMs = relTimeMs, scaleX = s, scaleY = s)
                            )
                        },
                        valueRange = 0.1f..3.5f,
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                        modifier = Modifier.height(14.dp)
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scale X (${String.format("%.2f", activeKf.scaleX)}x)", fontSize = 9.sp, color = TextSecondary)
                            Slider(
                                value = activeKf.scaleX.coerceIn(0.1f, 3.5f),
                                onValueChange = { s ->
                                    viewModel.timelineEngine.addKeyframeToSelectedClip(
                                        activeKf.copy(timeMs = relTimeMs, scaleX = s)
                                    )
                                },
                                valueRange = 0.1f..3.5f,
                                colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                                modifier = Modifier.height(12.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scale Y (${String.format("%.2f", activeKf.scaleY)}x)", fontSize = 9.sp, color = TextSecondary)
                            Slider(
                                value = activeKf.scaleY.coerceIn(0.1f, 3.5f),
                                onValueChange = { s ->
                                    viewModel.timelineEngine.addKeyframeToSelectedClip(
                                        activeKf.copy(timeMs = relTimeMs, scaleY = s)
                                    )
                                },
                                valueRange = 0.1f..3.5f,
                                colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                                modifier = Modifier.height(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// EASING TAB CONTENT
// -------------------------------------------------------------------------------------------------
@Composable
private fun AdvEasingTabContent(
    targetId: String,
    viewModel: StudioViewModel,
    onNotify: (String) -> Unit
) {
    val keyframes = viewModel.timelineEngine.getSelectedClipKeyframes()?.second ?: emptyList()
    val currentInterpolation = keyframes.firstOrNull()?.interpolation ?: KeyframeInterpolation.EASE_IN_OUT

    val easingPresets = listOf(
        KeyframeInterpolation.LINEAR to "Linear",
        KeyframeInterpolation.EASE_IN to "Ease In",
        KeyframeInterpolation.EASE_OUT to "Ease Out",
        KeyframeInterpolation.EASE_IN_OUT to "Ease In-Out",
        KeyframeInterpolation.CUBIC_BEZIER to "Cubic",
        KeyframeInterpolation.CUSTOM_CURVE to "Bezier Curve",
        KeyframeInterpolation.HOLD to "Hold / Step"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Easing Curve Mode", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(easingPresets) { (interp, label) ->
                val isSelected = currentInterpolation == interp
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) AccentCyan.copy(alpha = 0.2f) else AdvCardBg,
                    border = BorderStroke(1.dp, if (isSelected) AccentCyan else AdvPanelBorder),
                    modifier = Modifier.clickable {
                        val updated = keyframes.map { it.copy(interpolation = interp) }
                        viewModel.timelineEngine.updateClipKeyframes(targetId, updated)
                        onNotify("Applied Easing: $label")
                    }
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) AccentCyan else TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Cubic Bezier Curve Visualizer
        Surface(
            color = AdvControlSectionBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AdvPanelBorder),
            modifier = Modifier.fillMaxWidth().height(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val w = size.width
                val h = size.height

                drawLine(color = Color(0xFF1E334E), start = Offset(0f, h), end = Offset(w, h), strokeWidth = 1f)
                drawLine(color = Color(0xFF1E334E), start = Offset(0f, 0f), end = Offset(0f, h), strokeWidth = 1f)

                val path = Path()
                path.moveTo(0f, h)

                when (currentInterpolation) {
                    KeyframeInterpolation.LINEAR -> path.lineTo(w, 0f)
                    KeyframeInterpolation.EASE_IN -> path.cubicTo(w * 0.42f, h, w, h * 0.58f, w, 0f)
                    KeyframeInterpolation.EASE_OUT -> path.cubicTo(0f, h * 0.42f, w * 0.58f, 0f, w, 0f)
                    KeyframeInterpolation.EASE_IN_OUT, KeyframeInterpolation.CUBIC_BEZIER, KeyframeInterpolation.CUSTOM_CURVE -> {
                        path.cubicTo(w * 0.42f, h, w * 0.58f, 0f, w, 0f)
                    }
                    KeyframeInterpolation.HOLD -> {
                        path.lineTo(w, h)
                        path.lineTo(w, 0f)
                    }
                }

                drawPath(path = path, color = AccentCyan, style = Stroke(width = 3f, cap = StrokeCap.Round))
                drawCircle(color = AccentEmerald, radius = 4.5f, center = Offset(0f, h))
                drawCircle(color = AccentEmerald, radius = 4.5f, center = Offset(w, 0f))
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// SPEED CURVE TAB CONTENT
// -------------------------------------------------------------------------------------------------
@Composable
private fun AdvSpeedCurveTabContent(
    targetId: String,
    viewModel: StudioViewModel,
    onNotify: (String) -> Unit
) {
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }

    val speedPresets = listOf(
        "Constant" to 1.0f,
        "Slow In" to 0.7f,
        "Slow Out" to 1.3f,
        "Fast In" to 1.5f,
        "Fast Out" to 0.6f,
        "Slow In/Out" to 0.8f
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Speed Curve Profiles", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            speedPresets.forEach { (label, spd) ->
                val isSelected = abs(speedMultiplier - spd) < 0.05f
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) AccentCyan.copy(alpha = 0.2f) else AdvCardBg,
                    border = BorderStroke(1.dp, if (isSelected) AccentCyan else AdvPanelBorder),
                    modifier = Modifier.clickable {
                        speedMultiplier = spd
                        viewModel.timelineEngine.updateClipAnimation(targetId) {
                            it.copy(speed = spd)
                        }
                        onNotify("Applied Speed: $label (${spd}x)")
                    }
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) AccentCyan else TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Surface(
            color = AdvCardBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, AdvPanelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Animation Speed Multiplier", fontSize = 10.sp, color = TextSecondary)
                    Text("${String.format("%.2f", speedMultiplier)}x", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                }
                Slider(
                    value = speedMultiplier.coerceIn(0.2f, 3.0f),
                    onValueChange = { spd ->
                        speedMultiplier = spd
                        viewModel.timelineEngine.updateClipAnimation(targetId) {
                            it.copy(speed = spd)
                        }
                    },
                    valueRange = 0.2f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                    modifier = Modifier.height(14.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// MOTION BLUR TAB CONTENT
// -------------------------------------------------------------------------------------------------
@Composable
private fun AdvMotionBlurTabContent(
    targetId: String,
    viewModel: StudioViewModel,
    onNotify: (String) -> Unit
) {
    var isEnabled by remember { mutableStateOf(false) }
    var shutterDegrees by remember { mutableFloatStateOf(180f) }
    var sampleCount by remember { mutableFloatStateOf(6f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            color = if (isEnabled) AccentCyan.copy(alpha = 0.15f) else AdvCardBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (isEnabled) AccentCyan else AdvPanelBorder),
            modifier = Modifier.fillMaxWidth().clickable {
                isEnabled = !isEnabled
                viewModel.timelineEngine.toggleMotionBlur(targetId)
                onNotify(if (isEnabled) "Motion Blur Enabled" else "Motion Blur Disabled")
            }
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.BlurOn, contentDescription = null, tint = if (isEnabled) AccentCyan else TextSecondary, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Subframe Motion Blur", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Camera shutter blur on moving keyframes", fontSize = 9.sp, color = TextTertiary)
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        viewModel.timelineEngine.toggleMotionBlur(targetId)
                        onNotify(if (it) "Motion Blur Enabled" else "Motion Blur Disabled")
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = Color(0xFF163252))
                )
            }
        }

        if (isEnabled) {
            Surface(
                color = AdvCardBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, AdvPanelBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Shutter Angle", fontSize = 9.sp, color = TextSecondary)
                        Text("${shutterDegrees.toInt()}°", fontSize = 9.sp, color = AccentCyan)
                    }
                    Slider(
                        value = shutterDegrees,
                        onValueChange = { shutterDegrees = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                        modifier = Modifier.height(12.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subframe Samples", fontSize = 9.sp, color = TextSecondary)
                        Text("${sampleCount.toInt()} samples", fontSize = 9.sp, color = AccentCyan)
                    }
                    Slider(
                        value = sampleCount,
                        onValueChange = { sampleCount = it },
                        valueRange = 2f..16f,
                        steps = 7,
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan),
                        modifier = Modifier.height(12.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// ANIMATION MANAGEMENT BOTTOM BAR
// -------------------------------------------------------------------------------------------------
@Composable
private fun AdvAnimationManagementBottomBar(
    targetId: String,
    targetDurationMs: Long,
    viewModel: StudioViewModel,
    onNotify: (String) -> Unit
) {
    Surface(
        color = Color(0xFF060E1A),
        border = BorderStroke(0.5.dp, AdvPanelBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.timelineEngine.duplicateSelectedKeyframes()
                    onNotify("Duplicated Keyframes")
                },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp),
                border = BorderStroke(0.5.dp, AdvPanelBorder)
            ) {
                Text("Duplicate", fontSize = 10.sp, color = TextPrimary)
            }

            OutlinedButton(
                onClick = {
                    val keyframes = viewModel.timelineEngine.getSelectedClipKeyframes()?.second ?: emptyList()
                    val reversed = keyframes.map { kf -> kf.copy(timeMs = targetDurationMs - kf.timeMs) }.sortedBy { it.timeMs }
                    viewModel.timelineEngine.updateClipKeyframes(targetId, reversed)
                    onNotify("Reversed Keyframes")
                },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp),
                border = BorderStroke(0.5.dp, AdvPanelBorder)
            ) {
                Text("Reverse", fontSize = 10.sp, color = TextPrimary)
            }

            OutlinedButton(
                onClick = {
                    viewModel.timelineEngine.clearClipAnimation(targetId)
                    onNotify("Reset Animation")
                },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp),
                border = BorderStroke(0.5.dp, RedAccent.copy(alpha = 0.4f))
            ) {
                Text("Reset", fontSize = 10.sp, color = RedAccent)
            }

            OutlinedButton(
                onClick = {
                    viewModel.timelineEngine.clearAllKeyframesInSelectedClip()
                    onNotify("Cleared Keyframes")
                },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp),
                border = BorderStroke(0.5.dp, RedAccent.copy(alpha = 0.4f))
            ) {
                Text("Clear ◇", fontSize = 10.sp, color = RedAccent)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// PRESET DIALOG
// -------------------------------------------------------------------------------------------------
@Composable
private fun AdvPresetManagementDialog(
    context: Context,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("ah_adv_animation_presets", Context.MODE_PRIVATE) }
    var savedPresets by remember { mutableStateOf(prefs.all.keys.toList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Animation Presets", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (presetName.isNotBlank()) {
                            prefs.edit().putString(presetName.trim(), "saved").apply()
                            savedPresets = prefs.all.keys.toList()
                            onNotify("Saved preset: $presetName")
                            presetName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(30.dp)
                ) {
                    Text("Save Current Animation", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Divider(color = AdvPanelBorder, modifier = Modifier.padding(vertical = 2.dp))

                Text("Saved Presets (${savedPresets.size})", fontSize = 10.sp, color = TextSecondary)

                if (savedPresets.isEmpty()) {
                    Text("No saved custom presets.", fontSize = 9.sp, color = TextTertiary)
                } else {
                    savedPresets.forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = p, fontSize = 10.sp, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        onNotify("Applied preset $p")
                                        onDismiss()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Apply", fontSize = 9.sp, color = AccentCyan)
                                }
                                IconButton(
                                    onClick = {
                                        prefs.edit().remove(p).apply()
                                        savedPresets = prefs.all.keys.toList()
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = RedAccent, modifier = Modifier.size(11.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary, fontSize = 11.sp) }
        },
        containerColor = AdvCardBg
    )
}
