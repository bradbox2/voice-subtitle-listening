package voice.core.playback.player

import android.content.ContentResolver
import android.net.Uri
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.LocalMp3Extractor
import androidx.media3.extractor.mp3.Mp3Extractor

/**
 * Mp3 files on the device seek by an index of their frames, see [LocalMp3Extractor]. That reads the
 * file up to the target, which is only acceptable when the file is on the device.
 */
internal class LocalIndexSeekingExtractorsFactory(
  private val local: ExtractorsFactory = ExtractorsFactory {
    arrayOf(
      LocalMp3Extractor(
        Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING or Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING,
      ),
    )
  },
  private val other: ExtractorsFactory =
    DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
) : ExtractorsFactory {

  override fun createExtractors(): Array<Extractor> = other.createExtractors()

  override fun createExtractors(
    uri: Uri,
    responseHeaders: Map<String, List<String>>,
  ): Array<Extractor> {
    val extractors = other.createExtractors(uri, responseHeaders)
    return when (uri.scheme) {
      ContentResolver.SCHEME_FILE,
      ContentResolver.SCHEME_CONTENT ->
        extractors
          .flatMap { if (it is Mp3Extractor) local.createExtractors().asList() else listOf(it) }
          .toTypedArray()
      else -> extractors
    }
  }
}
