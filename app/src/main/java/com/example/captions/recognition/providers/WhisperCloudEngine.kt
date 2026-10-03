package com.ahstudio.captions.recognition.providers

import com.ahstudio.captions.core.errors.CaptionEngineException
import com.ahstudio.captions.core.language.LanguageTag
import com.ahstudio.captions.recognition.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WhisperConfig(
    val baseUrl: String,
    val apiKey: String?,
    val model: String = "whisper-1",
    val prompt: String? = null,
    val temperature: Float = 0f,
)

class WhisperCloudEngine(private val config: WhisperConfig?) : SpeechRecognitionEngine {

    override val id = "whisper-cloud"
    override val availability: StateFlow<ProviderAvailability> =
        MutableStateFlow(
            if (config?.baseUrl.isNullOrBlank()) ProviderAvailability.Unavailable("no endpoint configured")
            else ProviderAvailability.Available
        )

    override fun supportedLanguages(): Set<String> =
        setOf("en", "ur", "ar", "hi", "zh", "ja", "ko", "es", "fr", "de", "tr", "pt", "id")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(
        source: SpeechSource,
        options: RecognitionOptions,
        onProgress: (RecognitionProgress) -> Unit,
    ): SpeechRecognitionResult = withContext(Dispatchers.IO) {
        val cfg = config ?: throw CaptionEngineException.RecognitionUnavailable("whisper provider not configured")
        val pcm = source as? SpeechSource.Pcm ?: throw CaptionEngineException.RecognitionFailed("unsupported speech source")

        val wav = File.createTempFile("ahcap", ".wav").also { it.deleteOnExit() }
        val totalBytes: Long
        try {
            RandomAccessFile(wav, "rw").use { raf ->
                raf.seek(44)
                val resampler = com.ahstudio.captions.audio.LinearResampler(pcm.sampleRateHz, options.sampleRateHz)
                var written = 0L
                pcm.chunks.collect { chunk ->
                    currentCoroutineContext().ensureActiveCompatW()
                    val s = if (pcm.sampleRateHz == options.sampleRateHz) chunk.samples else resampler.process(chunk.samples)
                    val buf = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (v in s) buf.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                    val bytes = buf.array()
                    raf.write(bytes); written += bytes.size
                }
                raf.seek(0)
                raf.write(wavHeader(written.toInt(), options.sampleRateHz))
                totalBytes = written + 44
            }
        } catch (e: kotlinx.coroutines.CancellationException) { wav.delete(); throw e }
        catch (e: Exception) { wav.delete(); throw CaptionEngineException.RecognitionFailed("wav encode failed: ${e.message}", e) }

        if (totalBytes <= 44) { wav.delete(); return@withContext SpeechRecognitionResult(id, options.languageTag, emptyList()) }

        val boundary = "----AhCapBoundary" + System.currentTimeMillis()
        val payload = parseTranscription(upload(wav, cfg, options, boundary, totalBytes, onProgress), options.languageTag)
        wav.delete()
        SpeechRecognitionResult(providerId = id, languageTag = payload.second, utterances = payload.first)
    }

    private fun upload(
        wav: File, cfg: WhisperConfig, options: RecognitionOptions,
        boundary: String, totalBytes: Long, onProgress: (RecognitionProgress) -> Unit,
    ): String {
        val conn = URL(cfg.baseUrl.removeSuffix("/") + "/audio/transcriptions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            cfg.apiKey?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.setChunkedStreamingMode(64 * 1024)
            conn.connectTimeout = 15_000; conn.readTimeout = 300_000

            conn.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                }
                field("model", cfg.model)
                field("response_format", "verbose_json")
                field("timestamp_granularities[]", "segment")
                field("timestamp_granularities[]", "word")
                field("temperature", cfg.temperature.toString())
                if (!options.languageTag.equals("auto", true)) field("language", LanguageTag.baseOf(options.languageTag))
                cfg.prompt?.let { field("prompt", it) }
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())

                var sent = 0L
                wav.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); sent += n
                        onProgress(RecognitionProgress((sent.toFloat() / totalBytes).coerceIn(0f, 1f), 0))
                    }
                }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                throw CaptionEngineException.RecognitionFailed("whisper HTTP $code: ${err?.take(300)}")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally { conn.disconnect() }
    }

    internal fun parseTranscription(body: String, requestedLanguage: String): Pair<List<RecognizedUtterance>, String> {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw CaptionEngineException.RecognitionFailed("unparseable whisper response") }
        val language = obj["language"]?.jsonPrimitive?.content?.let { LanguageTag.baseOf(it) } ?: LanguageTag.baseOf(requestedLanguage)

        val globalWords = obj["words"]?.jsonArray?.map { el ->
            val w = el.jsonObject
            RecognizedWord(
                text = w["word"]!!.jsonPrimitive.content.trim(),
                startUs = (w["start"]!!.jsonPrimitive.content.toDouble() * 1_000_000).toLong().coerceAtLeast(0),
                endUs = (w["end"]!!.jsonPrimitive.content.toDouble() * 1_000_000).toLong(),
                confidence = 1f,
            )
        }
        val segments = obj["segments"]?.jsonArray

        val utterances = ArrayList<RecognizedUtterance>()
        if (segments != null && segments.isNotEmpty()) {
            for (segEl in segments) {
                val s = segEl.jsonObject
                val start = (s["start"]!!.jsonPrimitive.content.toDouble() * 1_000_000).toLong()
                val end = (s["end"]!!.jsonPrimitive.content.toDouble() * 1_000_000).toLong()
                val text = s["text"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (text.isEmpty()) continue
                val words = globalWords?.filter { w ->
                    val m = (w.startUs + w.endUs) / 2
                    m >= start && m <= end
                }
                utterances += RecognizedUtterance(text, start, end, words?.takeIf { it.isNotEmpty() })
            }
        } else if (globalWords != null && globalWords.isNotEmpty()) {
            var group = ArrayList<RecognizedWord>()
            for (w in globalWords) {
                if (group.isNotEmpty() && w.startUs - group.last().endUs > 600_000) {
                    utterances += utteranceFromWords(group); group = ArrayList()
                }
                group += w
            }
            if (group.isNotEmpty()) utterances += utteranceFromWords(group)
        }
        return utterances.filter { it.text.isNotBlank() } to language
    }

    private fun utteranceFromWords(words: List<RecognizedWord>) = RecognizedUtterance(
        text = words.joinToString(" ") { it.text },
        startUs = words.first().startUs,
        endUs = words.last().endUs,
        words = words,
    )

    private fun wavHeader(dataLen: Int, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + dataLen); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(sampleRate); bb.putInt(byteRate); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(dataLen)
        return bb.array()
    }

    private fun kotlin.coroutines.CoroutineContext.ensureActiveCompatW() {
        if (!isActive) throw kotlinx.coroutines.CancellationException("cancelled")
    }
}
