package voice.core.playback.stats

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.playback.session.bookId
import voice.core.playback.session.realChapterId
import voice.core.playback.session.toMediaIdOrNull
import voice.core.studystats.StudyCategorizer
import voice.core.studystats.StudyNames
import voice.core.studystats.StudyStats
import voice.core.studystats.studyNames

/**
 * Records how long each file plays per day, for the study stats of the subtitle fork. Attached to
 * the player that holds one media item per file, so a file repeating or ending is a transition.
 *
 * A file counts as played when most of its audio was heard since it last ended, so skipping to the
 * end doesn't count, while pauses and other speeds don't matter.
 */
@SingleIn(AppScope::class)
@Inject
class StudyTimeRecorder(
  private val stats: StudyStats,
  private val categorizer: StudyCategorizer,
  private val clock: Clock,
  private val scope: CoroutineScope,
) {

  private var current: StudyNames? = null
  private var durationMillis = 0L
  private var heardAudioMillis = 0L
  private var speed = 1F
  private var playing = false
  private var countedUntil = 0L
  private var checkpoints: Job? = null
  private val pending = mutableMapOf<Pair<LocalDate, StudyNames>, Pending>()
  private val writes = Mutex()

  fun attachTo(player: Player) {
    player.addListener(
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isPlaying) {
            player.currentMediaItem?.let(::started)
            speedChanged(player.playbackParameters.speed)
            player.duration.takeIf { it != C.TIME_UNSET }?.let(::durationKnown)
          } else {
            stopped()
          }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
          speedChanged(playbackParameters.speed)
        }

        override fun onTimelineChanged(
          timeline: Timeline,
          reason: Int,
        ) {
          player.duration.takeIf { it != C.TIME_UNSET }?.let(::durationKnown)
        }

        override fun onMediaItemTransition(
          mediaItem: MediaItem?,
          reason: Int,
        ) {
          if (
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
              reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
          ) {
            current?.let(::ended)
          }
          if (player.isPlaying) {
            mediaItem?.let(::started)
          }
          player.duration.takeIf { it != C.TIME_UNSET }?.let(::durationKnown)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_ENDED) {
            current?.let(::ended)
          }
        }
      },
    )
  }

  internal fun playing(names: StudyNames) {
    if (playing && current == names) return
    count()
    if (current != names) {
      current = names
      durationMillis = 0
      heardAudioMillis = 0
    }
    if (playing) return
    playing = true
    countedUntil = clock.millis()
    checkpoints = scope.launch {
      while (true) {
        delay(CHECKPOINT_INTERVAL)
        count()
        save()
      }
    }
  }

  internal fun stopped() {
    if (!playing) return
    count()
    playing = false
    checkpoints?.cancel()
    checkpoints = null
    save()
  }

  internal fun durationKnown(durationMillis: Long) {
    this.durationMillis = durationMillis
  }

  internal fun speedChanged(speed: Float) {
    // what played so far played at the old speed
    count()
    this.speed = speed
  }

  internal fun ended(names: StudyNames) {
    count()
    if (durationMillis > 0 && heardAudioMillis >= durationMillis * HEARD_TO_COUNT_AS_PLAYED) {
      pending.getOrPut(today() to names) { Pending() }.plays++
    }
    heardAudioMillis = 0
    save()
  }

  private fun count() {
    if (!playing) return
    val names = current ?: return
    val now = clock.millis()
    // the clock can be changed, so only count what fits in between two checkpoints
    val elapsed = (now - countedUntil).coerceIn(0, MAX_COUNTED_AT_ONCE)
    pending.getOrPut(today() to names) { Pending() }.listenedMillis += elapsed
    heardAudioMillis += (elapsed * speed).roundToLong()
    countedUntil = now
  }

  private fun save() {
    if (pending.isEmpty()) return
    val snapshot = pending.toMap()
    pending.clear()
    scope.launch {
      writes.withLock {
        snapshot.forEach { (key, value) ->
          stats.add(key.first, key.second, value.listenedMillis, value.plays)
        }
      }
    }
  }

  private fun started(item: MediaItem) {
    val mediaId = item.mediaId.toMediaIdOrNull() ?: return
    val bookId = mediaId.bookId ?: return
    val chapterId = mediaId.realChapterId ?: return
    playing(studyNames(bookId, chapterId))
    categorizer.onPlaying(bookId, chapterId)
  }

  private fun today(): LocalDate = LocalDate.now(clock)

  private class Pending {
    var listenedMillis = 0L
    var plays = 0
  }

  private companion object {
    val CHECKPOINT_INTERVAL = 30.seconds
    val MAX_COUNTED_AT_ONCE = 2.minutes.inWholeMilliseconds
    const val HEARD_TO_COUNT_AS_PLAYED = 0.9
  }
}
