package voice.core.playback.player

import androidx.media3.extractor.mp3.Mp3Extractor

public fun mp3ExtractorFlags(indexSeekingEnabled: Boolean): Int {
  return if (indexSeekingEnabled) {
    Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING
  } else {
    0
  }
}
