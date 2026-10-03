package com.ute.shaping

/** Adapter seam: replaceable by native HarfBuzz/ICU without touching any caller. */
interface TextShapingEngine {
    fun shape(request: ShapingRequest): ShapingResult
    fun supportsGlyphIds(): Boolean
}
