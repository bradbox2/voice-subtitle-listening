package voice.core.studystats

import androidx.datastore.core.DataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest

class StudyCategoriesTest {

  private val store = MemoryStore()
  private val categories = StudyCategories(StudyCategoriesStore(store))

  @Test
  fun `meditation is told by the name, in english and chinese`() {
    listOf(
        "21 Day Meditation Experience - Manifesting True Success",
        "Morning meditate",
        "冥想入门",
        "每日正念",
      )
      .forEach {
        assertEquals(StudyCategory.Meditation, detectStudyCategory(it, hasSubtitle = false), it)
      }
  }

  @Test
  fun `a folder with subtitles is language learning, any other is an audiobook`() {
    assertEquals(StudyCategory.Language, detectStudyCategory("S0106edition", hasSubtitle = true))
    assertEquals(StudyCategory.Audiobook, detectStudyCategory("Dune", hasSubtitle = false))
  }

  @Test
  fun `the name wins over subtitles`() {
    assertEquals(
      StudyCategory.Meditation,
      detectStudyCategory("Meditation 7 days", hasSubtitle = true),
    )
  }

  @Test
  fun `a folder is labelled by what was found in it`() = runTest {
    categories.detected("Dune", hasSubtitle = false)
    categories.detected("S0106edition", hasSubtitle = true)

    assertEquals(
      mapOf(
        "Dune" to FolderCategory(StudyCategory.Audiobook),
        "S0106edition" to FolderCategory(StudyCategory.Language),
      ),
      store.data.first(),
    )
  }

  @Test
  fun `a folder that gets subtitles later becomes language, but one file without doesn't turn it back`() =
    runTest {
      categories.detected("Tutor_Audio", hasSubtitle = false)
      categories.detected("Tutor_Audio", hasSubtitle = true)
      categories.detected("Tutor_Audio", hasSubtitle = false)

      assertEquals(
        FolderCategory(StudyCategory.Language),
        store.data.first().getValue("Tutor_Audio"),
      )
    }

  @Test
  fun `what the listener chose is never changed by the detection`() = runTest {
    categories.choose("Tutor_Audio", StudyCategory.Meditation)
    categories.detected("Tutor_Audio", hasSubtitle = true)
    categories.detected("Tutor_Audio", hasSubtitle = false)

    assertEquals(
      FolderCategory(StudyCategory.Meditation, manual = true),
      store.data.first().getValue("Tutor_Audio"),
    )
  }

  @Test
  fun `a later choice replaces an earlier one, also for a folder that was never seen`() = runTest {
    categories.choose("New", StudyCategory.Language)
    categories.choose("New", StudyCategory.Audiobook)

    assertEquals(
      FolderCategory(StudyCategory.Audiobook, manual = true),
      store.data.first().getValue("New"),
    )
  }

  @Test
  fun `an unlabelled folder still has a category, by its name`() {
    val labels = mapOf("Dune" to FolderCategory(StudyCategory.Language, manual = true))

    assertEquals(StudyCategory.Language, categoryOf("Dune", labels))
    assertEquals(StudyCategory.Meditation, categoryOf("冥想", labels))
    assertEquals(StudyCategory.Audiobook, categoryOf("Other", labels))
  }

  private class MemoryStore : DataStore<Map<String, FolderCategory>> {
    private val value = MutableStateFlow(emptyMap<String, FolderCategory>())
    override val data: Flow<Map<String, FolderCategory>> = value

    override suspend fun updateData(
      transform: suspend (t: Map<String, FolderCategory>) -> Map<String, FolderCategory>,
    ): Map<String, FolderCategory> = value.updateAndGet { transform(it) }
  }
}
