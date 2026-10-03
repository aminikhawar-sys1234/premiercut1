package com.ahstudio.captions.subtitle

import com.ahstudio.captions.core.errors.CaptionEngineException
import com.ahstudio.captions.core.model.CaptionStyle
import com.ahstudio.captions.timing.WordTimestampEngine
import com.ahstudio.captions.transcript.TranscriptEngine

data class SubtitleCue(val index: Int, val startUs: Long, val endUs: Long, val lines: List<String>)

object SubtitleImporter {
    fun autoDetectAndParse(text: String): List<SubtitleCue> = when {
        text.contains("-->") && text.trimStart().startsWith("WEBVTT") -> VttParser.parse(text)
        text.contains("-->") -> SrtParser.parse(text)
        text.contains("[Events]") || text.contains("[events]") -> AssParser.parse(text)
        else -> TxtParser.parse(text)
    }
}

object SrtParser {
    fun parse(text: String): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>()
        val blocks = text.replace("\r\n", "\n").replace("\r", "\n").split(Regex("\n{2,}"))
        var index = 0
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val tsLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (tsLineIdx < 0) continue
            val (s, e) = parseSrtRange(lines[tsLineIdx])
                ?: throw CaptionEngineException.SubtitleParse("malformed SRT timestamp: '${lines[tsLineIdx]}'")
            val body = lines.drop(tsLineIdx + 1).map { stripTags(it) }.filter { it.isNotBlank() }
            if (body.isEmpty()) continue
            index++
            cues += SubtitleCue(index, s, e, body)
        }
        return cues
    }

    private fun parseSrtRange(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size != 2) return null
        val s = parseSrtTime(parts[0].trim()) ?: return null
        val e = parseSrtTime(parts[1].trim().substringBefore(' ').trim()) ?: return null
        return s to e
    }

    fun parseSrtTime(t: String): Long? {
        val m = Regex("(\\d+):(\\d{1,2}):(\\d{1,2})[,\\.](\\d{1,3})").find(t) ?: return null
        val (h, mi, s, ms) = m.destructured
        return h.toLong() * 3_600_000_000L + mi.toLong() * 60_000_000L + s.toLong() * 1_000_000L +
            (ms.padEnd(3, '0').toLong() * 1_000L)
    }

    private fun stripTags(line: String) = line.replace(Regex("<[^>]+>"), "").trim()
}

object VttParser {
    fun parse(text: String): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>()
        val normalized = text.replace("\r\n", "\n")
        val blocks = normalized.split(Regex("\n{2,}"))
        var index = 0
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            if (lines.first().startsWith("WEBVTT") || lines.first().startsWith("NOTE") || lines.first().startsWith("STYLE") ||
                lines.first().startsWith("REGION")) continue
            val tsLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (tsLineIdx < 0) continue
            val parts = lines[tsLineIdx].split("-->")
            val s = parseVttTime(parts[0].trim()) ?: throw CaptionEngineException.SubtitleParse("malformed VTT timestamp")
            val e = parseVttTime(parts[1].trim().substringBefore(' ').trim())
                ?: throw CaptionEngineException.SubtitleParse("malformed VTT timestamp")
            val body = lines.drop(tsLineIdx + 1).map { it.replace(Regex("<[^>]+>"), "").trim() }.filter { it.isNotBlank() }
            if (body.isEmpty()) continue
            index++
            cues += SubtitleCue(index, s, e, body)
        }
        return cues
    }

    fun parseVttTime(t: String): Long? {
        val withHours = Regex("(\\d+):(\\d{2}):(\\d{2})\\.(\\d{3})").find(t)
        if (withHours != null) {
            val (h, mi, s, ms) = withHours.destructured
            return h.toLong() * 3_600_000_000L + mi.toLong() * 60_000_000L + s.toLong() * 1_000_000L + ms.toLong() * 1_000L
        }
        val mmOnly = Regex("(\\d+):(\\d{2})\\.(\\d{3})").find(t) ?: return null
        val (mi, s, ms) = mmOnly.destructured
        return mi.toLong() * 60_000_000L + s.toLong() * 1_000_000L + ms.toLong() * 1_000L
    }
}

