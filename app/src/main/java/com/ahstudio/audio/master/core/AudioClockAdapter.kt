package com.ahstudio.audio.master.core

interface AudioClockAdapter {
    fun playbackPositionSec(): Double
    fun isHostPlaying(): Boolean
    var audioPositionSource: (() -> Double)?
}
