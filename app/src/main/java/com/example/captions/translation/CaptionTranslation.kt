package com.ahstudio.captions.translation

import com.ahstudio.captions.core.model.CaptionClip
import com.ahstudio.captions.core.model.CaptionTrack
import com.ahstudio.captions.core.model.ClipSource
import com.ahstudio.captions.core.model.TrackKind
import com.ahstudio.captions.segmentation.SegmentationOptions
import com.ahstudio.captions.timing.WordTimestampEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

interface CaptionTranslationEngine {
    val providerId: String
    suspend fun translate(texts: List<String>, sourceLanguageTag: String, targetLanguageTag: String): List<String>
}

class LibreTranslateProvider(private val endpoint: String, private val apiKey: String? = null) : CaptionTranslationEngine {
    override val providerId = "libretranslate"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(texts: List<String>, sourceLanguageTag: String, targetLanguageTag: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("q", kotlinx.serialization.json.JsonArray(texts.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            put("source", sourceLanguageTag.substringBefore('-'))
            put("target", targetLanguageTag.substringBefore('-'))
            put("format", "text")
            apiKey?.let { put("api_key", it) }
        }.toString()
        val payload = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val conn = URL(endpoint.removeSuffix("/") + "/translate").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 10_000; conn.readTimeout = 30_000
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299)
                    throw com.ahstudio.captions.core.errors.CaptionEngineException.ExportError("translation endpoint HTTP $code")
                conn.inputStream.bufferedReader().readText()
            } finally { conn.disconnect() }
        }
        val arr = json.parseToJsonElement(payload).jsonObject["translatedText"]?.jsonArray
            ?: throw com.ahstudio.captions.core.errors.CaptionEngineException.ExportError("unexpected translation response")
        return arr.map { it.jsonPrimitive.content }
    }
}

object TranslationTrackBuilder {
    suspend fun build(
        original: CaptionTrack,
        engine: CaptionTranslationEngine,
        targetLanguageTag: String,
        newTrackId: String,
        segOptions: SegmentationOptions = SegmentationOptions(),
    ): CaptionTrack {
        val texts = original.clips.map { it.displayText }
        val translated = engine.translate(texts, original.languageTag, targetLanguageTag)
        val clips = original.clips.mapIndexedNotNull { i, clip ->
            val text = translated.getOrNull(i) ?: return@mapIndexedNotNull null
            val words = WordTimestampEngine.estimateText(text, clip.timing.start, clip.timing.end)
            CaptionClip(
                id = UUID.randomUUID().toString(),
                trackId = newTrackId,
                words = words,
                lineBreakIndices = com.ahstudio.captions.segmentation.LineBreakingEngine
                    .breakIndices(words, segOptions.maxCharsPerLine, segOptions.maxLines),
                timing = clip.timing,
                source = ClipSource.TRANSLATED,
            )
        }
        return CaptionTrack(
            id = newTrackId,
            name = "Translation ($targetLanguageTag)",
            kind = TrackKind.TRANSLATION,
            languageTag = targetLanguageTag,
            clips = clips,
            translationOfTrackId = original.id,
        )
    }
}
