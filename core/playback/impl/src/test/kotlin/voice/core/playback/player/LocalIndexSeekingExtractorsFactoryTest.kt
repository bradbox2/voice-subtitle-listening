package voice.core.playback.player

import android.net.Uri
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertSame
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalIndexSeekingExtractorsFactoryTest {

  private val localExtractor = mockk<Extractor>()
  private val otherExtractor = mockk<Extractor>()
  private val factory =
    LocalIndexSeekingExtractorsFactory(
      local = ExtractorsFactory { arrayOf(localExtractor) },
      other = ExtractorsFactory { arrayOf(otherExtractor) },
    )

  @Test
  fun `files on the device seek by index`() {
    assertSame(localExtractor, factory.extractorFor("file:///sdcard/Books/one.mp3"))
    assertSame(
      localExtractor,
      factory.extractorFor(
        "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fone.mp3",
      ),
    )
  }

  @Test
  fun `streamed books don't, as seeking by index reads the file from its start`() {
    assertSame(otherExtractor, factory.extractorFor("audiobookshelf://item/1/file/2"))
    assertSame(otherExtractor, factory.extractorFor("https://example.com/one.mp3"))
  }

  private fun ExtractorsFactory.extractorFor(uri: String): Extractor {
    return createExtractors(Uri.parse(uri), emptyMap()).single()
  }
}
