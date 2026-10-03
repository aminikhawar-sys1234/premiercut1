package com.ahstudio.face

import com.ahstudio.face.core.*
import com.ahstudio.face.detection.BoundsPx
import com.ahstudio.face.detection.RawFaceDetection
import com.ahstudio.face.geometry.FitMode
import com.ahstudio.face.geometry.VideoTransform
import com.ahstudio.face.overlay.*
import com.ahstudio.face.performance.TrackingCache
import com.ahstudio.face.temporal.OneEuroFilter
import com.ahstudio.face.timeline.FaceEffectClipData
import com.ahstudio.face.timeline.FaceEffectSerializer
import com.ahstudio.face.tracking.MultiFaceTracker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun det(id: Int?, x: Float, y: Float) = RawFaceDetection(
    boundsPx = BoundsPx((x - 0.08f) * 1000, (y - 0.14f) * 1000, (x + 0.08f) * 1000, (y + 0.14f) * 1000),
    eulerX = 0f, eulerY = 0f, eulerZ = 0f,
    landmarksPx = emptyMap(),
    smiling = null, leftEyeOpen = null, rightEyeOpen = null,
    trackingId = id
)

@RunWith(RobolectricTestRunner::class)
class FaceEngineTestSuite {

    @Test
    fun testTwoCrossingFacesKeepIdentities() {
        val t = MultiFaceTracker(FaceTrackingConfig())
        var out = emptyList<TrackedFace>()
        for (i in 0..40) {
            val p = i / 40f
            out = t.update(
                listOf(det(1, 0.2f + 0.6f * p, 0.4f), det(2, 0.8f - 0.6f * p, 0.4f)),
                1000, 1000, i * 33_333L, false
            )
        }
        assertEquals(2, out.size)
        val a = out.first { it.detectorTrackId == 1 }
        val b = out.first { it.detectorTrackId == 2 }
        assertNotEquals(a.trackId.value, b.trackId.value)
        assertTrue(a.bounds.centerX > 0.6f)
        assertTrue(b.bounds.centerX < 0.4f)
    }

    @Test
    fun testDetectionGapCoastsAsPredictedAndRecoversSameId() {
        val t = MultiFaceTracker(FaceTrackingConfig())
        t.update(listOf(det(null, 0.5f, 0.5f)), 1000, 1000, 0L, false)
        val coast = t.update(emptyList(), 1000, 1000, 33_333L, false)
        assertEquals(1, coast.size)
        assertEquals(FaceTrackState.PREDICTED, coast[0].state)
        assertEquals(1L, coast[0].trackId.value)
        val rec = t.update(listOf(det(null, 0.5f, 0.5f)), 1000, 1000, 66_666L, false)
        assertEquals(1, rec.size)
        assertEquals(FaceTrackState.RECOVERED, rec[0].state)
        assertEquals(1L, rec[0].trackId.value)
    }

    @Test
    fun testSameSpotReentryKeepsIdAndDistantFaceGetsNewId() {
        val t = MultiFaceTracker(FaceTrackingConfig())
        t.update(listOf(det(null, 0.2f, 0.5f)), 1000, 1000, 0L, false)
        val same = t.update(listOf(det(null, 0.22f, 0.5f)), 1000, 1000, 1_000_000L, false)
        assertEquals(1L, same[0].trackId.value)
        t.update(listOf(det(null, 0.9f, 0.9f)), 1000, 1000, 2_000_000L, false)
        val out = t.update(listOf(det(null, 0.9f, 0.88f)), 1000, 1000, 2_033_333L, false)
        assertTrue(out.any { it.trackId.value == 2L })
    }

    @Test
    fun testOneEuroPassesThroughFirstSample() {
        val f = OneEuroFilter(1.7, 0.3)
        assertEquals(5.0, f.filter(5.0, 1.0 / 30.0), 1e-9)
    }

    @Test
    fun testOneEuroDampsJitterMoreThanFastMotion() {
        val f = OneEuroFilter(1.0, 0.05)
        var out = 5.0
        repeat(30) { out = f.filter(5.0 + (if (it % 2 == 0) 0.05 else -0.05), 1.0 / 30.0) }
        assertEquals(5.0, out, 0.05)

        val fast = OneEuroFilter(1.0, 0.05)
        var o2 = 0.0
        repeat(30) { o2 = fast.filter(it * 0.05, 1.0 / 30.0) }
        assertTrue(o2 > 1.0)
    }

