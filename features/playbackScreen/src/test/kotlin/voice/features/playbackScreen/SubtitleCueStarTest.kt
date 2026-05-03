package voice.features.playbackScreen

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.playback.subtitle.SubtitleCue

class SubtitleCueStarTest {

  @Test
  fun `subtitle cue key is stable and does not include raw content uri`() {
    val cue = SubtitleCue(
      startMs = 4_500,
      endMs = 7_000,
      text = "This is the sentence I want to repeat.",
    )
    val chapterId = ChapterId("content://com.android.externalstorage.documents/document/primary%3ASample%2Flesson01.mp3")

    val key = cue.subtitleCueKey(
      bookId = BookId("book-1"),
      chapterId = chapterId,
    )

    key shouldBe cue.subtitleCueKey(
      bookId = BookId("book-1"),
      chapterId = chapterId,
    )
    key.contains("content://") shouldBe false
    key.split("|").size shouldBe 5
  }

  @Test
  fun `toggleSubtitleCueStar adds and removes key`() {
    val key = "book|chapter|4500|7000|hash"

    val starred = emptySet<String>().toggleSubtitleCueStar(key)
    starred shouldBe setOf(key)

    starred.toggleSubtitleCueStar(key) shouldBe emptySet()
  }
}
