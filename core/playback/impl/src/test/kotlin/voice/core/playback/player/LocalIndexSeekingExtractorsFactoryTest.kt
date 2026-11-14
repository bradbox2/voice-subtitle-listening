package voice.core.playback.player

import android.net.Uri
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.mp3.LocalMp3Extractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalIndexSeekingExtractorsFactoryTest {

  private val downloaded = "abs://item/1/track/0/2"
  private val factory =
    LocalIndexSeekingExtractorsFactory(isDownloaded = { it.toString() == downloaded })

  @Test
  fun `mp3 files on the device seek by index`() {
    listOf(
        "file:///sdcard/Books/one.mp3",
        "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fone.mp3",
      )
      .forEach { uri ->
        val extractors = extractorsFor(uri)
        assertTrue(extractors.any { it is LocalMp3Extractor }, uri)
        assertTrue(extractors.none { it is Mp3Extractor }, uri)
      }
  }

  @Test
  fun `streamed books keep the default, as seeking by index reads the file from its start`() {
    val extractors = extractorsFor("audiobookshelf://item/1/file/2.mp3")

    assertTrue(extractors.any { it is Mp3Extractor })
    assertTrue(extractors.none { it is LocalMp3Extractor })
  }

  @Test
  fun `downloaded server books seek by index`() {
    val extractors = extractorsFor(downloaded)

    assertTrue(extractors.any { it is LocalMp3Extractor })
    assertTrue(extractors.none { it is Mp3Extractor })
  }

  @Test
  fun `server books that stream keep the default`() {
    assertTrue(extractorsFor("abs://item/1/track/1/3").none { it is LocalMp3Extractor })
  }

  @Test
  fun `other formats stay available for files on the device`() {
    assertTrue(extractorsFor("file:///sdcard/Books/one.m4b").size > 1)
  }

  private fun extractorsFor(uri: String): List<Extractor> =
    factory.createExtractors(Uri.parse(uri), emptyMap()).asList()
}
