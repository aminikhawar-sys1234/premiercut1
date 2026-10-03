package com.ahstudio.screeneditor.core

import android.graphics.PointF

enum class EditorTool {
    SELECT,
    CROP,
    TEXT,
    EFFECTS,
    TRANSFORM
}

enum class InteractionMode {
    IDLE,
    TRANSFORMING,
    TRIMMING,
    SCRUBBING,
    CLIP_DRAGGING
}

data class ScreenEditorState(
    val selectedLayerId: String? = null,
    val selectedClipId: String? = null,
    val contextClipId: String? = null,      // clip under playhead (contextual panel), not selection steal
    val activeTool: EditorTool = EditorTool.SELECT,
    val interactionMode: InteractionMode = InteractionMode.IDLE,
    val isTransforming: Boolean = false,
    val isTrimming: Boolean = false,
    val isScrubbing: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTimelinePositionUs: Long = 0L,
    val viewportScale: Float = 1f,
    val viewportTranslation: PointF = PointF(0f, 0f),
    val snappingEnabled: Boolean = true,
    val guidesEnabled: Boolean = true
)
