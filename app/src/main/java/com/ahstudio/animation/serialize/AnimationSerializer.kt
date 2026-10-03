package com.ahstudio.animation.serialize

import com.ahstudio.animation.core.AnimationClip
import com.ahstudio.animation.core.AnimationEngine
import com.ahstudio.animation.core.AnimationMarker
import com.ahstudio.animation.core.AnimationTrack
import com.ahstudio.animation.core.Boundary
import com.ahstudio.animation.core.ClipTrack
import com.ahstudio.animation.curves.CubicBezierTiming
import com.ahstudio.animation.easing.EasingType
import com.ahstudio.animation.keyframes.*
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.properties.BindingKey
import com.ahstudio.animation.properties.PropertyType
import com.ahstudio.animation.procedural.Spring
import com.ahstudio.animation.procedural.Wiggle
import com.ahstudio.animation.time.LoopMode
import org.json.JSONArray
import org.json.JSONObject

data class LoadReport(
    val issues: List<ValidationIssue>,
    val tracksLoaded: Int,
    val markersLoaded: Int,
    val clipsLoaded: Int
) {
    val clean: Boolean get() = issues.isEmpty()
}

fun interface AnimationMigrator { fun migrate(json: JSONObject): JSONObject }

object AnimationSerializer {
    const val VERSION = 1

    private val migrators = LinkedHashMap<Int, AnimationMigrator>()
    fun registerMigrator(fromVersion: Int, m: AnimationMigrator) { migrators[fromVersion] = m }

    // ---------------- serialize ----------------
    fun serialize(engine: AnimationEngine): JSONObject {
        val root = JSONObject()
        root.put("animationVersion", VERSION)
        root.put("autoKey", engine.autoKeyframeEnabled)
        val arr = JSONArray()
        for ((key, tr) in engine.allTracks()) arr.put(writeTrack(key, tr.get()))
        root.put("tracks", arr)
        val src = JSONArray()
        for (k in engine.activeSourceKeys()) writeSource(engine, k, src)
        root.put("sources", src)
        val cArr = JSONArray()
        for (c in engine.clips()) cArr.put(writeClip(c))
        root.put("clips", cArr)
        val mArr = JSONArray()
        for (m in engine.markers()) mArr.put(writeMarker(m))
        root.put("markers", mArr)
        return root
    }

    private fun writeTrack(key: BindingKey, st: AnimationTrack.State): JSONObject {
        val o = JSONObject()
        o.put("target", key.targetId); o.put("property", key.property)
        o.put("type", st.type.name); o.put("enabled", st.enabled); o.put("spatial", st.spatial)
        st.rangeStartMs?.let { o.put("rs", it) }; st.rangeEndMs?.let { o.put("re", it) }
        o.put("before", st.beforeRange.name); o.put("after", st.afterRange.name)
        o.put("loop", st.loop.name); o.put("repeat", st.repeatCount); o.put("speed", st.speed)
        st.customTimeCurve?.let { o.put("tc", writeKfs(it.keyframes)) }
        o.put("kfs", writeKfs(st.data.keyframes))
        return o
    }

    private fun writeKfs(kfs: List<Keyframe>): JSONArray {
        val a = JSONArray()
        for (kf in kfs) {
            val o = JSONObject()
            o.put("t", kf.timeMs)
            if (kf.vecValue != null) { o.put("vx", kf.vecValue!!.x); o.put("vy", kf.vecValue!!.y) }
            else o.put("v", kf.value)
            if (kf.interpolation != InterpolationType.LINEAR) o.put("i", kf.interpolation.name)
            if (kf.easing != EasingType.LINEAR) o.put("e", kf.easing.name)
            if (kf.tangentMode != TangentMode.AUTO) o.put("tm", kf.tangentMode.name)
            if (kf.inTangent != 0.0) o.put("tin", kf.inTangent)
            if (kf.outTangent != 0.0) o.put("tout", kf.outTangent)
            kf.spatialInHandle?.let { o.put("hin", JSONArray().put(it.x).put(it.y)) }
            kf.spatialOutHandle?.let { o.put("hout", JSONArray().put(it.x).put(it.y)) }
            kf.bezier?.let { o.put("bez", JSONArray().put(it.x1()).put(it.y1()).put(it.x2()).put(it.y2())) }
            if (kf.metadata.isNotEmpty()) o.put("md", JSONObject(kf.metadata))
            a.put(o)
        }
        return a
    }

