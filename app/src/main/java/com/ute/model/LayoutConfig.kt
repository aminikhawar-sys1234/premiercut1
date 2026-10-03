package com.ute.model

enum class Align { START, CENTER, END, JUSTIFY }   // direction-relative
enum class WrapMode { NONE, WORD, CHARACTER }
enum class VerticalAlign { TOP, MIDDLE, BOTTOM }

data class LayoutConfig(
    /** 0 = auto width (no wrapping). Otherwise wrap at this width in px. */
    val widthPx: Float = 0f,
    val heightPx: Float = 0f,        // 0 = auto height
    val align: Align = Align.START,
    val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    val wrapMode: WrapMode = WrapMode.WORD,
    val maxLines: Int = 0,           // 0 = unlimited
    val safeMarginPx: Float = 0f,    // layout inset applied on all sides
)
