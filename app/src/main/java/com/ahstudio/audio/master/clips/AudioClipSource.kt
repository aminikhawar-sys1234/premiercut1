package com.ahstudio.audio.master.clips

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.model.AudioClipModel

interface AudioClipSource {
    fun readClip(clip: AudioClipModel, ctx: AudioRenderContext, out: AudioBuffer): ClipRead
}
