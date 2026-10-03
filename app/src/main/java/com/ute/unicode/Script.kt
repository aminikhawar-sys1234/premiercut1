package com.ute.unicode

/**
 * First-class scripts. The table is data — adding a script means adding a range
 * entry plus a fallback chain entry, never touching engine logic.
 */
enum class Script(val rtl: Boolean) {
    LATIN(false), CYRILLIC(false), GREEK(false),
    ARABIC(true),
    DEVANAGARI(false),
    HAN(false),
    COMMON(false),   // digits, punctuation, space — inherits from context
    UNKNOWN(false);

    companion object {
        // [start, end] inclusive ranges, per script.
        private val RANGES: List<Triple<Int, Int, Script>> = listOf(
            Triple(0x0041, 0x005A, LATIN), Triple(0x0061, 0x007A, LATIN),
            Triple(0x00C0, 0x024F, LATIN), Triple(0x1E00, 0x1EFF, LATIN),
            Triple(0x0370, 0x03FF, GREEK),
            Triple(0x0400, 0x04FF, CYRILLIC),
            Triple(0x0600, 0x06FF, ARABIC), Triple(0x0750, 0x077F, ARABIC),
            Triple(0x08A0, 0x08FF, ARABIC), Triple(0xFB50, 0xFDFF, ARABIC),
            Triple(0xFE70, 0xFEFF, ARABIC),
            Triple(0x0900, 0x097F, DEVANAGARI), Triple(0xA8E0, 0xA8FF, DEVANAGARI),
            Triple(0x1CD0, 0x1CFF, DEVANAGARI),
            Triple(0x3400, 0x4DBF, HAN), Triple(0x4E00, 0x9FFF, HAN),
            Triple(0xF900, 0xFAFF, HAN),
        )

        fun of(codePoint: Int): Script {
            for ((s, e, sc) in RANGES) if (codePoint in s..e) return sc
            return COMMON // digits, punctuation, whitespace, symbols inherit contextually
        }

        fun isCombiningMark(cp: Int): Boolean {
            val type = Character.getType(cp)
            return type == Character.NON_SPACING_MARK.toInt() ||
                   type == Character.ENCLOSING_MARK.toInt() ||
                   type == Character.COMBINING_SPACING_MARK.toInt()
        }
    }
}

data class ScriptRun(val start: Int, val end: Int, val script: Script)

object ScriptDetector {

    /**
     * Segment text into script runs. Common-script characters (spaces, digits,
     * punctuation) attach to the preceding strong script, falling back to the
     * next strong script — the standard heuristic used by real shapers.
     */
    fun segment(text: String): List<ScriptRun> {
        if (text.isEmpty()) return emptyList()
        val scripts = IntArray(text.length) { -1 }
        var lastStrong = -1
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            val sc = Script.of(cp)
            when {
                sc != Script.COMMON -> { for (j in i until i + len) scripts[j] = sc.ordinal; lastStrong = sc.ordinal }
                scripts[i] == -1 && lastStrong >= 0 -> { for (j in i until i + len) scripts[j] = lastStrong }
            }
            i += len
        }
        // Backward pass: leading/common characters with no preceding strong script
        // inherit the next strong script.
        var nextStrong = -1
        for (j in scripts.indices.reversed()) {
            val sc = Script.entries[scripts[j].coerceAtLeast(0)]
            if (scripts[j] >= 0 && sc != Script.COMMON && sc != Script.UNKNOWN) nextStrong = scripts[j]
            if (scripts[j] == -1 && nextStrong >= 0) scripts[j] = nextStrong
        }
        // Merge consecutive equal scripts.
        val runs = ArrayList<ScriptRun>()
        var runStart = 0
        for (j in 1..scripts.size) {
            if (j == scripts.size || scripts[j] != scripts[runStart]) {
                runs.add(ScriptRun(runStart, j, Script.entries[scripts[runStart].coerceAtLeast(0)]))
                runStart = j
            }
        }
        return runs
    }

    fun detect(text: String): Script =
        segment(text).firstOrNull { it.script != Script.COMMON }?.script ?: Script.LATIN

    fun isRtlScript(s: Script) = s.rtl
}
