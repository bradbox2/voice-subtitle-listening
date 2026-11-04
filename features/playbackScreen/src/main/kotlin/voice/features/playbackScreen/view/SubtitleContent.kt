@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.playbackScreen.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        chapterName = subtitles.fileName,
        chapterNumber = viewState.chapterNumber,
        chapterCount = viewState.chapterCount,
        onSkipToPrevious = onSkipToPrevious,
        onSkipToNext = onSkipToNext,
        onChapterClick = onCurrentChapterClick,
      )
    } else {
      Text(
        text = subtitles.fileName,
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
    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
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
    // one piece, so wrapping never separates the buttons from the value
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = { actions.onOffsetChange(-SUBTITLE_OFFSET_STEP_MS) },
        modifier = Modifier.size(40.dp),
      ) {
        Icon(
          imageVector = VoiceIcons.Remove,
          contentDescription = stringResource(StringsR.string.playback_subtitles_sync_earlier),
        )
      }
      val offset = formatSubtitleOffset(subtitles.offsetMs)
      val syncDescription = stringResource(StringsR.string.playback_subtitles_sync, offset)
      Text(
        text = offset,
        style = MaterialTheme.typography.labelLarge,
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .clickable(onClick = actions.onOffsetReset)
            .semantics { contentDescription = syncDescription }
            .padding(horizontal = 2.dp, vertical = 6.dp),
      )
      IconButton(
        onClick = { actions.onOffsetChange(SUBTITLE_OFFSET_STEP_MS) },
        modifier = Modifier.size(40.dp),
      ) {
        Icon(
          imageVector = VoiceIcons.Add,
          contentDescription = stringResource(StringsR.string.playback_subtitles_sync_later),
        )
      }
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
  BoxWithConstraints(modifier) {
    val listState = rememberLazyListState()
    val focusIndex = subtitles.activeIndex ?: subtitles.selectedIndex
    // The playing sentence always comes to rest at the same height, with a line or two before it,
    // so the eyes don't have to search for it. The top padding is that height, as a scrolled to
    // item rests right below it.
    val anchor = maxHeight * 0.2F
    LaunchedEffect(focusIndex) {
      // don't fight the listener scrolling to look something up
      if (focusIndex != null && !listState.isScrollInProgress) {
        listState.animateScrollToItem(focusIndex)
      }
    }
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      // lets the last sentences reach the anchor as well
      contentPadding = PaddingValues(top = anchor, bottom = maxHeight - anchor),
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
}

/**
 * Every line is laid out with the same size and weight, so no line wraps differently when the
 * playing sentence changes and the list below doesn't jump. The playing sentence only looks bigger
 * by drawing it scaled, which the row padding has room for.
 */
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
      1 -> 0.7F
      2 -> 0.55F
      else -> 0.45F
    }
  val scale by animateFloatAsState(if (active) ACTIVE_SCALE else 1F, label = "scale")
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clip(RoundedCornerShape(12.dp))
        // the strongest pair of the theme, so the playing sentence stands out in light and dark
        // themes. A tint of primary with primary text lost against the plain lines in dark mode.
        .background(if (active) colors.primary else Color.Transparent)
        .clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = cue.text,
      color =
        when {
          active -> colors.onPrimary
          selected -> colors.secondary
          else -> colors.onSurface.copy(alpha = alpha)
        },
      // large, as listeners read along at arm's length
      style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 25.sp),
      textAlign = TextAlign.Center,
      modifier = Modifier.weight(1F).padding(start = 40.dp, top = 12.dp, bottom = 12.dp),
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
      Spacer(Modifier.size(40.dp))
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
private const val ACTIVE_SCALE = 1.06F