object AssParser {
    fun parse(text: String): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>()
        var inEvents = false
        var textIdx = -1; var startIdx = -1; var endIdx = -1
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.equals("[Events]", true) -> inEvents = true
                line.startsWith("[") -> inEvents = false
                inEvents && line.startsWith("Format:", true) -> {
                    val fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                    startIdx = fields.indexOf("start"); endIdx = fields.indexOf("end"); textIdx = fields.indexOf("text")
                }
                inEvents && line.startsWith("Dialogue:", true) && startIdx >= 0 && textIdx >= 0 -> {
                    val fields = line.substringAfter(':').split(',', limit = maxOf(textIdx, endIdx) + 1)
                    if (fields.size > textIdx) {
                        val s = parseAssTime(fields.getOrNull(startIdx)?.trim() ?: "") ?: continue
                        val e = parseAssTime(fields.getOrNull(endIdx)?.trim() ?: "") ?: continue
                        val body = fields[textIdx].replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
                            .replace(Regex("\\{[^}]*\\}"), "").trim()
                        if (body.isNotEmpty()) cues += SubtitleCue(cues.size + 1, s, e, body.lines())
                    }
                }
            }
        }
        return cues
    }

    fun parseAssTime(t: String): Long? {
        val m = Regex("(\\d+):(\\d{1,2}):(\\d{1,2})\\.(\\d{1,2})").find(t) ?: return null
        val (h, mi, s, cs) = m.destructured
        return h.toLong() * 3_600_000_000L + mi.toLong() * 60_000_000L + s.toLong() * 1_000_000L + cs.padEnd(2, '0').toLong() * 10_000L
    }
}

object TxtParser {
    fun parse(text: String): List<SubtitleCue> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        return lines.mapIndexed { i, l -> SubtitleCue(i + 1, i * 3_000_000L, (i + 1) * 3_000_000L - 1, listOf(l)) }
    }
}

enum class SubtitleFormat { SRT, VTT, ASS, TXT }

object SubtitleExporter {
    fun export(cues: List<SubtitleCue>, style: CaptionStyle?, format: SubtitleFormat): String = when (format) {
        SubtitleFormat.SRT -> cues.joinToString("\n\n") { c ->
            "${c.index}\n${fmtSrt(c.startUs)} --> ${fmtSrt(c.endUs)}\n${c.lines.joinToString("\n")}"
        } + "\n"
        SubtitleFormat.VTT -> "WEBVTT\n\n" + cues.joinToString("\n\n") { c ->
            "${c.index}\n${fmtVtt(c.startUs)} --> ${fmtVtt(c.endUs)}\n${c.lines.joinToString("\n")}"
        } + "\n"
        SubtitleFormat.ASS -> buildAss(cues, style)
        SubtitleFormat.TXT -> cues.joinToString("\n") { it.lines.joinToString(" ") }
    }

    private fun fmtSrt(us: Long): String {
        val h = us / 3_600_000_000L; val m = us % 3_600_000_000L / 60_000_000L
        val s = us % 60_000_000L / 1_000_000L; val ms = us % 1_000_000L / 1_000L
        return "%02d:%02d:%02d,%03d".format(h, m, s, ms)
    }
    private fun fmtVtt(us: Long): String {
        val h = us / 3_600_000_000L; val m = us % 3_600_000_000L / 60_000_000L
        val s = us % 60_000_000L / 1_000_000L; val ms = us % 1_000_000L / 1_000L
        return "%02d:%02d:%02d.%03d".format(h, m, s, ms)
    }

    private fun buildAss(cues: List<SubtitleCue>, style: CaptionStyle?): String {
        val fs = style?.fontSizeSp?.toInt() ?: 20
        val font = style?.fontFamily ?: "sans-serif"
        val bold = if (style?.bold == true) -1 else 0
        return buildString {
            appendLine("[Script Info]"); appendLine("ScriptType: v4.00+"); appendLine("PlayResX: 1080"); appendLine("PlayResY: 1920"); appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, Bold, Italic, Outline, Shadow, Alignment, MarginV")
            appendLine("Style: Default,$font,$fs,&H00FFFFFF,$bold,0,2,2,2,60")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            for (c in cues) {
                val text = c.lines.joinToString("\\N")
                appendLine("Dialogue: 0,${fmtAss(c.startUs)},${fmtAss(c.endUs)},Default,,0,0,0,,${text}")
            }
        }
    }
    private fun fmtAss(us: Long): String {
        val h = us / 3_600_000_000L; val m = us % 3_600_000_000L / 60_000_000L
        val s = us % 60_000_000L / 1_000_000L; val cs = us % 1_000_000L / 10_000L
        return "%d:%02d:%02d.%02d".format(h, m, s, cs)
    }
}

object CueToWords {
    fun words(cue: SubtitleCue): List<com.ahstudio.captions.core.model.CaptionWord> {
        val text = cue.lines.joinToString(" ").let { TranscriptEngine.normalizeText(it) }
        return WordTimestampEngine.estimateText(text, com.ahstudio.captions.core.time.TimelineUs(cue.startUs), com.ahstudio.captions.core.time.TimelineUs(cue.endUs))
    }
}
