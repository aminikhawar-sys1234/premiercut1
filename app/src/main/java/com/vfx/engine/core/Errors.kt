package com.vfx.engine.core

sealed class VfxEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ShaderCompileError(val shaderName: String, val compileLog: String) :
  VfxEngineException("Shader compilation failed for $shaderName: $compileLog")

class GraphCycleError(val cyclePath: List<String>) :
  VfxEngineException("RenderGraph cycle detected: ${cyclePath.joinToString(" -> ")}")

class OutOfVramException(val requestedBytes: Long, val availableBytes: Long) :
  VfxEngineException("Insufficient VRAM: requested $requestedBytes bytes, available $availableBytes bytes")

class InvalidEffectException(val effectId: String, val reason: String) :
  VfxEngineException("Invalid effect $effectId: $reason")

/** Structured engine errors. Optional effects must never crash the pipeline. */
sealed class EffectEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class ShaderCompile(val shaderName: String, val log: String) : EffectEngineException("Shader compile failed: $shaderName\n$log")
    class ShaderLink(val programName: String, val log: String) : EffectEngineException("Program link failed: $programName\n$log")
    class InvalidFramebuffer(val fbo: Int, val status: Int) : EffectEngineException("Framebuffer $fbo incomplete, status=0x${status.toString(16)}")
    class InvalidTexture(message: String) : EffectEngineException(message)
    class EglFailure(val operation: String, val eglError: Int) : EffectEngineException("EGL failure at $operation, error=0x${eglError.toString(16)}")
    class ContextLost : EffectEngineException("EGL context lost — all GPU resources invalidated")
    class UnsupportedFeature(val feature: String, val reason: String) : EffectEngineException("Unsupported: $feature ($reason)")
    class InvalidParameter(val effectId: String, val paramId: String, val value: Any?) : EffectEngineException("Invalid parameter '$paramId' on '$effectId': $value")
    class InvalidLut(message: String) : EffectEngineException(message)
    class ResourceExhausted(val what: String) : EffectEngineException("Resource exhausted: $what")
    class Serialization(message: String) : EffectEngineException(message)
}

/** A resolved runtime failure used for graceful degradation. */
data class EffectRuntimeFailure(val effectId: String, val error: EffectEngineException, val skippedAtTimeUs: Long)
