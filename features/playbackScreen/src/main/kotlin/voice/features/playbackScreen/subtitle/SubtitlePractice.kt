package voice.features.playbackScreen.subtitle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.playback.PlaybackMode
import voice.core.playback.PlaybackModeStore
import voice.core.playback.PlayerController
import voice.core.subtitle.SubtitleCue
import voice.core.subtitle.SubtitleLoader
import voice.core.subtitle.activeCueIndex

/**
 * Subtitle positions are in the time of the subtitle file. The audio is that plus the listener's
 * offset for the chapter.
 */
@Inject
class SubtitlePractice(
  private val player: PlayerController,
  private val subtitleLoader: SubtitleLoader,
  private val dispatcherProvider: DispatcherProvider,
  private val focusModeStore: SubtitleFocusModeStore,
  private val starredCuesStore: StarredSubtitleCuesStore,
  private val offsetsStore: SubtitleOffsetsStore,
  private val playbackModeStore: PlaybackModeStore,
) {

  private val scope = MainScope(dispatcherProvider)
  private val repeatSentence = mutableStateOf(false)
  private val selectedCueStartMs = mutableStateOf<Long?>(null)
  private var loopRangeSet = false
  private var current: Current? = null

  /** Whether the chapter that plays has subtitles. */
  val active: Boolean
    get() = current != null

  @Composable
  fun viewState(
    bookId: BookId,
    chapterId: ChapterId,
    positionMs: Long,
    playbackSpeed: Float,
  ): SubtitlesViewState? {
    val cues =
      produceState(initialValue = emptyList<SubtitleCue>(), chapterId) {
          value = emptyList()
          value = withContext(dispatcherProvider.io) { subtitleLoader.loadForAudio(chapterId) }
        }
        .value
    LaunchedEffect(chapterId) {
      repeatSentence.value = false
      selectedCueStartMs.value = null
    }
    val offsets = remember { offsetsStore.data }.collectAsState(initial = emptyMap()).value
    val offsetMs = offsets[subtitleOffsetKey(bookId, chapterId)] ?: 0L
    val subtitleTimeMs = positionMs - offsetMs
    val repeat = repeatSentence.value
    val loopCue = if (repeat) cues.loopCue(subtitleTimeMs, selectedCueStartMs.value) else null
    LaunchedEffect(loopCue, offsetMs, chapterId) { applyLoop(chapterId, loopCue, offsetMs) }

    current =
      if (cues.isEmpty()) {
        null
      } else {
        Current(bookId = bookId, chapterId = chapterId, cues = cues, offsetMs = offsetMs)
      }
    if (cues.isEmpty()) return null

    val focusMode = remember { focusModeStore.data }.collectAsState(initial = true).value
    val playbackMode = remember {
      playbackModeStore.data
    }
      .collectAsState(initial = PlaybackMode.Sequential)
      .value
    val starred = remember { starredCuesStore.data }.collectAsState(initial = emptySet()).value
    val cueViewStates =
      remember(cues, starred, bookId, chapterId) {
        cues.map { cue ->
          val key = cue.starKey(bookId, chapterId)
          SubtitlesViewState.Cue(
            startMs = cue.startMs,
            text = cue.text,
            starKey = key,
            starred = key in starred,
          )
        }
      }
    val selectedStartMs = loopCue?.startMs ?: selectedCueStartMs.value
    return SubtitlesViewState(
      focusMode = focusMode,
      repeatSentence = repeat,
      playbackMode = playbackMode,
      cues = cueViewStates,
      activeIndex = cues.activeCueIndex(subtitleTimeMs),
      selectedIndex =
        selectedStartMs
          ?.let { start -> cues.indexOfFirst { it.startMs == start } }
          ?.takeIf { it >= 0 },
      offsetMs = offsetMs,
      speedOptions =
        SUBTITLE_PLAYBACK_SPEEDS.map { speed ->
          SubtitlesViewState.SpeedOption(speed = speed, selected = speed == playbackSpeed)
        },
    )
  }

  private fun applyLoop(
    chapterId: ChapterId,
    loopCue: SubtitleCue?,
    offsetMs: Long,
  ) {
    if (loopCue != null) {
      selectedCueStartMs.value = loopCue.startMs
      player.setLoopRange(
        chapterId = chapterId,
        startMs = (loopCue.startMs + offsetMs).coerceAtLeast(0),
        endMs = loopCue.endMs + offsetMs,
      )
      loopRangeSet = true
    } else if (loopRangeSet) {
      player.clearLoopRange()
      loopRangeSet = false
    }
  }

  fun onCueClick(startMs: Long) {
    val current = current ?: return
    selectedCueStartMs.value = startMs
    player.setPosition((startMs + current.offsetMs).coerceAtLeast(0), current.chapterId)
  }

  fun toggleRepeatSentence() {
    repeatSentence.value = !repeatSentence.value
  }

  fun cyclePlaybackMode() {
    scope.launch {
      val next =
        when (playbackModeStore.data.first()) {
          PlaybackMode.Sequential -> PlaybackMode.SingleTrackLoop
          PlaybackMode.SingleTrackLoop -> PlaybackMode.Shuffle
          PlaybackMode.Shuffle -> PlaybackMode.FolderLoop
          PlaybackMode.FolderLoop -> PlaybackMode.Sequential
        }
      playbackModeStore.updateData { next }
      player.setPlaybackMode(next)
    }
  }

  fun toggleFocusMode() {
    scope.launch { focusModeStore.updateData { !it } }
  }

  fun onStarClick(starKey: String) {
    scope.launch {
      starredCuesStore.updateData { keys ->
        if (starKey in keys) keys - starKey else keys + starKey
      }
    }
  }

  fun onSpeedClick(speed: Float) {
    player.setSpeed(speed)
  }

  fun onOffsetChange(deltaMs: Long) {
    val current = current ?: return
    val key = subtitleOffsetKey(current.bookId, current.chapterId)
    scope.launch {
      offsetsStore.updateData { offsets ->
        val updated = adjustSubtitleOffset(offsets[key] ?: 0L, deltaMs)
        if (updated == 0L) offsets - key else offsets + (key to updated)
      }
    }
  }

  fun onOffsetReset() {
    val current = current ?: return
    val key = subtitleOffsetKey(current.bookId, current.chapterId)
    scope.launch { offsetsStore.updateData { it - key } }
  }

  private data class Current(
    val bookId: BookId,
    val chapterId: ChapterId,
    val cues: List<SubtitleCue>,
    val offsetMs: Long,
  )
}

/**
 * The tapped sentence keeps looping while playback is around it. After a seek somewhere else, the
 * loop follows to the sentence playing there.
 */
internal fun List<SubtitleCue>.loopCue(
  subtitleTimeMs: Long,
  selectedStartMs: Long?,
): SubtitleCue? {
  val selected = selectedStartMs?.let { start -> firstOrNull { it.startMs == start } }
  if (
    selected != null &&
      subtitleTimeMs in
        (selected.startMs - LOOP_FOLLOW_TOLERANCE_MS)..(selected.endMs + LOOP_FOLLOW_TOLERANCE_MS)
  ) {
    return selected
  }
  return activeCueIndex(subtitleTimeMs)?.let(::get)
}

private const val LOOP_FOLLOW_TOLERANCE_MS = 1_000L
private val SUBTITLE_PLAYBACK_SPEEDS = listOf(0.75F, 1.0F, 1.25F)
