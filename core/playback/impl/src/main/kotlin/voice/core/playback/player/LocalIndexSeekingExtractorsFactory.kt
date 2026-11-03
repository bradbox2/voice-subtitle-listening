package voice.core.playback.player

import android.content.ContentResolver
import android.net.Uri
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor

/**
 * Seeking in a VBR mp3 by its Xing table of contents lands up to several seconds off on long files,
 * while the player reports the requested position, so subtitles drift after every seek. Seeking by
 * an index of the frames is exact, but reads the file up to the target, which is only acceptable
 * when the file is on the device.
 */
internal class LocalIndexSeekingExtractorsFactory(
  private val local: ExtractorsFactory =
    DefaultExtractorsFactory()
      .setConstantBitrateSeekingEnabled(true)
      .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING),
  private val other: ExtractorsFactory =
    DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
) : ExtractorsFactory {

  override fun createExtractors(): Array<Extractor> = other.createExtractors()

  override fun createExtractors(
    uri: Uri,
    responseHeaders: Map<String, List<String>>,
  ): Array<Extractor> {
    val factory =
      when (uri.scheme) {
        ContentResolver.SCHEME_FILE,
        ContentResolver.SCHEME_CONTENT -> local
        else -> other
      }
    return factory.createExtractors(uri, responseHeaders)
  }
}
