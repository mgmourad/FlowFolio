package com.flowfolio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.flowfolio.core.designsystem.LocalDestinationScope
import com.flowfolio.core.designsystem.ScreenEntrance
import com.flowfolio.core.designsystem.clearOfChrome
import com.flowfolio.feature.library.ShelfPageScreen
import com.flowfolio.feature.library.ShelfPageViewModel

/** The pages that show books as a shelf: a shelf, the Favourites tab and an imported folder. */
internal fun NavGraphBuilder.shelfPages(nav: NavHostController) {
    shelfDestination(nav)
    favoritesDestination(nav)
    folderDestination(nav)
}

/** A whole shelf, as tiles or a list (opened from a shelf's book count in the library). */
private fun NavGraphBuilder.shelfDestination(nav: NavHostController) = composable(
    Routes.SHELF,
    arguments = listOf(navArgument(ShelfPageViewModel.ARG_SHELF_ID) { type = NavType.LongType })
) {
    CompositionLocalProvider(LocalDestinationScope provides this) {
        ScreenEntrance(Modifier.clearOfChrome()) {
            ShelfPageScreen(
                onBack = { nav.popBackStack() },
                onOpenDetails = { book, origin -> nav.navigate(Routes.details(book.id, origin)) },
                onBookmarks = { book -> nav.navigate(Routes.bookBookmarks(book.id)) }
            )
        }
    }
}

/**
 * The Favourites tab (owner, M3T2): the starred books on a shelf page of their own, with no Back since it is a tab.
 * The shelf id is the route's default argument, so the page's view model reads it like any shelf's.
 */
private fun NavGraphBuilder.favoritesDestination(nav: NavHostController) = composable(
    TopLevel.Favorites.route,
    arguments = listOf(
        navArgument(ShelfPageViewModel.ARG_SHELF_ID) {
            type = NavType.LongType
            defaultValue = ShelfPageViewModel.FAVORITES
        }
    )
) {
    CompositionLocalProvider(LocalDestinationScope provides this) {
        ScreenEntrance(Modifier.clearOfChrome()) {
            ShelfPageScreen(
                onBack = null,
                onOpenDetails = { book, origin -> nav.navigate(Routes.details(book.id, origin)) },
                onBookmarks = { book -> nav.navigate(Routes.bookBookmarks(book.id)) }
            )
        }
    }
}

/** An imported folder: its folders and books, as tiles or a list (from the library's Folders chip). */
private fun NavGraphBuilder.folderDestination(nav: NavHostController) = composable(
    Routes.FOLDER,
    arguments = listOf(navArgument(ShelfPageViewModel.ARG_FOLDER) { type = NavType.StringType })
) {
    CompositionLocalProvider(LocalDestinationScope provides this) {
        ScreenEntrance(Modifier.clearOfChrome()) {
            ShelfPageScreen(
                onBack = { nav.popBackStack() },
                onOpenDetails = { book, origin -> nav.navigate(Routes.details(book.id, origin)) },
                onBookmarks = { book -> nav.navigate(Routes.bookBookmarks(book.id)) },
                onOpenFolder = { path -> nav.navigate(Routes.folder(path)) }
            )
        }
    }
}
