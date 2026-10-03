package com.ute.model

data class Transform2D(
    val x: Float = 0f, val y: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
    /** Anchor in normalized text-box space: (0.5, 0.5) = center pivot. */
    val anchorX: Float = 0.5f, val anchorY: Float = 0.5f,
    val opacity: Float = 1f,
)

data class Text3DConfig(
    val extrusionDepthPx: Float = 24f,
    val bevelWidthPx: Float = 2f,
    val bevelSteps: Int = 2,
    val materialId: String = "matte",
    val materialTint: Int = 0xFFFFFFFF.toInt(),
    val castShadow: Boolean = true,
    val backFaceVisible: Boolean = true,
)
