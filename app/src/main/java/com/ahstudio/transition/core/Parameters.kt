package com.ahstudio.transition.core

enum class ParamType { FLOAT, INT, BOOL, ENUM, COLOR, VEC2, VEC3, ANGLE, NORMALIZED }

sealed interface ParamValue {
    data class FloatValue(val value: Float) : ParamValue
    data class IntValue(val value: Int) : ParamValue
    data class BoolValue(val value: Boolean) : ParamValue
    data class EnumValue(val index: Int, val name: String) : ParamValue
    data class ColorValue(val rgba: List<Float>) : ParamValue
    data class Vec2Value(val values: List<Float>) : ParamValue
    data class Vec3Value(val values: List<Float>) : ParamValue
    data class AngleValue(val degrees: Float) : ParamValue          // wrapped to [0,360)
    data class NormalizedValue(val value: Float) : ParamValue       // clamped to [0,1]
}

data class TransitionParameterDefinition(
    val id: String,
    val displayName: String,
    val type: ParamType,
    val defaultValue: ParamValue,
    val min: ParamValue? = null,
    val max: ParamValue? = null,
    val step: ParamValue? = null,
    val animatable: Boolean = false,   // future keyframe hook; engine stays static unless host resolves it
    val enumValues: List<String> = emptyList(),
) {
    init {
        require(REGEX.matches(id)) {
            "Parameter id '$id' must match $REGEX — it becomes GLSL uniform u_$id"
        }
    }
    companion object { val REGEX = Regex("[a-zA-Z][a-zA-Z0-9_]{0,63}") }
}

object ParameterMath {
    private fun wrap360(d: Float) = ((d % 360f) + 360f) % 360f

    /** Coerces [value] to the definition's type and range. Second element: warning, or null. */
    fun coerce(value: ParamValue, def: TransitionParameterDefinition): Pair<ParamValue?, String?> {
        val fail: (String) -> Pair<ParamValue?, String?> = { null to it }
        return when (def.type) {
            ParamType.FLOAT -> {
                val v = (value as? ParamValue.FloatValue)?.value ?: return fail("Type mismatch for '${def.id}'")
                val lo = (def.min as? ParamValue.FloatValue)?.value ?: -Float.MAX_VALUE
                val hi = (def.max as? ParamValue.FloatValue)?.value ?: Float.MAX_VALUE
                ParamValue.FloatValue(v.coerceIn(lo, hi)) to null
            }
            ParamType.NORMALIZED -> {
                val v = (value as? ParamValue.FloatValue)?.value
                    ?: (value as? ParamValue.NormalizedValue)?.value
                    ?: return fail("Type mismatch for '${def.id}'")
                ParamValue.NormalizedValue(v.coerceIn(0f, 1f)) to null
            }
            ParamType.ANGLE -> {
                val v = (value as? ParamValue.FloatValue)?.value
                    ?: (value as? ParamValue.AngleValue)?.degrees
                    ?: return fail("Type mismatch for '${def.id}'")
                ParamValue.AngleValue(wrap360(v)) to null
            }
            ParamType.INT -> {
                val v = (value as? ParamValue.IntValue)?.value ?: return fail("Type mismatch for '${def.id}'")
                val lo = (def.min as? ParamValue.IntValue)?.value ?: Int.MIN_VALUE
                val hi = (def.max as? ParamValue.IntValue)?.value ?: Int.MAX_VALUE
                ParamValue.IntValue(v.coerceIn(lo, hi)) to null
            }
            ParamType.BOOL -> when (value) {
                is ParamValue.BoolValue -> value to null
                is ParamValue.IntValue -> ParamValue.BoolValue(value.value != 0) to null
                else -> fail("Type mismatch for '${def.id}'")
            }
            ParamType.ENUM -> {
                val idx = (value as? ParamValue.EnumValue)?.index
                    ?: (value as? ParamValue.IntValue)?.value
                    ?: return fail("Type mismatch for '${def.id}'")
                if (idx !in def.enumValues.indices) return fail("Enum index $idx out of range for '${def.id}'")
                ParamValue.EnumValue(idx, def.enumValues[idx]) to null
            }
            ParamType.COLOR -> {
                val c = (value as? ParamValue.ColorValue)?.rgba ?: return fail("Type mismatch for '${def.id}'")
                if (c.size != 4) return fail("COLOR needs 4 components for '${def.id}'")
                ParamValue.ColorValue(c.map { it.coerceIn(0f, 1f) }) to null
            }
            ParamType.VEC2 -> {
                val v = (value as? ParamValue.Vec2Value)?.values ?: return fail("Type mismatch for '${def.id}'")
                if (v.size != 2) return fail("VEC2 needs 2 components for '${def.id}'")
                ParamValue.Vec2Value(v) to null
            }
            ParamType.VEC3 -> {
                val v = (value as? ParamValue.Vec3Value)?.values ?: return fail("Type mismatch for '${def.id}'")
                if (v.size != 3) return fail("VEC3 needs 3 components for '${def.id}'")
                ParamValue.Vec3Value(v) to null
            }
        }
    }
}
