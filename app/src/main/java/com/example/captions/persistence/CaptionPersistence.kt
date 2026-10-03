package com.ahstudio.captions.persistence

import com.ahstudio.captions.core.errors.CaptionEngineException
import com.ahstudio.captions.core.model.CaptionProject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

object CaptionPersistence {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(project: CaptionProject, file: File) {
        val payload = runCatching { json.encodeToString(project) }
            .getOrElse { throw CaptionEngineException.ExportError("serialization failed: ${it.message}", it) }
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(temp).use { it.write(payload.toByteArray()) }
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) throw CaptionEngineException.ExportError("atomic rename failed")
        } catch (e: Exception) {
            temp.delete()
            throw CaptionEngineException.ExportError("save failed: ${e.message}", e)
        }
    }

    fun load(file: File): CaptionProject {
        if (!file.exists()) throw CaptionEngineException.ExportError("project file not found: ${file.absolutePath}")
        val payload = runCatching { file.readText() }
            .getOrElse { throw CaptionEngineException.ExportError("read failed: ${it.message}", it) }
        return runCatching { json.decodeFromString<CaptionProject>(payload) }
            .getOrElse { throw CaptionEngineException.ExportError("deserialization failed: ${it.message}", it) }
    }
}
