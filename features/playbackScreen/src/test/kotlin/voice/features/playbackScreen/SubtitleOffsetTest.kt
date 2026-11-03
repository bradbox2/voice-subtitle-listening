package voice.features.playbackScreen

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.ChapterId

class SubtitleOffsetTest {

  private val chapterId = ChapterId("content://com.android.externalstorage.documents/document/primary%3ASample%2Flesson01.mp3")

  @Test
  fun `subtitle offset key is stable and does not include raw content uri`() {
    val key = subtitleOffsetKey(
      bookId = BookId("book-1"),
      chapterId = chapterId,
    )

    key shouldBe subtitleOffsetKey(
      bookId = BookId("book-1"),
      chapterId = chapterId,
    )
    key.contains("content://") shouldBe false
    key.split("|").size shouldBe 2
  }

  @Test
  fun `subtitle offset keys differ per chapter`() {
    val bookId = BookId("book-1")
    val otherChapterId = ChapterId("content://other/lesson02.mp3")

    subtitleOffsetKey(bookId, chapterId) shouldNotBe subtitleOffsetKey(bookId, otherChapterId)
  }

  @Test
  fun `offset defaults to zero when no entry exists`() {
    emptyMap<String, Long>().subtitleOffsetMs("key") shouldBe 0L
    mapOf("key" to 300L).subtitleOffsetMs("key") shouldBe 300L
  }

  @Test
  fun `adjustSubtitleOffset applies delta and clamps to bounds`() {
    adjustSubtitleOffset(currentMs = 0L, deltaMs = 100L) shouldBe 100L
    adjustSubtitleOffset(currentMs = 200L, deltaMs = -100L) shouldBe 100L
    adjustSubtitleOffset(currentMs = 59_950L, deltaMs = 100L) shouldBe 60_000L
    adjustSubtitleOffset(currentMs = -59_950L, deltaMs = -100L) shouldBe -60_000L
  }

  @Test
  fun `formatSubtitleOffset shows signed seconds`() {
    formatSubtitleOffset(0L) shouldBe "0.0s"
    formatSubtitleOffset(300L) shouldBe "+0.3s"
    formatSubtitleOffset(-1_200L) shouldBe "-1.2s"
    formatSubtitleOffset(10_000L) shouldBe "+10.0s"
  }
}
