package voice.features.playbackScreen

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.MarkData
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.toUri
import voice.core.featureflag.MemoryFeatureFlag
import voice.core.playback.CurrentBookResolver
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.subtitle.SubtitleCue
import voice.core.playback.subtitle.SubtitleLoader
import voice.core.scanner.MediaScanTrigger
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerMode.TimedWithDuration
import voice.core.sleeptimer.SleepTimerState
import voice.features.sleepTimer.SleepTimerViewState
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BookPlayViewModelTest {

  private val scope = TestScope()
  private val sleepTimerDataStore = MemoryDataStore(SleepTimerPreference.Default.copy(duration = 5.minutes))
  private val subtitleFocusModeEnabledStore = MemoryDataStore(true)
  private val starredSubtitleCueKeysStore = MemoryDataStore(emptySet<String>())
  private val book = book()
  private val sleepTimer = mockk<SleepTimer> {
    val stateFlow = MutableStateFlow<SleepTimerState>(SleepTimerState.Disabled)
    every {
      state
    } returns stateFlow
    every {
      enable(any())
    } answers {
      stateFlow.value = when (val mode = firstArg<SleepTimerMode>()) {
        is TimedWithDuration -> SleepTimerState.Enabled.WithDuration(mode.duration)
        SleepTimerMode.TimedWithDefault -> SleepTimerState.Enabled.WithDuration(runBlocking { sleepTimerDataStore.data.first() }.duration)
        SleepTimerMode.EndOfChapter -> SleepTimerState.Enabled.WithEndOfChapter
      }
    }
    every {
      disable()
    } answers {
      stateFlow.value = SleepTimerState.Disabled
    }
  }

  private val player = mockk<PlayerController>()
  private val playStateManager = mockk<PlayStateManager> {
    every { flow } returns MutableStateFlow(PlayStateManager.PlayState.Paused)
  }
  private val subtitleLoader = mockk<SubtitleLoader> {
    every { loadForAudio(any()) } returns emptyList()
  }
  private val mediaScanTrigger = mockk<MediaScanTrigger> {
    every { scan(any()) } just Runs
  }
  private val currentBookStoreId = MemoryDataStore<BookId?>(null)
  private val currentBookResolver = mockk<CurrentBookResolver> {
    coEvery { book(book.id) } returns book
  }
  private val viewModel = BookPlayViewModel(
    bookRepository = mockk {
      coEvery { get(book.id) } returns book
      every { flow(book.id) } returns MutableStateFlow(book)
    },
    currentBookResolver = currentBookResolver,
    player = player.apply {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
      every { livePlaybackStateFlow(book.id) } returns MutableStateFlow(null)
    },
    sleepTimer = sleepTimer,
    playStateManager = playStateManager,
    currentBookStoreId = currentBookStoreId,
    navigator = mockk(),
    bookmarkRepository = mockk {
      coEvery { addBookmarkAtBookPosition(book, any(), any()) } returns Bookmark(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        addedAt = Instant.now(),
        setBySleepTimer = true,
        id = Bookmark.Id(UUID.randomUUID()),
        time = 0L,
        title = null,
      )
    },
    volumeGainFormatter = mockk(),
    batteryOptimization = mockk(),
    subtitleLoader = subtitleLoader,
    mediaScanTrigger = mediaScanTrigger,
    sleepTimerPreferenceStore = sleepTimerDataStore,
    subtitleFocusModeEnabledStore = subtitleFocusModeEnabledStore,
    starredSubtitleCueKeysStore = starredSubtitleCueKeysStore,
    bookId = book.id,
    dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
    experimentalPlaybackPersistenceFeatureFlag = MemoryFeatureFlag(false),
  )

  @Test
  fun sleepTimerValueChanging() = scope.runTest {
    fun assertDialogSleepTime(expected: Int) {
      viewModel.dialogState.value shouldBe BookPlayDialogViewState.SleepTimer(SleepTimerViewState(expected))
    }

    viewModel.toggleSleepTimer()
    yield()
    assertDialogSleepTime(5)

    suspend fun incrementAndAssert(time: Int) {
      viewModel.incrementSleepTime()
      yield()
      assertDialogSleepTime(time)
    }

    suspend fun decrementAndAssert(time: Int) {
      viewModel.decrementSleepTime()
      yield()
      assertDialogSleepTime(time)
    }

    decrementAndAssert(4)
    decrementAndAssert(3)
    decrementAndAssert(2)
    decrementAndAssert(1)

    decrementAndAssert(1)

    incrementAndAssert(2)
    incrementAndAssert(3)
  }

  @Test
  fun sleepTimerSettingFixedValue() = scope.runTest {
    viewModel.toggleSleepTimer()
    viewModel.onAcceptSleepTime(10)
    sleepTimerDataStore.data.first().duration shouldBe 5.minutes
    yield()
    verify(exactly = 1) {
      sleepTimer.enable(TimedWithDuration(10.minutes))
    }
  }

  @Test
  fun deactivateSleepTimer() = scope.runTest {
    viewModel.toggleSleepTimer()
    viewModel.onAcceptSleepTime(10)
    viewModel.toggleSleepTimer()
    yield()
    verifyOrder {
      sleepTimer.enable(TimedWithDuration(10.minutes))
      sleepTimer.disable()
    }
    sleepTimer.state.value.shouldBeInstanceOf<SleepTimerState.Disabled>()
  }

  @Test
  fun onCurrentChapterClickShowsDialogWithCorrectState() = scope.runTest {
    viewModel.onCurrentChapterClick()
    yield()

    val dialogState = viewModel.dialogState.value
      .shouldBeInstanceOf<BookPlayDialogViewState.SelectChapterDialog>()

    dialogState.items.shouldContainExactly(
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 1,
        name = "Chapter Start",
        active = false,
        time = "0:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 2,
        name = "Middle Section",
        active = false,
        time = "2:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 3,
        name = "Final Section",
        active = false,
        time = "4:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 4,
        name = "Chapter Start",
        active = false,
        time = "5:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 5,
        name = "Middle Section",
        active = true,
        time = "7:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 6,
        name = "Final Section",
        active = false,
        time = "9:00",
      ),
    )
  }

  @Test
  fun onChapterClickSetsPositionAndDismissesDialog() = scope.runTest {
    every { player.setPosition(any(), any()) } just Runs

    viewModel.onCurrentChapterClick()
    yield()

    viewModel.dialogState.value.shouldBeInstanceOf<BookPlayDialogViewState.SelectChapterDialog>()

    viewModel.onChapterClick(number = 2)
    yield()

    // Verify player.setPosition was called with correct parameters
    // The second mark starts at 2 minutes position in the first chapter
    verify(exactly = 1) {
      player.setPosition(time = 2.minutes.inWholeMilliseconds, id = book.chapters.first().id)
    }

    viewModel.dialogState.value shouldBe null
  }

  @Test
  fun `overlay prefers live controller position`() {
    val persistedBook = book()
    val overlaidBook = persistedBook.overlay(
      LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = persistedBook.chapters.first().id,
        positionMs = 1.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      ),
    )

    overlaidBook.currentChapter.id shouldBe persistedBook.chapters.first().id
    overlaidBook.content.positionInChapter shouldBe 1.minutes.inWholeMilliseconds
  }

  @Test
  fun `viewState prefers live playback state when feature flag is enabled`() = scope.runTest {
    val persistedBook = book()
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(null)
    val viewModel = viewModel(
      book = persistedBook,
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      awaitItem()!!.playedTime shouldBe 30.seconds

      livePlaybackFlow.value = LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = persistedBook.chapters.first().id,
        positionMs = 1.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      )

      val state = awaitItem()!!
      state.playing shouldBe true
      state.chapterName shouldBe "Chapter Start"
      state.playedTime shouldBe 1.minutes
    }
  }

  @Test
  fun `viewState falls back to manager play state when live playback is unavailable`() = scope.runTest {
    val viewModel = viewModel(
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = MutableStateFlow(null),
      playStateFlow = MutableStateFlow(PlayStateManager.PlayState.Playing),
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      val state = awaitItem()!!
      state.playing shouldBe true
      state.playedTime shouldBe 30.seconds
    }
  }

  @Test
  fun `viewState shows parsed subtitles and marks active cue`() = scope.runTest {
    val cues = listOf(
      SubtitleCue(
        startMs = 2.minutes.inWholeMilliseconds,
        endMs = 3.minutes.inWholeMilliseconds,
        text = "Active sentence",
      ),
      SubtitleCue(
        startMs = 3.minutes.inWholeMilliseconds,
        endMs = 4.minutes.inWholeMilliseconds,
        text = "Next sentence",
      ),
    )
    val viewModel = viewModel(
      subtitleLoader = mockk {
        every { loadForAudio(book.currentChapter.id.toUri()) } returns cues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      var state: BookPlayViewState?
      do {
        state = awaitItem()
      } while (state?.subtitles == null)

      state.subtitles shouldBe BookPlayViewState.SubtitlePanelViewState(
        visible = true,
        repeatSentenceEnabled = false,
        activeIndex = 0,
        speedOptions = listOf(
          BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 0.75F, selected = false),
          BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 1.0F, selected = true),
          BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 1.25F, selected = false),
        ),
        items = listOf(
          BookPlayViewState.SubtitlePanelViewState.Item(
            startMs = 2.minutes.inWholeMilliseconds,
            starKey = cues[0].subtitleCueKey(book.id, book.currentChapter.id),
            text = "Active sentence",
            active = true,
            starred = false,
          ),
          BookPlayViewState.SubtitlePanelViewState.Item(
            startMs = 3.minutes.inWholeMilliseconds,
            starKey = cues[1].subtitleCueKey(book.id, book.currentChapter.id),
            text = "Next sentence",
            active = false,
            starred = false,
          ),
        ),
      )
    }
  }

  @Test
  fun `subtitles use live player position when playback persistence is disabled`() = scope.runTest {
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(
      LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 1.5.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1.25F,
      ),
    )
    val cues = listOf(
      SubtitleCue(
        startMs = 1.minutes.inWholeMilliseconds,
        endMs = 2.minutes.inWholeMilliseconds,
        text = "Live cue",
      ),
      SubtitleCue(
        startMs = 2.minutes.inWholeMilliseconds,
        endMs = 3.minutes.inWholeMilliseconds,
        text = "Persisted cue",
      ),
    )
    val viewModel = viewModel(
      experimentalPlaybackPersistence = false,
      livePlaybackFlow = livePlaybackFlow,
      subtitleLoader = mockk {
        every { loadForAudio(any()) } returns cues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      val state = awaitStateWithSubtitles()

      state.subtitles!!.activeIndex shouldBe 0
      state.subtitles.speedOptions shouldContainExactly listOf(
        BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 0.75F, selected = false),
        BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 1.0F, selected = false),
        BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 1.25F, selected = true),
      )
    }
  }

  @Test
  fun `subtitles use live player position instead of stale persisted position`() = scope.runTest {
    val persistedBook = book(positionInChapter = 2.5.minutes.inWholeMilliseconds)
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(
      LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = persistedBook.currentChapter.id,
        positionMs = 1.5.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      ),
    )
    val cues = listOf(
      SubtitleCue(
        startMs = 1.minutes.inWholeMilliseconds,
        endMs = 2.minutes.inWholeMilliseconds,
        text = "Live position cue",
      ),
      SubtitleCue(
        startMs = 2.minutes.inWholeMilliseconds,
        endMs = 3.minutes.inWholeMilliseconds,
        text = "Persisted position cue",
      ),
    )
    val viewModel = viewModel(
      book = persistedBook,
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
      subtitleLoader = mockk {
        every { loadForAudio(persistedBook.currentChapter.id.toUri()) } returns cues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      val state = awaitStateWithSubtitles()

      state.subtitles!!.activeIndex shouldBe 0
      state.subtitles.items[0].active shouldBe true
      state.subtitles.items[1].active shouldBe false
    }
  }

  @Test
  fun `subtitles use live chapter when it differs from persisted current chapter`() = scope.runTest {
    val persistedBook = book(currentChapterIndex = 1, positionInChapter = 2.5.minutes.inWholeMilliseconds)
    val liveChapter = persistedBook.chapters.first()
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(
      LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = liveChapter.id,
        positionMs = 1.5.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      ),
    )
    val liveChapterCues = listOf(
      SubtitleCue(
        startMs = 1.minutes.inWholeMilliseconds,
        endMs = 2.minutes.inWholeMilliseconds,
        text = "Live chapter cue",
      ),
    )
    val viewModel = viewModel(
      book = persistedBook,
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
      subtitleLoader = mockk {
        every { loadForAudio(any()) } returns liveChapterCues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      val state = awaitStateWithSubtitles()

      state.subtitles!!.activeIndex shouldBe 0
      state.subtitles.items.single().starKey shouldBe liveChapterCues.single().subtitleCueKey(
        bookId = persistedBook.id,
        chapterId = liveChapter.id,
      )
    }
  }

  @Test
  fun `subtitles keep last valid chapter when live chapter is temporarily unknown`() = scope.runTest {
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(
      LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 1.5.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      ),
    )
    val cues = listOf(
      SubtitleCue(
        startMs = 1.minutes.inWholeMilliseconds,
        endMs = 2.minutes.inWholeMilliseconds,
        text = "Keep me visible",
      ),
    )
    val viewModel = viewModel(
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
      subtitleLoader = mockk {
        every { loadForAudio(book.currentChapter.id.toUri()) } returns cues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      val validState = awaitStateWithSubtitles()
      validState.subtitles!!.items.single().text shouldBe "Keep me visible"
      validState.subtitles.activeIndex shouldBe 0

      livePlaybackFlow.value = LivePlaybackState(
        bookId = book.id,
        chapterId = ChapterId("http://missing-live-chapter"),
        positionMs = 4.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      )

      val frozenState = awaitItem()!!
      frozenState.subtitles!!.items.single().text shouldBe "Keep me visible"
      frozenState.subtitles.activeIndex shouldBe 0
    }
  }

  @Test
  fun `onSubtitleClick seeks using active live chapter instead of persisted current chapter`() = scope.runTest {
    val persistedBook = book(currentChapterIndex = 1)
    val liveChapter = persistedBook.chapters.first()
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(
      LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = liveChapter.id,
        positionMs = 1.5.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      ),
    )
    val player = mockk<PlayerController> {
      every { pauseIfCurrentBookDifferentFrom(persistedBook.id) } just Runs
      every { livePlaybackStateFlow(persistedBook.id) } returns livePlaybackFlow
      every { setPosition(any(), any()) } just Runs
    }
    val viewModel = viewModel(
      book = persistedBook,
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
      player = player,
      subtitleLoader = mockk {
        every { loadForAudio(liveChapter.id.toUri()) } returns listOf(
          SubtitleCue(
            startMs = 1.minutes.inWholeMilliseconds,
            endMs = 2.minutes.inWholeMilliseconds,
            text = "Tap me",
          ),
        )
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      awaitStateWithSubtitles().subtitles!!.items.single().text shouldBe "Tap me"

      viewModel.onSubtitleClick(1.minutes.inWholeMilliseconds)
      yield()

      verify(exactly = 1) {
        player.setPosition(
          time = 1.minutes.inWholeMilliseconds,
          id = liveChapter.id,
        )
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `subtitle cue lookup does not scale live position by playback speed`() = scope.runTest {
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(
      LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 160_000L,
        isPlaying = true,
        playbackSpeed = 1.25F,
      ),
    )
    val cues = listOf(
      SubtitleCue(
        startMs = 150_000L,
        endMs = 170_000L,
        text = "Unscaled cue",
      ),
      SubtitleCue(
        startMs = 195_000L,
        endMs = 205_000L,
        text = "Scaled cue",
      ),
    )
    val viewModel = viewModel(
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
      subtitleLoader = mockk {
        every { loadForAudio(book.currentChapter.id.toUri()) } returns cues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      val state = awaitStateWithSubtitles()

      state.subtitles!!.activeIndex shouldBe 0
      state.subtitles.speedOptions shouldContainExactly listOf(
        BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 0.75F, selected = false),
        BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 1.0F, selected = false),
        BookPlayViewState.SubtitlePanelViewState.SpeedOption(speed = 1.25F, selected = true),
      )
    }
  }

  @Test
  fun `onSubtitleClick seeks exactly to cue start by default`() = scope.runTest {
    val player = mockk<PlayerController> {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
      every { setPosition(any(), any()) } just Runs
    }
    val viewModel = viewModel(player = player)

    viewModel.onSubtitleClick(2.minutes.inWholeMilliseconds)
    yield()

    verify(exactly = 1) {
      player.setPosition(
        time = 2.minutes.inWholeMilliseconds,
        id = book.currentChapter.id,
      )
    }
  }

  @Test
  fun `onSubtitleClick preserves near-start cue time when pre-roll is zero`() = scope.runTest {
    val player = mockk<PlayerController> {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
      every { setPosition(any(), any()) } just Runs
    }
    val viewModel = viewModel(player = player)

    viewModel.onSubtitleClick(100)
    yield()

    verify(exactly = 1) {
      player.setPosition(
        time = 100,
        id = book.currentChapter.id,
      )
    }
  }

  @Test
  fun `repeat sentence seeks to selected cue start when playback reaches cue end`() = scope.runTest {
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(null)
    val player = mockk<PlayerController> {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
      every { livePlaybackStateFlow(book.id) } returns livePlaybackFlow
      every { setPosition(any(), any()) } just Runs
    }
    val cues = listOf(
      SubtitleCue(
        startMs = 2.minutes.inWholeMilliseconds,
        endMs = 3.minutes.inWholeMilliseconds,
        text = "Loop me",
      ),
    )
    val viewModel = viewModel(
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
      player = player,
      subtitleLoader = mockk {
        every { loadForAudio(book.currentChapter.id.toUri()) } returns cues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      livePlaybackFlow.value = LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 2.5.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      )
      var state: BookPlayViewState?
      do {
        state = awaitItem()
      } while (state?.subtitles == null)

      viewModel.toggleRepeatSentence()
      livePlaybackFlow.value = LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 3.minutes.inWholeMilliseconds - 80,
        isPlaying = true,
        playbackSpeed = 1F,
      )
      awaitItem()

      verify(exactly = 1) {
        player.setPosition(
          time = 2.minutes.inWholeMilliseconds,
          id = book.currentChapter.id,
        )
      }

      livePlaybackFlow.value = LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 3.minutes.inWholeMilliseconds - 70,
        isPlaying = true,
        playbackSpeed = 1F,
      )
      awaitItem()

      verify(exactly = 1) {
        player.setPosition(
          time = 2.minutes.inWholeMilliseconds,
          id = book.currentChapter.id,
        )
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `subtitle click while repeat sentence is enabled selects and loops tapped cue`() = scope.runTest {
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(null)
    val player = mockk<PlayerController> {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
      every { livePlaybackStateFlow(book.id) } returns livePlaybackFlow
      every { setPosition(any(), any()) } just Runs
    }
    val cues = listOf(
      SubtitleCue(
        startMs = 2.minutes.inWholeMilliseconds,
        endMs = 3.minutes.inWholeMilliseconds,
        text = "First",
      ),
      SubtitleCue(
        startMs = 4.minutes.inWholeMilliseconds,
        endMs = 5.minutes.inWholeMilliseconds,
        text = "Second",
      ),
    )
    val viewModel = viewModel(
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
      player = player,
      subtitleLoader = mockk {
        every { loadForAudio(book.currentChapter.id.toUri()) } returns cues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      var state: BookPlayViewState?
      do {
        state = awaitItem()
      } while (state?.subtitles == null)

      viewModel.toggleRepeatSentence()
      viewModel.onSubtitleClick(4.minutes.inWholeMilliseconds)
      yield()

      verify(exactly = 1) {
        player.setPosition(
          time = 4.minutes.inWholeMilliseconds,
          id = book.currentChapter.id,
        )
      }

      livePlaybackFlow.value = LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 5.minutes.inWholeMilliseconds - 80,
        isPlaying = true,
        playbackSpeed = 1F,
      )
      awaitItem()

      verify(exactly = 2) {
        player.setPosition(
          time = 4.minutes.inWholeMilliseconds,
          id = book.currentChapter.id,
        )
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `repeat sentence keeps tapped cue selected from exact cue start`() = scope.runTest {
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(null)
    val player = mockk<PlayerController> {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
      every { livePlaybackStateFlow(book.id) } returns livePlaybackFlow
      every { setPosition(any(), any()) } just Runs
    }
    val cues = listOf(
      SubtitleCue(
        startMs = 2.minutes.inWholeMilliseconds,
        endMs = 4.minutes.inWholeMilliseconds,
        text = "Previous",
      ),
      SubtitleCue(
        startMs = 4.minutes.inWholeMilliseconds,
        endMs = 5.minutes.inWholeMilliseconds,
        text = "Tapped",
      ),
    )
    val viewModel = viewModel(
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
      player = player,
      subtitleLoader = mockk {
        every { loadForAudio(book.currentChapter.id.toUri()) } returns cues
      },
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      var state: BookPlayViewState?
      do {
        state = awaitItem()
      } while (state?.subtitles == null)

      viewModel.toggleRepeatSentence()
      viewModel.onSubtitleClick(4.minutes.inWholeMilliseconds)
      yield()

      livePlaybackFlow.value = LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 4.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      )
      awaitItem()

      verify(exactly = 0) {
        player.setPosition(
          time = 2.minutes.inWholeMilliseconds,
          id = book.currentChapter.id,
        )
      }

      livePlaybackFlow.value = LivePlaybackState(
        bookId = book.id,
        chapterId = book.currentChapter.id,
        positionMs = 5.minutes.inWholeMilliseconds - 80,
        isPlaying = true,
        playbackSpeed = 1F,
      )
      awaitItem()

      verify(exactly = 2) {
        player.setPosition(
          time = 4.minutes.inWholeMilliseconds,
          id = book.currentChapter.id,
        )
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `toggleSubtitleFocusMode persists subtitle focus mode`() = scope.runTest {
    val store = MemoryDataStore(true)
    val viewModel = viewModel(subtitleFocusModeEnabledStore = store)

    viewModel.toggleSubtitleFocusMode()
    yield()
    store.data.first() shouldBe false

    viewModel.toggleSubtitleFocusMode()
    yield()
    store.data.first() shouldBe true
  }

  @Test
  fun `subtitle star click persists starred key`() = scope.runTest {
    val starKey = "book|chapter|2000|3000|hash"
    val starStore = MemoryDataStore(emptySet<String>())
    val viewModel = viewModel(starredSubtitleCueKeysStore = starStore)

    viewModel.onSubtitleStarClick(starKey)
    yield()
    starStore.data.first() shouldBe setOf(starKey)

    viewModel.onSubtitleStarClick(starKey)
    yield()
    starStore.data.first() shouldBe emptySet()
  }

  @Test
  fun `subtitle playback speed chip changes playback speed`() = scope.runTest {
    val player = mockk<PlayerController> {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
      every { setSpeed(any()) } just Runs
    }
    val viewModel = viewModel(player = player)

    viewModel.onSubtitlePlaybackSpeedClick(0.75F)

    verify(exactly = 1) {
      player.setSpeed(0.75F)
    }
  }

  @Test
  fun `refresh subtitles restarts existing media scan`() = scope.runTest {
    val mediaScanTrigger = mockk<MediaScanTrigger> {
      every { scan(any()) } just Runs
    }
    val viewModel = viewModel(mediaScanTrigger = mediaScanTrigger)

    viewModel.refreshSubtitles()

    verify(exactly = 1) {
      mediaScanTrigger.scan(restartIfScanning = true)
    }
  }

  private suspend fun ReceiveTurbine<BookPlayViewState?>.awaitStateWithSubtitles(): BookPlayViewState {
    while (true) {
      val state = awaitItem()
      if (state?.subtitles != null) return state
    }
  }

  private fun viewModel(
    book: Book = this.book,
    experimentalPlaybackPersistence: Boolean = false,
    livePlaybackFlow: MutableStateFlow<LivePlaybackState?> = MutableStateFlow(null),
    playStateFlow: MutableStateFlow<PlayStateManager.PlayState> = MutableStateFlow(PlayStateManager.PlayState.Paused),
    subtitleLoader: SubtitleLoader = this.subtitleLoader,
    mediaScanTrigger: MediaScanTrigger = this.mediaScanTrigger,
    subtitleFocusModeEnabledStore: MemoryDataStore<Boolean> = this.subtitleFocusModeEnabledStore,
    starredSubtitleCueKeysStore: MemoryDataStore<Set<String>> = this.starredSubtitleCueKeysStore,
    player: PlayerController = mockk {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
      every { livePlaybackStateFlow(book.id) } returns livePlaybackFlow
    },
  ): BookPlayViewModel {
    val resolver = mockk<CurrentBookResolver> {
      coEvery { book(book.id) } returns book
    }
    return BookPlayViewModel(
      bookRepository = mockk {
        coEvery { get(book.id) } returns book
        every { flow(book.id) } returns MutableStateFlow(book)
      },
      currentBookResolver = resolver,
      player = player,
      sleepTimer = sleepTimer,
      playStateManager = mockk {
        every { flow } returns playStateFlow
        every { playState } returns playStateFlow.value
      },
      currentBookStoreId = MemoryDataStore(null),
      navigator = mockk(),
      bookmarkRepository = mockk(),
      volumeGainFormatter = mockk(),
      batteryOptimization = mockk(),
      subtitleLoader = subtitleLoader,
      mediaScanTrigger = mediaScanTrigger,
      sleepTimerPreferenceStore = sleepTimerDataStore,
      subtitleFocusModeEnabledStore = subtitleFocusModeEnabledStore,
      starredSubtitleCueKeysStore = starredSubtitleCueKeysStore,
      bookId = book.id,
      dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
      experimentalPlaybackPersistenceFeatureFlag = MemoryFeatureFlag(experimentalPlaybackPersistence),
    )
  }
}

private fun book(
  name: String = "TestBook",
  lastPlayedAtMillis: Long = 0L,
  addedAtMillis: Long = 0L,
  positionInChapter: Long = 2.5.minutes.inWholeMilliseconds,
  currentChapterIndex: Int = 1,
): Book {
  val chapters = listOf(
    chapter(),
    chapter(),
  )
  return Book(
    content = BookContent(
      author = UUID.randomUUID().toString(),
      name = name,
      positionInChapter = positionInChapter,
      playbackSpeed = 1F,
      addedAt = Instant.ofEpochMilli(addedAtMillis),
      chapters = chapters.map { it.id },
      cover = null,
      currentChapter = chapters[currentChapterIndex].id,
      isActive = true,
      lastPlayedAt = Instant.ofEpochMilli(lastPlayedAtMillis),
      skipSilence = false,
      id = BookId(UUID.randomUUID().toString()),
      gain = 0F,
      genre = null,
      narrator = null,
      series = null,
      part = null,
    ),
    chapters = chapters,
  )
}

private fun chapter(): Chapter {
  return Chapter(
    id = ChapterId("http://${UUID.randomUUID()}"),
    duration = 5.minutes.inWholeMilliseconds,
    fileLastModified = Instant.EPOCH,
    markData = listOf(
      MarkData(startMs = 0L, name = "Chapter Start"),
      MarkData(startMs = 2.minutes.inWholeMilliseconds, name = "Middle Section"),
      MarkData(startMs = 4.minutes.inWholeMilliseconds, name = "Final Section"),
    ),
    name = "name",
  )
}
