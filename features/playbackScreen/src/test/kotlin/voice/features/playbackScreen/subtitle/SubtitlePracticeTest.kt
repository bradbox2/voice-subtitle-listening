package voice.features.playbackScreen.subtitle

import androidx.compose.runtime.collectAsState
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import voice.core.common.DispatcherProvider
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.playback.PlaybackMode
import voice.core.playback.PlaybackModeStore
import voice.core.playback.PlayerController
import voice.core.subtitle.SubtitleCue
import voice.core.subtitle.SubtitleLoader
import voice.features.playbackScreen.MemoryDataStore

class SubtitlePracticeTest {

  private val scope = TestScope()
  private val bookId = BookId("book")
  private val chapterId = ChapterId("file:///sdcard/Books/one.mp3")
  private val cues =
    listOf(
      SubtitleCue(startMs = 1_000, endMs = 2_000, text = "one"),
      SubtitleCue(startMs = 2_000, endMs = 3_000, text = "two"),
      SubtitleCue(startMs = 5_000, endMs = 6_000, text = "three"),
    )
  private val player =
    mockk<PlayerController> {
      every { setPosition(any(), any(), any(), any()) } just Runs
      every { playFrom(any(), any()) } just Runs
      every { setLoopRange(any(), any(), any()) } just Runs
      every { clearLoopRange() } just Runs
    }
  private val offsets = MemoryDataStore<Map<String, Long>>(emptyMap())
  private val position = MutableStateFlow(1_500L)
  private val practice = subtitlePractice(cues)

  @Test
  fun `no subtitles without cues`() = scope.runTest {
    val practice = subtitlePractice(emptyList())
    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        practice.viewState(bookId, chapterId, positionMs = 0, playbackSpeed = 1F)
      }
      .test {
        assertNull(awaitItem())
        expectNoEvents()
      }
  }

  @Test
  fun `highlights the cue playing, shifted by the offset`() = scope.runTest {
    offsets.updateData { mapOf(subtitleOffsetKey(bookId, chapterId) to 1_000L) }
    position.value = 2_500

    viewStates {
      val state = awaitLoaded()
      assertEquals(expected = 0, actual = state.activeIndex)
      assertEquals(expected = 1_000, actual = state.offsetMs)
    }
  }

  @Test
  fun `tapping a cue plays from its start in the audio, also when it was paused`() = scope.runTest {
    offsets.updateData { mapOf(subtitleOffsetKey(bookId, chapterId) to -300L) }
    viewStates {
      awaitLoaded()

      practice.onCueClick(2_000)

      verify { player.playFrom(1_700, chapterId) }
      verify(exactly = 0) { player.setPosition(any(), any(), any(), any()) }
      assertEquals(expected = 1, actual = awaitLoaded().selectedIndex)
    }
  }

  @Test
  fun `repeating loops the sentence playing in the audio, until it is turned off`() =
    scope.runTest {
      offsets.updateData { mapOf(subtitleOffsetKey(bookId, chapterId) to 200L) }
      position.value = 1_500
      viewStates {
        awaitLoaded()

        practice.toggleRepeatSentence()
        runCurrent()
        verify { player.setLoopRange(chapterId, startMs = 1_200, endMs = 2_200) }

        practice.toggleRepeatSentence()
        runCurrent()
        verify { player.clearLoopRange() }
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `tapping another sentence while repeating loops that one`() = scope.runTest {
    viewStates {
      awaitLoaded()
      practice.toggleRepeatSentence()
      runCurrent()

      practice.onCueClick(5_000)
      position.value = 5_000
      runCurrent()

      verify { player.setLoopRange(chapterId, startMs = 5_000, endMs = 6_000) }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `offsets change per chapter and reset to none`() = scope.runTest {
    viewStates {
      awaitLoaded()

      practice.onOffsetChange(SUBTITLE_OFFSET_STEP_MS)
      practice.onOffsetChange(SUBTITLE_OFFSET_STEP_MS)
      runCurrent()
      assertEquals(
        expected = mapOf(subtitleOffsetKey(bookId, chapterId) to 200L),
        actual = offsets.data.first(),
      )

      practice.onOffsetReset()
      runCurrent()
      assertEquals(expected = emptyMap(), actual = offsets.data.first())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `the loop stays on the tapped sentence near it and follows a seek elsewhere`() {
    assertEquals(cues[0], cues.loopCue(subtitleTimeMs = 2_400, selectedStartMs = 1_000))
    assertEquals(cues[2], cues.loopCue(subtitleTimeMs = 5_500, selectedStartMs = 1_000))
    assertEquals(cues[1], cues.loopCue(subtitleTimeMs = 2_500, selectedStartMs = null))
    assertNull(cues.loopCue(subtitleTimeMs = 4_000, selectedStartMs = null))
  }

  private suspend fun TestScope.viewStates(
    validate: suspend ReceiveTurbine<SubtitlesViewState?>.() -> Unit,
  ) {
    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        practice.viewState(
          bookId = bookId,
          chapterId = chapterId,
          positionMs = position.collectAsState().value,
          playbackSpeed = 1F,
        )
      }
      .test(validate = validate)
  }

  @IgnorableReturnValue
  private suspend fun ReceiveTurbine<SubtitlesViewState?>.awaitLoaded(): SubtitlesViewState {
    var item = awaitItem()
    while (item == null || item.offsetMs != offsets.data.first().values.firstOrNull() ?: 0L) {
      item = awaitItem()
    }
    return item
  }

  private fun subtitlePractice(cues: List<SubtitleCue>): SubtitlePractice {
    return SubtitlePractice(
      player = player,
      subtitleLoader = mockk<SubtitleLoader> { every { loadForAudio(any()) } returns cues },
      dispatcherProvider =
        DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
      focusModeStore = SubtitleFocusModeStore(MemoryDataStore(true)),
      starredCuesStore = StarredSubtitleCuesStore(MemoryDataStore(emptySet())),
      offsetsStore = SubtitleOffsetsStore(offsets),
      playbackModeStore = PlaybackModeStore(MemoryDataStore(PlaybackMode.Sequential)),
    )
  }
}

internal fun noSubtitles(): SubtitlePractice {
  return SubtitlePractice(
    player = mockk(),
    subtitleLoader = mockk<SubtitleLoader> { every { loadForAudio(any()) } returns emptyList() },
    dispatcherProvider =
      DispatcherProvider(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined),
    focusModeStore = SubtitleFocusModeStore(MemoryDataStore(true)),
    starredCuesStore = StarredSubtitleCuesStore(MemoryDataStore(emptySet())),
    offsetsStore = SubtitleOffsetsStore(MemoryDataStore(emptyMap())),
    playbackModeStore = PlaybackModeStore(MemoryDataStore(PlaybackMode.Sequential)),
  )
}
