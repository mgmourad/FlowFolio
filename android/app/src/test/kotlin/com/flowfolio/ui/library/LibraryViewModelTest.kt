package com.flowfolio.ui.library

import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryViewModelTest {
    @Test
    fun `initial state is an empty library`() {
        val state = LibraryViewModel().uiState.value
        assertTrue(state.isEmpty)
    }
}
