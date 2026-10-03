package com.ahstudio.audio.master

sealed class AudioEngineResult<out T> {
    data class Success<T>(val value: T) : AudioEngineResult<T>() {
        val data: T get() = value
    }
    data class Failure(val code: AudioEngineError, val message: String, val cause: Throwable? = null) : AudioEngineResult<Nothing>() {
        val error: AudioEngineError get() = code
    }
    fun getOrNull(): T? = (this as? Success)?.value
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw cause ?: AudioEngineException(code, message, cause)
    }
    inline fun onSuccess(block: (T) -> Unit): AudioEngineResult<T> { if (this is Success) block(value); return this }
    inline fun onFailure(block: (AudioEngineError, String, Throwable?) -> Unit): AudioEngineResult<T> { if (this is Failure) block(code, message, cause); return this }
}

enum class AudioEngineError {
    NOT_INITIALIZED, INVALID_CLIP, SOURCE_NOT_FOUND, OVERLAP_REJECTED, DECODE_FAILED,
    PERMISSION_DENIED, RECORD_FAILED, EXPORT_FAILED, IO_FAILED, INVALID_PROJECT, UNKNOWN
}

class AudioEngineException(val code: AudioEngineError, message: String, cause: Throwable? = null) : Exception(message, cause)
