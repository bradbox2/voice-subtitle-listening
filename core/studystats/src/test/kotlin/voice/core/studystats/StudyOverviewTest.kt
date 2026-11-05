package voice.core.studystats

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyOverviewTest {

  @Test
  fun `folders and files add up over the days, the most recent first`() {
    val overview =
      studyOverview(
        listOf(
          StudyEntry("2026-11-03", "friendsTTS", "S01E05.mp3", 600_000, 1),
          StudyEntry("2026-11-04", "friendsTTS", "S01E05.mp3", 300_000, 2),
          StudyEntry("2026-11-04", "friendsTTS", "S01E06.mp3", 60_000, 0),
          StudyEntry("2026-11-02", "S0106edition", "S01E06-elevenlabs.mp3", 120_000, 1),
        ),
        today = LocalDate.of(2026, 11, 4),
      )

    assertEquals(360_000, overview.todayMillis)
    assertEquals(960_000 + 120_000, overview.weekMillis)
    assertEquals(1_080_000, overview.totalMillis)
    assertEquals(
      listOf(
        StudyOverview.Folder(
          name = "friendsTTS",
          category = StudyCategory.Audiobook,
          millis = 960_000,
          plays = 3,
          lastDate = "2026-11-04",
          files =
            listOf(
              StudyOverview.File("S01E05.mp3", 900_000, 3, "2026-11-04"),
              StudyOverview.File("S01E06.mp3", 60_000, 0, "2026-11-04"),
            ),
        ),
        StudyOverview.Folder(
          name = "S0106edition",
          category = StudyCategory.Audiobook,
          millis = 120_000,
          plays = 1,
          lastDate = "2026-11-02",
          files = listOf(StudyOverview.File("S01E06-elevenlabs.mp3", 120_000, 1, "2026-11-02")),
        ),
      ),
      overview.folders,
    )
  }

  @Test
  fun `nothing studied yet`() {
    val overview = studyOverview(emptyList(), LocalDate.of(2026, 11, 4))

    assertEquals(StudyOverview(0, 0, 0, emptyList()), overview)
  }
}
