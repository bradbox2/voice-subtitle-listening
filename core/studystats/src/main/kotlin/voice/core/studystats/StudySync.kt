package voice.core.studystats

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import voice.core.datastore.VoiceDataStoreFactory
import voice.core.logging.api.Logger

/** Where to send the study time, kept on the device only, see the backup rules. */
@Serializable
data class StudySyncSettings(
  val url: String = "",
  val token: String = "",
  val lastSuccessMillis: Long = 0,
) {
  val configured: Boolean
    get() = url.isNotBlank() && token.isNotBlank()
}

@SingleIn(AppScope::class)
class StudySyncSettingsStore(store: DataStore<StudySyncSettings>) :
  DataStore<StudySyncSettings> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = StudySyncSettings.serializer(),
      defaultValue = StudySyncSettings(),
      fileName = "studySync",
    ),
  )
}

class StudySyncDevice(val id: String, val appVersion: String) {

  @Inject
  constructor(
    context: Context,
  ) : this(
    id = Build.MODEL.takeIf { it.isNotBlank() }?.take(40) ?: "android",
    appVersion =
      context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown",
  )
}

data class TransportResponse(val code: Int, val body: String)

interface StudySyncTransport {

  /** @throws IOException when the server can't be reached. */
  suspend fun post(
    url: String,
    token: String,
    json: String,
  ): TransportResponse
}

@ContributesBinding(AppScope::class)
@Inject
class HttpStudySyncTransport : StudySyncTransport {

  override suspend fun post(
    url: String,
    token: String,
    json: String,
  ): TransportResponse =
    withContext(Dispatchers.IO) {
      val connection = URL(url).openConnection() as java.net.HttpURLConnection
      try {
        connection.requestMethod = "POST"
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.doOutput = true
        connection.setRequestProperty("X-Study-Token", token)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code < 400) connection.inputStream else connection.errorStream
        TransportResponse(code, stream?.use { it.readBytes().decodeToString() }.orEmpty())
      } finally {
        connection.disconnect()
      }
    }

  private companion object {
    const val TIMEOUT_MILLIS = 15_000
  }
}

sealed interface StudySyncResult {
  data object NotConfigured : StudySyncResult

  data object NothingToSend : StudySyncResult

  data object BadAddress : StudySyncResult

  data object BadToken : StudySyncResult

  data object Rejected : StudySyncResult

  data object ServerTokenMissing : StudySyncResult

  data object NoConnection : StudySyncResult

  data class ServerError(val code: Int) : StudySyncResult

  data class Done(val daysWritten: Int, val lastDate: String?) : StudySyncResult
}

@Serializable
private data class StudySyncResponse(
  @SerialName("days_written") val daysWritten: Int = 0,
  @SerialName("last_date") val lastDate: String? = null,
)

/**
 * Sends the study time of the last week to the listener's own server, see the sync API of
 * GoalSifter. The server replaces each date it gets as a whole, so sending the same days again is
 * fine, and a failed sync is simply sent again next time.
 */
@SingleIn(AppScope::class)
@Inject
class StudySync(
  private val stats: StudyStats,
  private val categories: StudyCategories,
  private val settings: StudySyncSettingsStore,
  private val transport: StudySyncTransport,
  private val device: StudySyncDevice,
  private val clock: Clock,
) {

  private val writes = Mutex()
  private var lastVisibleAttempt: Long? = null
  private val _lastResult = MutableStateFlow<StudySyncResult?>(null)

  /** What the latest sync of this run came to. */
  val lastResult: StateFlow<StudySyncResult?>
    get() = _lastResult

  val settingsFlow
    get() = settings.data

  suspend fun configure(
    url: String,
    token: String,
  ) {
    settings.updateData { it.copy(url = url.trim(), token = token.trim()) }
  }

  @IgnorableReturnValue
  suspend fun sync(): StudySyncResult = writes.withLock { send().also { _lastResult.value = it } }

  /** Coming back to the app, which can happen often, so not every time. */
  suspend fun syncOnVisible() {
    val now = clock.millis()
    lastVisibleAttempt?.let { if (now - it < VISIBLE_INTERVAL_MILLIS) return }
    if (!settings.data.first().configured) return
    lastVisibleAttempt = now
    sync()
  }

  suspend fun syncAfterListening() {
    if (!settings.data.first().configured) return
    sync()
  }

  private suspend fun send(): StudySyncResult {
    val config = settings.data.first()
    if (!config.configured) return StudySyncResult.NotConfigured
    if (!config.url.isHttpAddress()) return StudySyncResult.BadAddress
    val request =
      studySyncRequest(
        entries = stats.entries.first(),
        today = LocalDate.now(clock),
        chinaToday = LocalDate.now(clock.withZone(CHINA)),
        deviceId = device.id,
        appVersion = device.appVersion,
        labels = categories.labels.first(),
      ) ?: return StudySyncResult.NothingToSend
    val response =
      try {
        transport.post(config.url, config.token, studySyncJson(request))
      } catch (e: IOException) {
        Logger.d("Study sync can't reach the server: ${e::class.simpleName}")
        return StudySyncResult.NoConnection
      }
    val result =
      when (response.code) {
        200 -> response.toDone() ?: StudySyncResult.ServerError(200)
        401 -> StudySyncResult.BadToken
        422 -> StudySyncResult.Rejected
        503 -> StudySyncResult.ServerTokenMissing
        else -> StudySyncResult.ServerError(response.code)
      }
    if (result is StudySyncResult.Done) {
      settings.updateData { it.copy(lastSuccessMillis = clock.millis()) }
    }
    Logger.d("Study sync: ${result::class.simpleName}")
    return result
  }

  private fun TransportResponse.toDone(): StudySyncResult.Done? {
    return try {
      val parsed = lenientJson.decodeFromString<StudySyncResponse>(body)
      StudySyncResult.Done(parsed.daysWritten, parsed.lastDate)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  private companion object {
    val CHINA: ZoneId = ZoneId.of("Asia/Shanghai")
    val VISIBLE_INTERVAL_MILLIS = 3.minutes.inWholeMilliseconds
  }
}

internal fun String.isHttpAddress(): Boolean {
  if (!startsWith("http://") && !startsWith("https://")) return false
  return try {
    URI(this).host?.isNotBlank() == true
  } catch (_: URISyntaxException) {
    false
  }
}

private val lenientJson = Json { ignoreUnknownKeys = true }
