package com.ute.serialization

import com.ute.animation.*
import com.ute.core.TextEngineException
import com.ute.core.TimeRange
import com.ute.model.*
import com.ute.motion.MotionSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable JSON codec with schema versioning. Round-trip safe for documents,
 * layers, styles, animations, materials, and 3D settings.
 */
object TextDocumentCodec {

    fun encode(doc: TextDocument): JSONObject = JSONObject().apply {
        put("schema", TextDocument.SCHEMA_VERSION)
        put("id", doc.id)
        put("durationSec", doc.durationSec)
        put("canvas", JSONObject().put("w", doc.canvasWidthPx).put("h", doc.canvasHeightPx))
        put("layers", JSONArray().apply { doc.layers.forEach { put(encodeLayer(it)) } })
    }

    fun decode(json: JSONObject): TextDocument = try {
        TextDocument(
            id = json.getString("id"),
            layers = json.getJSONArray("layers").let { arr ->
                (0 until arr.length()).map { decodeLayer(arr.getJSONObject(it)) }
            },
            durationSec = json.getDouble("durationSec"),
            canvasWidthPx = json.getJSONObject("canvas").getInt("w"),
            canvasHeightPx = json.getJSONObject("canvas").getInt("h"),
        )
    } catch (e: Exception) {
        throw TextEngineException.SerializationFailure("document decode failed", e)
    }

    fun encodeLayer(l: TextLayer): JSONObject = JSONObject().apply {
        put("id", l.id); put("name", l.name); put("text", l.content)
        put("language", l.language); put("direction", l.direction.name)
        put("style", encodeStyle(l.style))
        put("layout", encodeLayout(l.layout))
        put("appearance", encodeAppearance(l.appearance))
        put("transform", JSONObject().put("x", l.transform.x).put("y", l.transform.y)
            .put("sx", l.transform.scaleX).put("sy", l.transform.scaleY)
            .put("rot", l.transform.rotationDeg).put("opacity", l.transform.opacity))
        l.motion?.let { put("motion", encodeMotion(it)) }
        l.text3d?.let { put("threeD", JSONObject().put("depth", it.extrusionDepthPx)
            .put("bevel", it.bevelWidthPx).put("material", it.materialId)) }
        if (l.tracks.isNotEmpty()) put("tracks", encodeTracks(l.tracks))
        put("startSec", l.timing.startSec); put("durationSec", l.timing.durationSec)
    }

    fun decodeLayer(o: JSONObject): TextLayer = TextLayer(
        id = o.getString("id"), name = o.optString("name", "Text"),
        content = o.getString("text"), language = o.optString("language", "en"),
        direction = Direction.valueOf(o.optString("direction", "AUTO")),
        style = decodeStyle(o.getJSONObject("style")),
        layout = decodeLayout(o.optJSONObject("layout") ?: JSONObject()),
        appearance = decodeAppearance(o.optJSONObject("appearance") ?: JSONObject()),
        transform = o.optJSONObject("transform")?.let {
            Transform2D(it.optDouble("x", 0.0).toFloat(), it.optDouble("y", 0.0).toFloat(),
                it.optDouble("sx", 1.0).toFloat(), it.optDouble("sy", 1.0).toFloat(),
                it.optDouble("rot", 0.0).toFloat(), opacity = it.optDouble("opacity", 1.0).toFloat())
        } ?: Transform2D(),
        motion = o.optJSONObject("motion")?.let { decodeMotion(it) },
        text3d = o.optJSONObject("threeD")?.let {
            Text3DConfig(it.optDouble("depth", 20.0).toFloat(), it.optDouble("bevel", 2.0).toFloat(),
                materialId = it.optString("material", "matte"))
        },
        tracks = o.optJSONObject("tracks")?.let { decodeTracks(it) } ?: emptyMap(),
        timing = TimeRange(o.optDouble("startSec", 0.0), o.optDouble("durationSec", 5.0)),
    )

