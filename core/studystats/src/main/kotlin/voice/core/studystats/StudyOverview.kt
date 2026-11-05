package voice.core.studystats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class StudyOverview(
  val todayMillis: Long,
  /** From the Monday of the week, as TTC counts it. */
  val weekMillis: Long,
  val totalMillis: Long,
  val folders: List<Folder>,
) {

  data class Folder(
    val name: String,
    val category: StudyCategory,
    val millis: Long,
    val plays: Int,
    val lastDate: String,
    val files: List<File>,
  )

  data class File(
    val name: String,
    val millis: Long,
    val plays: Int,
    val lastDate: String,
  )
}

data class StudyWeek(val start: LocalDate, val millis: Long, val plays: Int)

/** The entries of one category, or all of them for a null [filter]. */
fun List<StudyEntry>.inCategory(
  labels: Map<String, FolderCategory>,
  filter: StudyCategory?,
): List<StudyEntry> {
  return if (filter == null) this else filter { categoryOf(it.folder, labels) == filter }
}

/** Folders and their files, the most recently studied first. */
fun studyOverview(
  entries: List<StudyEntry>,
  today: LocalDate,
  labels: Map<String, FolderCategory> = emptyMap(),
  filter: StudyCategory? = null,
): StudyOverview {
  val shown = entries.inCategory(labels, filter)
  val todayString = today.toString()
  val monday = today.monday().toString()
  val folders =
    shown
      .groupBy { it.folder }
      .map { (folder, folderEntries) ->
        val files =
          folderEntries
            .groupBy { it.file }
            .map { (file, fileEntries) ->
              StudyOverview.File(
                name = file,
                millis = fileEntries.sumOf { it.listenedMillis },
                plays = fileEntries.sumOf { it.plays },
                lastDate = fileEntries.maxOf { it.date },
              )
            }
            .sortedWith(compareByDescending<StudyOverview.File> { it.lastDate }.thenBy { it.name })
        StudyOverview.Folder(
          name = folder,
          category = categoryOf(folder, labels),
          millis = files.sumOf { it.millis },
          plays = files.sumOf { it.plays },
          lastDate = files.maxOf { it.lastDate },
          files = files,
        )
      }
      .sortedWith(compareByDescending<StudyOverview.Folder> { it.lastDate }.thenBy { it.name })
  return StudyOverview(
    todayMillis = shown.filter { it.date == todayString }.sumOf { it.listenedMillis },
    weekMillis = shown.filter { it.date in monday..todayString }.sumOf { it.listenedMillis },
    totalMillis = shown.sumOf { it.listenedMillis },
    folders = folders,
  )
}

/**
 * The latest [count] weeks, this one first. Weeks before the first one with study time are left
 * out, except that there are at least [MIN_WEEKS], so a new listener sees a few weeks to fill.
 */
fun studyWeeks(
  entries: List<StudyEntry>,
  today: LocalDate,
  labels: Map<String, FolderCategory> = emptyMap(),
  filter: StudyCategory? = null,
  count: Int = 12,
): List<StudyWeek> {
  val shown = entries.inCategory(labels, filter)
  val weeks =
    (0 until count).map { weeksAgo ->
      val start = today.monday().minusWeeks(weeksAgo.toLong())
      val range = start.toString()..start.plusDays(6).toString()
      val inWeek = shown.filter { it.date in range }
      StudyWeek(
        start = start,
        millis = inWeek.sumOf { it.listenedMillis },
        plays = inWeek.sumOf { it.plays },
      )
    }
  val oldestWithStudyTime = weeks.indexOfLast { it.millis > 0 || it.plays > 0 }
  return weeks.take(maxOf(oldestWithStudyTime + 1, minOf(count, MIN_WEEKS)))
}

private const val MIN_WEEKS = 4

private fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
