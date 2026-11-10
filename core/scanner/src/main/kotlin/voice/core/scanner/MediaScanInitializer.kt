package voice.core.scanner

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
public class MediaScanInitializer(private val mediaScanTrigger: MediaScanTrigger) : AppInitializer {

  override fun onAppStart(application: Application) {
    mediaScanTrigger.scan()
    var lastScan = SystemClock.elapsedRealtime()
    // files get replaced in a file manager while Voice stays in the background, so coming back
    // scans again. The throttle keeps quick switches between apps from rescanning every time.
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        private var startedActivities = 0

        override fun onActivityStarted(activity: Activity) {
          if (startedActivities++ != 0) return
          val now = SystemClock.elapsedRealtime()
          if (now - lastScan < FOREGROUND_SCAN_INTERVAL_MS) return
          lastScan = now
          mediaScanTrigger.scan()
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
  }

  private companion object {
    const val FOREGROUND_SCAN_INTERVAL_MS = 30_000L
  }
}
