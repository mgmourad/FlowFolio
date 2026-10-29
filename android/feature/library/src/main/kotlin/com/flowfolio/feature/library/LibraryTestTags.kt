package com.flowfolio.feature.library

/** Semantic tags used by UI tests to find library, shelves and details elements. */
object LibraryTestTags {
    const val IMPORT_PROGRESS = "library_import_progress"
    const val ADD_BOOKS_LIST = "library_add_books_list"
    const val ADD_BOOKS_DONE = "library_add_books_done"
    const val SHELVE_BOOKS_DONE = "library_shelve_books_done"
    const val ADD_BOOKS = "library_add_books"
    const val SELECTION_ADD_TO_SHELF = "library_selection_add_to_shelf"
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
    const val SHELF_TILES = "shelf_page_tiles"
    const val SHELF_LIST = "shelf_page_list"
    const val SHELF_VIEW = "shelf_page_view"
    const val BOOKMARKS = "bookmarks"
    const val ANNOTATIONS = "annotations"
    const val BOOK_ANNOTATIONS = "book_annotations"
    const val THEME_PREFIX = "settings_theme_"
    const val SHELF_COLOUR_PREFIX = "shelf_colour_"
    const val HERO_ANNOTATIONS = "hero_annotations"
    const val HERO_BOOKMARKS = "hero_bookmarks"
    const val HERO_FLOW = "hero_flow"
    const val HERO_SHELF = "hero_shelf"
    const val SELECT = "select"
    const val SELECTION_BAR = "selection_bar"
    const val CONFIRM_BULK_DELETE = "confirm_bulk_delete"
    const val TEXT_FONT = "settings_text_font"
    const val TEXT_SAMPLE = "settings_text_sample"
    const val SEED_PREFIX = "settings_seed_"
    const val SETTINGS = "settings"
    const val EDIT_MENU = "details_edit"
    const val EDIT_DETAILS = "edit_details"
    const val SAVE_DETAILS = "edit_details_save"
    const val LOOKUP = "edit_details_lookup"

    fun book(id: String) = "book_$id"

    fun shelf(id: Long) = "shelf_$id"

    fun shelfCount(id: Long) = "shelf_count_$id"

    fun row(bookId: String) = "row_$bookId"

    fun folder(path: String) = "folder_$path"

    fun bookmark(id: Long) = "bookmark_$id"

    fun annotatedBook(id: String) = "annotated_$id"

    fun annotation(id: Long) = "annotation_$id"
}
