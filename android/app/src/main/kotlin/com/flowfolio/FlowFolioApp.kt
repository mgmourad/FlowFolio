package com.flowfolio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowfolio.feature.library.LibraryScreen
import com.flowfolio.feature.library.LibraryViewModel
import com.flowfolio.feature.library.SettingsScreen
import com.flowfolio.feature.reader.ReaderScreen

private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val READER = "reader/{bookId}?layout={layout}"

    fun reader(bookId: String, layout: String? = null) = "reader/$bookId" + (layout?.let { "?layout=$it" } ?: "")
}

@Composable
fun FlowFolioApp(appViewModel: AppViewModel) {
    val nav = rememberNavController()
    LaunchedEffect(nav) { appViewModel.directOpens.collect { (id, layout) -> nav.navigate(Routes.reader(id, layout)) } }
    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            val library: LibraryViewModel = hiltViewModel()
            LaunchedEffect(library) { appViewModel.sharedFiles.collect(library::onFileShared) }
            LibraryScreen(
                onOpenBook = { book -> nav.navigate(Routes.reader(book.id)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                viewModel = library
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(
            Routes.READER,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("layout") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ReaderScreen(onBack = { nav.popBackStack() })
        }
    }
}
