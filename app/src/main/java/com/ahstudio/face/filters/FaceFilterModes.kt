package com.ahstudio.face.filters

object FilterModes {
    private val map = mapOf(
        "face_blur" to 0,
        "face_pixelate" to 1,
        "skin_smooth" to 2,
        "face_bright_contrast" to 3,
        "face_glow" to 4,
    )
    fun glModeOf(id: String): Int? = map[id]
    val available: Set<String> get() = map.keys
}
