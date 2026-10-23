# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Added
- Phase 1 milestone 3 (reader, in progress): `:core:pagecurl` (vendored pagecurl with book-spread back pages),
  `:feature:reader` with page curl in single-page and two-page spread layouts (cover alone, print pairing),
  layout chosen from the window (fold/unfold/rotation keep the position), byte-budgeted page cache of GPU-backed
  images, password prompt, edge-gesture guard, `tools/measure-reader.ps1` (frames + memory on device).

- Launcher icon from the owner's artwork (adaptive icon: gradient background plus the artwork, built by
  `tools/icon/make_launcher_icons.ps1`; `tools/icon/preview_launcher_icon.ps1` previews it under launcher masks).
- Skeuomorphic page shading (iOS Books style): a soft gutter shadow and crease at the binding with a faint highlight
  where the paper bulges, outer-edge shading lit from the upper left, in spreads and single pages; softer cast shadow
  and back-page gutter on the turning sheet. Gradients only, so no offscreen layers (ADR-0013).
- Direction-based page turns on either page (iOS Books): swipe left = forward, swipe right = back, even short swipes;
  tap the right page of a spread to go forward, the left page to go back.
- Reader chrome styled like print: the page number has no background (grey type, soft shadow) at the bottom left of
  the left page (bottom centre on a single page); a tap in the centre of the screen shows the book title (top right
  of the right page, top centre on a single page) and Back for 10 seconds.
- Phase 1 milestone 4 (reader navigation): reading position saved in Room (schema v2, `books.last_page`) and resumed
  on open; Contents (outline, opens on the current chapter), Go to page (printed labels first, then 1..N) and a page
  scrubber in the centre-tap controls; PDF links (internal jumps; web links confirmed, http/https/mailto only);
  pinch-zoom up to 6x with 512 px 16-bit tiles rendered after the view settles (at most 40 tiles, freed on exit);
  Reading settings ("Aa"): two pages side by side on/off and cover-alone pairing on/off.
- Phase 1 milestone 5, foundations slice (ADR-0015, `docs/plans/phase1-m5-design.md`).
  - The owner's mock (`docs/design/FlowFolio_Mock.html`) is now the design reference: its palettes, surfaces,
    gradients, glow and shadow values, motion curves and grain.
  - Bundled Fraunces and Instrument Sans (SIL OFL, 1.17 MB).
  - Components: card, chip, pill, icon button, primary and ghost buttons, segmented control, sliding tabs, the
    mock's toggle, gradient progress bar, progress ring.
  - The 3D book (true perspective, spine depth from the page count, lift on press), the shelf plank and the mock's
    icons.
  - A debug-only design catalog (`--ez catalog true`).
