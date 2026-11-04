package voice.core.studystats

import androidx.datastore.core.DataStore
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest

class StudyStatsTest {

  private val store = MemoryStore()
  private val stats = StudyStats(StudyStatsStore(store))
  private val today = LocalDate.of(2026, 11, 4)
  private val lesson = StudyNames(folder = "S0106edition", file = "S01E06.mp3")

  @Test
  fun `time and plays of a file add up per day`() = runTest {
    stats.add(today, lesson, listenedMillis = 60_000, plays = 0)
    stats.add(today, lesson, listenedMillis = 30_000, plays = 1)
    stats.add(today.plusDays(1), lesson, listenedMillis = 10_000, plays = 0)

    assertEquals(
      listOf(
        StudyEntry("2026-11-04", "S0106edition", "S01E06.mp3", listenedMillis = 90_000, plays = 1),
        StudyEntry("2026-11-05", "S0106edition", "S01E06.mp3", listenedMillis = 10_000, plays = 0),
      ),
      store.data.first(),
    )
  }

  @Test
  fun `a replaced file with the same name keeps adding up`() = runTest {
    stats.add(today, lesson, listenedMillis = 60_000, plays = 1)
    // the new version of the file has another id, but the same names
    stats.add(today, lesson.copy(), listenedMillis = 60_000, plays = 1)

    assertEquals(1, store.data.first().size)
    assertEquals(120_000, store.data.first().single().listenedMillis)
  }

  @Test
  fun `nothing is stored for nothing`() = runTest {
    stats.add(today, lesson, listenedMillis = 0, plays = 0)

    assertEquals(emptyList(), store.data.first())
  }

  @Test
  fun `the csv opens in Excel with chinese names and quotes what needs it`() {
    val csv =
      studyCsv(
        listOf(
          StudyEntry("2026-11-04", "长音频", "a, \"b\".mp3", listenedMillis = 150_000, plays = 2),
        ),
      )

    assertEquals(
      0xFEFF.toChar() +
        "date,folder,file,minutes,plays\r\n" +
        "2026-11-04,长音频,\"a, \"\"b\"\".mp3\",2.5,2\r\n",
      csv,
    )
  }

  private class MemoryStore : DataStore<List<StudyEntry>> {
    private val value = MutableStateFlow(emptyList<StudyEntry>())
    override val data: Flow<List<StudyEntry>> = value

    override suspend fun updateData(
      transform: suspend (t: List<StudyEntry>) -> List<StudyEntry>,
    ): List<StudyEntry> = value.updateAndGet { transform(it) }
  }
}
