package voice.features.playbackScreen.subtitle

import android.net.Uri
import java.io.File
import voice.core.audiobookshelf.ServerFiles
import voice.core.data.BookId
import voice.core.data.ChapterId

internal object NoServerFiles : ServerFiles {
  override fun folderName(bookId: BookId): String? = null

  override fun fileName(chapterId: ChapterId): String? = null

  override fun subtitle(chapterId: ChapterId): File? = null

  override fun isDownloaded(uri: Uri): Boolean = false
}
