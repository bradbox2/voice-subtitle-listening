package voice.features.studyStats

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import voice.core.strings.R as StringsR
import voice.core.studystats.StudyCategory
import voice.core.studystats.StudyWeek

@Composable
internal fun StudyCategory.label(): String =
  stringResource(
    when (this) {
      StudyCategory.Language -> StringsR.string.study_category_language
      StudyCategory.Audiobook -> StringsR.string.study_category_audiobook
      StudyCategory.Meditation -> StringsR.string.study_category_meditation
    },
  )

/** Everything, or one of the categories. */
@Composable
internal fun CategoryFilter(
  selected: StudyCategory?,
  onSelect: (StudyCategory?) -> Unit,
) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
      selected = selected == null,
      onClick = { onSelect(null) },
      label = { Text(stringResource(StringsR.string.study_filter_all)) },
    )
    StudyCategory.entries.forEach { category ->
      FilterChip(
        selected = selected == category,
        onClick = { onSelect(category) },
        label = { Text(category.label()) },
      )
    }
  }
}

@Composable
internal fun CategoryDialog(
  folder: String,
  current: StudyCategory,
  onChoose: (StudyCategory) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(StringsR.string.study_category_choose, folder)) },
    text = {
      Column {
        StudyCategory.entries.forEach { category ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable(role = Role.RadioButton) { onChoose(category) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = category == current, onClick = null)
            Text(category.label(), modifier = Modifier.padding(start = 12.dp))
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(StringsR.string.common_action_close)) }
    },
  )
}

/** The time of a week, with a bar to compare it with the other weeks. */
@Composable
internal fun WeekRow(
  week: StudyWeek,
  longest: Long,
) {
  val locale = LocalConfiguration.current.locales[0]
  val format =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = week.start.format(format) + " – " + week.start.plusDays(6).format(format),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1F),
      )
      Text(studyTime(week.millis), style = MaterialTheme.typography.titleSmall)
    }
    LinearProgressIndicator(
      progress = { if (longest > 0) week.millis.toFloat() / longest else 0F },
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
  }
}
