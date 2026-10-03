package com.ahstudio.audio.master.automation

import com.ahstudio.audio.master.model.AudioAutomationModel
import com.ahstudio.audio.master.model.AudioClipModel
import com.ahstudio.audio.master.model.AudioFadeSettings
import com.ahstudio.audio.master.model.AudioKeyframeModel
import com.ahstudio.audio.master.model.AutomationParameter
import com.ahstudio.audio.master.model.FadeCurve
import com.ahstudio.audio.master.model.KeyframeCurve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object AudioKeyframeEngine {
    fun valueAt(keyframes: List<AudioKeyframeModel>, t: Double, default: Float): Float {
        if (keyframes.isEmpty()) return default
        val sorted = keyframes.sortedBy { it.timeSec }
        if (t <= sorted.first().timeSec) return sorted.first().value
        if (t >= sorted.last().timeSec) return sorted.last().value
        var i = 0
        while (i + 1 < sorted.size && sorted[i + 1].timeSec <= t) i++
        val a = sorted[i]; val b = sorted[i + 1]
        val span = b.timeSec - a.timeSec
        if (span <= 1e-9) return b.value
        val u = ((t - a.timeSec) / span).coerceIn(0.0, 1.0)
        return when (a.curve) {
            KeyframeCurve.HOLD -> a.value
            KeyframeCurve.SINE -> (a.value + (b.value - a.value) * (0.5 - 0.5 * cos(PI * u))).toFloat()
            KeyframeCurve.LINEAR -> (a.value + (b.value - a.value) * u).toFloat()
        }
    }

    fun hasKeyframeIn(keyframes: List<AudioKeyframeModel>, startSec: Double, endSec: Double): Boolean =
        keyframes.any { it.timeSec >= startSec && it.timeSec <= endSec }
}

class AudioAutomationResolver {
    data class ParamBlock(val startValue: Float, val endValue: Float, val needsPerFrame: Boolean)

    fun resolve(model: AudioAutomationModel?, t0: Double, t1: Double, default: Float): ParamBlock {
        if (model == null || !model.enabled || model.keyframes.isEmpty())
            return ParamBlock(default, default, needsPerFrame = false)
        val s = AudioKeyframeEngine.valueAt(model.keyframes, t0, default)
        val e = AudioKeyframeEngine.valueAt(model.keyframes, t1, default)
        val inside = AudioKeyframeEngine.hasKeyframeIn(model.keyframes, t0, t1)
        return ParamBlock(s, e, needsPerFrame = inside)
    }

    fun perFrame(model: AudioAutomationModel?, t: Double, default: Float): Float =
        if (model == null || !model.enabled || model.keyframes.isEmpty()) default
        else AudioKeyframeEngine.valueAt(model.keyframes, t, default)

    fun automationFor(clip: AudioClipModel, p: AutomationParameter): AudioAutomationModel? =
        clip.automation.firstOrNull { it.parameter == p }
}

object AudioEnvelopeGenerator {
    fun fadeGain(fade: AudioFadeSettings, clipLocalSec: Double, clipDurationSec: Double): Float {
        var g = 1.0
        if (fade.fadeInSec > 0.0 && clipLocalSec < fade.fadeInSec) {
            val u = (clipLocalSec / fade.fadeInSec).coerceIn(0.0, 1.0)
            g *= fadeInCurve(u, fade.fadeInCurve)
        }
        if (fade.fadeOutSec > 0.0) {
            val fromEnd = clipDurationSec - clipLocalSec
            if (fromEnd < fade.fadeOutSec) {
                val u = (fromEnd / fade.fadeOutSec).coerceIn(0.0, 1.0)
                g *= fadeOutCurve(u, fade.fadeOutCurve)
            }
        }
        return g.toFloat()
    }

    fun fadeInCurve(u: Double, curve: FadeCurve): Double = when (curve) {
        FadeCurve.LINEAR -> u
        FadeCurve.EXPONENTIAL -> u * u * u
        FadeCurve.SINE -> sin(u * PI / 2)
    }
    fun fadeOutCurve(u: Double, curve: FadeCurve): Double = when (curve) {
        FadeCurve.LINEAR -> u
        FadeCurve.EXPONENTIAL -> u * u * u
        FadeCurve.SINE -> sin(u * PI / 2)
    }
}
