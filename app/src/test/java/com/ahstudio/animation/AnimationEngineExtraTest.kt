package com.ahstudio.animation

import com.ahstudio.animation.core.*
import com.ahstudio.animation.curves.CubicBezierTiming
import com.ahstudio.animation.easing.Easing
import com.ahstudio.animation.easing.EasingType
import com.ahstudio.animation.keyframes.*
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.motion.MotionBlurSampler
import com.ahstudio.animation.motion.MotionPath
import com.ahstudio.animation.properties.AnimatableProperty
import com.ahstudio.animation.properties.BindingKey
import com.ahstudio.animation.properties.Props
import com.ahstudio.animation.properties.PropertyType
import com.ahstudio.animation.procedural.Spring
import com.ahstudio.animation.procedural.Wiggle
import com.ahstudio.animation.serialize.AnimationSerializer
import com.ahstudio.animation.keyframes.ValidationIssue
import com.ahstudio.animation.time.LoopMode
import com.ahstudio.animation.time.TimeRemapper
import com.ahstudio.animation.undo.UndoableCommand
import com.ahstudio.animation.undo.UndoSink
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val EPS = 1e-6

class AnimationEngineExtraTest {

    @Test fun pingPong_corrected() {
        var id = 1L
        val st = AnimationTrack.State(
            type = PropertyType.FLOAT,
            data = KeyframeTrackData.of(listOf(
                Keyframe(KeyframeId(id++), 0, value = 0.0),
                Keyframe(KeyframeId(id++), 1000, value = 100.0)), 1L),
            loop = LoopMode.PINGPONG)
        val tr = AnimationTrack(BindingKey("L", "P"), st)
        assertEquals(70.0, (tr.evaluate(1300, TrackEvalCache()) as EvaluatedValue.FloatV).value, EPS)
        assertEquals(30.0, (tr.evaluate(1700, TrackEvalCache()) as EvaluatedValue.FloatV).value, EPS)
        assertEquals(70.0, (tr.evaluate(2700, TrackEvalCache()) as EvaluatedValue.FloatV).value, EPS)
    }

    @Test fun timeRemapper_loopPingPongReverse() {
        assertEquals(500L, TimeRemapper(loop = LoopMode.LOOP).remap(1500, 1000))
        assertEquals(300L, TimeRemapper(loop = LoopMode.PINGPONG).remap(1700, 1000))
        assertEquals(800L, TimeRemapper(loop = LoopMode.REVERSE).remap(200, 1000))
        assertEquals(1000L, TimeRemapper(speed = 2.0).remap(500, 1000))
    }

    @Test fun timeRemapper_customCurve() {
        var id = 1L
        val curve = KeyframeTrackData.of(listOf(
            Keyframe(KeyframeId(id++), 0, value = 0.0),
            Keyframe(KeyframeId(id++), 1000, value = 250.0)), 1L)
        assertEquals(250L, TimeRemapper(customCurve = curve).remap(1000, 1000))
        assertEquals(125L, TimeRemapper(customCurve = curve).remap(500, 1000))
    }

    @Test fun boundaryContinue_extrapolatesWithVelocity() {
        var id = 1L
        val st = AnimationTrack.State(
            type = PropertyType.FLOAT,
            data = KeyframeTrackData.of(listOf(
                Keyframe(KeyframeId(id++), 0, value = 0.0),
                Keyframe(KeyframeId(id++), 1000, value = 100.0)), 1L),
            afterRange = Boundary.CONTINUE)
        val tr = AnimationTrack(BindingKey("L", "O"), st)
        val v = tr.evaluate(1500, TrackEvalCache()) as EvaluatedValue.FloatV
        assertEquals(150.0, v.value, EPS)
        assertEquals(100.0, v.velocityPerSec, EPS)
    }

    @Test fun spatialBezier_exactMidpoint() {
        var id = 1L
        val kfs = KeyframeTrackData.of(listOf(
            Keyframe(KeyframeId(id++), 0, vecValue = Vec2(0.0, 0.0), spatialOutHandle = Vec2(100.0, 0.0)),
            Keyframe(KeyframeId(id++), 1000, vecValue = Vec2(100.0, 100.0), spatialInHandle = Vec2(-100.0, 100.0))), 1L)
        val v = KeyframeEvaluator.evalVec2Full(kfs.keyframes, 500, spatial = true)!!
        assertEquals(50.0, v.value.x, EPS)
        assertEquals(87.5, v.value.y, EPS)
    }

