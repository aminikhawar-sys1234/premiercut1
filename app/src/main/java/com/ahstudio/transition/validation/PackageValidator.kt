package com.ahstudio.transition.validation

import com.ahstudio.transition.core.AlphaMode
import com.ahstudio.transition.core.ParamType
import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.ShaderSource
import com.ahstudio.transition.core.TransitionDefinition
import com.ahstudio.transition.core.TransitionEngineMetadata as Meta
import com.ahstudio.transition.core.TransitionError
import com.ahstudio.transition.core.TransitionFamily
import com.ahstudio.transition.core.TransitionParameterDefinition
import com.ahstudio.transition.core.TransitionRenderGraphSpec
import com.ahstudio.transition.core.TransitionResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

@Serializable
data class TransitionPackageManifest(
    val manifestVersion: Int,
    val packageId: String,
    val minEngineVersion: Int = 1,
    val transitions: List<Entry>,
) {
    @Serializable data class Entry(
        val id: String,
        val name: String,
        val family: String,
        val version: Int,
        val entryShader: String = "main",
        val defaultDurationMs: Long = 800,
        val alphaMode: String = "OPAQUE",
        val shaders: List<ShaderFile>,
        val parameters: List<ParamDef> = emptyList(),
    )
    @Serializable data class ShaderFile(
        val id: String,
        val file: String,
        val sha256: String? = null,
        val maxBytes: Int = Meta.DEFAULT_SHADER_SIZE_CAP_BYTES,
    )
    @Serializable data class ParamDef(
        val id: String,
        val displayName: String,
        val type: String,
        val default: JsonElement,
        val min: JsonElement? = null,
        val max: JsonElement? = null,
        val animatable: Boolean = false,
        val enumValues: List<String> = emptyList(),
    )
}

/** Pluggable file access so the validator is JVM-testable (assets / files / future downloads). */
interface PackageFileSource {
    fun exists(path: String): Boolean
    fun readBytes(path: String): ByteArray?
}

object PackageValidator {
    private val json = Json { ignoreUnknownKeys = true }

    fun validateAndLoad(manifestBytes: ByteArray, files: PackageFileSource):
            TransitionResult<List<TransitionDefinition>> {
        val manifest = try {
            val manifestText = String(manifestBytes, Charsets.UTF_8)
            json.decodeFromString(TransitionPackageManifest.serializer(), manifestText)
        } catch (e: Exception) {
            return TransitionResult.Err(TransitionError.Package("Manifest parse failed: ${e.message}"))
        }
        if (manifest.manifestVersion != Meta.SUPPORTED_MANIFEST_VERSION)
            return TransitionResult.Err(
                TransitionError.Package("Unsupported manifestVersion ${manifest.manifestVersion}"))
        if (manifest.minEngineVersion > Meta.ENGINE_VERSION)
            return TransitionResult.Err(
                TransitionError.Package("Package needs engine >= ${manifest.minEngineVersion}"))

        val defs = ArrayList<TransitionDefinition>(manifest.transitions.size)
        for (entry in manifest.transitions) {
            when (val r = loadEntry(manifest.packageId, manifest.minEngineVersion, entry, files)) {
                is TransitionResult.Ok -> defs.add(r.value)
                is TransitionResult.Err -> return r
            }
        }
        return TransitionResult.Ok(defs)
    }

