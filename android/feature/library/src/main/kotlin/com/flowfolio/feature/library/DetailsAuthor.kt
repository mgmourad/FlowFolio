package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.Book3D
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookType

/**
 * The mock's Author tab (`.author-card`): initials on the cover's colours, the author's name and role, their bio, and
 * their other books in the library. What is not known yet points to Edit details.
 */
@Composable
internal fun AuthorPanel(state: DetailsUiState, book: Book, onBook: (Book) -> Unit) {
    val author = book.author
    if (author == null) {
        Text(stringResource(R.string.author_unknown), style = FlowFolioType.sub.merge(FlowFolio.hint))
        return
    }
    val cover by rememberCover(book)
    val colours = book.face(cover).colors
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(AVATAR)
                    .clip(CircleShape)
                    .drawBehind { drawRect(Brush.linearGradient(listOf(colours.start, colours.end))) },
                contentAlignment = Alignment.Center
            ) {
                Text(initials(author), style = FlowFolioType.heroTitle.copy(fontSize = 22.sp, color = colours.ink))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(author, style = FlowFolioType.heroTitle.copy(fontSize = 22.sp).merge(FlowFolio.title))
                book.details.authorRole?.let {
                    Text(
                        it,
                        style = FlowFolioType.rowDetail.merge(FlowFolio.subItem),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Text(
            book.details.authorBio ?: stringResource(R.string.author_bio_missing),
            style = FlowFolioType.sub.copy(fontSize = 15.sp, lineHeight = 23.sp).merge(FlowFolio.hint),
            modifier = Modifier.padding(top = 16.dp)
        )
        if (state.sameAuthor.isNotEmpty()) {
            Text(
                stringResource(R.string.author_more).uppercase(),
                style = FlowFolioType.eyebrow.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                state.sameAuthor.take(MORE_BOOKS).forEach { other ->
                    val art by rememberCover(other)
                    Book3D(other.face(art), 62.dp, 90.dp, onClick = { onBook(other) })
                }
            }
        }
    }
}

/** "Dr. Amara Okafor" -> "AO": first letters of the first and last names, titles left out. */
internal fun initials(name: String): String {
    val words = name.split(' ', '.', ',').filter { it.length > 1 && it.lowercase() !in TITLES }
    return listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull())
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { name.take(1).uppercase() }
}

internal val BookType.label: Int
    get() = when (this) {
        BookType.TEXTBOOK -> R.string.type_textbook
        BookType.NOVEL -> R.string.type_novel
        BookType.NONFICTION -> R.string.type_nonfiction
        BookType.OTHER -> R.string.type_other
    }

private val TITLES = setOf("dr", "prof", "mr", "mrs", "ms", "sir", "dame")
private val AVATAR = 64.dp
private const val MORE_BOOKS = 3