    @Test fun bezierTangents_equalSlopesProduceLinearAndExactVelocity() {
        var id = 1L
        val kfs = KeyframeTrackData.of(listOf(
            Keyframe(KeyframeId(id++), 0, value = 0.0, interpolation = InterpolationType.BEZIER, outTangent = 100.0),
            Keyframe(KeyframeId(id++), 1000, value = 100.0, interpolation = InterpolationType.BEZIER, inTangent = 100.0)), 1L)
        val v = KeyframeEvaluator.evalScalarFull(kfs.keyframes, 500)!!
        assertEquals(50.0, v.value, EPS)
        assertEquals(100.0, v.velocityPerSec, EPS)
    }

    @Test fun easing_overshootAndCustom() {
        assertTrue(Easing.apply(EasingType.BACK_OUT, 0.7) > 1.0)
        val bez = CubicBezierTiming(0.3, 0.0, 0.7, 1.0)
        assertTrue(Easing.apply(EasingType.CUSTOM, 0.25, bez) < 0.25)
        assertTrue(Easing.apply(EasingType.CUSTOM, 0.75, bez) > 0.75)
    }

    @Test fun spring_settlesAndDeterministic() {
        val s = Spring(stiffness = 120.0, damping = 8.0)
        assertEquals(s.value(0.0, 100.0, 0.25), s.value(0.0, 100.0, 0.25), 0.0)
        assertEquals(100.0, s.value(0.0, 100.0, 5.0), 1e-3)
        assertEquals(0.0, s.value(0.0, 100.0, 0.0), EPS)
    }

    @Test fun wiggle_deterministicAndSeedSensitive() {
        val w1 = Wiggle(seed = 42, freqHz = 2.0, amplitude = 10.0)
        assertEquals(w1.value(1234), w1.value(1234), 0.0)
        val w2 = Wiggle(seed = 43, freqHz = 2.0, amplitude = 10.0)
        assertTrue(abs(w1.value(1000) - w2.value(1000)) > 1e-9)
    }

    @Test fun motionPath_endpointsAndTangent() {
        var id = 1L
        val kfs = listOf(
            Keyframe(KeyframeId(id++), 0, vecValue = Vec2(0.0, 0.0), spatialOutHandle = Vec2(50.0, 0.0)),
            Keyframe(KeyframeId(id++), 1000, vecValue = Vec2(100.0, 0.0), spatialInHandle = Vec2(-50.0, 0.0)))
        val mp = MotionPath(kfs)
        assertEquals(0.0, mp.positionAtDistance(0.0).x, EPS)
        assertEquals(100.0, mp.positionAtDistance(mp.totalLength).x, EPS)
        assertTrue(mp.totalLength >= 100.0 - 1e-6)
        assertEquals(1.0, mp.tangentAtDistance(mp.totalLength / 2).length(), EPS)
    }

    @Test fun motionBlur_subframesStraddleTime() {
        val e = AnimationEngine()
        val key = BindingKey("L", Props.POSITION)
        e.ensureTrack(key, PropertyType.VEC2)
        e.addKeyframe(key, Keyframe(e.nextId(), 0, vecValue = Vec2(0.0, 0.0)))
        e.addKeyframe(key, Keyframe(e.nextId(), 1000, vecValue = Vec2(200.0, 0.0)))
        val pts = MotionBlurSampler.subframePositions(e, key, 500, shutterMs = 100.0, samples = 5)
        assertEquals(5, pts.size)
        assertEquals(90.0, pts.first().x, EPS)
        assertEquals(110.0, pts.last().x, EPS)
    }

    @Test fun move_collisionReplacesDeterministically() {
        var id = 1L
        val a = Keyframe(KeyframeId(id++), 0, value = 1.0)
        val b = Keyframe(KeyframeId(id++), 100, value = 2.0)
        val data = KeyframeTrackData.of(listOf(a, b), 1L)
        val moved = KeyframeOps.move(data, setOf(a.id), 100)
        assertEquals(1, moved.keyframes.size)
        assertEquals(1.0, moved.keyframes[0].value, EPS)
        assertEquals(100L, moved.keyframes[0].timeMs)
    }

