package voice.features.playbackScreen.subtitle

import java.security.MessageDigest
import kotlin.math.absoluteValue
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.subtitle.SubtitleCue

internal const val MAX_SUBTITLE_OFFSET_MS = 60_000L
internal const val SUBTITLE_OFFSET_STEP_MS = 100L

// Hashed, so the stores don't keep the file paths. Changing these would lose the listener's stars
// and offsets.
internal fun SubtitleCue.starKey(
  bookId: BookId,
  chapterId: ChapterId,
): String {
  return listOf(
      bookId.value.stableShortHash(),
      chapterId.value.stableShortHash(),
      startMs.toString(),
      endMs.toString(),
      text.stableShortHash(),
    )
    .joinToString(separator = "|")
}

internal fun subtitleOffsetKey(
  bookId: BookId,
  chapterId: ChapterId,
): String {
  return listOf(bookId.value.stableShortHash(), chapterId.value.stableShortHash())
    .joinToString(separator = "|")
}

internal fun adjustSubtitleOffset(
  currentMs: Long,
  deltaMs: Long,
): Long {
  return (currentMs + deltaMs).coerceIn(-MAX_SUBTITLE_OFFSET_MS, MAX_SUBTITLE_OFFSET_MS)
}

internal fun formatSubtitleOffset(offsetMs: Long): String {
  val sign =
    when {
      offsetMs > 0L -> "+"
      offsetMs < 0L -> "-"
      else -> ""
    }
  val absMs = offsetMs.absoluteValue
  return "$sign${absMs / 1000}.${absMs % 1000 / 100}s"
}

private fun String.stableShortHash(): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
  return digest.take(8).joinToString(separator = "") { byte -> "%02x".format(byte) }
}
