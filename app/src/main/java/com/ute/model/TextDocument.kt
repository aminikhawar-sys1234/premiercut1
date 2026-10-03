package com.ute.model

import com.ute.animation.AnimatableProperty
import com.ute.animation.KeyframeSpec
import com.ute.core.TimeRange
import com.ute.effects.DeformSpec
import com.ute.effects.EffectSpec
import com.ute.effects.MaskSpec
import com.ute.effects.PathSpec
import com.ute.motion.MotionSpec

data class TextDocument(
    val id: String,
    val layers: List<TextLayer>,
    val durationSec: Double,
    val canvasWidthPx: Int,
    val canvasHeightPx: Int,
    val version: Int = SCHEMA_VERSION,
) {
    companion object { const val SCHEMA_VERSION = 1 }
    fun layersVisibleAt(tSec: Double) = layers.filter { it.timing.contains(tSec) }
}

/**
 * A text layer is fully self-describing: content, language, style, layout, appearance,
 * animation, 3D, effects, mask, timing. Everything is data — nothing is hardcoded
 * into rendering code — which is what makes templates and serialization possible.
 */
data class TextLayer(
    val id: String,
    val name: String = "Text",
    val content: String,
    val language: String = "en",                 // BCP-47
    val direction: Direction = Direction.AUTO,   // resolved by BidiEngine at layout time
    val spans: List<TextSpan> = emptyList(),     // per-range style overrides
    val style: TextStyle = TextStyle(),
    val layout: LayoutConfig = LayoutConfig(),
    val transform: Transform2D = Transform2D(),
    val appearance: Appearance = Appearance(),
    val effects: List<EffectSpec> = emptyList(),
    val mask: MaskSpec? = null,
    val motion: MotionSpec? = null,
    val tracks: Map<AnimatableProperty, List<KeyframeSpec>> = emptyMap(),
    val text3d: Text3DConfig? = null,
    val pathSpec: PathSpec? = null,
    val deform: DeformSpec? = null,
    val timing: TimeRange = TimeRange(0.0, 5.0),
    val locked: Boolean = false,
)

/** Range-based style override over [startCodeUnit, endCodeUnit) in UTF-16 indices. */
data class TextSpan(
    val start: Int,
    val end: Int,
    val styleOverride: TextStyle,
)

enum class Direction { LTR, RTL, AUTO }

/** Paragraph-split content model: '\n' delimits paragraphs. */
val TextLayer.paragraphs: List<String> get() = content.split('\n')
