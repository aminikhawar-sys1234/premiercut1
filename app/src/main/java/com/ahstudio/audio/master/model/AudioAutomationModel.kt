package com.ahstudio.audio.master.model

enum class AutomationParameter { VOLUME, GAIN, PAN, PITCH, SPEED, DSP_PARAM }
enum class KeyframeCurve { LINEAR, HOLD, SINE }

data class AudioKeyframeModel(
    val timeSec: Double,
    val value: Float,
    val curve: KeyframeCurve = KeyframeCurve.LINEAR,
)

data class AudioAutomationModel(
    val parameter: AutomationParameter,
    val enabled: Boolean = false,
    val keyframes: List<AudioKeyframeModel> = emptyList(),
    val dspNodeIndex: Int = -1,
    val dspParamKey: String = "",
)
