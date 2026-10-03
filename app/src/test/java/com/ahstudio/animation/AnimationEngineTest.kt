package com.ahstudio.animation

import com.ahstudio.animation.core.AnimationEngine
import com.ahstudio.animation.core.Boundary
import com.ahstudio.animation.core.BuiltinPresets
import com.ahstudio.animation.curves.CubicBezierTiming
import com.ahstudio.animation.easing.Easing
import com.ahstudio.animation.easing.EasingType
import com.ahstudio.animation.keyframes.*
import com.ahstudio.animation.math.Mat3
import com.ahstudio.animation.math.Vec2
import com.ahstudio.animation.parenting.MapParentResolver
import com.ahstudio.animation.parenting.Transform2D
import com.ahstudio.animation.parenting.WorldTransformEvaluator
import com.ahstudio.animation.properties.BindingKey
import com.ahstudio.animation.properties.Props
import com.ahstudio.animation.properties.PropertyType
import com.ahstudio.animation.serialize.AnimationSerializer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val EPS = 1e-6

class AnimationEngineTest {

    private fun scalarTrack(kfData: List<Pair<Long, Double>>, interp: InterpolationType = InterpolationType.LINEAR): KeyframeTrackData {
        var id = 1L
        return KeyframeTrackData.of(kfData.map { Keyframe(KeyframeId(id++), it.first, value = it.second, interpolation = interp) }, 1L)
    }
    private fun vecTrack(kfData: List<Triple<Long, Double, Double>>, handles: Boolean = false): KeyframeTrackData {
        var id = 1L
        return KeyframeTrackData.of(kfData.map {
            Keyframe(
                KeyframeId(id++), it.first, vecValue = Vec2(it.second, it.third),
                spatialOutHandle = if (handles) Vec2(100.0, 0.0) else null,
                spatialInHandle = if (handles) Vec2(-100.0, 100.0) else null
            )
        }, 1L)
    }

    @Test fun linearInterpolation_atMidpoint() {
        val kfs = scalarTrack(listOf(0L to 0.0, 1000L to 100.0))
        val v = KeyframeEvaluator.evalScalarFull(kfs.keyframes, 500)!!
        assertEquals(50.0, v.value, EPS)
        assertEquals(100.0, v.velocityPerSec, EPS)
    }

    @Test fun holdKeyframe_keepsValueUntilNext() {
        var id = 1L
        val kfs = KeyframeTrackData.of(listOf(
            Keyframe(KeyframeId(id++), 0, value = 0.0),
            Keyframe(KeyframeId(id++), 2000, value = 0.0, interpolation = InterpolationType.HOLD),
            Keyframe(KeyframeId(id++), 5000, value = 100.0)), 1L)
        assertEquals(0.0, KeyframeEvaluator.evalScalarFull(kfs.keyframes, 1999)!!.value, EPS)
        assertEquals(0.0, KeyframeEvaluator.evalScalarFull(kfs.keyframes, 2000)!!.value, EPS)
        assertEquals(0.0, KeyframeEvaluator.evalScalarFull(kfs.keyframes, 4999)!!.value, EPS)
        assertEquals(100.0, KeyframeEvaluator.evalScalarFull(kfs.keyframes, 5000)!!.value, EPS)
    }

    @Test fun cubicBezier_endpointsAndMidpoint() {
        val linear = CubicBezierTiming(0.0, 0.0, 1.0, 1.0)
        assertEquals(0.0, linear.progress(0.0), EPS)
        assertEquals(1.0, linear.progress(1.0), EPS)
        assertEquals(0.5, linear.progress(0.5), EPS)
        val ease = CubicBezierTiming(0.42, 0.0, 0.58, 1.0)
        assertTrue(ease.progress(0.25) < 0.25)
        assertTrue(ease.progress(0.75) > 0.75)
    }

    @Test fun easing_deterministicAndMonotonic() {
        val a = Easing.apply(EasingType.ELASTIC_OUT, 0.37)
        val b = Easing.apply(EasingType.ELASTIC_OUT, 0.37)
        assertEquals(a, b, 0.0)
        assertTrue(Easing.apply(EasingType.CUBIC_IN, 0.2) < Easing.apply(EasingType.CUBIC_IN, 0.8))
    }

