package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import voice.core.ui.PlayButton

@Composable
internal fun PlaybackRow(
  playing: Boolean,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  leadingControl: (@Composable () -> Unit)? = null,
) {
  if (leadingControl != null) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.weight(1F),
        contentAlignment = Alignment.Center,
      ) {
        leadingControl()
      }
      Box(
        modifier = Modifier.weight(1F),
        contentAlignment = Alignment.Center,
      ) {
        SkipButton(forward = false, onClick = onRewindClick)
      }
      Box(
        modifier = Modifier.weight(1F),
        contentAlignment = Alignment.Center,
      ) {
        PlayButton(playing = playing, fabSize = 80.dp, iconSize = 36.dp, onPlayClick = onPlayClick)
      }
      Box(
        modifier = Modifier.weight(1F),
        contentAlignment = Alignment.Center,
      ) {
        SkipButton(forward = true, onClick = onFastForwardClick)
      }
      Spacer(modifier = Modifier.weight(1F))
    }
    return
  }

  Row(
    modifier = Modifier
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    SkipButton(forward = false, onClick = onRewindClick)
    Spacer(modifier = Modifier.size(16.dp))

    PlayButton(playing = playing, fabSize = 80.dp, iconSize = 36.dp, onPlayClick = onPlayClick)
    Spacer(modifier = Modifier.size(16.dp))
    SkipButton(forward = true, onClick = onFastForwardClick)
  }
}