    // ---------------- deserialize ----------------
    fun load(engine: AnimationEngine, inputJson: JSONObject): LoadReport {
        var json = inputJson
        val issues = ArrayList<ValidationIssue>()
        var v = json.optInt("animationVersion", -1)
        if (v < 0) { issues.add(ValidationIssue.SkippedEntry("missing animationVersion")); v = 1 }
        while (v < VERSION) {
            val m = migrators[v]
            if (m == null) { issues.add(ValidationIssue.SkippedEntry("no migrator $v->${v + 1}")); break }
            val migrated = m.migrate(json); json = migrated; v = json.optInt("animationVersion", v + 1)
        }
        if (json.optInt("animationVersion", -1) > VERSION) {
            issues.add(ValidationIssue.UnsupportedVersion(json.optInt("animationVersion")))
            return LoadReport(issues, 0, 0, 0)
        }
        engine.autoKeyframeEnabled = json.optBoolean("autoKey", false)
        var tracksN = 0
        val tArr = json.optJSONArray("tracks") ?: JSONArray()
        for (i in 0 until tArr.length()) {
            val o = tArr.optJSONObject(i) ?: continue
            val target = o.optString("target"); val prop = o.optString("property")
            if (target.isEmpty() || prop.isEmpty()) { issues.add(ValidationIssue.SkippedEntry("track#$i missing key")); continue }
            val type = runCatching { PropertyType.valueOf(o.optString("type", "FLOAT")) }
                .getOrDefault(PropertyType.FLOAT)
            val st = AnimationTrack.State(
                type = type,
                data = readKfs(o.optJSONArray("kfs"), type, issues),
                enabled = o.optBoolean("enabled", true),
                spatial = o.optBoolean("spatial", type == PropertyType.VEC2),
                rangeStartMs = if (o.has("rs")) o.getLong("rs") else null,
                rangeEndMs = if (o.has("re")) o.getLong("re") else null,
                beforeRange = runCatching { Boundary.valueOf(o.optString("before", "HOLD")) }.getOrDefault(Boundary.HOLD),
                afterRange = runCatching { Boundary.valueOf(o.optString("after", "HOLD")) }.getOrDefault(Boundary.HOLD),
                loop = runCatching { LoopMode.valueOf(o.optString("loop", "NONE")) }.getOrDefault(LoopMode.NONE),
                repeatCount = o.optInt("repeat", Int.MAX_VALUE).coerceAtLeast(1),
                speed = o.optDouble("speed", 1.0).let { if (it.isFinite() && it > 0.0) it else 1.0 },
                customTimeCurve = o.optJSONArray("tc")?.let { readKfs(it, PropertyType.FLOAT, issues) }
            )
            engine.ensureTrack(BindingKey(target, prop), type, st.spatial).forceState(st)
            tracksN++
        }
        val sArr = json.optJSONArray("sources") ?: JSONArray()
        for (i in 0 until sArr.length()) readSource(engine, sArr.optJSONObject(i) ?: continue, issues)
        var clipsN = 0
        val cArr = json.optJSONArray("clips") ?: JSONArray()
        for (i in 0 until cArr.length()) {
            val c = cArr.optJSONObject(i) ?: continue
            val tracks = ArrayList<ClipTrack>()
            val ctArr = c.optJSONArray("tracks") ?: JSONArray()
            for (j in 0 until ctArr.length()) {
                val ct = ctArr.optJSONObject(j) ?: continue
                val type = runCatching { PropertyType.valueOf(ct.optString("type", "FLOAT")) }.getOrDefault(PropertyType.FLOAT)
                tracks.add(ClipTrack(
                    ct.optString("property"), type,
                    readKfs(ct.optJSONArray("kfs"), type, issues).keyframes,
                    runCatching { LoopMode.valueOf(ct.optString("loop", "NONE")) }.getOrDefault(LoopMode.NONE)))
            }
            engine.addClip(AnimationClip(c.optString("id"), c.optString("name"), c.optLong("dur"), tracks))
            clipsN++
        }
        var markersN = 0
        val mArr = json.optJSONArray("markers") ?: JSONArray()
        for (i in 0 until mArr.length()) {
            val m = mArr.optJSONObject(i) ?: continue
            engine.addMarker(m.optLong("t"), m.optString("name"), m.optString("cat"), m.optInt("color"))
            markersN++
        }
        engine.bumpVersion()
        return LoadReport(issues, tracksN, markersN, clipsN)
    }

