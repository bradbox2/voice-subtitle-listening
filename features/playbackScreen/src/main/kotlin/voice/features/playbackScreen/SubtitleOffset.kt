package voice.features.playbackScreen

import voice.core.data.BookId
import voice.core.data.ChapterId
import kotlin.math.absoluteValue

internal const val MAX_SUBTITLE_OFFSET_MS = 60_000L
internal const val SUBTITLE_OFFSET_STEP_MS = 100L

internal fun subtitleOffsetKey(
  bookId: BookId,
  chapterId: ChapterId,
): String {
  return listOf(
    bookId.value.stableShortHash(),
    chapterId.value.stableShortHash(),
  ).joinToString(separator = "|")
}

internal fun Map<String, Long>.subtitleOffsetMs(key: String): Long {
  return this[key] ?: 0L
}

internal fun adjustSubtitleOffset(
  currentMs: Long,
  deltaMs: Long,
): Long {
  return (currentMs + deltaMs).coerceIn(-MAX_SUBTITLE_OFFSET_MS, MAX_SUBTITLE_OFFSET_MS)
}

internal fun formatSubtitleOffset(offsetMs: Long): String {
  val sign = when {
    offsetMs > 0L -> "+"
    offsetMs < 0L -> "-"
    else -> ""
  }
  val absMs = offsetMs.absoluteValue
  return "$sign${absMs / 1000}.${absMs % 1000 / 100}s"
}
