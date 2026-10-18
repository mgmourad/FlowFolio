package com.flowfolio.feature.reader

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** Frees what the reader can rebuild when Android reports the system is short on memory while we are visible. */
@Composable
internal fun TrimMemoryEffect(onTrim: () -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onTrim)
    DisposableEffect(context) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // Only the levels that mean "the system is short on memory while we are visible".
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
                    level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                ) {
                    latest()
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = latest()
        }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }
}
