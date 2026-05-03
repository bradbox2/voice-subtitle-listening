package voice.core.playback.subtitle

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

public interface SubtitleFileProvider {
  public fun findSubtitleForAudio(audioUri: Uri): Uri?
}

@ContributesBinding(AppScope::class)
public class SameFolderSubtitleFileProvider private constructor(private val contentResolver: ContentResolver?) : SubtitleFileProvider {

  @Inject
  public constructor(context: Context) : this(context.contentResolver)

  internal constructor() : this(null)

  override fun findSubtitleForAudio(audioUri: Uri): Uri? {
    Log.d(TAG, "SubtitleFileProvider input uri=$audioUri scheme=${audioUri.scheme}")
    return when (audioUri.scheme) {
      "file" -> findFileSubtitleForAudio(audioUri)
      "content" -> findContentSubtitleForAudio(audioUri)
      else -> {
        Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=unsupported_scheme")
        null
      }
    }
  }

  private fun findFileSubtitleForAudio(audioUri: Uri): Uri? {
    val audioFile = try {
      audioUri.toFile()
    } catch (_: IllegalArgumentException) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=invalid_file_uri")
      return null
    } catch (_: SecurityException) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=security_exception")
      return null
    }

    if (!audioFile.isFile || !audioFile.canRead()) {
      Log.d(
        TAG,
        "SubtitleFileProvider result subtitleUri=null reason=audio_unreadable isFile=${audioFile.isFile} canRead=${audioFile.canRead()} path=${audioFile.absolutePath}",
      )
      return null
    }

    val parent = audioFile.parentFile
    if (parent == null) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=no_parent path=${audioFile.absolutePath}")
      return null
    }
    val subtitleName = subtitleNameForAudioDisplayName(audioFile.name)
    if (subtitleName == null) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=no_audio_basename path=${audioFile.absolutePath}")
      return null
    }
    val subtitleFile = parent.resolve(subtitleName)

    val subtitleUri = subtitleFile
      .takeIf { it.isFile && it.canRead() }
      ?.toUri()
    Log.d(
      TAG,
      "SubtitleFileProvider result subtitleUri=$subtitleUri expectedPath=${subtitleFile.absolutePath} exists=${subtitleFile.isFile} canRead=${subtitleFile.canRead()}",
    )
    return subtitleUri
  }

  private fun findContentSubtitleForAudio(audioUri: Uri): Uri? {
    val resolver = contentResolver
    if (resolver == null) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=no_content_resolver")
      return null
    }

    val audioDocument = audioUri.toSafAudioDocument()
    if (audioDocument == null) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=unsupported_content_uri uri=$audioUri")
      return null
    }

    val subtitleName = subtitleNameForAudioDisplayName(audioDocument.audioDisplayName)
    if (subtitleName == null) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=no_audio_basename audioName=${audioDocument.audioDisplayName}")
      return null
    }

    val childrenUri = try {
      DocumentsContract.buildChildDocumentsUriUsingTree(audioUri, audioDocument.parentDocumentId)
    } catch (_: IllegalArgumentException) {
      Log.d(
        TAG,
        "SubtitleFileProvider result subtitleUri=null reason=invalid_parent_document parentDocumentId=${audioDocument.parentDocumentId}",
      )
      return null
    }

    return try {
      resolver.query(
        childrenUri,
        arrayOf(
          DocumentsContract.Document.COLUMN_DOCUMENT_ID,
          DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ),
        null,
        null,
        null,
      )?.use { cursor ->
        cursor.findDocumentIdByDisplayName(subtitleName)
      }?.let { subtitleDocumentId ->
        DocumentsContract.buildDocumentUriUsingTree(audioUri, subtitleDocumentId)
      }.also { subtitleUri ->
        Log.d(
          TAG,
          "SubtitleFileProvider result subtitleUri=$subtitleUri expectedName=$subtitleName parentDocumentId=${audioDocument.parentDocumentId}",
        )
      }
    } catch (_: IllegalArgumentException) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=content_query_illegal_argument expectedName=$subtitleName")
      null
    } catch (_: SecurityException) {
      Log.d(TAG, "SubtitleFileProvider result subtitleUri=null reason=content_query_security_exception expectedName=$subtitleName")
      null
    } catch (e: Exception) {
      Log.d(
        TAG,
        "SubtitleFileProvider result subtitleUri=null reason=content_query_exception type=${e::class.java.simpleName} expectedName=$subtitleName",
      )
      null
    }
  }
}

internal data class SafAudioDocument(
  val authority: String?,
  val parentDocumentId: String,
  val audioDisplayName: String,
)

internal fun subtitleNameForAudioDisplayName(audioDisplayName: String): String? {
  val baseName = audioDisplayName.substringBeforeLast(".").takeUnless { it.isBlank() }
  return baseName?.let { "$it.srt" }
}

internal fun Uri.toSafAudioDocument(): SafAudioDocument? {
  if (scheme != "content") return null

  val documentId = try {
    DocumentsContract.getDocumentId(this)
  } catch (_: IllegalArgumentException) {
    return null
  }

  val slashIndex = documentId.lastIndexOf('/')
  val parentDocumentId: String
  val audioDisplayName: String
  if (slashIndex >= 0) {
    if (slashIndex == documentId.lastIndex) return null
    parentDocumentId = documentId.substring(0, slashIndex)
    audioDisplayName = documentId.substring(slashIndex + 1)
  } else {
    val colonIndex = documentId.indexOf(':')
    if (colonIndex == -1 || colonIndex == documentId.lastIndex) return null
    parentDocumentId = documentId.substring(0, colonIndex + 1)
    audioDisplayName = documentId.substring(colonIndex + 1)
  }

  if (parentDocumentId.isBlank() || audioDisplayName.isBlank()) return null
  return SafAudioDocument(
    authority = authority,
    parentDocumentId = parentDocumentId,
    audioDisplayName = audioDisplayName,
  )
}

private fun Cursor.findDocumentIdByDisplayName(displayName: String): String? {
  val documentIdIndex = getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
  val displayNameIndex = getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
  if (documentIdIndex == -1 || displayNameIndex == -1) return null

  while (moveToNext()) {
    if (getString(displayNameIndex) == displayName) {
      return getString(documentIdIndex)
    }
  }
  return null
}

private const val TAG = "VoiceSubtitle"
