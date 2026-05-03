package voice.core.playback.subtitle

import dev.zacsweers.metro.Inject

@Inject
public class SrtParser {

  public fun parse(input: String): List<SubtitleCue> {
    return input
      .replace("\r\n", "\n")
      .replace('\r', '\n')
      .split(Regex("\n{2,}"))
      .mapNotNull(::parseBlock)
  }

  private fun parseBlock(block: String): SubtitleCue? {
    val lines = block.lines()
      .map(String::trimEnd)
      .dropWhile(String::isBlank)
      .dropLastWhile(String::isBlank)

    val timingIndex = lines.indexOfFirst { "-->" in it }
    if (timingIndex < 0 || timingIndex == lines.lastIndex) return null

    val timing = lines[timingIndex].split("-->", limit = 2)
    val startMs = timing.getOrNull(0)?.trim()?.toMilliseconds() ?: return null
    val endMs = timing.getOrNull(1)
      ?.trim()
      ?.substringBefore(' ')
      ?.toMilliseconds()
      ?: return null
    if (endMs <= startMs) return null

    val text = lines
      .drop(timingIndex + 1)
      .joinToString("\n")
      .trim()
    if (text.isEmpty()) return null

    return SubtitleCue(
      startMs = startMs,
      endMs = endMs,
      text = text,
    )
  }

  private fun String.toMilliseconds(): Long? {
    val parts = split(":", ",")
    if (parts.size != 4) return null

    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].toLongOrNull() ?: return null
    val milliseconds = parts[3].toLongOrNull() ?: return null

    return hours * MILLIS_PER_HOUR +
      minutes * MILLIS_PER_MINUTE +
      seconds * MILLIS_PER_SECOND +
      milliseconds
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1_000L
    const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
  }
}
