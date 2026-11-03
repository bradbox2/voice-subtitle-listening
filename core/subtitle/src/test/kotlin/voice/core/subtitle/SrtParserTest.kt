package voice.core.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals

class SrtParserTest {

  private val parser = SrtParser()

  @Test
  fun `parses cues with multi line text`() {
    val cues =
      parser.parse(
        """
        1
        00:00:01,000 --> 00:00:02,500
        Hello

        2
        00:01:02,003 --> 01:00:00,000
        Second
        line
        """
          .trimIndent()
      )

    assertEquals(
      expected =
        listOf(
          SubtitleCue(startMs = 1_000, endMs = 2_500, text = "Hello"),
          SubtitleCue(startMs = 62_003, endMs = 3_600_000, text = "Second\nline"),
        ),
      actual = cues,
    )
  }

  @Test
  fun `accepts windows line endings, a byte order mark and dots before the milliseconds`() {
    val cues = parser.parse("﻿1\r\n00:00:01.000 --> 00:00:02.000\r\nHi\r\n\r\n")

    assertEquals(listOf(SubtitleCue(startMs = 1_000, endMs = 2_000, text = "Hi")), cues)
  }

  @Test
  fun `ignores position settings after the end time`() {
    val cues = parser.parse("1\n00:00:01,000 --> 00:00:02,000 X1:40 X2:600\nHi")

    assertEquals(listOf(SubtitleCue(startMs = 1_000, endMs = 2_000, text = "Hi")), cues)
  }

  @Test
  fun `skips broken, empty and backwards cues`() {
    val cues =
      parser.parse(
        """
        1
        00:00:03,000 --> 00:00:02,000
        Backwards

        2
        not a time --> 00:00:02,000
        Broken

        3
        00:00:04,000 --> 00:00:05,000

        4
        00:00:06,000 --> 00:00:07,000
        Fine
        """
          .trimIndent()
      )

    assertEquals(listOf(SubtitleCue(startMs = 6_000, endMs = 7_000, text = "Fine")), cues)
  }

  @Test
  fun `sorts cues by their start`() {
    val cues = parser.parse("1\n00:00:05,000 --> 00:00:06,000\nB\n\n2\n00:00:01,000 --> 00:00:02,000\nA")

    assertEquals(listOf("A", "B"), cues.map { it.text })
  }
}
