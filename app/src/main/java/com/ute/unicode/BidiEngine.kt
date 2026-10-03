package com.ute.unicode

import java.text.Bidi as PlatformBidi
import com.ute.model.Direction

data class BidiRun(val start: Int, val end: Int, val level: Int) {
    val isRtl: Boolean get() = level % 2 == 1
}

/**
 * Real Unicode BiDi (UAX#9):
 *  - Run levels come from the platform ICU-backed implementation (java.text.Bidi),
 *    which correctly handles numbers, punctuation mirroring, and nested levels.
 *  - Visual reordering implements rule L2 at run granularity: repeatedly reverse
 *    maximal sequences at the highest level down to the base level.
 * No string reversal. Ever.
 */
class BidiEngine {

    fun resolveDirection(requested: Direction, text: String): Direction =
        if (requested != Direction.AUTO) requested
        else if (PlatformBidi(text, PlatformBidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) Direction.LTR else Direction.RTL

    fun baseLevel(requested: Direction, text: String): Int =
        if (requested == Direction.RTL) 1
        else if (requested == Direction.LTR) 0
        else if (PlatformBidi(text, PlatformBidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) 0 else 1

    fun runs(text: String, requested: Direction): List<BidiRun> {
        if (text.isEmpty()) return emptyList()
        val bidi = PlatformBidi(text, if (requested == Direction.RTL)
            PlatformBidi.DIRECTION_RIGHT_TO_LEFT
        else PlatformBidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)

        val out = ArrayList<BidiRun>(bidi.runCount)
        for (r in 0 until bidi.runCount) {
            out.add(BidiRun(bidi.getRunStart(r), bidi.getRunLimit(r), bidi.getRunLevel(r)))
        }
        return out
    }

    /**
     * UAX#9 L2: from the highest run level down to base, reverse contiguous runs
     * sharing that level. Produces visual order for line assembly.
     */
    fun visualOrder(logical: List<BidiRun>, baseLevel: Int): List<BidiRun> {
        val order = logical.toMutableList()
        var level = order.maxOfOrNull { it.level } ?: return order
        while (level > baseLevel) {
            var i = 0
            while (i < order.size) {
                if (order[i].level == level) {
                    var j = i
                    while (j < order.size && order[j].level == level) j++
                    // reverse the contiguous block [i, j)
                    for (a in i until (i + j) / 2) {
                        val b = j - 1 - (a - i)
                        val tmp = order[a]; order[a] = order[b]; order[b] = tmp
                    }
                    i = j
                } else i++
            }
            level--
        }
        return order
    }

    /** Logical index → visual index map (for cursor/caret mapping in a host editor). */
    fun logicalToVisualMap(text: String, requested: Direction): IntArray {
        val logical = runs(text, requested)
        val visual = visualOrder(logical, baseLevel(requested, text))
        val map = IntArray(text.length)
        for (v in visual.indices) {
            val run = visual[v]
            for (i in run.start until run.end) map[i] = v
        }
        return map
    }
}
