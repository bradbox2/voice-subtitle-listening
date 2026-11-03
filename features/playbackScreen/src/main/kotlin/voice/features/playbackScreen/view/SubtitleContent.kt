@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.playbackScreen.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.time.Duration
import voice.core.data.BookId
import voice.core.playback.PlaybackMode
import voice.core.strings.R as StringsR
import voice.core.ui.icons.VoiceIcons
import voice.features.playbackScreen.BookPlayViewState
import voice.features.playbackScreen.subtitle.SUBTITLE_OFFSET_STEP_MS
import voice.features.playbackScreen.subtitle.SubtitlesViewState
import voice.features.playbackScreen.subtitle.formatSubtitleOffset

@Immutable
internal class SubtitleActions(
  val onCueClick: (startMs: Long) -> Unit,
  val onStarClick: (starKey: String) -> Unit,
  val onRepeatClick: () -> Unit,
  val onPlaybackModeClick: () -> Unit,
  val onFocusModeClick: () -> Unit,
  val onSpeedClick: (Float) -> Unit,
  val onOffsetChange: (deltaMs: Long) -> Unit,
  val onOffsetReset: () -> Unit,
) {
  companion object {
    val None = SubtitleActions({}, {}, {}, {}, {}, {}, {}, {})
  }
}

@Composable
internal fun SubtitleContent(
  contentPadding: PaddingValues,
  viewState: BookPlayViewState,
  subtitles: SubtitlesViewState,
  bookId: BookId,
  clock: () -> Float,
  actions: SubtitleActions,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeek: (Duration) -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  onJumpBack: (id: Long) -> Unit,
  onJumpBackExpire: (id: Long) -> Unit,
) {
  // reading along needs the screen, a paused screen may go off as usual
  if (viewState.playing) {
    val view = LocalView.current
    DisposableEffect(view) {
      view.keepScreenOn = true
      onDispose { view.keepScreenOn = false }
    }
  }
  var toolsExpanded by rememberSaveable { mutableStateOf(false) }
  Column(
    modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (viewState.showPreviousNextButtons) {
      ChapterSwitcher(
        chapterName = viewState.chapterName,
        chapterNumber = viewState.chapterNumber,
        chapterCount = viewState.chapterCount,
        onSkipToPrevious = onSkipToPrevious,
        onSkipToNext = onSkipToNext,
        onChapterClick = onCurrentChapterClick,
      )
    } else {
      Text(
        text = viewState.title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      )
    }
    SubtitleList(
      subtitles = subtitles,
      onCueClick = actions.onCueClick,
      onStarClick = actions.onStarClick,
      modifier = Modifier.weight(1F).fillMaxWidth(),
    )
    AnimatedVisibility(visible = toolsExpanded) {
      SubtitleTools(subtitles = subtitles, actions = actions)
    }
    MediaControlsDirection {
      SeekSection(
        playedTime = viewState.playedTime,
        duration = viewState.duration,
        playing = viewState.playing,
        clock = clock,
        onSeek = onSeek,
        jumpBack = viewState.jumpBack,
        unavailable = viewState.unavailable,
        onJumpBack = onJumpBack,
        onJumpBackExpire = onJumpBackExpire,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    MediaControlsDirection {
      Row(
        modifier = Modifier.fillMaxWidth().height(88.dp).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PlaybackModeButton(mode = subtitles.playbackMode, onClick = actions.onPlaybackModeClick)
        TransportControls(
          bookId = bookId,
          playing = viewState.playing,
          skipSeconds = viewState.skipSeconds,
          onRewindClick = onRewindClick,
          onPlayClick = onPlayClick,
          onFastForwardClick = onFastForwardClick,
          modifier = Modifier.weight(1F).fillMaxSize(),
        )
        FilledTonalIconToggleButton(
          checked = toolsExpanded,
          onCheckedChange = { toolsExpanded = it },
          shapes = IconButtonDefaults.toggleableShapes(),
          modifier = Modifier.width(64.dp).height(48.dp),
        ) {
          Icon(
            imageVector = VoiceIcons.Settings,
            contentDescription = stringResource(StringsR.string.playback_subtitles_tools),
          )
        }
      }
    }
  }
}

@Composable
private fun SubtitleTools(
  subtitles: SubtitlesViewState,
  actions: SubtitleActions,
) {
  val speedFormat = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
  // wraps on narrow phones instead of cutting off the sync
  FlowRow(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
  ) {
    FilledTonalIconToggleButton(
      checked = subtitles.repeatSentence,
      onCheckedChange = { actions.onRepeatClick() },
      shapes = IconButtonDefaults.toggleableShapes(),
    ) {
      Icon(
        imageVector = VoiceIcons.Replay,
        contentDescription = stringResource(StringsR.string.playback_subtitles_repeat),
      )
    }
    subtitles.speedOptions.forEach { option ->
      FilterChip(
        selected = option.selected,
        onClick = { actions.onSpeedClick(option.speed) },
        label = { Text(speedFormat.format(option.speed)) },
      )
    }
    IconButton(onClick = { actions.onOffsetChange(-SUBTITLE_OFFSET_STEP_MS) }) {
      Icon(
        imageVector = VoiceIcons.Remove,
        contentDescription = stringResource(StringsR.string.playback_subtitles_sync_earlier),
      )
    }
    Text(
      text =
        stringResource(
          StringsR.string.playback_subtitles_sync,
          formatSubtitleOffset(subtitles.offsetMs),
        ),
      style = MaterialTheme.typography.labelMedium,
      modifier =
        Modifier.clip(RoundedCornerShape(6.dp))
          .clickable(onClick = actions.onOffsetReset)
          .padding(horizontal = 4.dp, vertical = 6.dp),
    )
    IconButton(onClick = { actions.onOffsetChange(SUBTITLE_OFFSET_STEP_MS) }) {
      Icon(
        imageVector = VoiceIcons.Add,
        contentDescription = stringResource(StringsR.string.playback_subtitles_sync_later),
      )
    }
  }
}

@Composable
private fun SubtitleList(
  subtitles: SubtitlesViewState,
  onCueClick: (Long) -> Unit,
  onStarClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val focusIndex = subtitles.activeIndex ?: subtitles.selectedIndex
  LaunchedEffect(focusIndex) {
    // don't fight the listener scrolling to look something up
    if (focusIndex != null && !listState.isScrollInProgress) {
      listState.animateScrollToItem((focusIndex - 3).coerceAtLeast(0))
    }
  }
  LazyColumn(
    state = listState,
    modifier = modifier,
    contentPadding = PaddingValues(vertical = 16.dp),
  ) {
    itemsIndexed(items = subtitles.cues, key = { _, cue -> cue.starKey }) { index, cue ->
      SubtitleRow(
        cue = cue,
        active = index == subtitles.activeIndex,
        selected = index == subtitles.selectedIndex,
        distance = focusIndex?.let { abs(index - it) },
        onClick = { onCueClick(cue.startMs) },
        onStarClick = { onStarClick(cue.starKey) },
      )
    }
  }
}

@Composable
private fun SubtitleRow(
  cue: SubtitlesViewState.Cue,
  active: Boolean,
  selected: Boolean,
  distance: Int?,
  onClick: () -> Unit,
  onStarClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val alpha =
    when (distance) {
      0 -> 1F
      1 -> 0.84F
      2 -> 0.68F
      else -> 0.45F
    }
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = cue.text,
      color =
        when {
          active -> colors.primary
          selected -> colors.secondary
          else -> colors.onSurface.copy(alpha = alpha)
        },
      style =
        when (distance) {
          0 -> MaterialTheme.typography.titleLarge
          1 -> MaterialTheme.typography.titleMedium
          else -> MaterialTheme.typography.bodyLarge
        },
      fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal,
      textAlign = TextAlign.Center,
      modifier = Modifier.weight(1F).padding(start = 40.dp, top = 9.dp, bottom = 9.dp),
    )
    if (selected || cue.starred) {
      IconButton(onClick = onStarClick, modifier = Modifier.size(40.dp)) {
        Icon(
          imageVector = VoiceIcons.Star,
          contentDescription =
            stringResource(
              if (cue.starred) StringsR.string.playback_subtitles_unstar
              else StringsR.string.playback_subtitles_star,
            ),
          tint = if (cue.starred) StarColor else colors.onSurfaceVariant,
        )
      }
    } else {
      Row(Modifier.size(40.dp)) {}
    }
  }
}

@Composable
private fun PlaybackModeButton(
  mode: PlaybackMode,
  onClick: () -> Unit,
) {
  val label =
    stringResource(
      when (mode) {
        PlaybackMode.Sequential -> StringsR.string.playback_mode_sequential
        PlaybackMode.SingleTrackLoop -> StringsR.string.playback_mode_single_track_loop
        PlaybackMode.Shuffle -> StringsR.string.playback_mode_shuffle
        PlaybackMode.FolderLoop -> StringsR.string.playback_mode_folder_loop
      },
    )
  FilledTonalButton(
    onClick = onClick,
    shapes = ButtonDefaults.shapes(),
    contentPadding = PaddingValues(horizontal = 8.dp),
    modifier = Modifier.width(64.dp).height(48.dp),
  ) {
    Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

private val StarColor = Color(0xFFFFD54F)
