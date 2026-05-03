package voice.features.playbackScreen

import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.playback.subtitle.SubtitleCue
import java.security.MessageDigest

internal fun SubtitleCue.subtitleCueKey(
  bookId: BookId,
  chapterId: ChapterId,
): String {
  return listOf(
    bookId.value.stableShortHash(),
    chapterId.value.stableShortHash(),
    startMs.toString(),
    endMs.toString(),
    text.stableShortHash(),
  ).joinToString(separator = "|")
}

internal fun Set<String>.toggleSubtitleCueStar(key: String): Set<String> {
  return if (key in this) {
    this - key
  } else {
    this + key
  }
}

private fun String.stableShortHash(): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
  return digest.take(8).joinToString(separator = "") { byte -> "%02x".format(byte) }
}
