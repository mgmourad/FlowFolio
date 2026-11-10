package com.flowfolio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.flowfolio.core.data.MarkKind
import com.flowfolio.core.designsystem.LocalDestinationScope
import com.flowfolio.core.designsystem.ScreenEntrance
import com.flowfolio.core.designsystem.clearOfChrome
import com.flowfolio.feature.library.AnnotationsScreen
import com.flowfolio.feature.library.BookmarksScreen
import com.flowfolio.feature.library.DictionaryScreen
import com.flowfolio.feature.library.LibraryFilter
import com.flowfolio.feature.library.NotebookBookScreen
import com.flowfolio.feature.library.NotebookHome
import com.flowfolio.feature.library.NotebookSection

/**
 * The Notebook tab (ADR-0052): every book's marks, counted once ([com.flowfolio.core.data.MarkCounts]).
 * `notebook?section=` opens it on one kind (the stats tiles, the retired routes).
 */
internal fun NavGraphBuilder.notebookDestination(nav: NavHostController) = composable(
    Destinations.NOTEBOOK,
    arguments = listOf(
        navArgument("section") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )
) {
    CompositionLocalProvider(LocalDestinationScope provides this) {
        ScreenEntrance {
            NotebookHome(onOpenBook = { book, kind -> nav.navigate(Destinations.notebookBook(book.id, kind)) })
        }
    }
}

/** One book's notebook: its marks by kind, each opening at its page (ADR-0052). */
internal fun NavGraphBuilder.notebookBookDestination(nav: NavHostController) = composable(
    Destinations.NOTEBOOK_BOOK,
    arguments = listOf(
        navArgument("bookId") { type = NavType.StringType },
        navArgument("kind") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )
) { entry ->
    val bookId = entry.arguments?.getString("bookId").orEmpty()
    val kind = MarkKind.entries.firstOrNull { it.name == entry.arguments?.getString("kind") }
    ScreenEntrance(Modifier.clearOfChrome()) {
        NotebookBookScreen(
            bookId = bookId,
            initialKind = kind,
            onBack = { nav.popBackStack() },
            onRead = { book -> nav.navigate(Routes.reader(book.id)) }
        )
    }
}

/**
 * The routes of the tabs ADR-0052 retired. Each forwards to its new home and leaves the back stack as if it had
 * been opened there directly.
 */
internal fun NavGraphBuilder.retiredDestinations(nav: NavHostController) {
    listOf(Destinations.FAVORITES, Destinations.BOOKMARKS, Destinations.ANNOTATIONS, Destinations.DICTIONARY)
        .forEach { route ->
            composable(route) {
                LaunchedEffect(Unit) {
                    when (val to = Destinations.redirectFor(route)) {
                        Destinations.Redirect.LibraryFavorites -> {
                            nav.switchTo(TopLevel.Library)
                            nav.currentBackStackEntry?.savedStateHandle?.set(
                                LIBRARY_FILTER,
                                LibraryFilter.FAVORITES.name
                            )
                        }

                        is Destinations.Redirect.Notebook -> nav.navigate(Destinations.notebook(to.section)) {
                            popUpTo(nav.graph.findStartDestination().id)
                            launchSingleTop = true
                        }

                        null -> nav.switchTo(TopLevel.Library)
                    }
                }
            }
        }
}

/** The saved-state key the Library reads a requested chip from. */
internal const val LIBRARY_FILTER = "libraryFilter"
