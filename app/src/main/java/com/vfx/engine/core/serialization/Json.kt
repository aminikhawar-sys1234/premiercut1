package com.vfx.engine.core.serialization

import com.vfx.engine.core.EffectEngineException

/**
 * Minimal, dependency-free JSON model + strict parser + writer.
 * Used for portable effect/stack serialization — stable across app versions.
 */
sealed class Json {
    object Null : Json()
    data class Bool(val value: Boolean) : Json()
    data class Num(val value: Double) : Json()
    data class Str(val value: String) : Json()
    data class Arr(val items: List<Json>) : Json()
    data class Obj(val fields: Map<String, Json>) : Json()

    // ---- convenience accessors (null-safe) ----
    fun asObj(): Obj? = this as? Obj
    fun asArr(): Arr? = this as? Arr
    fun asStr(): String? = (this as? Str)?.value
    fun asBool(): Boolean? = (this as? Bool)?.value
    fun asFloat(): Float? = (this as? Num)?.value?.toFloat()
    fun asDouble(): Double? = (this as? Num)?.value
    fun asLong(): Long? = (this as? Num)?.value?.toLong()

    operator fun get(key: String): Json? = (this as? Obj)?.fields?.get(key)

    companion object {
        /** Builders for compact codec code. */
        fun obj(vararg pairs: Pair<String, Json>): Obj = Obj(LinkedHashMap<String, Json>().apply { pairs.forEach { put(it.first, it.second) } })
        fun arr(items: List<Json>): Arr = Arr(items)

        fun parse(text: String): Json {
            val p = Parser(text)
            p.skipWs()
            val v = p.parseValue()
            p.skipWs()
            if (!p.atEnd()) throw p.fail("Trailing content")
            return v
        }

        fun write(j: Json): String {
            val sb = StringBuilder()
            writeInto(j, sb)
            return sb.toString()
        }

        private fun writeInto(j: Json, sb: StringBuilder) {
            when (j) {
                is Null -> sb.append("null")
                is Bool -> sb.append(j.value)
                is Num -> {
                    val d = j.value
                    if (d.isNaN() || d.isInfinite()) sb.append("null")   // JSON has no NaN/Inf
                    else if (d == d.toLong().toDouble() && kotlin.math.abs(d) < 1e15)
                        sb.append(d.toLong().toString())
                    else sb.append(d.toString())
                }
                is Str -> writeString(j.value, sb)
                is Arr -> {
                    sb.append('[')
                    j.items.forEachIndexed { i, e ->
                        if (i > 0) sb.append(',')
                        writeInto(e, sb)
                    }
                    sb.append(']')
                }
                is Obj -> {
                    sb.append('{')
                    j.fields.entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) sb.append(',')
                        writeString(k, sb)
                        sb.append(':')
                        writeInto(v, sb)
                    }
                    sb.append('}')
                }
            }
        }

        private fun writeString(s: String, sb: StringBuilder) {
            sb.append('"')
            s.forEach { c ->
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
                }
            }
            sb.append('"')
        }

        private class Parser(private val s: String) {
            var pos = 0

            fun atEnd() = pos >= s.length
            fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
            fun fail(msg: String) = EffectEngineException.Serialization("$msg at position $pos")

            fun parseValue(): Json {
                skipWs()
                if (atEnd()) throw fail("Unexpected end of input")
                return when (val c = s[pos]) {
                    '{' -> parseObj()
                    '[' -> parseArr()
                    '"' -> Str(parseString())
                    't' -> { expect("true"); Bool(true) }
                    'f' -> { expect("false"); Bool(false) }
                    'n' -> { expect("null"); Null }
                    else -> if (c == '-' || c.isDigit()) parseNumber()
                    else throw fail("Unexpected character '$c'")
                }
            }

            fun expect(word: String) {
                if (!s.startsWith(word, pos)) throw fail("Expected '$word'")
                pos += word.length
            }

            fun parseObj(): Obj {
                pos++ // consume '{'
                val m = LinkedHashMap<String, Json>()
                skipWs()
                if (!atEnd() && s[pos] == '}') { pos++; return Obj(m) }
                while (true) {
                    skipWs()
                    if (atEnd() || s[pos] != '"') throw fail("Expected object key string")
                    val k = parseString()
                    skipWs()
                    if (atEnd() || s[pos] != ':') throw fail("Expected ':'")
                    pos++
                    m[k] = parseValue()
                    skipWs()
                    when {
                        !atEnd() && s[pos] == ',' -> pos++
                        !atEnd() && s[pos] == '}' -> { pos++; return Obj(m) }
                        else -> throw fail("Expected ',' or '}'")
                    }
                }
            }

            fun parseArr(): Arr {
                pos++ // consume '['
                val items = ArrayList<Json>()
                skipWs()
                if (!atEnd() && s[pos] == ']') { pos++; return Arr(items) }
                while (true) {
                    items.add(parseValue())
                    skipWs()
                    when {
                        !atEnd() && s[pos] == ',' -> pos++
                        !atEnd() && s[pos] == ']' -> { pos++; return Arr(items) }
                        else -> throw fail("Expected ',' or ']'")
                    }
                }
            }

            fun parseString(): String {
                pos++ // consume opening quote
                val sb = StringBuilder()
                while (true) {
                    if (atEnd()) throw fail("Unterminated string")
                    when (val c = s[pos++]) {
                        '"' -> return sb.toString()
                        '\\' -> {
                            if (atEnd()) throw fail("Unterminated escape")
                            when (val e = s[pos++]) {
                                '"' -> sb.append('"')
                                '\\' -> sb.append('\\')
                                '/' -> sb.append('/')
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000C')
                                'u' -> {
                                    if (pos + 4 > s.length) throw fail("Bad unicode escape")
                                    val code = s.substring(pos, pos + 4).toIntOrNull(16)
                                        ?: throw fail("Bad unicode escape")
                                    sb.append(code.toChar())
                                    pos += 4
                                }
                                else -> throw fail("Bad escape '\\$e'")
                            }
                        }
                        else -> sb.append(c)
                    }
                }
            }

            /** Strict JSON number grammar: -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)? */
            fun parseNumber(): Num {
                val start = pos
                if (!atEnd() && s[pos] == '-') pos++
                if (atEnd() || !s[pos].isDigit()) throw fail("Bad number")
                if (s[pos] == '0') pos++
                else while (!atEnd() && s[pos].isDigit()) pos++
                if (!atEnd() && s[pos] == '.') {
                    pos++
                    if (atEnd() || !s[pos].isDigit()) throw fail("Bad fractional part")
                    while (!atEnd() && s[pos].isDigit()) pos++
                }
                if (!atEnd() && (s[pos] == 'e' || s[pos] == 'E')) {
                    pos++
                    if (!atEnd() && (s[pos] == '+' || s[pos] == '-')) pos++
                    if (atEnd() || !s[pos].isDigit()) throw fail("Bad exponent")
                    while (!atEnd() && s[pos].isDigit()) pos++
                }
                val d = s.substring(start, pos).toDoubleOrNull() ?: throw fail("Bad number")
                return Num(d)
            }
        }
    }
}
