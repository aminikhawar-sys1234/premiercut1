package com.ahstudio.audio.master.persistence

import com.ahstudio.audio.master.dsp.eq.BiquadType
import com.ahstudio.audio.master.dsp.eq.EqualizerBand
import com.ahstudio.audio.master.model.*
import org.json.JSONArray
import org.json.JSONObject

object AudioMigrationManager {
    const val CURRENT_SCHEMA = 2
    fun migrate(json: String): String {
        val o = JSONObject(json)
        val v = o.optInt("schemaVersion", 1)
        if (v >= CURRENT_SCHEMA) return json
        o.optJSONArray("tracks")?.let { tracks ->
            for (t in 0 until tracks.length()) {
                val clips = tracks.getJSONObject(t).optJSONArray("clips") ?: continue
                for (c in 0 until clips.length()) {
                    val clip = clips.getJSONObject(c)
                    if (!clip.has("xf")) clip.put("xf", JSONObject().put("sp", 1.0).put("pt", 0.0).put("rv", false))
                    if (!clip.has("auto")) clip.put("auto", JSONArray())
                }
            }
        }
        o.put("schemaVersion", CURRENT_SCHEMA)
        return o.toString()
    }
}

object AudioStateMapper {
    private fun floatMap(o: JSONObject): Map<String, Float> {
        val out = mutableMapOf<String, Float>()
        val names = o.names() ?: return out
        for (i in 0 until names.length()) { val k = names.getString(i); out[k] = o.getDouble(k).toFloat() }
        return out
    }
    private fun arr(o: JSONObject, key: String): JSONArray = o.optJSONArray(key) ?: JSONArray()

    fun chainToJson(c: AudioDspChainSpec): JSONArray = JSONArray(c.nodes.map { n ->
        JSONObject().put("type", n.type).put("enabled", n.enabled)
            .put("params", JSONObject().apply { n.parameters.forEach { (k, v) -> put(k, v.toDouble()) } })
            .put("bands", JSONArray(n.bands.map { b ->
                JSONObject().put("t", b.type.name).put("f", b.frequencyHz.toDouble())
                    .put("g", b.gainDb.toDouble()).put("q", b.q.toDouble()).put("e", b.enabled) }))
    })
    fun chainFromJson(a: JSONArray): AudioDspChainSpec = AudioDspChainSpec((0 until a.length()).map { i ->
        val n = a.getJSONObject(i)
        AudioDspNodeSpec(
            n.getString("type"), n.optBoolean("enabled", true), floatMap(n.optJSONObject("params") ?: JSONObject()),
            (0 until arr(n, "bands").length()).map { k ->
                val b = n.getJSONArray("bands").getJSONObject(k)
                EqualizerBand(BiquadType.valueOf(b.optString("t", "PEAKING")), b.optDouble("f", 1000.0).toFloat(),
                    b.optDouble("g", 0.0).toFloat(), b.optDouble("q", 0.7071).toFloat(), b.optBoolean("e", true))
            })
    })

    fun automationToJson(a: AudioAutomationModel) = JSONObject()
        .put("p", a.parameter.name).put("e", a.enabled).put("ni", a.dspNodeIndex).put("pk", a.dspParamKey)
        .put("kf", JSONArray(a.keyframes.map { JSONObject().put("t", it.timeSec).put("v", it.value.toDouble()).put("c", it.curve.name) }))
    fun automationFromJson(o: JSONObject) = AudioAutomationModel(
        AutomationParameter.valueOf(o.getString("p")), o.optBoolean("e", false),
        (0 until arr(o, "kf").length()).map { k ->
            val q = o.getJSONArray("kf").getJSONObject(k)
            AudioKeyframeModel(q.getDouble("t"), q.getDouble("v").toFloat(), KeyframeCurve.valueOf(q.optString("c", "LINEAR")))
        }, o.optInt("ni", -1), o.optString("pk", ""))

    fun clipToJson(c: AudioClipModel): JSONObject = JSONObject()
        .put("id", c.id).put("trackId", c.trackId).put("sourceId", c.sourceId)
        .put("ts", c.timelineStartSec).put("td", c.timelineDurationSec)
        .put("ss", c.sourceStartSec).put("sd", c.sourceDurationSec)
        .put("vol", c.volume.toDouble()).put("gain", c.gainDb.toDouble()).put("pan", c.pan.pan.toDouble())
        .put("fade", JSONObject().put("i", c.fade.fadeInSec).put("ic", c.fade.fadeInCurve.name).put("o", c.fade.fadeOutSec).put("oc", c.fade.fadeOutCurve.name))
        .put("xf", JSONObject().put("sp", c.transform.speed.toDouble()).put("pt", c.transform.pitchSemitones.toDouble()).put("rv", c.transform.reverse))
        .put("dsp", chainToJson(c.clipDsp))
        .put("auto", JSONArray(c.automation.map { automationToJson(it) }))
        .put("meta", JSONObject().put("title", c.metadata.title ?: "").put("dur", c.metadata.durationSec))
    fun clipFromJson(o: JSONObject): AudioClipModel {
        val f = o.getJSONObject("fade"); val x = o.getJSONObject("xf"); val m = o.optJSONObject("meta") ?: JSONObject()
        return AudioClipModel(
            o.getString("id"), o.getString("trackId"), o.getString("sourceId"),
            o.getDouble("ts"), o.getDouble("td"), o.getDouble("ss"), o.getDouble("sd"),
            o.optDouble("vol", 1.0).toFloat(), o.optDouble("gain", 0.0).toFloat(),
            AudioPanSettings(o.optDouble("pan", 0.0).toFloat()),
            AudioFadeSettings(f.optDouble("i", 0.0), f.optDouble("o", 0.0),
                FadeCurve.valueOf(f.optString("ic", "LINEAR")), FadeCurve.valueOf(f.optString("oc", "LINEAR"))),
            AudioClipTransform(x.optDouble("sp", 1.0).toFloat(), x.optDouble("pt", 0.0).toFloat(), x.optBoolean("rv", false)),
            chainFromJson(arr(o, "dsp")),
            (0 until arr(o, "auto").length()).map { automationFromJson(o.getJSONArray("auto").getJSONObject(it)) },
            AudioMetadata(m.optString("title").ifEmpty { null }, durationSec = m.optDouble("dur", 0.0)))
    }

