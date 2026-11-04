package voice.core.studystats

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.net.URLDecoder
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.datastore.VoiceDataStoreFactory

/**
 * Study time of one file on one day. Kept by name instead of by id, as listeners replace files with
 * new versions of the same name and want the time to keep adding up.
 */
@Serializable
data class StudyEntry(
  /** The local day, like 2026-11-04. */
  val date: String,
  val folder: String,
  val file: String,
  val listenedMillis: Long,
  /** How often the file was played to its end. */
  val plays: Int,
)

data class StudyNames(val folder: String, val file: String)

@SingleIn(AppScope::class)
class StudyStatsStore(store: DataStore<List<StudyEntry>>) : DataStore<List<StudyEntry>> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = ListSerializer(StudyEntry.serializer()),
      defaultValue = emptyList(),
      fileName = "studyStats",
    ),
  )
}

@Inject
class StudyStats(private val store: StudyStatsStore) {

  val entries: Flow<List<StudyEntry>>
    get() = store.data

  suspend fun add(
    date: LocalDate,
    names: StudyNames,
    listenedMillis: Long,
    plays: Int,
  ) {
    if (listenedMillis <= 0 && plays <= 0) return
    val day = date.toString()
    store.updateData { entries ->
      val index = entries.indexOfFirst {
        it.date == day && it.folder == names.folder && it.file == names.file
      }
      if (index == -1) {
        entries + StudyEntry(day, names.folder, names.file, listenedMillis, plays)
      } else {
        entries.toMutableList().also {
          val entry = it[index]
          it[index] =
            entry.copy(
              listenedMillis = entry.listenedMillis + listenedMillis,
              plays = entry.plays + plays,
            )
        }
      }
    }
  }
}

/**
 * The folder is the folder of the book, so files in sub folders count for their book. A book of a
 * single file is named by the folder it is in.
 */
fun studyNames(
  bookId: BookId,
  chapterId: ChapterId,
): StudyNames {
  val chapterPath = chapterId.value.decodedPath()
  val file = chapterPath.substringAfterLast('/')
  val folder =
    if (bookId.value == chapterId.value) {
      chapterPath.substringBeforeLast('/').substringAfterLast('/')
    } else {
      bookId.value.decodedPath().substringAfterLast('/')
    }
  return StudyNames(folder = folder.substringAfterLast(':'), file = file.substringAfterLast(':'))
}

// + is literal in uris, URLDecoder would turn it into a space
private fun String.decodedPath(): String =
  URLDecoder.decode(replace("+", "%2B"), "UTF-8").trimEnd('/')

/** Opens in Excel with the names readable, which needs the byte order mark for UTF-8. */
fun studyCsv(entries: List<StudyEntry>): String {
  return buildString {
    append(0xFEFF.toChar())
    append("date,folder,file,minutes,plays\r\n")
    entries.forEach { entry ->
      val minutes = (entry.listenedMillis / 6_000.0).roundToLong() / 10.0
      append(
        listOf(entry.date, entry.folder.csv(), entry.file.csv(), minutes, entry.plays)
          .joinToString(","),
      )
      append("\r\n")
    }
  }
}

private fun String.csv(): String {
  return if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
    "\"" + replace("\"", "\"\"") + "\""
  } else {
    this
  }
}
