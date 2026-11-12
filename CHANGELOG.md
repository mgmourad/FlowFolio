# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning: [SemVer](https://semver.org/).

## [Unreleased]

## [0.7.0-beta.3] - 2026-11-12

### For readers
- **Clearer reading tools.** While you read, the bars and panels are dark on every paper, so their text stands out on
  light and sepia pages too.
- **Swipe to move between tabs** wherever there are tabs: book details, the Notebook, Words, Contents and Aa.
- **Pull a reader panel down to close it.** That works for Aa, Search, Contents, Narrator, Words and the riffle bar,
  and is easiest with the phone folded.
- **The keyboard's Search key** runs the search and puts the keyboard away, in every search box.
- **Before a scanned book is converted,** FlowFolio says what to expect: the text may not be exactly right, how long
  it takes, and that you can keep reading meanwhile. You can turn this notice off.
- **A progress bar on the book's cover** while its Dynamic Flow converts.
- **Quick books no longer wait** behind a long scanned book: the scan pauses, the quick book converts, then the scan
  carries on from where it stopped. A scan stopped part-way also resumes instead of starting again.
- Shelf cards are as tall as their covers, and settings no longer break a label mid-word on the folded screen.
- Night glow is gone: it no longer changed anything.

### Tightening milestone TM1: the reading surface and conversion (2026-11-12)
- **Plan:** `docs/plans/tightening-plan.md` (approved 2026-11-12).
- **Dark reader chrome (ADR-0056).**
  - The bars, dock, riffle, panels and selection menus use the dark palette (`ReaderChromePalette`) with a pale
    hairline edge (`readerChromeGlass`). The selection handles keep following the paper.
  - The dark third ink is lifted 70% toward the second inside the chrome only. Measured on `fold8_api37`, secondary
    text went from 2.8:1 to 4.7:1 on light, 4.9:1 on sepia and 6.4:1 on black paper (labels 10.3–14.1:1).
- **Gestures.**
  - `swipeTabs` lives in the design system and is used on book details, the Notebook's book page, Words, Contents
    and Aa.
  - `dragDownToDismiss` is on every `DockPanel` and the riffle card. A list inside scrolls first through nested
    scroll; a scroll back up retracts the panel before the list moves.
- **Conversion.**
  - `FlowService` runs a priority thread. While an OCR book is reading pages, a newly asked-for book converts there,
    and the OCR book gives the engine lock away at its next page (`FlowPause.WAITING`). Only one conversion works at
    a time.
  - `FileOcrCache` keeps each scanned page's OCR, stamped with the engine version, beside the bundle, and removes it
    once the book converts. A paused, stopped or killed conversion reads only the pages it has not read
    (`OcrResumeTest`, `FileOcrCacheTest`).
  - Verified on the emulator: Think Python, asked for second, finished in 62 s while the Arabic sample paused at 12%,
    and the Arabic sample then finished.
- **The scan notice.**
  - `DynamicFlow.convert` checks a never-converted book's first four pages and emits `FlowNotice.ScanAsk` instead of
    converting when most carry no text. `answerScan` converts, or puts the book back on its PDF; "Don't show this
    again" is `UserSettings.scanNotice`.
  - The estimate uses 6 s a page, from the Fold measurement below.
- **OCR speed measured on the Fold** (release build 0.7.0-beta.2, 50-page scans): 4.4 s a page for English and 7.0 s
  for Arabic, 2–3.5 times the plan's 2 s target (known issue 55).
- **Cover progress:** `conversionBar` reads `LocalConversionProgress`, which `AppViewModel` provides from Dynamic
  Flow's statuses.
- **Updates:** `Updates.pickApk` prefers the APK for the phone's processor, then the universal one, ahead of TM2's
  per-processor releases (`PickApkTest`).
- **Quick fixes:**
  - Shelf cards size their cover fan from the covers, and the Dynamic Flow toggle sits beside the stats.
  - A settings choice row stacks its options under the label when the label would get less than 140 dp.
  - `rememberSearchKeyboard` puts the Search key on every search field.
  - Night glow (`UserSettings.neonGlow`) is removed.

## [0.7.0-beta.2] - 2026-11-12

### For readers
- **Arabic scanned books now become text.** Most pages of a typical printed Arabic book now reflow; before, about
  half stayed pictures. Books you already converted are converted again the next time you open them.
- **Fewer broken words** in some PDFs: words like "necessary" no longer split in two.
- **Bullet lists come through as lists** in books whose bullets used to show up as empty lines.
- **What's new is shorter:** it now shows the changes you will notice, not the developers' notes.
- The Scanned books setting now says it reads Arabic, which it does.

