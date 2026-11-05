package voice.core.studystats

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StudySyncRequest(
  @SerialName("device_id") val deviceId: String,
  @SerialName("app_version") val appVersion: String,
  val days: List<StudySyncDay>,
)

@Serializable data class StudySyncDay(val date: String, val items: List<StudySyncItem>)

@Serializable
data class StudySyncItem(
  @SerialName("item_key") val itemKey: String,
  val title: String,
  val folder: String,
  /**
   * The [StudyCategory.id] of the folder, for the agent that sorts the study time on the server.
   */
  val category: String,
  val seconds: Int,
  val plays: Int,
)

/**
 * The days of the last week, as the server takes them: each date is replaced as a whole, so every
 * day goes with all of its files, and sending the same days again is fine. Null if there is nothing
 * to send.
 *
 * Whatever the server would refuse is cut here instead, as it refuses the whole request then.
 */
fun studySyncRequest(
  entries: List<StudyEntry>,
  today: LocalDate,
  chinaToday: LocalDate,
  deviceId: String,
  appVersion: String,
  labels: Map<String, FolderCategory> = emptyMap(),
): StudySyncRequest? {
  val earliest = today.minusDays(DAYS_SENT - 1L)
  val latest = chinaToday.plusDays(1)
  val days =
    entries
      .groupBy { it.date }
      .mapNotNull { (date, dayEntries) ->
        val day = LocalDate.parse(date)
        if (day < earliest || day > latest) return@mapNotNull null
        val items = dayEntries.toItems(labels)
        if (items.isEmpty()) null else StudySyncDay(date, items)
      }
      .sortedBy { it.date }
  if (days.isEmpty()) return null
  return StudySyncRequest(deviceId = deviceId, appVersion = appVersion, days = days)
}

fun studySyncJson(request: StudySyncRequest): String =
  Json.encodeToString(StudySyncRequest.serializer(), request)

private fun List<StudyEntry>.toItems(labels: Map<String, FolderCategory>): List<StudySyncItem> {
  var secondsLeft = SECONDS_PER_DAY
  return groupBy { it.folder to it.file }
    .map { (key, same) ->
      val (folder, file) = key
      StudySyncItem(
        itemKey = "$folder/$file",
        title = file,
        folder = folder,
        category = categoryOf(folder, labels).id,
        seconds =
          (same.sumOf { it.listenedMillis } / 1000).coerceIn(0, SECONDS_PER_DAY.toLong()).toInt(),
        plays = same.sumOf { it.plays }.coerceIn(0, MAX_PLAYS),
      )
    }
    .filter { it.seconds > 0 || it.plays > 0 }
    .sortedByDescending { it.seconds }
    .map { item ->
      val seconds = minOf(item.seconds, secondsLeft)
      secondsLeft -= seconds
      item.copy(seconds = seconds)
    }
    .filter { it.seconds > 0 || it.plays > 0 }
}

private const val DAYS_SENT = 7
private const val SECONDS_PER_DAY = 86_400
private const val MAX_PLAYS = 10_000
