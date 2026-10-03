package com.ahstudio.transition.core

sealed class TransitionError(val message: String, val cause: Throwable? = null) {
    class Validation(message: String) : TransitionError(message)
    class UnsupportedFeature(message: String) : TransitionError(message)
    class ShaderCompilation(message: String, val log: String) : TransitionError(message)
    class ResourceAllocation(message: String) : TransitionError(message)
    class Render(message: String, cause: Throwable? = null) : TransitionError(message, cause)
    class Package(message: String) : TransitionError(message)
}

sealed class TransitionResult<out T> {
    data class Ok<T>(val value: T, val warnings: List<String> = emptyList()) : TransitionResult<T>()
    data class Err(val error: TransitionError) : TransitionResult<Nothing>()
}

inline fun <T, R> TransitionResult<T>.map(f: (T) -> R): TransitionResult<R> = when (this) {
    is TransitionResult.Ok -> TransitionResult.Ok(f(value), warnings)
    is TransitionResult.Err -> this
}
