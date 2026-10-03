package com.example.captions

import com.ahstudio.captions.core.model.*
import com.ahstudio.captions.core.time.TimelineUs
import com.ahstudio.captions.segmentation.CaptionSegmentationEngine
import com.ahstudio.captions.segmentation.SegmentationOptions
import com.ahstudio.captions.subtitle.*
import org.junit.Assert.*
import org.junit.Test

class CaptionsCoreUnitTest {

  @Test
  fun `SrtParser parses valid multi-cue SRT format accurately`() {
    val srt = """
      1
      00:00:01,250 --> 00:00:03,750
      First caption line
      
      2
      00:00:04,100 --> 00:00:06,900
      Second caption line
      with multiple sentences
    """.trimIndent()

    val cues = SrtParser.parse(srt)
    assertEquals(2, cues.size)

    assertEquals(1, cues[0].index)
    assertEquals(1_250_000L, cues[0].startUs)
    assertEquals(3_750_000L, cues[0].endUs)
    assertEquals("First caption line", cues[0].lines.joinToString(" "))

    assertEquals(2, cues[1].index)
    assertEquals(4_100_000L, cues[1].startUs)
    assertEquals(6_900_000L, cues[1].endUs)
    assertTrue(cues[1].lines.contains("Second caption line"))
  }

  @Test
  fun `VttParser parses WebVTT header and timestamps correctly`() {
    val vtt = """
      WEBVTT
      
      00:00:00.500 --> 00:00:02.800
      Hello from WebVTT test!
      
      00:00:03.000 --> 00:00:05.500
      Second WebVTT subtitle cue
    """.trimIndent()

    val cues = VttParser.parse(vtt)
    assertEquals(2, cues.size)
    assertEquals(500_000L, cues[0].startUs)
    assertEquals(2_800_000L, cues[0].endUs)
    assertEquals("Hello from WebVTT test!", cues[0].lines[0])

    assertEquals(3_000_000L, cues[1].startUs)
    assertEquals(5_500_000L, cues[1].endUs)
  }

  @Test
  fun `AssParser parses Advanced SubStation Alpha format dialogue`() {
    val ass = """
      [Script Info]
      Title: ASS Subtitles
      
      [Events]
      Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
      Dialogue: 0,0:00:01.50,0:00:04.20,Default,,0,0,0,,Stylized dialogue text
    """.trimIndent()

    val cues = AssParser.parse(ass)
    assertEquals(1, cues.size)
    assertEquals(1_500_000L, cues[0].startUs)
    assertEquals(4_200_000L, cues[0].endUs)
    assertEquals("Stylized dialogue text", cues[0].lines[0])
  }

  @Test
  fun `SubtitleImporter detects SRT, VTT, and plain TXT automatically`() {
    val srt = "1\n00:00:01,000 --> 00:00:02,000\nSRT Line"
    val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nVTT Line"
    val txt = "Plain line one\nPlain line two"

    val srtCues = SubtitleImporter.autoDetectAndParse(srt)
    val vttCues = SubtitleImporter.autoDetectAndParse(vtt)
    val txtCues = SubtitleImporter.autoDetectAndParse(txt)

    assertEquals(1, srtCues.size)
    assertEquals("SRT Line", srtCues[0].lines[0])

    assertEquals(1, vttCues.size)
    assertEquals("VTT Line", vttCues[0].lines[0])

    assertEquals(2, txtCues.size)
    assertEquals("Plain line one", txtCues[0].lines[0])
    assertEquals("Plain line two", txtCues[1].lines[0])
  }