    fun sourceToJson(s: AudioSourceModel) = JSONObject()
        .put("id", s.id).put("uri", s.uri).put("kind", s.kind.name).put("dur", s.durationSec)
        .put("nsr", s.nativeSampleRate).put("nch", s.nativeChannels)
        .put("title", s.title ?: "").put("df", s.derivedFrom ?: "").put("dv", s.derivation)
    fun sourceFromJson(o: JSONObject) = AudioSourceModel(
        o.getString("id"), o.getString("uri"), AudioSourceKind.valueOf(o.optString("kind", "FILE")),
        o.optDouble("dur", 0.0), o.optInt("nsr", 48000), o.optInt("nch", 2),
        o.optString("title", "").ifEmpty { null }, o.optString("df", "").ifEmpty { null }, o.optString("dv", ""))

    fun trackToJson(t: AudioTrackModel) = JSONObject()
        .put("id", t.id).put("idx", t.index)
        .put("st", JSONObject().put("name", t.settings.name).put("vol", t.settings.volume.toDouble())
            .put("gain", t.settings.gainDb.toDouble()).put("pan", t.settings.pan.toDouble())
            .put("mute", t.settings.mute).put("solo", t.settings.solo).put("route", t.settings.routing.targetBusId))
        .put("dsp", chainToJson(t.trackDsp))
        .put("auto", JSONArray(t.automation.map { automationToJson(it) }))
        .put("clips", JSONArray(t.clips.map { clipToJson(it) }))
    fun trackFromJson(o: JSONObject): AudioTrackModel {
        val st = o.getJSONObject("st")
        return AudioTrackModel(o.getString("id"), o.optInt("idx", 0),
            AudioTrackSettings(st.optString("name", "Audio Track"), st.optDouble("vol", 1.0).toFloat(),
                st.optDouble("gain", 0.0).toFloat(), st.optDouble("pan", 0.0).toFloat(),
                st.optBoolean("mute"), st.optBoolean("solo"),
                AudioRoutingModel(st.optString("route", AudioRoutingModel.MASTER))),
            (0 until arr(o, "clips").length()).map { clipFromJson(o.getJSONArray("clips").getJSONObject(it)) },
            chainFromJson(arr(o, "dsp")),
            (0 until arr(o, "auto").length()).map { automationFromJson(o.getJSONArray("auto").getJSONObject(it)) })
    }

    fun projectFromJson(o: JSONObject): MasterAudioProject {
        val mix = o.getJSONObject("mix")
        return MasterAudioProject(
            o.optString("id", java.util.UUID.randomUUID().toString()),
            o.optInt("sr", 48000), o.optInt("ch", 2),
            (0 until arr(o, "tracks").length()).map { trackFromJson(o.getJSONArray("tracks").getJSONObject(it)) },
            (0 until arr(o, "sources").length()).map { sourceFromJson(o.getJSONArray("sources").getJSONObject(it)) }
                .associateBy { it.id },
            AudioMixSettings(mix.optDouble("mv", 1.0).toFloat(), mix.optDouble("mg", 0.0).toFloat(), mix.optDouble("hr", -0.3).toFloat()),
            chainFromJson(arr(o, "masterDsp")))
    }
}

class AudioProjectSerializer {
    fun serialize(p: MasterAudioProject): String = JSONObject()
        .put("schemaVersion", AudioMigrationManager.CURRENT_SCHEMA)
        .put("id", p.id).put("sr", p.sampleRate).put("ch", p.channels)
        .put("mix", JSONObject().put("mv", p.mix.masterVolume.toDouble()).put("mg", p.mix.masterGainDb.toDouble()).put("hr", p.mix.headroomDb.toDouble()))
        .put("masterDsp", AudioStateMapper.chainToJson(p.masterDsp))
        .put("sources", JSONArray(p.sources.values.map { AudioStateMapper.sourceToJson(it) }))
        .put("tracks", JSONArray(p.tracks.map { AudioStateMapper.trackToJson(it) }))
        .toString()
}

class AudioProjectDeserializer {
    fun deserialize(json: String): MasterAudioProject =
        AudioStateMapper.projectFromJson(JSONObject(AudioMigrationManager.migrate(json)))
}
