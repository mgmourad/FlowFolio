package com.flowfolio

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.flowfolio.core.designsystem.LocalBookOpener
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.LocalDestinationScope
import com.flowfolio.core.designsystem.LocalSharedTransition
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScreenEntrance
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.feature.library.AnnotationsScreen
import com.flowfolio.feature.library.AnnotationsViewModel
import com.flowfolio.feature.library.BookAnnotationsScreen
import com.flowfolio.feature.library.BookmarksScreen
import com.flowfolio.feature.library.BookmarksViewModel
import com.flowfolio.feature.library.DetailsScreen
import com.flowfolio.feature.library.DetailsViewModel
import com.flowfolio.feature.library.EditDetailsScreen
import com.flowfolio.feature.library.LibraryScreen
import com.flowfolio.feature.library.LibraryViewModel
import com.flowfolio.feature.library.SettingsScreen
import com.flowfolio.feature.library.ShelfPageScreen
import com.flowfolio.feature.library.ShelfPageViewModel
import com.flowfolio.feature.library.ShelvesScreen
import com.flowfolio.feature.reader.ReaderScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FlowFolioApp(appViewModel: AppViewModel) {
    val nav = rememberNavController()
    LaunchedEffect(nav) { appViewModel.directOpens.collect { (id, layout) -> nav.navigate(Routes.reader(id, layout)) } }
    val entry by nav.currentBackStackEntryAsState()
    // A shelf tapped on the Shelves tab: the library scrolls to its row.
    var shelfToShow by rememberSaveable { mutableStateOf<Long?>(null) }
    AppShell(entry?.destination?.route, onSelect = nav::switchTo) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransition provides this) {
                NavHost(
                    navController = nav,
                    startDestination = TopLevel.Library.route,
                    // The app's motion (ADR-0033): fade through between tabs, shared axis into pages.
                    enterTransition = NavMotion.enter,
                    exitTransition = NavMotion.exit,
                    popEnterTransition = NavMotion.popEnter,
                    popExitTransition = NavMotion.popExit
                ) {
                    destination(TopLevel.Library.route) {
                        val library: LibraryViewModel = hiltViewModel()
                        LaunchedEffect(library) { appViewModel.sharedFiles.collect(library::onFileShared) }
                        ScreenEntrance {
                            LibraryScreen(
                                onOpenDetails = { book, origin -> nav.navigate(Routes.details(book.id, origin)) },
                                onRead = { book -> nav.navigate(Routes.reader(book.id)) },
                                onOpenShelf = { id -> nav.navigate(Routes.shelf(id)) },
                                onBookmarks = { book -> nav.navigate(Routes.bookBookmarks(book.id)) },
                                onAnnotations = { book -> nav.navigate(Routes.bookAnnotations(book.id)) },
                                scrollToShelf = shelfToShow,
                                onScrolledToShelf = { shelfToShow = null },
                                viewModel = library
                            )
                        }
                    }
                    destination(TopLevel.Shelves.route) {
                        ScreenEntrance {
                            // A shelf opens on its own page, as from its book count in the library (owner, 2026-10-22).
                            ShelvesScreen(onOpenShelf = { id -> nav.navigate(Routes.shelf(id)) })
                        }
                    }
                    destination(TopLevel.Bookmarks.route) {
                        ScreenEntrance { BookmarksScreen(onRead = { book -> nav.navigate(Routes.reader(book.id)) }) }
                    }
                    destination(TopLevel.Annotations.route) {
                        ScreenEntrance {
                            AnnotationsScreen(onOpenBook = { book -> nav.navigate(Routes.bookAnnotations(book.id)) })
                        }
                    }
                    destination(TopLevel.Settings.route) { ScreenEntrance { SettingsScreen() } }
                    detailsDestination(nav)
                    editDestination(nav)
                    bookBookmarksDestination(nav)
                    bookAnnotationsDestination(nav)
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
        // The cover flies in while the page settles around it (ADR-0034).
        ScreenEntrance(holdForLandings = false) {
            DetailsScreen(
                origin = backStack.arguments?.getString("from").orEmpty(),
                onBack = { nav.popBackStack() },
                onRead = { book -> nav.navigate(Routes.reader(book.id)) },
                onEdit = { book -> nav.navigate(Routes.edit(book.id)) },
                onOpenBook = { book -> nav.navigate(Routes.details(book.id, "")) }
            )
        }
    }
}

/** One book's ribbons (from a shelf row, a tile's menu or a library book's menu). */
private fun NavGraphBuilder.bookBookmarksDestination(nav: NavHostController) = composable(
    Routes.BOOK_BOOKMARKS,
    arguments = listOf(navArgument(BookmarksViewModel.ARG_BOOK_ID) { type = NavType.StringType })
) {
    // Not a tab: no bar or rail here, so no room kept for one.
    CompositionLocalProvider(LocalChromePadding provides PaddingValues()) {
        ScreenEntrance {
            BookmarksScreen(onRead = { book -> nav.navigate(Routes.reader(book.id)) }, onBack = { nav.popBackStack() })
        }
    }
}

/** One book's highlights and notes (from the Annotations tab). */
private fun NavGraphBuilder.bookAnnotationsDestination(nav: NavHostController) = composable(
    Routes.BOOK_ANNOTATIONS,
    arguments = listOf(navArgument(AnnotationsViewModel.ARG_BOOK_ID) { type = NavType.StringType })
) {
    ScreenEntrance {
        BookAnnotationsScreen(
            onBack = { nav.popBackStack() },
            onRead = { book -> nav.navigate(Routes.reader(book.id)) }
        )
    }
}

/** Edit details for a book (from the details page's menu). */
private fun NavGraphBuilder.editDestination(nav: NavHostController) = composable(
    Routes.EDIT,
    arguments = listOf(navArgument(DetailsViewModel.ARG_BOOK_ID) { type = NavType.StringType })
) {
    ScreenEntrance { EditDetailsScreen(onDone = { nav.popBackStack() }) }
}

/** A whole shelf, as tiles or a list (opened from a shelf's book count in the library). */
private fun NavGraphBuilder.shelfDestination(nav: NavHostController) = composable(
    Routes.SHELF,
    arguments = listOf(navArgument(ShelfPageViewModel.ARG_SHELF_ID) { type = NavType.LongType })
) {
    CompositionLocalProvider(LocalDestinationScope provides this) {
        ScreenEntrance {
            ShelfPageScreen(
                onBack = { nav.popBackStack() },
                onOpenDetails = { book, origin -> nav.navigate(Routes.details(book.id, origin)) },
                onBookmarks = { book -> nav.navigate(Routes.bookBookmarks(book.id)) }
            )
        }
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
    // Back closes the book: the opening plays in reverse, back to the shelf or page it was opened from.
    val opener = LocalBookOpener.current
    ReaderScreen(onBack = { opener?.close { nav.popBackStack() } ?: nav.popBackStack() })
}

/** Switches tabs the usual way: one copy of each top-level screen, each keeping its state; Back returns to Library. */
private fun NavHostController.switchTo(destination: TopLevel) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
