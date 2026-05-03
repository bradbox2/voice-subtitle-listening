package voice.core.playback.subtitle

import androidx.core.net.toUri
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleLoaderTest {

  @get:Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  private val loader = SubtitleLoader(
    subtitleFileProvider = SameFolderSubtitleFileProvider(),
    srtParser = SrtParser(),
  )

  @Test
  fun `loads parsed cues for matching srt file`() {
    val audio = temporaryFolder.newFile("chapter01.mp3")
    temporaryFolder.newFile("chapter01.srt").writeText(
      """
        1
        00:00:00,500 --> 00:00:02,000
        First sentence
      """.trimIndent(),
    )

    loader.loadForAudio(audio.toUri()).shouldContainExactly(
      SubtitleCue(
        startMs = 500,
        endMs = 2_000,
        text = "First sentence",
      ),
    )
  }

  @Test
  fun `returns empty list when subtitle is missing`() {
    val audio = temporaryFolder.newFile("chapter01.mp3")

    loader.loadForAudio(audio.toUri()) shouldBe emptyList()
  }
}
