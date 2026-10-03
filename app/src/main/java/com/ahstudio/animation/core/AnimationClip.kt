package com.ahstudio.animation.core

import com.ahstudio.animation.curves.CubicBezierTiming
import com.ahstudio.animation.easing.EasingType
import com.ahstudio.animation.keyframes.InterpolationType
import com.ahstudio.animation.keyframes.Keyframe
import com.ahstudio.animation.keyframes.KeyframeId
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.properties.PropertyType
import com.ahstudio.animation.time.LoopMode

data class ClipTrack(
    val property: String,
    val type: PropertyType,
    val keyframes: List<Keyframe>,           // times RELATIVE to clip start
    val loop: LoopMode = LoopMode.NONE
)

data class AnimationClip(
    val id: String, val name: String, val durationMs: Long,
    val tracks: List<ClipTrack>,
    val metadata: Map<String, String> = emptyMap()
)

object BuiltinPresets {
    private fun fid() = KeyframeId(0L) // real ids assigned at apply-time by engine

    private fun f(t: Long, v: Double, i: InterpolationType = InterpolationType.LINEAR, e: EasingType = EasingType.LINEAR) =
        Keyframe(fid(), t, value = v, interpolation = i, easing = e)

    private fun v2(t: Long, x: Double, y: Double, i: InterpolationType = InterpolationType.LINEAR, e: EasingType = EasingType.LINEAR) =
        Keyframe(fid(), t, vecValue = Vec2(x, y), interpolation = i, easing = e)

    fun fadeIn(dur: Long = 800) = AnimationClip("preset.fadeIn", "Fade In", dur, listOf(
        ClipTrack("Transform.Opacity", PropertyType.FLOAT, listOf(f(0, 0.0), f(dur, 100.0)))))
    fun fadeOut(dur: Long = 800) = AnimationClip("preset.fadeOut", "Fade Out", dur, listOf(
        ClipTrack("Transform.Opacity", PropertyType.FLOAT, listOf(f(0, 100.0), f(dur, 0.0)))))
    fun zoomIn(dur: Long = 900) = AnimationClip("preset.zoomIn", "Zoom In", dur, listOf(
        ClipTrack("Transform.Scale", PropertyType.VEC2, listOf(
            v2(0, 20.0, 20.0, InterpolationType.EASE_OUT), v2(dur, 100.0, 100.0)))))
    fun smoothZoom(dur: Long = 1200) = AnimationClip("preset.smoothZoom", "Smooth Zoom", dur, listOf(
        ClipTrack("Transform.Scale", PropertyType.VEC2, listOf(
            Keyframe(fid(), 0, vecValue = Vec2(100.0, 100.0), interpolation = InterpolationType.CUSTOM_CURVE,
                bezier = CubicBezierTiming(0.33, 0.0, 0.2, 1.0)),
            v2(dur, 132.0, 132.0))),
        ClipTrack("Transform.Opacity", PropertyType.FLOAT, listOf(
            f(0, 100.0, InterpolationType.CUSTOM_CURVE), f(dur, 86.0)))))
    fun slideLeft(dur: Long = 700) = AnimationClip("preset.slideLeft", "Slide Left", dur, listOf(
        ClipTrack("Transform.Position", PropertyType.VEC2, listOf(
            v2(0, 400.0, 0.0, InterpolationType.EASE_OUT), v2(dur, 0.0, 0.0)))))
    fun slideRight(dur: Long = 700) = AnimationClip("preset.slideRight", "Slide Right", dur, listOf(
        ClipTrack("Transform.Position", PropertyType.VEC2, listOf(
            v2(0, -400.0, 0.0, InterpolationType.EASE_OUT), v2(dur, 0.0, 0.0)))))
    fun pop(dur: Long = 600) = AnimationClip("preset.pop", "Pop", dur, listOf(
        ClipTrack("Transform.Scale", PropertyType.VEC2, listOf(
            Keyframe(fid(), 0, vecValue = Vec2(0.0, 0.0), interpolation = InterpolationType.CUSTOM_CURVE,
                bezier = CubicBezierTiming(0.34, 1.56, 0.64, 1.0)),   // overshoot (back-out)
            v2((dur * 0.75).toLong(), 120.0, 120.0),
            v2(dur, 100.0, 100.0)))))
    fun bounce(dur: Long = 1200) = AnimationClip("preset.bounce", "Bounce", dur, listOf(
        ClipTrack("Transform.Position", PropertyType.VEC2, listOf(
            v2(0, 0.0, 0.0, InterpolationType.EASE_OUT, EasingType.BOUNCE_OUT),
            v2(dur, 0.0, -400.0)))))
    fun rotate(dur: Long = 1000) = AnimationClip("preset.rotate", "Rotate 360", dur, listOf(
        ClipTrack("Transform.Rotation", PropertyType.FLOAT, listOf(
            f(0, 0.0, InterpolationType.EASE_IN_OUT), f(dur, 360.0)))))
    fun shake(dur: Long = 800) = AnimationClip("preset.shake", "Shake", dur, listOf(
        ClipTrack("Transform.Position", PropertyType.VEC2, listOf(
            v2(0, 0.0, 0.0), v2(60, 14.0, -9.0), v2(120, -12.0, 7.0), v2(180, 9.0, -6.0),
            v2(240, -7.0, 4.0), v2(320, 4.0, -3.0), v2(400, -3.0, 2.0),
            v2(520, 1.5, -1.0), v2(640, 0.0, 0.5), v2(dur, 0.0, 0.0)))))
    fun all(): List<AnimationClip> = listOf(
        fadeIn(), fadeOut(), zoomIn(), smoothZoom(), slideLeft(), slideRight(),
        pop(), bounce(), rotate(), shake())
}
