package voice.core.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleCueTest {

  private val cues =
    listOf(
      SubtitleCue(startMs = 1_000, endMs = 2_000, text = "a"),
      SubtitleCue(startMs = 2_000, endMs = 3_000, text = "b"),
      SubtitleCue(startMs = 5_000, endMs = 6_000, text = "c"),
    )

  @Test
  fun `finds the cue the position is in`() {
    assertEquals(0, cues.activeCueIndex(1_000))
    assertEquals(0, cues.activeCueIndex(1_999))
    assertEquals(1, cues.activeCueIndex(2_000))
    assertEquals(2, cues.activeCueIndex(5_500))
  }

  @Test
  fun `no cue before the first, in a gap or after the last`() {
    assertNull(cues.activeCueIndex(999))
    assertNull(cues.activeCueIndex(4_000))
    assertNull(cues.activeCueIndex(6_000))
    assertNull(emptyList<SubtitleCue>().activeCueIndex(0))
  }
}