    @Test
    fun testVideoTransformContainLetterboxesAndCenters() {
        val t = VideoTransform(1920, 1080, viewportWidth = 1080, viewportHeight = 1920, fitMode = FitMode.CONTAIN)
        val c = t.toViewport(Vec2(0.5f, 0.5f))
        assertEquals(540f, c.x, 0.01f)
        assertEquals(960f, c.y, 0.01f)
        assertEquals(0f, t.toViewport(Vec2(0f, 0.5f)).x, 0.01f)
        assertEquals(1080f, t.toViewport(Vec2(1f, 0.5f)).x, 0.01f)
    }

    @Test
    fun testVideoTransformRotation90SwapsAxes() {
        val t = VideoTransform(100, 200, rotationDegrees = 90)
        assertEquals(Vec2(1f, 0f), t.rotatePoint(Vec2(0f, 0f)))
    }

    @Test
    fun testVideoTransformMirrorFlipsXOnly() {
        val t = VideoTransform(1000, 1000, mirror = true)
        assertEquals(Vec2(0f, 250f), t.toViewport(Vec2(1f, 0.25f)))
    }

    @Test
    fun testVideoTransformCropRotationMirrorRoundTrip() {
        val t = VideoTransform(1920, 1080, 90, true,
            FaceBounds(0.1f, 0.1f, 0.9f, 0.9f), 720, 1280, FitMode.CONTAIN)
        for (p in listOf(Vec2(0.2f, 0.3f), Vec2(0.5f, 0.5f), Vec2(0.8f, 0.9f))) {
            val rt = t.fromViewport(t.toViewport(p))
            assertEquals(p.x, rt.x, 1e-3f)
            assertEquals(p.y, rt.y, 1e-3f)
        }
    }

    @Test
    fun testTrackingCacheHitsAndInvalidates() {
        val c = TrackingCache(bucketUs = 33_333)
        c.put("clip", 0, FaceTrackingResult(66_666, 100, 100, false, emptyList(), DetectionSource.DETECTOR))
        assertNotNull(c.get("clip", 0, 80_000))
        assertNull(c.get("clip", 0, 500_000))
        c.bumpGeneration()
        assertNull(c.get("clip", 0, 66_666))
    }

    @Test
    fun testEffectWindowFollowsClipLocalSourceTime() {
        val d = FaceEffectClipData("e", "c", startSourceUs = 1_000_000, endSourceUs = 3_000_000)
        assertTrue(d.isActiveAt(2_000_000))
        assertFalse(d.isActiveAt(500_000))
        assertFalse(d.isActiveAt(3_500_000))
        assertTrue(d.copy(startSourceUs = 0).isActiveAt(0L))
    }

    @Test
    fun testPlacementIsResolutionIndependent() {
        val face = TrackedFace(FaceTrackId(1), null, 0L,
            FaceBounds(0.4f, 0.3f, 0.6f, 0.7f), FaceRotation(0f, 0f, 12f),
            null, null, 0.9f, FaceTrackState.DETECTED, interOcular = 0.1f)
        val spec = FaceOverlaySpec("e", "c", anchor = FaceAnchor.FOREHEAD, scale = 1.5f)
        val p1 = OverlayTransformSolver.solve(face, spec, KeyframeSample.IDENTITY, false)
        val p2 = OverlayTransformSolver.solve(face, spec, KeyframeSample.IDENTITY, false)
        assertEquals(p1.centerFrame, p2.centerFrame)
        val preview = VideoTransform(1920, 1080, viewportWidth = 800, viewportHeight = 600)
        val export = VideoTransform(1920, 1080, viewportWidth = 1920, viewportHeight = 1080)
        assertEquals(
            preview.toViewport(p1.centerFrame).x / 800f,
            export.toViewport(p2.centerFrame).x / 1920f, 1e-4f)
    }

    @Test
    fun testFaceEffectSerializerRoundTrip() {
        val d = FaceEffectClipData("e1", "c1", stickerId = "sunglasses", anchor = "EYES_CENTER",
            scale = 1.7f, deform = mapOf("eyeEnlarge" to 0.5f))
        val json = FaceEffectSerializer.toJson(d)
        val deserialized = FaceEffectSerializer.fromJson(json)
        assertEquals(d.effectId, deserialized.effectId)
        assertEquals(d.clipId, deserialized.clipId)
        assertEquals(d.stickerId, deserialized.stickerId)
        assertEquals(d.anchor, deserialized.anchor)
        assertEquals(d.scale, deserialized.scale, 1e-4f)
        assertEquals(d.deform["eyeEnlarge"], deserialized.deform["eyeEnlarge"])
    }
}
