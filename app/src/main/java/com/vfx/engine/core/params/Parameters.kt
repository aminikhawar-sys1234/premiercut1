package com.vfx.engine.core.params

import com.vfx.engine.core.curve.Curve
import com.vfx.engine.core.math.Mat4

/**
 * Type-tagged parameter descriptors.
 * Adding new effects NEVER requires modifying the core renderer —
 * effects simply declare their own ParamDescriptor lists.
 */
sealed class ParamDescriptor<T>(
    val id: String,
    val label: String,
    val default: T,
    val jsonType: String
) {
    abstract fun validate(value: Any?): Boolean
    abstract fun coerce(value: Any?): T
    abstract fun lerp(a: T, b: T, t: Float): T

    class FloatP(
        id: String, label: String,
        val min: Float, val max: Float,
        val step: Float = 0f,
        default: Float = min
    ) : ParamDescriptor<Float>(id, label, default.coerceIn(min, max), "float") {
        override fun validate(v: Any?) = v is Float && v >= min && v <= max && v.isFinite()
        override fun coerce(v: Any?) = when (v) {
            is Float -> v.coerceIn(min, max)
            is Int -> v.toFloat().coerceIn(min, max)
            is Number -> v.toDouble().toFloat().coerceIn(min, max)
            else -> throw IllegalArgumentException("Expected float for '$id'")
        }
        override fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    }

    class IntP(id: String, label: String, val min: Int, val max: Int, default: Int = min)
        : ParamDescriptor<Int>(id, label, default.coerceIn(min, max), "int") {
        override fun validate(v: Any?) = v is Int && v in min..max
        override fun coerce(v: Any?) = when (v) {
            is Int -> v.coerceIn(min, max)
            is Number -> v.toInt().coerceIn(min, max)
            else -> throw IllegalArgumentException("Expected int for '$id'")
        }
        override fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
    }

    class BoolP(id: String, label: String, default: Boolean = false)
        : ParamDescriptor<Boolean>(id, label, default, "bool") {
        override fun validate(v: Any?) = v is Boolean
        override fun coerce(v: Any?) = v as? Boolean ?: throw IllegalArgumentException("Expected bool for '$id'")
        override fun lerp(a: Boolean, b: Boolean, t: Float) = if (t < 0.5f) a else b
    }

    class EnumP(id: String, label: String, val options: List<String>, defaultIndex: Int = 0)
        : ParamDescriptor<String>(id, label, options[defaultIndex.coerceIn(0, options.size - 1)], "enum") {
        init { require(options.isNotEmpty()) { "Enum '$id' needs at least one option" } }
        override fun validate(v: Any?) = v is String && v in options
        override fun coerce(v: Any?) = (v as? String)?.takeIf { it in options }
            ?: throw IllegalArgumentException("'$v' not in options for '$id'")
        override fun lerp(a: String, b: String, t: Float) = if (t < 0.5f) a else b
    }

    /** RGBA in 0..1. */
    class ColorP(id: String, label: String, default: FloatArray = floatArrayOf(1f, 1f, 1f, 1f))
        : ParamDescriptor<FloatArray>(id, label, default, "color") {
        override fun validate(v: Any?) = v is FloatArray && v.size == 4 && v.all { it.isFinite() }
        override fun coerce(v: Any?) = (v as? FloatArray)?.also { require(it.size == 4) { "Expected rgba[4] for '$id'" } }
            ?: throw IllegalArgumentException("Expected rgba[4] for '$id'")
        override fun lerp(a: FloatArray, b: FloatArray, t: Float) = FloatArray(4) { a[it] + (b[it] - a[it]) * t }
    }

    class Vec2P(id: String, label: String, default: FloatArray = floatArrayOf(0f, 0f))
        : ParamDescriptor<FloatArray>(id, label, default, "vec2") {
        override fun validate(v: Any?) = v is FloatArray && v.size == 2
        override fun coerce(v: Any?) = (v as? FloatArray)?.also { require(it.size == 2) { "Expected vec2 for '$id'" } }
            ?: throw IllegalArgumentException("Expected vec2 for '$id'")
        override fun lerp(a: FloatArray, b: FloatArray, t: Float) = FloatArray(2) { a[it] + (b[it] - a[it]) * t }
    }

    class Vec3P(id: String, label: String, default: FloatArray = floatArrayOf(0f, 0f, 0f))
        : ParamDescriptor<FloatArray>(id, label, default, "vec3") {
        override fun validate(v: Any?) = v is FloatArray && v.size == 3
        override fun coerce(v: Any?) = (v as? FloatArray)?.also { require(it.size == 3) { "Expected vec3 for '$id'" } }
            ?: throw IllegalArgumentException("Expected vec3 for '$id'")
        override fun lerp(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { a[it] + (b[it] - a[it]) * t }
    }

    class Vec4P(id: String, label: String, default: FloatArray = floatArrayOf(0f, 0f, 0f, 0f))
        : ParamDescriptor<FloatArray>(id, label, default, "vec4") {
        override fun validate(v: Any?) = v is FloatArray && v.size == 4
        override fun coerce(v: Any?) = (v as? FloatArray)?.also { require(it.size == 4) { "Expected vec4 for '$id'" } }
            ?: throw IllegalArgumentException("Expected vec4 for '$id'")
        override fun lerp(a: FloatArray, b: FloatArray, t: Float) = FloatArray(4) { a[it] + (b[it] - a[it]) * t }
    }

    class Mat4P(id: String, label: String, default: Mat4 = Mat4())
        : ParamDescriptor<Mat4>(id, label, default, "mat4") {
        override fun validate(v: Any?) = v is Mat4
        override fun coerce(v: Any?) = v as? Mat4 ?: throw IllegalArgumentException("Expected mat4 for '$id'")
        override fun lerp(a: Mat4, b: Mat4, t: Float) = a // matrix interpolation out of scope
    }

    /** Reference to a host-managed texture (LUT, displacement map, mask source). */
    class TextureP(id: String, label: String) : ParamDescriptor<Int>(id, label, -1, "texture") {
        override fun validate(v: Any?) = v is Int && v >= 0
        override fun coerce(v: Any?) = v as? Int ?: throw IllegalArgumentException("Expected texture handle for '$id'")
        override fun lerp(a: Int, b: Int, t: Float) = if (t < 0.5f) a else b
    }

    class CurveP(id: String, label: String, default: Curve = Curve.LINEAR)
        : ParamDescriptor<Curve>(id, label, default, "curve") {
        override fun validate(v: Any?) = v is Curve && v.points.size >= 2
        override fun coerce(v: Any?) = v as? Curve ?: throw IllegalArgumentException("Expected curve for '$id'")
        override fun lerp(a: Curve, b: Curve, t: Float) = if (t < 0.5f) a else b
    }

    /** Gradient as sorted stops of (position, rgba). */
    class GradientP(
        id: String, label: String,
        default: List<Pair<Float, FloatArray>> = listOf(
            0f to floatArrayOf(0f, 0f, 0f, 1f),
            1f to floatArrayOf(1f, 1f, 1f, 1f))
    ) : ParamDescriptor<List<Pair<Float, FloatArray>>>(id, label, default, "gradient") {
        override fun validate(v: Any?) = v is List<*> && v.isNotEmpty()
        @Suppress("UNCHECKED_CAST")
        override fun coerce(v: Any?) = (v as? List<Pair<Float, FloatArray>>)?.sortedBy { it.first }
            ?: throw IllegalArgumentException("Expected gradient for '$id'")
        override fun lerp(a: List<Pair<Float, FloatArray>>, b: List<Pair<Float, FloatArray>>, t: Float) =
            if (t < 0.5f) a else b
    }

    class StringP(id: String, label: String, default: String = "")
        : ParamDescriptor<String>(id, label, default, "string") {
        override fun validate(v: Any?) = v is String
        override fun coerce(v: Any?) = v?.toString() ?: default
        override fun lerp(a: String, b: String, t: Float) = if (t < 0.5f) a else b
    }
}

typealias FloatP = ParamDescriptor.FloatP
typealias IntP = ParamDescriptor.IntP
typealias BoolP = ParamDescriptor.BoolP
typealias EnumP = ParamDescriptor.EnumP
typealias ColorP = ParamDescriptor.ColorP
typealias Vec2P = ParamDescriptor.Vec2P
typealias Vec3P = ParamDescriptor.Vec3P
typealias Vec4P = ParamDescriptor.Vec4P
typealias Mat4P = ParamDescriptor.Mat4P
typealias TextureP = ParamDescriptor.TextureP
typealias CurveP = ParamDescriptor.CurveP
typealias GradientP = ParamDescriptor.GradientP
typealias StringP = ParamDescriptor.StringP

/** Live value of one parameter: static value + optional keyframe track. */
class ParamValue<T>(val descriptor: ParamDescriptor<T>, staticValue: T) {
    var static: T = descriptor.coerce(staticValue); private set
    var track: KeyframeTrack<T>? = null

    fun set(value: T) { static = descriptor.coerce(value); track = null }
    fun setKeyframed(newTrack: KeyframeTrack<T>) { track = newTrack }

    /** Resolve at a media timestamp. Keyframe track wins over static when non-empty. */
    fun valueAt(timeUs: Long): T {
        val tr = track ?: return static
        if (tr.isEmpty) return static
        return descriptor.coerce(tr.evaluate(timeUs))
    }

    fun reset() { static = descriptor.default; track = null }
}
