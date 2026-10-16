package com.flowfolio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.ui.library.LibraryScreen
import com.flowfolio.ui.library.LibraryViewModel
import com.flowfolio.ui.theme.FlowFolioTheme
import dagger.hilt.android.AndroidEntryPoint

/** The app's only Activity. Screens are Compose destinations hosted here. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlowFolioTheme {
                val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
                LibraryScreen(state = state)
            }
        }
    }
}
