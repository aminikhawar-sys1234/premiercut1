package com.vfx.engine.core.mask

import com.vfx.engine.core.EffectEngineException
import com.vfx.engine.core.serialization.Json

/**
 * Mask shapes in normalized [0,1] UV space (origin: TOP-LEFT of image, y-down —
 * matches the presentation convention used by Transform2D).
 * All masks support feather, inversion and opacity; animated by attaching
 * keyframe tracks on the host side to the numeric fields before graph build.
 */
sealed class Mask {
    abstract var feather: Float     // edge softness in normalized units (0 = hard edge)
    abstract var invert: Boolean
    abstract var opacity: Float

    data class Rect(
        var cx: Float = 0.5f, var cy: Float = 0.5f,
        var w: Float = 0.5f, var h: Float = 0.5f,
        var cornerRadius: Float = 0f,
        var rotationDeg: Float = 0f,
        override var feather: Float = 0.05f,
        override var invert: Boolean = false,
        override var opacity: Float = 1f
    ) : Mask()

    data class Circle(
        var cx: Float = 0.5f, var cy: Float = 0.5f, var r: Float = 0.35f,
        override var feather: Float = 0.05f,
        override var invert: Boolean = false,
        override var opacity: Float = 1f
    ) : Mask()

    data class Ellipse(
        var cx: Float = 0.5f, var cy: Float = 0.5f,
        var rx: Float = 0.4f, var ry: Float = 0.3f,
        var rotationDeg: Float = 0f,
        override var feather: Float = 0.05f,
        override var invert: Boolean = false,
        override var opacity: Float = 1f
    ) : Mask()

    /** Linear gradient mask — softness IS (end - start); feather unused. */
    data class LinearGradient(
        var angleDeg: Float = 90f,
        var start: Float = 0.2f,
        var end: Float = 0.8f,
        override var feather: Float = 0f,
        override var invert: Boolean = false,
        override var opacity: Float = 1f
    ) : Mask()

    /** Radial gradient: full inside innerR, fades to 0 at outerR. */
    data class RadialGradient(
        var cx: Float = 0.5f, var cy: Float = 0.5f,
        var innerR: Float = 0.2f, var outerR: Float = 0.6f,
        override var feather: Float = 0f,
        override var invert: Boolean = false,
        override var opacity: Float = 1f
    ) : Mask()

    /** Host-provided mask texture — its .r channel drives the mask (white = show effect). */
    data class Texture(
        val textureHandle: Int,
        override var feather: Float = 0f,
        override var invert: Boolean = false,
        override var opacity: Float = 1f
    ) : Mask()
}

/** Portable JSON (de)serialization for masks — stable schema, version-safe. */
object MaskCodec {

    fun toJson(m: Mask): Json.Obj {
        val o = LinkedHashMap<String, Json>()
        o["type"] = Json.Str(
            when (m) {
                is Mask.Rect -> "rect"; is Mask.Circle -> "circle"; is Mask.Ellipse -> "ellipse"
                is Mask.LinearGradient -> "linear"; is Mask.RadialGradient -> "radial"
                is Mask.Texture -> "texture"
            }
        )
        o["feather"] = Json.Num(m.feather.toDouble())
        o["invert"] = Json.Bool(m.invert)
        o["opacity"] = Json.Num(m.opacity.toDouble())
        fun put(k: String, v: Float) { o[k] = Json.Num(v.toDouble()) }
        when (m) {
            is Mask.Rect -> {
                put("cx", m.cx); put("cy", m.cy); put("w", m.w); put("h", m.h)
                put("radius", m.cornerRadius); put("rot", m.rotationDeg)
            }
            is Mask.Circle -> { put("cx", m.cx); put("cy", m.cy); put("r", m.r) }
            is Mask.Ellipse -> {
                put("cx", m.cx); put("cy", m.cy); put("rx", m.rx); put("ry", m.ry); put("rot", m.rotationDeg)
            }
            is Mask.LinearGradient -> { put("angle", m.angleDeg); put("start", m.start); put("end", m.end) }
            is Mask.RadialGradient -> { put("cx", m.cx); put("cy", m.cy); put("r0", m.innerR); put("r1", m.outerR) }
            is Mask.Texture -> o["texture"] = Json.Num(m.textureHandle.toDouble())
        }
        return Json.Obj(o)
    }

    fun fromJson(j: Json.Obj): Mask {
        fun f(k: String, d: Float = 0f) = (j[k] as? Json.Num)?.value?.toFloat() ?: d
        fun b(k: String) = (j[k] as? Json.Bool)?.value == true
        return when ((j["type"] as? Json.Str)?.value) {
            "rect" -> Mask.Rect(
                f("cx", 0.5f), f("cy", 0.5f), f("w", 0.5f), f("h", 0.5f),
                f("radius"), f("rot"), f("feather", 0.05f), b("invert"), f("opacity", 1f))
            "circle" -> Mask.Circle(
                f("cx", 0.5f), f("cy", 0.5f), f("r", 0.35f),
                f("feather", 0.05f), b("invert"), f("opacity", 1f))
            "ellipse" -> Mask.Ellipse(
                f("cx", 0.5f), f("cy", 0.5f), f("rx", 0.4f), f("ry", 0.3f), f("rot"),
                f("feather", 0.05f), b("invert"), f("opacity", 1f))
            "linear" -> Mask.LinearGradient(
                f("angle", 90f), f("start", 0.2f), f("end", 0.8f),
                f("feather"), b("invert"), f("opacity", 1f))
            "radial" -> Mask.RadialGradient(
                f("cx", 0.5f), f("cy", 0.5f), f("r0", 0.2f), f("r1", 0.6f),
                f("feather"), b("invert"), f("opacity", 1f))
            "texture" -> Mask.Texture(
                f("texture").toInt(), f("feather"), b("invert"), f("opacity", 1f))
            else -> throw EffectEngineException.Serialization("Unknown mask type: ${j["type"]}")
        }
    }
}
