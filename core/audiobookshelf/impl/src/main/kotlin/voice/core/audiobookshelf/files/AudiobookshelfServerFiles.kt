package voice.core.audiobookshelf.files

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.cache.ContentMetadata
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.Request
import voice.core.audiobookshelf.ServerFiles
import voice.core.audiobookshelf.api.AbsLibraryItem
import voice.core.audiobookshelf.audiobookshelfJson
import voice.core.audiobookshelf.download.AudiobookshelfMedia
import voice.core.audiobookshelf.http.AudiobookshelfHttp
import voice.core.audiobookshelf.itemId
import voice.core.audiobookshelf.trackRef
import voice.core.data.AUDIOBOOKSHELF_SCHEME
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.logging.api.Logger

/**
 * Keeps, per server book, the names its folder and files have on the server and the subtitles next
 * to its files, as `<itemId>/names.json` and `<itemId>/<ino of the audio>.srt`. They are written
 * when the library sync stores the book, so they follow its updates.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AudiobookshelfServerFiles
internal constructor(
  context: Context,
  private val http: AudiobookshelfHttp,
  private val media: AudiobookshelfMedia,
) : ServerFiles {

  private val root = File(context.filesDir, "audiobookshelf/files")
  private val names = ConcurrentHashMap<String, Names>()

  override fun folderName(bookId: BookId): String? = bookId.itemId?.let(::names)?.folder

  override fun fileName(chapterId: ChapterId): String? {
    val track = chapterId.trackRef ?: return null
    return names(track.itemId)?.files?.get(track.ino)
  }

  override fun subtitle(chapterId: ChapterId): File? {
    val track = chapterId.trackRef ?: return null
    return File(File(root, track.itemId), "${track.ino}.srt").takeIf { it.isFile }
  }

  override fun isDownloaded(uri: Uri): Boolean {
    val key = cacheKey(uri) ?: return false
    val length = ContentMetadata.getContentLength(media.cache.getContentMetadata(key))
    return length > 0 && media.cache.isCached(key, 0, length)
  }

  // downloads are keyed by the chapter id, but the cache reports the url it was downloaded from
  private fun cacheKey(uri: Uri): String? {
    if (uri.scheme == AUDIOBOOKSHELF_SCHEME) return uri.toString()
    val segments = uri.pathSegments.takeLast(5)
    if (
      segments.size != 5 || segments[0] != "api" || segments[1] != "items" || segments[3] != "file"
    ) {
      return null
    }
    return media.cache.keys.firstOrNull { key ->
      trackRef(key)?.let { it.itemId == segments[2] && it.ino == segments[4] } == true
    }
  }

  internal fun has(itemId: String): Boolean = File(File(root, itemId), NAMES).isFile

  internal suspend fun store(
    serverUrl: String,
    item: AbsLibraryItem,
  ) {
    withContext(Dispatchers.IO) {
      val tracks = item.media.tracks.orEmpty()
      val subtitles =
        item.libraryFiles
          .filter { it.metadata.filename?.endsWith(".srt", ignoreCase = true) == true }
          .associateBy { it.metadata.filename!!.substringBeforeLast('.').lowercase() }
      val folder = File(root, item.id)
      val next = File(root, "${item.id}.next")
      next.deleteRecursively()
      next.mkdirs()
      try {
        tracks.forEach { track ->
          val base = track.metadata?.filename?.substringBeforeLast('.')?.lowercase()
          val subtitle = subtitles[base] ?: return@forEach
          download(
            "${serverUrl}api/items/${item.id}/file/${subtitle.ino}",
            File(next, "${track.ino}.srt"),
          )
        }
      } catch (e: IOException) {
        // without names.json the next sync tries again
        Logger.w(e, "Couldn't download the subtitles of a server book")
        next.deleteRecursively()
        return@withContext
      }
      val subtitleCount = next.list().orEmpty().size
      // a book of a single file is named by the folder it is in, like on the device
      val path = item.path?.trimEnd('/')
      val stored =
        Names(
          folder =
            (if (item.isFile) path?.substringBeforeLast('/') else path)?.substringAfterLast('/'),
          files =
            tracks
              .mapNotNull { track -> track.metadata?.filename?.let { track.ino to it } }
              .toMap(),
        )
      File(next, NAMES).writeText(audiobookshelfJson.encodeToString(Names.serializer(), stored))
      folder.deleteRecursively()
      next.renameTo(folder)
      names[item.id] = stored
      Logger.d(
        "Stored ${stored.files.size} file names and $subtitleCount subtitles of a server book",
      )
    }
  }

  private fun download(
    url: String,
    target: File,
  ) {
    http.authenticatedClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      target.outputStream().use { response.body.byteStream().copyTo(it) }
    }
  }

  private fun names(itemId: String): Names? {
    names[itemId]?.let {
      return it
    }
    val file = File(File(root, itemId), NAMES)
    if (!file.isFile) return null
    return try {
      audiobookshelfJson.decodeFromString(Names.serializer(), file.readText()).also {
        names[itemId] = it
      }
    } catch (e: IOException) {
      Logger.w(e, "Couldn't read the file names of a server book")
      null
    } catch (e: SerializationException) {
      Logger.w(e, "Couldn't read the file names of a server book")
      null
    }
  }

  @Serializable
  private data class Names(
    val folder: String?,
    val files: Map<String, String>,
  )

  private companion object {
    const val NAMES = "names.json"
  }
}
