package com.ahstudio.color

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

data class SliderEvent(val clipId: String, val path: String, val value: Float, val dragSession: Long)

/**
 * §58: strict thread separation. UI never touches GL or analysis.
 * Slider storms are conflated: at most one realtime tick per 33 ms per clip.
 */
class ColorEngineScope {
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val analysis = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sliders = MutableSharedFlow<SliderEvent>(
        replay = 0, extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sliders: SharedFlow<SliderEvent> = _sliders.asSharedFlow()

    fun emitSlider(e: SliderEvent) { _sliders.tryEmit(e) }

    fun <T> realtimeTick(block: suspend (List<SliderEvent>) -> T): Job =
        analysis.launch {
            sliders
                .sample(33)                       // coalesce storm → ≤30 fps of pipeline touches
                .runningFold(mutableListOf<SliderEvent>()) { acc, e -> acc.add(e); acc }
                .collectLatest { batch -> block(batch.toList()); batch.clear() }
        }

    fun cancelAll() { io.cancel(); analysis.cancel() }
}
