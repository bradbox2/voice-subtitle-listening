package voice.core.studystats

import androidx.datastore.core.DataStore
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest

class StudySyncTest {

  private val statsStore = MemoryStore(listOf(StudyEntry("2026-11-05", "F", "a.mp3", 120_000, 1)))
  private val settingsStore =
    MemoryStore(StudySyncSettings(url = "http://example.test/days", token = "secret"))
  private val transport = FakeTransport()
  private val clock = MutableClock(Instant.parse("2026-11-05T13:00:00Z"))
  private val sync =
    StudySync(
      stats = StudyStats(StudyStatsStore(statsStore)),
      settings = StudySyncSettingsStore(settingsStore),
      transport = transport,
      device = StudySyncDevice(id = "SM-S9180", appVersion = "1.0.0"),
      clock = clock,
    )

  @Test
  fun `the last days are sent to the address with the token, and the time is remembered`() =
    runTest {
      transport.response =
        TransportResponse(
          200,
          """{"ok":true,"days_written":1,"last_date":"2026-11-05","received_at":"x","extra":1}""",
        )

      val result = sync.sync()

      assertEquals(StudySyncResult.Done(daysWritten = 1, lastDate = "2026-11-05"), result)
      val call = transport.calls.single()
      assertEquals("http://example.test/days", call.url)
      assertEquals("secret", call.token)
      assertEquals(
        studySyncJson(
          studySyncRequest(
            statsStore.value.value,
            java.time.LocalDate.of(2026, 11, 5),
            java.time.LocalDate.of(2026, 11, 5),
            "SM-S9180",
            "1.0.0",
          )!!,
        ),
        call.json,
      )
      assertEquals(clock.millis(), settingsStore.data.first().lastSuccessMillis)
      assertEquals(result, sync.lastResult.value)
    }

  @Test
  fun `nothing is sent without an address and a token`() = runTest {
    settingsStore.updateData { StudySyncSettings(url = "http://example.test/days", token = " ") }

    assertEquals(StudySyncResult.NotConfigured, sync.sync())
    assertEquals(emptyList(), transport.calls)
  }

  @Test
  fun `nothing is sent for an address that isn't one`() = runTest {
    listOf("example.test/days", "ftp://example.test", "http://", "http://exa mple").forEach { url ->
      settingsStore.updateData { it.copy(url = url) }

      assertEquals(StudySyncResult.BadAddress, sync.sync(), url)
    }
    assertEquals(emptyList(), transport.calls)
  }

  @Test
  fun `no study time in the last week sends nothing`() = runTest {
    statsStore.updateData { emptyList() }

    assertEquals(StudySyncResult.NothingToSend, sync.sync())
    assertEquals(emptyList(), transport.calls)
  }

  @Test
  fun `what the server answers is told apart, and only success is remembered`() = runTest {
    mapOf(
        401 to StudySyncResult.BadToken,
        422 to StudySyncResult.Rejected,
        503 to StudySyncResult.ServerTokenMissing,
        500 to StudySyncResult.ServerError(500),
        200 to StudySyncResult.ServerError(200), // not what the server says
      )
      .forEach { (code, expected) ->
        transport.response = TransportResponse(code, "<html>")

        assertEquals(expected, sync.sync(), "code $code")
      }
    assertEquals(0, settingsStore.data.first().lastSuccessMillis)
  }

  @Test
  fun `no connection is a result, not a crash`() = runTest {
    transport.failure = IOException("down")

    assertEquals(StudySyncResult.NoConnection, sync.sync())
  }

  @Test
  fun `coming back to the app syncs at most every few minutes`() = runTest {
    transport.response = TransportResponse(200, """{"ok":true,"days_written":1}""")

    sync.syncOnVisible()
    clock.advance(java.time.Duration.ofMinutes(1))
    sync.syncOnVisible()
    assertEquals(1, transport.calls.size)

    clock.advance(java.time.Duration.ofMinutes(3))
    sync.syncOnVisible()
    assertEquals(2, transport.calls.size)
  }

  @Test
  fun `after listening it syncs right away, but only when set up`() = runTest {
    transport.response = TransportResponse(200, """{"ok":true,"days_written":1}""")
    sync.syncOnVisible()

    sync.syncAfterListening()
    assertEquals(2, transport.calls.size)

    settingsStore.updateData { StudySyncSettings() }
    sync.syncAfterListening()
    assertEquals(2, transport.calls.size)
    assertIs<StudySyncResult.Done>(sync.lastResult.value)
  }

  @Test
  fun `a changed address and token are used for the next sync`() = runTest {
    transport.response = TransportResponse(200, """{"ok":true,"days_written":1}""")

    sync.configure(url = " https://new.test/days ", token = " token2\n")
    sync.sync()

    assertEquals("https://new.test/days", transport.calls.single().url)
    assertEquals("token2", transport.calls.single().token)
  }

  private class FakeTransport : StudySyncTransport {
    data class Call(val url: String, val token: String, val json: String)

    val calls = mutableListOf<Call>()
    var response = TransportResponse(200, "{}")
    var failure: IOException? = null

    override suspend fun post(
      url: String,
      token: String,
      json: String,
    ): TransportResponse {
      calls += Call(url, token, json)
      failure?.let { throw it }
      return response
    }
  }

  private class MemoryStore<T>(initial: T) : DataStore<T> {
    val value = MutableStateFlow(initial)
    override val data: Flow<T> = value

    override suspend fun updateData(transform: suspend (t: T) -> T): T = value.updateAndGet {
      transform(it)
    }
  }

  private class MutableClock(private var now: Instant) : Clock() {
    fun advance(by: java.time.Duration) {
      now = now.plus(by)
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneId.of("Asia/Shanghai")

    override fun withZone(zone: ZoneId): Clock = this
  }
}