    private fun loadEntry(packageId: String, packageMinEngine: Int,
                          e: TransitionPackageManifest.Entry, files: PackageFileSource):
            TransitionResult<TransitionDefinition> {
        if (!e.id.startsWith("$packageId.")) return TransitionResult.Err(
            TransitionError.Package("Transition id '${e.id}' must be namespaced under '$packageId.'"))
        if (e.defaultDurationMs !in 1..Meta.MAX_DURATION_MS) return TransitionResult.Err(
            TransitionError.Package("defaultDurationMs ${e.defaultDurationMs} out of range in '${e.id}'"))

        val shaders = HashMap<String, ShaderSource>()
        for (sf in e.shaders) {
            if (!files.exists(sf.file)) return TransitionResult.Err(
                TransitionError.Package("Missing shader file '${sf.file}'"))
            val bytes = files.readBytes(sf.file)!!
            val cap = minOf(sf.maxBytes, Meta.HARD_SHADER_SIZE_CAP_BYTES)
            if (bytes.size > cap) return TransitionResult.Err(
                TransitionError.Package("Shader '${sf.file}' too large (${bytes.size} > $cap)"))
            if (sf.sha256 != null && sha256(bytes) != sf.sha256) return TransitionResult.Err(
                TransitionError.Package("Checksum mismatch for '${sf.file}'"))
            shaders[sf.id] = ShaderSource(sf.id, bytes.toString(Charsets.UTF_8), maxSourceBytes = cap)
        }
        if (e.entryShader !in shaders) return TransitionResult.Err(
            TransitionError.Package("entryShader '${e.entryShader}' missing in '${e.id}'"))

        val params = ArrayList<TransitionParameterDefinition>(e.parameters.size)
        for (p in e.parameters) {
            val type = try { ParamType.valueOf(p.type) } catch (_: Exception) {
                return TransitionResult.Err(TransitionError.Package(
                    "Unknown param type '${p.type}' in '${e.id}'"))
            }
            if (!TransitionParameterDefinition.REGEX.matches(p.id)) return TransitionResult.Err(
                TransitionError.Package("Invalid param id '${p.id}'"))
            val default = paramFromJson(type, p.default, p.enumValues) ?: return TransitionResult.Err(
                TransitionError.Package("Bad default for param '${p.id}'"))
            params.add(TransitionParameterDefinition(
                p.id, p.displayName, type, default,
                p.min?.let { paramFromJson(type, it, p.enumValues) },
                p.max?.let { paramFromJson(type, it, p.enumValues) },
                animatable = p.animatable, enumValues = p.enumValues))
        }

        val family = try { TransitionFamily.valueOf(e.family) } catch (_: Exception) { TransitionFamily.OTHER }
        val alpha = try { AlphaMode.valueOf(e.alphaMode) } catch (_: Exception) { AlphaMode.OPAQUE }

        val definition = TransitionDefinition(
            id = e.id, name = e.name, family = family, version = e.version,
            minEngineVersion = packageMinEngine,
            parameters = params, shaders = shaders,
            graph = TransitionRenderGraphSpec.singlePass(e.entryShader),
            defaultDurationMs = e.defaultDurationMs, alphaMode = alpha)

        val validation = ShaderValidator.validateDefinition(definition)
        if (!validation.isValid) return TransitionResult.Err(
            TransitionError.Package("Invalid shader in '${e.id}': ${validation.errors.first()}"))
        return TransitionResult.Ok(definition)
    }

    private fun floatList(el: JsonElement, count: Int): List<Float>? =
        (el as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
            ?.takeIf { it.size == count }

    private fun paramFromJson(type: ParamType, el: JsonElement, enums: List<String>): ParamValue? {
        val p = el as? JsonPrimitive
        return when (type) {
            ParamType.FLOAT -> p?.content?.toFloatOrNull()?.let { ParamValue.FloatValue(it) }
            ParamType.NORMALIZED -> p?.content?.toFloatOrNull()?.let { ParamValue.NormalizedValue(it) }
            ParamType.ANGLE -> p?.content?.toFloatOrNull()?.let { ParamValue.AngleValue(it) }
            ParamType.INT -> p?.content?.toIntOrNull()?.let { ParamValue.IntValue(it) }
            ParamType.BOOL -> p?.let { ParamValue.BoolValue(it.content == "true" || it.content == "1") }
            ParamType.ENUM -> p?.content?.toIntOrNull()?.takeIf { it in enums.indices }
                ?.let { ParamValue.EnumValue(it, enums[it]) }
            ParamType.COLOR -> floatList(el, 4)?.let { ParamValue.ColorValue(it) }
            ParamType.VEC2 -> floatList(el, 2)?.let { ParamValue.Vec2Value(it) }
            ParamType.VEC3 -> floatList(el, 3)?.let { ParamValue.Vec3Value(it) }
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
