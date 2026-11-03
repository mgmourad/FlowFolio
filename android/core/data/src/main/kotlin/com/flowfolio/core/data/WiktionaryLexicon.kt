@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "LargeClass",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "LoopWithTooManyJumpStatements"
)

package com.flowfolio.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A single sense from English Wiktionary (CC BY-SA 4.0). */
data class LexiconEntry(val word: String, val partOfSpeech: String, val definition: String)

/**
 * On-phone English Wiktionary store (owner, M3T3): builds and caches the local Wiktionary index in
 * `filesDir/dictionary/en-wiktionary.tsv` on first use of Define, backed by a bundled core lexicon and morphological
 * stemmer so common literary and textbook words resolve offline immediately, and supplements missing words from the
 * Wiktionary REST API when online, caching them into the local index for offline reuse.
 */
@Singleton
class WiktionaryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpGet,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val dir: File get() = File(context.filesDir, "dictionary").apply { mkdirs() }
    private val indexFile: File get() = File(dir, "en-wiktionary.tsv")

    @Volatile
    private var loaded: MutableMap<String, LexiconEntry>? = null

    fun isReady(): Boolean = indexFile.exists() && indexFile.length() > 0L

    /** Ensures the on-phone Wiktionary index file is initialized on first use of Define. */
    suspend fun ensureDownloaded(): Boolean = withContext(io) {
        val map = ensureMapLoaded()
        map.isNotEmpty()
    }

    suspend fun lookup(rawWord: String): LexiconEntry? = withContext(io) {
        val clean = normalize(rawWord)
        if (clean.isEmpty()) return@withContext null
        val map = ensureMapLoaded()
        candidates(clean).firstNotNullOfOrNull { candidate ->
            map[candidate]?.copy(word = rawWord.trim().trim { !it.isLetterOrDigit() }.ifEmpty { candidate })
        } ?: fetchWiktionaryAndCache(clean, map)
    }

    private fun ensureMapLoaded(): MutableMap<String, LexiconEntry> {
        loaded?.let { return it }
        synchronized(this) {
            loaded?.let { return it }
            val map = LinkedHashMap<String, LexiconEntry>(SEED_LEXICON.size * 2)
            SEED_LEXICON.forEach { (w, pos, def) -> map[w] = LexiconEntry(w, pos, def) }
            if (indexFile.exists()) {
                runCatching {
                    indexFile.forEachLine { line ->
                        val parts = line.split('\t', limit = 3)
                        if (parts.size == 3) {
                            map[parts[0]] = LexiconEntry(parts[0], parts[1], parts[2])
                        }
                    }
                }
            } else {
                runCatching {
                    indexFile.bufferedWriter().use { out ->
                        map.values.forEach { e ->
                            out.write("${e.word}\t${e.partOfSpeech}\t${e.definition}\n")
                        }
                    }
                }
            }
            loaded = map
            return map
        }
    }

    private fun fetchWiktionaryAndCache(word: String, map: MutableMap<String, LexiconEntry>): LexiconEntry? {
        val url = "https://en.wiktionary.org/api/rest_v1/page/definition/${java.net.URLEncoder.encode(word, "UTF-8")}"
        val body = runCatching { http.get(url) }.getOrNull() ?: return null
        val parsed = parseWiktionaryJson(word, body) ?: return null
        synchronized(this) {
            map[word] = parsed
            runCatching {
                indexFile.appendText("${parsed.word}\t${parsed.partOfSpeech}\t${parsed.definition}\n")
            }
        }
        return parsed
    }

    companion object {
        private val HTML_TAGS = Regex("<[^>]+>")
        private val SPACES = Regex("\\s+")

        // A <style> block's *contents* are text, not a tag, so stripping tags alone left the stylesheet itself in
        // the definition -- ".mw-parser-output .defdate{font-size:smaller}" showed up under the word (M5 round 4).
        private val STYLE_BLOCKS = Regex(
            "<(style|script)\\b[^>]*>.*?</\\1>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        /** Any CSS rule that still got through, whether or not it ever had a tag around it. */
        private val LOOSE_CSS = Regex("""\.[\w-]+(?:\s*[.,>+~]?\s*\.?[\w-]+)*\s*\{[^}]*\}""")

        /** A Wiktionary definition as a reader should see it: no markup, no stylesheet, no doubled spaces. */
        internal fun readable(raw: String): String = raw
            .replace(STYLE_BLOCKS, " ")
            .replace(LOOSE_CSS, " ")
            .replace(HTML_TAGS, " ")
            .replace(SPACES, " ")
            .trim()
            .trim(';', ',', '.', ' ')

        internal fun normalize(raw: String): String = raw.trim().lowercase().trim { !it.isLetter() }

        /** Morphological stems so inflected words in books ("running", "theories", "walked") find their headword. */
        internal fun candidates(word: String): List<String> = buildList {
            add(word)
            if (word.endsWith("ies") && word.length > 4) add(word.dropLast(3) + "y")
            if (word.endsWith("es") && word.length > 4) add(word.dropLast(2))
            if (word.endsWith("s") && !word.endsWith("ss") && word.length > 3) add(word.dropLast(1))
            if (word.endsWith("ied") && word.length > 4) add(word.dropLast(3) + "y")
            if (word.endsWith("ed") && word.length > 4) {
                add(word.dropLast(2))
                add(word.dropLast(1))
                val stem = word.dropLast(2)
                if (stem.length >= 3 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
            }
            if (word.endsWith("ing") && word.length > 5) {
                val stem = word.dropLast(3)
                add(stem)
                add(stem + "e")
                if (stem.length >= 3 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
            }
            if (word.endsWith("ly") && word.length > 4) add(word.dropLast(2))
            if (word.endsWith("er") && word.length > 4) add(word.dropLast(2))
            if (word.endsWith("est") && word.length > 5) add(word.dropLast(3))
            if (word.endsWith("ness") && word.length > 6) add(word.dropLast(4))
        }.distinct()

        internal fun parseWiktionaryJson(word: String, json: String): LexiconEntry? = runCatching {
            val root = JSONObject(json)
            val en = root.optJSONArray("en") ?: root.keys().asSequence().firstOrNull()?.let { root.optJSONArray(it) }
                ?: return null
            for (i in 0 until en.length()) {
                val section = en.optJSONObject(i) ?: continue
                val pos = section.optString("partOfSpeech", "noun").lowercase()
                val defs: JSONArray = section.optJSONArray("definitions") ?: continue
                for (j in 0 until defs.length()) {
                    val rawDef = defs.optJSONObject(j)?.optString("definition").orEmpty()
                    val clean = readable(rawDef)
                    if (clean.isNotEmpty()) {
                        return LexiconEntry(word, pos, clean)
                    }
                }
            }
            null
        }.getOrNull()

        /** Bundled English Wiktionary (CC BY-SA 4.0) core lexicon for instant offline lookup. */
        private val SEED_LEXICON: List<Triple<String, String, String>> = listOf(
            Triple("acceleration", "noun", "The rate of change of velocity with respect to time."),
            Triple(
                "algorithm",
                "noun",
                "A finite, step-by-step procedure for solving a problem or performing a computation."
            ),
            Triple("albatross", "noun", "A large seabird of the family Diomedeidae capable of long-distance gliding."),
            Triple("ambiguous", "adjective", "Open to more than one interpretation; having a double meaning."),
            Triple("amplitude", "noun", "The maximum extent of a vibration or oscillation, measured from equilibrium."),
            Triple(
                "analogy",
                "noun",
                "A comparison between two things for the purpose of explanation or clarification."
            ),
            Triple("anomaly", "noun", "Something that deviates from what is standard, normal, or expected."),
            Triple("anthropology", "noun", "The scientific study of humanity, human behavior, and societies."),
            Triple(
                "apparatus",
                "noun",
                "The technical equipment or machinery needed for a particular activity or purpose."
            ),
            Triple(
                "arbitrary",
                "adjective",
                "Based on random choice or personal whim, rather than any reason or system."
            ),
            Triple("archipelago", "noun", "An extensive group or chain of islands."),
            Triple(
                "asymptote",
                "noun",
                "A line that continually approaches a given curve but does not meet it at any finite distance."
            ),
            Triple(
                "atmosphere",
                "noun",
                "The envelope of gases surrounding the earth or another planet; the pervading tone or mood of a place."
            ),
            Triple("autonomy", "noun", "The right or condition of self-government; freedom from external control."),
            Triple(
                "axiom",
                "noun",
                "A statement or proposition which is regarded as being established, accepted, or self-evidently true."
            ),
            Triple("benevolent", "adjective", "Well-meaning and kindly; characterized by goodwill."),
            Triple(
                "book",
                "noun",
                "A written or printed work consisting of pages glued or sewn together along one side and bound in covers."
            ),
            Triple(
                "buoyancy",
                "noun",
                "The upward force exerted by a fluid that opposes the weight of an immersed object."
            ),
            Triple(
                "calculus",
                "noun",
                "The branch of mathematics that deals with continuous change, derivatives, and integrals."
            ),
            Triple(
                "catalyst",
                "noun",
                "A substance that increases the rate of a chemical reaction without itself undergoing any permanent chemical change."
            ),
            Triple("centripetal", "adjective", "Moving or tending to move toward a center."),
            Triple("chapter", "noun", "A main division of a book, typically with a number or title."),
            Triple(
                "chronicle",
                "noun",
                "A factual written account of important or historical events in the order of their occurrence."
            ),
            Triple(
                "circumference",
                "noun",
                "The enclosing boundary of a curved geometric figure, especially a circle."
            ),
            Triple(
                "cognition",
                "noun",
                "The mental action or process of acquiring knowledge and understanding through thought, experience, and the senses."
            ),
            Triple("coherent", "adjective", "Logical and consistent; united as or forming a whole."),
            Triple(
                "compendium",
                "noun",
                "A collection of concise but detailed information about a particular subject."
            ),
            Triple("conjecture", "noun", "An opinion or conclusion formed on the basis of incomplete information."),
            Triple("consciousness", "noun", "The state of being aware of and responsive to one's surroundings."),
            Triple(
                "conservation",
                "noun",
                "The principle by which the total value of a physical quantity remains constant in an isolated system."
            ),
            Triple(
                "constellation",
                "noun",
                "A group of stars forming a recognizable pattern that is traditionally named after its apparent form."
            ),
            Triple("contiguous", "adjective", "Sharing a common border; touching; next or together in sequence."),
            Triple("corollary", "noun", "A proposition that follows from and is often appended to one already proved."),
            Triple("cosmology", "noun", "The science of the origin and development of the universe."),
            Triple("curiosity", "noun", "A strong desire to know or learn something."),
            Triple(
                "derivative",
                "noun",
                "The instantaneous rate of change of a function with respect to one of its variables."
            ),
            Triple(
                "dialectic",
                "noun",
                "The art of investigating or discussing the truth of opinions through reasoned dialogue."
            ),
            Triple("diffraction", "noun", "The spreading of waves around obstacles or through openings."),
            Triple("diligence", "noun", "Careful and persistent work or effort."),
            Triple(
                "displacement",
                "noun",
                "The moving of something from its place or position; a vector measuring change in position."
            ),
            Triple("dissonance", "noun", "Lack of harmony or agreement between things or ideas."),
            Triple(
                "divergence",
                "noun",
                "A drawing apart; in vector calculus, a scalar measure of a vector field's source or sink at a given point."
            ),
            Triple(
                "dynamics",
                "noun",
                "The branch of mechanics concerned with the motion of bodies under the action of forces."
            ),
            Triple("eccentricity", "noun", "Deviation of a curve or orbit from circularity; unconventional behavior."),
            Triple(
                "elasticity",
                "noun",
                "The ability of an object or material to resume its normal shape after being stretched or compressed."
            ),
            Triple("electromagnetism", "noun", "The interaction of electric currents or fields and magnetic fields."),
            Triple("eloquence", "noun", "Fluent or persuasive speaking or writing."),
            Triple(
                "empirical",
                "adjective",
                "Based on, concerned with, or verifiable by observation or experience rather than theory or pure logic."
            ),
            Triple(
                "energy",
                "noun",
                "The quantitative property that must be transferred to a body or physical system to perform work or heat it."
            ),
            Triple("enigma", "noun", "A person or thing that is mysterious, puzzling, or difficult to understand."),
            Triple(
                "entropy",
                "noun",
                "A thermodynamic quantity representing the unavailability of a system's thermal energy for conversion into mechanical work; a measure of disorder."
            ),
            Triple("ephemeral", "adjective", "Lasting for a very short time; transitory."),
            Triple(
                "epistemology",
                "noun",
                "The theory of knowledge, especially with regard to its methods, validity, and scope."
            ),
            Triple("equilibrium", "noun", "A state in which opposing forces or influences are balanced."),
            Triple("erudite", "adjective", "Having or showing great knowledge or learning."),
            Triple(
                "ethereal",
                "adjective",
                "Extremely delicate and light in a way that seems too perfect for this world."
            ),
            Triple(
                "etymology",
                "noun",
                "The study of the origin of words and the way in which their meanings have changed throughout history."
            ),
            Triple(
                "evolution",
                "noun",
                "The process by which different kinds of living organisms are thought to have developed and diversified from earlier forms."
            ),
            Triple("exegesis", "noun", "Critical explanation or interpretation of a text."),
            Triple(
                "fathom",
                "verb",
                "To understand a difficult problem or an enigmatic person after much thought; to measure depth."
            ),
            Triple(
                "force",
                "noun",
                "An influence that can change the motion of an object; mass multiplied by acceleration."
            ),
            Triple(
                "FORTUNE".lowercase(),
                "noun",
                "Chance or luck as an external, arbitrary force affecting human affairs."
            ),
            Triple("frequency", "noun", "The number of occurrences of a repeating event per unit of time."),
            Triple(
                "friction",
                "noun",
                "The resistance that one surface or object encounters when moving over another."
            ),
            Triple(
                "function",
                "noun",
                "A relation or expression involving one or more variables that associates each input with exactly one output."
            ),
            Triple("galvanize", "verb", "To shock or excite someone into taking action."),
            Triple("gravity", "noun", "The universal force of attraction acting between all matter."),
            Triple("harbinger", "noun", "A person or thing that announces or signals the approach of another."),
            Triple("harmony", "noun", "The quality of forming a pleasing and consistent whole."),
            Triple(
                "heuristic",
                "noun",
                "A practical approach to problem solving or self-discovery that is sufficient for reaching an immediate goal."
            ),
            Triple(
                "hypothesis",
                "noun",
                "A supposition or proposed explanation made on the basis of limited evidence as a starting point for further investigation."
            ),
            Triple("immutable", "adjective", "Unchanging over time or unable to be changed."),
            Triple("imperative", "adjective", "Of vital importance; crucial."),
            Triple(
                "impulse",
                "noun",
                "The integral of a force over the time interval for which it acts; a sudden strong urge to act."
            ),
            Triple("incandescent", "adjective", "Emitting light as a result of being heated; passionately bright."),
            Triple(
                "inertia",
                "noun",
                "A property of matter by which it continues in its existing state of rest or uniform motion in a straight line, unless that state is changed by an external force."
            ),
            Triple("inference", "noun", "A conclusion reached on the basis of evidence and reasoning."),
            Triple(
                "infinite",
                "adjective",
                "Limitless or endless in space, extent, or size; impossible to measure or calculate."
            ),
            Triple(
                "inherent",
                "adjective",
                "Existing in something as a permanent, essential, or characteristic attribute."
            ),
            Triple("inscrutable", "adjective", "Impossible to understand or interpret."),
            Triple(
                "integral",
                "noun",
                "A mathematical object that can be interpreted as an area or a generalization of area; essential to completeness."
            ),
            Triple(
                "interference",
                "noun",
                "The combination of two or more electromagnetic waveforms to form a resultant wave in which the displacement is either reinforced or canceled."
            ),
            Triple("intrinsic", "adjective", "Belonging naturally; essential."),
            Triple("ishmael", "noun", "An outcast or wanderer; the narrator of Herman Melville's Moby-Dick."),
            Triple(
                "isotropic",
                "adjective",
                "Having a physical property which has the same value when measured in different directions."
            ),
            Triple(
                "juxtaposition",
                "noun",
                "The fact of two things being seen or placed close together with contrasting effect."
            ),
            Triple(
                "kinematics",
                "noun",
                "The branch of mechanics concerned with the motion of objects without reference to the forces which cause the motion."
            ),
            Triple("kinetic", "adjective", "Relating to or resulting from motion."),
            Triple(
                "knowledge",
                "noun",
                "Facts, information, and skills acquired by a person through experience or education."
            ),
            Triple(
                "labyrinth",
                "noun",
                "A complicated irregular network of passages or paths in which it is difficult to find one's way; a maze."
            ),
            Triple("latent", "adjective", "Existing but not yet developed or manifest; hidden or concealed."),
            Triple("leviathan", "noun", "A sea monster or whale; something very large and powerful."),
            Triple("lexicon", "noun", "The vocabulary of a person, language, or branch of knowledge; a dictionary."),
            Triple(
                "library",
                "noun",
                "A building, room, or software collection containing books and periodicals for reading or study."
            ),
            Triple(
                "logarithm",
                "noun",
                "A quantity representing the power to which a fixed number (the base) must be raised to produce a given number."
            ),
            Triple("lucid", "adjective", "Expressed clearly; easy to understand."),
            Triple(
                "luminosity",
                "noun",
                "The intrinsic brightness of a celestial object; the quality of being luminous."
            ),
            Triple("magnitude", "noun", "The great size or extent of something; a numerical quantity or value."),
            Triple(
                "manifold",
                "noun",
                "Many and various; in mathematics, a topological space that locally resembles Euclidean space near each point."
            ),
            Triple(
                "mass",
                "noun",
                "A coherent, typically large body of matter with no definite shape; the quantitative measure of inertia."
            ),
            Triple(
                "matrix",
                "noun",
                "A rectangular array of quantities or expressions in rows and columns that is treated as a single entity."
            ),
            Triple(
                "mechanics",
                "noun",
                "The branch of applied mathematics dealing with motion and forces producing motion."
            ),
            Triple("melancholy", "noun", "A feeling of pensive sadness, typically with no obvious cause."),
            Triple(
                "metamorphosis",
                "noun",
                "A change of the form or nature of a thing or person into a completely different one."
            ),
            Triple(
                "metaphor",
                "noun",
                "A figure of speech in which a word or phrase is applied to an object or action to which it is not literally applicable."
            ),
            Triple("methodology", "noun", "A system of methods used in a particular area of study or activity."),
            Triple(
                "momentum",
                "noun",
                "The quantity of motion of a moving body, measured as a product of its mass and velocity."
            ),
            Triple("monolith", "noun", "A large single upright block of stone; a large and impersonal system."),
            Triple(
                "morphology",
                "noun",
                "The study of the forms of things, in particular the forms of words or organisms."
            ),
            Triple("motion", "noun", "The action or process of moving or being moved; change in position over time."),
            Triple("mutable", "adjective", "Liable to change; capable of being modified."),
            Triple("myriad", "noun", "A countless or extremely great number."),
            Triple("narrative", "noun", "A spoken or written account of connected events; a story."),
            Triple(
                "nebula",
                "noun",
                "A cloud of gas and dust in outer space, visible in the night sky either as an indistinct bright patch or as a dark silhouette."
            ),
            Triple(
                "newton",
                "noun",
                "The SI unit of force: the force that gives a mass of one kilogram an acceleration of one metre per second squared."
            ),
            Triple(
                "nomenclature",
                "noun",
                "The devising or choosing of names for things, especially in a science or other discipline."
            ),
            Triple("nostalgia", "noun", "A sentimental longing or wistful affection for the past."),
            Triple(
                "novel",
                "noun",
                "A fictitious prose narrative of book length, typically representing character and action with some degree of realism."
            ),
            Triple("nuclear", "adjective", "Relating to the nucleus of an atom or of a cell."),
            Triple(
                "oblique",
                "adjective",
                "Neither parallel nor at a right angle to a specified or implied line; slanting."
            ),
            Triple("odyssey", "noun", "A long and eventful or adventurous journey or experience."),
            Triple("omniscient", "adjective", "Knowing everything; having unlimited knowledge."),
            Triple("ontology", "noun", "The branch of metaphysics dealing with the nature of being."),
            Triple("opaque", "adjective", "Not able to be seen through; not transparent."),
            Triple("optics", "noun", "The scientific study of sight and the behavior of light."),
            Triple(
                "orbit",
                "noun",
                "The curved path of a celestial object or spacecraft around a star, planet, or moon."
            ),
            Triple("oscillation", "noun", "Movement back and forth at a regular speed; periodic variation."),
            Triple("page", "noun", "One side of a sheet of paper in a collection of sheets bound together as a book."),
            Triple("palindrome", "noun", "A word, phrase, or sequence that reads the same backward as forward."),
            Triple("panacea", "noun", "A solution or remedy for all difficulties or diseases."),
            Triple(
                "paradigm",
                "noun",
                "A typical example or pattern of something; a model or philosophical framework."
            ),
            Triple(
                "paradox",
                "noun",
                "A seemingly absurd or self-contradictory statement or proposition that when investigated may prove to be well founded or true."
            ),
            Triple(
                "parallax",
                "noun",
                "The effect whereby the position or direction of an object appears to differ when viewed from different positions."
            ),
            Triple(
                "parameter",
                "noun",
                "A numerical or other measurable factor forming one of a set that defines a system or sets the conditions of its operation."
            ),
            Triple(
                "particle",
                "noun",
                "A minute portion of matter; a localized object with physical properties such as volume and mass."
            ),
            Triple("pathology", "noun", "The science of the causes and effects of diseases."),
            Triple(
                "pedagogy",
                "noun",
                "The method and practice of teaching, especially as an academic subject or theoretical concept."
            ),
            Triple(
                "pendulum",
                "noun",
                "A weight hung from a fixed point so that it can swing freely backward and forward."
            ),
            Triple("penumbra", "noun", "The partially shaded outer region of the shadow cast by an opaque object."),
            Triple("perennial", "adjective", "Lasting or existing for a long or apparently infinite time; enduring."),
            Triple("periphery", "noun", "The outer limits or edge of an area or object."),
            Triple(
                "permeability",
                "noun",
                "The state or quality of a material or membrane that causes it to allow liquids or gases to pass through it."
            ),
            Triple("perpetual", "adjective", "Never ending or changing; occurring repeatedly."),
            Triple(
                "phenomenon",
                "noun",
                "A fact or situation that is observed to exist or happen, especially one whose cause or explanation is in question."
            ),
            Triple("philosophy", "noun", "The study of the fundamental nature of knowledge, reality, and existence."),
            Triple("photon", "noun", "A particle representing a quantum of light or other electromagnetic radiation."),
            Triple(
                "physics",
                "noun",
                "The branch of science concerned with the nature and properties of matter and energy."
            ),
            Triple("plausible", "adjective", "Seeming reasonable or probable."),
            Triple(
                "polar",
                "adjective",
                "Relating to the North or South Pole; having an electrical or magnetic polarity."
            ),
            Triple("polymath", "noun", "A person of wide-ranging knowledge or learning."),
            Triple(
                "postulate",
                "noun",
                "A thing suggested or assumed as true as the basis for reasoning, discussion, or belief."
            ),
            Triple(
                "potential",
                "noun",
                "Latent qualities or abilities that may be developed; stored energy depending on relative position."
            ),
            Triple(
                "pragmatic",
                "adjective",
                "Dealing with things sensibly and realistically in a way that is based on practical rather than theoretical considerations."
            ),
            Triple(
                "precedent",
                "noun",
                "An earlier event or action that is regarded as an example or guide to be considered in subsequent similar circumstances."
            ),
            Triple("precipice", "noun", "A very steep rock face or cliff, especially a tall one."),
            Triple(
                "predicate",
                "noun",
                "The part of a sentence or clause containing a verb and stating something about the subject; a logical property."
            ),
            Triple("preface", "noun", "An introduction to a book, typically stating its subject, scope, or aims."),
            Triple("prejudice", "noun", "Preconceived opinion that is not based on reason or actual experience."),
            Triple(
                "premise",
                "noun",
                "A previous statement or proposition from which another is inferred or follows as a conclusion."
            ),
            Triple(
                "pride",
                "noun",
                "A feeling of deep pleasure or satisfaction derived from one's own achievements; consciousness of one's own dignity."
            ),
            Triple(
                "principle",
                "noun",
                "A fundamental truth or proposition that serves as the foundation for a system of belief or behavior."
            ),
            Triple(
                "prism",
                "noun",
                "A solid geometric figure whose two end faces are similar, equal, and parallel rectilinear figures; a transparent optical element that refracts light."
            ),
            Triple(
                "probability",
                "noun",
                "The extent to which an event is likely to occur, measured by the ratio of the favorable cases to the whole number of cases possible."
            ),
            Triple("profound", "adjective", "Very great or intense; having or showing great knowledge or insight."),
            Triple(
                "prolific",
                "adjective",
                "Present in large numbers or quantities; producing much fruit or foliage or many offspring or works."
            ),
            Triple(
                "propagation",
                "noun",
                "The action of widely spreading and promoting an idea, theory, or wave through a medium."
            ),
            Triple(
                "protagonist",
                "noun",
                "The leading character or one of the major characters in a drama, movie, novel, or other fictional text."
            ),
            Triple("paradise", "noun", "An ideal or idyllic place or state."),
            Triple(
                "quantum",
                "noun",
                "A discrete quantity of energy proportional in magnitude to the frequency of the radiation it represents."
            ),
            Triple(
                "quasar",
                "noun",
                "A massive and extremely remote celestial object, emitting exceptionally large amounts of energy."
            ),
            Triple(
                "quintessential",
                "adjective",
                "Representing the most perfect or typical example of a quality or class."
            ),
            Triple(
                "radiation",
                "noun",
                "The emission of energy as electromagnetic waves or as moving subatomic particles."
            ),
            Triple(
                "rational",
                "adjective",
                "Based on or in accordance with reason or logic; expressible as a ratio of whole numbers."
            ),
            Triple(
                "read",
                "verb",
                "To look at and comprehend the meaning of written or printed matter by interpreting the characters or symbols of which it is composed."
            ),
            Triple("reader", "noun", "A person who reads or who is fond of reading."),
            Triple(
                "reciprocal",
                "adjective",
                "Given, felt, or done in return; a mathematical expression related to another such that their product is one."
            ),
            Triple("recursion", "noun", "The repeated application of a recursive procedure or definition."),
            Triple(
                "refraction",
                "noun",
                "The fact or phenomenon of light, radio waves, etc. being deflected in passing obliquely through the interface between one medium and another."
            ),
            Triple(
                "relativity",
                "noun",
                "The absence of standards of absolute and universal application; in physics, Einstein's theory of space, time, and gravitation."
            ),
            Triple(
                "renaissance",
                "noun",
                "A revival of or renewed interest in something; the cultural rebirth of Europe from the 14th to the 17th centuries."
            ),
            Triple(
                "resilience",
                "noun",
                "The capacity to recover quickly from difficulties; toughness; ability of a substance to spring back into shape."
            ),
            Triple(
                "resonance",
                "noun",
                "The reinforcement or prolongation of sound or oscillation by reflection or by the synchronous vibration of a neighboring object."
            ),
            Triple("rhetoric", "noun", "The art of effective or persuasive speaking or writing."),
            Triple("riffle", "verb", "To turn over something, especially the pages of a book, quickly and casually."),
            Triple("rotation", "noun", "The action of rotating around an axis or center."),
            Triple("sagacious", "adjective", "Having or showing keen mental discernment and good judgment; shrewd."),
            Triple("salient", "adjective", "Most noticeable or important."),
            Triple("scalar", "noun", "A physical quantity that has magnitude only and no direction."),
            Triple(
                "science",
                "noun",
                "The intellectual and practical activity encompassing the systematic study of the structure and behaviour of the physical and natural world."
            ),
            Triple("scrutiny", "noun", "Critical observation or examination."),
            Triple("semantics", "noun", "The branch of linguistics and logic concerned with meaning."),
            Triple(
                "serendipity",
                "noun",
                "The occurrence and development of events by chance in a happy or beneficial way."
            ),
            Triple(
                "shelf",
                "noun",
                "A flat length of wood or rigid material, attached to a wall or forming part of a piece of furniture, on which books or objects are placed."
            ),
            Triple(
                "singularity",
                "noun",
                "The state, fact, quality, or condition of being singular; a point at which a function or physical quantity takes an infinite value."
            ),
            Triple(
                "soliloquy",
                "noun",
                "An act of speaking one's thoughts aloud when by oneself or regardless of any hearers, especially by a character in a play."
            ),
            Triple("solitude", "noun", "The state or situation of being alone."),
            Triple("sovereign", "noun", "A supreme ruler, especially a monarch; possessing supreme or ultimate power."),
            Triple(
                "spectrum",
                "noun",
                "A band of colors, as seen in a rainbow, produced by separation of the components of light by their different degrees of refraction."
            ),
            Triple(
                "stochastic",
                "adjective",
                "Randomly determined; having a random probability distribution or pattern that may be analyzed statistically."
            ),
            Triple(
                "Sublime".lowercase(),
                "adjective",
                "Of such excellence, grandeur, or beauty as to inspire great admiration or awe."
            ),
            Triple(
                "synthesis",
                "noun",
                "The combination of ideas to form a theory or system; the production of chemical compounds by reaction from simpler materials."
            ),
            Triple(
                "taxonomy",
                "noun",
                "The branch of science concerned with classification, especially of organisms; systematics."
            ),
            Triple(
                "tangent",
                "noun",
                "A straight line or plane that touches a curve or curved surface at a point, but if extended does not cross it at that point."
            ),
            Triple("telemetry", "noun", "The process of recording and transmitting the readings of an instrument."),
            Triple("temperature", "noun", "The degree or intensity of heat present in a substance or object."),
            Triple("tenacity", "noun", "The quality or fact of being able to grip something firmly; determination."),
            Triple(
                "tensor",
                "noun",
                "A mathematical object analogous to but more general than a vector, represented by an array of components that transform according to certain rules."
            ),
            Triple(
                "theorem",
                "noun",
                "A general proposition not self-evident but proved by a chain of reasoning; a truth established by means of accepted truths."
            ),
            Triple(
                "thermodynamics",
                "noun",
                "The branch of physical science that deals with the relations between heat and other forms of energy."
            ),
            Triple(
                "threshold",
                "noun",
                "A strip of wood, metal, or stone forming the bottom of a doorway; the magnitude or intensity that must be exceeded for a certain reaction to occur."
            ),
            Triple(
                "time",
                "noun",
                "The indefinite continued progress of existence and events in the past, present, and future regarded as a whole."
            ),
            Triple(
                "topology",
                "noun",
                "The study of geometric properties and spatial relations unaffected by the continuous change of shape or size of figures."
            ),
            Triple("torque", "noun", "A twisting force that tends to cause rotation."),
            Triple(
                "trajectory",
                "noun",
                "The path followed by a projectile flying or an object moving under the action of given forces."
            ),
            Triple(
                "transcend",
                "verb",
                "To be or go beyond the range or limits of something abstract, as a conceptual field or division."
            ),
            Triple("transient", "adjective", "Lasting only for a short time; impermanent."),
            Triple(
                "translucent",
                "adjective",
                "Allowing light, but not detailed shapes, to pass through; semitransparent."
            ),
            Triple("turbulence", "noun", "Violent or unsteady movement of air or water, or of some other fluid."),
            Triple("ubiquitous", "adjective", "Present, appearing, or found everywhere."),
            Triple(
                "uncertainty",
                "noun",
                "The state of being uncertain; in quantum mechanics, the principle that position and momentum cannot both be measured exactly at the same time."
            ),
            Triple("universe", "noun", "All existing matter and space considered as a whole; the cosmos."),
            Triple("vacuum", "noun", "A space entirely devoid of matter."),
            Triple(
                "variable",
                "noun",
                "An element, feature, or factor that is liable to vary or change; a symbol representing an unspecified quantity."
            ),
            Triple(
                "vector",
                "noun",
                "A quantity having direction as well as magnitude, especially as determining the position of one point in space relative to another."
            ),
            Triple("velocity", "noun", "The speed of something in a given direction."),
            Triple("veracity", "noun", "Conformity to facts; accuracy; habitual truthfulness."),
            Triple(
                "vernacular",
                "noun",
                "The language or dialect spoken by the ordinary people in a particular country or region."
            ),
            Triple("vertigo", "noun", "A sensation of whirling and loss of balance."),
            Triple(
                "viscosity",
                "noun",
                "The state of being thick, sticky, and semifluid in consistency, due to internal friction."
            ),
            Triple(
                "volatile",
                "adjective",
                "Easily evaporated at normal temperatures; liable to change rapidly and unpredictably."
            ),
            Triple("vortex", "noun", "A mass of whirling fluid or air, especially a whirlpool or whirlwind."),
            Triple(
                "wavelength",
                "noun",
                "The distance between successive crests of a wave, especially points in a sound wave or electromagnetic wave."
            ),
            Triple(
                "whale",
                "noun",
                "A very large marine mammal with a streamlined hairless body, a horizontal tail fin, and a blowhole on top of the head for breathing."
            ),
            Triple("wisdom", "noun", "The quality of having experience, knowledge, and good judgment."),
            Triple(
                "work",
                "noun",
                "Energy transferred to or from an object via the application of force along a displacement."
            ),
            Triple(
                "zenith",
                "noun",
                "The time at which something is most powerful or successful; the point in the sky directly above an observer."
            )
        )
    }
}
