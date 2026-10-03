package com.ahstudio.captions.animation

import com.ahstudio.captions.core.model.CaptionAnimationSpec
import com.ahstudio.captions.core.model.CaptionAnimationType
import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.time.TimelineUs
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

data class AnimationState(
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val offsetYFraction: Float = 0f,
    val revealedCharCount: Int = Int.MAX_VALUE,
    val revealedWordCount: Int = Int.MAX_VALUE,
    val pulseScale: Float = 1f,
    val waveOffsetFraction: Float = 0f,
)

object CaptionAnimationEngine {

    fun state(clip: CaptionClip, t: TimelineUs, spec: CaptionAnimationSpec?): AnimationState {
        if (spec == null || spec.type == CaptionAnimationType.NONE) return AnimationState()
        val durUs = clip.timing.durationUs.coerceAtLeast(1)
        val inUs = (t - clip.timing.start).micros.coerceIn(0, durUs)
        val outUs = (clip.timing.end - t).micros.coerceIn(0, durUs)
        val enterDurUs = spec.durationMs.coerceAtLeast(1) * 1000L
        val exitDurUs = (spec.exitType?.let { spec.durationMs } ?: 0) * 1000L
        val pIn = (inUs.toFloat() / enterDurUs).coerceIn(0f, 1f)
        val pOut = if (exitDurUs > 0) (outUs.toFloat() / exitDurUs).coerceIn(0f, 1f) else 1f

        var st = AnimationState()
        when (spec.type) {
            CaptionAnimationType.FADE -> st = st.copy(alpha = ease(pIn))
            CaptionAnimationType.SLIDE_UP -> st = st.copy(alpha = ease(pIn), offsetYFraction = (1 - ease(pIn)) * 0.05f)
            CaptionAnimationType.SLIDE_DOWN -> st = st.copy(alpha = ease(pIn), offsetYFraction = -(1 - ease(pIn)) * 0.05f)
            CaptionAnimationType.SCALE -> st = st.copy(alpha = ease(pIn), scaleX = 0.7f + 0.3f * ease(pIn), scaleY = 0.7f + 0.3f * ease(pIn))
            CaptionAnimationType.POP -> {
                val overshoot = 1f + 0.25f * sin(pIn * PI.toFloat())
                st = st.copy(alpha = pIn.coerceIn(0f, 1f),
                    scaleX = min(1.15f, 0.6f + 0.55f * pIn * overshoot),
                    scaleY = min(1.15f, 0.6f + 0.55f * pIn * overshoot))
            }
            CaptionAnimationType.TYPEWRITER -> {
                val p = inUs.toFloat() / durUs
                st = st.copy(revealedCharCount = (clip.displayText.length * ease(p)).toInt())
            }
            CaptionAnimationType.WORD_REVEAL -> {
                val p = inUs.toFloat() / durUs
                st = st.copy(revealedWordCount = (clip.words.size * ease(p)).toInt().coerceAtLeast(1))
            }
            CaptionAnimationType.KARAOKE_FILL -> st = st
            CaptionAnimationType.PULSE -> {
                val phase = (inUs % 1_200_000L) / 1_200_000f
                val s = 1f + 0.04f * sin(phase * 2 * PI.toFloat())
                st = st.copy(pulseScale = s)
            }
            CaptionAnimationType.BOUNCE -> {
                val p = inUs.toFloat() / durUs
                st = st.copy(offsetYFraction = -0.015f * kotlin.math.abs(sin(p * 3 * PI.toFloat())))
            }
            CaptionAnimationType.WAVE -> {
                val p = inUs.toFloat() / durUs
                st = st.copy(waveOffsetFraction = 0.01f * sin(p * 4 * PI.toFloat()))
            }
            else -> Unit
        }
        when (spec.exitType) {
            CaptionAnimationType.FADE -> st = st.copy(alpha = st.alpha * ease(pOut))
            CaptionAnimationType.SLIDE_UP -> st = st.copy(alpha = st.alpha * ease(pOut), offsetYFraction = st.offsetYFraction - (1 - ease(pOut)) * 0.04f)
            CaptionAnimationType.SLIDE_DOWN -> st = st.copy(alpha = st.alpha * ease(pOut), offsetYFraction = st.offsetYFraction + (1 - ease(pOut)) * 0.04f)
            CaptionAnimationType.SCALE -> st = st.copy(alpha = st.alpha * ease(pOut), scaleX = st.scaleX * (0.8f + 0.2f * ease(pOut)), scaleY = st.scaleY * (0.8f + 0.2f * ease(pOut)))
            else -> Unit
        }
        return st
    }

    private fun ease(p: Float): Float {
        val c = p.coerceIn(0f, 1f)
        return c * c * (3 - 2 * c)
    }
}
