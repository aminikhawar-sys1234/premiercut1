package com.ahstudio.screeneditor.ports

import kotlinx.coroutines.flow.StateFlow

interface PlaybackPort {
    val isPlaying: StateFlow<Boolean>
    fun play()
    fun pause()
    fun seekTo(us: Long)
}
