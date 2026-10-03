package com.ahstudio.transition

import com.ahstudio.transition.core.ParamType
import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.ParameterMath
import com.ahstudio.transition.core.TransitionParameterDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ParameterCoercionTest {
    private val zoomAmount = TransitionParameterDefinition("zoomAmount", "Zoom", ParamType.FLOAT,
        ParamValue.FloatValue(1.6f),
        min = ParamValue.FloatValue(1.0f), max = ParamValue.FloatValue(3.0f))

    @Test fun `value below min clamps`() {
        val (v, w) = ParameterMath.coerce(ParamValue.FloatValue(-2f), zoomAmount)
        assertNull(w); assertEquals(1.0f, (v as ParamValue.FloatValue).value, 1e-6f)
    }
    @Test fun `value above max clamps`() {
        val v = ParameterMath.coerce(ParamValue.FloatValue(99f), zoomAmount).first!!
        assertEquals(3.0f, (v as ParamValue.FloatValue).value, 1e-6f)
    }
    @Test fun `default within range unchanged`() {
        val v = ParameterMath.coerce(ParamValue.FloatValue(1.6f), zoomAmount).first!!
        assertEquals(1.6f, (v as ParamValue.FloatValue).value, 1e-6f)
    }
    @Test fun `type mismatch flagged`() {
        val (_, w) = ParameterMath.coerce(ParamValue.BoolValue(true), zoomAmount)
        assertNotNull(w)
    }
    @Test fun `angle wraps to 0-360`() {
        val def = TransitionParameterDefinition("rot", "Rot", ParamType.ANGLE, ParamValue.AngleValue(0f))
        val v = ParameterMath.coerce(ParamValue.AngleValue(-45f), def).first!! as ParamValue.AngleValue
        assertEquals(315f, v.degrees, 1e-4f)
    }
    @Test fun `normalized clamps`() {
        val def = TransitionParameterDefinition("s", "S", ParamType.NORMALIZED, ParamValue.NormalizedValue(0f))
        val v = ParameterMath.coerce(ParamValue.FloatValue(2f), def).first!! as ParamValue.NormalizedValue
        assertEquals(1f, v.value, 1e-6f)
    }
    @Test fun `color components clamp to 0-1`() {
        val def = TransitionParameterDefinition("c", "C", ParamType.COLOR,
            ParamValue.ColorValue(listOf(0f, 0f, 0f, 0f)))
        val v = ParameterMath.coerce(ParamValue.ColorValue(listOf(2f, -1f, 0.5f, 1f)), def).first!!
                as ParamValue.ColorValue
        assertEquals(listOf(1f, 0f, 0.5f, 1f), v.rgba)
    }
    @Test fun `enum index out of range rejected`() {
        val def = TransitionParameterDefinition("d", "D", ParamType.ENUM,
            ParamValue.EnumValue(0, "left"), enumValues = listOf("left", "right"))
        val (_, w) = ParameterMath.coerce(ParamValue.IntValue(5), def)
        assertNotNull(w)
    }
}
