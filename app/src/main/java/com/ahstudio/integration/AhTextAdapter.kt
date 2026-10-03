package com.ahstudio.integration

import com.ahstudio.screeneditor.ports.TextPort
import com.ahstudio.screeneditor.ports.TextStateRef
import com.example.engine.TimelineEngine

class AhTextAdapter(
    private val timelineEngine: TimelineEngine
) : TextPort {

    override fun stateFor(layerId: String): TextStateRef? {
        val tl = timelineEngine.timeline.value
        val txt = tl.textClips.firstOrNull { it.id == layerId } ?: return null
        return TextStateRef(
            text = txt.text,
            fontName = txt.fontFamily,
            fontSizeSp = txt.fontSizeSp,
            colorArgb = txt.textColor,
            isBold = txt.fontWeight >= 700,
            isItalic = txt.isItalic,
            letterSpacing = txt.letterSpacing,
            lineSpacing = txt.lineSpacing,
            shadowColorArgb = txt.shadowColor,
            shadowRadius = txt.shadowBlur,
            strokeColorArgb = txt.strokeColor,
            strokeWidth = txt.strokeWidth
        )
    }

    override fun update(layerId: String, mutate: (TextStateRef) -> TextStateRef) {
        val cur = timelineEngine.timeline.value
        val txt = cur.textClips.firstOrNull { it.id == layerId } ?: return
        val currentRef = stateFor(layerId) ?: TextStateRef()
        val nextRef = mutate(currentRef)
        val updatedClips = cur.textClips.map {
            if (it.id == layerId) {
                it.copy(
                    text = nextRef.text,
                    fontFamily = nextRef.fontName,
                    fontSizeSp = nextRef.fontSizeSp,
                    textColor = nextRef.colorArgb,
                    isItalic = nextRef.isItalic,
                    letterSpacing = nextRef.letterSpacing,
                    lineSpacing = nextRef.lineSpacing,
                    shadowColor = nextRef.shadowColorArgb,
                    shadowBlur = nextRef.shadowRadius,
                    strokeColor = nextRef.strokeColorArgb,
                    strokeWidth = nextRef.strokeWidth
                )
            } else it
        }
        timelineEngine.loadTimeline(cur.copy(textClips = updatedClips))
    }
}
