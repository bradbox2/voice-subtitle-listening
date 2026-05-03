package voice.core.playback.subtitle

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class SrtParserTest {

  private val parser = SrtParser()

  @Test
  fun `parses srt cues with multiline text`() {
    val srt = """
      1
      00:00:01,500 --> 00:00:03,000
      Hello
      world

      2
      00:01:02,003 --> 00:01:05,250
      Next sentence
    """.trimIndent()

    parser.parse(srt).shouldContainExactly(
      SubtitleCue(
        startMs = 1_500,
        endMs = 3_000,
        text = "Hello\nworld",
      ),
      SubtitleCue(
        startMs = 62_003,
        endMs = 65_250,
        text = "Next sentence",
      ),
    )
  }

  @Test
  fun `ignores invalid cue blocks safely`() {
    val srt = """
      not a cue

      2
      00:00:04,000 --> 00:00:05,000
      Valid sentence
    """.trimIndent()

    parser.parse(srt).shouldContainExactly(
      SubtitleCue(
        startMs = 4_000,
        endMs = 5_000,
        text = "Valid sentence",
      ),
    )
  }

  @Test
  fun `matches active cue using inclusive start and exclusive end`() {
    val cues = listOf(
      SubtitleCue(startMs = 1_000, endMs = 2_000, text = "First"),
      SubtitleCue(startMs = 2_000, endMs = 3_000, text = "Second"),
    )

    cues.activeCueIndex(positionMs = 999) shouldBe null
    cues.activeCueIndex(positionMs = 1_000) shouldBe 0
    cues.activeCueIndex(positionMs = 1_999) shouldBe 0
    cues.activeCueIndex(positionMs = 2_000) shouldBe 1
    cues.activeCueIndex(positionMs = 3_000) shouldBe null
  }
}
