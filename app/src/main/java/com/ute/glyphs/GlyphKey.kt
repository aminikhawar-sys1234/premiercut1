package com.ute.glyphs

/** Atlas cache key. Cluster-text based so it works on every API level. */
data class GlyphKey(
    val fontId: Int,
    val clusterText: String,     // the grapheme cluster's characters
    val rasterSizePx: Int,       // fixed raster bucket (SDF mode) or actual px (bitmap mode)
    val sdf: Boolean,
)
