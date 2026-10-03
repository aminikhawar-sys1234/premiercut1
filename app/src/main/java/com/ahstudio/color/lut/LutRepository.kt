package com.ahstudio.color.lut

import java.io.File
import java.io.InputStream
import java.util.UUID

enum class LutFailureReason { MISSING_FILE, BAD_EXTENSION, TOO_LARGE, CORRUPT, UNSUPPORTED, IO_ERROR }
sealed class LutImportResult {
    data class Success(val lutId: String, val lut: Lut) : LutImportResult()
    data class Failure(val reason: LutFailureReason, val detail: String? = null) : LutImportResult()
}

interface LutRepository {
    suspend fun load(lutId: String): Lut?
    fun import(source: InputStream, originalName: String): LutImportResult
    fun remove(lutId: String)
    fun listIds(): List<String>
}

/**
 * §55: validates extension, size, format, data, domain. All failures return typed
 * results — an invalid LUT can NEVER crash the app.
 */
class DefaultLutRepository(
    private val dir: File,
    private val cache: LutCache = LutCache(12),
    private val maxFileBytes: Long = 64L * 1024 * 1024
) : LutRepository {
    init { dir.mkdirs() }

    override suspend fun load(lutId: String): Lut? {
        cache.get(lutId)?.let { return it }
        val f = fileFor(lutId) ?: return null
        if (!f.exists()) return null
        return try {
            f.inputStream().use { CubeParser.parse(it, f.nameWithoutExtension) }
                .also { cache.put(lutId, it) }
        } catch (t: Throwable) { null }
    }

    override fun import(source: InputStream, originalName: String): LutImportResult {
        val name = originalName.trim()
        if (name.isEmpty()) return LutImportResult.Failure(LutFailureReason.MISSING_FILE)
        if (!name.endsWith(".cube", ignoreCase = true) && !looksLikeCube(source))
            return LutImportResult.Failure(LutFailureReason.BAD_EXTENSION, name)
        try {
            val bytes = source.use { it.readBytes() }
            if (bytes.isEmpty()) return LutImportResult.Failure(LutFailureReason.MISSING_FILE)
            if (bytes.size > maxFileBytes)
                return LutImportResult.Failure(LutFailureReason.TOO_LARGE, "${bytes.size} bytes")
            val lut = bytes.inputStream().use { CubeParser.parse(it, name.removeSuffix(".cube")) }
            val id = UUID.randomUUID().toString().take(8) + "-" +
                     name.removeSuffix(".cube").replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
            fileFor(id)!!.writeBytes(bytes)
            cache.put(id, lut)
            return LutImportResult.Success(id, lut)
        } catch (e: LutParseException) {
            return LutImportResult.Failure(LutFailureReason.CORRUPT, e.message)
        } catch (e: java.io.IOException) {
            return LutImportResult.Failure(LutFailureReason.IO_ERROR, e.message)
        } catch (e: Throwable) {
            return LutImportResult.Failure(LutFailureReason.UNSUPPORTED, e.message)
        }
    }

    override fun remove(lutId: String) { fileFor(lutId)?.delete(); cache.clear() }
    override fun listIds(): List<String> =
        dir.listFiles { f -> f.extension == "cube" }?.map { it.nameWithoutExtension } ?: emptyList()

    private fun looksLikeCube(s: InputStream): Boolean = try {
        s.use { st ->
            val head = ByteArray(64); val n = st.read(head)
            String(head, 0, maxOf(0, n)).uppercase().contains("LUT_")
        }
    } catch (t: Throwable) { false }

    private fun fileFor(id: String) =
        id.filter { it.isLetterOrDigit() || it in "-_" }.take(64)
            .takeIf { it.isNotEmpty() }?.let { File(dir, "$it.cube") }
}
