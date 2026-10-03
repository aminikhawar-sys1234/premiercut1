package com.ahstudio.integration

import com.ahstudio.screeneditor.ports.PlaybackPort
import com.example.engine.TimelineEngine
import com.example.engine.controller.PlaybackController
import kotlinx.coroutines.flow.StateFlow

class AhPlaybackAdapter(
    private val timelineEngine: TimelineEngine,
    private val playbackController: PlaybackController? = null
) : PlaybackPort {

    override val isPlaying: StateFlow<Boolean> = timelineEngine.isPlaying

    override fun play() {
        if (playbackController != null) {
            playbackController.play()
        } else {
            if (!timelineEngine.isPlaying.value) {
                timelineEngine.togglePlayPause()
            }
        }
    }

    override fun pause() {
        if (playbackController != null) {
            playbackController.pause()
        } else {
            timelineEngine.pause()
        }
    }

    override fun seekTo(us: Long) {
        val ms = us / 1000L
        timelineEngine.setPosition(ms, snap = false)
        playbackController?.seekTo(ms)
    }
}
