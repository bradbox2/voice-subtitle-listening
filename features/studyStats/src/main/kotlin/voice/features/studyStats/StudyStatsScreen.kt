@file:OptIn(ExperimentalMaterial3Api::class)

package voice.features.studyStats

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.rootGraphAs
import voice.core.strings.R as StringsR
import voice.core.studystats.StudyOverview
import voice.core.studystats.StudyStats
import voice.core.studystats.studyCsv
import voice.core.studystats.studyMonth
import voice.core.studystats.studyOverview
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator

@ContributesTo(AppScope::class)
interface StudyStatsGraph {
  val studyStats: StudyStats
  val navigator: Navigator
}

@BindingContainer
@ContributesTo(AppScope::class)
object StudyStatsProvider {

  @Provides
  @IntoSet
  fun navEntryProvider(): NavEntryProvider<*> =
    NavEntryProvider<Destination.StudyStats> { key -> NavEntry(key) { StudyStatsScreen() } }
}

@Composable
fun StudyStatsScreen(modifier: Modifier = Modifier) {
  val graph = remember { rootGraphAs<StudyStatsGraph>() }
  val entries = remember { graph.studyStats.entries }.collectAsState(initial = null).value
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val exportedMessage = stringResource(StringsR.string.study_exported)
  val export =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) {
        scope.launch {
          writeCsv(context, uri, studyCsv(graph.studyStats.entries.first()))
          Toast.makeText(context, exportedMessage, Toast.LENGTH_SHORT).show()
        }
      }
    }
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(StringsR.string.study_title)) },
        navigationIcon = {
          IconButton(onClick = { graph.navigator.goBack() }) {
            Icon(
              imageVector = VoiceIcons.ArrowBack,
              contentDescription = stringResource(StringsR.string.common_action_close),
            )
          }
        },
        actions = {
          IconButton(
            onClick = { export.launch("voice-study-${LocalDate.now()}.csv") },
            enabled = !entries.isNullOrEmpty(),
          ) {
            Icon(
              imageVector = VoiceIcons.Download,
              contentDescription = stringResource(StringsR.string.study_export),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    if (entries == null) return@Scaffold
    val overview = remember(entries) { studyOverview(entries, LocalDate.now()) }
    var byMonth by rememberSaveable { mutableStateOf(false) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val days = remember(entries, month) { studyMonth(entries, month) }
    LazyColumn(
      contentPadding =
        PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = contentPadding.calculateTopPadding() + 8.dp,
          bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { Totals(overview) }
      item { ViewSwitch(byMonth = byMonth, onByMonthChange = { byMonth = it }) }
      if (byMonth) {
        item {
          StudyCalendar(
            days = days,
            month = month,
            selected = selectedDay,
            onMonthChange = { month = it },
            onDayClick = { selectedDay = it },
          )
        }
        val day = days[selectedDay]
        if (day == null) {
          item {
            Text(
              text = stringResource(StringsR.string.study_day_empty),
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(vertical = 12.dp),
            )
          }
        } else {
          items(day.files, key = { it.folder + "/" + it.file }) { entry -> DayFileRow(entry) }
        }
      } else {
        if (overview.folders.isEmpty()) {
          item {
            Text(
              text = stringResource(StringsR.string.study_empty),
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(vertical = 24.dp),
            )
          }
        }
        items(overview.folders, key = { it.name }) { folder -> FolderCard(folder) }
      }
    }
  }
}

@Composable
private fun Totals(overview: StudyOverview) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Total(
      label = stringResource(StringsR.string.study_today),
      millis = overview.todayMillis,
      modifier = Modifier.weight(1F),
    )
    Total(
      label = stringResource(StringsR.string.study_total),
      millis = overview.totalMillis,
      modifier = Modifier.weight(1F),
    )
  }
}

@Composable
private fun Total(
  label: String,
  millis: Long,
  modifier: Modifier = Modifier,
) {
  Card(modifier = modifier) {
    Column(Modifier.padding(16.dp)) {
      Text(label, style = MaterialTheme.typography.labelLarge)
      Text(studyTime(millis), style = MaterialTheme.typography.headlineSmall)
    }
  }
}

@Composable
private fun FolderCard(folder: StudyOverview.Folder) {
  var expanded by rememberSaveable(folder.name) { mutableStateOf(false) }
  Card(shape = RoundedCornerShape(20.dp)) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1F)) {
          Text(
            folder.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            details(folder.plays, folder.lastDate),
            style = MaterialTheme.typography.bodySmall,
          )
        }
        Text(studyTime(folder.millis), style = MaterialTheme.typography.titleMedium)
        Icon(
          imageVector = VoiceIcons.ExpandMore,
          contentDescription = null,
          modifier = Modifier.padding(start = 8.dp),
        )
      }
      if (expanded) {
        folder.files.forEach { file ->
          HorizontalDivider()
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1F)) {
              Text(file.name, style = MaterialTheme.typography.bodyMedium)
              Text(details(file.plays, file.lastDate), style = MaterialTheme.typography.bodySmall)
            }
            Text(studyTime(file.millis), style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
  }
}

@Composable
internal fun details(
  plays: Int,
  lastDate: String,
): String {
  return pluralStringResource(StringsR.plurals.study_plays, plays, plays) +
    " · " +
    stringResource(StringsR.string.study_last, lastDate)
}

@Composable
internal fun playsText(plays: Int): String =
  pluralStringResource(StringsR.plurals.study_plays, plays, plays)

@Composable
internal fun studyTime(millis: Long): String {
  if (millis in 1..<60_000) return stringResource(StringsR.string.study_time_less_than_minute)
  val minutes = millis / 60_000
  return if (minutes >= 60) {
    stringResource(StringsR.string.study_time_hours_minutes, minutes / 60, minutes % 60)
  } else {
    stringResource(StringsR.string.study_time_minutes, minutes)
  }
}

private suspend fun writeCsv(
  context: Context,
  uri: Uri,
  csv: String,
) {
  withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
  }
}