    @Test fun scaleTimes_reverseRange_quantize_distribute() {
        var id = 1L
        val kfs = listOf(
            Keyframe(KeyframeId(id++), 0, value = 0.0),
            Keyframe(KeyframeId(id++), 100, value = 1.0))
        val d = KeyframeTrackData.of(kfs, 1L)
        val scaled = KeyframeOps.scaleTimes(d, setOf(kfs[1].id), 0, 2.0)
        assertEquals(200L, scaled.keyframes[1].timeMs)

        var id2 = 1L
        val r = KeyframeTrackData.of(listOf(
            Keyframe(KeyframeId(id2++), 0, value = 0.0),
            Keyframe(KeyframeId(id2++), 500, value = 1.0),
            Keyframe(KeyframeId(id2++), 1000, value = 2.0)), 1L)
        val rev = KeyframeOps.reverseRange(r, 0, 1000)
        assertEquals(0L, rev.keyframes[0].timeMs)
        assertEquals(2.0, rev.keyframes[0].value, 0.0)
        assertEquals(1000L, rev.keyframes[2].timeMs)
        assertEquals(0.0, rev.keyframes[2].value, 0.0)

        var id3 = 1L
        val q = KeyframeTrackData.of(listOf(
            Keyframe(KeyframeId(id3++), 37, value = 0.0),
            Keyframe(KeyframeId(id3++), 76, value = 1.0)), 1L)
        val quant = KeyframeOps.quantize(q, q.keyframes.map { it.id }.toSet(), 50)
        assertEquals(50L, quant.keyframes[0].timeMs)
        assertEquals(100L, quant.keyframes[1].timeMs)

        var id4 = 1L
        val dd = KeyframeTrackData.of(listOf(
            Keyframe(KeyframeId(id4++), 0, value = 0.0),
            Keyframe(KeyframeId(id4++), 100, value = 1.0),
            Keyframe(KeyframeId(id4++), 400, value = 2.0)), 1L)
        val dist = KeyframeOps.distributeEvenly(dd, dd.keyframes.map { it.id }.toSet())
        assertEquals(200L, dist.keyframes[1].timeMs)
    }

    @Test fun clipboard_copyPasteRelative() {
        var id = 1L
        val kfs = listOf(
            Keyframe(KeyframeId(id++), 100, value = 7.0),
            Keyframe(KeyframeId(id++), 300, value = 9.0))
        KeyframeClipboard.copy(kfs, "FLOAT")
        var nid = 1000L
        val pasted = KeyframeClipboard.paste(1000) { KeyframeId(nid++) }
        assertEquals(listOf(1000L, 1200L), pasted.map { it.timeMs })
        assertEquals(9.0, pasted[1].value, EPS)
    }

    @Test fun applyClip_fadeIn_installsAndEvaluates() {
        val e = AnimationEngine()
        e.addClip(BuiltinPresets.fadeIn(800))
        val keys = e.applyClip("preset.fadeIn", "L1", 2000)
        assertEquals(1, keys.size)
        assertEquals(0.0, e.evaluate(2000).floatValue(BindingKey("L1", Props.OPACITY))!!, EPS)
        assertEquals(100.0, e.evaluate(2800).floatValue(BindingKey("L1", Props.OPACITY))!!, EPS)
        assertTrue(e.applyClip("does.not.exist", "L1", 0).isEmpty())
    }

    @Test fun snapshot_multiProperty_andVelocity() {
        val e = AnimationEngine()
        val op = BindingKey("L1", Props.OPACITY)
        val rot = BindingKey("L1", Props.ROTATION)
        e.ensureTrack(op, PropertyType.FLOAT)
        e.addKeyframe(op, Keyframe(e.nextId(), 0, value = 0.0))
        e.addKeyframe(op, Keyframe(e.nextId(), 1000, value = 100.0))
        e.ensureTrack(rot, PropertyType.FLOAT)
        e.addKeyframe(rot, Keyframe(e.nextId(), 0, value = 0.0))
        e.addKeyframe(rot, Keyframe(e.nextId(), 1000, value = 90.0))
        val snap = e.evaluate(500)
        assertEquals(50.0, snap.floatValue(op)!!, EPS)
        assertEquals(45.0, snap.floatValue(rot)!!, EPS)
        assertEquals(90.0, snap.velocityOf(rot)!!.x, EPS)
        assertEquals(2, e.evaluateTarget("L1", 500).size)
    }

    @Test fun disabledTrack_returnsNull() {
        val e = AnimationEngine()
        val k = BindingKey("L", "O")
        val tr = e.ensureTrack(k, PropertyType.FLOAT)
        e.addKeyframe(k, Keyframe(e.nextId(), 0, value = 5.0))
        tr.forceState(tr.get().copy(enabled = false))
        assertNull(e.evaluateKey(k, 100))
    }

