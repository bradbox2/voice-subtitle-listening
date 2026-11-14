package voice.core.audiobookshelf

import android.net.Uri
import java.io.File
import voice.core.data.BookId
import voice.core.data.ChapterId

/**
 * What the subtitle fork needs to know about the files of server books, which their ids don't
 * carry: the names they have on the server, their subtitles and whether they are downloaded.
 * Everything else gets null or false.
 */
interface ServerFiles {

  fun folderName(bookId: BookId): String?

  /** With the extension. */
  fun fileName(chapterId: ChapterId): String?

  fun subtitle(chapterId: ChapterId): File?

  fun isDownloaded(uri: Uri): Boolean
}
