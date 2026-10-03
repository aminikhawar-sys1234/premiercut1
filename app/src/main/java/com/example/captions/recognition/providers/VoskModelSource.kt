package com.ahstudio.captions.recognition.providers

import android.content.Context
import com.ahstudio.captions.core.language.LanguageTag
import com.ahstudio.captions.recognition.ProviderAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

interface VoskModelSource {
    val status: StateFlow<ProviderAvailability>
    fun availableLanguages(): Set<String>
    suspend fun obtainModelDir(languageTag: String): File
}

class AssetVoskModelSource(
    private val context: Context,
    private val assetDir: String = "vosk",
    private val models: Map<String, String>,
) : VoskModelSource {

    override val status = MutableStateFlow<ProviderAvailability>(ProviderAvailability.Available)

    override fun availableLanguages(): Set<String> =
        models.keys.filterTo(mutableSetOf()) { tag ->
            try { context.assets.list(assetDir)?.contains(models[tag]) == true } catch (_: Exception) { false }
        }

    override suspend fun obtainModelDir(languageTag: String): File = withContext(Dispatchers.IO) {
        val zipName = models[languageTag] ?: models.entries
            .firstOrNull { LanguageTag.baseOf(it.key) == LanguageTag.baseOf(languageTag) }?.value
            ?: throw IllegalStateException("no model asset for '$languageTag'")
        val dest = File(context.filesDir, "vosk-models/${LanguageTag.baseOf(languageTag)}")
        val marker = File(dest, ".unpacked-ok")
        if (marker.exists() && findModelRoot(dest) != null) return@withContext findModelRoot(dest)!!
        status.value = ProviderAvailability.Downloading(0f)
        dest.deleteRecursively(); dest.mkdirs()
        context.assets.open("$assetDir/$zipName").use { input ->
            ZipInputStream(input).use { zip ->
                val canonicalDest = dest.canonicalPath
                var entryCount = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val out = File(dest, entry.name)
                    if (!out.canonicalPath.startsWith(canonicalDest)) continue
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                    if (++entryCount % 200 == 0) status.value = ProviderAvailability.Downloading(-1f)
                    zip.closeEntry()
                }
            }
        }
        val root = findModelRoot(dest) ?: throw IllegalStateException("model zip '$zipName' missing conf/ directory")
        marker.createNewFile()
        status.value = ProviderAvailability.Available
        root
    }

    private fun findModelRoot(dir: File): File? {
        if (File(dir, "conf").isDirectory) return dir
        return dir.listFiles()?.firstOrNull { it.isDirectory && File(it, "conf").isDirectory }
    }
}
