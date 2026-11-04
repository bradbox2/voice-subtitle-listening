package voice.core.studystats

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyMonthTest {

  @Test
  fun `the days of a month add up their files, the most studied first`() {
    val a = StudyEntry("2026-11-04", "friendsTTS", "S01E05.mp3", 60_000, 0)
    val b = StudyEntry("2026-11-04", "S0106edition", "S01E06.mp3", 300_000, 1)
    val c = StudyEntry("2026-11-20", "friendsTTS", "S01E05.mp3", 120_000, 1)
    val otherMonth = StudyEntry("2026-10-31", "friendsTTS", "S01E05.mp3", 999_000, 3)

    val month = studyMonth(listOf(a, b, c, otherMonth), YearMonth.of(2026, 11))

    assertEquals(
      mapOf(
        LocalDate.of(2026, 11, 4) to StudyDay(millis = 360_000, plays = 1, files = listOf(b, a)),
        LocalDate.of(2026, 11, 20) to StudyDay(millis = 120_000, plays = 1, files = listOf(c)),
      ),
      month,
    )
  }
}
