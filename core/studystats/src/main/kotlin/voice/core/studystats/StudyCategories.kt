package voice.core.studystats

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import voice.core.datastore.VoiceDataStoreFactory

/**
 * What a folder is for, in the order it is shown. [id] is what the sync sends, so it must not
 * change. [rank] is how far the detection got, see [StudyCategories.detected].
 */
@Serializable
enum class StudyCategory(val id: String, val rank: Int) {
  Language("language", rank = 1),
  Audiobook("audiobook", rank = 0),
  Meditation("meditation", rank = 2),
}

/** @param manual the listener chose it, so the detection leaves it alone. */
@Serializable data class FolderCategory(val category: StudyCategory, val manual: Boolean = false)

/**
 * A name with one of these is a meditation. Subtitles next to the audio mean the listener practices
 * a language with it. Everything else is an audiobook.
 */
fun detectStudyCategory(
  folder: String,
  hasSubtitle: Boolean,
): StudyCategory {
  return when {
    MEDITATION_WORDS.any { folder.contains(it, ignoreCase = true) } -> StudyCategory.Meditation
    hasSubtitle -> StudyCategory.Language
    else -> StudyCategory.Audiobook
  }
}

/** The label of the folder, or what its name says for folders that were never looked at. */
fun categoryOf(
  folder: String,
  labels: Map<String, FolderCategory>,
): StudyCategory = labels[folder]?.category ?: detectStudyCategory(folder, hasSubtitle = false)

private val MEDITATION_WORDS = listOf("meditat", "冥想", "正念")

@SingleIn(AppScope::class)
class StudyCategoriesStore(store: DataStore<Map<String, FolderCategory>>) :
  DataStore<Map<String, FolderCategory>> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = MapSerializer(String.serializer(), FolderCategory.serializer()),
      defaultValue = emptyMap(),
      fileName = "studyCategories",
    ),
  )
}

/** The category of each folder, by its name like the study time, so replacing files keeps it. */
@Inject
class StudyCategories(private val store: StudyCategoriesStore) {

  val labels: Flow<Map<String, FolderCategory>>
    get() = store.data

  /**
   * What was found in the folder. It only ever moves up, from audiobook to language to meditation,
   * as one file without subtitles says nothing about the others, and never changes a choice of the
   * listener.
   */
  suspend fun detected(
    folder: String,
    hasSubtitle: Boolean,
  ) {
    val found = detectStudyCategory(folder, hasSubtitle)
    store.updateData { labels ->
      val current = labels[folder]
      if (current != null && (current.manual || current.category.rank >= found.rank)) {
        labels
      } else {
        labels + (folder to FolderCategory(found))
      }
    }
  }

  suspend fun choose(
    folder: String,
    category: StudyCategory,
  ) {
    store.updateData { labels -> labels + (folder to FolderCategory(category, manual = true)) }
  }
}
