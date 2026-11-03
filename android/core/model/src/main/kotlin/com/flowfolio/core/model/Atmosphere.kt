package com.flowfolio.core.model

/**
 * The sound behind the reading (owner, M5). [SILENCE] is the default and plays nothing at all, rather than playing
 * a silent track: an ambience you have not chosen should cost no battery.
 *
 * Each of the others names one looping recording bundled with the app (`core/data/src/main/res/raw/ambient_*.mp3`,
 * royalty-free, supplied by the owner 2026-11-02).
 */
enum class Ambient {
    SILENCE,
    RAIN,
    FIREPLACE,
    OCEAN,
    FOREST,
    STREAM,
    NIGHT_GARDEN,
    CAFE,
    BROWN_NOISE
}

/**
 * A Reading Atmosphere (owner, M5): how the page is set and what plays behind it, saved together under a [name] so
 * the whole mood can be put on at once.
 *
 * The page settings are the reader's own ([ReaderText], [PaperStyle]), so applying an atmosphere is the same as
 * having moved every slider in the "Aa" sheet by hand -- nothing in the reader needs to know atmospheres exist.
 */
data class Atmosphere(
    val name: String,
    val text: ReaderText = ReaderText(),
    val paper: PaperStyle = PaperStyle.AUTO,
    val ambient: Ambient = Ambient.SILENCE,
    /** How loud the ambience plays, 0 to 1. */
    val volume: Float = DEFAULT_AMBIENT_VOLUME,
    /** True for the five that ship with the app: they can be applied and copied, but not edited away. */
    val builtIn: Boolean = false,
    /**
     * A sound of the reader's own in place of [ambient] (owner, M5): either a file they picked ("content://...")
     * or the address of an audio stream. Null leaves [ambient] in charge.
     */
    val soundUri: String? = null
) {
    /** What this atmosphere actually plays: the reader's own sound where there is one, else the bundled ambience. */
    val silent: Boolean get() = soundUri == null && ambient == Ambient.SILENCE
}

/** Ambience sits under the words, so it starts quieter than the paper sounds do. */
const val DEFAULT_AMBIENT_VOLUME = 0.45f

/**
 * The five atmospheres the app ships with (owner, M5). They are starting points: applying one and then changing a
 * setting leaves the reader's own settings changed, and "Save as..." keeps the result as a new atmosphere.
 */
object AtmospherePresets {
    val RainyAfternoon = Atmosphere(
        name = "Rainy Afternoon",
        text = ReaderText(font = ReadingFont.LITERATA, lineSpacing = LineSpacing.RELAXED),
        paper = PaperStyle.PAPER,
        ambient = Ambient.RAIN,
        builtIn = true
    )

    val Fireside = Atmosphere(
        name = "Fireside",
        text = ReaderText(font = ReadingFont.LITERATA, margins = PageMargins.WIDE),
        paper = PaperStyle.SEPIA,
        ambient = Ambient.FIREPLACE,
        builtIn = true
    )

    val GardenMorning = Atmosphere(
        name = "Garden Morning",
        text = ReaderText(lineSpacing = LineSpacing.RELAXED, margins = PageMargins.WIDE),
        paper = PaperStyle.PAPER,
        ambient = Ambient.FOREST,
        builtIn = true
    )

    val MidnightReading = Atmosphere(
        name = "Midnight Reading",
        paper = PaperStyle.NIGHT,
        ambient = Ambient.NIGHT_GARDEN,
        builtIn = true
    )

    val MinimalFocus = Atmosphere(
        name = "Minimal Focus",
        paper = PaperStyle.AUTO,
        ambient = Ambient.BROWN_NOISE,
        builtIn = true
    )

    val all = listOf(RainyAfternoon, Fireside, GardenMorning, MidnightReading, MinimalFocus)
}

/**
 * Every atmosphere on offer: the five built in, then the reader's own.
 *
 * Names are unique: saving over a preset (moving its volume, say) keeps one entry rather than listing the name
 * twice, which is what used to put "Fireside" in the menu two times.
 */
fun UserSettings.allAtmospheres(): List<Atmosphere> {
    val byName = LinkedHashMap<String, Atmosphere>()
    AtmospherePresets.all.forEach { byName[it.name] = it }
    atmospheres.forEach { byName[it.name] = it }
    return byName.values.toList()
}

/**
 * The atmosphere the settings name, or null when none is chosen or the named one has since been deleted. Reading
 * this rather than the name keeps every caller from having to know where atmospheres come from.
 */
fun UserSettings.atmosphereInForce(): Atmosphere? =
    atmosphereName?.let { name -> allAtmospheres().firstOrNull { it.name == name } }
