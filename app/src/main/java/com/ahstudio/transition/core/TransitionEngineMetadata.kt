package com.ahstudio.transition.core

object TransitionEngineMetadata {
    /** Bump when shader/uniform contract or graph semantics change. Packages declare compatibility. */
    const val ENGINE_VERSION = 1
    const val SUPPORTED_MANIFEST_VERSION = 1
    const val HARD_SHADER_SIZE_CAP_BYTES = 256 * 1024
    const val DEFAULT_SHADER_SIZE_CAP_BYTES = 64 * 1024
    const val MAX_DURATION_MS = 60_000L
}
