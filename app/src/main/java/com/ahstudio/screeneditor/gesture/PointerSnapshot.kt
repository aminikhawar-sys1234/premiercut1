package com.ahstudio.screeneditor.gesture

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.hypot

class PointerSnapshot private constructor(
    val ids: IntArray,
    val xs: FloatArray,
    val ys: FloatArray,
    val eventTimeMs: Long
) {
    val count: Int get() = ids.size
    private val centroidPoint = PointF()

    fun centroid(): PointF {
        if (count == 0) {
            centroidPoint.set(0f, 0f)
            return centroidPoint
        }
        var x = 0f
        var y = 0f
        for (i in 0 until count) {
            x += xs[i]
            y += ys[i]
        }
        centroidPoint.set(x / count, y / count)
        return centroidPoint
    }

    fun span(): Float {
        if (count < 2) return 0f
        val dx = xs[1] - xs[0]
        val dy = ys[1] - ys[0]
        return hypot(dx, dy)
    }

    fun angleRad(): Float {
        if (count < 2) return 0f
        return atan2(ys[1] - ys[0], xs[1] - xs[0])
    }

    companion object {
        fun obtain(ids: IntArray, xs: FloatArray, ys: FloatArray, t: Long): PointerSnapshot =
            PointerSnapshot(ids.copyOf(), xs.copyOf(), ys.copyOf(), t)
    }
}
