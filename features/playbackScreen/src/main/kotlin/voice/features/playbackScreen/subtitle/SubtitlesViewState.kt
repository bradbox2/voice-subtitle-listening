package voice.features.playbackScreen.subtitle

import androidx.compose.runtime.Immutable
import voice.core.playback.PlaybackMode

@Immutable
data class SubtitlesViewState(
  /** Files of a book often share the title in their tags, the file name tells them apart. */
  val fileName: String,
  val focusMode: Boolean,
  val repeatSentence: Boolean,
  val playbackMode: PlaybackMode,
  val cues: List<Cue>,
  val activeIndex: Int?,
  val selectedIndex: Int?,
  val offsetMs: Long,
  val speedOptions: List<SpeedOption>,
) {

  @Immutable
  data class Cue(
    val startMs: Long,
    val text: String,
    val starKey: String,
    val starred: Boolean,
  )

  @Immutable data class SpeedOption(val speed: Float, val selected: Boolean)
}
