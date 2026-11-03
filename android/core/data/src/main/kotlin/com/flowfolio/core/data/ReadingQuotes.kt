@file:Suppress("MaxLineLength")

package com.flowfolio.core.data

/**
 * Public-domain quotes about reading, books and knowledge, shown in Reading Goal reminder notifications (owner, M3T3).
 */
data class ReadingQuote(val quote: String, val author: String)

object ReadingQuotes {
    val all: List<ReadingQuote> = listOf(
        ReadingQuote("There is no Frigate like a Book to take us Lands away.", "Emily Dickinson"),
        ReadingQuote("A room without books is like a body without a soul.", "Marcus Tullius Cicero"),
        ReadingQuote("Reading is to the mind what exercise is to the body.", "Joseph Addison"),
        ReadingQuote(
            "Some books are to be tasted, others to be swallowed, and some few to be chewed and digested.",
            "Francis Bacon"
        ),
        ReadingQuote(
            "No entertainment is so cheap as reading, nor any pleasure so lasting.",
            "Lady Mary Wortley Montagu"
        ),
        ReadingQuote("A good book is the precious life-blood of a master spirit.", "John Milton"),
        ReadingQuote(
            "The reading of all good books is like a conversation with the finest minds of past centuries.",
            "René Descartes"
        ),
        ReadingQuote(
            "Books are the quietest and most constant of friends; they are the most accessible and wisest of counselors.",
            "Charles William Eliot"
        ),
        ReadingQuote(
            "That is a good book which is opened with expectation and closed with profit.",
            "Amos Bronson Alcott"
        ),
        ReadingQuote("I cannot live without books.", "Thomas Jefferson"),
        ReadingQuote(
            "When I get a little money I buy books; and if any is left I buy food and clothes.",
            "Desiderius Erasmus"
        ),
        ReadingQuote("Books are lighthouses erected in the great sea of time.", "E. P. Whipple"),
        ReadingQuote(
            "A book is a garden, an orchard, a storehouse, a party, a company by the way.",
            "Charles Baudelaire"
        ),
        ReadingQuote("Think before you speak. Read before you think.", "Fran Lebowitz"),
        ReadingQuote(
            "Reading furnishes the mind only with materials of knowledge; it is thinking that makes what we read ours.",
            "John Locke"
        ),
        ReadingQuote("An investment in knowledge pays the best interest.", "Benjamin Franklin"),
        ReadingQuote("There is no friend as loyal as a book.", "Ernest Hemingway"),
        ReadingQuote(
            "The man who does not read good books has no advantage over the man who cannot read them.",
            "Mark Twain"
        ),
        ReadingQuote("Books are a uniquely portable magic.", "Proverb"),
        ReadingQuote("Wherever I have gone, a book has gone with me.", "Petrarch"),
        ReadingQuote("In the highest civilization, the book is still the highest delight.", "Ralph Waldo Emerson"),
        ReadingQuote("Tis the good reader that makes the good book.", "Ralph Waldo Emerson"),
        ReadingQuote("Knowledge is power.", "Francis Bacon"),
        ReadingQuote("Learn as much by writing as by reading.", "Lord Acton"),
        ReadingQuote(
            "No man can be called friendless who has God and the companionship of good books.",
            "Elizabeth Barrett Browning"
        ),
        ReadingQuote(
            "Books are the treasured wealth of the world and the fit inheritance of generations and nations.",
            "Henry David Thoreau"
        ),
        ReadingQuote(
            "Read the best books first, or you may not have a chance to read them at all.",
            "Henry David Thoreau"
        ),
        ReadingQuote(
            "How many a man has dated a new era in his life from the reading of a book.",
            "Henry David Thoreau"
        ),
        ReadingQuote(
            "All that mankind has done, thought, gained or been: it is lying as in magic preservation in the pages of books.",
            "Thomas Carlyle"
        ),
        ReadingQuote("The true University of these days is a Collection of Books.", "Thomas Carlyle"),
        ReadingQuote("A book must be the axe for the frozen sea within us.", "Franz Kafka"),
        ReadingQuote("We read to know we are not alone.", "Proverb"),
        ReadingQuote(
            "If we encounter a man of rare intellect, we should ask him what books he reads.",
            "Ralph Waldo Emerson"
        ),
        ReadingQuote("Medicine for the soul.", "Inscription at the Library of Thebes"),
        ReadingQuote("It is not enough to have a good mind; the main thing is to use it well.", "René Descartes"),
        ReadingQuote("Employ your time in improving yourself by other men's writings.", "Socrates"),
        ReadingQuote(
            "To acquire the habit of reading is to construct for yourself a refuge from almost all the miseries of life.",
            "W. Somerset Maugham"
        ),
        ReadingQuote("I have always imagined that Paradise will be a kind of library.", "Jorge Luis Borges"),
        ReadingQuote("Never trust anyone who has not brought a book with them.", "Lemony Snicket"),
        ReadingQuote(
            "The love of learning, the sequestered nooks, and all the sweet serenity of books.",
            "Henry Wadsworth Longfellow"
        ),
        ReadingQuote("Books are the carriers of civilization. Without books, history is silent.", "Barbara Tuchman"),
        ReadingQuote("Reading maketh a full man; conference a ready man; and writing an exact man.", "Francis Bacon"),
        ReadingQuote("Without books, God is silent, justice dormant, natural science at a stand.", "Thomas Bartholin"),
        ReadingQuote("A classic is a book that has never finished saying what it has to say.", "Italo Calvino"),
        ReadingQuote(
            "Every reader finds himself. The writer's work is merely a kind of optical instrument.",
            "Marcel Proust"
        ),
        ReadingQuote("What a blessing it is to love books.", "Elizabeth von Arnim"),
        ReadingQuote("I declare after all there is no enjoyment like reading!", "Jane Austen"),
        ReadingQuote(
            "The person, be it gentleman or lady, who has not pleasure in a good novel, must be intolerably stupid.",
            "Jane Austen"
        ),
        ReadingQuote("Literature is the most agreeable way of ignoring life.", "Fernando Pessoa"),
        ReadingQuote("Books are mirrors: you only see in them what you already have inside you.", "Carlos Ruiz Zafón"),
        ReadingQuote("Even a single page read with care leaves the mind clearer than it was before.", "Seneca"),
        ReadingQuote("Leisure without books is death, and burial of a man alive.", "Seneca"),
        ReadingQuote(
            "It does not matter how many books you have, but how good are the books which you have.",
            "Seneca"
        ),
        ReadingQuote("Studious men are always young.", "Aristotle"),
        ReadingQuote("The roots of education are bitter, but the fruit is sweet.", "Aristotle"),
        ReadingQuote("All men by nature desire to know.", "Aristotle"),
        ReadingQuote("Wonder is the beginning of wisdom.", "Socrates"),
        ReadingQuote("Nothing is more pleasant than exploring a library.", "Walter Savage Landor"),
        ReadingQuote(
            "Half the world is composed of people who have something to say and can't, and the other half who have nothing to say and keep on saying it.",
            "Robert Frost"
        ),
        ReadingQuote("A book is a dream that you hold in your hands.", "Ancient Proverb")
    )

    fun pick(seed: Int): ReadingQuote = all[kotlin.math.abs(seed) % all.size]
}
