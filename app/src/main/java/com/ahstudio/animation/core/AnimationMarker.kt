package com.ahstudio.animation.core

data class AnimationMarker(
    val id: Long, val timeMs: Long, val name: String,
    val category: String = "", val color: Int = 0,
    val metadata: Map<String, String> = emptyMap()
)
