package com.flowfolio

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScreenEntrance
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.feature.library.LibraryScreen
import com.flowfolio.feature.library.LibraryViewModel
import com.flowfolio.feature.library.SettingsScreen
import com.flowfolio.feature.reader.ReaderScreen

private object Routes {
    const val READER = "reader/{bookId}?layout={layout}"

    fun reader(bookId: String, layout: String? = null) = "reader/$bookId" + (layout?.let { "?layout=$it" } ?: "")
}

@Composable
fun FlowFolioApp(appViewModel: AppViewModel) {
    val nav = rememberNavController()
    LaunchedEffect(nav) { appViewModel.directOpens.collect { (id, layout) -> nav.navigate(Routes.reader(id, layout)) } }
    val entry by nav.currentBackStackEntryAsState()
    AppShell(current = TopLevel.of(entry?.destination?.route), onSelect = nav::switchTo) {
        NavHost(
            navController = nav,
            startDestination = TopLevel.Library.route,
            enterTransition = { fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) },
            exitTransition = { fadeOut(tween(Motion.FAST_MS, easing = Motion.EaseOut)) }
        ) {
            composable(TopLevel.Library.route) {
                val library: LibraryViewModel = hiltViewModel()
                LaunchedEffect(library) { appViewModel.sharedFiles.collect(library::onFileShared) }
                ScreenEntrance {
                    LibraryScreen(onOpenBook = { book -> nav.navigate(Routes.reader(book.id)) }, viewModel = library)
                }
            }
            composable(TopLevel.Shelves.route) {
                ScreenEntrance { Placeholder(R.string.tab_shelves, R.string.shelves_placeholder) }
            }
            composable(TopLevel.Bookmarks.route) {
                ScreenEntrance { Placeholder(R.string.tab_bookmarks, R.string.bookmarks_placeholder) }
            }
            composable(TopLevel.Settings.route) { ScreenEntrance { SettingsScreen() } }
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
}

/** Switches tabs the usual way: one copy of each top-level screen, each keeping its state; Back returns to Library. */
private fun NavHostController.switchTo(destination: TopLevel) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Shelves and Bookmarks until their slices land: the screen title and a line on what is coming. */
@Composable
private fun Placeholder(title: Int, body: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .chromeStart()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(title),
            style = FlowFolioType.screenTitle.merge(FlowFolio.title),
            modifier = Modifier.reveal(0)
        )
        Text(stringResource(body), style = FlowFolioType.sub.merge(FlowFolio.hint), modifier = Modifier.reveal(1))
    }
}
