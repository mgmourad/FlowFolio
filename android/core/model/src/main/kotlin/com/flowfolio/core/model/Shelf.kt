package com.flowfolio.core.model

/**
 * A collection the reader curates ("Fall Term", "Leisure Reading"). A book can be on any number of shelves. [color]
 * is chosen when it is made; null follows the app's theme.
 */
data class Shelf(val id: Long, val name: String, val bookIds: List<String>, val color: ShelfColor? = null)

/** A shelf's colour: the hues of Material You's colour choices (owner, 2026-10-22). */
enum class ShelfColor { BLUE, TEAL, GREEN, AMBER, ROSE, VIOLET }
