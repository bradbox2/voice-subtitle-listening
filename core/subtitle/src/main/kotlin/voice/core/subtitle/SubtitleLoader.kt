package voice.core.subtitle

import android.content.Context
import dev.zacsweers.metro.Inject
import java.io.IOException
import voice.core.data.ChapterId
import voice.core.data.toUri
import voice.core.logging.api.Logger

@Inject
class SubtitleLoader(
  private val context: Context,
  private val subtitleFileProvider: SubtitleFileProvider,
  private val srtParser: SrtParser,
) {

  fun loadForAudio(chapterId: ChapterId): List<SubtitleCue> {
    val subtitleUri =
      subtitleFileProvider.findSubtitleForAudio(chapterId.toUri()) ?: return emptyList()
    val bytes =
      try {
        context.contentResolver.openInputStream(subtitleUri)?.use { it.readBytes() }
      } catch (e: IOException) {
        Logger.w(e, "Couldn't read the subtitle file")
        null
      } catch (e: SecurityException) {
        Logger.w(e, "No access to the subtitle file")
        null
      } ?: return emptyList()
    return srtParser.parse(decodeSubtitleText(bytes)).also { cues ->
      Logger.d("Loaded ${cues.size} subtitle cues from ${bytes.size} bytes")
    }
  }
}
