package com.ute.layout

import com.ute.unicode.Script

/**
 * UAX#14-simplified break opportunities:
 *  - after whitespace (whitespace collapses at wrap)
 *  - between Han ideographs (CJK can break anywhere)
 *  - never inside a grapheme cluster (clusters are pre-merged by the caller)
 */
object LineBreaker {

    fun opportunities(text: String, start: Int, end: Int): BooleanArray {
        val ok = BooleanArray(end - start)
        var i = start
        while (i < end) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            val nextIdx = i + len
            if (nextIdx < end) {
                val next = text.codePointAt(nextIdx)
                ok[i - start] =
                    (Character.isWhitespace(cp) && !Character.isWhitespace(next)) ||
                    (Script.of(cp) == Script.HAN && Script.of(next) == Script.HAN)
            }
            i = nextIdx
        }
        return ok
    }
}
