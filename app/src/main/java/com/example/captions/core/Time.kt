package com.ahstudio.captions.core.time

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = TimelineUsSerializer::class)
data class TimelineUs(val micros: Long) : Comparable<TimelineUs> {
    val millis: Long get() = micros / 1000L
    val seconds: Double get() = micros / 1_000_000.0

    override fun compareTo(other: TimelineUs): Int = micros.compareTo(other.micros)

    operator fun plus(other: TimelineUs) = TimelineUs(micros + other.micros)
    operator fun minus(other: TimelineUs) = TimelineUs(micros - other.micros)
    operator fun plus(us: Long) = TimelineUs(micros + us)
    operator fun minus(us: Long) = TimelineUs(micros - us)

    fun coerceIn(min: TimelineUs, max: TimelineUs): TimelineUs =
        TimelineUs(micros.coerceIn(min.micros, max.micros))

    companion object {
        val ZERO = TimelineUs(0L)
        val MAX = TimelineUs(Long.MAX_VALUE / 4)
    }
}

object TimelineUsSerializer : KSerializer<TimelineUs> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TimelineUs", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: TimelineUs) = encoder.encodeLong(value.micros)
    override fun deserialize(decoder: Decoder): TimelineUs = TimelineUs(decoder.decodeLong())
}
