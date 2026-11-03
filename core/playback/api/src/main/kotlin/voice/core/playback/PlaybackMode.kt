package voice.core.playback

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import voice.core.datastore.VoiceDataStoreFactory

/** Applies to the audio files of a book, not to the chapters inside a file. */
@Serializable
enum class PlaybackMode {
  Sequential,
  SingleTrackLoop,
  Shuffle,
  FolderLoop,
}

@SingleIn(AppScope::class)
class PlaybackModeStore(store: DataStore<PlaybackMode>) : DataStore<PlaybackMode> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = PlaybackMode.serializer(),
      defaultValue = PlaybackMode.Sequential,
      fileName = "playbackMode",
    ),
  )
}
