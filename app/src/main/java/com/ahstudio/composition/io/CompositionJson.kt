package com.ahstudio.composition.io

import com.ahstudio.composition.core.TimeMapper
import com.ahstudio.composition.graph.*
import org.json.JSONArray
import org.json.JSONObject

object CompositionJson {

    // ---------- tracks ----------
    fun floatTrackToJson(t: PropertyTrack<Float>): JSONObject = JSONObject().put("s", t.staticValue.toDouble()).put("kf", JSONArray().apply {
        t.keyframes.forEach { put(JSONObject().put("t", it.timeUs).put("v", it.value.toDouble()).put("i", it.interp.name)) }
    })

    fun floatTrackFromJson(o: JSONObject): PropertyTrack<Float> = PropertyTrack(
        o.getDouble("s").toFloat(),
        o.optJSONArray("kf")?.let { arr ->
            (0 until arr.length()).map { k ->
                val x = arr.getJSONObject(k)
                PropertyTrack.Kf(x.getLong("t"), x.getDouble("v").toFloat(), PropertyTrack.Interp.valueOf(x.getString("i")))
            }
        } ?: emptyList()
    )

    fun transformToJson(t: Transform2D): JSONObject = JSONObject()
        .put("px", floatTrackToJson(t.positionX)).put("py", floatTrackToJson(t.positionY))
        .put("sx", floatTrackToJson(t.scaleX)).put("sy", floatTrackToJson(t.scaleY))
        .put("rot", floatTrackToJson(t.rotationDeg)).put("ax", floatTrackToJson(t.anchorX)).put("ay", floatTrackToJson(t.anchorY))
        .put("kx", floatTrackToJson(t.skewX)).put("ky", floatTrackToJson(t.skewY))
        .put("fh", t.flipH).put("fv", t.flipV)

    fun transformFromJson(o: JSONObject): Transform2D = Transform2D(
        floatTrackFromJson(o.getJSONObject("px")), floatTrackFromJson(o.getJSONObject("py")),
        floatTrackFromJson(o.getJSONObject("sx")), floatTrackFromJson(o.getJSONObject("sy")),
        floatTrackFromJson(o.getJSONObject("rot")), floatTrackFromJson(o.getJSONObject("ax")), floatTrackFromJson(o.getJSONObject("ay")),
        floatTrackFromJson(o.getJSONObject("kx")), floatTrackFromJson(o.getJSONObject("ky")),
        o.getBoolean("fh"), o.getBoolean("fv")
    )

    // ---------- path / mask ----------
    fun pathToJson(p: PathData): JSONArray = JSONArray().apply {
        p.commands.forEach { c ->
            put(when (c) {
                is PathData.Cmd.M -> JSONObject().put("c", "M").put("x", c.x.toDouble()).put("y", c.y.toDouble())
                is PathData.Cmd.L -> JSONObject().put("c", "L").put("x", c.x.toDouble()).put("y", c.y.toDouble())
                is PathData.Cmd.Q -> JSONObject().put("c", "Q").put("cx", c.cx.toDouble()).put("cy", c.cy.toDouble()).put("x", c.x.toDouble()).put("y", c.y.toDouble())
                is PathData.Cmd.C -> JSONObject().put("c", "C").put("c1x", c.c1x.toDouble()).put("c1y", c.c1y.toDouble()).put("c2x", c.c2x.toDouble()).put("c2y", c.c2y.toDouble()).put("x", c.x.toDouble()).put("y", c.y.toDouble())
                PathData.Cmd.Z -> JSONObject().put("c", "Z")
            })
        }
    }

    fun pathFromJson(arr: JSONArray): PathData = PathData((0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        when (o.getString("c")) {
            "M" -> PathData.Cmd.M(o.getDouble("x").toFloat(), o.getDouble("y").toFloat())
            "L" -> PathData.Cmd.L(o.getDouble("x").toFloat(), o.getDouble("y").toFloat())
            "Q" -> PathData.Cmd.Q(o.getDouble("cx").toFloat(), o.getDouble("cy").toFloat(), o.getDouble("x").toFloat(), o.getDouble("y").toFloat())
            "C" -> PathData.Cmd.C(o.getDouble("c1x").toFloat(), o.getDouble("c1y").toFloat(), o.getDouble("c2x").toFloat(), o.getDouble("c2y").toFloat(), o.getDouble("x").toFloat(), o.getDouble("y").toFloat())
            else -> PathData.Cmd.Z
        }
    })

    fun maskToJson(m: MaskInstance): JSONObject = JSONObject()
        .put("id", m.id).put("path", pathToJson(m.path))
        .put("mode", m.mode.name).put("opacity", m.opacity.toDouble()).put("feather", m.featherPx.toDouble())
        .put("expansion", m.expansionPx.toDouble()).put("inv", m.inverted)

    fun maskFromJson(o: JSONObject): MaskInstance = MaskInstance(
        o.getLong("id"), pathFromJson(o.getJSONArray("path")),
        MaskMode.valueOf(o.getString("mode")), o.getDouble("opacity").toFloat(), o.getDouble("feather").toFloat(),
        o.getDouble("expansion").toFloat(), o.getBoolean("inv")
    )