  @Test
  fun `SubtitleExporter produces correct SRT and VTT string formatting`() {
    val cues = listOf(
      SubtitleCue(1, 1_000_000L, 3_250_000L, listOf("First Subtitle")),
      SubtitleCue(2, 4_000_000L, 6_000_000L, listOf("Second Subtitle"))
    )

    val srt = SubtitleExporter.export(cues, null, SubtitleFormat.SRT)
    assertTrue(srt.contains("1\n00:00:01,000 --> 00:00:03,250\nFirst Subtitle"))
    assertTrue(srt.contains("2\n00:00:04,000 --> 00:00:06,000\nSecond Subtitle"))

    val vtt = SubtitleExporter.export(cues, null, SubtitleFormat.VTT)
    assertTrue(vtt.startsWith("WEBVTT"))
    assertTrue(vtt.contains("00:00:01.000 --> 00:00:03.250"))
    assertTrue(vtt.contains("First Subtitle"))

    val ass = SubtitleExporter.export(cues, CaptionStyle.DEFAULT, SubtitleFormat.ASS)
    assertTrue(ass.contains("[Script Info]"))
    assertTrue(ass.contains("Dialogue: 0,0:00:01.00,0:00:03.25,Default,,0,0,0,,First Subtitle"))

    val txt = SubtitleExporter.export(cues, null, SubtitleFormat.TXT)
    assertEquals("First Subtitle\nSecond Subtitle", txt)
  }

  @Test
  fun `CueToWords distributes word timestamps evenly across cue duration`() {
    val cue = SubtitleCue(1, 2_000_000L, 5_000_000L, listOf("Create amazing video captions"))
    val words = CueToWords.words(cue)

    assertEquals(4, words.size)
    assertEquals("Create", words[0].text)
    assertEquals("amazing", words[1].text)
    assertEquals("video", words[2].text)
    assertEquals("captions", words[3].text)

    assertTrue(words[0].start.micros >= 2_000_000L)
    assertTrue(words[3].end.micros <= 5_000_000L)
    assertTrue(words[0].end.micros <= words[1].start.micros)
    assertTrue(words[1].end.micros <= words[2].start.micros)
    assertTrue(words[2].end.micros <= words[3].start.micros)
  }

  @Test
  fun `CaptionSegmentationEngine segments words based on length and timing constraints`() {
    val options = SegmentationOptions(maxCharsPerLine = 15, maxLines = 1)
    val engine = CaptionSegmentationEngine(options)

    val words = listOf(
      CaptionWord("Hello", TimelineUs(0L), TimelineUs(400_000L)),
      CaptionWord("World", TimelineUs(450_000L), TimelineUs(800_000L)),
      CaptionWord("this", TimelineUs(900_000L), TimelineUs(1_200_000L)),
      CaptionWord("is", TimelineUs(1_250_000L), TimelineUs(1_400_000L)),
      CaptionWord("a", TimelineUs(1_450_000L), TimelineUs(1_550_000L)),
      CaptionWord("test", TimelineUs(1_600_000L), TimelineUs(1_900_000L))
    )

    val result = engine.segment(
      words = words,
      trackId = "track_1",
      styleId = CaptionStyle.DEFAULT_ID,
      silences = emptyList(),
      source = ClipSource.RECOGNIZED
    )

    assertTrue(result.clips.isNotEmpty())
    result.clips.forEach { clip ->
      assertTrue("Clip display text must not be empty", clip.displayText.isNotBlank())
      assertTrue("Start must be before or equal to end", clip.timing.start.micros <= clip.timing.end.micros)
    }
  }

  @Test
  fun `Roundtrip test - export and import subtitles preserves content`() {
    val originalCues = listOf(
      SubtitleCue(1, 1_000_000L, 3_000_000L, listOf("Roundtrip test line 1")),
      SubtitleCue(2, 3_500_000L, 5_500_000L, listOf("Roundtrip test line 2"))
    )

    val srtOutput = SubtitleExporter.export(originalCues, null, SubtitleFormat.SRT)
    val parsedCues = SubtitleImporter.autoDetectAndParse(srtOutput)

    assertEquals(2, parsedCues.size)
    assertEquals(1_000_000L, parsedCues[0].startUs)
    assertEquals(3_000_000L, parsedCues[0].endUs)
    assertEquals("Roundtrip test line 1", parsedCues[0].lines[0])

    assertEquals(3_500_000L, parsedCues[1].startUs)
    assertEquals(5_500_000L, parsedCues[1].endUs)
    assertEquals("Roundtrip test line 2", parsedCues[1].lines[0])
  }
}
