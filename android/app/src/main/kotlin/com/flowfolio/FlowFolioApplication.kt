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

    override fun onCreate() {
        super.onCreate()
        // The home-screen widgets follow the app's own process only (not `:convert`, which converts and nothing else).
        if (getProcessName() == packageName) widgets.start()
    }
}
