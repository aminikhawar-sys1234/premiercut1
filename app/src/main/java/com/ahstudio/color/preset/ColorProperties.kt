package com.ahstudio.color.preset

import com.ahstudio.color.core.ColorState

object ColorProperties {
    class FloatProp(
        val path: String,
        val get: (ColorState) -> Float,
        val set: (ColorState, Float) -> ColorState,
        val min: Float, val max: Float
    )

    val PRIMARY = listOf(
        FloatProp("exposure",      { it.exposure },      { s, v -> s.copy(exposure = v) },      -5f, 5f),
        FloatProp("contrast",      { it.contrast },      { s, v -> s.copy(contrast = v) },      -1f, 1f),
        FloatProp("contrastPivot", { it.contrastPivot }, { s, v -> s.copy(contrastPivot = v) }, 0.05f, 0.95f),
        FloatProp("highlights",    { it.highlights },    { s, v -> s.copy(highlights = v) },    -1f, 1f),
        FloatProp("shadows",       { it.shadows },       { s, v -> s.copy(shadows = v) },       -1f, 1f),
        FloatProp("whites",        { it.whites },        { s, v -> s.copy(whites = v) },        -1f, 1f),
        FloatProp("blacks",        { it.blacks },        { s, v -> s.copy(blacks = v) },        -1f, 1f),
        FloatProp("temperature",   { it.temperature },   { s, v -> s.copy(temperature = v) },   -1f, 1f),
        FloatProp("tint",          { it.tint },          { s, v -> s.copy(tint = v) },          -1f, 1f),
        FloatProp("saturation",    { it.saturation },    { s, v -> s.copy(saturation = v) },    -1f, 1f),
        FloatProp("vibrance",      { it.vibrance },      { s, v -> s.copy(vibrance = v) },      -1f, 1f),
        FloatProp("skinProtect",   { it.skinProtect },   { s, v -> s.copy(skinProtect = v) },    0f, 1f)
    )

    val SPLIT = listOf(
        FloatProp("splitTone.shadowHue",    { it.splitTone.shadowHue },    { s, v -> s.copy(splitTone = s.splitTone.copy(shadowHue = v)) },    0f, 1f),
        FloatProp("splitTone.shadowSat",    { it.splitTone.shadowSat },    { s, v -> s.copy(splitTone = s.splitTone.copy(shadowSat = v)) },    0f, 1f),
        FloatProp("splitTone.highlightHue", { it.splitTone.highlightHue }, { s, v -> s.copy(splitTone = s.splitTone.copy(highlightHue = v)) }, 0f, 1f),
        FloatProp("splitTone.highlightSat", { it.splitTone.highlightSat }, { s, v -> s.copy(splitTone = s.splitTone.copy(highlightSat = v)) }, 0f, 1f),
        FloatProp("splitTone.balance",      { it.splitTone.balance },      { s, v -> s.copy(splitTone = s.splitTone.copy(balance = v)) },     -1f, 1f),
        FloatProp("splitTone.strength",     { it.splitTone.strength },     { s, v -> s.copy(splitTone = s.splitTone.copy(strength = v)) },     0f, 1f)
    )

    val ALL: Map<String, FloatProp> = (PRIMARY + SPLIT).associateBy { it.path }
}
