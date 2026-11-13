package com.flowfolio

import android.app.Application
import com.flowfolio.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt's dependency graph is created here, once per process. */
@HiltAndroidApp
class FlowFolioApplication : Application() {
    @Inject
    lateinit var widgets: WidgetUpdater

    @Inject
    lateinit var resources: com.flowfolio.core.data.ResourceDownloads

    override fun onCreate() {
        super.onCreate()
        // The home-screen widgets follow the app's own process only (not `:convert`, which converts and nothing else).
        if (getProcessName() == packageName) {
            widgets.start()
            // The Full edition's packs go in place on first start (a few seconds, in the background).
            resources.installBundled(this)
        }
    }
}
