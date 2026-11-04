package voice.core.studystats

import java.time.LocalDate
import java.time.YearMonth

data class StudyDay(
  val millis: Long,
  val plays: Int,
  /** The most studied first. */
  val files: List<StudyEntry>,
)

/** The days of [month] with study time. */
fun studyMonth(
  entries: List<StudyEntry>,
  month: YearMonth,
): Map<LocalDate, StudyDay> {
  val prefix = "$month-"
  return entries
    .filter { it.date.startsWith(prefix) }
    .groupBy { LocalDate.parse(it.date) }
    .mapValues { (_, dayEntries) ->
      StudyDay(
        millis = dayEntries.sumOf { it.listenedMillis },
        plays = dayEntries.sumOf { it.plays },
        files = dayEntries.sortedByDescending { it.listenedMillis },
      )
    }
}
