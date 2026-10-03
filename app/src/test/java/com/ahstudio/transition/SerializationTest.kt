package com.ahstudio.transition

import com.ahstudio.transition.core.Easing
import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.TransitionAlignment
import com.ahstudio.transition.core.TransitionInstance
import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.serialize.InstanceMigrator
import com.ahstudio.transition.serialize.TransitionInstanceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {
    private val i = TransitionInstance(
        "i1", "com.ahstudio.transition.cross_dissolve", "clipA", "clipB",
        1000, 1800, TransitionAlignment.CUSTOM, 0.25f,
        Easing.bezier(0.3f, 0f, 0.7f, 1f),
        mapOf(
            "softness" to ParamValue.NormalizedValue(0.25f),
            "zoomAmount" to ParamValue.FloatValue(1.6f),
            "direction" to ParamValue.Vec2Value(listOf(1f, 0f)),
            "tint" to ParamValue.ColorValue(listOf(1f, 0.5f, 0.25f, 1f)),
            "mode" to ParamValue.EnumValue(1, "right"),
            "iterations" to ParamValue.IntValue(4),
            "flip" to ParamValue.BoolValue(true),
            "angle" to ParamValue.AngleValue(315f)),
        enabled = true)

    @Test fun `round trip preserves everything`() {
        val decoded = (TransitionInstanceJson.decode(TransitionInstanceJson.encode(i))
                as TransitionResult.Ok).value
        assertEquals(i, decoded)
    }
    @Test fun `schema version present and current`() {
        assertTrue(TransitionInstanceJson.encode(i).contains("\"schemaVersion\":1"))
    }
    @Test fun `future schema version fails safely`() {
        val future = TransitionInstanceJson.encode(i).replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        assertTrue(TransitionInstanceJson.decode(future) is TransitionResult.Err)
    }
    @Test fun `migrator hook applied for old versions`() {
        val old = TransitionInstanceJson.encode(i).replace("\"schemaVersion\":1", "\"schemaVersion\":0")
        val result = TransitionInstanceJson.decode(old,
            migrators = mapOf(0 to InstanceMigrator { it.copy(schemaVersion = 1) }))
        assertTrue(result is TransitionResult.Ok)
    }
    @Test fun `corrupt json fails safely`() {
        assertTrue(TransitionInstanceJson.decode("{ not json") is TransitionResult.Err)
    }
    @Test fun `unknown alignment fails safely`() {
        assertTrue(TransitionInstanceJson.decode(
            TransitionInstanceJson.encode(i).replace("CUSTOM", "SIDEWAYS")) is TransitionResult.Err)
    }
    @Test fun `unknown param type dropped with warning`() {
        val text = TransitionInstanceJson.encode(i).replace("\"type\":\"normalized\"", "\"type\":\"hyper\"")
        val r = TransitionInstanceJson.decode(text)
        assertTrue(r is TransitionResult.Ok)
        assertTrue((r as TransitionResult.Ok).warnings.isNotEmpty())
        assertFalse(r.value.parameters.containsKey("softness"))
    }
}
