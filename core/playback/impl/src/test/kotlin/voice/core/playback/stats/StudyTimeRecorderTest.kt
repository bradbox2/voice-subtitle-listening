package voice.core.playback.stats

import androidx.datastore.core.DataStore
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import voice.core.studystats.StudyEntry
import voice.core.studystats.StudyNames
import voice.core.studystats.StudyStats
import voice.core.studystats.StudyStatsStore

class StudyTimeRecorderTest {

  private val scope = TestScope()
  private val store = MemoryStore()
  // 20:00 in Shanghai
  private val clock = SchedulerClock(scope.testScheduler, Instant.parse("2026-11-04T12:00:00Z"))
  private val recorder =
    StudyTimeRecorder(
      stats = StudyStats(StudyStatsStore(store)),
      categorizer = mockk(relaxed = true),
      clock = clock,
      scope = scope.backgroundScope,
      serverFiles = NoServerFiles,
    )
  private val lesson = StudyNames(folder = "S0106edition", file = "S01E06.mp3")
  private val other = StudyNames(folder = "S0106edition", file = "S01E07.mp3")

  @Test
  fun `the time played is added when playback stops`() = scope.runTest {
    recorder.playing(lesson)
    advanceTimeBy(10.minutes)
    recorder.stopped()
    runCurrent()

    assertEquals(
      listOf(entry(lesson, listened = 10.minutes.inWholeMilliseconds)),
      store.value.value,
    )
  }

  @Test
  fun `the time is saved while playing, so little is lost when the app is killed`() =
    scope.runTest {
      recorder.playing(lesson)
      advanceTimeBy(31.seconds)

      assertEquals(listOf(entry(lesson, listened = 30_000)), store.value.value)
    }

  @Test
  fun `time while paused doesn't count`() = scope.runTest {
    recorder.playing(lesson)
    advanceTimeBy(1.minutes)
    recorder.stopped()
    advanceTimeBy(1.hours)
    recorder.playing(lesson)
    advanceTimeBy(1.minutes)
    recorder.stopped()
    runCurrent()

    assertEquals(listOf(entry(lesson, listened = 120_000)), store.value.value)
  }

  @Test
  fun `each file gets its own time, and a file listened to the end counts as played`() =
    scope.runTest {
      recorder.playing(lesson)
      recorder.durationKnown(5.minutes.inWholeMilliseconds)
      advanceTimeBy(5.minutes)
      recorder.ended(lesson)
      recorder.playing(other)
      advanceTimeBy(1.minutes)
      recorder.stopped()
      runCurrent()

      assertEquals(
        listOf(entry(lesson, listened = 300_000, plays = 1), entry(other, listened = 60_000)),
        store.value.value,
      )
    }

  @Test
  fun `a file repeating counts every time it was listened to`() = scope.runTest {
    recorder.playing(lesson)
    recorder.durationKnown(1.minutes.inWholeMilliseconds)
    advanceTimeBy(1.minutes)
    recorder.ended(lesson)
    advanceTimeBy(1.minutes)
    recorder.ended(lesson)
    recorder.stopped()
    runCurrent()

    assertEquals(listOf(entry(lesson, listened = 120_000, plays = 2)), store.value.value)
  }

  @Test
  fun `skipping to the end doesn't count as played`() = scope.runTest {
    recorder.playing(lesson)
    recorder.durationKnown(10.minutes.inWholeMilliseconds)
    advanceTimeBy(15.seconds)
    recorder.ended(lesson)
    recorder.stopped()
    runCurrent()

    assertEquals(listOf(entry(lesson, listened = 15_000)), store.value.value)
  }

  @Test
  fun `faster playback finishes the file in less time`() = scope.runTest {
    recorder.playing(lesson)
    recorder.durationKnown(10.minutes.inWholeMilliseconds)
    recorder.speedChanged(1.25F)
    advanceTimeBy(8.minutes)
    recorder.ended(lesson)
    recorder.stopped()
    runCurrent()

    assertEquals(listOf(entry(lesson, listened = 480_000, plays = 1)), store.value.value)
  }

  @Test
  fun `pauses don't break a play, as long as the file was heard`() = scope.runTest {
    recorder.playing(lesson)
    recorder.durationKnown(2.minutes.inWholeMilliseconds)
    advanceTimeBy(1.minutes)
    recorder.stopped()
    advanceTimeBy(1.hours)
    recorder.playing(lesson)
    advanceTimeBy(1.minutes)
    recorder.ended(lesson)
    recorder.stopped()
    runCurrent()

    assertEquals(listOf(entry(lesson, listened = 120_000, plays = 1)), store.value.value)
  }

  private fun entry(
    names: StudyNames,
    listened: Long,
    plays: Int = 0,
  ) = StudyEntry("2026-11-04", names.folder, names.file, listened, plays)

  private class MemoryStore : DataStore<List<StudyEntry>> {
    val value = MutableStateFlow(emptyList<StudyEntry>())
    override val data: Flow<List<StudyEntry>> = value

    override suspend fun updateData(
      transform: suspend (t: List<StudyEntry>) -> List<StudyEntry>,
    ): List<StudyEntry> = value.updateAndGet { transform(it) }
  }

  private class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant,
  ) : Clock() {
    override fun instant(): Instant = start.plusMillis(scheduler.currentTime)

    override fun getZone(): ZoneId = ZoneId.of("Asia/Shanghai")

    override fun withZone(zone: ZoneId): Clock = this
  }
}
