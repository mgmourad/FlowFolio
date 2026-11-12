package com.flowfolio.core.model

/** A book in the library. [id] is the SHA-256 of the file's bytes (hex), shared with the converter's bookId. */
data class Book(
    val id: String,
    val title: String,
    val pageCount: Int,
    val storage: BookStorage,
    val sizeBytes: Long,
    val addedAtMillis: Long,
    val lastOpenedAtMillis: Long?,
    val coverPath: String?,
    val passwordProtected: Boolean,
    /** Where the reader left off (0-based page), or null if the book has not been read yet. */
    val lastPage: Int? = null,
    /** The PDF's Author metadata (or what the reader typed), or null when unknown. */
    val author: String? = null,
    /** Subtitle, edition, publisher, description and the rest, as entered by the reader. */
    val details: BookDetails = BookDetails(),
    /** Pages run right to left (Arabic and other right-to-left books); set per book in the reader. */
    val rightToLeft: Boolean = false,
    /**
     * The book's printed page numbers ([PageLabels] runs): from the PDF, or read from its page margins. Null until the
     * reader has looked; "" when the book prints none (pages count from the cover).
     */
    val pageLabels: String? = null,
    val format: BookFormat = BookFormat.PDF,
    /** An EPUB's reading position as a reflow locator; [lastPage] and [pageCount] are at the size it was last read. */
    val lastLocator: String? = null,
    /** The folder it was imported with ("Books/Physics"), or null when it was imported on its own. */
    val folder: String? = null,
    /**
     * A PDF's view (ADR-0045): true for Dynamic Flow, false for the PDF, null when the reader never chose (then the
     * "Turn on Dynamic Flow upon opening" setting decides).
     */
    val flowMode: Boolean? = null,
    /** Where the reader is in the book's Dynamic Flow (a reflow locator); [lastPage] stays at the PDF page. */
    val flowLocator: String? = null,
    /** A favourite (owner, M3T2). */
    val favorite: Boolean = false,
    /** The reader's own intention for this book (owner, M5); null when they have not said. */
    val intent: ReadingIntent? = null,
    /** When the reader marked it finished (epoch millis); null while not finished. */
    val finishedAtMillis: Long? = null
) {
    /** Whether this PDF is to be read in Dynamic Flow, given the "upon opening" setting [onOpen]. */
    fun wantsFlow(onOpen: Boolean): Boolean = format == BookFormat.PDF && (flowMode ?: onOpen)

    /** Marked finished by the reader. */
    val finished: Boolean get() = finishedAtMillis != null

    /** How far through the book the reader is, 0..1 (0 when not started). */
    val progress: Float
        get() = lastPage?.let { if (pageCount > 1) it.toFloat() / (pageCount - 1) else 1f }?.coerceIn(0f, 1f) ?: 0f
}

/** Where the book's bytes live (ADR-0012 import storage setting). */
sealed interface BookStorage {
    val mode: StorageMode

    /** FlowFolio's own copy in app-private storage. */
    data class Copied(val path: String) : BookStorage {
        override val mode get() = StorageMode.COPY
    }

    /** Read in place through a persisted Storage Access Framework URI. */
    data class Linked(val uri: String) : BookStorage {
        override val mode get() = StorageMode.LINK
    }
}

enum class StorageMode { COPY, LINK }

/** What kind of file a book is. EPUBs are reflowable: their page count depends on the text size (Phase 2). */
enum class BookFormat { PDF, EPUB }

/** Whether a book's file can currently be opened. Only linked books can go missing. */
enum class Availability { AVAILABLE, MISSING }

/**
 * How the library orders its books (ADR-0052), in every view and inside each shelf row. [RECENT] is most recently
 * opened first; [TITLE] and [AUTHOR] are alphabetical; [DATE_ADDED], [PROGRESS] and [READ_TIME] put the newest, the
 * furthest read and the longest read first.
 */
enum class LibrarySort { RECENT, TITLE, AUTHOR, DATE_ADDED, PROGRESS, READ_TIME }

/** How the library lays its books out (ADR-0052): on shelves (rows over planks), as a grid, or as a list. */
enum class LibraryView { SHELF, GRID, LIST }

/** How the Shelves tab orders its shelves (ADR-0052): their own order, or by name, books, time read or marks. */
enum class ShelvesOrder { CUSTOM, NAME, MOST_BOOKS, MOST_READ, MOST_MARKS }

/** The library's stats band (ADR-0052): one summary line that opens, open at once, or not shown. */
enum class StatsBand { COLLAPSED, EXPANDED, HIDDEN }

