package voice.core.studystats

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
@Inject
class StudyCategoryInitializer(
  private val categorizer: StudyCategorizer,
  private val scope: CoroutineScope,
) : AppInitializer {

  override fun onAppStart(application: Application) {
    scope.launch { categorizer.classifyLibrary() }
  }
}
