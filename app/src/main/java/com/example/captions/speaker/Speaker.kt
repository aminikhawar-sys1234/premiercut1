package com.ahstudio.captions.speaker

import com.ahstudio.captions.core.model.CaptionSpeaker
import com.ahstudio.captions.core.model.CaptionWord
import com.ahstudio.captions.recognition.SpeechSource

interface SpeakerDiarizationEngine {
    val id: String
    suspend fun assignSpeakers(words: List<CaptionWord>, audio: SpeechSource?): Pair<List<CaptionWord>, List<CaptionSpeaker>>
}

class SingleSpeakerDiarizationEngine : SpeakerDiarizationEngine {
    override val id = "single-speaker"
    private val speaker = CaptionSpeaker("spk_1", "Speaker 1")
    override suspend fun assignSpeakers(
        words: List<CaptionWord>, audio: SpeechSource?,
    ): Pair<List<CaptionWord>, List<CaptionSpeaker>> = words.map { it.copy(speakerId = speaker.id) } to listOf(speaker)
}
