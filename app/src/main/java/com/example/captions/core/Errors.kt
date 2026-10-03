package com.ahstudio.captions.core.errors

sealed class CaptionEngineException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AudioExtraction(message: String, cause: Throwable? = null) : CaptionEngineException(message, cause)
    class RecognitionUnavailable(message: String) : CaptionEngineException(message)
    class UnsupportedLanguage(val languageTag: String, val providerId: String) :
        CaptionEngineException("language '$languageTag' not supported by provider '$providerId'")
    class RecognitionFailed(message: String, cause: Throwable? = null) : CaptionEngineException(message, cause)
    class TranscriptError(message: String) : CaptionEngineException(message)
    class SubtitleParse(message: String, cause: Throwable? = null) : CaptionEngineException(message, cause)
    class PersistenceError(message: String, cause: Throwable? = null) : CaptionEngineException(message, cause)
    class ExportError(message: String, cause: Throwable? = null) : CaptionEngineException(message, cause)
}
