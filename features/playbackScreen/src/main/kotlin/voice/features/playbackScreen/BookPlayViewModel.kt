package voice.features.playbackScreen

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.durationMs
import voice.core.data.markForPosition
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.SleepTimerPreferenceStore
import voice.core.data.store.StarredSubtitleCueKeysStore
import voice.core.data.store.SubtitleFocusModeStore
import voice.core.data.toUri
import voice.core.featureflag.ExperimentalPlaybackPersistenceQualifier
import voice.core.featureflag.FeatureFlag
import voice.core.logging.api.Logger
import voice.core.playback.CurrentBookResolver
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.misc.Decibel
import voice.core.playback.misc.VolumeGain
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.subtitle.SubtitleCue
import voice.core.playback.subtitle.SubtitleLoader
import voice.core.playback.subtitle.activeCueIndex
import voice.core.scanner.MediaScanTrigger
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerMode.TimedWithDuration
import voice.core.sleeptimer.SleepTimerState
import voice.core.ui.ImmutableFile
import voice.core.ui.formatTime
import voice.features.playbackScreen.batteryOptimization.BatteryOptimization
import voice.features.sleepTimer.SleepTimerViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@AssistedInject
class BookPlayViewModel(
  private val bookRepository: BookRepository,
  private val currentBookResolver: CurrentBookResolver,
  private val player: PlayerController,
  private val sleepTimer: SleepTimer,
  private val playStateManager: PlayStateManager,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
  private val navigator: Navigator,
  private val bookmarkRepository: BookmarkRepo,
  private val volumeGainFormatter: VolumeGainFormatter,
  private val batteryOptimization: BatteryOptimization,
  private val subtitleLoader: SubtitleLoader,
  private val mediaScanTrigger: MediaScanTrigger,
  private val dispatcherProvider: DispatcherProvider,
  @SleepTimerPreferenceStore
  private val sleepTimerPreferenceStore: DataStore<SleepTimerPreference>,
  @SubtitleFocusModeStore
  private val subtitleFocusModeEnabledStore: DataStore<Boolean>,
  @StarredSubtitleCueKeysStore
  private val starredSubtitleCueKeysStore: DataStore<Set<String>>,
  @ExperimentalPlaybackPersistenceQualifier
  private val experimentalPlaybackPersistenceFeatureFlag: FeatureFlag<Boolean>,
  @Assisted
  private val bookId: BookId,
) {

  private val scope = MainScope(dispatcherProvider)

  private val _viewEffects = MutableSharedFlow<BookPlayViewEffect>(extraBufferCapacity = 1)
  internal val viewEffects: Flow<BookPlayViewEffect> get() = _viewEffects

  private val _dialogState = mutableStateOf<BookPlayDialogViewState?>(null)
  internal val dialogState: State<BookPlayDialogViewState?> get() = _dialogState
  private val repeatSentenceEnabled = mutableStateOf(false)
  private val selectedSubtitleCueStartMs = mutableStateOf<Long?>(null)
  private val subtitleRefreshKey = mutableIntStateOf(0)
  private var loopSeekPendingForCueStartMs: Long? = null
  private var activePlaybackChapterId: ChapterId? = null
  private var activePlaybackPositionMs: Long? = null
  private var activeSubtitleChapterId: ChapterId? = null

  init {
    scope.launch {
      player.pauseIfCurrentBookDifferentFrom(bookId)
      currentBookStoreId.updateData { bookId }
    }
  }

  @Composable
  fun viewState(): BookPlayViewState? {
    val persistedBook = remember(bookId) {
      bookRepository.flow(bookId).filterNotNull()
    }.collectAsState(initial = null).value ?: return null

    val experimentalPlaybackPersistence = experimentalPlaybackPersistenceFeatureFlag.get()
    val livePlaybackState = remember(bookId) { player.livePlaybackStateFlow(bookId) }
      .collectAsState(null).value
    val managerPlayState by remember {
      playStateManager.flow
    }.collectAsState()

    val book = if (
      experimentalPlaybackPersistence &&
      livePlaybackState != null &&
      livePlaybackState.chapterId in persistedBook.content.chapters
    ) {
      persistedBook.overlay(livePlaybackState)
    } else {
      persistedBook
    }
    val playbackSnapshot = book.playbackSnapshot(
      persistedBook = persistedBook,
      livePlaybackState = livePlaybackState,
      managerPlayState = managerPlayState,
    )
    activePlaybackChapterId = playbackSnapshot.activeChapter?.id
    activePlaybackPositionMs = playbackSnapshot.activePositionMs.takeIf { playbackSnapshot.activeChapter != null }
    activeSubtitleChapterId = playbackSnapshot.activeChapter?.id

    val displayChapter = playbackSnapshot.activeChapter ?: book.currentChapter
    val displayPositionMs = if (playbackSnapshot.activeChapter != null) {
      playbackSnapshot.activePositionMs
    } else {
      book.content.positionInChapter
    }
    val subtitleChapterId = playbackSnapshot.activeChapter?.id
    Log.d(
      SUBTITLE_LOG_TAG,
      "viewState bookTitle=${book.content.name} activeChapter.id=$subtitleChapterId activeChapter.id.toUri()=${subtitleChapterId?.toUri()}",
    )
    val subtitleCues by produceState<List<SubtitleCue>>(
      initialValue = emptyList(),
      key1 = subtitleChapterId,
      key2 = subtitleRefreshKey.intValue,
    ) {
      value = emptyList()
      val chapterId = subtitleChapterId ?: return@produceState
      value = withContext(dispatcherProvider.io) {
        subtitleLoader.loadForAudio(chapterId.toUri())
      }
    }
    val subtitlePositionMs = playbackSnapshot.activePositionMs
    val currentSubtitlePlaybackSpeed = playbackSnapshot.activePlaybackSpeed

    LaunchedEffect(
      playbackSnapshot.activeChapter?.id,
      subtitlePositionMs,
      subtitleCues,
      repeatSentenceEnabled.value,
    ) {
      val subtitleChapterId = playbackSnapshot.activeChapter?.id ?: return@LaunchedEffect
      updateRepeatSentenceLoop(
        cues = subtitleCues,
        positionMs = subtitlePositionMs,
        chapterId = subtitleChapterId,
      )
    }

    val currentMark = displayChapter.markForPosition(displayPositionMs)
    val positionInCurrentMark = if (playbackSnapshot.activeIsPlaying && currentMark.durationMs > 0) {
      val relativePosition = displayPositionMs - currentMark.startMs
      relativePosition.coerceIn(0L, currentMark.durationMs)
    } else {
      displayPositionMs - currentMark.startMs
    }

    val sleepTime = remember { sleepTimer.state }.collectAsState().value
    val subtitleFocusModeEnabled = remember { subtitleFocusModeEnabledStore.data }
      .collectAsState(initial = true)
      .value
    val starredSubtitleCueKeys = remember { starredSubtitleCueKeysStore.data }
      .collectAsState(initial = emptySet())
      .value
    val hasMoreThanOneChapter = book.chapters.sumOf { it.chapterMarks.count() } > 1
    val subtitlePanelViewState = subtitleCues.toSubtitlePanelViewState(
      positionMs = subtitlePositionMs,
      currentPlaybackSpeed = currentSubtitlePlaybackSpeed,
      repeatSentenceEnabled = repeatSentenceEnabled.value,
      starredCueKeys = starredSubtitleCueKeys,
      bookId = book.id,
      chapterId = playbackSnapshot.activeChapter?.id ?: book.currentChapter.id,
    )
    logSubtitleSync(
      playbackSpeed = currentSubtitlePlaybackSpeed,
      livePlayerPositionMs = livePlaybackState?.positionMs,
      bookPositionInChapterMs = persistedBook.content.positionInChapter,
      activeChapterId = playbackSnapshot.activeChapter?.id,
      liveChapterId = livePlaybackState?.chapterId,
      persistedCurrentChapterId = persistedBook.content.currentChapter,
      activePositionMs = playbackSnapshot.activePositionMs,
      subtitlePositionSource = playbackSnapshot.subtitlePositionSource,
      subtitlePositionMs = subtitlePositionMs,
      currentCueIndex = subtitlePanelViewState?.activeIndex,
      currentCue = subtitlePanelViewState?.activeIndex?.let(subtitleCues::getOrNull),
    )
    Log.d(
      SUBTITLE_LOG_TAG,
      "SubtitlePanelViewState isNull=${subtitlePanelViewState == null} cueCount=${subtitleCues.size} visible=${subtitlePanelViewState?.visible} itemCount=${subtitlePanelViewState?.items?.size}",
    )
    return BookPlayViewState(
      sleepTimerState = sleepTime.toViewState(),
      playing = playbackSnapshot.activeIsPlaying,
      title = book.content.name,
      showPreviousNextButtons = hasMoreThanOneChapter,
      chapterName = currentMark.name.takeIf { hasMoreThanOneChapter },
      duration = currentMark.durationMs.milliseconds,
      playedTime = positionInCurrentMark.milliseconds,
      cover = book.content.cover?.let(::ImmutableFile),
      skipSilence = book.content.skipSilence,
      subtitleFocusModeEnabled = subtitleFocusModeEnabled,
      subtitles = subtitlePanelViewState,
    )
  }

  fun dismissDialog() {
    Logger.d("dismissDialog")
    _dialogState.value = null
  }

  fun incrementSleepTime() {
    updateSleepTimeViewState {
      val customTime = it.customSleepTime
      val newTime = customTime + 1
      sleepTimerPreferenceStore.updateData { preference -> preference.copy(duration = newTime.minutes) }
      SleepTimerViewState(newTime)
    }
  }

  fun decrementSleepTime() {
    updateSleepTimeViewState {
      val customTime = it.customSleepTime
      val newTime = (customTime - 1).coerceAtLeast(1)
      sleepTimerPreferenceStore.updateData { preference ->
        preference.copy(duration = newTime.minutes)
      }
      SleepTimerViewState(newTime)
    }
  }

  fun onAcceptSleepTime(time: Int) {
    updateSleepTimeViewState {
      val book = currentBook() ?: return@updateSleepTimeViewState null
      scope.launch {
        bookmarkRepository.addBookmarkAtBookPosition(
          book = book,
          setBySleepTimer = true,
          title = null,
        )
      }
      sleepTimer.enable(TimedWithDuration(time.minutes))
      null
    }
  }

  fun onAcceptSleepAtEndOfChapter() {
    updateSleepTimeViewState {
      sleepTimer.enable(SleepTimerMode.EndOfChapter)
      null
    }
  }

  private fun updateSleepTimeViewState(update: suspend (SleepTimerViewState) -> SleepTimerViewState?) {
    scope.launch {
      val current = dialogState.value
      val updated: SleepTimerViewState? = if (current is BookPlayDialogViewState.SleepTimer) {
        update(current.viewState)
      } else {
        update(SleepTimerViewState(sleepTimerPreferenceStore.data.first().duration.inWholeMinutes.toInt()))
      }
      _dialogState.value = updated?.let(BookPlayDialogViewState::SleepTimer)
    }
  }

  fun onPlaybackSpeedChanged(speed: Float) {
    _dialogState.value = BookPlayDialogViewState.SpeedDialog(speed)
    player.setSpeed(speed)
  }

  fun onSubtitlePlaybackSpeedClick(speed: Float) {
    player.setSpeed(speed)
  }

  fun onVolumeGainChanged(gain: Decibel) {
    _dialogState.value = volumeGainDialogViewState(gain)
    player.setGain(gain)
  }

  fun next() {
    player.next()
  }

  fun previous() {
    player.previous()
  }

  fun playPause() {
    if (playStateManager.playState != PlayStateManager.PlayState.Playing) {
      scope.launch {
        if (batteryOptimization.shouldRequest()) {
          _viewEffects.tryEmit(BookPlayViewEffect.RequestIgnoreBatteryOptimization)
          batteryOptimization.onBatteryOptimizationsRequested()
        }
      }
    }
    player.playPause()
  }

  fun rewind() {
    player.rewind()
  }

  fun fastForward() {
    player.fastForward()
  }

  fun onCloseClick() {
    navigator.goBack()
  }

  fun onCurrentChapterClick() {
    scope.launch {
      val book = currentBook() ?: return@launch
      _dialogState.value = BookPlayDialogViewState.SelectChapterDialog(
        items = book.chapters.flatMapIndexed { chapterIndex, chapter ->
          chapter.chapterMarks.mapIndexed { markIndex, chapterMark ->
            val previousChapters = book.chapters.take(chapterIndex)
            BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
              number = previousChapters.sumOf { it.chapterMarks.count() } + markIndex + 1,
              name = chapterMark.name ?: "",
              active = chapterMark == book.currentMark && chapter == book.currentChapter,
              time = formatTime(previousChapters.sumOf { it.duration } + chapterMark.startMs),
            )
          }
        },
      )
    }
  }

  fun onChapterClick(number: Int) {
    scope.launch {
      val book = currentBook() ?: return@launch
      var currentIndex = -1
      book.chapters.forEach { chapter ->
        chapter.chapterMarks.forEach { mark ->
          currentIndex++
          if (currentIndex == number - 1) {
            player.setPosition(mark.startMs, chapter.id)
            _dialogState.value = null
            return@launch
          }
        }
      }
    }
  }

  fun onPlaybackSpeedIconClick() {
    scope.launch {
      val playbackSpeed = currentBook()?.content?.playbackSpeed ?: return@launch
      _dialogState.value = BookPlayDialogViewState.SpeedDialog(playbackSpeed)
    }
  }

  fun onVolumeGainIconClick() {
    scope.launch {
      val content = currentBook()?.content ?: return@launch
      _dialogState.value = volumeGainDialogViewState(Decibel(content.gain))
    }
  }

  private fun volumeGainDialogViewState(gain: Decibel): BookPlayDialogViewState.VolumeGainDialog {
    return BookPlayDialogViewState.VolumeGainDialog(
      gain = gain,
      maxGain = VolumeGain.MAX_GAIN,
      valueFormatted = volumeGainFormatter.format(gain),
    )
  }

  fun onBookmarkClick() {
    navigator.goTo(Destination.Bookmarks(bookId))
  }

  fun onBookmarkLongClick() {
    scope.launch {
      val book = currentBook() ?: return@launch
      bookmarkRepository.addBookmarkAtBookPosition(
        book = book,
        title = null,
        setBySleepTimer = false,
      )
      _viewEffects.tryEmit(BookPlayViewEffect.BookmarkAdded)
    }
  }

  fun seekTo(position: Duration) {
    scope.launch {
      val book = currentBook() ?: return@launch
      val chapter = activePlaybackChapterId
        ?.let { activeChapterId -> book.chapters.firstOrNull { it.id == activeChapterId } }
        ?: book.currentChapter
      val positionMs = activePlaybackPositionMs ?: book.content.positionInChapter
      val currentMark = chapter.markForPosition(positionMs)
      player.setPosition(currentMark.startMs + position.inWholeMilliseconds, chapter.id)
    }
  }

  fun onSubtitleClick(cueStartMs: Long) {
    selectedSubtitleCueStartMs.value = cueStartMs
    loopSeekPendingForCueStartMs = null
    scope.launch {
      val book = currentBook() ?: return@launch
      val chapterId = activeSubtitleChapterId
        ?: activePlaybackChapterId
        ?: book.currentChapter.id
      player.setPosition(
        time = (cueStartMs - SUBTITLE_SEEK_PRE_ROLL_MS).coerceAtLeast(0),
        id = chapterId,
      )
    }
  }

  fun toggleRepeatSentence() {
    repeatSentenceEnabled.value = !repeatSentenceEnabled.value
    if (!repeatSentenceEnabled.value) {
      loopSeekPendingForCueStartMs = null
    }
  }

  fun toggleSubtitleFocusMode() {
    scope.launch {
      subtitleFocusModeEnabledStore.updateData { enabled -> !enabled }
    }
  }

  fun onSubtitleStarClick(starKey: String) {
    scope.launch {
      starredSubtitleCueKeysStore.updateData { keys -> keys.toggleSubtitleCueStar(starKey) }
    }
  }

  fun refreshSubtitles() {
    subtitleRefreshKey.intValue += 1
    runCatching {
      mediaScanTrigger.scan(restartIfScanning = true)
    }.onFailure { error ->
      Logger.w(error, "Failed to refresh media scan from playback screen")
    }
  }

  fun toggleSleepTimer() {
    scope.launch {
      Logger.d("toggleSleepTimer while active=${sleepTimer.state.value}")
      if (sleepTimer.state.value.enabled) {
        sleepTimer.disable()
        _dialogState.value = null
      } else {
        _dialogState.value = BookPlayDialogViewState.SleepTimer(
          viewState = SleepTimerViewState(
            customSleepTime = sleepTimerPreferenceStore.data.first().duration.inWholeMinutes.toInt(),
          ),
        )
      }
    }
  }

  fun onBatteryOptimizationRequested() {
    navigator.goTo(Destination.BatteryOptimization)
  }

  fun toggleSkipSilence() {
    scope.launch {
      val skipSilence = currentBook()?.content?.skipSilence ?: return@launch
      player.skipSilence(!skipSilence)
    }
  }

  private suspend fun currentBook(): Book? {
    return currentBookResolver.book(bookId)
  }

  private fun updateRepeatSentenceLoop(
    cues: List<SubtitleCue>,
    positionMs: Long,
    chapterId: ChapterId,
  ) {
    if (cues.isEmpty()) {
      repeatSentenceEnabled.value = false
      selectedSubtitleCueStartMs.value = null
      loopSeekPendingForCueStartMs = null
      return
    }
    val activeCueIndex = cues.activeCueIndex(positionMs)
    val selectedCue = selectedSubtitleCueStartMs.value
      ?.let { selectedStartMs -> cues.firstOrNull { it.startMs == selectedStartMs } }
    val effectiveCue = if (
      selectedCue != null &&
      positionMs >= (selectedCue.startMs - SUBTITLE_SEEK_PRE_ROLL_MS).coerceAtLeast(0) &&
      positionMs <= selectedCue.endMs
    ) {
      selectedCue
    } else {
      activeCueIndex?.let(cues::get)
    }

    selectedSubtitleCueStartMs.value = effectiveCue?.startMs

    if (!repeatSentenceEnabled.value || effectiveCue == null) {
      loopSeekPendingForCueStartMs = null
      return
    }

    if (positionMs < effectiveCue.endMs - SUBTITLE_LOOP_THRESHOLD_MS) {
      loopSeekPendingForCueStartMs = null
      return
    }

    if (loopSeekPendingForCueStartMs == effectiveCue.startMs) return

    loopSeekPendingForCueStartMs = effectiveCue.startMs
    player.setPosition(
      time = (effectiveCue.startMs - SUBTITLE_SEEK_PRE_ROLL_MS).coerceAtLeast(0),
      id = chapterId,
    )
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): BookPlayViewModel
  }
}

