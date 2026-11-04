package voice.core.playback.player

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.strings.R as StringsR

/**
 * Playback can be started from the library, the notification or a widget, where a failure would
 * otherwise just do nothing. Files are often moved or deleted by sync apps while the library still
 * lists them until the next scan.
 */
@Inject
class PlaybackErrorToast(private val context: Context) {

  fun attachTo(
    player: Player,
    scope: CoroutineScope,
  ) {
    player.addListener(
      object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          val uri = player.currentMediaItem?.localConfiguration?.uri ?: return
          scope.launch {
            val message = withContext(Dispatchers.IO) { message(uri) } ?: return@launch
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
          }
        }
      },
    )
  }

  internal fun message(uri: Uri): String? {
    val exists =
      when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).exists() } ?: false
        ContentResolver.SCHEME_CONTENT -> documentExists(uri)
        else -> return null
      }
    val name = displayName(uri)
    return if (exists) {
      context.getString(StringsR.string.playback_error_unplayable, name)
    } else {
      context.getString(StringsR.string.playback_error_file_missing, name)
    }
  }

  private fun documentExists(uri: Uri): Boolean {
    return try {
      context.contentResolver
        .query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
        ?.use { it.count > 0 } ?: false
    } catch (_: Exception) {
      // the providers throw all kinds of exceptions for documents that are gone
      false
    }
  }
}

internal fun displayName(uri: Uri): String {
  val last = uri.lastPathSegment ?: uri.toString()
  return last
    .substringAfterLast('/')
    .substringAfterLast('\\')
    .substringAfterLast(':')
    .substringBeforeLast('.')
}
