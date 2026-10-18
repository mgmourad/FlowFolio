package com.flowfolio

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.LocalDestinationScope
import com.flowfolio.core.designsystem.LocalSharedTransition
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScreenEntrance
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.feature.library.DetailsScreen
import com.flowfolio.feature.library.DetailsViewModel
import com.flowfolio.feature.library.LibraryScreen
import com.flowfolio.feature.library.LibraryViewModel
import com.flowfolio.feature.library.SettingsScreen
import com.flowfolio.feature.library.ShelfPageScreen
import com.flowfolio.feature.library.ShelfPageViewModel
import com.flowfolio.feature.library.ShelvesScreen
import com.flowfolio.feature.reader.ReaderScreen

private object Routes {
    const val READER = "reader/{bookId}?layout={layout}"
    const val DETAILS = "book/{${DetailsViewModel.ARG_BOOK_ID}}?from={from}"

    fun reader(bookId: String, layout: String? = null) = "reader/$bookId" + (layout?.let { "?layout=$it" } ?: "")

    fun details(bookId: String, origin: String) = "book/$bookId?from=$origin"

    const val SHELF = "shelf/{${ShelfPageViewModel.ARG_SHELF_ID}}"

    fun shelf(id: Long) = "shelf/$id"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FlowFolioApp(appViewModel: AppViewModel) {
    val nav = rememberNavController()
    LaunchedEffect(nav) { appViewModel.directOpens.collect { (id, layout) -> nav.navigate(Routes.reader(id, layout)) } }
    val entry by nav.currentBackStackEntryAsState()
    // A shelf tapped on the Shelves tab: the library scrolls to its row.
    var shelfToShow by rememberSaveable { mutableStateOf<Long?>(null) }
    AppShell(current = TopLevel.of(entry?.destination?.route), onSelect = nav::switchTo) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransition provides this) {
                NavHost(
                    navController = nav,
                    startDestination = TopLevel.Library.route,
                    enterTransition = { fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) },
                    exitTransition = { fadeOut(tween(Motion.FAST_MS, easing = Motion.EaseOut)) }
                ) {
                    destination(TopLevel.Library.route) {
                        val library: LibraryViewModel = hiltViewModel()
                        LaunchedEffect(library) { appViewModel.sharedFiles.collect(library::onFileShared) }
                        ScreenEntrance {
                            LibraryScreen(
                                onOpenDetails = { book, origin -> nav.navigate(Routes.details(book.id, origin)) },
                                onRead = { book -> nav.navigate(Routes.reader(book.id)) },
                                onOpenShelf = { id -> nav.navigate(Routes.shelf(id)) },
                                scrollToShelf = shelfToShow,
                                onScrolledToShelf = { shelfToShow = null },
                                viewModel = library
                            )
                        }
                    }
                    destination(TopLevel.Shelves.route) {
                        ScreenEntrance {
                            ShelvesScreen(onOpenShelf = { id ->
                                shelfToShow = id
                                nav.switchTo(TopLevel.Library)
                            })
                        }
                    }
                    destination(TopLevel.Bookmarks.route) {
                        ScreenEntrance { Placeholder(R.string.tab_bookmarks, R.string.bookmarks_placeholder) }
                    }
                    destination(TopLevel.Settings.route) { ScreenEntrance { SettingsScreen() } }
                    detailsDestination(nav)
                    shelfDestination(nav)
                    readerDestination(nav)
                }
            }
        }
    }
}

/** A top-level destination whose content can take part in shared-element transitions. */
private fun NavGraphBuilder.destination(route: String, content: @Composable AnimatedContentScope.() -> Unit) =
    composable(route) {
        CompositionLocalProvider(LocalDestinationScope provides this) { content() }
    }

/** A book's details; `from` names the library row it was opened from, for the cover flight. */
private fun NavGraphBuilder.detailsDestination(nav: NavHostController) = composable(
    Routes.DETAILS,
    arguments = listOf(
        navArgument(DetailsViewModel.ARG_BOOK_ID) { type = NavType.StringType },
        navArgument("from") {
            type = NavType.StringType
            defaultValue = ""
        }
    )
) { backStack ->
    CompositionLocalProvider(LocalDestinationScope provides this) {
        DetailsScreen(
            origin = backStack.arguments?.getString("from").orEmpty(),
            onBack = { nav.popBackStack() },
            onRead = { book -> nav.navigate(Routes.reader(book.id)) }
        )
    }
}

/** A whole shelf, as tiles or a list (opened from a shelf's book count in the library). */
private fun NavGraphBuilder.shelfDestination(nav: NavHostController) = composable(
    Routes.SHELF,
    arguments = listOf(navArgument(ShelfPageViewModel.ARG_SHELF_ID) { type = NavType.LongType })
) {
    CompositionLocalProvider(LocalDestinationScope provides this) {
        ShelfPageScreen(
            onBack = { nav.popBackStack() },
            onOpenDetails = { book, origin -> nav.navigate(Routes.details(book.id, origin)) }
        )
    }
}

/** The reader; `layout` (single or spread) is a debug override used by the measurement scripts. */
private fun NavGraphBuilder.readerDestination(nav: NavHostController) = composable(
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

/** Switches tabs the usual way: one copy of each top-level screen, each keeping its state; Back returns to Library. */
private fun NavHostController.switchTo(destination: TopLevel) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Bookmarks until its slice lands: the screen title and a line on what is coming. */
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
