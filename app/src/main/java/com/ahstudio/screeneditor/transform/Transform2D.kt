package com.ahstudio.screeneditor.transform

data class Transform2D(
    val translationX: Float = 0f,      // editor-space position of the ANCHOR point
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
    val anchorX: Float = 0.5f,         // normalized within (cropped) source
    val anchorY: Float = 0.5f,
    val flipH: Boolean = false,
    val flipV: Boolean = false
)

data class CropRect(
    val l: Float = 0f,
    val t: Float = 0f,
    val r: Float = 1f,
    val b: Float = 1f
) {
    val width: Float get() = (r - l).coerceAtLeast(0.001f)
    val height: Float get() = (b - t).coerceAtLeast(0.001f)
}
