package com.flowfolio.feature.library

/** Semantic tags used by UI tests to find library, shelves and details elements. */
object LibraryTestTags {
    const val EMPTY = "library_empty"
    const val GRID = "library_grid"
    const val IMPORT = "library_import"
    const val SEARCH = "library_search"
    const val SEARCH_FIELD = "library_search_field"
    const val HERO = "library_hero"
    const val RESUME = "library_resume"
    const val NEW_SHELF = "shelf_picker_new"
    const val SHELF_NAME = "shelf_name"
    const val SHELVES = "shelves_grid"
    const val ADD_SHELF = "shelves_add"
    const val NEW_SHELF_CARD = "shelves_new_card"
    const val DETAILS = "details"
    const val READ = "details_read"
    const val ADD_TO_SHELF = "details_add_to_shelf"
    const val CONTENTS = "details_contents"

    fun book(id: String) = "book_$id"

    fun shelf(id: Long) = "shelf_$id"
}