- Phase 1 milestone 5, app shell slice:
  - Library, Shelves, Bookmarks and Settings tabs in the mock's floating glass tab bar (folded), or a glass rail on
    the left when the window is 600 dp or wider (unfolded). The chrome slides away in the reader.
  - Real backdrop blur on the glass (Haze, Android 12+; tint only on older phones).
  - Animated splash on cold start (the mock's icon, ribbon and page curl, wordmark and tagline; tap to skip).
  - Screen entrance (fade and rise) and staggered reveal motion from the mock. The mock's focus blur and slight
    scale on entrance are left out: each frame of a changing blur, or of glows at a changing scale, left a texture in
    the GPU cache (+140 MB on the library screen before the fix). Animated shadows and glows now draw once into a
    layer that is then transformed. The library screen costs 247 MB (193 MB before the shell).
  - Theme changes are revealed in a circle growing from the touch (700 ms).
  - Status and navigation bar icons follow the app's theme.
  - Shelves and Bookmarks are placeholders until their slices.
- Phase 1 milestone 5, Library, Shelves and Details slice:
  - Library as in the mock: header with search, sort and import; filter chips (All, In progress, Not started,
    Recently added); a "Continue reading" card with Resume; a row of 3D books on a plank per shelf, then the books on
    no shelf. Long-press a book to add it to shelves, locate its file or remove it.
  - Shelves: user collections (Room schema v3: `shelves`, `shelf_books`), cards with three books fanned out, a "New
    shelf" card, rename and remove; tapping a shelf scrolls the library to it.
  - Book details: blurred cover backdrop, swaying 3D cover, title, author, pills, Read/Continue and Add to shelf;
    Overview, Details and Contents tabs (the PDF's outline; tapping a chapter opens the reader there). Two panes when
    unfolded.
  - The cover flies from the shelf to the details page; Read swings the cover open before the reader appears.
  - Books keep the PDF's Author metadata (schema v3 `books.author`; books imported earlier show none until the
    metadata slice).
  - The backdrop blur is computed once on the CPU from a 40 px copy of the cover, not as a live GPU blur.
- Owner tweaks after slice 3:
  - The splash shows the owner's own icon artwork: it pops in, a band of light crosses it, then the wordmark rises.
  - Launcher icon refitted: the whole book fits the safe circle every launcher shape keeps, on the plate's blue
    extended to the edges (`tools/icon/make_launcher_icons.ps1` grows the plate's edge colours outward).
  - A shelf page: tap a shelf's book count to see the whole shelf as 3D tiles or a list. List rows show cover, title,
    author, progress and size, with a menu; swipe a row left for Rename and Delete, right for Shelves and Locate.
  - Books can be renamed (the library title; the file is unchanged).
  - 3D book spines are painted in the cover's dominant colour, with the title in a contrasting ink.
  - Shelf rows snap to a book when swiped.
  - The unfolded rail spreads its tabs over its full height.
  - Toggle on-colours swapped between dark and light mode.
- Phase 1 milestone 5, reader slice:
  - The mock's glass bars: Back, title, Contents, Go to page, "Aa" and the ribbon button on top; page, scrubber and
    percent below. They hide after 10 seconds, as before.
  - Bookmarks (Room schema v4, `bookmarks`): the ribbon button opens a strip of the app's five colours to put a
    ribbon on the page or recolour it, and a remove button. The ribbon hangs from the page's top edge and turns with
    it.
  - Page colours in "Aa": Auto, Paper, Sepia, Night and Black. Text and line art are recoloured in PDFium (images
    keep their colours); the status bar icons follow the paper. Auto gives Night pages in dark mode and printed
    white in light mode. This replaces the Night pages switch.
  - Page turns in "Aa": Curl (default), Slide (pages slide like a carousel) and Scroll (one continuous column, single
    pages).
  - Edge bounce: turning past the first or last page nudges the page and springs it back.
  - Reader memory on the unfolded Fold, spread, Night paper, 300 turns: 263 MB, growth 2.3%, 2.25% janky frames.
- Phase 1 milestone 5, online lookup slice (ADR-0017):
  - Book details from Open Library: by ISBN, else by title and author, preferring an edition in the phone's language;
    a description in another language is left out. Only the ISBN, or the title and author, are sent.
  - Off by default. After the first import FlowFolio asks once; Settings > Library > "Look up book details online"
    changes it. When on, each imported book is looked up in the background.
  - Found details fill only empty fields, never what the reader typed. Edit details has "Look up online" to run it on
    demand and review before saving.
  - Built on Android's own HTTP and JSON; no new dependency, no key.
- Fixes after annotations (Room schema v7):
  - Closing a book: screens no longer replay their entrance when you come back, so the cover lands on a still shelf.
  - Selecting text works in every turn style (Curl, Slide, Scroll) and zoomed in: pages report where they are on
    screen, and a press near the text finds the nearest word.
  - Translate in the selection bar: Google Translate in a glass panel over the book (never leaves the app; ADR-0017).
    Adds the INTERNET permission.
  - Page numbers follow the book's own numbering: the PDF's page labels, or else the numbers printed in its margins
    (found once and kept as `books.page_labels`), including roman front matter and prefixed appendices; books with no
    numbering count from the cover as page 1. The library, details, bookmarks and the reader all use them.
- Phase 2 milestone 3, typography (in progress; `docs/plans/phase2-m3.md`, ADR-0027):
  - Seven reading fonts bundled for EPUBs (Literata, the default; Charter; Atkinson Hyperlegible Next; EB Garamond;
    Source Sans 3; Lexend; Andika), with real bold and italic faces; about 7.7 MB.
  - "Aa" gains a Text section for EPUBs: font (each shown in its own face), size, line spacing, margins, justification
    and hyphenation. A change lays the book out again at the reader's place.
  - Line spacing is an exact line height, the same in every font (it was a multiple of each font's own line height).
  - Descenders on a paragraph's last line are no longer cut.
  - The "Aa" sheet is reorganised into tabs (Text for EPUBs, Page, Controls) with glyphs, at a fixed 46% of the screen
    so the page stays in view (owner, 2026-10-21): compact rows, line spacing and margins as small pictures, on/off
    settings as chips, each sound's switch and volume on one line.
  - Font cards are all one size, each showing "Ag" in the font itself with its name in the app's face.
  - Maths inside EPUB tables and figures is drawn in the paper's ink: on Night and Black it was black on black, inside a
    light frame (the rule that keeps stand-alone equations black for tinting also caught nested maths). Island cache
    version 2, so stored tables are drawn again.
  - Pinching an EPUB page changes the text size (the size shows while pinching; letting go sets it). PDFs still zoom.
  - Re-layout is fast on big books: the chapters up to the reader's are laid out first (every page number up to the
    reader's is exact, so spreads keep their pairing), the rest in the background with page images kept as their numbers
    settle. The island cache trim (0.8 s on a big textbook) moved after the layout. Linear Algebra (1,132 pages, at
    53%) on the Fold, non-debuggable build: 206-250 ms to the reader's page, from 352-372 ms (1.4 s with the trim).
  - An hourglass (sand runs down, the glass turns over) shows over the pages while a layout runs and until the new
    pages are drawn, instead of half-drawn pages; it waits 120 ms before showing and stays at least 500 ms.
- App themes (ADR-0028, owner 2026-10-22): Settings > Appearance > Theme offers FlowFolio (the mock), Rustic and
  Material You, as three small previews drawn in each theme; Light or dark applies to all three, and the change runs
  through the circular reveal.
  - Rustic: dark is the shelf wood itself (#48120c, deeper walnut), cream text (#f6e4ca), honey-brass accents and
    candle-light glows; light is parchment with walnut ink. Both lay a wood grain behind the content.
  - Material You: a Material 3 scheme mapped onto the palette (flat tonal cards, no glows or grain), from the
    wallpaper (Android 12 and later) or one of six seeds (blue, teal, green, amber, rose, violet) generated in the app.
  - The reader's bars and panels follow the theme. New Rustic paper (#48120c, cream ink); with the Rustic theme,
    Auto paper is Rustic in dark mode and Sepia in light mode.
  - Rustic is plain colours: the wood grain behind its screens is gone (owner, ADR-0029).
- Settings > Text (M3): the EPUB text settings outside the reader too: the seven fonts as the same cards as the "Aa"
  sheet (`FlowFolioFontPicker`, now in `:core:designsystem`), a sample paragraph set in the chosen font, size,
  spacing, margins and alignment, a size stepper, line spacing, margins, justify and hyphenation.
- The book's own fonts (M3, ADR-0030): "Book's font", first among the font cards, sets the body text in the family
  the book's CSS gives its body (`@font-face`), with its bold and italic faces; Literata when the book ships none (the
  reader's card then says "Book's (none)"). Fonts are read from the book only, de-obfuscated (IDPF, Adobe), WOFF 1 is
  unwrapped, and only real TrueType/OpenType files up to 16 MB are loaded; WOFF 2 is not read. Checked on the Fold
  with the IDPF *Moby-Dick* (STIX, four faces).
- Owner's batch, 2026-10-22:
  - Rustic keeps the original design's toggle colours; ribbons keep the original colours in every theme.
  - Book shadows are a soft, wide diffuse shade instead of a solid dark drop (library, details, shelves).
  - Font cards: every "Ag" on one baseline and fully shown, at the same size.
  - Riffle arrows twice as large and level with the page block; the reader's top-bar buttons twice as large unfolded
    (84 dp; 56 dp on the cover screen).
  - Page sounds on the media stream with a pool that rebuilds itself, and the volume keys set them in the reader
    (ADR-0032), after flips once went silent until the phone restarted.
  - Shelves have a colour, chosen when made (the theme's or Material You's six; Room v11 `shelves.color`): the shelf
    card is outlined in it and its library row (title and plank edge) takes it; "Not on a shelf" keeps the theme's.
    Shelf cards fan up to five books; tapping one opens the shelf's own page.
  - Continue Reading has shortcuts to the book's annotations, its bookmarks and its shelf.
  - Annotations show the chapter (the deepest contents entry) they are in.
  - Select mode with an iOS-style jiggle in Library, Shelves, Bookmarks (and a book's bookmarks), Annotations (and a
    book's annotations): choose items, Select all, and remove them together after a confirmation.
  - Follow-up (owner, 2026-10-22): the reader's buttons 56 dp unfolded (50 dp folded) and the riffle arrows 42 dp,
    instead of doubled; a quicker jiggle, none on the Bookmarks and Annotations pages (they still select); select mode
    on a shelf's page (tiles and list); Continue Reading's shortcuts at the right.
  - From the owner's marked-up cover-screen screenshots: lists never jiggle (only tiles and cards); shelf-page tile
    titles in the display face, bold and centred; on a narrow screen the library header keeps "Library" on one line,
    the count on two lines and the buttons under it; buttons and chips off glass carry a faint accent ring; a shelf's
    "N books ›" is a bold shaded pill; the tab bar's tabs share its width and their labels shrink to fit one line.
- Entrances and edge fades (ADR-0034, owner 2026-10-23): on every visit to a screen or page its elements fall into
  place one after another (fading in while settling from 112%), like icons after unlocking a phone; the library's books
  arrive one by one, shelf by shelf, and the details page settles around the cover flying in. A screen a cover is
  flying home to stays still. Tabs zoom through (the old one recedes, the new one settles from slightly larger). In the
  reader the bars' buttons fall in each time the bars show, and the bars fade as they slide. Soft, blurred fades in the
  theme's colour at the top and bottom of the screen keep content clear of the status bar, the camera and the
  navigation bar. Shelf-page tiles fill their cells (titles centred under their books) and stand further apart.
- Motion (ADR-0033, owner 2026-10-23): fade through between the tabs; shared axis X into and out of pages (a shelf,
  a book's bookmarks or annotations, edit details), scrubbed by the predictive back gesture; a plain fade around the
  details page and the reader, whose cover flight and book opening carry the motion. Items in lists and grids glide
  into place and fade as they come and go; the header's buttons cross-fade into the selection bar; selection checks pop
  in; the details tabs slide their content in from the side they lie on. Shelf-page titles sit below the book's shadow.
- Islands per layout (M3, ADR-0031): after a text size change, equations already drawn at the nearest other size stand
  in at once, scaled (their sizes lay the pages out, their images show), until this size's are measured and drawn.
- Memory and speed (M3, ADR-0031; results in `docs/plans/phase2-m3-memory.md`): the island WebView is created only for
  books with equations, SVG or complex tables; island markup is kept packed after the book opens; island images in
  memory are capped at 16 MB (was 32); EPUB pages are drawn into one reused 16-bit bitmap instead of a new 32-bit one
  per page; covers are decoded once for every screen and kept on the GPU only.
- The riffle scrubber's one-page arrows sit at the two ends of the strip and are always there with the dock; they no
  longer appear beside the ribbon after a release, where they got in the way of dragging it on (owner, 2026-10-22).
- Book details redesigned to look less like a page of buttons: the pills became one quiet line (format, pages,
  size); "Add to shelf" is an outlined second button with its label; tabs are words on a hairline with a sliding
  underline (`FlowFolioUnderlineTabs`); Overview shows the percentage over a slim bar and three figures split by
  hairlines instead of three cards; Details groups the eleven boxes into two lists (Book, File) of label and value in
  one card each. The blurred cover behind the hero is veiled in the theme's background so text over it stays legible.
- Phase 2, EPUB links and footnote popovers (the M2 follow-up, `docs/plans/phase2-m2-links.md`):
  - Links in EPUB text are tappable: a link into the book jumps to its page, a web link asks before opening (http,
    https and mailto only), a link to anything outside the reading order does nothing.
  - A footnote or endnote reference opens the note in a glass popover next to it (with its bold and italics; long
    notes scroll); a tap outside, Back or a page turn closes it.
  - Notes are recognised by `epub:type` or ARIA role: footnotes leave the flow as before; endnotes, rear notes and
    notes now stay in their notes section and open in the popover too (`rearnote` and `note` were not recognised).
  - `:core:reflow` finds each link's boxes on a page (`linkAreas`); PDF and EPUB links share one model in the reader.
  - A printed contents page in the reading order (`nav epub:type="toc"`) now shows, with links into the book; it was
    dropped with every other `nav`. Page lists and landmarks stay hidden. Checked on the Fold: notes, web links (a link
    wrapping over two lines) and contents links.
- Phase 2 milestone M2b, the riffle scrubber (ADR-0022, ADR-0023, in progress):
  - R1: the `:feature:reader:riffle` module's logic, tested on the JVM: positions on the fore edge for single pages,
    spreads (cover alone or not) and right-to-left books; the gap around the pin; leaf thinning; fine scrubbing (half,
    quarter and a tenth of the speed, relative so the ribbon never jumps); flag stacking, hit areas and tap
    cancelling; the auto-hide timer; the riffle state machine; the landing plan; loupe flips; haptic and sound rules;
    chapter bleed bands; and the mock's sample book for the demo screen.
  - R2: the fore-edge strip drawn in one canvas as in the mock (leaves in 18 shades with spring physics and bow,
    the gap and needle, chapter bleed bands, cloth cover boards in the book's colours, flags and stacks, the satin
    ribbon and pin), the dock header and countdown line, and a demo screen (`--ez riffleDemo true`). The frame loop
    runs only while something moves; drawing allocates nothing. On the Fold the strip draws in 0.5–1.3 ms on average
    (2.9 ms at worst) while riffling, against the 4 ms target.
  - R3: gestures (only the ribbon riffles, with fine scrubbing by height; flags are tap-only; a flag travels, a stack
    opens a popover; the block wiggles the ribbon; every touch is consumed), haptics per ADR-0023, the loupe (mini
    spreads from thumbnails or skeletons, flips over its spine, the fine-scrub speed), and generated paper sounds
    (16 KB, silent in silent and vibrate modes; replaced in R5 by the owner's recordings). Screenshot tests with Roborazzi
    (ADR-0024).
  - R4 (in the module and the demo): the landing turn (up to seven staggered sheets, 520 ms each, or one 700 ms
    turn; a crossfade when the system's animations are off), Go to page by printed number (a number keyboard unless
    the book has lettered page numbers), nudge arrows for 3 s after a release, and the auto-hide timer with its
    countdown line (holds while dragging, in a popover, in the field or while the arrows show).
  - R5: the scrubber in the real reader, replacing the slider:
    - The bar sits on glass, with the book's cover colours on its boards and bookmarks as flags.
    - Loupe thumbnails are drawn by the book's own page source (16-bit, 16 MB cache).
    - The landing turn plays over the pages in Curl; Slide and Scroll jump.
    - The chrome follows the riffle's timer: 3, 5 (default), 8 or 15 s, or Never, set in "Aa" and in Settings.
      An open panel holds it, and the system bars hide with it.
    - "Paper sounds" and "Riffle haptics" settings.
    - EPUB margins as before R5: R5 made them taller, which left wasted space on the screen, so they were put back
      (owner, 2026-10-21). The bars may cover a line while they show, as on PDFs.
    - The strip is a slider for screen readers, with actions for next and previous spread and chapter, Go to page
      and each bookmark. Arrow keys and Page Up and Down move along it; Enter shows or hides the chrome.
  - Owner's tweaks (2026-10-21):
    - No floor shadow under the strip.
    - A red pin rises from the ribbon into the pages, in place of the pin head.
    - The dock header reads "Page 306 – 307 of 842 — 36%".
    - Looser riffle physics: fewer, thinner leaves, a wider gap and softer springs.
    - The owner's recorded paper sounds (ADR-0025, `tools/sounds/prepare_recorded_sounds.py`) replace the generated
      ones.
    - Every Curl and Slide page turn plays the page flip as it begins, not only landings (ADR-0026; a turn callback in
      the vendored pagecurl).
    - "Page turn sound" and "Riffle sound" each have a switch and a volume slider, in "Aa" and in Settings.
    - Settings' "Hide controls after" row puts its five choices under its label.
- Phase 2 milestone 2, EPUB reading (ADR-0019, Room schema v10, in progress):
  - `:core:reflow`: chapters parsed into text, image and island blocks; pages cut natively with StaticLayout
    (justified, hyphenated, widows, orphans and headings kept with their text); positions saved as chapter and
    character offset (`books.last_locator`), so a new size, fold state or layout keeps the reader on the same text.
  - The reader opens EPUBs with the same curl, spreads, contents, Go to page and papers as PDFs; headings in
    Fraunces.
  - Islands: MathML, SVG and complex tables are laid out by a hidden WebView (book files only, no network), one
    chapter at a time starting with the one being read, and drawn into the pages in the paper's ink on a
    transparent background. MathML's `mfenced` is rewritten for MathML Core, and brackets around matrices are drawn
    as borders, since phone fonts cannot stretch them.
  - The `startPage` debug extra now opens EPUBs at that page (saving a page position clears the locator).
  - Fixes from the device checks: the reader no longer shows stale pages after a chapter is laid out again; wide
    equations scale down to the column instead of being cut off; the first island measure after a cold start no
    longer hangs (and is retried if it does); one printed page across a spread reads "1", not "1–1".
  - Island disk cache (ADR-0021): each book's equations, SVG and tables are drawn once, in the background while the
    reader is still, into the app's cache (sizes and images, 200 MB cap); page turns load small images instead of
    waiting for the WebView, and a cached book opens laid out at once. Linear Algebra: 401 MB and 4.6% janky frames
    with the cache warm, against 545–660 MB and 19% before.
- Phase 2 milestone 1, EPUB in the library (ADR-0020, Room schema v9):
  - Books without a cover image show the drawn title cover in list rows and small covers too (`FlatCover`), as on
    the shelf.
  - `:core:epub`: our own EPUB 2/3 reader: container, package document (metadata, manifest, spine, reading direction,
    fixed layout), the EPUB 3 nav document or EPUB 2 NCX (contents, page list, landmarks) and the cover. Hostile books
    are refused or contained: paths stay inside the book, entries are capped by inflated size, DOCTYPE processing is
    off, and DRM is detected (font obfuscation allowed).
  - Import recognises EPUBs by content, keeps them as `<id>.epub`, writes their cover and fills author, language,
    publisher, description, ISBN, date and reading direction from the book. "Open with", the share sheet, the file
    picker and Locate file accept EPUB.
  - Library and details show EPUB books (format pill, printed pages from the page list when there is one, the book's own
    contents in the Contents tab). Reading
    them comes in milestone 2; opening one says so.
  - New import messages: not a PDF or EPUB, and DRM-protected EPUB.
- Phase 1 close-out (`docs/plans/phase1-signoff.md`):
  - Baseline profile (startup and page turns) shipped with `profileinstaller`; `:benchmark` module with startup and
    page-turn macrobenchmarks and the profile generator. Release build, unfolded Fold: turn frames 2.6-2.9 ms CPU P50,
    5-10 ms P99; cold start to the library 0.20-0.24 s. The profile made no measurable difference on the Fold.
  - Reader memory re-measured: 282 MB after 300 spread turns (ceiling 292 MB), growth 6.9%.
  - `measure-reader.ps1` stops if FlowFolio is not in front and keeps the screen on.
- Reader fixes and on-device translation (ADR-0018, Room schema v8):
  - Turning back no longer stalls: the vendored pagecurl keyed every page on the moving edge, so all pages were
    rebuilt each frame; it now keys on the current page and reads the edge while drawing.
  - The left page of a spread follows the finger when dragged right, like a grab on the right page, and finishes or
    lays back the sheet on release.
  - Highlights cover the full line height (PDFium's loose character boxes), and the lines of a paragraph meet so a
    highlight is one block.
  - A magnifier above the finger while selecting; selection state is read while drawing, so a drag no longer
    recomposes the reader. Page text is prepared for the pages in view, so a press selects at once.
  - Highlights made while zoomed in show straight away.
  - Turns no longer hitch when they land: each curl page keeps its own key, so a turn reorders the pages instead
    of rebuilding them; new page images are converted to 16 bits and uploaded off the main thread; the page under the
    current one is not drawn while it is hidden. Unfolded Fold, spread, release-like build (not debuggable): turns
    2-4% janky frames, p99 12-18 ms; slow drags 1-2%; selection drag p95 7 ms.
  - The left page of a spread follows the curl: while a sheet is lifted back, the previous spread's left page shows
    underneath (it used to keep the current one), and after a forward turn it changes in the same frame the sheet
    lands instead of a moment later. When a forward sheet lands, the next left page shows in that same frame (the old
    one flickered through for a frame before).
  - Translate runs on the phone with Google ML Kit, in our own glass panel: detected source language, From and To
    menus with swap, Copy and Share, and a history of recent translations (`translations` table) with Clear.
    Language models download once (about 30 MB each); the passage never leaves the phone. Replaces the Google
    Translate web view. Adds about 17 MB to the install on arm64.
- Annotations and right-to-left books (ADR-0016, Room schema v6):
  - Highlights: press and hold a word, drag to select more, adjust with the handles, then pick one of five
    highlighter colours. Highlights are drawn on the page (multiplied into light paper, a soft wash on dark paper) and
    turn with it. Tap a highlight to recolour it, add or edit its note, share it, or delete it.
  - Notes on highlights; Share sends the passage in quotation marks with the book's title, author and page; Copy puts
    the same on the clipboard.
  - The page text comes from PDFium's text layer over our JNI bridge (`PdfDocument.pageText`); highlights are stored
    in FlowFolio's database, never written into the PDF.
  - An Annotations tab (books with highlights, with counts, colours and the latest passage) and a page per book
    (open at the passage, swipe to share, edit the note or delete), plus an Annotations panel in the reader's top bar.
  - Reading direction per book in "Aa": right to left mirrors the book (the curl lifts from the left, a spread starts
    on the right, the scrubber runs right to left) while pages read normally.
  - The reader's ribbon panel lists the book's ribbons under the colours; tap one to go there.
  - Shelf pages: swipe a row right (or press and hold a tile) for Bookmarks, the book's own ribbons page.
  - Library to details: one 3D book flies on a single smooth path while the library fades; the library no longer
    shifts left as the rail slides away. Back flies it home.
  - Closing a book: the flat cover crossfades into the shelf book as it lands, with no snap.
- Phase 1 milestone 5, data slice (Room schema v5):
  - Editable book details: subtitle, edition, publisher, ISBN, description, language, release date, the author's role
    and bio, and the book's type (Textbook, Novel, Non-fiction, Other). Edit details is in the details page's menu;
    blank fields read as "Not known yet".
  - Details page as in the mock: subtitle and edition under the title, a description on Overview, the full Details
    grid, and an Author tab (initials, role, bio, the author's other books in the library).
  - Time read: the reader records reading sessions (`reading_sessions`) while it is in front and in use; five minutes
    without a page turn ends a session, and glances under ten seconds are not kept. Overview shows time read and
    ribbons.
  - Library chips Textbooks and Novels appear once a book has that type.
- Owner tweaks after the Bookmarks and Settings slice:
  - On Night and Black pages the Contents, Go to page and "Aa" panels use a nearly opaque glass, so their text stays
    readable over bright photos on the page.
  - Unfolded, the book's title sits beside Back at the top left.
  - Back (button or gesture) closes the book: the opening plays in reverse and the book flies back to where it was
    opened from.
  - The rail's tabs sit closer together in the middle of the rail.
  - Unfolded, bookmark cards in both columns have the same height.
- Phase 1 milestone 5, Bookmarks and Settings slice:
  - Bookmarks tab: every ribbon in the library as a card with its colour tag, the book's cover, the printed page and
    the chapter it is in (from the PDF's outline), newest first; colour filter chips; tap to open the book there with
    the book opening; swipe right to recolour, left to remove. Two columns unfolded.
  - Settings grouped as in the mock: Appearance (theme, Neon glow, page turn), Reading (two pages side by side, cover
    on its own), Library (import storage, ask every time) and Conversion ("On this device"). Two columns unfolded.
    The mock's reflow and reminder settings stay hidden until those features exist.
  - Neon glow (setting `neon_glow`, on by default) turns the text and icon glows off.
- Owner tweaks after the reader slice:
  - The reader's bars and panels take the light or dark palette of the page colour, so icons, the page number and
    the percentage read well on every paper; over dark papers the glass is a lighter frosted tint with a pale edge.
  - Contents, Go to page, the ribbon colours and the "Aa" sheet are glass panels drawn in the reader itself, so they
    blur the page behind (sheets and dialogs are separate windows the glass cannot see through). Contents opens under
    the top bar and stops above the bottom bar. Back or a tap outside closes them.
  - Ribbon colours: green replaces grey (a stored grey ribbon reads as green). The ribbon is flat and solid like the
    mock, drops in on a spring and pokes 6 dp above the page.
  - Changing the page colour is revealed in a circle from the touch, like the theme switch; the circle starts once the
    pages are drawn in the new colour.
  - Slide turns pages like a stack: the top page lifts off to the left over the next one.
  - Book opening (Read on the details page and Resume in the library): the cover flies to the page it opens on (the
    right-hand page of a spread), the paper floods in, then the cover swings open on its spine and the page melts in.
  - Unfolded, the details page's blurred cover spans the top half of the screen.

### Changed
- Shadows (cards, chips, buttons, glass, book contact shadows, the plank glow) are rendered once on the CPU into small
  alpha bitmaps and reused (`ShadowBitmaps`). As GPU blurs, each kept its own render target: the slice 3 library used
  525 MB on the unfolded Fold; now about 365 MB. Reader, spread, 300 turns: 263 MB, growth 0.7%, 1.9% janky frames.
- `tools/measure-reader.ps1` takes `-StartPage` (default 0) and sets the reading position before turning (debug-only
  `startPage` extra), since the position now persists between runs.
- FlowFolio design language (ADR-0014): the owner's dark and light palettes (pixel-exact), glow in dark mode and
  soft shadow in light mode, rounded surfaces, own toggle and outline glyphs, themed dialogs and sheets; library,
  settings and the reader's sheets use it.
- Night pages (reader setting, dark mode only): text and line art light on dark, images keep their printed colours.
  Replaced by the page colours of the milestone 5 reader slice.
- Touch zones: outer 30% on each side turn pages (tap or swipe), the middle 40% is for taps (controls).
- `ReaderZoomOnDeviceTest` (UiAutomator 2.4.0) for pinch gestures on a device; Compose test tags are exposed as
  resource ids.

### Fixed
- Right-page turns finished on a fast default spring; they now finish at the pace of a full turn, like the left page.
- Taps on the page curl stopped turning pages after the first turn (the curl state was recreated on every turn).
- Reader memory budget (ADR-0013): 16-bit GPU-backed page images and gradient fold shading instead of the blurred
  curl shadow; `tools/measure-reader.ps1` now measures the app floor, samples retained memory and reports PASS/FAIL.
- Reader memory leaks found on the reference Fold: evicted GPU-backed pages are now recycled promptly, and PDFium's
  per-document caches are bounded by reloading the document every 100 renders (+12 MB/100 pages before).
- Phase 1 milestone 2: library and import. Modules `:core:database` (Room), `:core:datastore` (settings),
  `:core:data` (repositories), `:core:designsystem`, `:feature:library`. Import from the file picker and
  "Open with"/share; storage setting with the approved disclaimers (copy by default, or read in place); SHA-256 book
  ids, duplicate detection, metadata titles, JPEG covers, missing-file detection and "Locate file", delete, sort,
  settings screen (storage, ask every time, theme). The reader screen is a stub until milestone 3.
- Phase 1 milestone 1: `:core:model` (page sizing, outline) and `:core:pdf` with our PDFium JNI bridge (ADR-0010):
  file-descriptor loading for app and Storage Access Framework files, typed errors, password support, tile rendering,
  outline and page labels, armeabi-v7a/arm64-v8a/x86_64.
- Robustness test PDFs generated by `tools/testpdfs/make_test_pdfs.py` (labels, rotation, extreme sizes, zero pages,
  RC4 encryption, broken xref, non-PDF) plus damaged variants; on-device tests on the reference Fold.
- ADR-0009 (on-device conversion), ADR-0010 (PDFium), ADR-0011 (vendored pagecurl), ADR-0012 (module structure);
  PDF renderer spike report.
- Phase 0 foundation: monorepo layout, MIT license, `CLAUDE.md`, architecture overview,
  ADRs 0001–0008, dependency license matrix, conversion output contract draft (spec v0.1.0).
- Android skeleton: single-activity Compose + Material 3 app with Hilt, empty Library screen,
  convention plugins, Spotless/ktlint, detekt, unit tests, Robolectric Compose tests, and an
  instrumented launch test.
- Converter skeleton: FastAPI service with `/healthz` and `/version`, `flowfolio-convert` CLI,
  env-based settings, tests, multi-stage non-root Docker image, dev compose file.
- CI: path-aware GitHub Actions workflow (Android, converter, Docker smoke test, Conventional
  Commits check, `ci-gate`), Dependabot, pre-commit hooks.
- Golden corpus manifest schema (no books yet).

### Fixed
- `gradlew` committed without the executable bit, breaking the Linux CI build.
- CI commit-subject check rejected every commit (anchored regex used mid-pattern).

### Changed
- The Python converter is no longer the product path (ADR-0009); conversion moves on-device in Kotlin.
- Dependabot no longer proposes Python minor/major bumps for the converter image (pinned by ADR-0004).
