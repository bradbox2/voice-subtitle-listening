package voice.features.studyStats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import voice.core.strings.R as StringsR
import voice.core.studystats.StudyDay
import voice.core.studystats.StudyEntry
import voice.core.ui.icons.VoiceIcons

enum class StudyView {
  Folders,
  Weeks,
  Month,
}

@Composable
internal fun ViewSwitch(
  view: StudyView,
  onViewChange: (StudyView) -> Unit,
) {
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    StudyView.entries.forEachIndexed { index, option ->
      SegmentedButton(
        selected = view == option,
        onClick = { onViewChange(option) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = StudyView.entries.size),
      ) {
        Text(
          stringResource(
            when (option) {
              StudyView.Folders -> StringsR.string.study_view_folders
              StudyView.Weeks -> StringsR.string.study_view_weeks
              StudyView.Month -> StringsR.string.study_view_month
            },
          ),
        )
      }
    }
  }
}

/** Each day is tinted by how long was studied, relative to the most studied day of the month. */
@Composable
internal fun StudyCalendar(
  days: Map<LocalDate, StudyDay>,
  month: YearMonth,
  selected: LocalDate,
  onMonthChange: (YearMonth) -> Unit,
  onDayClick: (LocalDate) -> Unit,
) {
  val locale = LocalConfiguration.current.locales[0]
  val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
  val maxMillis = days.values.maxOfOrNull { it.millis } ?: 0L
  val colors = MaterialTheme.colorScheme
  Column {
    Card(shape = RoundedCornerShape(20.dp)) {
      Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(
              VoiceIcons.ArrowBack,
              contentDescription = stringResource(StringsR.string.study_month_previous),
            )
          }
          Column(Modifier.weight(1F), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text =
                pluralStringResource(
                  StringsR.plurals.study_month_summary,
                  days.size,
                  studyTime(days.values.sumOf { it.millis }),
                  days.size,
                ),
              style = MaterialTheme.typography.bodySmall,
            )
          }
          IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
            Icon(
              VoiceIcons.ArrowBack,
              contentDescription = stringResource(StringsR.string.study_month_next),
              modifier = Modifier.rotate(180F),
            )
          }
        }
        Row(Modifier.fillMaxWidth()) {
          repeat(7) { offset ->
            Text(
              text = firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, locale),
              style = MaterialTheme.typography.labelSmall,
              textAlign = TextAlign.Center,
              modifier = Modifier.weight(1F).padding(vertical = 4.dp),
            )
          }
        }
        val leadingBlanks = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = leadingBlanks + month.lengthOfMonth()
        repeat((cells + 6) / 7) { week ->
          Row(Modifier.fillMaxWidth()) {
            repeat(7) { weekday ->
              val dayOfMonth = week * 7 + weekday - leadingBlanks + 1
              if (dayOfMonth !in 1..month.lengthOfMonth()) {
                Spacer(Modifier.weight(1F))
              } else {
                val date = month.atDay(dayOfMonth)
                val day = days[date]
                val intensity =
                  if (day == null || maxMillis == 0L) 0F else 0.25F + 0.75F * day.millis / maxMillis
                Box(
                  modifier =
                    Modifier.weight(1F)
                      .aspectRatio(1F)
                      .padding(2.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .background(
                        if (intensity > 0F) colors.primary.copy(alpha = intensity)
                        else Color.Transparent,
                      )
                      .then(
                        if (date == selected) {
                          Modifier.border(2.dp, colors.onSurface, RoundedCornerShape(10.dp))
                        } else {
                          Modifier
                        },
                      )
                      .clickable { onDayClick(date) },
                  contentAlignment = Alignment.Center,
                ) {
                  val textColor = if (intensity > 0.5F) colors.onPrimary else colors.onSurface
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = dayOfMonth.toString(),
                      style = MaterialTheme.typography.labelLarge,
                      color = textColor,
                    )
                    if (day != null) {
                      Text(
                        text =
                          stringResource(
                            StringsR.string.study_time_minutes_short,
                            day.millis / 60_000,
                          ),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    Text(
      text =
        selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

@Composable
internal fun DayFileRow(entry: StudyEntry) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.weight(1F)) {
      Text(entry.file, style = MaterialTheme.typography.bodyMedium)
      Text(
        text = entry.folder + " · " + playsText(entry.plays),
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Text(studyTime(entry.listenedMillis), style = MaterialTheme.typography.bodyMedium)
  }
}
