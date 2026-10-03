package com.ahstudio.color.core

/** A GPU frame entering the color pipeline. CPU path wraps Bitmap-backed float arrays instead. */
data class ColorFrame(
    val textureId: Int,
    val textureTarget: Int,       // GL_TEXTURE_2D or GL_TEXTURE_EXTERNAL_OES
    val width: Int,
    val height: Int,
    val timestampNs: Long,
    val metadata: InputColorMetadata
)
