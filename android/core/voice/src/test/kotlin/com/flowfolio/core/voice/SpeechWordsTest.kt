package com.flowfolio.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every word reaches the voice in a form it can say (owner, 2026-11-10: "Kubernetes", "CSA", "NIST" were skipped). The
 * voice only says words in its lexicon, so anything else is rephrased into words it knows; nothing is dropped.
 */
class SpeechWordsTest {
    private val vocab = Vocabulary(
        listOf(
            "the", "and", "we", "use", "on", "a", "b", "c", "d", "i", "m", "p", "s", "v", "nist", "nasa", "git", "hub",
            "dev", "ops", "docker", "dockers", "file", "web", "hook", "sub", "net", "it", "is", "that", "change", "log",
            "back", "ends", "zzlettera", "zzpluralm", "zzpluralp", "zzpluraly", "power", "shell", "mac", "o", "x",
            "kubernetes", "ansible", "ansibles", "kim", "ram", "us", "book", "kri", "ef", "e", "f", "k", "r", "n", "l",
            "y", "six", "eight", "three", "ip", "in", "spec", "kilo", "watt", "hours"
        )
    )

    private fun say(text: String) = SpeechWords.expand(text, vocab)

    @Test
    fun knownWordsAreLeftAlone() {
        assertEquals("We use Docker on the book", say("We use Docker on the book"))
    }

    @Test
    fun unknownAcronymsAreSpelled() {
        assertEquals("the C S zzlettera and NIST", say("the CSA and NIST"))
    }

    @Test
    fun shortKnownAcronymsAreSpelledButLongOnesAreSaid() {
        // "US" and "IT" are words too, but in capitals they are letters; "NASA" is said as a word.
        assertEquals("the U S and NASA", say("the US and NASA"))
    }

    @Test
    fun wordsSaidAsWordsStayWordsInCapitals() {
        assertEquals("RAM", say("RAM"))
    }

    @Test
    fun pluralAcronymsKeepTheirPlural() {
        assertEquals("V zzpluralm", say("VMs"))
    }

    @Test
    fun camelCaseIsSplitIntoWordsItKnows() {
        assertEquals("Git Hub and Dev Ops", say("GitHub and DevOps"))
        assertEquals("Power Shell", say("PowerShell"))
    }

    @Test
    fun camelCaseWithAnAcronymSpellsThePartThatIsLetters() {
        assertEquals("mac O S", say("macOS"))
    }

    @Test
    fun possessivesSoundLikeTheirPlural() {
        assertEquals("Dockers and Ansibles", say("Docker's and Ansible's"))
    }

    @Test
    fun contractedIsBecomesIs() {
        assertEquals("that is it", say("that's it"))
    }

    @Test
    fun compoundsAreSplitIntoKnownWords() {
        assertEquals(
            "docker file web hook sub net change log back ends",
            say("Dockerfile webhook subnet changelog backends")
        )
    }

    @Test
    fun lettersAndDigitsAreSaidApart() {
        assertEquals("I P v six", say("IPv6"))
    }

    @Test
    fun anUnknownNameIsSoundedOutRatherThanSkipped() {
        assertEquals("kri ef", say("Krief"))
    }

    @Test
    fun anUnknownShortLowercaseWordWithoutVowelsIsSpelled() {
        // "npm": no vowels, so it is letters.
        assertEquals("n p m", say("npm"))
    }

    @Test
    fun capitalisedHeadingsAreReadAsWords() {
        // A heading in capitals is not a row of acronyms.
        assertEquals("THE BOOK AND THE HUB", say("THE BOOK AND THE HUB"))
    }

    @Test
    fun punctuationAroundWordsSurvives() {
        assertEquals("on Git Hub, and C S zzlettera.", say("on GitHub, and CSA."))
    }

    @Test
    fun nothingIsDropped() {
        // Every input word yields at least one spoken word.
        val text = "Zxqv Kubernetes Ansible Jenkins"
        val out = say(text)
        assertEquals(true, out.split(' ').size >= text.split(' ').size)
    }
}
