package com.flowfolio

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt's dependency graph is created here, once per process. */
@HiltAndroidApp
class FlowFolioApplication : Application()