    private fun readKfs(a: JSONArray?, type: PropertyType, issues: MutableList<ValidationIssue>): KeyframeTrackData {
        val out = ArrayList<Keyframe>()
        if (a != null) for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val t = o.optLong("t", Long.MIN_VALUE)
            if (t == Long.MIN_VALUE) { issues.add(ValidationIssue.RepairedKeyframe("kf#$i missing time -- dropped")); continue }
            val interp = runCatching { InterpolationType.valueOf(o.optString("i", "LINEAR")) }
                .getOrElse { issues.add(ValidationIssue.RepairedKeyframe("kf#$i bad interpolation->LINEAR")); InterpolationType.LINEAR }
            val easing = runCatching { EasingType.valueOf(o.optString("e", "LINEAR")) }.getOrDefault(EasingType.LINEAR)
            val tm = runCatching { TangentMode.valueOf(o.optString("tm", "AUTO")) }.getOrDefault(TangentMode.AUTO)
            var kf = when (type) {
                PropertyType.VEC2 -> {
                    val vx = o.optDouble("vx", Double.NaN); val vy = o.optDouble("vy", Double.NaN)
                    if (!vx.isFinite() || !vy.isFinite()) { issues.add(ValidationIssue.RepairedKeyframe("kf@$t NaN vec -- dropped")); continue }
                    Keyframe(nextTmpId(), t, vecValue = Vec2(vx, vy), interpolation = interp, easing = easing, tangentMode = tm)
                }
                PropertyType.FLOAT -> {
                    val v = o.optDouble("v", Double.NaN)
                    if (!v.isFinite()) { issues.add(ValidationIssue.RepairedKeyframe("kf@$t NaN value -- dropped")); continue }
                    Keyframe(nextTmpId(), t, value = v, interpolation = interp, easing = easing, tangentMode = tm)
                }
            }
            kf = kf.copy(
                inTangent = o.optDouble("tin", 0.0).let { if (it.isFinite()) it else 0.0 },
                outTangent = o.optDouble("tout", 0.0).let { if (it.isFinite()) it else 0.0 },
                spatialInHandle = o.optJSONArray("hin")?.let { Vec2(it.optDouble(0), it.optDouble(1)) }
                    ?.takeIf { com.ahstudio.animation.math.isFinite(it) },
                spatialOutHandle = o.optJSONArray("hout")?.let { Vec2(it.optDouble(0), it.optDouble(1)) }
                    ?.takeIf { com.ahstudio.animation.math.isFinite(it) },
                bezier = o.optJSONArray("bez")?.takeIf { it.length() == 4 }?.let {
                    try { CubicBezierTiming(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3)) }
                    catch (e: Exception) { issues.add(ValidationIssue.RepairedKeyframe("kf@$t bad bezier->LINEAR")); null }
                },
                metadata = o.optJSONObject("md")?.let { mo ->
                    buildMap { for (k in mo.keys()) put(k, mo.optString(k)) }
                } ?: emptyMap()
            )
            val (fixed, issue) = KeyframeSanitizer.sanitize(kf)
            if (issue != null) issues.add(issue)
            fixed?.let { out.add(it) }
        }
        return KeyframeTrackData.of(out, 0L)
    }

    private var tmpId = 1L
    private fun nextTmpId() = KeyframeId(tmpId++)

    private fun writeSource(engine: AnimationEngine, key: BindingKey, arr: JSONArray) {
        when (val d = engine.sourceDescriptors[key]) {
            is AnimationEngine.SourceDescriptor.WiggleDesc -> arr.put(JSONObject()
                .put("kind", "WIGGLE").put("target", key.targetId).put("property", key.property)
                .put("type", if (d.vec2) "VEC2" else "FLOAT")
                .put("seed", d.seed).put("freq", d.freqHz).put("amp", d.amplitude).put("oct", d.octaves))
            is AnimationEngine.SourceDescriptor.SpringDesc -> arr.put(JSONObject()
                .put("kind", "SPRING").put("target", key.targetId).put("property", key.property)
                .put("from", d.from).put("to", d.to).put("startMs", d.startMs).put("durMs", d.durationMs)
                .put("stiff", d.stiffness).put("damp", d.damping).put("mass", d.mass))
            null -> {}
        }
    }

    private fun readSource(engine: AnimationEngine, o: JSONObject, issues: MutableList<ValidationIssue>) {
        when (o.optString("kind")) {
            "WIGGLE" -> {
                val target = o.optString("target"); val prop = o.optString("property")
                val type = runCatching { PropertyType.valueOf(o.optString("type", "FLOAT")) }.getOrDefault(PropertyType.FLOAT)
                val w = Wiggle(o.optLong("seed"), o.optDouble("freq", 2.0), o.optDouble("amp", 10.0), o.optInt("oct", 2))
                val k = BindingKey(target, prop)
                if (type == PropertyType.VEC2) engine.setWiggleVec2(k, w) else engine.setWiggle(k, w)
            }
            "SPRING" -> {
                val k = BindingKey(o.optString("target"), o.optString("property"))
                engine.setSpring(k, o.optDouble("from"), o.optDouble("to"), o.optLong("startMs"),
                    o.optLong("durMs", 1000), Spring(o.optDouble("stiff", 120.0), o.optDouble("damp", 12.0), o.optDouble("mass", 1.0)))
            }
            else -> issues.add(ValidationIssue.SkippedEntry("unknown source kind"))
        }
    }

    private fun writeClip(c: AnimationClip): JSONObject {
        val o = JSONObject()
        o.put("id", c.id); o.put("name", c.name); o.put("dur", c.durationMs)
        val arr = JSONArray()
        for (t in c.tracks) {
            val to = JSONObject()
            to.put("property", t.property); to.put("type", t.type.name); to.put("loop", t.loop.name)
            to.put("kfs", writeKfs(t.keyframes))
            arr.put(to)
        }
        o.put("tracks", arr)
        return o
    }
    private fun writeMarker(m: AnimationMarker): JSONObject {
        val o = JSONObject()
        o.put("t", m.timeMs); o.put("name", m.name); o.put("cat", m.category); o.put("color", m.color)
        return o
    }
}
