package voice.features.playbackScreen.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.strings.R

@Composable
internal fun ChapterRow(
  chapterName: String,
  nextPreviousVisible: Boolean,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  compact: Boolean = false,
) {
  val iconSize = if (compact) 32.dp else 36.dp
  val edgePadding = if (compact) 0.dp else 8.dp
  val textStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
  val rowAlpha = if (compact) 0.86F else 1F
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = edgePadding)
      .alpha(rowAlpha),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (nextPreviousVisible) {
      IconButton(onClick = onSkipToPrevious) {
        Icon(
          modifier = Modifier.size(iconSize),
          imageVector = Icons.Outlined.ChevronLeft,
          contentDescription = stringResource(id = R.string.previous_track),
        )
      }
    }
    Row(
      modifier = Modifier
        .weight(1F)
        .clickable(onClick = onCurrentChapterClick)
        .padding(vertical = if (compact) 6.dp else 16.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (nextPreviousVisible) {
        Spacer(modifier = Modifier.size(if (compact) 12.dp else 28.dp))
      }
      Text(
        modifier = Modifier.padding(horizontal = 4.dp),
        text = chapterName,
        style = textStyle,
        textAlign = TextAlign.Center,
        maxLines = if (compact) 1 else Int.MAX_VALUE,
        overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
      )
      if (nextPreviousVisible && !compact) {
        Icon(
          modifier = Modifier.size(if (compact) 18.dp else 28.dp),
          imageVector = Icons.Outlined.ExpandMore,
          contentDescription = stringResource(id = R.string.next_track),
        )
      }
    }
    if (nextPreviousVisible) {
      IconButton(onClick = onSkipToNext) {
        Icon(
          modifier = Modifier.size(iconSize),
          imageVector = Icons.Outlined.ChevronRight,
          contentDescription = stringResource(id = R.string.next_track),
        )
      }
    }
  }
}