    @Test fun autoKeyframe_vec2() {
        val e = AnimationEngine()
        val key = BindingKey("L", Props.POSITION)
        e.registerProperty(AnimatableProperty(key, PropertyType.VEC2))
        e.markAnimatable(key); e.autoKeyframeEnabled = true
        assertTrue(e.notifyUserValueChange(key, Vec2(10.0, 20.0), 400))
        assertEquals(20.0, e.evaluate(400).vec2Value(key)!!.y, EPS)
        assertEquals(10.0, e.evaluate(0).vec2Value(key)!!.x, EPS)
    }

    @Test fun undo_deleteRestores() {
        val e = AnimationEngine(); val k = BindingKey("L", "O")
        e.ensureTrack(k, PropertyType.FLOAT)
        val ids = (0..2).map { val id = e.nextId(); e.addKeyframe(k, Keyframe(id, it * 100L, value = it.toDouble())); id }
        val log = ArrayList<UndoableCommand>(); e.undoSink = UndoSink { log.add(it) }
        e.deleteKeyframes(k, setOf(ids[1]))
        assertEquals(2, e.trackFor(k)!!.keyframeCount)
        log.last().undo(); assertEquals(3, e.trackFor(k)!!.keyframeCount)
        log.last().redo(); assertEquals(2, e.trackFor(k)!!.keyframeCount)
    }

    @Test fun migration_oldVersion_upgraded() {
        val engine = AnimationEngine()
        var called = false
        AnimationSerializer.registerMigrator(0) { j -> called = true; j.put("animationVersion", 1); j }
        val r = AnimationSerializer.load(engine, JSONObject("""{"animationVersion":0,"tracks":[]}"""))
        assertTrue(called); assertEquals(0, r.tracksLoaded)
    }

    @Test fun futureVersion_rejectedNotCorrupted() {
        val r = AnimationSerializer.load(AnimationEngine(), JSONObject("""{"animationVersion":99}"""))
        assertTrue(r.issues.any { it is ValidationIssue.UnsupportedVersion })
        assertEquals(0, r.tracksLoaded)
    }

    @Test fun corruptedJson_neverCrashes() {
        val e = AnimationEngine()
        val garbage = JSONObject("""{"animationVersion":1,"tracks":[
            {"target":"","property":""},
            {"target":"A","property":"X","type":"WAT","kfs":"notarray"}]}""")
        val r = AnimationSerializer.load(e, garbage)
        assertTrue(r.issues.isNotEmpty())
        e.evaluate(1234)
    }

    @Test fun nanVec_serializationDropped() {
        val e = AnimationEngine()
        val r = AnimationSerializer.load(e, JSONObject("""{"animationVersion":1,"tracks":[
            {"target":"A","property":"P","type":"VEC2","kfs":[
                {"t":0,"vx":0,"vy":0},{"t":100,"vx":1e999,"vy":0}]}]}"""))
        assertTrue(r.issues.isNotEmpty())
        assertEquals(1, e.trackFor(BindingKey("A", "P"))!!.keyframeCount)
    }

    @Test fun performance_10kKeyframes_sequentialAndRandom() {
        var id = 1L
        val kfs = ArrayList<Keyframe>(10_001)
        for (i in 0..10_000) kfs.add(Keyframe(KeyframeId(id++), i * 10L, value = i * 2.0))
        val track = AnimationTrack(BindingKey("P", "V"),
            AnimationTrack.State(type = PropertyType.FLOAT, data = KeyframeTrackData.of(kfs, 1L)))
        val cache = TrackEvalCache()
        val t0 = System.nanoTime()
        var last = 0.0
        for (i in 0..10_000) last = (track.evaluate(i * 10L, cache) as EvaluatedValue.FloatV).value
        val seqMs = (System.nanoTime() - t0) / 1e6
        assertTrue("sequential eval slow: $seqMs ms", seqMs < 1000.0)
        assertEquals(20000.0, last, EPS)
        assertEquals(10001.0, (track.evaluate(50_005, TrackEvalCache()) as EvaluatedValue.FloatV).value, EPS)
        assertEquals(4001.0, (track.evaluate(20_005, TrackEvalCache()) as EvaluatedValue.FloatV).value, EPS)
    }
}
