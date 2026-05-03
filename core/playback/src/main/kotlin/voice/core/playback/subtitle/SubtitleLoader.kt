package voice.core.playback.subtitle

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import dev.zacsweers.metro.Inject
import java.io.IOException

public class SubtitleLoader internal constructor(
  private val subtitleFileProvider: SubtitleFileProvider,
  private val srtParser: SrtParser,
  private val contentResolver: ContentResolver? = null,
) {

  @Inject
  public constructor(
    context: Context,
    subtitleFileProvider: SubtitleFileProvider,
    srtParser: SrtParser,
  ) : this(
    subtitleFileProvider = subtitleFileProvider,
    srtParser = srtParser,
    contentResolver = context.contentResolver,
  )

  public fun loadForAudio(audioUri: Uri): List<SubtitleCue> {
    Log.d(TAG, "SubtitleLoader audioUri=$audioUri scheme=${audioUri.scheme}")
    val subtitleUri = subtitleFileProvider.findSubtitleForAudio(audioUri)
    if (subtitleUri == null) {
      Log.d(TAG, "SubtitleLoader cue count=0 reason=no_subtitle_uri")
      return emptyList()
    }
    val subtitleText = try {
      subtitleUri.readSubtitleText(contentResolver)
    } catch (_: IllegalArgumentException) {
      Log.d(TAG, "SubtitleLoader cue count=0 reason=invalid_subtitle_file_uri subtitleUri=$subtitleUri")
      return emptyList()
    } catch (_: IOException) {
      Log.d(TAG, "SubtitleLoader cue count=0 reason=io_exception subtitleUri=$subtitleUri")
      return emptyList()
    } catch (_: SecurityException) {
      Log.d(TAG, "SubtitleLoader cue count=0 reason=security_exception subtitleUri=$subtitleUri")
      return emptyList()
    }

    val cues = srtParser.parse(subtitleText)
    val firstCue = cues.firstOrNull()
    if (firstCue != null) {
      Log.d(
        TAG,
        "SubtitleLoader cue count=${cues.size} firstCue startMs=${firstCue.startMs} endMs=${firstCue.endMs} text=${firstCue.text}",
      )
    } else {
      Log.d(TAG, "SubtitleLoader cue count=0 reason=parse_empty subtitleUri=$subtitleUri")
    }
    return cues
  }
}

private fun Uri.readSubtitleText(contentResolver: ContentResolver?): String {
  return when (scheme) {
    "file" -> toFile().readText()
    "content" -> {
      val resolver = requireNotNull(contentResolver)
      resolver.openInputStream(this)?.bufferedReader()?.use { it.readText() }
        ?: throw IOException("Unable to open subtitle content uri")
    }
    else -> throw IllegalArgumentException("Unsupported subtitle uri scheme: $scheme")
  }
}

private const val TAG = "VoiceSubtitle"
