package com.example.ai.providers

import android.content.Context
import android.graphics.Bitmap
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import java.io.File

data class VideoHighlightSegment(
  val title: String,
  val startTimeMs: Long,
  val endTimeMs: Long,
  val score: Float,
  val description: String
)

/**
 * SpeechToText provider interface.
 * Transcribes actual imported audio files into timed caption segments.
 */
interface SpeechToTextProvider {
  val isAvailable: Boolean
  val providerName: String
  suspend fun transcribeAudio(audioFile: File, language: String): Result<List<TextClip>>
  suspend fun transcribeAudio(audioUriOrPath: String, language: String): Result<List<TextClip>>
}
typealias SpeechToText = SpeechToTextProvider

/**
 * Translation provider interface.
 * Translates subtitle text or caption clips while preserving timing and structure.
 */
interface TranslationProvider {
  val isAvailable: Boolean
  val providerName: String
  suspend fun translateText(text: String, targetLanguage: String): Result<String>
  suspend fun translateCaptions(captions: List<TextClip>, targetLanguage: String): Result<List<TextClip>>
}
typealias Translation = TranslationProvider

/**
 * BackgroundRemoval provider interface.
 * Processes an actual bitmap or video frame and returns a transparent result or mask.
 */
interface BackgroundRemovalProvider {
  val isAvailable: Boolean
  val providerName: String
  suspend fun removeBackground(inputBitmap: Bitmap): Result<Bitmap>
  suspend fun generateAlphaMask(inputBitmap: Bitmap): Result<Bitmap>
}
typealias BackgroundRemoval = BackgroundRemovalProvider

/**
 * NoiseReduction provider interface.
 * Processes real audio files to reduce background noise, hiss, and hum.
 */
interface NoiseReductionProvider {
  val isAvailable: Boolean
  val providerName: String
  suspend fun reduceNoise(audioFile: File): Result<File>
}
typealias NoiseReduction = NoiseReductionProvider

/**
 * TextToSpeech provider interface.
 * Synthesizes written text into an actual playable audio file.
 */
interface TextToSpeechProvider {
  val isAvailable: Boolean
  val providerName: String
  suspend fun synthesizeSpeech(text: String, pitch: Float, speed: Float): Result<File>
}
typealias TextToSpeech = TextToSpeechProvider

/**
 * HighlightDetection provider interface.
 * Analyzes video timeline and content metadata to detect peak moments.
 */
interface HighlightDetectionProvider {
  val isAvailable: Boolean
  val providerName: String
  suspend fun detectHighlights(videoTitle: String, durationMs: Long): Result<List<VideoHighlightSegment>>
  suspend fun analyzeHighlights(videoTitle: String, durationMs: Long): Result<List<VideoHighlightSegment>> = detectHighlights(videoTitle, durationMs)
  suspend fun analyzeTimelineHighlights(timeline: Timeline): Result<List<VideoHighlightSegment>>
}
typealias HighlightDetection = HighlightDetectionProvider
typealias HighlightAnalysisProvider = HighlightDetectionProvider
