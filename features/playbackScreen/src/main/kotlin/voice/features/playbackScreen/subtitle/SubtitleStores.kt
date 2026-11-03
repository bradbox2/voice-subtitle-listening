package voice.features.playbackScreen.subtitle

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import voice.core.datastore.VoiceDataStoreFactory

@SingleIn(AppScope::class)
class SubtitleFocusModeStore(store: DataStore<Boolean>) : DataStore<Boolean> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(factory.boolean("subtitleFocusModeEnabled", defaultValue = true))
}

@SingleIn(AppScope::class)
class StarredSubtitleCuesStore(store: DataStore<Set<String>>) : DataStore<Set<String>> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = SetSerializer(String.serializer()),
      defaultValue = emptySet(),
      fileName = "starredSubtitleCueKeys",
    ),
  )
}

@SingleIn(AppScope::class)
class SubtitleOffsetsStore(store: DataStore<Map<String, Long>>) :
  DataStore<Map<String, Long>> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = MapSerializer(String.serializer(), Long.serializer()),
      defaultValue = emptyMap(),
      fileName = "subtitleOffsets",
    ),
  )
}
