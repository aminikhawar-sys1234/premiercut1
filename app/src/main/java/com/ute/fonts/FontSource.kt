package com.ute.fonts

/** Where a font came from. Remote fonts stream through the same interface. */
sealed class FontSource {
    data class Bundled(val assetPath: String) : FontSource()
    data class Application(val resId: Int) : FontSource()
    data class UserImported(val filePath: String) : FontSource()
    data class System(val familyName: String) : FontSource()
    data class Remote(val url: String) : FontSource()   // downloaded to cache dir, then UserImported
}

data class FontDescriptor(
    val family: String,
    val weight: Int,
    val italic: Boolean,
    val supportedScripts: Set<com.ute.unicode.Script> = emptySet(),
    val isVariable: Boolean = false,
)
