package voice.core.playback.player

import androidx.media3.extractor.mp3.Mp3Extractor
import io.kotest.matchers.shouldBe
import org.junit.Test

class Mp3ExtractorFlagsTest {

  @Test
  fun `enabled index seeking maps to the mp3 index seeking flag`() {
    mp3ExtractorFlags(indexSeekingEnabled = true) shouldBe Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING
  }

  @Test
  fun `disabled index seeking maps to no extractor flags`() {
    mp3ExtractorFlags(indexSeekingEnabled = false) shouldBe 0
  }
}
