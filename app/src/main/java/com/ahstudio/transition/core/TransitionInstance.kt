package com.ahstudio.transition.core

/** User-facing, serializable placement of a definition on two timeline clips. */
data class TransitionInstance(
    val instanceId: String,
    val definitionId: String,
    val outgoingClipId: String,
    val incomingClipId: String,
    val startMs: Long,
    val endMs: Long,
    val alignment: TransitionAlignment = TransitionAlignment.CENTERED,
    val customAnchor: Float = 0.5f,
    val easing: Easing = Easing.linear(),
    val parameters: Map<String, ParamValue> = emptyMap(),
    val enabled: Boolean = true,
    val schemaVersion: Int = 1,
)
