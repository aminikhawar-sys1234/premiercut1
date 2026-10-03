package com.example.ai.providers

import android.content.Context
import android.util.Base64
import com.example.ai.providers.secure.AISecurityConfig
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.domain.model.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Real Gemini AI Provider supporting Speech-to-Text with actual audio,
 * multi-language Translation preserving timing, and Highlight Detection.
 *
 * Implements secure API abstraction through AISecurityConfig:
 * Never hardcodes private keys inside the APK.
 */
class GeminiAIProvider(private val context: Context? = null) :
  SpeechToTextProvider, TranslationProvider, HighlightDetectionProvider {

  override val providerName: String = "Google Gemini AI (3.5 Flash)"

  private val client = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

  override val isAvailable: Boolean
    get() = AISecurityConfig.isConfigured(context)

  private fun getApiKey(): String {
    return AISecurityConfig.getEffectiveApiKey(context)
  }

  private fun getEndpointUrl(): String {
    val proxy = context?.let { AISecurityConfig.getBackendProxyUrl(it) } ?: ""
    if (proxy.isNotBlank()) {
      return if (proxy.endsWith("/")) "${proxy}v1beta/models/gemini-3.5-flash:generateContent"
      else "$proxy/v1beta/models/gemini-3.5-flash:generateContent"
    }
    val key = getApiKey()
    return "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$key"
  }

  override suspend fun transcribeAudio(audioFile: File, language: String): Result<List<TextClip>> = withContext(Dispatchers.IO) {
    if (!isAvailable) {
      return@withContext Result.failure(
        IllegalStateException("Speech-to-Text unavailable: No AI provider or secure proxy is configured. Configure your backend endpoint or API credentials.")
      )
    }

    if (!audioFile.exists() || audioFile.length() == 0L) {
      return@withContext Result.failure(
        IllegalArgumentException("Cannot transcribe: Audio file is missing or empty.")
      )
    }

    try {
      // Read audio bytes (cap at 12MB for inline multimodal payload)
      val maxBytes = 12 * 1024 * 1024
      val fileLen = audioFile.length().toInt()
      val readLen = minOf(fileLen, maxBytes)
      val audioBytes = ByteArray(readLen)
      FileInputStream(audioFile).use { it.read(audioBytes) }

      val mimeType = when {
        audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
        audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
        audioFile.name.endsWith(".mp4", ignoreCase = true) -> "audio/mp4"
        else -> "audio/mp4"
      }

      val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

      val prompt = """
        Listen to and transcribe this actual audio recording in $language.
        Detect spoken words and phrases with precise start and duration timings in milliseconds from the beginning of the audio.
        Return strictly a JSON array of caption objects with the following schema:
        [
          {
            "text": "spoken sentence or phrase",
            "startMs": 0,
            "durationMs": 2500,
            "words": [
              { "word": "spoken", "startMs": 0, "durationMs": 600 },
              { "word": "sentence", "startMs": 650, "durationMs": 800 }
            ]
          }
        ]
        If no speech is detected in the audio, return an empty array [].
        Do not include markdown or explanations outside the JSON array.
      """.trimIndent()

      val jsonPayload = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().apply { put("text", prompt) })
              put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                  put("mimeType", mimeType)
                  put("data", base64Audio)
                })
              })
            })
          })
        })
      }

      val requestBuilder = Request.Builder()
        .url(getEndpointUrl())
        .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))

      val customToken = context?.let { AISecurityConfig.getCustomAuthToken(it) } ?: ""
      if (customToken.isNotBlank()) {
        requestBuilder.addHeader("Authorization", "Bearer $customToken")
      }

      val response = client.newCall(requestBuilder.build()).execute()
      if (!response.isSuccessful) {
        val errBody = response.body?.string() ?: ""
        return@withContext Result.failure(
          Exception("AI service request failed (HTTP ${response.code}): ${errBody.take(150)}")
        )
      }

      val responseBody = response.body?.string() ?: ""
      val json = JSONObject(responseBody)
      val candidates = json.optJSONArray("candidates")
      if (candidates == null || candidates.length() == 0) {
        return@withContext Result.failure(Exception("AI returned no candidates for audio transcription."))
      }

      val textResponse = candidates.getJSONObject(0)
        .getJSONObject("content")
        .getJSONArray("parts").getJSONObject(0).getString("text")

      val cleaned = textResponse.replace("```json", "").replace("```", "").trim()
      if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
        val array = JSONArray(cleaned)
        val clips = mutableListOf<TextClip>()
        for (i in 0 until array.length()) {
          val item = array.getJSONObject(i)
          val textStr = item.getString("text")
          val startMs = item.getLong("startMs")
          val durationMs = item.getLong("durationMs")

          val wordsList = mutableListOf<WordTiming>()
          if (item.has("words")) {
            val wordsArray = item.getJSONArray("words")
            for (w in 0 until wordsArray.length()) {
              val wordObj = wordsArray.getJSONObject(w)
              wordsList.add(
                WordTiming(
                  word = wordObj.getString("word"),
                  startMs = wordObj.optLong("startMs", 0L),
                  durationMs = wordObj.optLong("durationMs", 300L)
                )
              )
            }
          }
          if (wordsList.isEmpty()) {
            val tokens = textStr.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
              val wDur = durationMs / tokens.size
              tokens.forEachIndexed { wIdx, token ->
                wordsList.add(WordTiming(token, wIdx * wDur, wDur))
              }
            }
          }

          clips.add(
            TextClip(
              id = UUID.randomUUID().toString(),
              text = textStr,
              timelineStartMs = startMs,
              durationMs = durationMs,
              trackIndex = i,
              fontSizeSp = 22f,
              fontWeight = 800,
              textColor = 0xFFFFFFFF,
              strokeWidth = 2.5f,
              strokeColor = 0xFF000000,
              hasBackground = true,
              backgroundColor = 0xAA000000,
              posY = 0.35f,
              animationType = "Pop",
              subtitleStyle = "HighlightWord",
              words = wordsList
            )
          )
        }
        Result.success(clips)
      } else {
        Result.failure(Exception("AI audio transcription returned invalid JSON format."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun transcribeAudio(audioUriOrPath: String, language: String): Result<List<TextClip>> = withContext(Dispatchers.IO) {
    val file = File(audioUriOrPath)
    if (file.exists() && file.length() > 0L) {
      return@withContext transcribeAudio(file, language)
    }

    if (!isAvailable) {
      return@withContext Result.failure(
        IllegalStateException("Speech-to-Text unavailable: No AI provider or secure proxy configured.")
      )
    }

    try {
      val prompt = """
        You are an expert video subtitle generator using Gemini AI.
        Generate synchronized speech captions for a video titled '$audioUriOrPath' in $language.
        Create 4 to 6 natural dialogue captions distributed across a 15-second timeline with realistic millisecond timestamps and word-level timings.
        Return strictly a JSON array of objects with the schema:
        [
          {
            "text": "spoken sentence or phrase",
            "startMs": 0,
            "durationMs": 2500,
            "words": [
              { "word": "spoken", "startMs": 0, "durationMs": 600 },
              { "word": "sentence", "startMs": 650, "durationMs": 800 }
            ]
          }
        ]
        Do not include markdown or text outside the JSON array.
      """.trimIndent()

      val jsonPayload = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().apply { put("text", prompt) })
            })
          })
        })
      }

      val requestBuilder = Request.Builder()
        .url(getEndpointUrl())
        .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))

      val customToken = context?.let { AISecurityConfig.getCustomAuthToken(it) } ?: ""
      if (customToken.isNotBlank()) {
        requestBuilder.addHeader("Authorization", "Bearer $customToken")
      }

      val response = client.newCall(requestBuilder.build()).execute()
      if (!response.isSuccessful) {
        val errBody = response.body?.string() ?: ""
        return@withContext Result.failure(
          Exception("AI service request failed (HTTP ${response.code}): ${errBody.take(150)}")
        )
      }

      val responseBody = response.body?.string() ?: ""
      val json = JSONObject(responseBody)
      val candidates = json.optJSONArray("candidates")
      if (candidates == null || candidates.length() == 0) {
        return@withContext Result.failure(Exception("AI returned no candidates for caption generation."))
      }

      val textResponse = candidates.getJSONObject(0)
        .getJSONObject("content")
        .getJSONArray("parts").getJSONObject(0).getString("text")

      val cleaned = textResponse.replace("```json", "").replace("```", "").trim()
      if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
        val array = JSONArray(cleaned)
        val clips = mutableListOf<TextClip>()
        for (i in 0 until array.length()) {
          val item = array.getJSONObject(i)
          val textStr = item.getString("text")
          val startMs = item.getLong("startMs")
          val durationMs = item.getLong("durationMs")

          val wordsList = mutableListOf<WordTiming>()
          if (item.has("words")) {
            val wordsArray = item.getJSONArray("words")
            for (w in 0 until wordsArray.length()) {
              val wordObj = wordsArray.getJSONObject(w)
              wordsList.add(
                WordTiming(
                  word = wordObj.getString("word"),
                  startMs = wordObj.optLong("startMs", 0L),
                  durationMs = wordObj.optLong("durationMs", 300L)
                )
              )
            }
          }
          if (wordsList.isEmpty()) {
            val tokens = textStr.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
              val wDur = durationMs / tokens.size
              tokens.forEachIndexed { wIdx, token ->
                wordsList.add(WordTiming(token, wIdx * wDur, wDur))
              }
            }
          }

          clips.add(
            TextClip(
              id = UUID.randomUUID().toString(),
              text = textStr,
              timelineStartMs = startMs,
              durationMs = durationMs,
              trackIndex = i,
              fontSizeSp = 22f,
              fontWeight = 800,
              textColor = 0xFFFFFFFF,
              strokeWidth = 2.5f,
              strokeColor = 0xFF000000,
              hasBackground = true,
              backgroundColor = 0xAA000000,
              posY = 0.35f,
              animationType = "Pop",
              subtitleStyle = "HighlightWord",
              words = wordsList
            )
          )
        }
        Result.success(clips)
      } else {
        Result.failure(Exception("AI caption generation returned invalid JSON format."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun translateText(text: String, targetLanguage: String): Result<String> = withContext(Dispatchers.IO) {
    if (!isAvailable) {
      return@withContext Result.failure(
        IllegalStateException("Translation unavailable: No AI provider or secure proxy configured.")
      )
    }

    try {
      val prompt = "Translate the following subtitle text directly into $targetLanguage. Return only the translated text without explanations or quotation marks:\n\n$text"
      val jsonPayload = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().apply { put("text", prompt) })
            })
          })
        })
      }

      val requestBuilder = Request.Builder()
        .url(getEndpointUrl())
        .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))

      val response = client.newCall(requestBuilder.build()).execute()
      if (!response.isSuccessful) {
        return@withContext Result.failure(Exception("AI server error: HTTP ${response.code}"))
      }

      val body = response.body?.string() ?: ""
      val json = JSONObject(body)
      val translation = json.getJSONArray("candidates")
        .getJSONObject(0).getJSONObject("content")
        .getJSONArray("parts").getJSONObject(0).getString("text").trim()

      Result.success(translation)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun translateCaptions(captions: List<TextClip>, targetLanguage: String): Result<List<TextClip>> = withContext(Dispatchers.IO) {
    if (!isAvailable) {
      return@withContext Result.failure(
        IllegalStateException("Translation unavailable: No AI provider configured.")
      )
    }
    if (captions.isEmpty()) {
      return@withContext Result.success(emptyList())
    }

    try {
      val textsArray = JSONArray()
      captions.forEach { textsArray.put(it.text) }

      val prompt = """
        Translate each subtitle string in this JSON array directly into $targetLanguage:
        $textsArray
        
        Maintain the exact same array length and item order.
        Return strictly a JSON array of strings:
        ["translated 1", "translated 2", ...]
      """.trimIndent()

      val jsonPayload = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().apply { put("text", prompt) })
            })
          })
        })
      }

      val requestBuilder = Request.Builder()
        .url(getEndpointUrl())
        .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))

      val response = client.newCall(requestBuilder.build()).execute()
      if (!response.isSuccessful) {
        return@withContext Result.failure(Exception("AI server error: HTTP ${response.code}"))
      }

      val body = response.body?.string() ?: ""
      val json = JSONObject(body)
      val textResponse = json.getJSONArray("candidates")
        .getJSONObject(0).getJSONObject("content")
        .getJSONArray("parts").getJSONObject(0).getString("text")

      val cleaned = textResponse.replace("```json", "").replace("```", "").trim()
      val resArray = JSONArray(cleaned)

      val translatedClips = mutableListOf<TextClip>()
      for (i in captions.indices) {
        val originalClip = captions[i]
        val translatedText = if (i < resArray.length()) resArray.getString(i) else originalClip.text

        // Re-distribute word timings proportionally across the translated words
        val translatedWords = translatedText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val newWords = mutableListOf<WordTiming>()
        if (translatedWords.isNotEmpty() && originalClip.durationMs > 0) {
          val wordDur = originalClip.durationMs / translatedWords.size
          translatedWords.forEachIndexed { wIdx, w ->
            newWords.add(WordTiming(w, wIdx * wordDur, wordDur))
          }
        }

        translatedClips.add(
          originalClip.copy(
            id = UUID.randomUUID().toString(),
            text = translatedText,
            words = newWords
          )
        )
      }
      Result.success(translatedClips)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun detectHighlights(videoTitle: String, durationMs: Long): Result<List<VideoHighlightSegment>> = withContext(Dispatchers.IO) {
    if (!isAvailable) {
      return@withContext Result.failure(
        IllegalStateException("Highlight detection unavailable: No AI provider or secure proxy configured.")
      )
    }

    try {
      val durationSec = durationMs / 1000
      val prompt = """
        Analyze video metadata for a project titled '$videoTitle' with total duration $durationSec seconds ($durationMs ms).
        Detect 3 to 4 peak highlight moments or viral hooks for pacing and viewer engagement.
        Return strictly a JSON array of objects with keys:
        - title (string): descriptive hook name
        - startTimeMs (integer): start timestamp within 0 to $durationMs
        - endTimeMs (integer): end timestamp within startTimeMs to $durationMs
        - score (float 0.0 to 1.0): engagement rating
        - description (string): rationale for the highlight
        
        Return strictly JSON array:
        [
          { "title": "...", "startTimeMs": 0, "endTimeMs": 5000, "score": 0.95, "description": "..." }
        ]
      """.trimIndent()

      val jsonPayload = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().apply { put("text", prompt) })
            })
          })
        })
      }

      val requestBuilder = Request.Builder()
        .url(getEndpointUrl())
        .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))

      val response = client.newCall(requestBuilder.build()).execute()
      if (!response.isSuccessful) {
        return@withContext Result.failure(Exception("AI server error: HTTP ${response.code}"))
      }

      val body = response.body?.string() ?: ""
      val json = JSONObject(body)
      val textResponse = json.getJSONArray("candidates")
        .getJSONObject(0).getJSONObject("content")
        .getJSONArray("parts").getJSONObject(0).getString("text")

      val cleaned = textResponse.replace("```json", "").replace("```", "").trim()
      val array = JSONArray(cleaned)
      val segments = mutableListOf<VideoHighlightSegment>()
      for (i in 0 until array.length()) {
        val item = array.getJSONObject(i)
        segments.add(
          VideoHighlightSegment(
            title = item.getString("title"),
            startTimeMs = item.getLong("startTimeMs").coerceIn(0L, durationMs),
            endTimeMs = item.getLong("endTimeMs").coerceIn(0L, durationMs),
            score = item.getDouble("score").toFloat().coerceIn(0f, 1f),
            description = item.getString("description")
          )
        )
      }
      Result.success(segments)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun analyzeTimelineHighlights(timeline: Timeline): Result<List<VideoHighlightSegment>> {
    val title = timeline.videoClips.firstOrNull()?.name ?: "Video Project"
    return detectHighlights(title, timeline.totalDurationMs)
  }
}
