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

private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val READER = "reader/{bookId}"

    fun reader(bookId: String) = "reader/$bookId"
}

@Composable
fun FlowFolioApp(appViewModel: AppViewModel) {
    val nav = rememberNavController()
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
        composable(Routes.READER, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) {
            ReaderPlaceholder(onBack = { nav.popBackStack() })
        }
    }
}

/** STUB until Phase 1 milestone 3 delivers the reader. */
@Composable
private fun ReaderPlaceholder(onBack: () -> Unit) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.reader_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("reader_placeholder")
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
    }
}
