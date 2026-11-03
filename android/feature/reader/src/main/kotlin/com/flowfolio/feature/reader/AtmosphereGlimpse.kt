package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.AtmosphereOverlay
import com.flowfolio.core.model.atmosphereInForce

/**
 * A glimpse of the atmosphere just put on (owner, M5): the scene fades up over the page, holds a moment and goes.
 *
 * It watches the atmosphere's name rather than a button, so it plays whether the choice came from the "Aa" sheet or
 * from Settings, and never on the first composition -- opening a book should not replay a mood chosen last week.
 */
@Composable
internal fun BoxScope.AtmosphereGlimpse(book: ReaderViewModel) {
    val settings by book.userSettings.collectAsStateWithLifecycle()
    val chosen = settings.atmosphereInForce()
    var token by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf<String?>(null) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(chosen?.name, settings.atmosphereOn) {
        val name = chosen?.name?.takeIf { settings.atmosphereOn }
        if (!seen) {
            seen = true
            shown = name
            return@LaunchedEffect
        }
        if (name != shown) {
            shown = name
            if (name != null) token++
        }
    }
    if (token > 0) {
        AtmosphereOverlay(chosen?.ambient?.takeIf { settings.atmosphereOn }, token, Modifier.matchParentSize())
    }
}
