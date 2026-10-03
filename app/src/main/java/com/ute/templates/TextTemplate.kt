package com.ute.templates

import com.ute.core.TimeRange
import com.ute.effects.EffectSpec
import com.ute.model.*
import com.ute.motion.MotionSpec

/**
 * A template is pure data (JSON-serializable). Applying it produces a fully
 * editable TextLayer — no rendering code knows any template exists.
 */
data class TextTemplate(
    val id: String,
    val category: Category,
    val name: String,
    val defaultText: String,
    val language: String = "en",
    val direction: Direction = Direction.AUTO,
    val style: TextStyle = TextStyle(),
    val appearance: Appearance = Appearance(),
    val layout: LayoutConfig = LayoutConfig(),
    val transform: Transform2D = Transform2D(),
    val motion: MotionSpec? = null,
    val timing: TimeRange = TimeRange(0.0, 5.0),
    val threeD: Text3DConfig? = null,
    val effects: List<EffectSpec> = emptyList(),
    /** Parameter slots the host UI can expose: "text", "color", "size"… */
    val editableParameters: List<EditableParameter> = listOf(EditableParameter.TEXT),
) {
    enum class Category { SOCIAL, CINEMATIC, MOTION, BUSINESS, CREATIVE }

    data class EditableParameter(val key: Key, val label: String) {
        enum class Key { TEXT, FILL_COLOR, FONT_SIZE, MOTION_PRESET }
        companion object { val TEXT = EditableParameter(Key.TEXT, "Text") }
    }
}
