package com.ahstudio.screeneditor.ports

enum class KeyableProperty {
    POSITION_X,
    POSITION_Y,
    SCALE_X,
    SCALE_Y,
    ROTATION,
    OPACITY,
    CROP_L,
    CROP_T,
    CROP_R,
    CROP_B
}

data class KeyframeView(
    val timeUs: Long,
    val value: Float
)

interface KeyframePort {
    fun hasTrack(layerId: String, prop: KeyableProperty): Boolean
    fun keys(layerId: String, prop: KeyableProperty): List<KeyframeView>
    fun upsert(layerId: String, prop: KeyableProperty, timeUs: Long, value: Float)
    fun remove(layerId: String, prop: KeyableProperty, timeUs: Long)
    fun sample(layerId: String, prop: KeyableProperty, timeUs: Long, fallback: Float): Float
}
