package com.flowfolio.feature.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.flowfolio.core.data.Found
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookType

/**
 * The Edit details form's text fields; it survives rotation and fold changes. The looked-up details with no field of
 * their own (subjects, print pages, series, rating) ride along unseen, so saving keeps them and Look up fills them.
 */
@Stable
class EditForm(values: List<String>) {
    var title by mutableStateOf(values[0])
    var subtitle by mutableStateOf(values[1])
    var author by mutableStateOf(values[2])
    var edition by mutableStateOf(values[3])
    var released by mutableStateOf(values[4])
    var publisher by mutableStateOf(values[5])
    var isbn by mutableStateOf(values[6])
    var language by mutableStateOf(values[7])
    var description by mutableStateOf(values[8])
    var authorRole by mutableStateOf(values[9])
    var authorBio by mutableStateOf(values[10])
    private var subjects by mutableStateOf(values.getOrElse(11) { "" })
    private var printPages by mutableStateOf(values.getOrElse(12) { "" })
    private var series by mutableStateOf(values.getOrElse(13) { "" })
    private var rating by mutableStateOf(values.getOrElse(14) { "" })
    private var ratingsCount by mutableStateOf(values.getOrElse(15) { "" })

    private fun values() = listOf(
        title, subtitle, author, edition, released, publisher, isbn, language, description, authorRole, authorBio,
        subjects, printPages, series, rating, ratingsCount
    )

    /** The details as typed; blank fields are dropped by the repository. */
    fun details(type: BookType?) = BookDetails(
        subtitle = subtitle,
        edition = edition,
        publisher = publisher,
        isbn = isbn,
        description = description,
        language = language,
        released = released,
        authorRole = authorRole,
        authorBio = authorBio,
        type = type,
        subjects = subjects.split(SUBJECTS).filter { it.isNotBlank() },
        printPages = printPages.toIntOrNull(),
        series = series,
        rating = rating.toFloatOrNull(),
        ratingsCount = ratingsCount.toIntOrNull()
    )

    /** Puts what the lookup found into the fields that are still empty; true when any field changed. */
    fun fillEmpty(found: Found): Boolean {
        val f = found.details
        val before = values()
        if (author.isBlank()) author = found.author.orEmpty()
        if (subtitle.isBlank()) subtitle = f.subtitle.orEmpty()
        if (edition.isBlank()) edition = f.edition.orEmpty()
        if (released.isBlank()) released = f.released.orEmpty()
        if (publisher.isBlank()) publisher = f.publisher.orEmpty()
        if (isbn.isBlank()) isbn = f.isbn.orEmpty()
        if (language.isBlank()) language = f.language.orEmpty()
        if (description.isBlank()) description = f.description.orEmpty()
        if (authorBio.isBlank()) authorBio = f.authorBio.orEmpty()
        if (subjects.isBlank()) subjects = f.subjects.joinToString(SUBJECTS)
        if (printPages.isBlank()) printPages = f.printPages?.toString().orEmpty()
        if (series.isBlank()) series = f.series.orEmpty()
        if (rating.isBlank()) {
            rating = f.rating?.toString().orEmpty()
            ratingsCount = f.ratingsCount?.toString().orEmpty()
        }
        return values() != before
    }

    companion object {
        private const val SUBJECTS = "\n"

        fun of(book: Book): EditForm {
            val d = book.details
            return EditForm(
                listOf(
                    book.title,
                    d.subtitle,
                    book.author,
                    d.edition,
                    d.released,
                    d.publisher,
                    d.isbn,
                    d.language,
                    d.description,
                    d.authorRole,
                    d.authorBio,
                    d.subjects.joinToString(SUBJECTS),
                    d.printPages?.toString(),
                    d.series,
                    d.rating?.toString(),
                    d.ratingsCount?.toString()
                ).map { it.orEmpty() }
            )
        }

        val Saver: Saver<EditForm, Any> = listSaver(save = { it.values() }, restore = { EditForm(it) })
    }
}
