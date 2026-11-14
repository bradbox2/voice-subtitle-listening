package voice.core.studystats

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import voice.core.audiobookshelf.ServerFiles
import voice.core.data.BookId
import voice.core.data.ChapterId

class StudyNamesTest {

  private val tree = "content://com.android.externalstorage.documents/tree/primary%3ADownload"

  @Test
  fun `a file of a folder book is named by the folder and the file`() {
    val names =
      studyNames(
        bookId = BookId("$tree/document/primary%3ADownload%2FBaiduNetdisk%2FS0106edition"),
        chapterId =
          ChapterId(
            "$tree/document/primary%3ADownload%2FBaiduNetdisk%2FS0106edition%2FS01E06-elevenlabs.mp3",
          ),
      )

    assertEquals(StudyNames(folder = "S0106edition", file = "S01E06-elevenlabs.mp3"), names)
  }

  @Test
  fun `files in sub folders of a book count for the book`() {
    val names =
      studyNames(
        bookId = BookId("$tree/document/primary%3ADownload%2FfriendsTTS"),
        chapterId = ChapterId("$tree/document/primary%3ADownload%2FfriendsTTS%2FCD1%2FS01E01.mp3"),
      )

    assertEquals(StudyNames(folder = "friendsTTS", file = "S01E01.mp3"), names)
  }

  @Test
  fun `a book of a single file is named by the folder it is in`() {
    val id = "$tree/document/primary%3ADownload%2FOral%2F001_james.wav"

    assertEquals(
      StudyNames(folder = "Oral", file = "001_james.wav"),
      studyNames(BookId(id), ChapterId(id)),
    )
  }

  @Test
  fun `server books are named like on the server`() {
    val bookId = BookId("abs://item/li_1")
    val chapterId = ChapterId("abs://item/li_1/track/1/418302")
    val serverFiles =
      mockk<ServerFiles> {
        every { folderName(bookId) } returns "VoiceSubtitleTestBook"
        every { fileName(chapterId) } returns "E01.mp3"
      }

    assertEquals(
      StudyNames(folder = "VoiceSubtitleTestBook", file = "E01.mp3"),
      studyNames(bookId, chapterId, serverFiles),
    )
  }

  @Test
  fun `file uris and characters that need decoding`() {
    assertEquals(
      StudyNames(folder = "长音频", file = "C++ 第1课.mp3"),
      studyNames(
        BookId("file:///sdcard/Books/%E9%95%BF%E9%9F%B3%E9%A2%91"),
        ChapterId("file:///sdcard/Books/%E9%95%BF%E9%9F%B3%E9%A2%91/C++%20%E7%AC%AC1%E8%AF%BE.mp3"),
      ),
    )
  }
}
