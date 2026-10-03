package com.ahstudio.captions.rendering

import com.ahstudio.captions.layout.LayoutCache

class CaptionRenderCache {
    val layoutCache = LayoutCache()
    val glyphAtlas = GlyphAtlas()
    fun invalidateStyle(fontFamily: String, fontSizeSp: Float) =
        glyphAtlas.invalidateFont("$fontFamily|$fontSizeSp")
    fun clear() { layoutCache.clear() }
}
