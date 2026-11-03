package voice.core.subtitle

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleFileProviderTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  private val provider = SameFolderSubtitleFileProvider(contentResolver = null)

  @Test
  fun `finds the srt with the same name in the same folder`() {
    val audio = temporaryFolder.newFile("chapter01.mp3")
    val subtitle = temporaryFolder.newFile("chapter01.srt")

    assertEquals(subtitle.toUri(), provider.findSubtitleForAudio(audio.toUri()))
  }

  @Test
  fun `keeps dots inside the name`() {
    val audio = temporaryFolder.newFile("lesson.02.m4b")
    val subtitle = temporaryFolder.newFile("lesson.02.srt")

    assertEquals(subtitle.toUri(), provider.findSubtitleForAudio(audio.toUri()))
  }

  @Test
  fun `matches an upper case extension`() {
    val audio = temporaryFolder.newFile("chapter01.mp3")
    val subtitle = temporaryFolder.newFile("chapter01.SRT")

    assertEquals(subtitle.toUri(), provider.findSubtitleForAudio(audio.toUri()))
  }

  @Test
  fun `no subtitle without a matching file`() {
    val audio = temporaryFolder.newFile("chapter01.mp3")
    temporaryFolder.newFile("chapter02.srt")

    assertNull(provider.findSubtitleForAudio(audio.toUri()))
  }

  @Test
  fun `no subtitle for streamed books`() {
    assertNull(provider.findSubtitleForAudio(Uri.parse("audiobookshelf://item/1")))
  }

  @Test
  fun `splits a saf document id into its folder and name`() {
    val uri =
      Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
          "primary%3ABooks%2FA%2Fone.mp3",
      )

    assertEquals(
      SafDocument(parentDocumentId = "primary:Books/A", displayName = "one.mp3"),
      uri.toSafDocument(),
    )
  }

  @Test
  fun `a saf document in the root of a volume`() {
    val uri =
      Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3Aone.mp3",
      )

    assertEquals(
      SafDocument(parentDocumentId = "primary:", displayName = "one.mp3"),
      uri.toSafDocument(),
    )
  }
}
