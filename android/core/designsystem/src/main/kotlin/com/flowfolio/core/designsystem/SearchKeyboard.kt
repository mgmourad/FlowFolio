package com.flowfolio.core.designsystem

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

/** What a search field passes to its text field: a Search key that searches and puts the keyboard away. */
class SearchKeyboard(val options: KeyboardOptions, val actions: KeyboardActions)

/**
 * The keyboard for every search field (owner, 2026-11-12): its Search (or Enter) key runs [onSearch], hides the
 * keyboard and lets go of the field, so the results are in view. Fields that search as you type pass nothing.
 */
@Composable
fun rememberSearchKeyboard(onSearch: () -> Unit = {}): SearchKeyboard {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return SearchKeyboard(
        KeyboardOptions(imeAction = ImeAction.Search),
        KeyboardActions(onSearch = {
            onSearch()
            keyboard?.hide()
            focus.clearFocus()
        })
    )
}
