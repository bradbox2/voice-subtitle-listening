package voice.core.studystats

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StudySyncPayloadTest {

  private val today = LocalDate.of(2026, 11, 5)
  private val chinaToday = LocalDate.of(2026, 11, 5)

  private fun request(entries: List<StudyEntry>, china: LocalDate = chinaToday) =
    studySyncRequest(
      entries = entries,
      today = today,
      chinaToday = china,
      deviceId = "SM-S9180",
      appVersion = "1.0.0-debug",
    )

  @Test
  fun `a file is sent with its folder and name as the key, in seconds`() {
    val request =
      request(
        listOf(
          StudyEntry("2026-11-05", "S0106edition", "S01E06-chatterbox_actors.mp3", 11_640_400, 9),
        ),
      )!!

    assertEquals("SM-S9180", request.deviceId)
    assertEquals("1.0.0-debug", request.appVersion)
    assertEquals(
      listOf(
        StudySyncItem(
          itemKey = "S0106edition/S01E06-chatterbox_actors.mp3",
          title = "S01E06-chatterbox_actors.mp3",
          folder = "S0106edition",
          category = "audiobook",
          seconds = 11_640,
          plays = 9,
        ),
      ),
      request.days.single().items,
    )
  }

  @Test
  fun `the json uses the names of the api`() {
    val json =
      Json.parseToJsonElement(
          studySyncJson(request(listOf(StudyEntry("2026-11-05", "F", "a.mp3", 60_000, 1)))!!),
        )
        .jsonObject

    assertEquals(setOf("device_id", "app_version", "days"), json.keys)
    val day = json.getValue("days").jsonArray.single().jsonObject
    assertEquals("2026-11-05", day.getValue("date").jsonPrimitive.content)
    val item = day.getValue("items").jsonArray.single() as JsonObject
    assertEquals(setOf("item_key", "title", "folder", "category", "seconds", "plays"), item.keys)
  }

  @Test
  fun `only the last 7 days are sent`() {
    val entries =
      (0..9).map { StudyEntry(today.minusDays(it.toLong()).toString(), "F", "a.mp3", 60_000, 0) }

    val dates = request(entries)!!.days.map { it.date }

    assertEquals(
      (6 downTo 0).map { today.minusDays(it.toLong()).toString() },
      dates,
    )
  }

  @Test
  fun `a day more than one day ahead of china is left out, as the server would refuse everything`() {
    val entries =
      listOf(
        StudyEntry("2026-11-05", "F", "a.mp3", 60_000, 0),
        StudyEntry("2026-11-07", "F", "a.mp3", 60_000, 0),
      )

    assertEquals(
      listOf("2026-11-05"),
      request(entries, china = LocalDate.of(2026, 11, 5))!!.days.map { it.date },
    )
  }

  @Test
  fun `seconds stay within a day, and the items of a day add up to at most a day`() {
    val request =
      request(
        listOf(
          StudyEntry("2026-11-05", "F", "long.mp3", 90_000_000, 0),
          StudyEntry("2026-11-05", "F", "other.mp3", 10_000_000, 0),
        ),
      )!!

    val items = request.days.single().items
    assertTrue(items.all { it.seconds in 0..86_400 })
    assertEquals(86_400, items.sumOf { it.seconds })
    assertEquals("F/long.mp3", items.first().itemKey)
  }

  @Test
  fun `plays stay within the limit, and items with nothing to say are left out`() {
    val request =
      request(
        listOf(
          StudyEntry("2026-11-05", "F", "a.mp3", 60_000, 99_999),
          StudyEntry("2026-11-05", "F", "b.mp3", 400, 0),
        ),
      )!!

    assertEquals(listOf(10_000), request.days.single().items.map { it.plays })
    assertEquals(listOf("F/a.mp3"), request.days.single().items.map { it.itemKey })
  }

  @Test
  fun `each item says what its folder is, as the listener or the detection labelled it`() {
    val request =
      studySyncRequest(
        entries =
          listOf(
            StudyEntry("2026-11-05", "S0106edition", "a.mp3", 60_000, 0),
            StudyEntry("2026-11-05", "Dune", "ch1.mp3", 50_000, 0),
            StudyEntry("2026-11-05", "21 Day Meditation", "day1.mp3", 40_000, 0),
          ),
        today = today,
        chinaToday = chinaToday,
        deviceId = "SM-S9180",
        appVersion = "1.0.0",
        labels =
          mapOf(
            "S0106edition" to FolderCategory(StudyCategory.Language),
            "Dune" to FolderCategory(StudyCategory.Meditation, manual = true),
          ),
      )!!

    assertEquals(
      mapOf(
        "S0106edition/a.mp3" to "language",
        "Dune/ch1.mp3" to "meditation",
        "21 Day Meditation/day1.mp3" to "meditation",
      ),
      request.days.single().items.associate { it.itemKey to it.category },
    )
  }

  @Test
  fun `nothing to send without study time in the last days`() {
    assertNull(request(emptyList()))
    assertNull(request(listOf(StudyEntry("2026-10-01", "F", "a.mp3", 60_000, 0))))
  }
}