private fun SleepTimerState.toViewState(): BookPlayViewState.SleepTimerViewState = when (this) {
  SleepTimerState.Disabled -> BookPlayViewState.SleepTimerViewState.Disabled
  is SleepTimerState.Enabled.WithDuration -> BookPlayViewState.SleepTimerViewState.Enabled.WithDuration(this.leftDuration)
  SleepTimerState.Enabled.WithEndOfChapter -> BookPlayViewState.SleepTimerViewState.Enabled.WithEndOfChapter
}

private fun List<SubtitleCue>.toSubtitlePanelViewState(
  positionMs: Long,
  currentPlaybackSpeed: Float,
  repeatSentenceEnabled: Boolean,
  starredCueKeys: Set<String>,
  bookId: BookId,
  chapterId: ChapterId,
): BookPlayViewState.SubtitlePanelViewState? {
  if (isEmpty()) return null

  val activeIndex = activeCueIndex(positionMs)
  return BookPlayViewState.SubtitlePanelViewState(
    visible = true,
    repeatSentenceEnabled = repeatSentenceEnabled,
    activeIndex = activeIndex,
    speedOptions = SUBTITLE_PLAYBACK_SPEEDS.map { speed ->
      BookPlayViewState.SubtitlePanelViewState.SpeedOption(
        speed = speed,
        selected = currentPlaybackSpeed == speed,
      )
    },
    items = mapIndexed { index, cue ->
      val key = cue.subtitleCueKey(bookId = bookId, chapterId = chapterId)
      BookPlayViewState.SubtitlePanelViewState.Item(
        startMs = cue.startMs,
        starKey = key,
        text = cue.text,
        active = index == activeIndex,
        starred = key in starredCueKeys,
      )
    },
  )
}

