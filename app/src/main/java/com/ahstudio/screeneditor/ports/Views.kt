package com.ahstudio.screeneditor.ports

import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight read models over existing timeline structures.
 * These are constructed on demand and do NOT duplicate timeline storage.
 */
data class ClipView(
    val id: String,
    val trackId: String,
    val layerId: String?,
    val startUs: Long,
    val durationUs: Long,
    val locked: Boolean = false,
    val enabled: Boolean = true
)

data class TrackView(
    val id: String,
    val kind: TrackKind,
    val locked: Boolean = false,
    val muted: Boolean = false
)

enum class TrackKind {
    VIDEO, AUDIO, OVERLAY, TEXT, EFFECT, STICKER
}

enum class TrimEdge {
    START, END
}

enum class BlendMode {
    NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION
}

data class EffectChainRef(
    val chainId: String,
    val effectIds: List<String> = emptyList()
)

data class TextStateRef(
    val text: String = "",
    val fontName: String = "Roboto",
    val fontSizeSp: Float = 24f,
    val colorArgb: Long = 0xFFFFFFFF,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1.2f,
    val shadowColorArgb: Long = 0x00000000,
    val shadowRadius: Float = 0f,
    val strokeColorArgb: Long = 0x00000000,
    val strokeWidth: Float = 0f
)

data class SizeF(val width: Float, val height: Float)
