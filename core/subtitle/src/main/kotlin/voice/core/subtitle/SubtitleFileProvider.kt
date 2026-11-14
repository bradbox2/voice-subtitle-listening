package voice.core.subtitle

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toFile
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import voice.core.audiobookshelf.ServerFiles
import voice.core.data.ChapterId
import voice.core.logging.api.Logger

interface SubtitleFileProvider {
  fun findSubtitleForAudio(audioUri: Uri): Uri?
}

@ContributesBinding(AppScope::class)
class SameFolderSubtitleFileProvider
internal constructor(
  private val contentResolver: ContentResolver?,
  private val serverFiles: ServerFiles? = null,
) : SubtitleFileProvider {

  @Inject
  constructor(
    context: Context,
    serverFiles: ServerFiles,
  ) : this(context.contentResolver, serverFiles)

  override fun findSubtitleForAudio(audioUri: Uri): Uri? {
    return try {
      when (audioUri.scheme) {
        ContentResolver.SCHEME_FILE -> findNextToFile(audioUri)
        ContentResolver.SCHEME_CONTENT -> findNextToDocument(audioUri)
        else -> serverFiles?.subtitle(ChapterId(audioUri))?.toUri()
      }
    } catch (e: SecurityException) {
      Logger.w(e, "No access to the folder of the audio file")
      null
    } catch (e: IllegalArgumentException) {
      Logger.w(e, "Couldn't list the folder of the audio file")
      null
    }
  }

  private fun findNextToFile(audioUri: Uri): Uri? {
    val audioFile = audioUri.toFile()
    val subtitleName = subtitleName(audioFile.name) ?: return null
    return audioFile.parentFile
      ?.listFiles()
      ?.firstOrNull { it.isFile && it.name.equals(subtitleName, ignoreCase = true) }
      ?.toUri()
  }

  private fun findNextToDocument(audioUri: Uri): Uri? {
    val resolver = contentResolver ?: return null
    val document = audioUri.toSafDocument() ?: return null
    val subtitleName = subtitleName(document.displayName) ?: return null
    val childrenUri =
      DocumentsContract.buildChildDocumentsUriUsingTree(audioUri, document.parentDocumentId)
    val subtitleDocumentId =
      resolver
        .query(
          childrenUri,
          arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
          ),
          null,
          null,
          null,
        )
        ?.use { cursor ->
          var match: String? = null
          while (match == null && cursor.moveToNext()) {
            if (cursor.getString(1).equals(subtitleName, ignoreCase = true)) {
              match = cursor.getString(0)
            }
          }
          match
        } ?: return null
    return DocumentsContract.buildDocumentUriUsingTree(audioUri, subtitleDocumentId)
  }
}

internal data class SafDocument(val parentDocumentId: String, val displayName: String)

// The external storage provider's ids are paths like "primary:Books/A/one.mp3", which is the only
// way to find the folder without listing the whole tree.
internal fun Uri.toSafDocument(): SafDocument? {
  val documentId =
    try {
      DocumentsContract.getDocumentId(this)
    } catch (_: IllegalArgumentException) {
      return null
    }
  val separator = documentId.lastIndexOf('/').takeIf { it >= 0 } ?: documentId.indexOf(':')
  if (separator < 0 || separator == documentId.lastIndex) return null
  val parentEnd = if (documentId[separator] == ':') separator + 1 else separator
  return SafDocument(
    parentDocumentId = documentId.substring(0, parentEnd),
    displayName = documentId.substring(separator + 1),
  )
}

private fun subtitleName(audioName: String): String? {
  val baseName = audioName.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: return null
  return "$baseName.srt"
}
