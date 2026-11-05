package voice.features.studyStats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import voice.core.strings.R as StringsR
import voice.core.studystats.StudySync
import voice.core.studystats.StudySyncResult
import voice.core.studystats.StudySyncSettings

@Composable
internal fun StudySyncDialog(
  sync: StudySync,
  onDismiss: () -> Unit,
) {
  val settings = remember { sync.settingsFlow }.collectAsState(initial = null).value
  val result = sync.lastResult.collectAsState().value
  // typed in here, and only stored with "Sync now"
  var url by remember { mutableStateOf("") }
  var token by remember { mutableStateOf("") }
  var loaded by remember { mutableStateOf(false) }
  LaunchedEffect(settings) {
    if (settings != null && !loaded) {
      url = settings.url
      token = settings.token
      loaded = true
    }
  }
  var syncing by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(StringsR.string.study_sync_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text(stringResource(StringsR.string.study_sync_address)) },
          placeholder = { Text("https://…/api/v1/study-sync/days") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = token,
          onValueChange = { token = it },
          label = { Text(stringResource(StringsR.string.study_sync_token)) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier.fillMaxWidth(),
        )
        if (url.trim().startsWith("http://")) {
          Text(
            text = stringResource(StringsR.string.study_sync_http_warning),
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (syncing) {
          CircularProgressIndicator()
        } else {
          result?.let {
            Text(text = resultText(it), style = MaterialTheme.typography.bodyMedium)
          }
        }
        settings
          ?.takeIf { it.lastSuccessMillis > 0 }
          ?.let {
            Text(
              text = stringResource(StringsR.string.study_sync_last, lastSyncText(it)),
              style = MaterialTheme.typography.bodySmall,
            )
          }
      }
    },
    confirmButton = {
      TextButton(
        enabled = !syncing && url.isNotBlank() && token.isNotBlank(),
        onClick = {
          scope.launch {
            syncing = true
            sync.configure(url, token)
            sync.sync()
            syncing = false
          }
        },
      ) {
        Text(stringResource(StringsR.string.study_sync_now))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(StringsR.string.common_action_close)) }
    },
  )
}

@Composable
private fun resultText(result: StudySyncResult): String {
  return when (result) {
    is StudySyncResult.Done ->
      result.lastDate?.let { stringResource(StringsR.string.study_sync_done_last, it) }
        ?: stringResource(StringsR.string.study_sync_done)
    StudySyncResult.NotConfigured -> stringResource(StringsR.string.study_sync_not_configured)
    StudySyncResult.NothingToSend -> stringResource(StringsR.string.study_sync_nothing)
    StudySyncResult.BadAddress -> stringResource(StringsR.string.study_sync_bad_address)
    StudySyncResult.BadToken -> stringResource(StringsR.string.study_sync_bad_token)
    StudySyncResult.Rejected -> stringResource(StringsR.string.study_sync_rejected)
    StudySyncResult.ServerTokenMissing ->
      stringResource(StringsR.string.study_sync_server_token_missing)
    StudySyncResult.NoConnection -> stringResource(StringsR.string.study_sync_no_connection)
    is StudySyncResult.ServerError ->
      stringResource(StringsR.string.study_sync_server_error, result.code)
  }
}

private fun lastSyncText(settings: StudySyncSettings): String {
  return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(settings.lastSuccessMillis))
}
