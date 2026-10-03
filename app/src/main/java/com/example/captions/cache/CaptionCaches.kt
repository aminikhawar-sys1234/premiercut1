package com.ahstudio.captions.cache

import com.ahstudio.captions.recognition.SpeechRecognitionResult
import java.io.File
import java.security.MessageDigest

class RecognitionCache(private val cacheDir: File) {
    init { cacheDir.mkdirs() }

    private fun key(uri: android.net.Uri, languageTag: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(uri.toString().toByteArray())
        md.update(languageTag.toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) } + ".json"
    }

    fun get(uri: android.net.Uri, languageTag: String): SpeechRecognitionResult? {
        val f = File(cacheDir, key(uri, languageTag))
        if (!f.exists()) return null
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString<SpeechRecognitionResult>(f.readText())
        }.getOrNull()
    }

    fun put(uri: android.net.Uri, languageTag: String, result: SpeechRecognitionResult) {
        val f = File(cacheDir, key(uri, languageTag))
        runCatching { f.writeText(kotlinx.serialization.json.Json.encodeToString(SpeechRecognitionResult.serializer(), result)) }
    }
}
