package com.flowfolio.core.model

/** A collection the reader curates ("Fall Term", "Leisure Reading"). A book can be on any number of shelves. */
data class Shelf(val id: Long, val name: String, val bookIds: List<String>)
