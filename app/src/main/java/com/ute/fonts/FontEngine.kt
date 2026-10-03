package com.ute.fonts

import android.content.Context
import android.graphics.Typeface
import com.ute.core.Safe
import com.ute.core.TextEngineException
import com.ute.unicode.Script
import java.io.File
import java.net.URL

/**
 * Font registration, discovery, and script-aware fallback. The fallback chain is:
 * primary (layer style) → user chain → script-specific system chain → system default.
 * The final entry is always the platform default font, which on Android resolves
 * through the system font stack (including Noto scripts and color emoji), so a
 * missing glyph NEVER crashes rendering.
 */
class FontEngine(private val appContext: Context) {

    private val registered = LinkedHashMap<Int, FontHandle>()
    private var nextId = 1
    private val cacheDir: File = File(appContext.cacheDir, "ute_fonts").apply { mkdirs() }

    /** Ordered script → system family names (Android ships Noto for all of these). */
    private val scriptFallbacks: Map<Script, List<String>> = mapOf(
        Script.LATIN to listOf("sans-serif"),
        Script.CYRILLIC to listOf("sans-serif"),
        Script.GREEK to listOf("sans-serif"),
        Script.ARABIC to listOf("sans-serif", "Noto Naskh Arabic", "Noto Sans Arabic"),
        Script.DEVANAGARI to listOf("sans-serif", "Noto Sans Devanagari", "Noto Serif Devanagari"),
        Script.HAN to listOf("sans-serif", "Noto Sans CJK SC", "Noto Sans CJK TC"),
    )

    // ---------- registration / discovery ----------

    fun register(source: FontSource): FontHandle = Safe.critical("registerFont", systemDefault()) {
        val tf: Typeface = when (source) {
            is FontSource.Bundled -> Typeface.createFromAsset(appContext.assets, source.assetPath)
            is FontSource.Application -> runCatching {
                Typeface.createFromAsset(appContext.assets, "fonts/${source.resId}")
            }.getOrElse { androidx.core.content.res.ResourcesCompat.getFont(appContext, source.resId) ?: Typeface.DEFAULT }
            is FontSource.UserImported -> Typeface.createFromFile(source.filePath)
            is FontSource.System -> Typeface.create(source.familyName, Typeface.NORMAL)
            is FontSource.Remote -> {
                val file = File(cacheDir, source.url.hashCode().toString() + ".ttf")
                if (!file.exists()) {
                    URL(source.url).openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                Typeface.createFromFile(file.absolutePath)
            }
        }
        val handle = FontHandle(nextId++, tf, describe(tf, source), source)
        registered[handle.id] = handle
        handle
    }

    fun unregister(id: Int) { registered.remove(id) }

    fun allFonts(): List<FontHandle> = registered.values.toList()

    fun find(family: String, weight: Int = 400, italic: Boolean = false): FontHandle? =
        registered.values.filter { it.descriptor.family.equals(family, ignoreCase = true) }
            .minByOrNull { kotlin.math.abs(it.descriptor.weight - weight) * 10 + (if (it.descriptor.italic != italic) 5 else 0) }

    fun systemDefault(): FontHandle = SystemDefaultHandle

    fun downloadAndRegister(url: String): FontHandle = register(FontSource.Remote(url))

    /** Language-aware fallback chain, de-duplicated, system default always last. */
    fun fallbackChain(primary: FontHandle?, script: Script, language: String): List<FontHandle> {
        val chain = ArrayList<FontHandle>()
        primary?.let { chain.add(it) }
        // Font families registered by the app that explicitly support the script
        registered.values
            .filter { it.descriptor.supportedScripts.contains(script) && it != primary }
            .sortedByDescending { it.descriptor.weight }
            .take(2)
            .forEach { if (it !in chain) chain.add(it) }
        scriptFallbacks[script]?.forEach { family ->
            find(family)?.let { if (it !in chain) chain.add(it) }
        }
        if (SystemDefaultHandle !in chain) chain.add(SystemDefaultHandle)
        return chain
    }

    private fun describe(tf: Typeface, source: FontSource): FontDescriptor {
        val family = when (source) {
            is FontSource.System -> source.familyName
            is FontSource.Bundled -> File(source.assetPath).nameWithoutExtension
            is FontSource.UserImported -> File(source.filePath).nameWithoutExtension
            is FontSource.Remote -> "remote-${source.url.hashCode()}"
            is FontSource.Application -> "res-${source.resId}"
        }
        val weight = if (android.os.Build.VERSION.SDK_INT >= 28) tf.weight else 400
        val italic = if (android.os.Build.VERSION.SDK_INT >= 28) tf.isItalic else false
        return FontDescriptor(family, weight, italic)
    }

    private object SystemDefaultHolder {
        val INSTANCE = FontHandle(0, Typeface.create("sans-serif", Typeface.NORMAL),
            FontDescriptor("system-default", 400, false), FontSource.System("sans-serif"))
    }
    val SystemDefaultHandle: FontHandle get() = systemDefaultInternal()

    private fun systemDefaultInternal() = FontEngineSystemDefault(this)

    class FontEngineSystemDefault internal constructor(owner: FontEngine) :
        FontHandle(0, Typeface.create("sans-serif", Typeface.NORMAL),
            FontDescriptor("system-default", 400, false), FontSource.System("sans-serif")) {
        override fun canRender(codePoint: Int) = true
    }
}
