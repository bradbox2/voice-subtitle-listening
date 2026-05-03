package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.features.playbackScreen.BookPlayViewState
import kotlin.time.Duration

@Composable
internal fun BookPlayContent(
  contentPadding: PaddingValues,
  viewState: BookPlayViewState,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeek: (Duration) -> Unit,
  onSubtitleClick: (Long) -> Unit,
  onSubtitleStarClick: (String) -> Unit,
  onRepeatSentenceClick: () -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  useLandscapeLayout: Boolean,
) {
  val subtitles = viewState.subtitles
  if (subtitles != null && viewState.subtitleFocusModeEnabled && !useLandscapeLayout) {
    SubtitleFocusedContent(
      contentPadding = contentPadding,
      viewState = viewState,
      subtitles = subtitles,
      onPlayClick = onPlayClick,
      onRewindClick = onRewindClick,
      onFastForwardClick = onFastForwardClick,
      onSeek = onSeek,
      onSubtitleClick = onSubtitleClick,
      onSubtitleStarClick = onSubtitleStarClick,
      onRepeatSentenceClick = onRepeatSentenceClick,
      onSkipToNext = onSkipToNext,
      onSkipToPrevious = onSkipToPrevious,
      onCurrentChapterClick = onCurrentChapterClick,
    )
    return
  }

  if (useLandscapeLayout) {
    Row(Modifier.padding(contentPadding)) {
      CoverRow(
        cover = viewState.cover,
        onPlayClick = onPlayClick,
        sleepTimerState = viewState.sleepTimerState,
        modifier = Modifier
          .fillMaxHeight()
          .weight(1F)
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      )
      Column(
        modifier = Modifier
          .fillMaxHeight()
          .weight(1F),
        verticalArrangement = Arrangement.Center,
      ) {
        viewState.chapterName?.let { chapterName ->
          ChapterRow(
            chapterName = chapterName,
            nextPreviousVisible = viewState.showPreviousNextButtons,
            onSkipToNext = onSkipToNext,
            onSkipToPrevious = onSkipToPrevious,
            onCurrentChapterClick = onCurrentChapterClick,
          )
        }
        Spacer(modifier = Modifier.size(20.dp))
        SliderRow(
          duration = viewState.duration,
          playedTime = viewState.playedTime,
          onSeek = onSeek,
        )
        Spacer(modifier = Modifier.size(16.dp))
        PlaybackRow(
          playing = viewState.playing,
          onPlayClick = onPlayClick,
          onRewindClick = onRewindClick,
          onFastForwardClick = onFastForwardClick,
        )
      }
    }
  } else {
    Column(Modifier.padding(contentPadding)) {
      CoverRow(
        onPlayClick = onPlayClick,
        cover = viewState.cover,
        sleepTimerState = viewState.sleepTimerState,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1F)
          .padding(start = 16.dp, end = 16.dp, top = 8.dp),
      )
      viewState.chapterName?.let { chapterName ->
        Spacer(modifier = Modifier.size(16.dp))
        ChapterRow(
          chapterName = chapterName,
          nextPreviousVisible = viewState.showPreviousNextButtons,
          onSkipToNext = onSkipToNext,
          onSkipToPrevious = onSkipToPrevious,
          onCurrentChapterClick = onCurrentChapterClick,
        )
      }
      Spacer(modifier = Modifier.size(20.dp))
      SliderRow(
        duration = viewState.duration,
        playedTime = viewState.playedTime,
        onSeek = onSeek,
      )
      Spacer(modifier = Modifier.size(16.dp))
      PlaybackRow(
        playing = viewState.playing,
        onPlayClick = onPlayClick,
        onRewindClick = onRewindClick,
        onFastForwardClick = onFastForwardClick,
      )
      Spacer(modifier = Modifier.size(24.dp))
    }
  }
}

@Composable
private fun SubtitleFocusedContent(
  contentPadding: PaddingValues,
  viewState: BookPlayViewState,
  subtitles: BookPlayViewState.SubtitlePanelViewState,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeek: (Duration) -> Unit,
  onSubtitleClick: (Long) -> Unit,
  onSubtitleStarClick: (String) -> Unit,
  onRepeatSentenceClick: () -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCurrentChapterClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(contentPadding)
      .background(MaterialTheme.colorScheme.surface),
  ) {
    if (viewState.cover != null) {
      AsyncImage(
        model = viewState.cover.file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .blur(28.dp),
      )
    }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color.Black.copy(alpha = 0.78F),
              Color.Black.copy(alpha = 0.56F),
              Color.Black.copy(alpha = 0.86F),
            ),
          ),
        ),
    )
    CompositionLocalProvider(LocalContentColor provides Color.White) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(modifier = Modifier.size(4.dp))
        ChapterRow(
          chapterName = viewState.chapterName ?: viewState.title,
          nextPreviousVisible = true,
          onSkipToNext = onSkipToNext,
          onSkipToPrevious = onSkipToPrevious,
          onCurrentChapterClick = onCurrentChapterClick,
          compact = true,
        )
        SubtitlePanel(
          viewState = subtitles,
          onSubtitleClick = onSubtitleClick,
          onSubtitleStarClick = onSubtitleStarClick,
          focused = true,
          modifier = Modifier
            .weight(1F)
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp),
        )
        SliderRow(
          duration = viewState.duration,
          playedTime = viewState.playedTime,
          onSeek = onSeek,
        )
        Spacer(modifier = Modifier.size(8.dp))
        PlaybackRow(
          playing = viewState.playing,
          onPlayClick = onPlayClick,
          onRewindClick = onRewindClick,
          onFastForwardClick = onFastForwardClick,
          leadingControl = {
            RepeatSentenceButton(
              enabled = subtitles.repeatSentenceEnabled,
              onClick = onRepeatSentenceClick,
            )
          },
        )
        Spacer(modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun RepeatSentenceButton(
  enabled: Boolean,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick) {
    Icon(
      imageVector = Icons.Outlined.RepeatOne,
      contentDescription = "Repeat sentence",
      tint = if (enabled) {
        MaterialTheme.colorScheme.primary
      } else {
        LocalContentColor.current
      },
    )
  }
}
