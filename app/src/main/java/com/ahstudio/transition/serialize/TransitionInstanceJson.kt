package com.ahstudio.transition.serialize

import com.ahstudio.transition.core.Easing
import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.TransitionAlignment
import com.ahstudio.transition.core.TransitionError
import com.ahstudio.transition.core.TransitionInstance
import com.ahstudio.transition.core.TransitionResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ParamDto(val type: String, val value: JsonElement, val name: String? = null)

@Serializable
data class EasingDto(val type: String, val x1: Float? = null, val y1: Float? = null,
                     val x2: Float? = null, val y2: Float? = null)

@Serializable
data class TransitionInstanceDto(
    val schemaVersion: Int = 1,
    val instanceId: String,
    val definitionId: String,
    val outgoingClipId: String,
    val incomingClipId: String,
    val startMs: Long,
    val endMs: Long,
    val alignment: String,
    val customAnchor: Float? = null,
    val easing: EasingDto,
    val parameters: Map<String, ParamDto> = emptyMap(),
    val enabled: Boolean = true,
)

fun interface InstanceMigrator { fun migrate(dto: TransitionInstanceDto): TransitionInstanceDto }

object TransitionInstanceJson {
    const val CURRENT_SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(i: TransitionInstance): String =
        json.encodeToString(TransitionInstanceDto.serializer(), i.toDto())

    fun decode(text: String, migrators: Map<Int, InstanceMigrator> = emptyMap()):
            TransitionResult<TransitionInstance> {
        val base = try { json.decodeFromString(TransitionInstanceDto.serializer(), text) }
        catch (e: Exception) {
            return TransitionResult.Err(
                TransitionError.Validation("Instance JSON parse failed: ${e.message}"))
        }
        var dto = base
        var v = dto.schemaVersion
        while (v < CURRENT_SCHEMA_VERSION) {
            val m = migrators[v] ?: return TransitionResult.Err(
                TransitionError.Validation("No migrator for schemaVersion $v"))
            dto = m.migrate(dto)
            v = dto.schemaVersion
        }
        if (v != CURRENT_SCHEMA_VERSION) return TransitionResult.Err(
            TransitionError.Validation("schemaVersion $v is newer than engine $CURRENT_SCHEMA_VERSION"))
        return dto.toInstance()
    }

    private fun TransitionInstance.toDto() = TransitionInstanceDto(
        schemaVersion = schemaVersion,
        instanceId = instanceId, definitionId = definitionId,
        outgoingClipId = outgoingClipId, incomingClipId = incomingClipId,
        startMs = startMs, endMs = endMs,
        alignment = alignment.name,
        customAnchor = if (alignment == TransitionAlignment.CUSTOM) customAnchor else null,
        easing = EasingDto(easing.type.name,
            if (easing.type == Easing.Type.CUBIC_BEZIER) easing.bx1 else null,
            if (easing.type == Easing.Type.CUBIC_BEZIER) easing.by1 else null,
            if (easing.type == Easing.Type.CUBIC_BEZIER) easing.bx2 else null,
            if (easing.type == Easing.Type.CUBIC_BEZIER) easing.by2 else null),
        parameters = parameters.mapValues { it.value.toParamDto() },
        enabled = enabled)

    private fun TransitionInstanceDto.toInstance(): TransitionResult<TransitionInstance> {
        val alignment = try { TransitionAlignment.valueOf(alignment) }
        catch (_: Exception) {
            return TransitionResult.Err(TransitionError.Validation("Unknown alignment '$alignment'"))
        }
        val easing = when (easing.type) {
            "CUBIC_BEZIER" -> {
                val x1 = easing.x1 ?: return TransitionResult.Err(
                    TransitionError.Validation("CUBIC_BEZIER easing missing x1"))
                val y1 = easing.y1 ?: return TransitionResult.Err(
                    TransitionError.Validation("CUBIC_BEZIER easing missing y1"))
                val x2 = easing.x2 ?: return TransitionResult.Err(
                    TransitionError.Validation("CUBIC_BEZIER easing missing x2"))
                val y2 = easing.y2 ?: return TransitionResult.Err(
                    TransitionError.Validation("CUBIC_BEZIER easing missing y2"))
                try { Easing.bezier(x1, y1, x2, y2) } catch (e: IllegalArgumentException) {
                    return TransitionResult.Err(
                        TransitionError.Validation(e.message ?: "Invalid bezier control points"))
                }
            }
            else -> try { Easing.of(Easing.Type.valueOf(easing.type)) }
            catch (_: Exception) {
                return TransitionResult.Err(TransitionError.Validation("Unknown easing '${easing.type}'"))
            }
        }
        val warnings = mutableListOf<String>()
        val params = LinkedHashMap<String, ParamValue>()
        parameters.forEach { (key, dto) ->
            val v = dto.toParamValue()
            if (v != null) params[key] = v
            else warnings += "Parameter '$key' has unknown type '${dto.type}' and was reset to default."
        }
        return TransitionResult.Ok(
            TransitionInstance(instanceId, definitionId, outgoingClipId, incomingClipId,
                startMs, endMs, alignment, customAnchor ?: 0.5f, easing, params, enabled, schemaVersion),
            warnings)
    }

    private fun ParamValue.toParamDto(): ParamDto = when (this) {
        is ParamValue.FloatValue -> ParamDto("float", JsonPrimitive(value))
        is ParamValue.IntValue -> ParamDto("int", JsonPrimitive(value))
        is ParamValue.BoolValue -> ParamDto("bool", JsonPrimitive(value))
        is ParamValue.EnumValue -> ParamDto("enum", JsonPrimitive(index), name)
        is ParamValue.ColorValue -> ParamDto("color", JsonArray(rgba.map { JsonPrimitive(it) }))
        is ParamValue.Vec2Value -> ParamDto("vec2", JsonArray(values.map { JsonPrimitive(it) }))
        is ParamValue.Vec3Value -> ParamDto("vec3", JsonArray(values.map { JsonPrimitive(it) }))
        is ParamValue.AngleValue -> ParamDto("angle", JsonPrimitive(degrees))
        is ParamValue.NormalizedValue -> ParamDto("normalized", JsonPrimitive(value))
    }

    private fun ParamDto.toParamValue(): ParamValue? {
        val p = value as? JsonPrimitive
        val arr = value as? JsonArray
        return when (type) {
            "float" -> p?.content?.toFloatOrNull()?.let { ParamValue.FloatValue(it) }
            "int" -> p?.content?.toIntOrNull()?.let { ParamValue.IntValue(it) }
            "bool" -> p?.let { ParamValue.BoolValue(it.content == "true" || it.content == "1") }
            "enum" -> p?.content?.toIntOrNull()?.let { ParamValue.EnumValue(it, name ?: it.toString()) }
            "angle" -> p?.content?.toFloatOrNull()?.let { ParamValue.AngleValue(it) }
            "normalized" -> p?.content?.toFloatOrNull()?.let { ParamValue.NormalizedValue(it) }
            "color" -> arr?.mapNotNull { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
                ?.takeIf { it.size == 4 }?.let { ParamValue.ColorValue(it) }
            "vec2" -> arr?.mapNotNull { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
                ?.takeIf { it.size == 2 }?.let { ParamValue.Vec2Value(it) }
            "vec3" -> arr?.mapNotNull { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
                ?.takeIf { it.size == 3 }?.let { ParamValue.Vec3Value(it) }
            else -> null
        }
    }
}
