# Phase 6 M0 — the owner's tweak batch (reading-surface precision)

- **Status:** approved by the owner 2026-11-07 ("yes to all. start").
- **Delivery:** a debug APK (`assembleDebug`), verified locally on `fold8_api37` folded and unfolded
  (`spotlessCheck detekt lintDebug testDebugUnitTest`). CI runs at the phase boundary, not here.

## Items

1. **Folded running head clear of the camera** — the folio-style chapter title (reflowed books, chrome bars
   hidden) currently starts at the page text's left margin (32 dp) and is capped at half the cover width, so it
   runs under the front-camera punch-hole. Move its left edge to near the screen edge and truncate it at the
   camera's left edge, read from the display cutout (no hardcoded Fold pixels).
2. **Top-level page title onto the button row** — `ScreenHeader` puts the title above the buttons. Lift the title
   up to share the buttons' row (bottom-aligned), slogan staying below. Applies to every `ScreenHeader` page.
3. **Unfolded section label right-aligned** — the section label on the bottom-right of a spread currently starts at
   the right page's inner margin and grows right. Right-align it to the right page's outer margin, capped so it can
   grow left only to the right page's left margin, ellipsis at the right.
4. **Book-details title capped at 3 lines** — `DetailsHeading` shows the title uncapped. Cap at 3 lines with an
   ellipsis and make the title open the full title in a small dialog.
5. **First-use guides on buttons** — wire spotlight coach marks to the reader's chrome tools on first *use* (tap),
   not first sight, reusing the existing `Guide`/`CoachMark`/`seenGuides`/`tipsEnabled` machinery. Reader buttons
   first (Aa, Contents, Go-to-page, ribbon, Dictionary, Annotations) plus the riffle scrubber, then library search
   and details. One-time each; "Never show tips again" and Settings → "Introduce features again" keep working.
6. **Bookmarks grouped per book** — mirror the Annotations tab: the main Bookmarks tab shows one card per book
   (title, author, small ribbon icons matching the ribbons actually in that book); tapping opens that book's ribbon
   cards; the colour-filter bar moves into the per-book page.

## Files

- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderChrome.kt` — items 1, 3, 5 (tools).
- `android/feature/library/src/main/kotlin/com/flowfolio/feature/library/ScreenHeader.kt` — item 2.
- `android/feature/library/src/main/kotlin/com/flowfolio/feature/library/DetailsScreen.kt` — item 4.
- `android/feature/library/src/main/kotlin/com/flowfolio/feature/library/Guide.kt` — item 5 (new entries).
- `android/core/designsystem/src/main/kotlin/com/flowfolio/core/designsystem/Spotlight.kt` and `Guidance.kt` — item 5
  (reuse `CoachMark`; no change expected unless the form needs it).
- `android/feature/library/src/main/kotlin/com/flowfolio/feature/library/BookmarksScreen.kt` and
  `BookmarksViewModel.kt` — item 6.
- `android/feature/library/src/main/res/values/strings.xml` — copy for items 4, 5, 6.

## Verification

- `./gradlew spotlessApply detekt testDebugUnitTest lintDebug` (with `-x :tools:convert:test` on macOS arm64).
- `./gradlew :app:assembleDebug`.
- On `fold8_api37`: folded and unfolded, confirm each item; screenshots via `screencap -d <id>`.
- Unit tests where logic is added: bookmarks grouping (item 6), guide ids (item 5).