/** How far the narrator's previous and next buttons move: the lock screen, the notifications and headsets. */
enum class NarratorSkip { PARAGRAPH, PAGE, CHAPTER, SUBCHAPTER }

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val storageMode: StorageMode = StorageMode.COPY,
    val askStorageEachImport: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val storageNoticeSeen: Boolean = false,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    /** How the library shows its books, remembered (ADR-0052). */
    val libraryView: LibraryView = LibraryView.SHELF,
    /** What Narrator Mode's previous and next buttons skip by (owner, 2026-11-10). */
    val narratorSkip: NarratorSkip = NarratorSkip.PARAGRAPH,
    /** The library's stats band: its state, remembered, and Settings' choice of Hidden (ADR-0052). */
    val statsBand: StatsBand = StatsBand.COLLAPSED,
    /** How the Shelves tab orders its shelves, remembered (ADR-0052). */
    val shelvesOrder: ShelvesOrder = ShelvesOrder.CUSTOM,
    /** Two pages side by side on wide windows (the unfolded Fold); off means always one page. */
    val readerSpreads: Boolean = true,
    /** In a spread, show the cover on its own (print style) rather than pairing from the first page. */
    val spreadCoverAlone: Boolean = true,
    /** The page colour in the reader (the Aa sheet's swatches); images always keep their printed colours. */
    val paperStyle: PaperStyle = PaperStyle.AUTO,
    /** How pages turn in the reader. */
    val pageTurn: PageTurn = PageTurn.CURL,
    /** The app's theme (ADR-0028); light or dark still follows [theme]. */
    val appStyle: AppStyle = AppStyle.FLOWFOLIO,
    /** Material You's colours, when [appStyle] is Material. */
    val materialSeed: MaterialSeed = MaterialSeed.WALLPAPER,
    /** Fetch a newer build when one is published; Android still asks before installing (ADR-0048). */
    val autoUpdate: Boolean = false,
    /** Look book details up online (Open Library, ADR-0017). Off until the reader agrees. */
    val onlineLookup: Boolean = false,
    /** Whether the first import has asked about the online lookup yet. */
    val onlineLookupAsked: Boolean = false,
    /** The language passages are translated into last (BCP-47); null means the phone's language. */
    val translateTo: String? = null,
    /** The languages the reader keeps as chips in Translate (BCP-47), newest last (ADR-0052). */
    val translateLanguages: List<String> = emptyList(),
    /** How long the reader's controls stay up after the last use (the riffle scrubber, ADR-0022). */
    val controlsAutoHide: ControlsAutoHide = ControlsAutoHide.S5,
    /** The riffle sound while dragging the scrubber's ribbon (ADR-0025); never in silent or vibrate mode. */
    val riffleSound: Boolean = true,
    /** Its loudness, 0 to 1. */
    val riffleVolume: Float = DEFAULT_SOUND_VOLUME,
    /** A page flip sound for every page turn and landing (ADR-0025); never in silent or vibrate mode. */
    val turnSound: Boolean = true,
    /** Its loudness, 0 to 1. */
    val turnVolume: Float = DEFAULT_SOUND_VOLUME,
    /** Haptic ticks while riffling (ADR-0023). */
    val scrubHaptics: Boolean = true,
    /** Screen brightness override in the reader (-1 follows system brightness; 0..1 overrides). */
    val readerBrightness: Float = DEFAULT_BRIGHTNESS,
    /** Keep the screen on while reading a book. */
    val keepScreenOn: Boolean = false,
    /** How EPUB text is set: font, size, spacing, margins (M3). */
    val text: ReaderText = ReaderText(),
    /** "Turn on Dynamic Flow upon opening" (ADR-0045): PDFs whose view was never chosen open in Dynamic Flow. */
    val dynamicFlowOnOpen: Boolean = false,
    /** What switching views does with the highlights, notes and bookmarks made in the view being left. */
    val flowCarry: FlowCarry = FlowCarry.ASK,
    /** Reading Atmosphere (owner, M5): whether the chosen atmosphere is put on for every book. */
    val atmosphereOn: Boolean = false,
    /** The atmosphere in force, by name; null means none has been chosen. */
    val atmosphereName: String? = null,
    /** The atmospheres the reader has saved, over and above [AtmospherePresets.all]. */
    val atmospheres: List<Atmosphere> = emptyList(),
    /**
     * Which first-run splashes and tips have been seen (owner, M5), by [Guide] name. A guide is shown once and
     * then never again, so this only ever grows; "Show me around again" in Settings empties it.
     */
    val seenGuides: Set<String> = emptySet(),
    /** The build number whose "what's new" notes were last shown, so the popup appears once after each update. */
    val lastSeenVersionCode: Long = 0,
    /** Whether first-use tips and the reader demo may appear; off means "never show tips again" (owner, 2026-11-07). */
    val tipsEnabled: Boolean = true
)

/** Switching between a PDF and its Dynamic Flow: ask, or (once "don't show again" was chosen) the remembered answer. */
enum class FlowCarry { ASK, CONVERT, SKIP }

/** The paper sounds' loudness until the reader moves the sliders. */
const val DEFAULT_SOUND_VOLUME = 0.7f
const val DEFAULT_BRIGHTNESS = -1f

/** The reader's auto-hide delays (the riffle scrubber's spec 5.7); [NEVER] keeps the controls up. */
enum class ControlsAutoHide { S3, S5, S8, S15, NEVER }

/**
 * The reader's paper (the mock's swatches). [AUTO] follows the app: pages as printed in light mode, Night in dark
 * mode. The others tint text and line art; photos are never changed.
 */
enum class PaperStyle { AUTO, PAPER, SEPIA, NIGHT, BLACK, RUSTIC }

/** The app's look (ADR-0028): the mock's neon FlowFolio design, classic wood, or Material You. */
enum class AppStyle { FLOWFOLIO, RUSTIC, MATERIAL, AMOLED }

/** Material You's colours: from the wallpaper (Android 12 and later), or one of these seeds' hues (CIE LCh degrees). */
@Suppress("MagicNumber") // Each seed is its hue; naming the numbers would only repeat the entries' names.
enum class MaterialSeed(val hue: Float) {
    WALLPAPER(-1f),
    BLUE(282f),
    TEAL(200f),
    GREEN(140f),
    AMBER(72f),
    ROSE(12f),
    VIOLET(312f)
}

/** Page turns: the 3D curl (default), sliding sideways, or one continuous vertical scroll. */
enum class PageTurn { CURL, SLIDE, SCROLL }
