package voice.core.studystats

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.subtitle.SubtitleFileProvider

@RunWith(AndroidJUnit4::class)
class StudyCategorizerTest {

  private val scope = TestScope()
  private val store = MemoryStore()
  private val categories = StudyCategories(StudyCategoriesStore(store))
  private val withSubtitles = mutableSetOf<String>()
  private val subtitles =
    object : SubtitleFileProvider {
      override fun findSubtitleForAudio(audioUri: Uri): Uri? = audioUri.takeIf {
        it.toString() in withSubtitles
      }
    }
  private val library = mutableListOf<Book>()
  private val books = mockk<BookRepository> { coEvery { all() } answers { library.toList() } }
  private val categorizer =
    StudyCategorizer(
      categories = categories,
      subtitles = subtitles,
      books = books,
      dispatcherProvider =
        DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
      scope = scope.backgroundScope,
    )

  private fun book(
    folder: String,
    vararg files: String,
  ): Book {
    val chapters = files.map { file ->
      mockk<Chapter> { every { id } returns ChapterId("file:///sdcard/Books/$folder/$file") }
    }
    return mockk {
      every { id } returns BookId("file:///sdcard/Books/$folder")
      every { this@mockk.chapters } returns chapters
    }
  }

  @Test
  fun `the library is labelled by names and subtitles when the app starts`() = scope.runTest {
    withSubtitles += "file:///sdcard/Books/S0106edition/b.mp3"
    library +=
      listOf(
        book("S0106edition", "a.mp3", "b.mp3"),
        book("Dune", "ch1.mp3"),
        book("21 Day Meditation", "day1.mp3"),
      )

    categorizer.classifyLibrary()

    assertEquals(
      mapOf(
        "S0106edition" to FolderCategory(StudyCategory.Language),
        "Dune" to FolderCategory(StudyCategory.Audiobook),
        "21 Day Meditation" to FolderCategory(StudyCategory.Meditation),
      ),
      store.data.first(),
    )
  }

  @Test
  fun `folders that already have a label are left alone`() = scope.runTest {
    categories.choose("Dune", StudyCategory.Language)
    library += book("Dune", "ch1.mp3")

    categorizer.classifyLibrary()

    assertEquals(
      mapOf("Dune" to FolderCategory(StudyCategory.Language, manual = true)),
      store.data.first(),
    )
  }

  @Test
  fun `a file that starts playing labels its folder`() = scope.runTest {
    withSubtitles += "file:///sdcard/Books/Tutor_Audio/louie.mp3"

    categorizer.onPlaying(
      BookId("file:///sdcard/Books/Tutor_Audio"),
      ChapterId("file:///sdcard/Books/Tutor_Audio/louie.mp3"),
    )
    runCurrent()

    assertEquals(
      mapOf("Tutor_Audio" to FolderCategory(StudyCategory.Language)),
      store.data.first(),
    )
  }

  @Test
  fun `a folder that was an audiobook becomes language once a file with subtitles plays`() =
    scope.runTest {
      categories.detected("Tutor_Audio", hasSubtitle = false)
      withSubtitles += "file:///sdcard/Books/Tutor_Audio/louie.mp3"

      categorizer.onPlaying(
        BookId("file:///sdcard/Books/Tutor_Audio"),
        ChapterId("file:///sdcard/Books/Tutor_Audio/louie.mp3"),
      )
      runCurrent()

      assertEquals(StudyCategory.Language, store.data.first().getValue("Tutor_Audio").category)
    }

  private class MemoryStore : DataStore<Map<String, FolderCategory>> {
    private val value = MutableStateFlow(emptyMap<String, FolderCategory>())
    override val data: Flow<Map<String, FolderCategory>> = value

    override suspend fun updateData(
      transform: suspend (t: Map<String, FolderCategory>) -> Map<String, FolderCategory>,
    ): Map<String, FolderCategory> = value.updateAndGet { transform(it) }
  }
}
