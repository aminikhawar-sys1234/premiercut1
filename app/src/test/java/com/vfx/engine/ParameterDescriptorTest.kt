package com.vfx.engine

import com.vfx.engine.core.math.Color
import com.vfx.engine.core.math.Vec2
import com.vfx.engine.core.params.ParamConstraints
import com.vfx.engine.core.params.ParamMap
import com.vfx.engine.core.params.ParameterDescriptor
import com.vfx.engine.core.params.ParameterValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterDescriptorTest {

    @Test
    fun `param map retrieves float with fallback`() {
        val map = ParamMap(mapOf("intensity" to ParameterValue.FloatVal(0.8f)))
        assertEquals(0.8f, map.getFloat("intensity", 0f), 1e-5f)
        assertEquals(1.0f, map.getFloat("missing", 1.0f), 1e-5f)
    }

    @Test
    fun `param map retrieves int with fallback`() {
        val map = ParamMap(mapOf("iterations" to ParameterValue.IntVal(4)))
        assertEquals(4, map.getInt("iterations", 1))
        assertEquals(1, map.getInt("missing", 1))
    }

    @Test
    fun `param map retrieves bool and vec2 and color`() {
        val map = ParamMap(
            mapOf(
                "enabled" to ParameterValue.BoolVal(true),
                "offset" to ParameterValue.Vec2Val(Vec2(0.5f, -0.2f)),
                "tint" to ParameterValue.ColorVal(Color(1f, 0.5f, 0.2f, 1f))
            )
        )

        assertTrue(map.getBool("enabled", false))
        assertFalse(map.getBool("missing", false))
        assertEquals(0.5f, map.getVec2("offset").x, 1e-5f)
        assertEquals(-0.2f, map.getVec2("offset").y, 1e-5f)
        assertEquals(1f, map.getColor("tint").r, 1e-5f)
    }

    @Test
    fun `parameter descriptors store metadata correctly`() {
        val floatDesc = ParameterDescriptor.FloatParam(
            name = "radius",
            default = 8f,
            constraints = ParamConstraints(min = 0f, max = 64f, step = 1f)
        )
        assertEquals("radius", floatDesc.name)
        assertEquals(8f, (floatDesc.defaultValue as ParameterValue.FloatVal).value, 1e-5f)
        assertEquals(64f, floatDesc.constraints.max, 1e-5f)
    }
}