    // ---------- time mapper ----------
    fun mapperToJson(m: TimeMapper): JSONObject = when (m) {
        is TimeMapper.Identity -> JSONObject().put("k", "id")
        is TimeMapper.Reverse -> JSONObject().put("k", "rev").put("in", m.inUs).put("out", m.outUs)
        is TimeMapper.Speed -> JSONObject().put("k", "spd").put("in", m.inUs).put("f", m.factor.toDouble())
        is TimeMapper.Segmented -> JSONObject().put("k", "seg").put("s", JSONArray().apply {
            m.segments.forEach { put(JSONObject().put("ps", it.parentStartUs).put("cs", it.childStartUs).put("sp", it.speed.toDouble())) }
        })
    }

    fun mapperFromJson(o: JSONObject): TimeMapper = when (o.getString("k")) {
        "rev" -> TimeMapper.Reverse(o.getLong("in"), o.getLong("out"))
        "spd" -> TimeMapper.Speed(o.getLong("in"), o.getDouble("f").toFloat())
        "seg" -> TimeMapper.Segmented(o.getJSONArray("s").let { arr ->
            (0 until arr.length()).map { i ->
                val x = arr.getJSONObject(i)
                TimeMapper.Segmented.Segment(x.getLong("ps"), x.getLong("cs"), x.getDouble("sp").toFloat())
            }
        })
        else -> TimeMapper.Identity
    }

    // ---------- payload ----------
    private fun payloadToJson(p: LayerPayload): JSONObject = when (p) {
        is LayerPayload.Video -> JSONObject().put("k", "video").put("uri", p.sourceUri).put("ti", p.trimInUs).put("to", p.trimOutUs).put("sp", p.speed.toDouble())
        is LayerPayload.Image -> JSONObject().put("k", "image").put("uri", p.sourceUri)
        is LayerPayload.Text -> JSONObject().put("k", "text").put("spec", p.textSpec)
        is LayerPayload.Shape -> JSONObject().put("k", "shape").put("path", pathToJson(p.pathData))
            .put("fill", p.fillColor).put("stroke", p.strokeColor).put("sw", p.strokeWidth.toDouble())
        is LayerPayload.PreComp -> JSONObject().put("k", "precomp").put("ref", p.compositionRef.value).put("map", mapperToJson(p.timeMapper))
        LayerPayload.Null -> JSONObject().put("k", "null")
    }

    private fun payloadFromJson(o: JSONObject): LayerPayload = when (o.getString("k")) {
        "video" -> LayerPayload.Video(o.getString("uri"), o.getLong("ti"), o.getLong("to"), o.getDouble("sp").toFloat())
        "image" -> LayerPayload.Image(o.getString("uri"))
        "text" -> LayerPayload.Text(o.getString("spec"))
        "shape" -> LayerPayload.Shape(pathFromJson(o.getJSONArray("path")), o.getLong("fill"), o.getLong("stroke"), o.getDouble("sw").toFloat())
        "precomp" -> LayerPayload.PreComp(CompositionId(o.getLong("ref")), mapperFromJson(o.getJSONObject("map")))
        else -> LayerPayload.Null
    }

    // ---------- layer / graph ----------
    fun layerToJson(l: CompositionLayer): JSONObject = JSONObject()
        .put("id", l.id.value).put("type", l.type.name).put("name", l.name)
        .put("s", l.startTimeUs).put("e", l.endTimeUs).put("ins", l.insertionIndex).put("z", l.zOrder)
        .put("vis", l.visible).put("en", l.enabled).put("parent", l.parentId?.value ?: -1)
        .put("tr", transformToJson(l.transform)).put("op", floatTrackToJson(l.opacity))
        .put("blend", l.blendMode.name).put("cop", l.compositeOp.name)
        .put("matteL", l.trackMatteLayer?.value ?: -1).put("matteM", l.trackMatteMode.name)
        .put("masks", JSONArray().apply { l.masks.forEach { put(maskToJson(it)) } })
        .put("fx", JSONArray(l.effectIds)).put("payload", payloadToJson(l.payload)).put("ver", l.sourceVersion)

    fun layerFromJson(o: JSONObject): CompositionLayer = CompositionLayer(
        LayerId(o.getLong("id")), LayerType.valueOf(o.getString("type")), o.getString("name"),
        o.getLong("s"), o.getLong("e"), o.getInt("ins"), o.getInt("z"),
        o.getBoolean("vis"), o.getBoolean("en"),
        o.getLong("parent").takeIf { it >= 0 }?.let { LayerId(it) },
        transformFromJson(o.getJSONObject("tr")), floatTrackFromJson(o.getJSONObject("op")),
        BlendMode.valueOf(o.getString("blend")), CompositeOp.valueOf(o.getString("cop")),
        o.getLong("matteL").takeIf { it >= 0 }?.let { LayerId(it) }, TrackMatteMode.valueOf(o.getString("matteM")),
        o.getJSONArray("masks").let { arr -> (0 until arr.length()).map { maskFromJson(arr.getJSONObject(it)) } },
        o.getJSONArray("fx").let { arr -> (0 until arr.length()).map { arr.getLong(it) } },
        payloadFromJson(o.getJSONObject("payload")), o.getLong("ver")
    )

    fun graphToJson(g: CompositionGraph): JSONObject = JSONObject().put("compId", g.compositionId.value)
        .put("layers", JSONArray().apply { g.allLayers.forEach { put(layerToJson(it)) } })

    fun graphFromJson(text: String): CompositionGraph {
        val o = JSONObject(text)
        val g = CompositionGraph(CompositionId(o.getLong("compId")))
        o.getJSONArray("layers").let { arr -> for (i in 0 until arr.length()) g.addLayer(layerFromJson(arr.getJSONObject(i))) }
        return g
    }
}
