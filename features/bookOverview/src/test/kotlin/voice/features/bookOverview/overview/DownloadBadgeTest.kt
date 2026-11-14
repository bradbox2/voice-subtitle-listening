package voice.features.bookOverview.overview

import kotlin.test.Test
import kotlin.test.assertEquals
import voice.core.audiobookshelf.download.BookDownloadState
import voice.core.data.BookId

class DownloadBadgeTest {

  private val serverBook = BookId("abs://item/1")
  private val deviceBook = BookId("content://books/1")

  @Test
  fun `server books show whether they stream or are downloaded`() {
    assertEquals(DownloadBadge.Streamed, null.badge(serverBook))
    assertEquals(DownloadBadge.Streamed, BookDownloadState.NotDownloaded.badge(serverBook))
    assertEquals(
      DownloadBadge.Downloaded,
      BookDownloadState.Downloaded(bytes = 1).badge(serverBook),
    )
  }

  @Test
  fun `books on the device have no badge`() {
    assertEquals(null, null.badge(deviceBook))
  }
}
