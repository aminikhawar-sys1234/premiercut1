package com.ahstudio.transition.core

/**
 * Immutable render-state snapshot (§23). Built on the edit thread, handed to the GL
 * thread; contains everything needed to render one frame. Structural equality makes
 * determinism directly testable (§20): same time + same state ⇒ equal snapshot.
 */
data class TransitionRenderSnapshot(
    val instanceId: String,
    val definitionId: String,
    val transitionName: String,
    val startMs: Long,
    val endMs: Long,
    val timelineTimeMs: Long,
    val rawProgress: Float,
    val progress: Float,                 // eased
    val parameters: Map<String, ResolvedParameter>,
    val shaders: Map<String, ShaderSource>,
    val graph: TransitionRenderGraphSpec,
    val outputWidth: Int,
    val outputHeight: Int,
    val outputPremultiplied: Boolean,
    val playbackDirection: Int,          // +1 / -1, informational
    val easing: Easing,
    val warnings: List<String> = emptyList(),
)
