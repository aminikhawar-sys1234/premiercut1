package com.ahstudio.composition.core

/** Composition time mapping. The AUTHORITATIVE time always comes from MasterTimelineClock. */
sealed class TimeMapper {
    object Identity : TimeMapper()
    data class Reverse(val inUs: Long, val outUs: Long) : TimeMapper()
    data class Speed(val inUs: Long, val factor: Float) : TimeMapper()
    data class Segmented(val segments: List<Segment>) : TimeMapper() {
        data class Segment(val parentStartUs: Long, val childStartUs: Long, val speed: Float)
    }
    fun parentToChild(p: Long): Long = when (this) {
        Identity -> p
        is Reverse -> inUs + outUs - p
        is Speed -> inUs + ((p - inUs) * factor).toLong()
        is Segmented -> {
            var last = segments.firstOrNull()
            for (s in segments) { if (p >= s.parentStartUs) last = s else break }
            val s = last ?: return p
            s.childStartUs + ((p - s.parentStartUs) * s.speed).toLong()
        }
    }
}
