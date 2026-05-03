package voice.core.playback.subtitle

public data class SubtitleCue(
  val startMs: Long,
  val endMs: Long,
  val text: String,
)

public fun List<SubtitleCue>.activeCueIndex(positionMs: Long): Int? {
  val index = indexOfFirst { cue ->
    positionMs >= cue.startMs && positionMs < cue.endMs
  }
  return index.takeIf { it >= 0 }
}
