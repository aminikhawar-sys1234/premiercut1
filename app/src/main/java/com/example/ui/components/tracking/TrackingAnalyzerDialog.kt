package com.example.ui.components.tracking

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * CapCut-style modern Motion Tracking Analysis Progress Dialog.
 */
@Composable
fun TrackingAnalyzerDialog(
    progress: Float,
    statusText: String = "Analyzing motion vector template matching...",
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "TrackingProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "TrackingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14171F),
            border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated Progress Ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer rotating glow ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationAngle)
                            .border(
                                width = 2.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color(0xFF00E5FF),
                                        Color(0xFF8B5CF6),
                                        Color(0xFF00E5FF).copy(alpha = 0.1f),
                                        Color(0xFF00E5FF)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Material progress indicator
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(90.dp),
                        color = Color(0xFF00E5FF),
                        trackColor = Color(0xFF1E2638),
                        strokeWidth = 6.dp,
                    )

                    // Percentage Text
                    Text(
                        text = "${(animatedProgress * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Tracking Motion...",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = statusText,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232838)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Cancel Tracking", color = Color(0xFFFF5252), fontSize = 13.sp)
                }
            }
        }
    }
}
