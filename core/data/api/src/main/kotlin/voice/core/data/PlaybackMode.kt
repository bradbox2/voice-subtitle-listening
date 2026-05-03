package voice.core.data

import kotlinx.serialization.Serializable

@Serializable
public enum class PlaybackMode {
  Sequential,
  SingleTrackLoop,
  Shuffle,
  FolderLoop,
}