    fun encodeStyle(s: TextStyle): JSONObject = JSONObject().apply {
        put("fontFamily", s.fontFamily); put("sizePx", s.sizePx.toDouble())
        put("weight", s.weight.cssValue); put("italic", s.italic)
        put("tracking", s.letterSpacingEm.toDouble()); put("lineHeight", s.lineHeightMultiple.toDouble())
        if (s.fallbackFamilies.isNotEmpty()) put("fallbacks", JSONArray(s.fallbackFamilies))
        if (s.fontFeatures.isNotEmpty()) put("features", JSONObject(s.fontFeatures))
        if (s.variableAxes.isNotEmpty()) put("axes", JSONObject(s.variableAxes.mapValues { it.value.toDouble() }))
    }

    fun decodeStyle(o: JSONObject): TextStyle = TextStyle(
        fontFamily = o.optString("fontFamily", "sans-serif"),
        sizePx = o.optDouble("sizePx", 64.0).toFloat(),
        weight = FontWeight.fromCss(o.optInt("weight", 400)),
        italic = o.optBoolean("italic", false),
        letterSpacingEm = o.optDouble("tracking", 0.0).toFloat(),
        lineHeightMultiple = o.optDouble("lineHeight", 1.2).toFloat(),
        fallbackFamilies = o.optJSONArray("fallbacks")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        fontFeatures = o.optJSONObject("features")?.let { jo ->
            jo.keys().asSequence().associateWith { jo.optInt(it, 1) }
        } ?: emptyMap(),
        variableAxes = o.optJSONObject("axes")?.let { jo ->
            jo.keys().asSequence().associateWith { jo.optDouble(it, 0.0).toFloat() }
        } ?: emptyMap(),
    )

    fun encodeLayout(l: LayoutConfig): JSONObject = JSONObject().apply {
        put("widthPx", l.widthPx.toDouble()); put("heightPx", l.heightPx.toDouble())
        put("align", l.align.name); put("wrap", l.wrapMode.name); put("maxLines", l.maxLines)
    }
    fun decodeLayout(o: JSONObject): LayoutConfig = LayoutConfig(
        widthPx = o.optDouble("widthPx", 0.0).toFloat(), heightPx = o.optDouble("heightPx", 0.0).toFloat(),
        align = Align.valueOf(o.optString("align", "START")),
        wrapMode = WrapMode.valueOf(o.optString("wrap", "WORD")),
        maxLines = o.optInt("maxLines", 0),
    )

    fun encodeAppearance(a: Appearance): JSONObject = JSONObject().apply {
        put("fill", encodePaint(a.fill))
        a.outline?.let { put("outline", JSONObject().put("color", hex(it.color))
            .put("widthPx", it.widthPx.toDouble()).put("opacity", it.opacity.toDouble())) }
        a.shadow?.let { put("shadow", JSONObject().put("color", hex(it.color))
            .put("distancePx", it.distancePx.toDouble()).put("angleDeg", it.angleDeg.toDouble())
            .put("softnessPx", it.softnessPx.toDouble())) }
        a.glow?.let { put("glow", JSONObject().put("color", hex(it.color))
            .put("radiusPx", it.radiusPx.toDouble()).put("strength", it.strength.toDouble())) }
        a.background?.let { put("background", JSONObject().put("color", hex(it.color))
            .put("radius", it.cornerRadiusPx.toDouble())) }
        put("opacity", a.opacity.toDouble())
    }

    fun decodeAppearance(o: JSONObject): Appearance = Appearance(
        fill = o.optJSONObject("fill")?.let { decodePaint(it) } ?: PaintSpec.Solid(0xFFFFFFFF.toInt()),
        outline = o.optJSONObject("outline")?.let {
            OutlineSpec(parseHex(it.getString("color")), it.optDouble("widthPx", 4.0).toFloat(),
                it.optDouble("opacity", 1.0).toFloat())
        },
        shadow = o.optJSONObject("shadow")?.let {
            ShadowSpec(parseHex(it.getString("color")), it.optDouble("distancePx", 6.0).toFloat(),
                it.optDouble("angleDeg", 45.0).toFloat(), it.optDouble("softnessPx", 8.0).toFloat())
        },
        glow = o.optJSONObject("glow")?.let {
            GlowSpec(parseHex(it.getString("color")), it.optDouble("radiusPx", 12.0).toFloat(),
                it.optDouble("strength", 1.0).toFloat())
        },
        background = o.optJSONObject("background")?.let {
            BackgroundSpec(parseHex(it.getString("color")), it.optDouble("radius", 8.0).toFloat())
        },
        opacity = o.optDouble("opacity", 1.0).toFloat(),
    )

