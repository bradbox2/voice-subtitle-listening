package voice.core.playback.subtitle

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleFileProviderTest {

  @get:Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  private val provider: SubtitleFileProvider = SameFolderSubtitleFileProvider()

  @Test
  fun `finds srt file with same base name in same folder`() {
    val audio = temporaryFolder.newFile("chapter01.mp3")
    val subtitle = temporaryFolder.newFile("chapter01.srt")

    provider.findSubtitleForAudio(audio.toUri()) shouldBe subtitle.toUri()
  }

  @Test
  fun `keeps full base name when audio file name contains multiple dots`() {
    val audio = temporaryFolder.newFile("lesson.02.m4b")
    val subtitle = temporaryFolder.newFile("lesson.02.srt")

    provider.findSubtitleForAudio(audio.toUri()) shouldBe subtitle.toUri()
  }

  @Test
  fun `returns null when matching subtitle is missing`() {
    val audio = temporaryFolder.newFile("chapter01.mp3")

    provider.findSubtitleForAudio(audio.toUri()) shouldBe null
  }

  @Test
  fun `returns null for unsupported content uris`() {
    provider.findSubtitleForAudio("content://voice/books/chapter01.mp3".toUri()) shouldBe null
  }

  @Test
  fun `builds srt file name from audio display name`() {
    subtitleNameForAudioDisplayName(
      "Mike Epps Delusional_001_srt922-943_And I got a chance to work with the great Whitne.mp3",
    ) shouldBe "Mike Epps Delusional_001_srt922-943_And I got a chance to work with the great Whitne.srt"
  }

  @Test
  fun `extracts parent document id and audio display name from saf document uri`() {
    val uri = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks/document/primary%3AAudiobooks%2Fsample%2Flesson01.mp3".toUri()

    uri.toSafAudioDocument()
      ?.copy(authority = null) shouldBe SafAudioDocument(
      authority = null,
      parentDocumentId = "primary:Audiobooks/sample",
      audioDisplayName = "lesson01.mp3",
    )
  }

  @Test
  fun `returns null for inaccessible file uri`() {
    val missingAudio = temporaryFolder.root.resolve("missing/chapter01.mp3")

    provider.findSubtitleForAudio(missingAudio.toUri()) shouldBe null
  }
}
