package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.features.playbackScreen.BookPlayViewState

@Composable
internal fun SubtitlePanel(
  viewState: BookPlayViewState.SubtitlePanelViewState,
  onSubtitleClick: (Long) -> Unit,
  onSubtitleStarClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  focused: Boolean = false,
) {
  val listState = rememberLazyListState()
  LaunchedEffect(viewState.activeIndex) {
    val activeIndex = viewState.activeIndex
    if (activeIndex != null) {
      listState.animateScrollToItem(if (focused) (activeIndex - 3).coerceAtLeast(0) else activeIndex)
    }
  }
  LazyColumn(
    state = listState,
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (focused) {
          Modifier.fillMaxHeight()
        } else {
          Modifier.heightIn(max = 180.dp)
        },
      )
      .clip(RoundedCornerShape(8.dp))
      .background(
        if (focused) {
          Color.Transparent
        } else {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55F)
        },
      ),
    contentPadding = PaddingValues(vertical = if (focused) 18.dp else 6.dp),
  ) {
    items(
      items = viewState.items,
      key = { item -> item.starKey },
    ) { item ->
      SubtitleRow(
        item = item,
        focused = focused,
        onSubtitleClick = onSubtitleClick,
        onSubtitleStarClick = onSubtitleStarClick,
      )
    }
  }
}

@Composable
private fun SubtitleRow(
  item: BookPlayViewState.SubtitlePanelViewState.Item,
  focused: Boolean,
  onSubtitleClick: (Long) -> Unit,
  onSubtitleStarClick: (String) -> Unit,
) {
  val background = if (item.active) {
    if (focused) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84F)
    } else {
      MaterialTheme.colorScheme.secondaryContainer
    }
  } else {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0F)
  }
  val color = if (item.active) {
    if (focused) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSecondaryContainer
    }
  } else {
    if (focused) {
      Color.White.copy(alpha = 0.68F)
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    }
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(background),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = item.text,
      color = color,
      style = if (focused && item.active) {
        MaterialTheme.typography.titleLarge
      } else if (focused) {
        MaterialTheme.typography.bodyLarge
      } else {
        MaterialTheme.typography.bodyMedium
      },
      fontWeight = if (focused && item.active) FontWeight.SemiBold else FontWeight.Normal,
      textAlign = if (focused) TextAlign.Center else TextAlign.Start,
      modifier = Modifier
        .weight(1F)
        .clickable { onSubtitleClick(item.startMs) }
        .padding(
          start = if (focused) 42.dp else 14.dp,
          end = 4.dp,
          top = if (focused) 9.dp else 8.dp,
          bottom = if (focused) 9.dp else 8.dp,
        ),
    )
    if (item.active) {
      IconButton(
        onClick = { onSubtitleStarClick(item.starKey) },
        modifier = Modifier.padding(end = if (focused) 2.dp else 0.dp),
      ) {
        Icon(
          imageVector = if (item.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
          contentDescription = if (item.starred) "Unstar sentence" else "Star sentence",
          tint = if (item.starred) {
            Color(0xFFFFD54F)
          } else if (focused) {
            Color.White.copy(alpha = 0.54F)
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7F)
          },
        )
      }
    }
  }
}
