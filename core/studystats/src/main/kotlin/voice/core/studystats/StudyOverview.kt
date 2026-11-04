package voice.core.studystats

import java.time.LocalDate

data class StudyOverview(
  val todayMillis: Long,
  val totalMillis: Long,
  val folders: List<Folder>,
) {

  data class Folder(
    val name: String,
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

/** Folders and their files, the most recently studied first. */
fun studyOverview(
  entries: List<StudyEntry>,
  today: LocalDate,
): StudyOverview {
  val todayString = today.toString()
  val folders =
    entries
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
          millis = files.sumOf { it.millis },
          plays = files.sumOf { it.plays },
          lastDate = files.maxOf { it.lastDate },
          files = files,
        )
      }
      .sortedWith(compareByDescending<StudyOverview.Folder> { it.lastDate }.thenBy { it.name })
  return StudyOverview(
    todayMillis = entries.filter { it.date == todayString }.sumOf { it.listenedMillis },
    totalMillis = entries.sumOf { it.listenedMillis },
    folders = folders,
  )
}
