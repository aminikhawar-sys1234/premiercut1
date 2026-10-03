package com.ahstudio.captions.core.language

import java.text.Bidi

enum class TextDirection { LTR, RTL }

object BidiTextEngine {
    fun baseDirection(text: String): TextDirection {
        if (text.isEmpty()) return TextDirection.LTR
        val bidi = Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        return if (bidi.isRightToLeft) TextDirection.RTL else TextDirection.LTR
    }
}

object TextMetrics {
    fun visualLength(text: String): Double {
        var len = 0.0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            len += if (isCjk(cp)) 2.0 else 1.0
            i += Character.charCount(cp)
        }
        return len
    }

    fun isCjk(cp: Int): Boolean =
        cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2A6DF ||
        cp in 0x3040..0x309F || cp in 0x30A0..0x30FF || cp in 0xAC00..0xD7AF

    inline fun forEachCodePoint(text: String, action: (Int) -> Unit) {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            action(cp)
            i += Character.charCount(cp)
        }
    }

    fun joinWordsText(words: List<String>): String {
        val sb = StringBuilder()
        for (i in words.indices) {
            val w = words[i]
            if (i > 0) {
                val prev = words[i - 1]
                val prevCjk = isCjk(prev.lastOrNull()?.code ?: 0)
                val currCjk = isCjk(w.firstOrNull()?.code ?: 0)
                if (!prevCjk && !currCjk) sb.append(' ')
            }
            sb.append(w)
        }
        return sb.toString()
    }
}
