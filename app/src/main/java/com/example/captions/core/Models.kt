package com.ahstudio.captions.core.model

import com.ahstudio.captions.core.time.TimelineUs
import com.ahstudio.captions.core.time.TimelineUsSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class WordTimestampSource { NONE, ESTIMATED, MEASURED }

@Serializable
enum class ClipSource { RECOGNIZED, MANUAL, TRANSLATED, IMPORTED }

@Serializable
enum class TrackKind { PRIMARY, TRANSLATION, COMMENTARY }

@Serializable
enum class TextAlignment { CENTER, LEFT, RIGHT }

@Serializable
enum class CaptionAnimationType {
    NONE, FADE, SLIDE_UP, SLIDE_DOWN, SCALE, POP, TYPEWRITER, WORD_REVEAL, KARAOKE_FILL, PULSE, BOUNCE, WAVE
}

@Serializable
enum class TemplateCategory { MINIMAL, BOLD, KARAOKE, PODCAST, SOCIAL, NEWS, GAMING, CINEMATIC, EDUCATIONAL }

@Serializable
data class CaptionWord(
    val text: String,
    @Serializable(with = TimelineUsSerializer::class) val start: TimelineUs,
    @Serializable(with = TimelineUsSerializer::class) val end: TimelineUs,
    val confidence: Float = 1f,
    val speakerId: String? = null,
)

@Serializable
data class CaptionTiming(
    @Serializable(with = TimelineUsSerializer::class) val start: TimelineUs,
    @Serializable(with = TimelineUsSerializer::class) val end: TimelineUs,
) {
    val durationUs: Long get() = (end - start).micros.coerceAtLeast(0)
}

@Serializable
data class CaptionTransform(
    val position: CaptionPosition = CaptionPosition(),
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
)

@Serializable
data class CaptionPosition(
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.82f,
)

@Serializable
data class CaptionBackgroundSpec(
    val colorArgb: Long = 0xAA000000L,
    val opacity: Float = 1f,
)

@Serializable
data class CaptionStyle(
    val id: String = DEFAULT_ID,
    val name: String = "Default",
    val fontFamily: String = "sans-serif",
    val fontSizeSp: Float = 20f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val colorArgb: Long = 0xFFFFFFFFL,
    val opacity: Float = 1f,
    val strokeWidthDp: Float = 2f,
    val strokeColorArgb: Long = 0xFF000000L,
    val shadowRadiusDp: Float = 4f,
    val shadowDxDp: Float = 0f,
    val shadowDyDp: Float = 2f,
    val shadowColorArgb: Long = 0x80000000L,
    val background: CaptionBackgroundSpec? = CaptionBackgroundSpec(),
    val cornerRadiusDp: Float = 8f,
    val paddingDp: Float = 8f,
    val lineSpacingMultiplier: Float = 1.15f,
    val letterSpacingEm: Float = 0.0f,
    val alignment: TextAlignment = TextAlignment.CENTER,
) {
    companion object {
        const val DEFAULT_ID = "style.default"
        val DEFAULT = CaptionStyle()
    }
}

@Serializable
data class CaptionAnimationSpec(
    val type: CaptionAnimationType = CaptionAnimationType.NONE,
    val durationMs: Long = 250L,
    val exitType: CaptionAnimationType? = null,
)

@Serializable
data class CaptionLine(
    val words: List<CaptionWord>,
    @Serializable(with = TimelineUsSerializer::class) val start: TimelineUs,
    @Serializable(with = TimelineUsSerializer::class) val end: TimelineUs,
) {
    val text: String get() = com.ahstudio.captions.core.language.TextMetrics.joinWordsText(words.map { it.text })
}

@Serializable
data class CaptionClip(
    val id: String,
    val trackId: String,
    val words: List<CaptionWord>,
    val lineBreakIndices: List<Int> = listOf(0),
    val styleId: String = CaptionStyle.DEFAULT_ID,
    val animation: CaptionAnimationSpec? = null,
    val transform: CaptionTransform = CaptionTransform(),
    val timing: CaptionTiming,
    val source: ClipSource = ClipSource.RECOGNIZED,
) {
    val displayText: String get() = com.ahstudio.captions.core.language.TextMetrics.joinWordsText(words.map { it.text })

    val lines: List<CaptionLine> get() {
        if (words.isEmpty()) return emptyList()
        val sortedBreaks = lineBreakIndices.distinct().sorted()
        val out = ArrayList<CaptionLine>()
        for (i in sortedBreaks.indices) {
            val startIdx = sortedBreaks[i]
            val endIdx = if (i + 1 < sortedBreaks.size) sortedBreaks[i + 1] else words.size
            if (startIdx >= words.size) break
            val slice = words.subList(startIdx, minOf(endIdx, words.size))
            if (slice.isNotEmpty()) {
                out += CaptionLine(slice, slice.first().start, slice.last().end)
            }
        }
        return out
    }
}

@Serializable
data class CaptionTrack(
    val id: String,
    val name: String,
    val kind: TrackKind = TrackKind.PRIMARY,
    val languageTag: String = "en",
    val clips: List<CaptionClip> = emptyList(),
    val translationOfTrackId: String? = null,
) {
    fun clipsSorted(): List<CaptionClip> = clips.sortedBy { it.timing.start.micros }
}

@Serializable
data class CaptionSpeaker(
    val id: String,
    val label: String,
    val colorArgb: Long = 0xFF4A90D9L,
)

@Serializable
data class CaptionMetadata(
    val source: ClipSource = ClipSource.RECOGNIZED,
    val providerId: String? = null,
    val languageTag: String = "en",
    val wordTimestampSource: WordTimestampSource = WordTimestampSource.NONE,
    val mediaDurationUs: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class CaptionProject(
    val id: String,
    val metadata: CaptionMetadata = CaptionMetadata(),
    val tracks: List<CaptionTrack> = emptyList(),
    val styles: List<CaptionStyle> = listOf(CaptionStyle.DEFAULT),
    val speakers: List<CaptionSpeaker> = emptyList(),
) {
    fun findTrack(trackId: String): CaptionTrack? = tracks.firstOrNull { it.id == trackId }
    fun findClip(clipId: String): CaptionClip? = tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == clipId }
    fun styleFor(styleId: String): CaptionStyle = styles.firstOrNull { it.id == styleId } ?: CaptionStyle.DEFAULT

    fun withTrack(track: CaptionTrack): CaptionProject {
        val existing = tracks.indexOfFirst { it.id == track.id }
        val newTracks = if (existing >= 0) tracks.toMutableList().apply { set(existing, track) } else tracks + track
        return copy(tracks = newTracks)
    }

    fun updateClip(clipId: String, transform: (CaptionClip) -> CaptionClip): CaptionProject {
        val newTracks = tracks.map { track ->
            if (track.clips.none { it.id == clipId }) track
            else track.copy(clips = track.clips.map { if (it.id == clipId) transform(it) else it })
        }
        return copy(tracks = newTracks)
    }
}

@Serializable
data class CaptionTemplate(
    val id: String,
    val name: String,
    val category: TemplateCategory,
    val style: CaptionStyle,
    val animation: CaptionAnimationSpec? = null,
)