### Phase 7 M4: verifying M3, the Arabic gate, and the open issues (2026-11-12)
- **Verifying M3 found that no Arabic book worked** (owner's report, confirmed on his 1960s Egyptian book).
  - Tesseract read the pages well; PaddleOCR, the checker, garbled a letter in most words.
  - Whole-word agreement therefore fell to 0.17–0.57, and pages Tesseract had read well stayed pictures.
  - **ADR-0055** (proposed) measures agreement by letter pairs instead (Dice over in-word bigrams), with the bar at
    0.66. On the M3 corpus it keeps exactly the pages it kept before and rejects every misread page, manuscripts
    included; on the owner's book it keeps 10 of 11 body pages where 6 were kept before.
  - PaddleOCR now checks Tesseract on the lines already detected, rather than detecting the page a second time.
  - Engine **1.5.3**: books convert again on their next opening.
- **CI's OCR gate had never run.**
  - The corpus job passed the OCR paths relative to the wrong folder, so `OcrCorpusTest` skipped all three tests
    while the job stayed green. It also read Ubuntu's smaller `tesseract-ocr-ara`, not the phone's `tessdata_best`.
  - Now: absolute paths; ground truth read from `samples/golden/ocr/`; the phone's four models fetched from the
    mirror and checked against `OcrPack`'s hashes; and a set-but-missing folder fails the test instead of skipping it.
  - Baselines tightened to just under the measurements (Arabic recall 0.88, kept 0.35; English kept 0.95, CER 0.05).
- **Known issue 51 fixed** (*Learning DevOps*):
  - "necessar y", "repositor y", "encr ypt": that PDF sets its fonts at size 1 (known issue 46), so the word-space
    rule allowed a quarter of a point and kerning read as a space. The em now comes from the glyph box when the
    reported size is less than a third of it.
  - Its square bullets are U+0082, a control code, and came out as empty paragraphs; the C1 range now counts as a
    bullet.
  - "repositor" and "Packer CL" without their last letter are in the PDF itself: PDFium and Poppler both read them
    that way, and the page prints them so.
- **Known issue 44 is no longer reproducible.** The Python book's cover now converts as one whole-page picture
  (Phase 4's full-page covers).
- **Known issue 45 did not reproduce.** On `fold8_api37` three taps at the right edge of a reflowed book turned
  three spreads; taps sent while a reflowed book is still opening are dropped, which fits the original report.
- **What's new:**
  - It shows a version's `### For readers` section on its own when there is one.
  - A build ahead of the changelog is labelled with its own version, not "Unreleased".
- **Speed is not yet known on the Fold** (see CLAUDE.md, known issue 55). On `fold8_api37` one Arabic page takes
  about a minute in the release build. The time is in native code (Tesseract 21 s, PaddleOCR detection 16 s), which
  is 50 times slower there than on a Mac, while plain CPU work is only 1.3 times slower: the emulator's memory
  handling, not the code, is the likely cause.

## [0.7.0-beta.1] - 2026-11-12

### Phase 7 M3: the scanned-books quality gate (2026-11-12)
- **A scanned corpus with proofread ground truth.** 41 public-domain pages (21 English, 20 Arabic) from Wikisource's
  proofread `Page:` namespace; the ground-truth text and the manifest (id, source, scan sha256) are committed to
  `samples/golden/ocr/`, the scans stay fetched (`tools/corpus/fetch_ocr_corpus.py`), never committed.
- **`OcrCorpusTest`** (in `:tools:convert`, the corpus harness) measures, per page, word recall and CER, the share of
  pages kept as text, and that the manuscripts and a blurred page stay pictures. It runs in the `corpus` CI job
  (which installs `libtesseract` and fetches the pages and models), checked against the committed baselines.
- **The gates hold as ADR-0054 set them.** English 98.9% recall / 3.0% CER, 20 of 21 pages kept (corrected 2026-11-12: not all 21) at the 0.85 confidence
  gate; Arabic printed 93.0% recall / 16.0% CER on the pages the two engines agreed on (4 of 10 kept at the 40% bar —
  the pre-1950 metal type stays pictures, the 9 manuscripts do too).
- **OCR is fast enough.** English recognition is about 0.35 s a page compiled (the debug build's 25–35 s was
  interpreted Kotlin); on the Fold that is about 0.7–1.4 s a page, inside the 2 s target. (Corrected 2026-11-12: this was
  estimated from the desktop JVM, not measured on the Fold.)

### Phase 7 M2: Arabic scanned books read by Tesseract (2026-11-11)
- **Tesseract reads Arabic scans.** Tesseract 5.5.4 and Leptonica 1.87.0 are built from source with the NDK for
  arm64-v8a, armeabi-v7a and x86_64 (pinned tarballs, SHA-256-checked, as PDFium and sherpa-onnx are). Google's
  `cpu_features` (`ndk_compat`, Apache-2.0) is a statically-linked build dependency Tesseract 5.5 needs on Android to
  detect NEON. Raw grayscale pixels go in, so no image codec library is built.
- **The two engines agree.** A scanned Arabic page is read by Tesseract and checked by PaddleOCR's Arabic model; the
  page becomes text only when their words agree at least 40% (`OcrGate`), and Tesseract's text is shown. Tesseract
  returns Arabic in logical order, so no reordering is applied (PaddleOCR's visual-order output still is).
- **The pack grows to 51 MB.** `ara.traineddata` (tessdata_best, 12.6 MB) joins the three PaddleOCR models in
  Settings → Dynamic Flow → Scanned books, wired through `TesseractOcr` in `:core:ocr`. The models are now mirrored
  on FlowFolio's releases (`ocr-models-v1`) with SHA-256s pinned, instead of ModelScope.
- **CI can read Arabic.** `tess4j` 5.21.0 (Apache-2.0) is added to `:tools:convert` tests only, and the Linux runner
  installs `libtesseract` and `tesseract-ocr-ara`.
- Verified on `fold8_api37`: a two-page Arabic scan converts with both pages read as `ocr:tesseract-ara` (word
  agreement 58% and 80%).
- **Arabic books open turning right to left.** A book whose strong letters read mostly right to left is written with
  `dir="rtl"` and `lang="ar"` on its chapters and package, and `page-progression-direction="rtl"` on its spine (the
  reader already turns right to left). Engine **1.5.2**, so books convert again.

### Arabic books, from the owner's own (2026-11-11)
- **Typed Arabic text reads in order.** The words of a born-digital Arabic page came out reversed. The cause was a
  PDF that gives its spaces a flat box on the baseline: the converter's same-line test saw no overlap at each space
  and cut the line there, and the cut words were then sorted left to right.
  - Now a space never starts a line by itself, and the test measures against the last letter.
  - A line whose first strong letter is Arabic or Hebrew treats only a jump to the *right* as the start of a new
    line.
  - Word spacing is measured on whichever side the next letter lies.
- **Right-to-left pages read right to left.** A page whose letters are mostly right to left reads its right column
  first, and items on one row right to left (`ReadingOrder`).
- **An Arabic scanned book no longer spends its time on pages that can only stay pictures.** Each book's scanned
  pages vote on its script (`BookScript`: 6 pages, 80% agreeing). Once a book is Arabic and no Arabic reader is
  installed, its remaining pages are kept as pictures without OCR. The owner's 663-page Arabic book took about 50
  minutes to come out almost all pictures. A page in the other script before the vote settles is still read: that
  book's English newspaper clipping on page 160 was read as English, which it is.
- **Big picture-heavy books no longer run the conversion out of memory.** Each progressive EPUB is now written
  straight to its file instead of being built as bytes and copied (`Converter.convertProgressively` hands over a
  writer). Before this, the Arabic book (a 67 MB EPUB, almost all pictures) failed on `fold8_api37` with
  `OutOfMemoryError` at 87%; the phone's larger heap had got it through.
- **The desktop converter runs on Apple Silicon Macs.** The host PDFium fetch knows `pdfium-mac-arm64` from the same
  release, so `:tools:convert:test` and the corpus run here (known issue 37). There is a new debug command, `convert
  chars <pdf> <index>`, which lists PDFium's characters with their boxes.

### Phase 7 M2: scanned books read by OCR (2026-11-11, English; Arabic pending)
- **Dynamic Flow reads scanned pages.** Settings → Dynamic Flow → **Scanned books** downloads a 38 MB pack (PaddleOCR
  PP-OCRv6 detection and recognition, plus PP-OCRv5 Arabic recognition), checked against pinned SHA-256s.
  - With the pack installed, each scanned page goes through PaddleOCR. A page it reads with a mean line confidence of
    at least 0.85 becomes text, laid out by the converter's usual stages: reading order, paragraphs, headings, and
    hyphens joined.
  - Any other page stays a picture, marked `fallback:ocr-low-confidence`.
  - The sidecar marks read blocks `ocr:<engine>@<confidence>` and counts the pages as scanned.
  - Engine **1.5.0**, so books convert again on their next opening.
- **Arabic pages stay pictures for now.** The English model was 99.8% sure of nonsense on a clean Arabic page, so a
  script check (both PaddleOCR recognisers read a few lines, and the surer one names the script) keeps Arabic away
  from it. ADR-0054's Arabic reading (Tesseract with PaddleOCR checking it) arrives with Tesseract.
- **Built in Kotlin** in `:core:conversion/ocr`, the way RapidOCR runs the models:
  - minimum-area rotated rectangles, so tilted scan lines come out straight;
  - CTC decoding;
  - the bidi reordering for Arabic.
- **Measured against the spike's 20 proofread English scans** (`PaddleOcrScansTest`, run when pointed at the pages):
  98.9% median word recall, at least 86% on every page. RapidOCR scored 98.7%.
- **Three traps found and handled:**
  - ONNX Runtime's Java API mangles the models' alphabets through JNI "modified UTF-8", so `OnnxMetadata` reads them
    from the file's bytes.
  - Its Android glue cannot load against sherpa-onnx's versioned runtime symbols (`VERS_1.28.2` against
    `1.28.0`), so `:core:voice` gained its own small C API bridge (`OrtBridge`, `ort_bridge.cpp`). The app still
    carries one runtime.
  - On the JVM, `ImageIO.getRGB` changes the gamma of grayscale scans.
- Verified on `fold8_api37`: the pack downloads, and an 8-page scanned sample (7 English pages from different books,
  1 Arabic) converts. The English pages became reflowed text at confidence 0.94–0.99, and the Arabic page stayed a
  picture.
  - Not yet good enough: about 25–35 s a page on the emulator's debug build. The Fold, in a release build, is the
    measurement that counts, and it has not been taken.

### Phase 7 M1: the OCR spike (2026-11-11)
- `docs/spikes/2026-11-ocr/` measures PaddleOCR's models (through RapidOCR) and Tesseract on 20 proofread English
  and 20 Arabic Wikisource scans. **ADR-0054 is proposed:** PaddleOCR models on the ONNX Runtime the app already
  ships.
  - English: PP-OCRv6 small reads 98.7% of words (median), with 86% on its worst page.
  - Arabic: PP-OCRv5 reads 74–91% on modern print, and fails on type from before about 1950, as does Tesseract.
  - A page-confidence gate of 0.85 kept every good page and dropped every bad page and manuscript, so those stay
    pictures.
  - **ADR-0054 accepted by the owner (2026-11-11), with Arabic read by both engines.** They check each other:
    a page becomes text only when their words agree (at least 40%), and Tesseract's text is used. Choosing by
    each engine's own confidence was simulated and rejected: it showed a manuscript as text.
  - Nothing in the app changed yet. Each dependency is confirmed when M2 adds it.

### Changed
- **"Install updates automatically"** replaces "Fetch updates automatically" (owner, 2026-11-11).
  - The app checks for a new build **every time it is opened or returned to**, at most once a minute. GitHub
    allows 60 unauthenticated checks an hour.
  - **Off (the default):** an in-app banner says a new build is available. Tapping it opens Settings → Updates,
    where **Install** and **What's new** sit side by side. Install fetches the build and hands it to Android.
  - **On:** the app fetches the build and installs it through a `PackageInstaller` session
    (`UpdateInstaller`). It asks Android to skip the confirmation, which Android 12+ allows when FlowFolio updates
    itself. The first time (a copy installed by a browser or adb), Android still asks once. After that, FlowFolio
    is its own installer and updates without asking.
  - After an update, the next opening shows a **"FlowFolio was updated"** banner together with the What's new popup.
  - Before this, the setting only downloaded the build, and Settings checked only while it was on. Installing
    always went through Android's prompt. The installs the owner saw on 2026-11-10 were made over adb during
    development, not by the app.
  - Verified on `fold8_api37` with a release build numbered 600003 against the published canary.4. Off: the
    banner, then Install and What's new in Settings, and nothing installed. On: the app fetched and installed
    canary.4 after Android's one-time prompt, and became its own installer of record. The banner and What's new
    were checked together with two debug builds. A fully silent second update needs a newer published release to
    observe.

## [0.6.0-canary.4] - 2026-11-10

> Narrator Mode grows up: it keeps reading in the background and with the phone locked, with the phone's own player
> and one of FlowFolio's own, and it no longer skips words it doesn't know. Verified locally and on `fold8_api37`;
> gated on green CI.

### Added
- **Narrator Mode keeps reading in the background and with the screen off** (owner, 2026-11-10; ADR-0053).
  `NarratorService` is a foreground service of type media playback, with a `MediaSession` and a wake lock held while
  the voice speaks.
  - **The phone's own player** (lock screen, quick settings, headsets, Bluetooth): previous, play or pause, next, and
    a speed button.
  - **FlowFolio's own persistent player** in the notifications: the title, previous, play or pause, next, a **speed
    chip** that steps through 0.8×, 1×, 1.25×, 1.5× and 2× and shows the rate, and close. Expanded, it shows the
    album art.
  - **The title is "FlowFolio: <Book Title> - <Author> - <Chapter>"**, following the chapter as the narrator reads.
  - **Album art:** the book's cover **fitted** inside a square, never stretched, on a softened copy of itself, with
    the app icon as a badge on its bottom right (`NarratorArt`). A book without a cover gets its title on paper.
  - **No seekbar**: the session never publishes a duration.
  - FlowFolio's player asks for Android 13's notification permission the first time narration starts, and appears as
    soon as it is granted.
- **Settings → Sound & voice → Skip/Reverse behavior**: Paragraph (the default), Page, Chapter or Subchapter. It
  sets how far previous and next move, in both players and on headsets (`NarratorSkips`).
  - Previous works like a music player's: partway into a paragraph, chapter or section it restarts that one, and
    from its start it goes to the one before.
  - Chapters are the outline's top level, subchapters any level. A book without an outline skips by page.
- Verified on `fold8_api37`:
  - both players appear, with the title, the art and its badge, the speed (1× to 1.25× from the chip, with the
    session's own speed following) and no seekbar;
  - skipping by paragraph and by chapter lands where the tests say, from FlowFolio's player and from the phone's
    media controls;
  - narration kept playing for 80 s with the screen off and the app in the background, wake lock held;
  - leaving the book stops the voice, the service and both notifications.
  - The emulator has no screen lock set, so the lock-screen player itself was not seen. It is drawn from the same
    session.

### Fixed
- **Narrator Mode no longer skips words it doesn't know** (owner's report, 2026-11-10: "Kubernetes", "Cybercrime",
  "CSA", "NIST").
  - The cause: sherpa-onnx 2.0.1's multi-language Kokoro looks every word up in its lexicon and **silently drops**
    any it cannot find (`OOV word skipped`). That happens even though the voice pack ships espeak-ng, and no setting
    or newer release changes it. In *Learning DevOps* that was 4.5% of all words, about one in 22.
  - `SpeechWords` (`:core:voice`) now rephrases every word the voice doesn't know into words it does, using the
    voice's own vocabulary (`Vocabulary`, packed into one string to keep memory low):
    - CamelCase is split ("GitHub", "PowerShell").
    - Acronyms are spelled ("CSA", "VMs", "APIs"); known ones of four letters or more are said as words ("NIST").
    - Possessives and contractions are read properly ("Docker's", "that's").
    - Compounds become known words ("Dockerfile", "webhook").
    - Letters and digits are said apart ("IPv6").
    - Anything left is sounded out from known pieces.
    - Headings in capitals are still read as words.
  - FlowFolio's own lexicon (`FlowFolioLexicon`), loaded last so it wins: about 50 technical names (Kubernetes,
    Ansible, Jenkins, YAML, JSON, nginx, kubectl…), plus the article "a" as /ə/. The pack lists only the letter
    name, so **every "a" was read as "ay"**.
  - Numbers, symbols and abbreviations are read for their meaning (`SpeechNumbers`):
    - years ("twenty twenty three"), thousands, decimals, versions ("two point zero point one"), ordinals;
    - $, %, & and + ("C plus plus"), ranges ("1990 to 2000"), and links as their address;
    - e.g., i.e., etc., Dr., Mr., Fig. and No. are expanded and no longer treated as the end of a sentence, and
      neither are initials ("J. R. R. Tolkien").
    - Before this, "$10,000" was read "ten, zero", and "2023", "1st" and "45%" were skipped.
  - The extra forms list (`lexicon-us-en-inflected-v4`) now covers stems in -ss and -us ("processes", "discussed",
    "focuses") and verbs' -ing forms ("automating"). It holds to those stems because sherpa-onnx keeps about
    500 bytes of native memory per entry. That adds 13 MB while narrating, against 32 MB for every stem ending in s.
  - Measured on *Learning DevOps* (103,000 words): **4,555 words skipped before, 0 now**. The real engine, fed
    the rephrased text, reported no skipped word across 34 sample sentences.
- **Narrator Mode takes audio focus** (`NarratorAudioFocus`). It used to talk over music, carry on through a phone
  call, and keep speaking aloud through the speaker when headphones were unplugged. Now music pauses for it, and
  it stops on a call, when another app starts playing, or when the headphones come out. Every way narration ends
  gives the focus back.
- **A word hyphenated across a page break is said once.** Continuous reading prepared each following page with the
  fragment the previous page had already finished ("informa-" / "tion"), so the fragment was said again or
  skipped. Starting on such a page now says the whole word (`SpeechText.startingOn`, `continuingOnto`).
- Verified on `fold8_api37`: the voice pack downloaded and installed in the app, the Kubernetes chapter of
  *Learning DevOps* was read with Kubernetes and links spoken, and audio focus was held while speaking and
  released on Pause.

## [0.6.0-canary.3] - 2026-11-10

> A fix release for a serious bug: Dynamic Flow could stay at "Preparing" for good when Android stopped the
> conversion process part-way. Also the tour's pictures retaken in the new design, and "Replay the tour" fixed.
> Verified locally and on `fold8_api37`; gated on green CI.

### Fixed
- **Dynamic Flow no longer sticks at "Preparing"** (owner's report, 2026-11-10).
  - The cause: Android can stop the conversion process (`:convert`) part-way, most often for memory. The owner's Fold
    records a LOW_MEMORY kill of it. When that happened, nothing told the app. The book stayed "Converting" in
    memory, and choosing Dynamic Flow again did nothing, because the book still read as converting.
  - The app now notices. `FlowWatch` gives up on a book only when it has heard nothing for 15 s *and* the conversion
    process is not running, so a queued book or an app waking from the background is never misjudged.
  - A lost book converts again by itself once. If it is lost a second time, the reader says "Stopped. Tap Dynamic
    Flow to retry." (the new `FlowFailure.INTERRUPTED`), and tapping does retry.
  - `FlowService` closes three smaller holes:
    - A stack overflow now reports like out-of-memory does.
    - Stopping after the last book can no longer cut off a book sent a moment later (`stopSelf(startId)`).
    - Android 15's time limit on data-sync services, or any other shutdown, reports every unfinished book.
  - Verified on `fold8_api37`. Killing `:convert` mid-book restarted the conversion within 10 s; killing it again
    showed the new message; tapping Dynamic Flow converted again. A 939-page textbook then converted
    in full (10 min) with the watch running and no false loss. The 0.6.0-canary.2 release build converted a book
    end to end in 38 s, and so did the phone, so R8 was never the problem.
- **Settings → Help & about → "Replay the tour" plays the tour again.** Since the overhaul's Settings hub
  (Phase 7) it had opened the "Show me around" list instead, so the tour could not be seen after the first launch.
  It now forgets only the tour's own flag (`SettingsRepository.forgetGuide`), and the app shell draws the tour.
  "Show me around" has its own row again. Checked on `fold8_api37`, unfolded and folded.

### Changed
- **The tour's eleven pictures were retaken in the new design** (owner, 2026-11-10). None shows the phone's status
  bar or gesture bar any more, at the owner's request: they are 2256x2360, and the tour's frame
  (`SHOT_ASPECT`) follows.
  - The reader pictures show what the overhaul built: the dock and the riffle mid-drag with its page preview, the
    selection menu's nine colours, the Words panel, Narrator Mode above the dock, and Aa's View row on a book in
    Dynamic Flow.
  - The others: a page caught mid-curl, the atmosphere moods, the goal sheet, and Appearance.
  - The README's copies in `docs/screenshots/` match.

## [0.6.0-canary.2] - 2026-11-10

> **The UX overhaul ("Quiet Library", ADR-0052, `docs/plans/ux-overhaul-plan.md`) is closed: Phase 6.5.**
> - Nine phases between Phase 6 and Phase 7: design system, app shell, Library, Shelves, Notebook, Details,
>   Settings, Reader, and the sweep.
> - Then the owner's rounds of fixes after review, and a new app icon.
> - Every phase was verified locally and on `fold8_api37`. This release is gated on green CI.
> - **Room is at v22** (v21 `book_text`, v22 `book_tags`). Both migrations are additive.
> - OCR stays Phase 7.

### Added
- **A new app icon** (owner, 2026-11-10): "Flowing Pages".
  - An open book on the app's terracotta. The left page is set type; on the right, three orange lines flow as
    Dynamic Flow does.
  - A black Fraunces F stands on the right page's top edge, its stem running on as the bookmark ribbon into a V
    notch.
  - The launcher icon is now Android vector layers (`drawable/ic_launcher_{background,foreground,monochrome}.xml`),
    sharp at every density, with a monochrome layer for themed icons. The old bitmap foregrounds are gone.
  - The splash and the unfolded rail's mark (formerly an "F" on the accent) use the same artwork
    (`flowfolio_icon.png`).
  - Source: `docs/brand/icon-concepts/`. `glyph.py` takes the F from the bundled Fraunces with fontTools, run once
    through `uvx` (a development tool only, nothing added to the app); `make_vector.py` writes the layers.
- **Tags of your own** (owner, 2026-11-10). **Room v22** adds `book_tags` (one row per book and tag, removed with
  the book; additive auto-migration 21 → 22, held by `BookTagMigrationTest`).
  - Details → Overview has "+ Tag": type a word or two, or tap one of the library's other tags. Tap a tag to take it
    off.
  - `BookTagRepository` (`:core:data`) trims and de-duplicates.
- **Text settings per book** (owner, 2026-11-09). **Room v21** adds `book_text` (one row per book that has text of its
  own, removed with the book; additive auto-migration 20 → 21, held by `BookTextMigrationTest`, which replays the
  committed v20 schema into SQLite and opens it under v21).
  - `BookTextRepository` (`:core:data`) gives each book its own text or the defaults (`effective`).
  - The reader uses the book's text. Its Aa panel and pinch-to-zoom set this book only, and Aa offers "Use defaults"
    and "Make default".
  - Settings → Text holds the defaults, and says so.

### Changed
- **The owner's last fixes before the release** (2026-11-10).
  - **Shelves, folded:** the title showed as "Shel…", because four header buttons took the row. Folded, header
    buttons now draw at 40 dp, and their invisible touch margins overlap slightly (`LocalIconButtonSize`,
    `LocalHeaderGap`), so the title shows in full. Every button keeps its 48 dp touch target.
  - **Library, Shelf view:** each shelf slides sideways again, showing all its books over a plank that stays put
    (`shelfRow`). Before, it showed only one row's worth.
  - **The tour's Library, Atmosphere and Appearance pictures** were retaken to show the new rail mark (known issue
    43).
  - **What's new** ignores a debug build's "-dev" suffix, and skips an empty "Unreleased", so it always shows real
    notes. `ChangelogTest` covers both.
  - Checked on `fold8_api37`, folded and unfolded.

- **The reader's panels, held to the mock** (owner, 2026-11-10, second round). Checked on `fold8_api37` unfolded
  except where noted; CI has not run.
  - **One placement for every panel** (`DockPanel`): a glass card as wide as the dock, 560 dp unfolded, a fixed
    10 dp above it, or above the keyboard while one is up. The chrome reports the dock's height (`DockSpace`), so
    the gap is the same for every panel.
    - The dock, riffle included, now stays under an open panel. Opening a panel used to dismiss the riffle bar.
  - **Search** matches the mock:
    - "Search in this book" with "Esc to close" (the Esc key closes it).
    - An accent-bordered field with the number of matches inside it.
    - **Whole book**, **This chapter** and **Match case**. This chapter and Match case are new.
    - Results grouped under the chapter name with a count, each a page number and a line with the match on the
      accent.
    - The ⌃/⌄ stepping, which the mock does not have, is gone; tapping a result jumps there as before.
  - **Aa** is restyled to the mock: tabs **Text · Page · Behaviour** ("Controls" renamed).
    - The rows have the name on the left, the control on the right, and hairlines between them.
    - **Text:** the "Ag" font cards (the chosen one outlined in the accent), Size with round A buttons, and Line
      spacing and Margins as worded segments. Justify and Hyphenate are switches.
    - **Page:** round "Aa" paper swatches (Auto split light and dark), Atmosphere with its value and ›, Page turn,
      Direction, then Two pages side by side and Cover on its own as switches.
    - **Behaviour:** Hide controls after; Page-turn sound and Riffle sound, each a switch with its loudness under
      it; Brightness; Riffle haptics; Keep screen on.
    - For a PDF, Dynamic Flow is now the mock's **View** row (Dynamic Flow | Original PDF) at the top of the first
      tab, with its status and health. It replaces the card above the tabs; switching still offers to bring
      highlights across.
  - **Contents** gains the mock's **Contents | Ribbons** tabs, with the ribbon count, and the current chapter is
    in the accent.
  - **The title pill** shows the time left ("23 h left"). It uses your own pace on this book once there are 10
    minutes over 10 pages, else the mock's 1.1 minutes a page, kept between 15 s and 5 min a page
    (`ReadingEstimate`, tested).
  - **The riffle header** no longer says "Page 12–12" when both screen pages are the same printed page.
  - **The first-book guide:** "Got it" and "Never show tips again" sit side by side in the clear band between the
    menus zone and the riffle zone, inside the side zones.
    - Folded, the second button reads "Never show again", and the zone labels stay on one line.
    - Checked folded and unfolded.
  - Not checked on a device: Search on the folded screen. The emulator's taps kept landing on the page.

- **The owner's fixes after the overhaul** (2026-11-10). Checked on `fold8_api37` unless noted; CI has not run.
  - **Details now follows the mock's layout.**
    - No blurred backdrop, and no shadow under the book.
    - Folded: one centred column. Unfolded: the cover on the left; the title, the author in the accent and the
      format line on the right, then Continue, Shelf, Goal and ★ on one line, the four fact tiles, and the tabs.
    - Overview holds the description, then the chips: the book's shelves with their colour (tap one to open it),
      the reading intentions, your own tags and "+ Tag". A PDF also gets the Dynamic Flow box.
  - **Reader, top bar:** the title pill fills the bar, with **title** · chapter and, unfolded, "N pages left". The
    title shortens before the chapter does. No reading-speed estimate exists, so it counts pages rather than hours.
  - **Reader, dock:** one glass card, as the mock has it. The riffle header and strip sit on top, and the six tools
    below as icons with their names in tooltips. While a panel is open, or the riffle bar is dismissed, the tools
    stand on their own.
  - **Riffle strip: monochrome.** The leaves still bow and riffle as before, the 3D motion is unchanged, and the
    riffle book stays. They are now drawn in the theme's ink, darker on the part already read. Chapter bands are in
    the accent, the boards are ink, and ribbon flags keep their colours above the strip. It is opt-in through
    `BoardColors.ink`/`accent`, so the riffle demo and its screenshot tests keep the original look.
  - **Words is one panel** with Define, Translate and History tabs. The dock's Words tool opens it on History; a
    selection's Define or Translate open it on that tab.
    - Define shows the word in Fraunces italic, its sources as chips, the part of speech and the numbered meanings,
      then Saved to History, Translate and Copy.
    - Translate shows from and to, the passage, your language chips, and the result on the second surface.
    - The old full-screen Define and Translate overlays are removed.
  - **Library:**
    - Folded, the goal ring holds the reading glasses. A reached goal turns them into 🎉 with confetti; the burst
      stays inside the card, so it is small.
    - Unfolded, Details sits on Resume's line.
  - **Fixed:** the chapter shown for a page could be the book's own name. OpenStax outlines carry a bookmark to page 1
    named after the book, and `PageLookup.chapterTitleAt` went by list order. It now goes by page, and the title pill
    also skips entries named after the book. Covered by `PageLookupTest`.

- **Overhaul: the items Phases 8 and 9 deferred** (2026-11-10).
  - **Riffle header:** the chapter in the accent, then one tappable label, "Page 216–217 of 842 · 26%", that opens
    Go to page (hold it for the detour mark, as before).
  - **Selection menu:** the mock's `.selmenu`: the nine highlighter colours in one row over six labelled actions
    (Note, VoiceMark, Define, Translate, Copy, Share; Delete last on a tapped highlight), on the surface with a
    hairline and a soft shadow.
  - **Translate:** your target languages as chips (at first the phone's and English), the current one filled, and
    (+) opening a sheet of every ML Kit language. Tap to add, tap a ticked one to take it off. Kept in settings
    (`translate_languages`); a language still downloads the first time it is used.
  - **Listen:** five speed segments, 0.8× to 2×, instead of a slider. A rate saved from the old slider shows on its
    nearest segment.
  - **Coach marks:** five steps after the first-book demo (the ribbon, the strip, Aa, Listen, the page). The page
    dims round an outlined cut-out of each control, and the card stays centred so it can't be clipped. Shown once;
    Settings → Help → "Introduce features again" brings them back.
  - **Dynamic Flow's progress** beside the page number now says "Preparing · 42%".
  - **The page number** no longer sits under the dock: it hides while a panel is open, and rides above the dock when
    the riffle bar has been dismissed.
  - **Reading goal:** a sheet rather than a centred window: a ring showing the target, 15m to 1h 30m presets with
    five minutes less or more, Day/Week/Month, the reminder, Reset and Save, then the books in progress.
  - **The tour:** 11 slides (Listen added), "N of 11", tappable dots, Back, and the picture beside the words when
    unfolded. Every picture was retaken on this build (known issue 43), in the app and in `docs/screenshots/`.
  - **What's new reads `CHANGELOG.md`**, which the build copies into the APK (`copyChangelog`), so the notes work
    offline and describe this exact build. Sections are New, Changed and Fixed, and earlier versions open one at a
    time. After an update it shows this version's notes, falling back to the releases page's. Settings → Help has
    it too. `ChangelogTest` covers the parser.
  - **Palette aliases removed:** `title`, `item`, `hint`, `subItem` and `glyph` are gone from `Palette`; about 500
    uses now name `ink` or `ink2`. The design-system screenshot tests match pixel for pixel, so no colour moved.
  - `Glyph.Close` (✕) added.

- **Overhaul Phase 9: the sweep and close-out** (2026-11-09).
  - **Reset progress waits for RESET to be typed.** The button stays greyed until the word matches. `DialogAction`
    gains `enabled` for this.
  - **Every icon button has a tooltip:** 25 that had none now show their label on long-press.
  - **Lint fixes:** the Notebook and Shelves snackbars read their strings through `LocalResources`, which follows
    configuration changes, not `LocalContext`. `DatabaseModule` is allowed one provider per DAO.
  - **`docs/plans/ux-overhaul-parity.md`** lists where every existing feature now lives and how it was checked.
  - The intro, What's new, the goal sheet, the alias removal and the tour pictures followed on 2026-11-10 (above).

- **Overhaul Phase 8: the reader's chrome and in-book search** (2026-11-09).
  - **Top bar:** Back, the book's title on a glass chip, the ribbon button (tap toggles, long-press picks a colour)
    and ⋯ (Go to page, Ribbon colour).
  - **The dock:** six tools on glass above the riffle bar: Contents, Search, Aa, Listen, Notebook, Words. Each has a
    glyph and a label, and the open panel's tool sits on the soft accent. **The unfolded right-hand rail is gone**:
    it sat on the right page's line ends. Every tool it held is in the dock or the top bar.
  - **Panels** now open from the bottom, just above the dock, rather than under the top bar.
  - **In-book search (bug 1):**
    - It waits for two characters and 250 ms of quiet before it searches.
    - Snippets put a space where two blocks meet ("Brief Contents Title Page", not "Brief ContentsTitle Page").
    - The hit's highlight now ends on the match's last character; the EPUB end offset was off by one.
    - Results show a count and ⌃/⌄ stepping, and are grouped by chapter with printed page labels.
    - While Dynamic Flow is still preparing a book, search reads the PDF's own text instead of finding nothing.
    - `SearchSnippetTest` covers the snippet joining and the bounds.
  - The riffle restyle, the selection menu, the language chips, Listen's speeds and the coach marks followed on
    2026-11-10 (above).

- **Overhaul Phase 7: Settings** (2026-11-09).
  - **The hub:** ten sections in the mock's order: Appearance, Text, Reading, Sound & voice, Atmosphere, Library,
    Dynamic Flow, Updates, Storage, and Help & about. Each row shows the orange `SettingsTile`, the section's name,
    a live one-line summary of its values, and a chevron.
  - **Folded,** a row opens the section's own page (Back, a large tile, the title). **Unfolded,** the hub stands as a
    compact list on the left with the chosen page on the right.
  - **New pages:**
    - **Sound & voice** gathers the page-turn and riffle sounds, their volumes and the haptics, and says where
      narrator voices live.
    - **Help & about** holds the tour, reader tips again, licences, Send feedback (the project's issues page) and the
      version line.
  - **Atmosphere** has a headphones glyph (`Glyph.Ambience`) in place of the smiley.
- **Overhaul Phase 6: book details** (2026-11-09).
  - **The primary action says where it lands:** "Continue · p. 55" (the page's printed label), "Start reading" or
    "Read again". Beside it: Shelf, Goal, and a ★ that makes the book a favourite.
  - **Four fact tiles:** Complete %, Read, Ribbons and Shelves. Each name is singular or plural as the figure needs
    ("1 SHELF" was wrong), and the ribbons come from `MarkCounts`.
  - **Tabs in the mock's order:** Overview, Contents, Notebook (with its count, the book's count pills, and "Open
    notebook"), Details and Author.
  - **⋯ menu:** Edit details, Add to shelf, favourite, finished, Locate for linked books, **Export notes** (this
    book's marks as Markdown, through the share sheet), **Reset progress** (asks first), and Remove from library in
    red.
- **Overhaul Phase 5: the Notebook** (2026-11-09).
  - **One count for every screen** (`MarkCounts`, `countByBook` in `:core:data`). Every mark is counted after the
    reader's current-view filter (`ReadingViews`), so the Library, the Notebook and Shelves give the same figures.
    - Annotations split into highlights, notes (those with words) and VoiceMarks (those with a recording).
    - Ribbons and saved words are counted by book.
  - **Notebook landing** (`NotebookHome`): "Everything you marked, book by book", with:
    - Search, Sort (Recently marked, Book title, Most marks) and ⋯ (Export as Markdown, Export as CSV, Share all,
      through the system's share sheet);
    - chips for All, Highlights, Notes, Bookmarks, VoiceMarks and Words, each with its count;
    - one card per book with a count pill per kind, zeros dimmed. Unfolded, cards sit in two columns and show the
      latest mark.
  - **A book's notebook** (`NotebookBookScreen`, route `notebook/book/{id}?kind=`): its cover and counts, tabs by kind
    with counts, and Sort (Page order, Newest first, Type). Each mark shows its page and kind:
    - quotes ruled in their highlight colour, with the note under them;
    - a play row for VoiceMarks;
    - words with their part of speech, or a "Translation" label when there is only a translation.

    Each has Open at this page, Share, and Delete with Undo (the mark is hidden at once and deleted when the snackbar
    goes).
  - The Library's book menu opens these pages at the right kind. The old per-book Bookmarks, Annotations and
    Dictionary routes still work.
- **Overhaul Phase 4: Shelves** (2026-11-09).
  - **Shelves tab:** two columns folded and three unfolded, each card with:
    - a 2 dp border in the shelf's colour;
    - its name on two lines, beside the Dynamic Flow switch (for shelves with PDFs) and ⋯;
    - up to five flat covers fanned 5 degrees apart;
    - its books, annotations, ribbons, words and time read, each read out with its name.

    A dashed "New shelf" card closes the grid.
  - **Header:** Search, Sort (Custom order, Name, Most books, Most read, Most marks; `ShelvesOrder`, remembered in
    DataStore) and New shelf. Select mode is kept.
  - **Shelf ⋯ menu:** Open, Rename, Change colour, Add books, Dynamic Flow for the shelf, Select, and Delete shelf in
    red.
  - **New shelf** (`ShelfNameColourDialog`): a name and a row of colours, followed by a snackbar offering "Add books".
    **Delete shelf** asks first and offers Undo, which puts the shelf back with its books and colour.
  - **Shelf page:** Back, Sort and view (the shelf's orders including Custom, then Grid or List), Add books and ⋯
    (Rename, Change colour, Add books, Delete shelf), over the shelf's colour dot, its name and its counts.
  - **Books on shelf pages and the Finished tiles** are the flat 2:3 cover now (`ShelfBook`). `Book3D` stays for the
    flights.
- **Overhaul Phase 3: the Library** (2026-11-09).
  - **Header:** the title with Reading goal (folded only; unfolded it is a ring in Continue reading), Sort and view,
    and Import. Import offers files, a folder, and **"Make shelves from a folder…"**, which imports a folder and makes
    its shelves with no question asked (`onFolderPickedForShelves`).
  - **Global search:** a pill under the header opens a full-screen search (`GlobalSearch`).
    - Before typing, it shows the four most recently opened books and a hint.
    - From two letters on (`SEARCH_MINIMUM`, also enforced in `SearchViewModel.search`), it shows grouped results.
    - Nothing matching says so, and points to the reader for searching inside a book.
  - **Stats band** (`StatsBandCard`): one summary line ("17 books · 5 in progress · 2 finished", plus ribbons and time
    read unfolded).
    - It opens on a spring to eight tiles, each leading to its chip, its Notebook section or the reading goal.
    - Its state is remembered (`UserSettings.statsBand`), and Settings → Appearance → Library stats chooses Collapsed,
      Expanded or Hidden.
  - **Continue reading:** the cover, "Continue reading", the title, the author, "Page X of Y" with a percentage, a
    thin bar, and Resume (plus Details unfolded).
    - The ⋯ menu sits in the card's top-right corner.
    - Unfolded, the reading-goal ring takes a third column (`GoalRing`).
  - **Folded reading-goal card** (`GoalCard`): a 46 dp ring (green once the goal is met), time read in the goal's
    period, what is left, and the reminder.
  - **Chips:** a ★ Favorites chip joins the rest. Recent and Folders keep their place at the end.
  - **Books:** one 2:3 cover everywhere (`BookTile`), with its badges, its title, and a progress bar, "Finished" or
    "Not started".
    - In Shelf view, each shelf shows a heading with "N books ›" and its first three (folded) or six (unfolded)
      books, the plank *under* the titles and progress lines. "Not on a shelf" comes last.
    - Grid and List views, and every filtered view, keep the chosen view.
  - **Sort and view** (`SortViewMenu`): Recently opened, Title, Author, Date added, Progress or Read time
    (`LibrarySort`, now six orders, `orderLibrary`), applied in every view and inside shelf rows. View as Shelf, Grid
    or List (`LibraryView`). Both are remembered in DataStore.
  - **Book menu** (long press, or ⋯ on Continue reading; `BookMenu`): Open, Details, favourite, finished, Add to
    shelf, the book's Notebook pages, Dynamic Flow for PDFs, its reading goal, Locate for linked books, and Remove
    from library in red.
- **Removed:**
  - The library's separate "Finished" row (the Finished chip shows those books).
  - The header's totals block and goal path (`LibraryTotals.kt`).
  - Drag-to-reorder inside library rows. Shelf pages keep drag-to-reorder.
- **Not added:** "Share file" from the mock's book menu waits for the owner, since it would widen the FileProvider
  beyond update downloads.

### Fixed
- **Counts that disagreed** (5 ribbons on the library card, 2 on the Bookmarks page): every count now comes from
  `MarkCounts`.
- **Translation-only words** show a "Translation" label instead of a missing part-of-speech chip.
- **Library scroll crash** `IllegalArgumentException: Key "row-loose" was already used`. The "Not on a shelf" row and
  the "Finished" row both had no shelf and so took the same lazy key. Every row now has its own key (`ShelfRow.key`),
  and `LibraryOverhaulTest` holds the regression.

### Changed (earlier phases)
- **Overhaul Phase 2: app shell and navigation** (2026-11-09).
  - **Four destinations instead of seven:** Library, Shelves, Notebook and Settings.
    - Folded, a glass tab bar with labels.
    - Unfolded, a rail down the left edge: the app's mark at the top, the tabs, and Settings pinned to the bottom.
    - The selected tab sits on a soft accent pill that grows from its centre on a spring.
  - **Favorites** is now a ★ chip in the Library (`LibraryFilter.FAVORITES`), not a tab.
  - **Notebook** (`NotebookScreen`, `NotebookSection`) holds the Bookmarks, Annotations and Dictionary pages as
    sections under one header and three chips. Each page keeps its own search, select mode and lists until Phase 5
    rebuilds the Notebook.
  - **Retired routes forward to their new homes:** `favorites`, `bookmarks`, `annotations` and `dictionary` go to the
    Library with the Favorites chip, or to the Notebook at the right section (`Destinations`). Their tabs stay lit.
    Notification intents and the adb debug extras are unchanged.
  - **Status-bar fade:** content never shows through under the clock. The fade uses the background colour, and
    unfolded there is no bottom fade.
  - **Calmer motion:**
    - Tab switches fade through from 98% over 300 ms.
    - Screen elements rise 16 dp as they fade in, in place of the 112% fall-in. The stagger is 30 ms and each rise
      takes 420 ms.
    - The rail and bar slide on Material's emphasized curves.
    - With the system's animations removed, entrances are skipped.
  - **Tests:** `DestinationsTest` covers the tabs, the retired routes and the Notebook sections. `ShelfRowsTest` adds
    the Favorites chip.
- **Overhaul Phase 1: design system** (2026-11-09).
  - **Tokens.** Every theme is now one set of semantic colours (`Tokens`: bg, bg2, surface 1 to 3, line, three inks,
    one accent with its ink and soft tint, ok, danger, paper), built into a `Palette` by `quietPalette`.
    - FlowFolio light uses a terracotta accent and dark an amber one, both on warm neutrals.
    - Rustic, AMOLED and the Material You fallback take the mock's overrides. Material You still maps the wallpaper's
      dynamic scheme onto the same slots.
  - **Aliases.** The older role fields (`title`, `item`, `hint`, `subItem`, `glyph`, toggle and button colours) are now
    aliases of the semantic set, so every screen took the new look at once. They were removed at the end of the
    overhaul.
    - Warnings and removals now name `danger` explicitly: swipe-to-delete, the selection bin, VoiceMark recording and
      discard, the annotation and shelf warnings.
  - **Kept as they were:** ribbon colours (frozen at their exact hues) and the highlighter colours.
  - **"Night glow"** (was "Neon glow") is off by default. It now only blooms titles in dark mode. A stored choice is
    kept.
  - **Accessibility:** the light theme's third ink is `#938574`, a step darker than the mock's `#9A8B79`, so meta text
    reaches 3:1 on the background (WCAG AA).
  - **Components restyled:**
    - Cards are the surface with a hairline and a soft shadow.
    - Chips are 34 dp; selected ones fill with ink and can show a count.
    - Icon buttons are 42 dp on the surface with 48 dp targets and a long-press tooltip.
    - Pill buttons are flat (primary, ghost, soft, danger; `FlowFolioButton`).
    - The toggle's track is its state (accent or neutral) and the thumb is always white.
    - Sliders always show their value.
    - The segmented control and the underlined tabs (with counts) slide on springs.
    - The progress bar and ring are accent on a neutral track; the ring takes a size.
    - The shelf plank and the page background are flat.
    - Glass is the surface at 84% over an 18 dp blur.
  - **New components:**
    - `FlowFolioMenuItem`, `FlowFolioMenuHeader` and a restyled `FlowFolioMenu`: checkmarks, trailing notes,
      destructive items.
    - `FlowFolioSheet`: Material's sheet, with predictive back.
    - `FlowFolioDialog`: icon tile, optional text field and colour swatches, destructive variant.
    - `FlowFolioSnackbar` with `showFlowFolio`: an icon and an Undo action.
    - `FlowFolioTooltip`, `FlowFolioStepper` and `SettingsTile`.
    - `BookCover`: one 2:3 cover with spine, sheen, shadow and badges at the bottom right. Generated covers now set
      their title at the top.
  - **Motion:** interactive parts move on springs and can be interrupted mid-flight. Panels, menus and snackbars rise
    16 dp on the mock's curve. The system's "Remove animations" is read into `LocalReducedMotion`.
  - **Launch window:** the background takes the new colours.
  - **Tests:** `TokenContrastTest` checks WCAG contrast in every theme. `ComponentScreenshotTest` holds Roborazzi
    references for FlowFolio light and dark, Rustic light and dark, and AMOLED.

## [0.6.0-canary.1] - 2026-11-08

> **Phase 6 (reading features) is closed.** `docs/plans/phase6-signoff.md` records it; ADR-0051 (offline neural TTS via
> `sherpa-onnx` with Kokoro and Piper voice packs) is accepted with this sign-off. The release is gated on green CI,
> including the converter corpus gate.

### Added
- **Narrator Mode with offline neural voices** (Phase 6 M3, ADR-0051). The reader can read a book aloud offline using
  two neural text-to-speech families powered by `sherpa-onnx` (`:core:voice`): **Kokoro** (`kokoro-int8-multi-lang-v1_0`,
  11 US and UK English voices — Heart, Bella, Nicole, Sarah, Sky, Adam, Michael, Emma, Isabella, George and Lewis — from
  one shared download) and **Piper VITS** (`Amy` US female and `Alan` UK male as standalone lighter-weight downloads).
  Voice packs download on first use with SHA-256 verification and a non-blocking progress overlay in the reader chrome.
- **Natural reading cadence, hyphen joining, and seamless page transitions in Narrator Mode** (owner, 2026-11-08).
  `SpeechText` inserts distinct structural pauses after titles (`480 ms`), subtitles (`340 ms`), paragraphs (`320 ms`),
  sentences (`150 ms`) and long clauses (`65 ms`), while inserting **zero pause (`0 ms`)** when a page ends
  mid-sentence. `Narrator` pre-synthesizes the next page's opening sentence into the active `AudioTrack` queue before
  auto-advancing and plays the page-turn sound on flip. Words split across lines or page boundaries by `-`, soft hyphens
  (`\u00AD`) or PDFium line-break hyphens (`\u0002`) are rejoined, running headers and folios are stripped across
  mid-sentence page turns, and regular plural (`-s`/`-es`/`-ies`) and past-tense (`-d`/`-ed`/`-ied`) lexicon inflections
  are generated on-device.
- **Vocoder de-hiss conditioning** (`PcmAudio.cleanVocoderBand`, owner, 2026-11-08). A zero-allocation 2-pole Butterworth
  low-pass filter at `8.2 kHz`, a soft quadratic downward expander below `-41 dBFS`, and `6 ms` raised-cosine edge fades
  strip 8-bit neural vocoder quantization hiss while keeping speech crisp.
- **Search inside a book** (Phase 6 M1). A Search tool in the reader searches every chapter of a Dynamic Flow book or
  every page of a PDF, showing each match with its chapter/section, surrounding snippet and page number; tapping jumps to
  the place and highlights the match until the search is dismissed.
- **Fixed-layout EPUBs** (Phase 6 M2). EPUBs with `rendition:layout: pre-paginated` open through `FixedEpubPageSource`,
  rendering one spine item per page at its viewport dimensions in an isolated, network-blocked WebView rather than
  reflowing page-designed books.
- **Three-source Define across Wiktionary, WordNet 3.1 and Wikipedia** (owner, 2026-11-07). Looking up a word queries the
  offline English Wiktionary, the full bundled Princeton WordNet 3.1 database (`wordnet.db.gz`, 132k entries with
  synonyms and hypernyms), and English Wikipedia's opening summary for specialized and technical terms.
- **Vertical tool rail on the unfolded reader** (owner, 2026-11-08). When unfolded, the reader's secondary tools (Aa,
  Narrator, Bookmark, Annotate, Translate, Search, Contents) sit in a vertical glass rail on the right edge with an
  active-panel glow.
- **First-use guides on the reader's buttons** (Phase 6 M0). The first time a reader taps Contents, Go-to-page, Aa, the
  ribbon, Annotations or Dictionary, a centred window explains it — "Got it" and "Never show tips again" — remembered via
  `seenGuides`/`tipsEnabled`.
- **The Bookmarks tab groups by book** (Phase 6 M0). One card per book — its title, author, the ribbon colours actually
  placed in it, and "N Bookmarks" — newest ribbon first; tapping a book opens its ribbon cards and colour filter.

### Changed
- **Top-level page titles sit on the button row** (Phase 6 M0). `ScreenHeader` puts the title beside the page's buttons
  (the slogan drops below); on a narrow (folded) screen the title steps down a size so a name like "Bookmarks" stays
  whole.
- **Formatted release notes in the "What's new" window** (owner, 2026-11-08). `WhatsNewDialog` renders headings, bullets
  and bold spans from the release notes and waits until the first-launch tour is dismissed on fresh installs.

### Fixed
- **R8 keep rules for ML Kit and `sherpa-onnx` JNI callbacks** (owner, 2026-11-07 & 2026-11-08). Kept `com.google.mlkit.**`,
  `com.k2fsa.sherpa.onnx.**`, and `ChunkCallback`'s specialized `Function1.invoke([F)Ljava/lang/Integer;` method so
  on-device translation and streaming neural TTS work in minified release builds.
- **Piper VITS single-file lexicon loading and atomic voice-pack switching** (owner, 2026-11-08). `VoiceEngine.loadPiper`
  writes a single merged lexicon file (`lexicon-en-us-complete.txt`) as required by `OfflineTtsVitsModelConfig`, and
  `VoicePackDownloader` uses `.part` staging files with 3x retry and clean coroutine cancellation so switching voices
  mid-narration never triggers false download errors.
- **Page-turn sounds play even under Do Not Disturb** when the reader has them enabled, and the Define panel opens
  directly under its button with smaller justified text.
- **The folded running head no longer sits under the front camera** (Phase 6 M0). It starts left of the text border and
  truncates at the camera's left edge, read from the display cutout rather than a hardcoded offset.
- **The unfolded section label right-aligns** to the right page's outer margin, growing left only to that page's inner
  margin, ellipsis at the right.
- **The reader's running head no longer overlaps the tools.** It is hidden while the chrome shows (with or without a
  panel open), so a chapter title never sits under the top bar's buttons.
- **The book-details title caps at three lines**, and tapping the ellipsis opens the full title in a small window.

## [0.5.0-canary.1] - 2026-11-07

> **Phase 5 (the product flow around conversion) is closed.** `docs/plans/phase5-signoff.md` records it. This is the
> first canary release under the renumbered channels (ADR-0050: 3–4 alpha, 5–6 canary, 7 beta). The release is gated on
> green CI, including the converter corpus gate.

### Added
- **Progressive delivery** (Phase 5 M1). A converting book's first chapter is readable in Dynamic Flow long before the
  whole book is done, instead of only at the end. The converter renders each chapter's images and rewrites the EPUB
  after every chapter — a valid EPUB of only the finished chapters — and the sidecar carries `complete: false` and a
  `finishedSpineItems` list while partial. The service writes each partial atomically and reports "chapter N of M
  readable"; the reader opens the first chapter as soon as it lands, then picks up further chapters as they land
  (throttled so reading isn't interrupted every chapter), and reopens to the whole book when conversion finishes. A
  complete conversion is byte-for-byte unchanged, so the engine version is not bumped and no book re-converts.
- **The first-book demo** (owner, 2026-11-07). The first time a reader opens a book, an overlay marks the tap zones —
  the sides for page turns, the middle for the menus, the bottom for the riffle bar — with "Got it" and
  **"Never show tips again"**, which turns all first-use tips off. "Introduce features again" in Settings turns them
  back on.
- **The shelf page header** (owner, 2026-11-07). Back, import (+) and select sit on one line; the shelf name moves to
  its own line below (a little smaller) so a long name has the full width; the sorting and tiles/list line sits under
  it.
- **The update banner opens Settings at the update section** (owner, 2026-11-07), not just the Settings tab.
- **A "what's new" window after an update** (owner, 2026-11-07), shown once per update and dismissed only by "Ok".
- **The conversion health badge** (Phase 5 M2). The reader's "Aa" sheet now says how well a book converted, from the
  metrics already in `flowfolio.json`: "Converted well · 98% text", or "A few/Many pages are pictures" when pages fell
  back, graded good/fair/poor from the fallback count.
- **The reader's chapter and section** (owner, 2026-11-07). In Dynamic Flow a spread shows the chapter at the top left
  and the section at the right page's foot, each kept to its own page's column; folded it shows the section (or the
  chapter) at the top left, cut at half the screen so the front camera never clips it. Tapping either opens the
  contents; tapping the page number opens "Go to page".
- **Settings as a menu of pages** (Phase 5 M3). Settings is now a menu — Appearance, Text, Reading, Atmosphere,
  Library, Dynamic Flow, Updates and Storage each open a page of their own behind a shared piece of art (the page's
  icon on a glowing plate), with a slide-and-fade transition; About and "Show me around" stay on the menu.
- **Annotations: counts, VoiceMark tabs and the author** (owner, 2026-11-07). Each book's card on the Annotations page
  shows its highlight, note and VoiceMark counts beside its cover, with the author under the title; the Notes and
  VoiceMarks tabs sit on the right with a glyph each.
- **The Favourites page header** (owner, 2026-11-07). Favourites matches the other top-level pages — the title on the
  left with its slogan underneath, and Search and Select on the right; in select mode, Delete asks whether to remove
  the chosen books from Favourites or from the library.

## [0.3.0-alpha.6] - 2026-11-06

> **Phase 4 (trust the conversion) is closed.** `docs/plans/phase4-signoff.md` records it. The honest summary: the
> "fallbacks worth keeping" work and the owner's tweaks (code highlighting, the update check) landed fully; the pixel
> verification that was the phase's core ended up diagnostic-only — `geometricCoverage` is recorded but not gated,
> because the geometric signal false-positives on text-matrix-scaled PDFs. A real pixel diff is deferred. ADR-0050 is
> proposed with this sign-off (release-channel renumbering).

### Added
- **Syntax highlighting for code in Dynamic Flow** (owner, on-the-fly tweak). `CodeHighlighter` (pure JVM) detects the
  language of a code listing from its keywords and comment markers across seven languages — Python, JavaScript, Java,
  Kotlin, Shell, SQL, C/C++ — and tokenises keywords, strings, comments and numbers; the reader colours a whole
  `<pre>` listing automatically. An unrecognised listing keeps the plain code face, so nothing is ever coloured wrong.
- **The app checks for updates when it opens or returns to the foreground** (throttled to ten minutes), and drops an
  in-app banner when a newer build exists. Tapping it opens Settings at the update section, which now has a
  **"What's new"** link for the release's own notes.
- **Geometric coverage in the sidecar** (Phase 4 M1+M2). `geometricCoverage` records, per page, the share of text whose
  glyph boxes agree with their declared size — the check that would have caught the Packt bug (fonts reported as
  `size=1.0` against ~10 pt glyphs). It is recorded and printed by the corpus gate, **not gated**, because the
  size-vs-glyph signal false-positives on text-matrix-scaled PDFs.
- **Fallbacks worth keeping** (Phase 4 M3): covers pass through as full-page images — a first/last page that is a
  *design* (graphics with little text), not a title, index or colophon page that merely sits on an edge; fallback and
  figure crops are sharper (figure 216 dpi, page 144 dpi); captions pair with
  equations as well as figures and tables; a table wider than the column spans the full width; and a pulsing magnifier
  hint marks pictures that open when tapped. The long-press menus are themed to match the design system.

### Fixed
- **A folded (single-page) page turn did not show the back of the page** while curling (owner). The single-page reader
  passed no `backPage` to the curl, so the back of the turning sheet fell back to a translucent mirror of the front.
  It now shows the next page — the verso of the leaf — the same way the unfolded spread already did.
- **Spurious spaces in justified text** ("ver y", "ser vice"; the DevOps book). The word-space threshold was raised
  from 0.25 em to 0.32 em, so a justified line that stretches its within-word gaps unevenly is less likely to read
  some of them as spaces. Untested against the DevOps book it was reported from; documented as such in the code.

> The converter's engine version is bumped to **1.4.0**, so Dynamic Flow books are rebuilt on first open.

## [0.3.0-alpha.5] - 2026-11-06

> **Phase 3 (converter v1 on the phone) is closed.** This is the sign-off release: every exit criterion is met,
> including the Fold timing — *University Physics* (959 pages) converts in **199.8 s** on the real Fold (target 5
> minutes). `docs/plans/phase3-signoff.md` records it; ADR-0044, ADR-0048 and ADR-0049 are accepted with this sign-off.

### Added
- **An in-app licences screen** (known issue 8, "needed before release"). Settings &rarr; About &rarr; Open-source
  licences lists everything that ships inside FlowFolio: the ten typefaces with their full SIL Open Font License
  (or Bitstream Charter) texts, PDFium and its bundled third-party components, and the remaining libraries by name
  and licence. The full texts are read from the very assets the app distributes
  (`assets/third_party_licenses/`), so what is shown is what ships; tapping a row expands its text.

## [0.3.0-alpha.4] - 2026-11-03

### Fixed
- **Code listings came out as columns of single letters** in some books (owner, reported on a Packt *Learning
  DevOps* PDF). A listing is laid out on a character cell, each glyph placed at `(x - left) / cell` so that code
  indented or drawn with characters keeps its shape. The cell came from the book's code *style* in preference to
  the listing itself, and that book sets its fonts at size 1 and scales them through the text matrix -- so every
  style arrived as `size=1.0` and the cell fell back to `size * 0.6` = 0.6pt against a real cell of 6pt. Ten
  times too narrow gives every character a column of its own. The listing's own spacing, measured from the very
  glyphs being laid out, now comes first; the style only stands in when a listing has no pair to measure
  (`Blocks.advanceFor`, `CodeAdvanceTest`).
- **Letter-spaced text was read as one word per letter.** A quarter em of space meant a word space on its own,
  which is true of ordinary text but not of text set with tracking, where *every* glyph stands that far from the
  next. A word space now has to clear the line's own low-quartile gap by a quarter em, which leaves ordinary text
  exactly as it was (`Extraction.typicalGap`, `WordSpacingTest`).
- **Three of the eight bundled ambiences had no atmosphere.** Ocean, Stream and Café could only be reached by
  building a custom atmosphere, so they looked like sounds that were never added. **Seaside**, **Streamside** and
  **Corner Café** join the five presets, and `AtmospherePresetsTest` fails if a bundled recording is ever left
  without one again.

### Added
- **"Make shelves from this folder"** in a folder's press-and-hold menu, in the Folders view and on a folder's own
  page. It acts at once rather than offering, since the reader went looking for it, and folders inside it get
  their own shelves too. The offer after a folder import is unchanged.

> Dynamic Flow books convert again on first open: the converter's output changed, so engine 1.2.3 supersedes
> 1.2.2 and existing bundles are rebuilt. Highlights follow (`FlowCarrier.reanchor`).

## [0.3.0-alpha.3] - 2026-11-03

> **The first release of FlowFolio that anyone can install.** The two earlier pre-releases were withdrawn on
> 2026-11-03: the repository was private for their whole life, neither was ever downloaded, and neither can be
> updated from. See them below for the record.
>
> If you sideloaded one of them, **uninstall it before installing this** — it is signed with a different key, so
> Android treats it as a different app and will refuse to replace it. Anything in that install goes with it.
> From here on the signing key does not change, so every later build updates in place.

### Changed
- **Release APKs are signed with APK Signature Scheme v3** (ADR-0049). v3 is the only scheme that can carry a
  rotation lineage, so a build without it could never nominate a successor key even while the current key was in
  hand. v1 is switched off: it is only needed below API 24 and minSdk is 29. Verified with `apksigner verify
  --min-sdk-version 29 --max-sdk-version 37`, and the signed build installs and runs on `fold8_api37`.
- **Release builds are signed with the release key, or they fail** (ADR-0042). `checkReleaseSigning` in
  `app/build.gradle.kts` now fails `packageRelease` when `flowfolio.releaseSigning.*` is missing, rather than
  quietly emitting an unsigned APK as it did for alpha.2. The dev key signs sideloaded development builds only:
  it lives on a development machine, so making it the identity readers update against would let anyone holding
  it publish an update straight over an install.
- **The repository is public**, so **Settings &rarr; Updates** works for the first time. While the repository
  was private the releases API answered 404 and no check could ever find a build.

### Fixed
- **A failed update check says which failure it was** (owner, 2026-11-03). `UpdateState.Failed` carried a single
  `offline` flag, and every non-200 from the releases API collapsed into it, so a prompt, correct `404` was
  reported as "Could not reach the releases page" -- blaming the reader's connection for a page that had answered.
  `UpdateFailure` now carries the reason (`OFFLINE`, `MISSING`, `BUSY`, `UNEXPECTED`, `DOWNLOAD`) from the
  connection up to the screen, and each gets its own sentence. `UpdateFailureTest` holds the mapping. This makes
  the message honest; only making the repository public makes the check find anything.

## [0.3.0-alpha.2] - 2026-11-03 — WITHDRAWN

> Withdrawn 2026-11-03, never downloaded. It was signed with the development key rather than a release key
> (ADR-0042), so nothing can update over it. Kept here for the record only.

> Signed with the **dev** key rather than the release key (owner's decision, 2026-11-03: the release key was not on
> the build machine). It therefore cannot install over 0.3.0-alpha.1 -- Android sees a different app -- so this
> build needs a clean install, and so will the next one if the release key comes back.

### Added
- **M5, Reading Atmosphere, VoiceMark, the tour and the rest** (owner, 2026-11-02 to 2026-11-03). Five rounds of the
  owner's own design, built and checked on the `fold8_api37` emulator:
  - **Reading Atmosphere** (`Atmosphere`, `AmbientPlayer`, `Ambience`, `AtmosphereOverlay`): the page and the sound
    kept together. Five moods ship with the app over eight recorded ambiences; a reader can make their own from a
    paper, an ambience, **an audio file on the phone or any stream address**, choosing between the three with radio
    buttons. Choosing a mood plays a drawn scene over the page for a moment -- rain, a fire, swell, shafts through
    leaves, a stream, a moon and fireflies, café bokeh, a breathing haze. An atmosphere can be edited by the pencil
    beside it and deleted from inside its own editor, which says what will go before it goes.
  - **Audio from a page, through yt-dlp embedded** (ADR-0048's sibling, **ADR-0047**; `:core:extract`): a stream
    address that is a *page* rather than a file is resolved. `StreamResolver` reads what any site publishes openly --
    an `og:audio` tag, a podcast enclosure, an `<audio>` element, a `.pls` or `.m3u`, an HLS manifest in the player's
    own data -- and only when that finds nothing does it ask yt-dlp, running on an embedded CPython 3.12 (Chaquopy).
    Only an address is extracted; Android's own `MediaPlayer` plays it, so nothing is downloaded and no ffmpeg is
    needed, which is what keeps the app clear of GPL code (ADR-0003). Live pages answer nothing on purpose.
  - **VoiceMark** (`VoiceRecorder`, `VoicePlayer`, `VoiceMarkPill`, Room v20): a spoken note kept against a highlight,
    recorded as AAC in an `.m4a`, with its loudness sampled as it runs so the waveform can be drawn without decoding
    the file again. The pill's one button is a microphone, a square, a play or a pause depending on what it will do.
    The Annotations panel gained a VoiceMark tab where each one can be played, stopped and deleted, while tapping the
    row still goes to the passage.
  - **The detour mark** (`DetourState`, `RiffleGestures`): holding the riffle book drops a mark, which pulses on the
    bar *and* beside the page number, so dismissing the bar does not take the way back with it. Tapping either
    returns to the mark and clears it, and a notice says so when one is made.
  - **An offline dictionary and a Dictionary page** (ADR-0046), translation history that remembers its page, and a
    bin on every definition, translation, note and ribbon.
  - **The app's own tour** (`Onboarding.kt`): ten pages of **real screenshots of this build**, swiped or stepped
    through, with Skip on screen the whole way. Shown once on first launch and brought back by
    "Introduce features again".
  - **Folders become shelves** (`FoldersToShelves`): after importing a folder, FlowFolio offers to make a shelf for
    every folder that holds books, at any depth, named after the folder and coloured at random. The question says
    what it will do and carries a "don't ask me again".
  - **Updates in the app** (**ADR-0048**, `Updates`): Settings says which build is running, checks the releases page
    for a newer one, fetches it and hands it to Android's installer -- which always asks first. "Fetch updates
    automatically" does the checking and fetching, never the installing, because no app can install silently and
    this one does not pretend to.
  - **Storage housekeeping** (`Maintenance`): Clear cache, with what it is holding shown beside it, and Reset
    FlowFolio, which empties the library and everything made in it. **No file on the phone is ever deleted** -- a
    book read from its own location is only let go of; only FlowFolio's own copies go. Settings are kept, and the
    confirmation says all of this.
  - **Intentional tags** on a book's details ("Need the right mood", "Want to study this", "Paused, not abandoned",
    "Read with someone"), in a row whose ends fade while there is more past them and whose chosen tag glows.

### Fixed
- **The ambience was not on the media stream.** `AmbientPlayer` set its audio attributes *after* `prepare()`, where
  `MediaPlayer` ignores them (`setAudioAttributes called in state 8` in the device log), so ADR-0032's placement was
  silently not happening for a reader's own sounds. Set before `prepare()` now.
- **Wiktionary's stylesheet showed up in definitions.** A `<style>` block's *contents* are text, so stripping tags
  left `.mw-parser-output .defdate{font-size:smaller}` under the word. `WiktionaryStore.readable` removes style and
  script blocks, and any CSS rule that lost its tags, before the markup.
- **The VoiceMark clock sat at 00:00.** The recorder's flags are plain properties, so reading them told Compose
  nothing and nothing redrew. The ViewModel publishes both as state now, held to it by `VoiceTimerTest`.
- The detour mark was drawn between the two halves of the riffle bar's label, and its notice overlapped the reader's
  top bar.
- The one-line welcome no longer stacks on top of the first-launch tour.

### Changed
- `FlowFolioChip` can be lit as well as ringed when selected (`glow`), drawn with a cached soft shadow rather than a
  GPU blur, since these sit in scrolling rows (known issue 21).
- `Modifier.scrollEdgeFades` fades a scrollable row at whichever end still has something past it, on the content's
  own alpha so it works on any background.
- `VoiceRecorder` is an interface, so the pill's clock can be tested without a microphone.


### Added
- M4b, the owner's second batch of tweaks (owner, 2026-11-02):
  - **A slogan on every top-level page** (`ScreenHeader.kt`): the line under the page's name that Shelves already had
    ("Collections you curate") now appears on Bookmarks, Annotations, Dictionary and Settings too, with each page's
    buttons bottom-aligned and dropped onto the slogan's line rather than floating above it, so select mode opens
    neatly under the heading.
  - **Four more ribbons and four more highlighters** (owner: "why not make it more fun"): Teal, Amber, Rose and
    Violet ribbons; Orange, Teal, Mint and Coral highlighters. Appended to their enums so every stored colour keeps
    its name. The ribbon strip wraps five to a line and the highlighter grid splits five and four, since nine no
    longer fit one line on the cover screen.
  - **A translation remembers where it was asked for** (Room v18: `translations.page`, `translations.locator`,
    `ReadingPlace`): tapping one in the reader's Dictionary panel or on the Dictionary page takes the reader back to
    that page, which definitions already did.
- M4, the owner's batch of tweaks (owner, 2026-11-02):
  - **AMOLED theme** (`AppStyle.AMOLED`, `AmoledPalette`): pitch black with the dark FlowFolio palette's colours and
    the least contrasting lines the layouts still read by. No grain, since the texture would light every background
    pixel the theme exists to leave dark. It is black whichever way Light or dark points. The theme picker wraps two
    by two below 520 dp so four previews fit a folded screen.
  - **Confetti on anything that fills** (`FlowFolioConfetti`, `rememberCelebration`, `ConfettiHeight`): a burst thrown
    up and out from a bar that reaches its end, built into `FlowFolioProgressBar`, `FlowFolioRing` and
    `GradientProgressBar`, so every caller gets it. A `celebrationId` (a book's id, a goal's scope) lets a screen that
    is already full celebrate once per run of the app; without one, only a completion watched happening fires. Dropping
    back under 100% arms it again.
  - **A green tick at the end of the reading-goal path** once the goal is met, beside the open book, with the burst
    over the whole path.
  - **Dynamic Flow on the cover**, beside the ribbon and the favourite: all of a book's marks now sit together in the
    bottom-right corner of its cover on a dimmed plate (`BookBadge`), instead of a ribbon top-left and a star top-right.
  - **A window for the reminder's time of day** (`TimeOfDayDialog` in the new `GoalReminder.kt`): hour and five-minute
    wheels, Cancel and Save, in place of a chip that stepped the time on a quarter of an hour per tap with no sign it
    could be set at all.
  - **The shelf page's six orders in the library** (`ShelfSorting.kt`: `ShelfSort`, `sortBooks`, `sortRank`): Custom,
    Name, Date added, Progress, Read Time and Read Goal, applied inside each shelf row so the books stay on their
    shelves. Picking Name also fetches the books by title, so the rows need no reordering at all.
  - `Glyph.StarFilled`, and `ic_stat_flowfolio`, the app icon's open book and "f" as one white stroke for the status
    bar.
- M3T2, finished books, favourites and fuzzy search (`docs/plans/phase3-m3-tweaks-plan.md`, Room v16):
  - Favourites (`books.favorite`): a star on each book, a Favourites tab in the tab bar and rail, a star toggle on the
    Continue reading card, and "Add to / Remove from favourites" in press-and-hold menus.
  - Finished books (`books.finished_at`): "Mark as finished" in press-and-hold menus and on the Continue reading card,
    a "Finished" filter chip (tiles view), a "Finished Books" shelf at the bottom of the library, and automatic
    removal from "In progress".
  - Glyphs on every filter chip and a second column of library header stats (favourites, finished books, dictionary
    words).
  - Global fuzzy search (`Fuzzy`, `SearchViewModel`, `SearchResultsList`): searches books (title, author, series,
    subjects), shelves, highlights and notes, bookmarks, favourites, and dictionary words (translations and
    definitions), forgiving typos and partial words, grouped with counts.
- M3T3, Reading Goal and offline Dictionary (`docs/plans/phase3-m3-tweaks-plan.md`, ADR-0046, Room v17):
  - Reading Goal icon (`Glyph.ReadingGoal`, open book with target and arrow) at the top right of the library header,
    and the curved Goal path (`GoalPath`, from `Glyph.Glasses` to `Glyph.OpenBook`) under the second column of stats
    when folded and in its own column when unfolded.
  - Centred Reading Goal window (`ReadingGoalDialog`): iPhone-style scrollable wheels for hours and minutes, period
    selector (Day, Week, Month), Reset, "Remind me" with time of day and day-of-week chips (requesting notification
    permission on Android 13+), and an In-progress section with gradient progress bars (`GradientProgressBar`).
  - Book details: "Add Reading Goal" button beside Read/Continue and Add to shelf, a second progress bar in Overview
    for time read against that book's goal, and a tip suggesting resetting the goal once exceeded.
  - Reading goal reminders (`GoalReminderScheduler`, `GoalReminderReceiver`, `GoalBootReceiver`, `ReadingQuotes`):
    scheduled via `AlarmManager`, restored after reboot, titled "Time to read <book title>" with 60 public-domain
    quotes about reading and knowledge.
  - Offline English Wiktionary dictionary (`WiktionaryStore`, `DictionaryRepository`, `DefineOverlay`): "Define" in
    the reader's selection menu when a single word is selected, and a Dictionary tab in the app shell grouped by book
    showing each word's translation and English definition, searchable and opening its place in the book.

### Changed
- M4b, the owner's second batch of tweaks (owner, 2026-11-02):
  - **Book details stacks its actions**: Read/Continue wide across the top, with Add to shelf and Add Reading Goal
    centred on a second row under it. Two to a row leaves room for their words, so only the narrowest screens
    shorten them.
  - **The reader's lookup search is no longer a tab.** Typing in the Dictionary panel now shows every definition and
    every translation it matches at once, whichever tab is showing; the tabs only split the lists when nothing is
    being searched for.
  - **The Continue Reading card's finished tick is the palette's green** whether or not the book is finished -- muted
    when it is not, bright and glowing when it is -- instead of reading as a dim grey tick. Its page count is now
    "p. 90/201" at 10 sp, which fits the cover screen; "p. 90 of 201" was cut off.
- M4, the owner's batch of tweaks (owner, 2026-11-02):
  - **The Reading Goal glyph is a target.** The M3T3 glyph drew an open book, a target and an arrow over one another
    and closed into a blot at the 16-18 dp it is used at; three rings stay legible that small.
  - **The reading-goal path curves much further**: its control points now sit beyond the ends and just outside a box
    raised from 20 dp to 34 dp, so the swing has room.
  - **The library's search box animates open** -- it grows, slides and fades in rather than appearing whole -- takes
    the cursor and raises the keyboard with it, and is outlined in the theme's own title colour instead of the faint
    hairline the rest of the app uses.
  - **The reminder's days are unmistakable**: solid and glowing in the theme's colour when the reminder runs on them,
    hollow and dim when it does not. The last day left cannot be turned off, since the reminder would never fire.
  - The goal period chips read **"A day", "A week", "A month"**.
  - **Book details keeps its three actions on one line** in every orientation and fold state. Where the line is short
    -- a folded screen, or the unfolded page's left half -- the two quieter buttons take one-word labels ("Shelf",
    "Goal") at chip size, everything tightens, and the three share the width equally so no label is cut off.
    `FlowFolioPrimaryButton` gained a `compact` flag and no longer wraps its label onto a second line.
  - On the Continue Reading card the **favourite's star fills in** (`ToggleShortcut`'s `checkedGlyph`) and the
    **finished tick turns the palette's green** (`checkedColor`), both glowing.
  - Every FlowFolio notification -- the reading-goal reminder, Dynamic Flow's progress, and "Dynamic Flow ready" for a
    shelf -- now carries the app's own glyph instead of a stock Android bell, download tick or the page-and-waves mark.
- M3T1, the owner's polish and navigation tweaks (`docs/plans/phase3-m3-tweaks-plan.md`):
  - Reader: the Contents panel's rows pour in one after another (`Modifier.cascade`); the riffle bar's side buttons are
    slim capsules with drawn, centred chevrons; the ribbon and pin are replaced by a riffle book under the bar whose
    pages flip as it slides, and a glass marker on the page block glowing in the theme's colour (no blur; never wider
    than the book; Roborazzi references re-recorded).
  - "Aa" sheet: a glyph for every setting (new glyphs: line spacing, margins, justify, hyphenate, paper, page turn,
    direction, spread, cover, auto-hide, sound, riffle, haptics); the tabs slide over each other; a setting's control
    goes under its name when both don't fit; the Dynamic Flow card has a glowing outline that breathes while it is on.
  - Dynamic Flow asks "convert the changes" only when the view being left has highlights, notes or bookmarks
    (`DynamicFlow.hasNotes`); otherwise it switches at once.
  - App shell: the tab bar (folded) and rail (unfolded) stay on every screen but the reader, showing glyphs only (the
    names are read by TalkBack and shown on a long press); pages under a tab keep clear of it (`clearOfChrome`) and
    keep that tab lit.
  - Library: search, sort and import sit level with the title; the Continue reading card sets its title smaller, shows
    "Progress" (open book) and "Read time" (clock) and, for a PDF, a Dynamic Flow button lit when on and dim when off.
  - Shelves: opening a shelf zooms its page out of the card tapped and back into it on Back (`ZoomOrigin`,
    `ZoomMotion`: scale and alpha only, so nothing is measured again while it runs); the shelf page's + and Select sit
    level with its title, Select's options under them and above the Tiles/List switch.
  - Owner's round on M3T1: a tab always opens on its own main page (a shelf left open under Shelves is closed) and the
    bar lights the right tab; the rail centres its glyphs and shows their names under them; the riffle book is outlined
    in the theme's darkest colour (black on a light theme) and riffles faster; margins sit under their label like line
    spacing; the text size's smaller and larger A are rounded squares like the sheet's other choices, the size centred
    between them; Go to page moved from the top bar into the Contents panel's top right.
  - Continue reading card: the cover sits at the top, level with the title, a little bigger; read time comes first,
    then the progress with its bar; the shortcuts (Dynamic Flow, annotations, bookmarks, shelf) have a row of their
    own under it all, so none is cut off on the cover screen.

### Fixed
- M4 (owner, 2026-11-02): the marker in the shelf colour picker followed the theme swatch rather than the choice. It
  was drawn under `c == null`, and the theme swatch is always first, so it looked stuck on the first colour. The
  selected swatch now carries a tick, in black or white depending on what it sits on.

- M4b (owner, 2026-11-02): the `fold8_api37` emulator flickered on macOS because `hw.gpu.mode=auto` resolved to
  software rendering (`swangle` + `lavapipe`) on Apple Silicon, which could not keep up with the design's blurs and
  glows at 2256x2504. The AVD now pins `hw.gpu.mode=host`, which selects Metal.

## [0.3.0-alpha.1] - 2026-10-28 — WITHDRAWN

> Withdrawn 2026-11-03, never downloaded. It was signed with the original release key, which was lost
> (ADR-0049); with no key there is no rotation lineage, so nothing can ever update over it. Kept for the record.

Phase 3 (the converter on the phone and Dynamic Flow), the first published build (ADR-0042); it also
holds Phases 0 to 2.

### Added
- Phase 3 milestones 1 and 2, converter v1 (`docs/plans/phase3-plan.md`, `docs/plans/phase3-m1m2-signoff.md`).
  - `:core:conversion`: a plain JVM module (ADR-0044) that turns a born-digital PDF into EPUB 3 plus `flowfolio.json`
    on the phone. Stages: extraction (lines, spans with per-character positions, fragments of one row joined),
    design system (running heads and folios as artifacts, printed page numbers, styles and heading levels), regions
    (figures, sidebars, tables), shapes (text and maths drawn as vector shapes become inline images in the line or
    display equations with their numbers), reading order (columns), blocks (paragraphs, lists, captions, code,
    superscripts and subscripts), headings and chapters from the PDF outline, and the EPUB writer (page list, contents
    in reading order, page breaks, WebP images on the phone). Pages under 98% text coverage go out as page images.
  - `:core:pdf`: `PdfiumConversionSource` reads glyph runs, fonts, images and paths through our JNI bridge
    (`nativeConversionPage`) and encodes crops as WebP. A 959-page textbook converts in about 58 s on the emulator.
  - The reader: converted books' inline drawings sit in the line, sized in ems (`InlineImageSpan`); equations are
    drawn in the text colour on any paper (`inkFilter`); `<sup>`/`<sub>` are set smaller as well as raised or lowered
    (`ScriptSpan`).
  - `android/tools/convert`: the same pipeline on the desktop through JNA and PDFium's own builds (development only):
    `convert`, `page`, `probe`, `render`, `unmatched` commands; `ConversionTest` (robustness PDFs, device-made EPUBs)
    and `CorpusTest` (EPUBCheck 5.4.0, the sidecar schema, metrics against `samples/golden/baselines/`, hand-labelled
    headings and reading order on 30 pages).
  - The corpus: `tools/corpus/fetch_corpus.py` (standard library) downloads 9 OpenStax PDFs and 7 Project Gutenberg
    EPUBs, pinned by SHA-256 in `samples/corpus/manifest.yaml`.
  - `docs/conversion-spec.md` v1.0.0 (normative) and `docs/schemas/flowfolio.schema.json`.
  - Release channels and versioning (ADR-0042): `0.<phase>.<patch>-<channel>.<n>`, a release signing key outside the
    repo, `flowfolio.versionName`/`versionCode` in `android/gradle.properties`.
- Phase 3 milestone 3, Dynamic Flow (the owner's design, ADR-0045): a PDF read as reflowed text, converted on the phone.
  - One book, two views: the EPUB is a hidden bundle (`files/flow/<book>/`), never a second book in the library, and
    is deleted with the book. Room v15: `books.flow_mode` (never chosen, PDF, Dynamic Flow) and `books.flow_locator`.
  - Settings > Dynamic Flow: "Turn on Dynamic Flow upon opening" (off by default) and "Ask before switching views".
  - The reader's "Aa" sheet: a Dynamic Flow card (its own glyph) with a switch, available whatever the setting; a
    progress ring on the switch and a small bar beside the page number while the book converts; the reader switches
    to Dynamic Flow at the same place when it is ready, and back to the PDF at the same page.
  - Conversion runs in a foreground service in its own process (`FlowService`, `:convert`), one book at a time, and
    waits while the phone is too hot or the battery is low. In-app banners and phone notifications when a conversion
    starts and ends (tapping "ready" opens the book); notification permission is asked at the first conversion.
  - Highlights, notes and bookmarks stay with the view they were made in, and each view, the Annotations and Bookmarks
    tabs and the library's counts show only the current view's. Switching asks whether to "convert the changes"
    (with "Don't show this again"); converting finds each highlight's words near the same page in the other view
    (`FlowCarrier`) and tells how many came across.
  - "Turn on/off Dynamic Flow" in a PDF's press-and-hold menu and the list's swipe actions.
  - `FlowServiceOnDeviceTest`; the CC BY books of the corpus are checked in CI (`corpus` job, cached by the manifest).

- Conversion quality round (the owner's findings, 2026-10-28; conversion spec 1.1.0, engine 1.1.0):
  - Code: monospaced fonts are recognised by measuring the glyphs (TeX's `SFTT`, `M+ 1mn` and others aren't flagged);
    a listing keeps its lines, indentation and blank lines, each character at its column, so diagrams drawn with
    characters keep their shape; shaded code boxes are listings, not sidebars; a listing over a page turn stays one;
    code in running text is `<code>`. The reader sets code in a monospaced face on a faint panel, never hyphenated or
    justified, scaled down to fit the column, and keeps short listings whole on one page.
  - Lists: an item's lines that hang under its text stay in it; consecutive items make one list, nested by depth,
    numbered (`<ol>`, its start and style) or bulleted, with bullets set in symbol fonts or drawn as shapes; the reader
    draws each item's bullet or number beside it (not part of the text, so highlights don't move).
  - Headings: decorative shapes behind a heading's text (a band, a number's tab) are no longer images in it; a running
    header beside the page number is removed even over a one-page section; a chapter's label ("Chapter 5") is the first
    line of its title, not the end of the previous chapter; a table's header row is not a heading.
  - Tables without rules (three or more aligned columns of short cells) go out as tables (an image of the table, its
    text as alt), drawn in the page's ink like equations; a word hyphenated at a line's end (PDFium's 0x02) is joined.
  - Dynamic Flow bundles made by an older converter are converted again when the book is next opened, and their
    highlights found again by their words.
  - Tests: `technical.pdf` (made by `tools/testpdfs`) and `TechnicalBookTest`, `CodeLayoutTest`, parser tests for list
    markers and code; five technical books in the local corpus (OpenStax computing titles, *Think Python*, *Pro Git*).

- Conversion round on the owner's own books (2026-10-28; conversion spec 1.2.0, engine 1.2.2; notes exported from Word,
  a book of notes typed in a code font, an O'Reilly title):
  - Paragraphs: a new paragraph is found by the step from line to line, wider than the page's usual one, rather than
    by the gap between line boxes (Word's boxes are as tall as the leading, or tight to the glyphs, so every line was a
    paragraph or none was); a sentence ending well short of a full next line ends its paragraph; a line mostly in bold
    at the body's size that runs across the column is prose, not a heading.
  - Inline style: words in bold or italics are `<strong>` and `<em>`; a line with a few bold words is no longer a
    heading.
  - Code: a book set throughout in a monospaced face (with its code in another) reads as prose; when the most used
    style sits mostly on shaded panels, the body is the next one that doesn't.
  - Lists: symbol and emoji bullets (✅ 🔹 📌 ❶) are kept as each item's marker (`list-style-type`, drawn by the
    reader); Word's second-level "o" in Courier New is a bullet, not code; bullets drawn as small pictures may touch
    their text.
  - Words broken over a line or a page turn with Unicode's hyphen or a soft hyphen are joined ("Java‐ Script"); a name
    in code broken over two lines without a hyphen is one name ("Deployment").
  - Tables: Word's cell borders (drawn column by column) make one table, grown over its full-width outer rules, a
    header row shaded instead of ruled, and a last row drawn without borders; two tables one above the other stay two;
    two columns of short cells under a bold header row are a table.
  - A logo or band repeated at the same place in the margins of several pages is page furniture, like a running header.
  - Table pictures keep their own colours when they have any (coloured icons or cells, a shaded header row; `Ink`,
    from the drawn pixels); only plain black-and-white tables are drawn in the reader's ink (the owner's choice).
  - A highlight drawn behind a line of text (a marker band under a title) is no longer a figure showing the text
    again; rules of the same length down a column with no side borders (fraction bars, section separators) are cut
    where no row could span the gap, so they are no longer a table hiding the text between them.
  - A list item whose text runs to the margin keeps the indented sub-item under it as an item of its own (the
    hanging-indent rule had joined them); an emoji in a code listing ("Result: ✅ PASS") no longer cuts it in two; a
    heading with a word in code ("The `in` operator") is still a heading.
  - Reader: code (panels and inline `code`) is set in JetBrains Mono 2.211 (SIL OFL 1.1, 187 KB, bundled in
    `core:designsystem`; the owner's choice), so it looks the same on every phone. `Typeface.MONOSPACE` is not used:
    a phone's font style (Samsung's) can swap it for a proportional face and break code's columns. Without the bundled
    face (`CodeFace`), a system font file that really is monospaced is used. A code line too long even at the
    smallest size wraps with its continuation set in two characters, like an editor's soft wrap.
  - Dynamic Flow: after a book is converted again (a newer engine), the reading place is found again from the PDF
    page it was on; the old place, a character offset in the old text, could open the book dozens of pages away.
  - Engine 1.2.2. Tests: `OwnBooksTest`, a parser test for an item's own glyph; the desktop tool's `probe` lists a
    page's paths and `page` the book's styles.

### Fixed
- Recording reading time for a book that is no longer in the library no longer crashes the reader.
- Turning Dynamic Flow on or off from a book's press-and-hold menu or the list's swipe now asks the same questions as
  the reader ("convert the changes"), and carries the notes.
- A bookmark or highlight made in Dynamic Flow, opened from the Bookmarks or Annotations tab, opens Dynamic Flow at it
  (the jump set the EPUB place, which Dynamic Flow doesn't read).

### Removed
- The Python `converter/` server, its Docker image, its CI jobs and Dependabot entries (ADR-0043). The ruff hooks now
  check the `tools/` scripts.

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
- Owner's batch of 2026-10-23: no select mode on the Library (bulk deletion was too easy by accident; shelf pages keep
  it); elsewhere the selection bar takes a row of its own under the title, so the title no longer collapses on the
  cover screen, and Select all turns into Unselect all. The library header lists books, books in progress, bookmarks,
  annotations and reading time, each after its glyph (new Hourglass and Stopwatch glyphs; `LibraryStatsViewModel`).
  Details: the top fade's blur eases out in bands and is lighter over the cover's backdrop; "Add to shelf" shows a
  shelf; a sideways swipe moves between the tabs; bigger play triangles on Continue and Resume. Folded, the reader's
  top bar puts the whole book title on its own centred row above Back and the tools.
- Phase 2 (EPUB reading and the pagination engine) signed off by the owner, 2026-10-27 (`docs/plans/phase2-signoff.md`).
- EPUB chapters start on their own spread (ADR-0041, owner, 2026-10-27). In two-page view every chapter after the first
  starts on a left page, with a blank page before it when needed. A text change lays out only the reader's chapter
  before the pages show, and the rest in the background: on the Fold, Moby-Dick at 41% shows its pages 18-45 ms after
  a size change (was 414-617 ms). The equations of the pages in view are read on all cores before they are drawn
  (about 180 ms a page before, 7-37 ms now). While a page is drawn again with new equations, its earlier drawing in
  the same layout stays on screen (`PageKey.layout`).
- M5, Phase 2 close-out (2026-10-27, `docs/plans/phase2-signoff.md`).
  - `EpubPageTurnBenchmark` (Moby-Dick in a spread, release build): on the emulator EPUB turns cost no more than PDF
    turns (frame CPU P50 9.2 ms against 10.7 ms).
  - `RobustnessOnDeviceTest`: every corpus book, the IDPF samples and the hostile ones, is read through at two sizes
    (text, boxes, links, pictures, islands, drawing) or refused cleanly; all 17 pass.
  - Memory over 300 EPUB spread turns: 3.2% growth.
  - After a text change the hourglass goes as soon as the pages in view are drawn, not all eight planned pages; page
    renders go before thumbnails, links and highlight placement. Logcat `FlowFolioLayout` reports "pages in view
    shown N ms after the change".
- M4, Phase 1 features on EPUB (2026-10-27, `docs/plans/phase2-m4.md`, ADR-0040).
  - Press and hold selects text on EPUB pages as on PDFs (handles, magnifier). The page's text and character boxes
    come from the layout (`ReflowBook.pageGlyphs`), with the slack of justified lines shared over their spaces
    (`getPrimaryHorizontal` leaves justification out).
  - Highlights, notes, Share, Copy and Translate on EPUBs. A highlight is stored by its chapter and character offsets
    (`annotations.locator`, Room v14) and placed again after any re-layout (text settings, fold or unfold), on each
    page it runs onto, in Curl, Slide and Scroll.
  - Ribbons on EPUB pages, stored by the text at the top of the page (`bookmarks.locator`). The reader's
    Annotations and ribbon buttons show for EPUBs: six buttons for every book.
  - The Annotations and Bookmarks tabs open an EPUB at the highlight's or ribbon's text and name its chapter from the
    book's contents. Saved pages follow the reader's last layout.
  - Tapping a picture in an EPUB opens it zoomable, like a shrunk equation (`FigureZoom`, formerly `IslandZoom`).
  - Right to left: selection follows the script's reading order, with handles at its start and end.
  - Fixed-layout EPUBs move to Phase 8 (a second, WebView-based renderer); until then they are reflowed.
- M3 checks (2026-10-26, `docs/plans/phase2-m3-signoff.md`). Pagination is exact in every reading font at five sizes
  and with the other text settings at their extremes (415 layouts on the corpus, `PaginationOnDeviceTest`); a sheet
  of every font on every paper (`docs/plans/assets/phase2-m3-fonts-papers.jpg`). Re-layout lays the chapters up to the
  reader's out on all cores at once (Moby-Dick at 41% on the emulator: 489-984 ms before, 419-679 ms after, debuggable).
- Fixed: changing the paper colour while reading could crash the app: the riffle scrubber's loupe drew a page
  thumbnail the cache had already recycled for the old colour. Thumbnails are checked as they are drawn and fetched
  again in the new colour (the landing preview too).
- Fixed: folded, a PDF's six reader buttons overflowed the cover screen and the row squeezed the last one, the
  bookmark; the buttons now share the row's width equally.
- Owner's shelf batch (2026-10-26).
  - Press and hold a shelf on the Shelves tab: Select, Add books, Edit, Remove. "Add books" lists every book in the
    library (with a search once there are more than eight), ticks the ones already on the shelf, and adds the newly
    ticked ones first. A shelf's page has a + that does the same, beside Select.
  - Press and hold now offers "Select" everywhere items can be chosen (books, folders, shelves, ribbons, annotations
    and annotated books): select mode starts with that item chosen. List rows on shelf pages respond to press and hold.
  - Select mode on a shelf or folder page has "Add to shelf": tick shelves (or make one) to put every chosen book on;
    nothing is taken off a shelf. A chosen folder brings all its books.
  - Drag and drop to reorder a shelf's books, on the library's shelf rows (when no filter or search is on) and on the
    shelf's page in tiles or list: press and hold, then drag; holding still and letting go opens the menu as before.
    The order is kept (Room v13 `shelf_books.position`); books added to a shelf go first.
- Fixed: switching the reader's paper between a light and a dark one rebuilt the reader's bars and panels mid-reveal
  (the Aa panel jumped back to its first tab and the bars' entrance replayed), because `FlowFolioPalette` composed its
  content in a different place for each case. The reveal also starts from a closed circle every time.
- Owner's library batch (2026-10-26, ADR-0037, ADR-0038).
  - Import several files at once, or a whole folder with every folder inside it (the + button's "Import files…" and
    "Import a folder…"). A batch shows "Importing n of N" and ends with one message (added, already there, couldn't be
    opened). A folder read in place keeps one grant for the whole folder.
  - A "Folders" chip lists the imported folders; each opens a page like a shelf's (folders first, then books, as tiles
    or a list), with press-and-hold menus and select mode for folders and books alike (Room v12 `books.folder`).
  - The library's shelf rows keep one height, so the plank no longer moves while a row of books of different sizes
    scrolls, and books stand half as far apart.
  - Grid tiles: books 30% bigger with no shadow under them, a smaller left-aligned title (two lines, no wider than the
    book) and the author under it; rows take as many books as fit (five on the open Fold, two on the cover screen).
  - Fuller book details from Open Library: subjects, printed pages, series and rating join the details page; failed
    calls are retried once; searches ask for every field. Google Books was tried as a second source and removed the
    same day: without an API key its shared daily quota refuses requests (ADR-0039).
- Fixed: EPUBs read in place from shared storage could not be imported or opened ("not a book"): Android refuses to
  re-open `/proc/self/fd/N` by path under scoped storage, so `EpubArchive` now reads the zip through the descriptor
  itself (`ChannelZip`).
- M2 carry-overs (Phase 2, 2026-10-26). Paragraphs inside lists, and paragraphs broken into several lines with `<br>`
  (lists and verse written by hand), are set ragged, not justified, so their short lines no longer gape. A book that
  can't open (file missing, damaged PDF or EPUB) shows a card in the app's theme instead of black text on blank paper.
  Scroll shows an EPUB as one continuous column: each page only down to its last line (`ReflowBook.scrollExtent`,
  `ScrollBands`, `ScrollBand`), so no page margins interrupt the text, and chapters stand 3 ems apart; links in Scroll
  are found where the pages lie. An equation or table drawn noticeably smaller than its size (a wide derivation on the
  cover screen) opens on its own when tapped (`IslandZoom`): fitted to the screen, pinch to zoom up to 4x and pan,
  tap or Back to close.
- Owner's follow-ups (ADR-0035, 2026-10-23): the edge fades blur only behind the system bars and the top one ends
  closer to them, clear of the titles; in the reader a fade in the paper's colour lies behind the top bar while it shows.
  Annotations name the section they come from by page: a stray outline entry listed late (University Physics' "Blank
  Page") no longer stands for every page after it; the details page's Contents marks the current chapter the same way.
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