    @Test fun rotation_multiTurn() {
        val kfs = scalarTrack(listOf(0L to 0.0, 1000L to 1080.0))
        assertEquals(540.0, KeyframeEvaluator.evalScalarFull(kfs.keyframes, 500)!!.value, EPS)
    }

    @Test fun seekDeterminism_directJumpMatchesContinuous() {
        val kfs = scalarTrack((0..10).map { (it * 500L) to (it * 13.7) })
        val continuous = HashMap<Long, Double>()
        for (t in 0..5000 step 100) continuous[t.toLong()] = KeyframeEvaluator.evalScalarFull(kfs.keyframes, t.toLong())!!.value
        val direct = KeyframeEvaluator.evalScalarFull(kfs.keyframes, 5000)!!.value
        assertEquals(continuous[5000L]!!, direct, 0.0)
        val direct2500 = KeyframeEvaluator.evalScalarFull(kfs.keyframes, 2500)!!.value
        assertEquals(continuous[2500L]!!, direct2500, 0.0)
    }

    @Test fun loopMode_wraps() {
        var id = 1L
        val st = com.ahstudio.animation.core.AnimationTrack.State(
            type = PropertyType.FLOAT,
            data = KeyframeTrackData.of(listOf(
                Keyframe(KeyframeId(id++), 0, value = 0.0), Keyframe(KeyframeId(id++), 1000, value = 100.0)), 1L),
            loop = com.ahstudio.animation.time.LoopMode.LOOP)
        val tr = com.ahstudio.animation.core.AnimationTrack(BindingKey("L", "P"), st)
        assertEquals(50.0, (tr.evaluate(1500, TrackEvalCache()) as EvaluatedValue.FloatV).value, EPS)
    }

    @Test fun spatialBezier_followsPathNotDiagonal() {
        val kfs = vecTrack(listOf(Triple(0L, 0.0, 0.0), Triple(1000L, 100.0, 100.0)), handles = true)
        val q = KeyframeEvaluator.evalVec2Full(kfs.keyframes, 250, spatial = true)!!.value
        assertTrue(abs(q.x - 25.0) > 1.0 || abs(q.y - 25.0) > 1.0)
    }

    @Test fun parentChild_worldTransform() {
        val resolver = MapParentResolver(
            parents = mapOf("child" to "parent"),
            locals = mapOf(
                "parent" to Transform2D(rotationDeg = 90.0),
                "child" to Transform2D(position = Vec2(10.0, 0.0))))
        val w = WorldTransformEvaluator.localToWorldPoint("child", Vec2.ZERO, resolver)
        assertEquals(0.0, w.x, EPS); assertEquals(10.0, w.y, EPS)
    }

    @Test fun matrix_inverseRoundTrip() {
        val m = Mat3.translation(30.0, -12.0) * Mat3.rotationDeg(37.0) * Mat3.scaling(2.0, 0.5)
        val p = Vec2(4.2, -7.7)
        val back = m.inverse().transform(m.transform(p))
        assertEquals(p.x, back.x, 1e-9); assertEquals(p.y, back.y, 1e-9)
    }

    @Test fun autoKeyframe_createsTrack_whenMarkedAndEnabled() {
        val engine = AnimationEngine()
        val key = BindingKey("layer1", Props.OPACITY)
        engine.registerProperty(com.ahstudio.animation.properties.AnimatableProperty(key, PropertyType.FLOAT, 100.0, max = 100.0))
        engine.markAnimatable(key)
        engine.autoKeyframeEnabled = true
        assertTrue(engine.notifyUserValueChange(key, 42.0, 300))
        val snap = engine.evaluate(300)
        assertEquals(42.0, snap.floatValue(key)!!, EPS)
        assertEquals(42.0, engine.evaluate(500).floatValue(key)!!, EPS)
    }

