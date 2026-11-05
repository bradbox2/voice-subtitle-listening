package voice.core.studystats

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import voice.core.initializer.AppInitializer

/**
 * Android may kill the app in the background, so it syncs on what does run: when the app comes to
 * the front, and a little after the study time stopped changing, which is when a listening session
 * ended.
 */
@OptIn(FlowPreview::class)
@ContributesIntoSet(AppScope::class)
@Inject
class StudySyncInitializer(
  private val stats: StudyStats,
  private val sync: StudySync,
  private val scope: CoroutineScope,
) : AppInitializer {

  override fun onAppStart(application: Application) {
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        private var startedActivities = 0

        override fun onActivityStarted(activity: Activity) {
          if (startedActivities++ == 0) {
            scope.launch { sync.syncOnVisible() }
          }
        }

        override fun onActivityStopped(activity: Activity) {
          startedActivities--
        }

        override fun onActivityCreated(
          activity: Activity,
          savedInstanceState: Bundle?,
        ) {}

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivitySaveInstanceState(
          activity: Activity,
          outState: Bundle,
        ) {}

        override fun onActivityDestroyed(activity: Activity) {}
      },
    )
    scope.launch {
      stats.entries.drop(1).debounce(SESSION_ENDED_AFTER).collect { sync.syncAfterListening() }
    }
  }

  private companion object {
    // the study time is saved every 30 s while playing
    val SESSION_ENDED_AFTER = 45.seconds
  }
}
