package voice.features.playbackScreen.view

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BedtimeOff
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.features.playbackScreen.BookPlayViewState

@Composable
internal fun BookPlayAppBar(
  viewState: BookPlayViewState,
  onSleepTimerClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onBookmarkLongClick: () -> Unit,
  onSpeedChangeClick: () -> Unit,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
  onRefreshClick: () -> Unit,
  onSubtitleFocusModeClick: () -> Unit,
  onCloseClick: () -> Unit,
  useLandscapeLayout: Boolean,
) {
  val appBarActions: @Composable RowScope.() -> Unit = {
    if (viewState.subtitles != null) {
      IconButton(onClick = onSubtitleFocusModeClick) {
        Icon(
          imageVector = if (viewState.subtitleFocusModeEnabled) Icons.Outlined.Image else Icons.Outlined.Subtitles,
          contentDescription = if (viewState.subtitleFocusModeEnabled) {
            "Switch to cover mode"
          } else {
            "Switch to subtitle mode"
          },
        )
      }
    }
    IconButton(onClick = onRefreshClick) {
      Icon(
        imageVector = Icons.Outlined.Refresh,
        contentDescription = "Refresh subtitles",
      )
    }
    IconButton(onClick = onSleepTimerClick) {
      Icon(
        imageVector = if (viewState.sleepTimerState is BookPlayViewState.SleepTimerViewState.Disabled) {
          Icons.Outlined.Bedtime
        } else {
          Icons.Outlined.BedtimeOff
        },
        contentDescription = stringResource(id = R.string.action_sleep),
      )
    }
    Box(
      modifier = Modifier
        .size(40.dp)
        .combinedClickable(
          onClick = onBookmarkClick,
          onLongClick = onBookmarkLongClick,
          indication = ripple(bounded = false, radius = 20.dp),
          interactionSource = remember { MutableInteractionSource() },
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.CollectionsBookmark,
        contentDescription = stringResource(id = R.string.bookmark),
      )
    }
    IconButton(onClick = onSpeedChangeClick) {
      Icon(
        imageVector = Icons.Outlined.Speed,
        contentDescription = stringResource(id = R.string.playback_speed),
      )
    }
    OverflowMenu(
      skipSilence = viewState.skipSilence,
      onSkipSilenceClick = onSkipSilenceClick,
      onVolumeBoostClick = onVolumeBoostClick,
    )
  }
  val subtitleFocusMode = viewState.subtitles != null && viewState.subtitleFocusModeEnabled
  if (useLandscapeLayout || subtitleFocusMode) {
    TopAppBar(
      navigationIcon = {
        CloseIcon(onCloseClick)
      },
      actions = appBarActions,
      title = {
        if (!subtitleFocusMode) {
          AppBarTitle(viewState.title)
        }
      },
    )
  } else {
    LargeTopAppBar(
      navigationIcon = {
        CloseIcon(onCloseClick)
      },
      actions = appBarActions,
      title = {
        AppBarTitle(viewState.title)
      },
    )
  }
}
