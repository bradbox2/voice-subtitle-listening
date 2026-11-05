package voice.core.studystats

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.data.toUri
import voice.core.subtitle.SubtitleFileProvider

/**
 * Labels the folders of the study time without the listener having to, see [detectStudyCategory].
 * Folders are looked at when a file of them starts playing, and the library once when the app
 * starts, for the books that were listened to before the labels existed.
 */
@SingleIn(AppScope::class)
@Inject
class StudyCategorizer(
  private val categories: StudyCategories,
  private val subtitles: SubtitleFileProvider,
  private val books: BookRepository,
  private val dispatcherProvider: DispatcherProvider,
  private val scope: CoroutineScope,
) {

  fun onPlaying(
    bookId: BookId,
    chapterId: ChapterId,
  ) {
    scope.launch(dispatcherProvider.io) {
      classify(studyNames(bookId, chapterId).folder, listOf(chapterId))
    }
  }

  suspend fun classifyLibrary() {
    withContext(dispatcherProvider.io) {
      val labelled = categories.labels.first()
      books.all().forEach { book ->
        val first = book.chapters.firstOrNull() ?: return@forEach
        val folder = studyNames(book.id, first.id).folder
        if (folder !in labelled) {
          classify(folder, book.chapters.take(CHAPTERS_LOOKED_AT).map { it.id })
        }
      }
    }
  }

  private suspend fun classify(
    folder: String,
    chapters: List<ChapterId>,
  ) {
    val current = categories.labels.first()[folder]
    // there is nothing left to find out, subtitles are the only way up from an audiobook
    if (current != null && (current.manual || current.category != StudyCategory.Audiobook)) return
    val hasSubtitle = chapters.any { subtitles.findSubtitleForAudio(it.toUri()) != null }
    categories.detected(folder, hasSubtitle)
  }

  private companion object {
    // listing a folder of a document tree is slow, so not every file of a long book
    const val CHAPTERS_LOOKED_AT = 3
  }
}