    private fun encodePaint(p: PaintSpec): JSONObject = JSONObject().apply {
        when (p) {
            is PaintSpec.Solid -> put("type", "solid").put("color", hex(p.color))
            is PaintSpec.Gradient -> put("type", "gradient").put("gradient", JSONObject().apply {
                put("type", p.gradient.type.name)
                put("stops", JSONArray().apply { p.gradient.stops.forEach {
                    put(JSONObject().put("position", it.position.toDouble()).put("color", hex(it.color))) } })
            })
            is PaintSpec.Material -> put("type", "material").put("id", p.materialId).put("tint", hex(p.tint))
        }
    }

    private fun decodePaint(o: JSONObject): PaintSpec = when (o.optString("type", "solid")) {
        "gradient" -> PaintSpec.Gradient(o.getJSONObject("gradient").let { g ->
            GradientSpec(GradientType.valueOf(g.optString("type", "LINEAR")),
                g.getJSONArray("stops").let { a -> (0 until a.length()).map {
                    val s = a.getJSONObject(it)
                    GradientStop(s.getDouble("position").toFloat(), parseHex(s.getString("color")))
                } })
        })
        "material" -> PaintSpec.Material(o.getString("id"), parseHex(o.optString("tint", "#FFFFFFFF")))
        else -> PaintSpec.Solid(parseHex(o.getString("color")))
    }

    fun encodeMotion(m: MotionSpec): JSONObject = JSONObject().apply {
        put("preset", m.preset.name); put("unit", m.unit.name)
        put("durationSec", m.durationSec); put("staggerSec", m.staggerSec)
        put("direction", m.direction.name); put("seed", m.seed)
    }
    fun decodeMotion(o: JSONObject): MotionSpec = MotionSpec(
        preset = com.ute.motion.MotionPreset.valueOf(o.getString("preset")),
        unit = com.ute.motion.MotionUnit.valueOf(o.optString("unit", "CHARACTER")),
        durationSec = o.optDouble("durationSec", 0.6),
        staggerSec = o.optDouble("staggerSec", 0.05),
        direction = com.ute.motion.RevealDirection.valueOf(o.optString("direction", "LTR")),
        seed = o.optInt("seed", 1),
    )

    fun encodeTracks(tracks: Map<AnimatableProperty, List<KeyframeSpec>>): JSONObject = JSONObject().apply {
        tracks.forEach { (prop, keys) ->
            put(prop.name, JSONArray().apply { keys.forEach {
                put(JSONObject().put("t", it.timeSec).put("v", it.value.toDouble())) } })
        }
    }
    fun decodeTracks(o: JSONObject): Map<AnimatableProperty, List<KeyframeSpec>> =
        o.keys().asSequence().associate { key ->
            val arr = o.getJSONArray(key)
            AnimatableProperty.valueOf(key) to (0 until arr.length()).map {
                val k = arr.getJSONObject(it)
                KeyframeSpec(k.getDouble("t"), k.getDouble("v").toFloat(), Easing.EaseInOut)
            }
        }

    private fun hex(c: Int) = String.format("#%08X", c)

    private fun parseHex(s: String): Int = try {
        val clean = s.removePrefix("#")
        val v = clean.toLong(16)
        if (clean.length <= 6) {
            (0xFF000000.toLong() or v).toInt()
        } else {
            v.toInt()
        }
    } catch (e: Exception) {
        0xFFFFFFFF.toInt()
    }
}
