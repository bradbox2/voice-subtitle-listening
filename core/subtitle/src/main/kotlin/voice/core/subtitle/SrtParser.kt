package voice.core.subtitle

import dev.zacsweers.metro.Inject

@Inject
class SrtParser {

  fun parse(input: String): List<SubtitleCue> {
    return input
      .removePrefix("﻿")
      .replace("\r\n", "\n")
      .replace('\r', '\n')
      .split(Regex("\n\\s*\n"))
      .mapNotNull(::parseBlock)
      .sortedBy { it.startMs }
  }

  private fun parseBlock(block: String): SubtitleCue? {
    val lines = block.lines().map(String::trimEnd).dropWhile(String::isBlank)
    val timingIndex = lines.indexOfFirst { "-->" in it }
    if (timingIndex < 0) return null

    val (start, end) = lines[timingIndex].split("-->", limit = 2)
    val startMs = start.trim().toMilliseconds() ?: return null
    val endMs = end.trim().substringBefore(' ').toMilliseconds() ?: return null
    if (endMs <= startMs) return null

    val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
    if (text.isEmpty()) return null

    return SubtitleCue(startMs = startMs, endMs = endMs, text = text)
  }

  private fun String.toMilliseconds(): Long? {
    val parts = split(':', ',', '.')
    if (parts.size != 4) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].toLongOrNull() ?: return null
    val millis = parts[3].padEnd(3, '0').take(3).toLongOrNull() ?: return null
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
  }
}
