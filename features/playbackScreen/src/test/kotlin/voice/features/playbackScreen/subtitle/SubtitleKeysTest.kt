package voice.features.playbackScreen.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.subtitle.SubtitleCue

class SubtitleKeysTest {

  private val bookId = BookId("book-1")
  private val chapterId =
    ChapterId(
      "content://com.android.externalstorage.documents/document/primary%3ASample%2Flesson01.mp3",
    )

  @Test
  fun `star keys stay the same as in earlier versions, so stars survive updates`() {
    val cue = SubtitleCue(startMs = 4_500, endMs = 7_000, text = "This is the sentence.")

    val key = cue.starKey(bookId, chapterId)

    assertEquals(
      expected = "${hash("book-1")}|${hash(chapterId.value)}|4500|7000|${hash(cue.text)}",
      actual = key,
    )
    assertFalse(key.contains("content://"))
  }

  @Test
  fun `offset keys differ per chapter and don't contain the path`() {
    val key = subtitleOffsetKey(bookId, chapterId)

    assertEquals(expected = "${hash("book-1")}|${hash(chapterId.value)}", actual = key)
    assertNotEquals(key, subtitleOffsetKey(bookId, ChapterId("content://other/lesson02.mp3")))
  }

  @Test
  fun `offsets change in steps and stay within a minute`() {
    assertEquals(100L, adjustSubtitleOffset(currentMs = 0L, deltaMs = 100L))
    assertEquals(100L, adjustSubtitleOffset(currentMs = 200L, deltaMs = -100L))
    assertEquals(60_000L, adjustSubtitleOffset(currentMs = 59_950L, deltaMs = 100L))
    assertEquals(-60_000L, adjustSubtitleOffset(currentMs = -59_950L, deltaMs = -100L))
  }

  @Test
  fun `the file name tells files with the same title apart`() {
    assertEquals(
      expected = "S01E06-chatterbox_actors",
      actual =
        ChapterId(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/" +
              "primary%3ADownload%2FS0106edition%2FS01E06-chatterbox_actors.mp3",
          )
          .fileName(),
    )
    assertEquals(
      expected = "C++ 第1课",
      actual = ChapterId("file:///sdcard/C++%20第1课.mp3").fileName(),
    )
    assertEquals(
      expected = "one",
      actual = ChapterId("content://x/document/primary%3Aone.mp3").fileName(),
    )
  }

  @Test
  fun `offsets show as signed seconds`() {
    assertEquals("0.0s", formatSubtitleOffset(0L))
    assertEquals("+0.3s", formatSubtitleOffset(300L))
    assertEquals("-1.2s", formatSubtitleOffset(-1_200L))
    assertEquals("+10.0s", formatSubtitleOffset(10_000L))
  }

  // the hash the fork used, written out independently
  private fun hash(value: String): String {
    val digest =
      java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.take(8).joinToString("") { "%02x".format(it) }
  }
}
