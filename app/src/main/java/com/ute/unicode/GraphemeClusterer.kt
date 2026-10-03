package com.ute.unicode

/**
 * UAX#29-simplified grapheme clusters: base + combining marks + ZWJ sequences +
 * regional indicator pairing. This is what "one character" means for animation
 * (character-by-character motion) and cluster-level font fallback — it correctly
 * keeps Devanagari matra+consonant units and emoji ZWJ sequences together.
 */
object GraphemeClusterer {

    fun clusterStarts(text: String): IntArray {
        val starts = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            starts.add(i)
            i = extendCluster(text, i)
        }
        return starts.toIntArray()
    }

    fun clusterCount(text: String): Int = clusterStarts(text).size

    private fun extendCluster(text: String, start: Int): Int {
        var i = start
        val cp = text.codePointAt(i)
        i += Character.charCount(cp)

        // Regional indicator pairs (flags) stay together.
        if (cp in 0x1F1E6..0x1F1FF && i < text.length) {
            val next = text.codePointAt(i)
            if (next in 0x1F1E6..0x1F1FF) i += Character.charCount(next)
            return i
        }

        while (i < text.length) {
            val next = text.codePointAt(i)
            // ZWJ sequence: base ZWJ base (emoji families, skin tones)
            if (next == 0x200D && i + Character.charCount(next) < text.length) {
                i += Character.charCount(next)
                i += Character.charCount(text.codePointAt(i))
                continue
            }
            // Combining marks attach to the base (covers matras, vowel signs, diacritics)
            if (Script.isCombiningMark(next)) { i += Character.charCount(next); continue }
            // Variation selectors & skin-tone modifiers
            if (next in 0xFE00..0xFE0F || next in 0x1F3FB..0x1F3FF) { i += Character.charCount(next); continue }
            break
        }
        return i
    }
}
