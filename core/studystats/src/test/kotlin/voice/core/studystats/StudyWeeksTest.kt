package voice.core.studystats

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyWeeksTest {

  // a Wednesday, so the week began on Monday the 2nd
  private val today = LocalDate.of(2026, 11, 4)

  private val entries =
    listOf(
      StudyEntry("2026-11-04", "S0106edition", "a.mp3", 3_600_000, 2),
      StudyEntry("2026-11-04", "21 Day Meditation", "day1.mp3", 1_200_000, 0),
      StudyEntry("2026-11-02", "21 Day Meditation", "day1.mp3", 600_000, 0),
      // Sunday, the week before
      StudyEntry("2026-11-01", "21 Day Meditation", "day1.mp3", 1_800_000, 0),
      StudyEntry("2026-10-27", "Dune", "ch1.mp3", 900_000, 0),
    )
  private val labels =
    mapOf(
      "S0106edition" to FolderCategory(StudyCategory.Language),
      "Dune" to FolderCategory(StudyCategory.Audiobook),
    )

  @Test
  fun `weeks begin on Monday, the latest first, and also show the weeks without study time`() {
    val weeks = studyWeeks(entries, today, labels, count = 4)

    assertEquals(
      listOf(
        StudyWeek(LocalDate.of(2026, 11, 2), millis = 5_400_000, plays = 2),
        StudyWeek(LocalDate.of(2026, 10, 26), millis = 2_700_000, plays = 0),
        StudyWeek(LocalDate.of(2026, 10, 19), millis = 0, plays = 0),
        StudyWeek(LocalDate.of(2026, 10, 12), millis = 0, plays = 0),
      ),
      weeks,
    )
  }

  @Test
  fun `weeks before the first one with study time are left out, down to four weeks`() {
    assertEquals(4, studyWeeks(entries, today, labels).size)
    assertEquals(
      6,
      studyWeeks(listOf(StudyEntry("2026-09-28", "Dune", "a.mp3", 60_000, 0)), today, labels).size,
    )
    assertEquals(4, studyWeeks(emptyList(), today, labels).size)
  }

  @Test
  fun `the meditation of each week, by the name of the folder when it has no label`() {
    val weeks = studyWeeks(entries, today, labels, StudyCategory.Meditation, count = 2)

    assertEquals(listOf(1_800_000L, 1_800_000L), weeks.map { it.millis })
  }

  @Test
  fun `the overview of a category only counts its folders`() {
    val overview = studyOverview(entries, today, labels, StudyCategory.Language)

    assertEquals(3_600_000, overview.todayMillis)
    assertEquals(3_600_000, overview.weekMillis)
    assertEquals(3_600_000, overview.totalMillis)
    assertEquals(listOf("S0106edition"), overview.folders.map { it.name })
    assertEquals(StudyCategory.Language, overview.folders.single().category)
  }

  @Test
  fun `the overview of everything has the week from Monday`() {
    val overview = studyOverview(entries, today, labels)

    assertEquals(5_400_000, overview.weekMillis)
    assertEquals(8_100_000, overview.totalMillis)
  }
}
