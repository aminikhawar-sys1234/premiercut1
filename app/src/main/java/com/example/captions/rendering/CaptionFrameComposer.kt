package com.ahstudio.captions.rendering

import com.ahstudio.captions.animation.AnimationState
import com.ahstudio.captions.animation.CaptionAnimationEngine
import com.ahstudio.captions.core.language.TextMetrics
import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.model.CaptionProject
import com.ahstudio.captions.core.model.CaptionStyle
import com.ahstudio.captions.core.model.CaptionTrack
import com.ahstudio.captions.core.model.CaptionAnimationType
import com.ahstudio.captions.core.time.TimelineUs
import com.ahstudio.captions.highlight.CaptionHighlightEngine
import com.ahstudio.captions.highlight.WordHighlightState
import com.ahstudio.captions.layout.CaptionLayout
import com.ahstudio.captions.layout.CaptionLayoutEngine
import com.ahstudio.captions.layout.SafeArea
import com.ahstudio.captions.timeline.CaptionTimelineEngine

data class RenderableCaption(
    val clip: CaptionClip,
    val style: CaptionStyle,
    val layout: CaptionLayout,
    val animation: AnimationState,
    val highlight: WordHighlightState?,
    val scale: Float,
    val rotationDegrees: Float,
    val karaokeProgress: Float? = null,
)

class CaptionFrameComposer(
    private val layoutEngine: CaptionLayoutEngine = CaptionLayoutEngine(),
) {
    private val timeline = CaptionTimelineEngine()

    fun compose(
        project: CaptionProject,
        tracks: List<CaptionTrack>,
        clockUs: TimelineUs,
        canvasW: Float,
        canvasH: Float,
        density: Float,
        safeArea: SafeArea = SafeArea(),
        tracking: Map<String, com.ahstudio.captions.tracking.TrackingResult> = emptyMap(),
    ): List<RenderableCaption> {
        val out = ArrayList<RenderableCaption>()
        for (track in tracks) {
            val clip = timeline.activeClip(track, clockUs) ?: continue
            val style = project.styleFor(clip.styleId)
            val highlight = CaptionHighlightEngine.at(clip, clockUs)

            val lines = clip.lines
            val displayLines = lines.map { it.text }
            val perLineWordRanges = lines.map { line ->
                var off = 0
                line.words.map { w ->
                    val start = off
                    off += w.text.length + if (TextMetrics.isCjk(w.text.lastOrNull()?.code ?: 0) ||
                        (line.words.lastOrNull() === w)) 0 else 1
                    start until start + w.text.length
                }
            }
            val layout = layoutEngine.layout(
                displayLines, perLineWordRanges, style, density, canvasW, canvasH, safeArea,
                clip.transform.position.xFraction, clip.transform.position.yFraction,
                highlightWordGlobalIndex = highlight?.activeWordIndex,
            )

            val anim = CaptionAnimationEngine.state(clip, clockUs, clip.animation)
            val trk = tracking[clip.id]?.sampleAt(clockUs)
            val karaoke = if (clip.animation?.type == CaptionAnimationType.KARAOKE_FILL) highlight?.wordProgress else null

            out += RenderableCaption(
                clip, style, layout, anim, highlight,
                scale = clip.transform.scale * anim.pulseScale * (trk?.scale ?: 1f),
                rotationDegrees = clip.transform.rotationDegrees + (trk?.rotationDegrees ?: 0f),
                karaokeProgress = karaoke,
            ).let { rc ->
                val dy = anim.offsetYFraction + (trk?.offsetYFraction ?: 0f)
                rc.copy(layout = rc.layout.copy(
                    anchorYPx = rc.layout.anchorYPx + dy * canvasH,
                    anchorXPx = rc.layout.anchorXPx + (trk?.offsetXFraction ?: 0f) * canvasW,
                ))
            }
        }
        return out
    }
}