    @Test fun editSession_commitSingleCommand_undoRedo() {
        val engine = AnimationEngine()
        val key = BindingKey("L", Props.OPACITY)
        engine.ensureTrack(key, PropertyType.FLOAT)
        engine.addKeyframe(key, Keyframe(engine.nextId(), 0, value = 0.0))
        engine.addKeyframe(key, Keyframe(engine.nextId(), 1000, value = 100.0))
        val undoLog = ArrayList<com.ahstudio.animation.undo.UndoableCommand>()
        engine.undoSink = com.ahstudio.animation.undo.UndoSink { undoLog.add(it) }
        val kf = engine.trackFor(key)!!.get().data.keyframes[1]
        engine.editSession(key).moveKeyframes(key, setOf(kf.id), 500).commit("drag")
        assertEquals(1, undoLog.size)
        assertEquals(1500.0, engine.trackFor(key)!!.get().data.keyframes[1].timeMs.toDouble(), 0.0)
        undoLog[0].undo()
        assertEquals(1000.0, engine.trackFor(key)!!.get().data.keyframes[1].timeMs.toDouble(), 0.0)
        undoLog[0].redo()
        assertEquals(1500.0, engine.trackFor(key)!!.get().data.keyframes[1].timeMs.toDouble(), 0.0)
    }

    @Test fun serialization_roundtripEvaluationEquivalent() {
        val engine = AnimationEngine()
        val k = BindingKey("L1", Props.POSITION)
        engine.ensureTrack(k, PropertyType.VEC2, spatial = true)
        engine.addKeyframe(k, Keyframe(engine.nextId(), 0, vecValue = Vec2(0.0, 0.0),
            spatialOutHandle = Vec2(80.0, 0.0), interpolation = InterpolationType.BEZIER, outTangent = 60.0))
        engine.addKeyframe(k, Keyframe(engine.nextId(), 2000, vecValue = Vec2(300.0, 200.0),
            spatialInHandle = Vec2(-80.0, 100.0), interpolation = InterpolationType.HOLD))
        val op = BindingKey("L1", Props.OPACITY)
        engine.ensureTrack(op, PropertyType.FLOAT)
        engine.addKeyframe(op, Keyframe(engine.nextId(), 0, value = 0.0, easing = EasingType.BACK_OUT))
        engine.addKeyframe(op, Keyframe(engine.nextId(), 900, value = 100.0))
        engine.addMarker(500, "beat")
        engine.addClip(BuiltinPresets.pop())

        val json = AnimationSerializer.serialize(engine).toString()
        val engine2 = AnimationEngine()
        val report = AnimationSerializer.load(engine2, JSONObject(json))
        assertTrue(report.issues.isEmpty())
        for (t in longArrayOf(0, 250, 500, 1000, 1500, 2000, 2500)) {
            val a = engine.evaluate(t).values; val b = engine2.evaluate(t).values
            assertEquals(a.size, b.size)
            for ((key, va) in a) {
                val vb = b[key]!!
                if (va is EvaluatedValue.FloatV) assertEquals(va.value, (vb as EvaluatedValue.FloatV).value, 1e-9)
                if (va is EvaluatedValue.Vec2V) {
                    assertEquals(va.value.x, (vb as EvaluatedValue.Vec2V).value.x, 1e-9)
                    assertEquals(va.value.y, vb.value.y, 1e-9)
                }
            }
        }
        assertEquals(1, engine2.markers().size)
        assertEquals(1, engine2.clips().size)
    }

    @Test fun nanValue_rejectedGracefully() {
        val engine = AnimationEngine()
        val k = BindingKey("L", "P")
        engine.ensureTrack(k, PropertyType.FLOAT)
        var threw = false
        try { KeyframeFactory.create(engine.nextId(), 0, Double.NaN) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        val bad = JSONObject("""{"animationVersion":1,"tracks":[{"target":"L","property":"P","type":"FLOAT",
            "kfs":[{"t":0,"v":0},{"t":100,"v":1e999}]}]}""")
        val report = AnimationSerializer.load(engine, bad)
        assertTrue(report.issues.isNotEmpty())
        assertEquals(1, report.tracksLoaded)
        assertEquals(0.0, engine.evaluate(100).floatValue(k)!!, EPS)
    }
}
