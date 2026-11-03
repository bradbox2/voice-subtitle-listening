package voice.core.subtitle

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/** Expects the cues sorted by their start, as [SrtParser] returns them. */
fun List<SubtitleCue>.activeCueIndex(positionMs: Long): Int? {
  var low = 0
  var high = lastIndex
  var candidate = -1
  while (low <= high) {
    val mid = (low + high) ushr 1
    if (this[mid].startMs <= positionMs) {
      candidate = mid
      low = mid + 1
    } else {
      high = mid - 1
    }
  }
  return candidate.takeIf { it >= 0 && positionMs < this[it].endMs }
}
