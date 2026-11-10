package com.flowfolio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.flowfolio.core.designsystem.LocalDestinationScope
import com.flowfolio.core.designsystem.ScreenEntrance
import com.flowfolio.feature.library.AnnotationsScreen
import com.flowfolio.feature.library.BookmarksScreen
import com.flowfolio.feature.library.DictionaryScreen
import com.flowfolio.feature.library.LibraryFilter
import com.flowfolio.feature.library.NotebookScreen
import com.flowfolio.feature.library.NotebookSection

/**
 * The Notebook tab (ADR-0052): Bookmarks, Annotations and Dictionary as its sections. The section shown is kept
 * across visits; `notebook?section=` opens a given one.
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
) { entry ->
    val asked = entry.arguments?.getString("section")
    var section by rememberSaveable(asked) { mutableStateOf(Destinations.sectionOf(asked)) }
    CompositionLocalProvider(LocalDestinationScope provides this) {
        ScreenEntrance {
            NotebookScreen(section, onSection = { section = it }) { shown ->
                when (shown) {
                    NotebookSection.HIGHLIGHTS ->
                        AnnotationsScreen(onOpenBook = { book -> nav.navigate(Routes.bookAnnotations(book.id)) })

                    NotebookSection.BOOKMARKS -> BookmarksScreen(
                        onRead = { nav.navigate(Routes.reader(it.id)) },
                        onOpenBook = { book -> nav.navigate(Routes.bookBookmarks(book.id)) }
                    )

                    NotebookSection.WORDS -> DictionaryScreen(onRead = { nav.navigate(Routes.reader(it.id)) })
                }
            }
        }
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