private val SUBTITLE_PLAYBACK_SPEEDS = listOf(0.75F, 1.0F, 1.25F)
private const val SUBTITLE_LOG_TAG = "VoiceSubtitle"
private const val SUBTITLE_SYNC_LOG_TAG = "VoiceSubtitleSync"
private const val SUBTITLE_SEEK_PRE_ROLL_MS = 0L
private const val SUBTITLE_LOOP_THRESHOLD_MS = 80L

private enum class SubtitlePositionSource {
  LIVE,
  PERSISTED_INITIAL_ONLY,
}

private data class PlaybackSnapshot(
  val activeChapter: Chapter?,
  val activePositionMs: Long,
  val activePlaybackSpeed: Float,
  val activeIsPlaying: Boolean,
  val subtitlePositionSource: SubtitlePositionSource,
)

private fun Book.playbackSnapshot(
  persistedBook: Book,
  livePlaybackState: LivePlaybackState?,
  managerPlayState: PlayStateManager.PlayState,
): PlaybackSnapshot {
  val liveChapter = livePlaybackState
    ?.chapterId
    ?.let { liveChapterId -> chapters.firstOrNull { it.id == liveChapterId } }
  return if (livePlaybackState != null) {
    PlaybackSnapshot(
      activeChapter = liveChapter,
      activePositionMs = livePlaybackState.positionMs,
      activePlaybackSpeed = livePlaybackState.playbackSpeed,
      activeIsPlaying = livePlaybackState.isPlaying,
      subtitlePositionSource = SubtitlePositionSource.LIVE,
    )
  } else {
    PlaybackSnapshot(
      activeChapter = currentChapter,
      activePositionMs = persistedBook.content.positionInChapter,
      activePlaybackSpeed = persistedBook.content.playbackSpeed,
      activeIsPlaying = managerPlayState == PlayStateManager.PlayState.Playing,
      subtitlePositionSource = SubtitlePositionSource.PERSISTED_INITIAL_ONLY,
    )
  }
}

private fun logSubtitleSync(
  playbackSpeed: Float,
  livePlayerPositionMs: Long?,
  bookPositionInChapterMs: Long,
  activeChapterId: ChapterId?,
  liveChapterId: ChapterId?,
  persistedCurrentChapterId: ChapterId,
  activePositionMs: Long,
  subtitlePositionSource: SubtitlePositionSource,
  subtitlePositionMs: Long,
  currentCueIndex: Int?,
  currentCue: SubtitleCue?,
) {
  val delta = livePlayerPositionMs?.let { subtitlePositionMs - it }
  Log.d(
    SUBTITLE_SYNC_LOG_TAG,
    "playbackSpeed=$playbackSpeed " +
      "subtitlePositionSource=$subtitlePositionSource " +
      "activeChapterId=$activeChapterId " +
      "liveChapterId=$liveChapterId " +
      "persistedCurrentChapterId=$persistedCurrentChapterId " +
      "activePositionMs=$activePositionMs " +
      "livePlayerPositionMs=$livePlayerPositionMs " +
      "bookPositionInChapterMs=$bookPositionInChapterMs " +
      "subtitlePositionMs=$subtitlePositionMs " +
      "currentCueIndex=$currentCueIndex " +
      "currentCue.startMs=${currentCue?.startMs} " +
      "currentCue.endMs=${currentCue?.endMs} " +
      "delta=$delta",
  )
}
